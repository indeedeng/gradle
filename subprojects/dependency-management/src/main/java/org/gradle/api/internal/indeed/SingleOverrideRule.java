package org.gradle.api.internal.indeed;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.apache.ivy.plugins.matcher.AnyMatcher;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.Matcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class SingleOverrideRule extends OverrideRule {

    public final String groupStr;
    public final String nameStr;
    public final String matcher;
    public final String rev;

    private final Matcher groupMatcher;
    private final Matcher nameMatcher;

    public SingleOverrideRule(
            @Nullable String groupStr,
            @Nullable String nameStr,
            final String matcher,
            final String rev
    ) {
        this.groupStr = (groupStr == null) ? "" : groupStr;
        this.nameStr = (nameStr == null) ? "" : nameStr;
        this.matcher = matcher;
        this.rev = rev;

        final PatternMatcher patternMatcher = matcher == null ? ExactPatternMatcher.INSTANCE : PatternMatchers.getInstance().getMatcher(matcher);
        this.groupMatcher = StringUtils.isEmpty(groupStr) ? AnyMatcher.INSTANCE : patternMatcher.getMatcher(groupStr);
        this.nameMatcher = StringUtils.isEmpty(nameStr) ? AnyMatcher.INSTANCE : patternMatcher.getMatcher(nameStr);
    }

    @Nullable
    @Override
    public Result getOverride(final ModuleIdentifier id) {
        if (groupMatcher.matches(id.getGroup()) && nameMatcher.matches(id.getName())) {
            final String reason;
            if (EXCLUDE_VERSION.equals(rev)) {
                reason = "<exclude> in " + getSource();
            } else {
                reason = "<override> in " + getSource();
            }
            return new Result(rev, false, ImmutableSet.of(reason));
        }
        return null;
    }

    private static final String FIELD_DELIM = "~!!!~";
    public String serialize() {
        return Joiner.on(FIELD_DELIM).join(new String[]{
                groupStr,
                nameStr,
                matcher,
                rev
        });
    }
    public static SingleOverrideRule parse(final String input) {
        final List<String> fields = Splitter.on(FIELD_DELIM).splitToList(input);
        if (fields.size() != 4) throw new RuntimeException("Invalid encoded IndeedHackOverrideRule: " + input);
        return new SingleOverrideRule(
                fields.get(0),
                fields.get(1),
                fields.get(2),
                fields.get(3)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleOverrideRule that = (SingleOverrideRule) o;
        if (hashCode() != that.hashCode()) {
            return false;
        }
        return Objects.equals(groupStr, that.groupStr) &&
                Objects.equals(nameStr, that.nameStr) &&
                Objects.equals(matcher, that.matcher) &&
                Objects.equals(rev, that.rev);
    }

    private boolean cachedHashCode = false;
    private int hashCode;

    @Override
    public int hashCode() {
        if (!cachedHashCode) {
            cachedHashCode = true;
            hashCode = Objects.hash(groupStr, nameStr, matcher, rev);
        }
        return hashCode;
    }

    public SingleOverrideRule withSelector(final ModuleComponentSelector selector) {
        final SingleOverrideRule out = new SingleOverrideRule(
                selector.getGroup(),
                selector.getModule(),
                matcher,
                selector.getVersion()
        );
        out.setSource(getSource());
        return out;
    }
}
