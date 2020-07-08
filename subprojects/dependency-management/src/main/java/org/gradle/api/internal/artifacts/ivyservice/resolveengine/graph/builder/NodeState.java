/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.indeed.IndeedResolverCore;
import org.gradle.api.internal.indeed.IndeedRuleRewriter;
import org.gradle.api.internal.indeed.MergedOverrideRules;
import org.gradle.api.internal.indeed.OverrideRuleSerializer;
import org.gradle.api.internal.indeed.SingleOverrideRule;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ShadowedCapability;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.SelectedByVariantMatchingConfigurationMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a node in the dependency graph.
 */
public class NodeState implements DependencyGraphNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final Long resultId;
    private final ComponentState component;
    private final List<EdgeState> incomingEdges = Lists.newArrayList();
    private final List<EdgeState> outgoingEdges = Lists.newArrayList();
    private final ResolvedConfigurationIdentifier id;

    private final ConfigurationMetadata metaData;
    private final ResolveState resolveState;
    private final ModuleExclusions moduleExclusions;
    private final boolean isTransitive;
    private final boolean selectedByVariantAwareResolution;
    private final boolean dependenciesMayChange;
    private boolean doesNotHaveDependencies;

    // In opposite to outgoing edges, virtual edges are for now pretty rare, so they are created lazily
    private List<EdgeState> virtualEdges;
    private boolean queued;
    private boolean evicted;
    private int transitiveEdgeCount;
    private Set<ModuleIdentifier> upcomingNoLongerPendingConstraints;
    private boolean virtualPlatformNeedsRefresh;
    private Set<EdgeState> edgesToRecompute;
    private Multimap<ModuleIdentifier, DependencyState> potentiallyActivatedConstraints;

    // caches
    private Map<DependencyMetadata, DependencyState> dependencyStateCache = Maps.newHashMap();
    private Map<DependencyState, EdgeState> edgesCache = Maps.newHashMap();

    // exclusions optimizations
    private int previousIncomingEdgeCount;
    private long previousIncomingHash;
    private long incomingHash;
    private ExcludeSpec cachedModuleResolutionFilter;
    private ResolvedVariantResult cachedVariantResult;


    // BEGIN_INDEED GRADLE-432
    private List<SingleOverrideRule> localOverrideRules;
    MergedOverrideRules previousMergedOverrideRules;
    // END_INDEED

    public NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
        this.resultId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.metaData = md;
        this.isTransitive = metaData.isTransitive();
        this.selectedByVariantAwareResolution = md instanceof SelectedByVariantMatchingConfigurationMetadata;
        this.moduleExclusions = resolveState == null ? null : resolveState.getModuleExclusions(); // can be null in tests, ResolveState cannot be mocked
        this.dependenciesMayChange = component.getModule() != null && component.getModule().isVirtualPlatform(); // can be null in tests, ComponentState cannot be mocked
        component.addConfiguration(this);

        // BEGIN_INDEED GRADLE-432
        /*
        Generate the list of overrides defined in the metadata for this module.
        This includes force="true", <override>, <conflict>, and <exclude> tags.
        */
        localOverrideRules = IndeedRuleRewriter.rewriteOverrides(
                resolveState.getDependencySubstitutionApplicator(),
                OverrideRuleSerializer.parseFromNode(component.getMetadata(), metaData)
        );
        // END_INDEED
    }

    // the enqueue and dequeue methods are used for performance reasons
    // in order to avoid tracking the set of enqueued nodes
    boolean enqueue() {
        if (queued) {
            return false;
        }
        queued = true;
        return true;
    }

    NodeState dequeue() {
        queued = false;
        return this;
    }

    @Override
    public ComponentState getComponent() {
        return component;
    }

    @Override
    public Long getNodeId() {
        return resultId;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public ResolvedConfigurationIdentifier getResolvedConfigurationId() {
        return id;
    }

    @Override
    public ComponentState getOwner() {
        return component;
    }

    @Override
    public List<EdgeState> getIncomingEdges() {
        return incomingEdges;
    }

    @Override
    public List<EdgeState> getOutgoingEdges() {
        return outgoingEdges;
    }

    @Override
    public ConfigurationMetadata getMetadata() {
        return metaData;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        if (metaData instanceof LocalConfigurationMetadata) {
            // Only when this node has a transitive incoming edge
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.isTransitive()) {
                    return ((LocalConfigurationMetadata) metaData).getFiles();
                }
            }
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", component, id.getConfiguration());
    }

    public String getSimpleName() {
        return component.getId().toString();
    }

    public String getNameWithVariant() {
        return component.getId() + " variant " + id.getConfiguration();
    }

    public boolean isTransitive() {
        return isTransitive;
    }

    /**
     * Visits all of the dependencies that originate on this node, adding them as outgoing edges.
     * The {@link #outgoingEdges} collection is populated, as is the `discoveredEdges` parameter.
     *
     * @param discoveredEdges A collector for visited edges.
     */
    public void visitOutgoingDependencies(Collection<EdgeState> discoveredEdges) {
        // If this configuration's version is in conflict, do not traverse.
        // If none of the incoming edges are transitive, remove previous state and do not traverse.
        // If not traversed before, simply add all selected outgoing edges (either hard or pending edges)
        // If traversed before:
        //      If net exclusions for this node have not changed, ignore
        //      If net exclusions for this node not changed, remove previous state and traverse outgoing edges again.

        if (!component.isSelected()) {
            LOGGER.debug("version for {} is not selected. ignoring.", this);
            if (upcomingNoLongerPendingConstraints != null) {
                for (ModuleIdentifier identifier : upcomingNoLongerPendingConstraints) {
                    ModuleResolveState module = resolveState.getModule(identifier);
                    for (EdgeState unattachedDependency : module.getUnattachedDependencies()) {
                        if (!unattachedDependency.getSelector().isResolved()) {
                            // Unresolved - we have a selector that was deferred but the constraint has been removed in between
                            NodeState from = unattachedDependency.getFrom();
                            from.prepareToRecomputeEdge(unattachedDependency);
                        }
                    }
                }
                upcomingNoLongerPendingConstraints = null;
            }
            return;
        }

        // Check if there are any transitive incoming edges at all. Don't traverse if not.
        if (transitiveEdgeCount == 0 && !isRoot()) {
            handleNonTransitiveNode(discoveredEdges);
            return;
        }

        // Determine the net exclusion for this node, by inspecting all transitive incoming edges
        // BEGIN_INDEED GRADLE-432
        final ExcludeSpec resolutionFilter = moduleExclusions.nothing();
        final MergedOverrideRules mergedOverrideRules = getMergedOverrideRules(incomingEdges);
        // END_INDEED

        // Virtual platforms require their constraints to be recomputed each time as each module addition can cause a shift in versions
        if (!isVirtualPlatformNeedsRefresh()) {
            // Check if node was previously traversed with the same net exclusion when not a virtual platform
            // BEGIN_INDEED GRADLE-432
            if (previousMergedOverrideRules != null && previousMergedOverrideRules.equals(mergedOverrideRules)) {
            // END_INDEED
                boolean newConstraints = handleNewConstraints(discoveredEdges);
                boolean edgesToRecompute = handleEdgesToRecompute(discoveredEdges);
                if (!newConstraints && !edgesToRecompute) {
                    // Was previously traversed, and no change to the set of modules that are linked by outgoing edges.
                    // Don't need to traverse again, but hang on to the new filter since it may change the set of excluded artifacts.
                    LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                }
                // BEGIN_INDEED GRADLE-432
                previousMergedOverrideRules = mergedOverrideRules;
                // END_INDEED
                return;
            }
        }

        // Clear previous traversal state, if any
        // BEGIN_INDEED GRADLE-432
        if (previousMergedOverrideRules != null) {
        // END_INDEED
            removeOutgoingEdges();
            upcomingNoLongerPendingConstraints = null;
            edgesToRecompute = null;
            potentiallyActivatedConstraints = null;
        }

        // BEGIN_INDEED GRADLE-432
        visitDependencies(resolutionFilter, discoveredEdges, mergedOverrideRules);
        // END_INDEED

        visitOwners(discoveredEdges);
    }

    private void prepareToRecomputeEdge(EdgeState edgeToRecompute) {
        if (edgesToRecompute == null) {
            edgesToRecompute = Sets.newLinkedHashSet();
        }
        edgesToRecompute.add(edgeToRecompute);
        resolveState.onMoreSelected(this);
    }

    private boolean handleEdgesToRecompute(Collection<EdgeState> discoveredEdges) {
        if (edgesToRecompute != null) {
            discoveredEdges.addAll(edgesToRecompute);
            edgesToRecompute = null;
            return true;
        }
        return false;
    }

    private boolean handleNewConstraints(Collection<EdgeState> discoveredEdges) {
        if (upcomingNoLongerPendingConstraints != null) {
            // Previously traversed but new constraints no longer pending, so partial traversing
            visitAdditionalConstraints(discoveredEdges);
            return true;
        }
        return false;
    }

    private boolean isVirtualPlatformNeedsRefresh() {
        return virtualPlatformNeedsRefresh;
    }

    /**
     * Removes outgoing edges from no longer transitive node
     * Also process {@code belongsTo} if node still has edges at all.
     *
     * @param discoveredEdges In/Out parameter collecting dependencies or platforms
     */
    private void handleNonTransitiveNode(Collection<EdgeState> discoveredEdges) {
        // If node was previously traversed, need to remove outgoing edges.
        // BEGIN_INDEED GRADLE-432
        if (previousMergedOverrideRules != null) {
        // END_INDEED
            removeOutgoingEdges();
        }
        if (!incomingEdges.isEmpty()) {
            LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
            visitOwners(discoveredEdges);
        } else {
            LOGGER.debug("{} has no incoming edges. ignoring.", this);
        }
    }

    private DependencyState createDependencyState(DependencyMetadata md) {
        return new DependencyState(md, resolveState.getComponentSelectorConverter());
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    // BEGIN_INDEED GRADLE-432
    private void visitDependencies(ExcludeSpec resolutionFilter, Collection<EdgeState> discoveredEdges, MergedOverrideRules mergedOverrideRules) {
    // END_INDEED
        PendingDependenciesVisitor pendingDepsVisitor = resolveState.newPendingDependenciesVisitor();
        try {
            // BEGIN_INDEED GRADLE-432
            final IndeedResolverCore indeedResolverCore = new IndeedResolverCore(
                    mergedOverrideRules,
                    resolveState.getDependencySubstitutionApplicator()
            );
            for (DependencyMetadata dependency : metaData.getDependencies()) {
                if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
                    continue;
                }
                for (final DependencyState dependencyState : createDependencyStates(dependency, indeedResolverCore)) {
                    PendingDependenciesVisitor.PendingState pendingState = pendingDepsVisitor.maybeAddAsPendingDependency(this, dependencyState);
                    if (dependencyState.getDependency().isConstraint()) {
                        registerActivatingConstraint(dependencyState);
                    }
                    if (!pendingState.isPending()) {
                        createAndLinkEdgeState(dependencyState, discoveredEdges, mergedOverrideRules, pendingState == PendingDependenciesVisitor.PendingState.NOT_PENDING_ACTIVATING);
                    }
                }
            }
            previousMergedOverrideRules = mergedOverrideRules;
            // END_INDEED
        } finally {
            // If there are 'pending' dependencies that share a target with any of these outgoing edges,
            // then reset the state of the node that owns those dependencies.
            // This way, all edges of the node will be re-processed.
            pendingDepsVisitor.complete();
        }
    }

    private void registerActivatingConstraint(DependencyState dependencyState) {
        if (potentiallyActivatedConstraints == null) {
            potentiallyActivatedConstraints = ArrayListMultimap.create();
        }
        potentiallyActivatedConstraints.put(dependencyState.getModuleIdentifier(), dependencyState);
    }

    // BEGIN_INDEED GRADLE-432
    private List<DependencyState> createDependencyStates(
            final DependencyMetadata dependency,
            final IndeedResolverCore indeedResolverCore
    ) {
        final DependencyState baseState = createDependencyState(dependency);
        try {
            final ComponentSelector selector = dependency.getSelector();
            final ImmutableList.Builder<DependencyState> states = ImmutableList.builder();

            final List<IndeedResolverCore.Result> rewritten = indeedResolverCore.rewrite(selector);
            for (final IndeedResolverCore.Result result : rewritten) {
                if (rewritten.size() == 1) {
                    if (selector.equals(result.selector) && result.reasons.isEmpty()) {
                        states.add(baseState);
                    } else {
                        states.add(baseState.withTarget(result.selector, result.reasons));
                    }
                } else {
                    final ModuleComponentSelector freshSelector = DefaultModuleComponentSelector.newSelector(
                            DefaultModuleIdentifier.newId(
                                "indeed-combo",
                                selector.getDisplayName()+"-"+result.selector.getDisplayName()
                            ),
                            "indeed-combo"
                    );
                    final GradleDependencyMetadata freshMetadata = new GradleDependencyMetadata(
                            freshSelector,
                            dependency.getExcludes(),
                            dependency.isConstraint(),
                            dependency.getReason(),
                            false
                    );
                    final DependencyState state = createDependencyState(freshMetadata);
                    states.add(state.withTarget(result.selector, result.reasons));
                }
            }
            return states.build();
        } catch(Throwable e) {
            baseState.failure = new ModuleVersionResolveException(baseState.getRequested(), e);
            return ImmutableList.of(baseState);
        }
    }
    // END_INDEED

    // BEGIN_INDEED GRADLE-432
    private void createAndLinkEdgeState(DependencyState dependencyState, Collection<EdgeState> discoveredEdges, MergedOverrideRules mergedOverrideRules, boolean deferSelection) {
        EdgeState dependencyEdge = new EdgeState(this, dependencyState, mergedOverrideRules, resolveState);
    // END_INDEED
        dependencyEdge.getSelector().update(dependencyState);
        outgoingEdges.add(dependencyEdge);
        discoveredEdges.add(dependencyEdge);
        dependencyEdge.getSelector().use(deferSelection);
    }

    /**
     * Iterate over the dependencies originating in this node, adding only the constraints listed
     * in upcomingNoLongerPendingConstraints
     */
    private void visitAdditionalConstraints(Collection<EdgeState> discoveredEdges) {
        if (potentiallyActivatedConstraints == null) {
            return;
        }
        for (ModuleIdentifier module : upcomingNoLongerPendingConstraints) {
            Collection<DependencyState> dependencyStates = potentiallyActivatedConstraints.get(module);
            if (!dependencyStates.isEmpty()) {
                for (DependencyState dependencyState : dependencyStates) {
                    // BEGIN_INDEED GRADLE-432
                    createAndLinkEdgeState(dependencyState, discoveredEdges, previousMergedOverrideRules, false);
                    // END_INDEED
                }
            }
        }
        upcomingNoLongerPendingConstraints = null;
    }

    // BEGIN_INDEED GRADLE-432
    /* Calculate the override rules which apply to this module */
    private MergedOverrideRules getMergedOverrideRules(
            final List<EdgeState> transitiveEdges
    ) {
        // Filter out parents that originate from 'platform' edges, as they are dependency loops which should be allowed.
        // For instance, when using version alignment, we may wind up with a dependency branch that looks like this and should be allowed:
        // org.springframework:spring-beans:4.0.0 -> org.springframework:platform:4.1.0 -> org.springframework:spring-beans:4.1.0
        final Set<MergedOverrideRules> parentOverrideRules = new HashSet<>();
        for (final EdgeState edge : transitiveEdges) {
            if (edge.getDependencyMetadata() instanceof LenientPlatformDependencyMetadata) {
                continue;
            }
            parentOverrideRules.add(edge.getMergedOverrideRules());
        }

        return MergedOverrideRules.create(
                parentOverrideRules,
                localOverrideRules,
                this
        );
    }
    // END_INDEED

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     *
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(Collection<EdgeState> discoveredEdges) {
        ImmutableList<? extends ComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            PendingDependenciesVisitor visitor = resolveState.newPendingDependenciesVisitor();
            for (ComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier platformId = (ModuleComponentIdentifier) owner;
                    final ModuleComponentSelector cs = DefaultModuleComponentSelector.newSelector(platformId.getModuleIdentifier(), platformId.getVersion());

                    // There are 2 possibilities here:
                    // 1. the "platform" referenced is a real module, in which case we directly add it to the graph
                    // 2. the "platform" is a virtual, constructed thing, in which case we add virtual edges to the graph
                    addPlatformEdges(discoveredEdges, platformId, cs);
                    visitor.markNotPending(platformId.getModuleIdentifier());
                }
            }
            visitor.complete();
        }
    }

    private void addPlatformEdges(Collection<EdgeState> discoveredEdges, ModuleComponentIdentifier platformComponentIdentifier, ModuleComponentSelector platformSelector) {
        PotentialEdge potentialEdge = PotentialEdge.of(resolveState, this, platformComponentIdentifier, platformSelector, platformComponentIdentifier);
        ComponentResolveMetadata metadata = potentialEdge.metadata;
        VirtualPlatformState virtualPlatformState = null;
        if (metadata == null || metadata instanceof LenientPlatformResolveMetadata) {

            virtualPlatformState = potentialEdge.component.getModule().getPlatformState();
            virtualPlatformState.participatingModule(component.getModule());
        }
        if (metadata == null) {
            // the platform doesn't exist, so we're building a lenient one
            metadata = new LenientPlatformResolveMetadata(platformComponentIdentifier, potentialEdge.toModuleVersionId, virtualPlatformState, this, resolveState);
            potentialEdge.component.setMetadata(metadata);
            // And now let's make sure we do not have another version of that virtual platform missing its metadata
            potentialEdge.component.getModule().maybeCreateVirtualMetadata(resolveState);
        }
        if (virtualEdges == null) {
            virtualEdges = Lists.newArrayList();
        }
        EdgeState edge = potentialEdge.edge;
        virtualEdges.add(edge);
        discoveredEdges.add(edge);
        edge.getSelector().use(false);
    }

    private boolean hasAnyTransitiveEdge() {
        if (isRoot()) {
            return true;
        }
        return incomingEdges.stream().anyMatch(EdgeState::isTransitive);
    }

    void addIncomingEdge(EdgeState dependencyEdge) {
        if (!incomingEdges.contains(dependencyEdge)) {
            incomingEdges.add(dependencyEdge);
            incomingHash += dependencyEdge.hashCode();
            resolveState.onMoreSelected(this);
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount++;
            }
        }
    }

    void removeIncomingEdge(EdgeState dependencyEdge) {
        if (incomingEdges.remove(dependencyEdge)) {
            incomingHash -= dependencyEdge.hashCode();
            resolveState.onFewerSelected(this);
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount--;
            }
        }
    }

    @Override
    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    public void evict() {
        evicted = true;
        restartIncomingEdges();
    }

    boolean shouldIncludedInGraphResult() {
        return isSelected() && !component.getModule().isVirtualPlatform();
    }

    private static boolean isConstraint(EdgeState dependencyEdge) {
        return dependencyEdge.getDependencyMetadata().isConstraint();
    }

    private void removeOutgoingEdges() {
        if (!outgoingEdges.isEmpty()) {
            for (EdgeState outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release();
                outgoingDependency.maybeDecreaseHardEdgeCount(this);
            }
        }
        outgoingEdges.clear();
        if (virtualEdges != null) {
            for (EdgeState outgoingDependency : virtualEdges) {
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release();
            }
        }
        virtualEdges = null;
        // BEGIN_INDEED GRADLE-432
        previousMergedOverrideRules = null;
        // END_INDEED
        virtualPlatformNeedsRefresh = false;
    }

    public void restart(ComponentState selected) {
        // Restarting this configuration after conflict resolution.
        // If this configuration belongs to the select version, queue ourselves up for traversal.
        // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
        if (component == selected) {
            if (!evicted) {
                resolveState.onMoreSelected(this);
            }
        } else {
            if (!incomingEdges.isEmpty()) {
                restartIncomingEdges();
            }
        }
    }

    private void restartIncomingEdges() {
        if (incomingEdges.size() == 1) {
            EdgeState singleEdge = incomingEdges.get(0);
            singleEdge.restart();
        } else {
            for (EdgeState dependency : new ArrayList<>(incomingEdges)) {
                dependency.restart();
            }
        }
        clearIncomingEdges();
    }

    private void clearIncomingEdges() {
        incomingEdges.clear();
        incomingHash = 0;
        transitiveEdgeCount = 0;
    }

    public void deselect() {
        removeOutgoingEdges();
    }

    void prepareForConstraintNoLongerPending(ModuleIdentifier moduleIdentifier) {
        if (upcomingNoLongerPendingConstraints == null) {
            upcomingNoLongerPendingConstraints = Sets.newLinkedHashSet();
        }
        upcomingNoLongerPendingConstraints.add(moduleIdentifier);
        // Trigger a replay on this node, to add new constraints to graph
        resolveState.onFewerSelected(this);
    }

    void markForVirtualPlatformRefresh() {
        assert component.getModule().isVirtualPlatform();
        virtualPlatformNeedsRefresh = true;
        resolveState.onFewerSelected(this);
    }

    public ImmutableAttributesFactory getAttributesFactory() {
        return resolveState.getAttributesFactory();
    }

    /**
     * Invoked when this node is back to being a pending dependency.
     * There may be some incoming edges left at that point, but they must all be coming from constraints.
     */
    public void clearConstraintEdges(PendingDependencies pendingDependencies, NodeState backToPendingSource) {
        for (EdgeState incomingEdge : incomingEdges) {
            assert isConstraint(incomingEdge);
            NodeState from = incomingEdge.getFrom();
            if (from != backToPendingSource) {
                // Only remove edges that come from a different node than the source of the dependency going back to pending
                // The edges from the "From" will be removed first
                incomingEdge.getSelector().release();
                from.getOutgoingEdges().remove(incomingEdge);
            }
            pendingDependencies.addNode(from);
        }
        clearIncomingEdges();
    }

    void forEachCapability(Action<? super Capability> action) {
        List<? extends Capability> capabilities = metaData.getCapabilities().getCapabilities();
        // If there's more than one node selected for the same component, we need to add
        // the implicit capability to the list, in order to make sure we can discover conflicts
        // between variants of the same module. Note that the fact the implicit capability is
        // in general not included is not a bug but a performance optimization
        if (capabilities.isEmpty() && component.hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
            action.execute(component.getImplicitCapability());
        } else {
            // The isEmpty check is not required, might look innocent, but Guava's performance bad for an empty immutable list
            // because it still creates an inner class for an iterator, which delegates to an Array iterator, which does... nothing.
            // so just adding this check has a significant impact because most components do not declare any capability
            if (!capabilities.isEmpty()) {
                for (Capability capability : capabilities) {
                    action.execute(capability);
                }
            }
        }
    }

    public Capability findCapability(String group, String name) {
        Capability onComponent = component.findCapability(group, name);
        if (onComponent != null) {
            return onComponent;
        }
        List<? extends Capability> capabilities = metaData.getCapabilities().getCapabilities();
        if (!capabilities.isEmpty()) { // Not required, but Guava's performance bad for an empty immutable list
            for (Capability capability : capabilities) {
                if (capability.getGroup().equals(group) && capability.getName().equals(name)) {
                    return capability;
                }
            }
        }
        return null;
    }

    public boolean isAttachedToVirtualPlatform() {
        for (EdgeState incomingEdge : incomingEdges) {
            if (incomingEdge.getDependencyMetadata() instanceof LenientPlatformDependencyMetadata) {
                return true;
            }
        }
        return false;
    }

    boolean hasShadowedCapability() {
        for (Capability capability : metaData.getCapabilities().getCapabilities()) {
            if (capability instanceof ShadowedCapability) {
                return true;
            }
        }
        return false;
    }

    boolean isSelectedByVariantAwareResolution() {
        // the order is strange logically but here for performance optimization
        return selectedByVariantAwareResolution && isSelected();
    }

    void makePending(EdgeState edgeState) {
        outgoingEdges.remove(edgeState);
        edgeState.getSelector().release();
        registerActivatingConstraint(edgeState.getDependencyState());
    }

    ImmutableAttributes desugar(ImmutableAttributes attributes) {
        return resolveState.desugar(attributes);
    }

    public ResolvedVariantResult getResolvedVariant() {
        if (cachedVariantResult != null) {
            return cachedVariantResult;
        }
        DisplayName name = Describables.of(metaData.getName());
        List<? extends Capability> capabilities = metaData.getCapabilities().getCapabilities();
        AttributeContainer attributes = desugar(metaData.getAttributes());
        List<Capability> resolvedVariantCapabilities = capabilities.isEmpty() ? Collections.singletonList(component.getImplicitCapability()) : ImmutableList.copyOf(capabilities);
        cachedVariantResult = new DefaultResolvedVariantResult(
            name,
            attributes,
            resolvedVariantCapabilities
        );
        return cachedVariantResult;
    }
}
