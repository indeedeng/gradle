package org.gradle.api.internal.indeed;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VersionSelectorComparatorWithExcludedTest {

    @Test
    public void compare() {
        assertTrue(compare("1", "0") > 0);
        assertTrue(compare("[0,)", "10") > 0);
        assertTrue(compare("(1,)", "10") > 0);
        assertTrue(compare("3+", "3") > 0);
        assertTrue(compare("3.+", "3") > 0);
        assertTrue(compare("3.+", "3.9") > 0);
        assertTrue(compare("latest.integration", "3.9") > 0);
        assertTrue(compare("latest.integration", "9999") > 0);
        assertTrue(compare("latest.integration", "latest.integration") == 0);
        assertTrue(compare("latest.integration", "(0,)") == 0);
        assertTrue(compare("3+", "(0,3)") > 0);
        assertTrue(compare("3+", "(0,3]") > 0);
        assertTrue(compare("2+", "(0,3)") < 0);
        assertTrue(compare("2+", "(0,3]") < 0);
        assertTrue(compare("3", "(0,3]") == 0);
        assertTrue(compare("3", "(0,3)") > 0);
        assertTrue(compare("4", "3+") > 0);
    }

    public int compare(final String a, final String b) {
        return compare(a, b, false);
    }

    public int compare(final String a, final String b, final boolean excludePreferred) {
        final VersionSelectorComparatorWithExcluded comparator = new VersionSelectorComparatorWithExcluded();
        return comparator.compare(a, b, excludePreferred);
    }
}