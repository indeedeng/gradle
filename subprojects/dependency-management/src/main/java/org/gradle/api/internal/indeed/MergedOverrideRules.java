package org.gradle.api.internal.indeed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contains all override rules that apply to a module.
 * "parentRules" are rules inherited from incoming edges.
 * "localRules" are rules present within this module.
 *
 * The proper method of resolution for a version, is to first pass the request
 * through all parentRules, and keep the newest version. If any parentRule
 * did not return a result for the request (meaning it was not overridden), then
 * use the result of localRules if it is newer than the newest from parents.
 */
public class MergedOverrideRules extends OverrideRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedOverrideRules.class);

    private static final VersionSelectorComparatorWithExcluded versionComparator =
            new VersionSelectorComparatorWithExcluded();

    /**
     * A version override will be set to this string
     * if the module should be excluded.
     */

    private final Collection<MergedOverrideRules> parentRules;
    private final Collection<SingleOverrideRule> localRules;
    // This is stored purely for informational purposes when debugging

    private final Object source;
    private final Collection<MergedOverrideRules> realParentRules;
    private final Set<NodeState> allParentSources;

    public MergedOverrideRules(
            final Collection<MergedOverrideRules> parentRules,
            final Collection<SingleOverrideRule> localRules,
            final Object source,
            final Collection<MergedOverrideRules> realParentRules,
            final Set<NodeState> allParentSources
    ) {
        this.parentRules = parentRules;
        this.localRules = localRules;
        this.source = source;
        this.realParentRules = realParentRules;
        this.allParentSources = allParentSources;
    }

    public static MergedOverrideRules create(
            final Collection<MergedOverrideRules> parentRules,
            final Collection<SingleOverrideRule> localRules,
            final Object source
    ) {
        final Set<MergedOverrideRules> flatParentRules = Sets.newHashSet();
        final Set<NodeState> allParentSources = new HashSet<>();
        for (final MergedOverrideRules parentRule : parentRules) {
            if (parentRule.source instanceof NodeState) {
                allParentSources.add((NodeState)parentRule.source);
            }
            allParentSources.addAll(parentRule.allParentSources);
            if (parentRule.localRules.isEmpty() && !parentRule.parentRules.isEmpty()) {
                flatParentRules.addAll(parentRule.parentRules);
            } else {
                flatParentRules.add(parentRule);
            }
        }

        final MergedOverrideRules merged = new MergedOverrideRules(flatParentRules, localRules, source, parentRules, allParentSources);
        merged.assertNoDependencyLoop();
        return merged;
    }

    // BEGIN_INDEED GRADLE-440
    private void assertNoDependencyLoop() {
        if (!allParentSources.contains(source)) {
            return;
        }
        LOGGER.error("===========================");
        LOGGER.error("===========================");
        LOGGER.error("===========================");
        LOGGER.error(" ~~ FATAL RESOLVE ERROR ~~ ");
        LOGGER.error("Detected a dependency loop revolving around: " + this.toString());
        LOGGER.error("Attempting to find more details about the loop ...");
        checkLoopPath(new LinkedList<MergedOverrideRules>(ImmutableList.of(this)));
        throw new Error("Dependency loop. See error log above for details.");
    }

    private void checkLoopPath(final LinkedList<MergedOverrideRules> path) {
        if (path.size() > 1 && path.get(0).source == path.get(path.size()-1).source) {
            LOGGER.error("Found a loop:");
            for (final MergedOverrideRules node : path) {
                LOGGER.error(node.source.toString()+" ->");
            }
            LOGGER.error("...");
            LOGGER.error(" ");
            return;
        }
        if (path.size() > 100) {
            LOGGER.error("Path length sanity error");
            return;
        }
        if (path.get(0).allParentSources.contains(path.get(path.size()-1).source)) {
            for (final MergedOverrideRules parent : path.get(0).realParentRules) {
                path.addFirst(parent);
                checkLoopPath(path);
                path.removeFirst();
            }
        }
    }
    // END_GRADLE

    private Map<ModuleIdentifier, Result> resultCache
            = new HashMap<ModuleIdentifier, Result>();

    @Nullable
    public Result getOverride(final ModuleComponentSelector selector) {
        final ModuleIdentifier id = DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule());
        final String requested = selector.getVersion();
        Result result = getOverride(id);

        // Don't use override if requested version is higher and there's a clear path to root
        if (result != null && result.hasClearPathToRoot && versionComparator.compare(requested, result.version, false) > 0) {
            result = null;
        }

        // Don't use override if it would do nothing
        if (result != null && StringUtils.equals(result.version, requested)) {
            result = null;
        }

        return result;
    }

    /**
     * Returns null if no override rule applies
     * Returns EXCLUDE_VERSION if module should be excluded
     */
    @Override
    @Nullable
    public Result getOverride(final ModuleIdentifier id) {
        if (resultCache.containsKey(id)) {
            return resultCache.get(id);
        }
        final Result override = getOverrideNoCache(id);
        resultCache.put(id, override);
        return override;
    }

    // BEGIN_INDEED GRADLE-436
    @Nullable
    private Result getOverrideNoCache(final ModuleIdentifier id) {
        final Result parentRuling = getOverrideNoCache(id, parentRules, false);
        final Result localRuling = getOverrideNoCache(id, localRules, true);

        // Order of preference for proper override logic
        // 1. Local <exclude> always wins
        // 2. Throw out any parent <exclude> ruling if it's partial
        // 3. Use local ruling if (local ruling > parent ruling) and (there's a clear path to root)
        // 4. Use parent ruling (if there is one)
        // 5. Use local ruling (if there is one)

        if (localRuling != null && localRuling.isExclude()) {
            return localRuling;
        }
        if (parentRuling != null) {
            if (parentRuling.hasClearPathToRoot) {
                if (parentRuling.isExclude()) {
                    return localRuling;
                } else if (localRuling != null && versionComparator.compare(localRuling.version, parentRuling.version, false) > 0) {
                    return localRuling;
                } else {
                    final boolean hasClearPathToRoot = localRuling == null;
                    return new Result(parentRuling.version, hasClearPathToRoot, parentRuling.reasons);
                }
            } else {
                return parentRuling;
            }
        }
        return localRuling;
    }
    // END_INDEED

    @Nullable
    private static Result getOverrideNoCache(
            final ModuleIdentifier id,
            final Collection<? extends OverrideRule> rules,
            final boolean isLocalRuling
    ) {
        final boolean excludePreferred = isLocalRuling;

        boolean hasClearPathToRoot = rules.isEmpty();
        String override = null;
        final Set<String> reasons = new HashSet<String>();
        for (final OverrideRule rule : rules) {
            final Result result = rule.getOverride(id);
            if (result != null) {
                if (override == null) {
                    override = result.version;
                    reasons.addAll(result.reasons);
                } else {
                    final int compare = versionComparator.compare(result.version, override, excludePreferred);
                    if (compare >= 0) {
                        if (compare > 0) {
                            reasons.clear();
                        }
                        override = result.version;
                        reasons.addAll(result.reasons);
                    }
                }
                hasClearPathToRoot |= result.hasClearPathToRoot;
            } else if (!isLocalRuling) {
                hasClearPathToRoot = true;
            }
        }
        if (override == null) {
            return null;
        } else {
            return new Result(override, hasClearPathToRoot, reasons);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergedOverrideRules that = (MergedOverrideRules) o;
        if (hashCode() != that.hashCode()) {
            return false;
        }
        return Objects.equals(parentRules, that.parentRules) &&
                Objects.equals(localRules, that.localRules);
    }

    private boolean cachedHashCode = false;
    private int hashCode;

    @Override
    public int hashCode() {
        if (!cachedHashCode) {
            cachedHashCode = true;
            hashCode = Objects.hash(parentRules, localRules);
        }
        return hashCode;
    }
}
