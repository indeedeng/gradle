package org.gradle.api.internal.indeed;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Combines two artifact sets
 */

public class CompositeArtifactSet implements ArtifactSet {

    private final Collection<ArtifactSet> sets;

    public CompositeArtifactSet(final Collection<ArtifactSet> sets) {
        this.sets = sets;
    }

    @Override
    public ResolvedArtifactSet select(final Spec<? super ComponentIdentifier> componentFilter, final VariantSelector selector) {
        final List<ResolvedArtifactSet> resolved = new ArrayList<ResolvedArtifactSet>();
        for (final ArtifactSet set : sets) {
            resolved.add(set.select(componentFilter, selector));
        }
        return CompositeResolvedArtifactSet.of(resolved);
    }
}
