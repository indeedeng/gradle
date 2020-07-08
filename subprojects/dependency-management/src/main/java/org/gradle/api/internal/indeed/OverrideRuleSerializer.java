package org.gradle.api.internal.indeed;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;

import java.util.List;
import java.util.Map;

public class OverrideRuleSerializer {
    private static final NamespaceId STORAGE_KEY = new NamespaceId("INDEED_HACK", "OVERRIDE_RULES");
    private static final String ENTRY_DELIM = "~///~";
    public static String serialize(final List<SingleOverrideRule> list) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i != 0) out.append(ENTRY_DELIM);
            out.append(list.get(i).serialize());
        }
        return out.toString();
    }
    public static List<SingleOverrideRule> parse(final String input, final Object source) {
        final List<SingleOverrideRule> rules = Lists.newArrayList();
        if (!input.isEmpty()) {
            for (final String entry : Splitter.on(ENTRY_DELIM).split(input)) {
                final SingleOverrideRule rule = SingleOverrideRule.parse(entry);
                rule.setSource(source);
                rules.add(rule);
            }
        }
        return rules;
    }
    @SuppressWarnings("unchecked")
    public static void serializeToExtraInfo(final Map extraInfo, final List<SingleOverrideRule> rules) {
        extraInfo.put(STORAGE_KEY, serialize(rules));
    }
    @SuppressWarnings("unchecked")
    private static List<SingleOverrideRule> parseFromExtraInfo(final Map extraInfo, final Object source) {
        if (extraInfo.containsKey(STORAGE_KEY)) {
            return parse((String)extraInfo.get(STORAGE_KEY), source);
        } else {
            return Lists.newArrayList();
        }
    }

    public static List<SingleOverrideRule> parseFromNode(
            final ComponentResolveMetadata componentMetadata,
            final ConfigurationMetadata configurationMetadata
    ) {
        final Object source = componentMetadata.getId();
        final List<SingleOverrideRule> overrides = Lists.newArrayList();
        // BEGIN_INDEED GRADLE-432
        if (componentMetadata instanceof DefaultIvyModuleResolveMetadata) {
            final DefaultIvyModuleResolveMetadata ivyMetadata = (DefaultIvyModuleResolveMetadata) componentMetadata;
            overrides.addAll(parseFromExtraInfo(ivyMetadata.getExtraAttributes(), source));

            for (DependencyMetadata dependency : configurationMetadata.getDependencies()) {
                if (dependency.getSelector() instanceof ModuleComponentSelector) {
                    boolean forced = false;
                    for (final ExcludeMetadata exclude : dependency.getExcludes()) {
                        if ("FORCED_MARKER".equals(exclude.getModuleId().getName())) {
                            forced = true;
                            break;
                        }
                    }
                    if (forced) {
                        final ModuleComponentSelector selector = (ModuleComponentSelector) dependency.getSelector();
                        final SingleOverrideRule rule = new SingleOverrideRule(
                                selector.getGroup(),
                                selector.getModule(),
                                "exact",
                                selector.getVersion()
                        );
                        rule.setSource(source);
                        overrides.add(rule);
                    }
                }
            }
        }
        // END_INDEED

        // BEGIN_INDEED GRADLE-433
        if (componentMetadata instanceof DefaultMavenModuleResolveMetadata) {
            for (DependencyMetadata dependency : configurationMetadata.getDependencies()) {
                if (dependency.getSelector() instanceof ModuleComponentSelector) {
                    final ModuleComponentSelector selector = (ModuleComponentSelector) dependency.getSelector();
                    final SingleOverrideRule rule = new SingleOverrideRule(
                            selector.getGroup(),
                            selector.getModule(),
                            "exact",
                            selector.getVersion()
                    );
                    rule.setSource(source);
                    overrides.add(rule);
                }
            }
        }
        // END_INDEED

        // BEGIN_INDEED GRADLE-436
        for (final ExcludeMetadata exclude : configurationMetadata.getExcludes()) {
            if (exclude.getArtifact() == null) {
                final SingleOverrideRule rule = new SingleOverrideRule(
                        exclude.getModuleId().getGroup(),
                        exclude.getModuleId().getName(),
                        exclude.getMatcher(),
                        OverrideRule.EXCLUDE_VERSION
                );
                rule.setSource(source);
                overrides.add(rule);
            }
        }
        // END_INDEED
        return overrides;
    }

    // BEGIN_INDEED GRADLE-436
    public static List<SingleOverrideRule> parseFromEdge(
            final DependencyMetadata edgeMetadata,
            final Object source
    ) {
        final List<SingleOverrideRule> overrides = Lists.newArrayList();
        for (final ExcludeMetadata exclude : edgeMetadata.getExcludes()) {
            if (exclude.getArtifact() == null && !"FORCED_MARKER".equals(exclude.getModuleId().getName())) {
                final SingleOverrideRule rule = new SingleOverrideRule(
                        exclude.getModuleId().getGroup(),
                        exclude.getModuleId().getName(),
                        exclude.getMatcher(),
                        OverrideRule.EXCLUDE_VERSION
                );
                rule.setSource(source);
                overrides.add(rule);
            }
        }
        return overrides;
    }
    // END_INDEED GRADLE-436
}
