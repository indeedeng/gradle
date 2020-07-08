package org.gradle.api.internal.indeed;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Redirects requests made to certain configuration names
 */
public class IndeedConfigurationRedirector {

    private static Set<String> legacyIvyConfNames = ImmutableSet.of(
            "closure",
            "common",
            "compileinterfaces",
            "compile-interfaces",
            "interfaces",
            "master",
            "messagebundle",
            "po",
            "proto",
            "source",
            "sources",
            "soy",
            "testbase",
            "tests",
            "withPo",
            "withWeb"
    );

    private static Set<String> badIvyToMavenConfNames = ImmutableSet.of(
            "master",
            "compile",
            "provided",
            "runtime",
            "test",
            "sources"
    );

    public enum FROM_TYPE {
        IVY, // from an ivy.xml
        MAVEN, // from a maven pom
        GRADLE // from a local gradlefile
    }

    // BEGIN_INDEED GRADLE-445
    @Nullable
    public static ConfigurationMetadata shouldRedirect(
            final FROM_TYPE fromType,
            final ComponentResolveMetadata targetComponent,
            final String targetConfiguration
    ) {
        final ConfigurationMetadata targetSelected = targetComponent.getConfiguration(targetConfiguration);
        final ConfigurationMetadata targetDefault = targetComponent.getConfiguration(Dependency.DEFAULT_CONFIGURATION);

        // Requests to ivy or maven modules for legacy ivy names (that no longer exist) should be redirected to default
        if (targetComponent instanceof MavenModuleResolveMetadata || targetComponent instanceof IvyModuleResolveMetadata) {
            if (legacyIvyConfNames.contains(targetConfiguration) && targetSelected == null) {
                return targetDefault;
            }
        }

        // Request from ivy to maven should sometimes redirect to the default conf.
        // This is typically useful if an ivy module migrated to gradle, and clients were depending on it with conf="test->test".
        // After the migration, these dependencies will effectively turn into conf="test->default" due to this rule.
        if (fromType == FROM_TYPE.IVY && targetComponent instanceof MavenModuleResolveMetadata) {
            if (badIvyToMavenConfNames.contains(targetConfiguration)) {
                return targetDefault;
            }
        }

        // Any requests from non-ivy to an ivy "default" configuration should be redirected to "interfaces" if available.
        // If "interfaces" is not available, we redirect to DEFAULT anyways, to prevent maven poms from scope-matching against indeed
        //    ivy modules. (By default, maven "compile" scope would pull in ivy "compile" scope)
        if (fromType != FROM_TYPE.IVY) {
            if (targetComponent instanceof IvyModuleResolveMetadata) {
                if (targetConfiguration.equals(Dependency.DEFAULT_CONFIGURATION)) {
                    final ConfigurationMetadata targetInterfaces = targetComponent.getConfiguration("interfaces");
                    if (targetInterfaces != null && !targetInterfaces.getArtifacts().isEmpty()) {
                        return targetInterfaces;
                    }
                }
            }
        }

        // Prevent maven poms from scope-matching against indeed ivy modules
        // Indeed ivy modules are designed to always be consumed using the default conf (not compile / runtime like maven expects)
        if (fromType == FROM_TYPE.MAVEN) {
            if (targetComponent instanceof IvyModuleResolveMetadata) {
                return targetDefault;
            }
        }

        return null;
    }
    // END_INDEED
}
