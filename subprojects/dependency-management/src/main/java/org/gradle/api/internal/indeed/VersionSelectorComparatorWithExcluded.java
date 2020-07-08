package org.gradle.api.internal.indeed;

import com.google.common.base.Objects;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

/**
* Compares VersionSelectors to find which one is higher
* Supports comparing between Exact, VersionRange, SubVersion, and Latest
* Also supports treating "excluded" as either high or low
*/
// BEGIN_INDEED GRADLE-436
public class VersionSelectorComparatorWithExcluded {

    private final static VersionParser versionParser = new VersionParser();
    private final static VersionComparator versionComparator = new DefaultVersionComparator();
    private final static DefaultVersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, versionParser);

    public int compare(final String a, final String b, final boolean excludePreferred) {
        if (Objects.equal(a,b)) {
            return 0;
        }
        if (StringUtils.equals(a,MergedOverrideRules.EXCLUDE_VERSION)) {
            return excludePreferred ? 1 : -1;
        }
        if (StringUtils.equals(b,MergedOverrideRules.EXCLUDE_VERSION)) {
            return excludePreferred ? -1 : 1;
        }
        final VersionSelector selectorA = versionSelectorScheme.parseSelector(a);
        final VersionSelector selectorB = versionSelectorScheme.parseSelector(b);
        return versionComparator.asVersionComparator().compare(
                versionParser.transform(convertToVersionStringForComparison(selectorA)),
                versionParser.transform(convertToVersionStringForComparison(selectorB))
        );
    }

    private final static String MAX_VERSION = "999999999";

    private String convertToVersionStringForComparison(final VersionSelector selector) {
        if (selector instanceof VersionRangeSelector) {
            final String upperBound = ((VersionRangeSelector) selector).getUpperBound();
            if (upperBound == null) {
                return MAX_VERSION;
            } else if (((VersionRangeSelector) selector).isUpperInclusive()) {
                return upperBound;
            } else {
                return upperBound + ".dev";
            }
        }
        if (selector instanceof SubVersionSelector) {
            final String prefix = ((SubVersionSelector) selector).getPrefix();
            if (StringUtils.isEmpty(prefix)) {
                return MAX_VERSION;
            }
            final char ch = prefix.charAt(prefix.length()-1);
            if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                return prefix+MAX_VERSION;
            }
            return prefix+"."+MAX_VERSION;
        }
        if (selector instanceof LatestVersionSelector) {
            return MAX_VERSION;
        }
        return selector.getSelector();
    }

}
// END_INDEED GRADLE-436
