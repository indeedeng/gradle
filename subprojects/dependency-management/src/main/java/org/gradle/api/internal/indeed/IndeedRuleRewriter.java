package org.gradle.api.internal.indeed;

import com.google.common.base.Objects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.internal.component.external.descriptor.DefaultExclude;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.List;

// Rewrites Excludes using rewrite rules from the resolutionStrategy
public class IndeedRuleRewriter {
    // BEGIN_INDEED GRADLE-436 <PS: This function is not used anymore.>
    public static ImmutableList<ExcludeMetadata> rewriteExcludes(
            final DependencySubstitutionApplicator rules,
            final Iterable<ExcludeMetadata> inputs
    ) {
        final IndeedResolverCore core = new IndeedResolverCore(null, rules);
        final ImmutableList.Builder<ExcludeMetadata> outputs = ImmutableList.builder();
        for (final ExcludeMetadata input : inputs) {
            if (! (input instanceof Exclude)) {
                outputs.add(input);
                continue;
            }
            final Exclude exclude = (Exclude)input;

            try {
                final List<IndeedResolverCore.Result> results = core.rewrite(DefaultModuleComponentSelector.newSelector(
                        DefaultModuleIdentifier.newId(
                                exclude.getModuleId().getGroup(),
                            exclude.getModuleId().getName()
                        ),
                        OverrideRule.EXCLUDE_VERSION
                ));

                for (final IndeedResolverCore.Result result : results) {
                    if (result.selector instanceof ModuleComponentSelector) {
                        final ModuleComponentSelector mcs = (ModuleComponentSelector) result.selector;
                        final ModuleIdentifier resultId = DefaultModuleIdentifier.newId(mcs.getGroup(), mcs.getModule());
                        if (!Objects.equal(resultId, exclude.getModuleId())) {
                            outputs.add(new DefaultExclude(
                                    resultId,
                                    exclude.getArtifact(),
                                    FluentIterable.from(exclude.getConfigurations()).toArray(String.class),
                                    exclude.getMatcher()
                            ));
                            continue;
                        }
                    }
                    outputs.add(exclude);
                }
            } catch(Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return outputs.build();
    }
    // END_INDEED

    public static ImmutableList<SingleOverrideRule> rewriteOverrides(
            final DependencySubstitutionApplicator rules,
            final Iterable<SingleOverrideRule> inputs
    ) {
        final IndeedResolverCore core = new IndeedResolverCore(null, rules);
        final ImmutableList.Builder<SingleOverrideRule> outputs = ImmutableList.builder();
        for (final SingleOverrideRule input : inputs) {
            try {
                final ModuleComponentSelector oldSelector = DefaultModuleComponentSelector.newSelector(
                        DefaultModuleIdentifier.newId(
                                input.groupStr,
                                input.nameStr
                        ),
                        input.rev
                );
                final List<IndeedResolverCore.Result> results = core.rewrite(oldSelector);

                for (final IndeedResolverCore.Result result : results) {
                    if (!Objects.equal(result.selector, oldSelector)
                            && result.selector instanceof ModuleComponentSelector) {
                        ModuleComponentSelector newSelector = (ModuleComponentSelector)result.selector;
                        // Ensure excludes are not rewritten to overrides
                        if (oldSelector.getVersion().equals(OverrideRule.EXCLUDE_VERSION) && !newSelector.getVersion().equals(OverrideRule.EXCLUDE_VERSION)) {
                            newSelector = DefaultModuleComponentSelector.newSelector(newSelector.getModuleIdentifier(), OverrideRule.EXCLUDE_VERSION);
                        }
                        outputs.add(input.withSelector(newSelector));
                    } else {
                        outputs.add(input);
                    }
                }
            } catch(Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return outputs.build();
    }
}
