package org.gradle.api.internal.indeed;

import org.gradle.api.artifacts.ModuleIdentifier;

import javax.annotation.Nullable;
import java.util.Collection;

public abstract class OverrideRule {

    public static String EXCLUDE_VERSION = "exclude";
    private Object source;

    @Nullable
    public abstract Result getOverride(final ModuleIdentifier id);

    public Object getSource() {
        return source;
    }

    public void setSource(final Object source) {
        this.source = source;
    }

    public static class Result {
        public final String version;
        public final boolean hasClearPathToRoot;
        public final Collection<String> reasons;

        public Result(final String version, final boolean hasClearPathToRoot, final Collection<String> reasons) {
            this.version = version;
            this.hasClearPathToRoot = hasClearPathToRoot;
            this.reasons = reasons;
        }

        public boolean isExclude() {
            return EXCLUDE_VERSION.equals(version);
        }
    }
}
