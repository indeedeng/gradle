package org.gradle.api.internal.indeed;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultComponentSelectionDescriptor;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Given a ComponentSelector, applies all the proper rewrites, overrides, and excludes
 * to return the appropriate resulting 0 or more ComponentSelectors.
 *
 * Essentially performs this loop until the result is consistent:
 * (1) Apply <override> and <exclude> rules
 *      (never override to a revision previously seen in the loop)
 * (2) Apply rewrite rules given to us by gradle plugins
 */
public class IndeedResolverCore {
    @Nullable
    private final MergedOverrideRules overrideRules;

    @Nullable
    private final DependencySubstitutionApplicator rewriteRules;

    private static final GradleDependencyMetadata dummyMetadata = new GradleDependencyMetadata(
            null,
            ImmutableList.of(),
            false,
            null,
            false
    );

    public IndeedResolverCore(
            @Nullable final MergedOverrideRules overrideRules,
            @Nullable final DependencySubstitutionApplicator rewriteRules
    ) {
        this.overrideRules = overrideRules;
        this.rewriteRules = rewriteRules;
    }

    public List<Result> rewrite(
            final ComponentSelector selector
    ) throws Throwable {
        return rewrite(
                selector,
                ImmutableList.of(),
                0
        );
    }

    private List<Result> rewrite(
            final ComponentSelector selector,
            final Collection<ComponentSelectionDescriptorInternal> baseReasons,
            final int depth
    ) throws Throwable {
        if (depth > 20) {
            throw new RuntimeException("Detected loop while resolving overrides for " + selector + ": " + baseReasons);
        }

        final List<Result> rewritten = rewriteOneStep(selector);
        final ImmutableList.Builder<Result> results = ImmutableList.builder();
        for (final Result res : rewritten) {
            final List<ComponentSelectionDescriptorInternal> reasons = new ArrayList<>(baseReasons);
            reasons.addAll(res.reasons);
            if (Objects.equal(res.selector, selector)) {
                results.add(new Result(res.selector, reasons));
            } else {
                results.addAll(rewrite(res.selector, reasons, depth + 1));
            }
        }
        return results.build();
    }

    /**
     * Rewrites a single step (either overrides, or rewrites)
     * Returns null if no remaining action is needed.
     */
    private List<Result> rewriteOneStep(
            ComponentSelector selector
    ) throws Throwable {

        final ImmutableList.Builder<ComponentSelectionDescriptorInternal> reasons = ImmutableList.builder();

        // Apply overrides
        if (overrideRules != null && selector instanceof ModuleComponentSelector) {
            final ModuleComponentSelector mcs = (ModuleComponentSelector)selector;
            final OverrideRule.Result result = overrideRules.getOverride(mcs);
            if (result != null) {
                // BEGIN_INDEED GRADLE-436
                if (result.isExclude()) {
                    // Selector was excluded
                    return ImmutableList.of();
                // END_INDEED
                } else {
                    // Selector was overridden
                    selector = DefaultModuleComponentSelector.newSelector(
                            DefaultModuleIdentifier.newId(
                                    mcs.getGroup(),
                                    mcs.getModule()
                            ),
                            result.version
                    );
                    final String reason = mcs + " -> " + selector + " (" + Joiner.on(", ").join(result.reasons) + ")";
                    reasons.add(new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of(reason)));
                }
            }
        }

        // BEGIN_INDEED GRADLE-438
        // Apply rewrite rules
        if (rewriteRules != null) {
            final DependencySubstitutionApplicator.SubstitutionResult substitutionResult =
                    rewriteRules.apply(dummyMetadata.withTarget(selector));
            if (substitutionResult.hasFailure()) {
                throw substitutionResult.getFailure();
            }
            DependencySubstitutionInternal details = substitutionResult.getResult();
            if (details != null && details.isUpdated()) {
                final ComponentSelector oldSelector = selector;
                selector = details.getTarget();
                final String rewriteReason = details.getRuleDescriptors().stream().map(rule -> rule.getDescription()).collect(Collectors.joining(" + "));
                if (selector instanceof ModuleComponentSelector) {
                    final ModuleComponentSelector mcs = (ModuleComponentSelector)selector;
                    if (mcs.getVersion().equals("{{")) {
                        return ImmutableList.of();
                    } else if (mcs.getVersion().startsWith("{{")) {
                        final Iterable<String> notations = Splitter.on("||").split(mcs.getVersion().substring(2));
                        final Iterable<ModuleComponentSelector> selectors = FluentIterable.from(notations)
                                .transform(new Function<String, ModuleComponentSelector>() {
                                    public ModuleComponentSelector apply(String notation) {
                                        final List<String> split = Splitter.on(':').splitToList(notation);
                                        if (split.size() < 2 || split.size() > 3) {
                                            throw new RuntimeException("Invalid id: " + split);
                                        }
                                        final ModuleIdentifier id = DefaultModuleIdentifier.newId(split.get(0), split.get(1));
                                        if (split.size() == 3) {
                                            return DefaultModuleComponentSelector.newSelector(id, split.get(2));
                                        } else {
                                            return DefaultModuleComponentSelector.newSelector(id, "");
                                        }
                                    }
                                });
                        final String reason = oldSelector + " -> " + Joiner.on(',').join(selectors) + " (" + rewriteReason + ")";
                        reasons.add(new DefaultComponentSelectionDescriptor(ComponentSelectionCause.SELECTED_BY_RULE, Describables.of(reason)));
                        final ImmutableList.Builder<Result> results = ImmutableList.builder();
                        for (final ModuleComponentSelector s : selectors) {
                            results.add(new Result(s, reasons.build()));
                        }
                        return results.build();
                    }
                }
                final String reason = oldSelector + " -> " + selector + " (" + rewriteReason + ")";
                reasons.add(new DefaultComponentSelectionDescriptor(ComponentSelectionCause.SELECTED_BY_RULE, Describables.of(reason)));
            }
        }
        // END_INDEED
        return ImmutableList.of(new Result(selector, reasons.build()));
    }

    public static class Result {
        public final ComponentSelector selector;
        public final List<ComponentSelectionDescriptorInternal> reasons;

        public Result(final ComponentSelector selector, final List<ComponentSelectionDescriptorInternal> reasons) {
            this.selector = selector;
            this.reasons = reasons;
        }
    }

}
