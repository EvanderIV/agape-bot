package com.agape;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Regression tests for the pure preference-extraction engine.
 *
 * extractPreferences turns free text (dealbreaker lists, decline reasons,
 * lookFor fields) into signed "+tag"/"-tag" strings. The sign logic:
 *   dealbreaker context (invert=false): trait present → "-", negated → "+"
 *   lookFor context     (invert=true):  trait present → "+", negated → "-"
 */
public class UserInsightsManagerTest {

    @Test
    public void dealbreakerTraitProducesNegativeTag() {
        List<String> tags = UserInsightsManager.extractPreferences("smoking", false);
        assertEquals(1, tags.size());
        assertEquals("-smoking", tags.get(0));
    }

    @Test
    public void lookForTraitProducesPositiveTag() {
        List<String> tags = UserInsightsManager.extractPreferences("loyal", true);
        assertEquals(1, tags.size());
        assertEquals("+loyal", tags.get(0));
    }

    @Test
    public void negatedLookForTraitProducesNegativeTag() {
        List<String> tags = UserInsightsManager.extractPreferences("not religious", true);
        assertTrue(tags.contains("-religion"));
    }

    @Test
    public void negatedDealbreakerProducesPositiveTag() {
        // "hates dogs" in a dealbreaker list means the author WANTS dogs
        List<String> tags = UserInsightsManager.extractPreferences("hates dogs", false);
        assertTrue(tags.contains("+dogs"));
    }

    @Test
    public void apostropheContractionsAreNotDetectedAsNegation() {
        // Known quirk: punctuation (including apostrophes) is stripped before the
        // negation check, so "doesn't" becomes "doesn t" and does NOT negate.
        List<String> tags = UserInsightsManager.extractPreferences("doesn't like dogs", false);
        assertTrue(tags.contains("-dogs"));
    }

    @Test
    public void multipleCommaSeparatedItemsEachProduceTags() {
        List<String> tags = UserInsightsManager.extractPreferences("smoking, lying, drama", false);
        assertTrue(tags.contains("-smoking"));
        assertTrue(tags.contains("-lying"));
        assertTrue(tags.contains("-dramatic"));
    }

    @Test
    public void newlineSeparatedItemsAreSplit() {
        List<String> tags = UserInsightsManager.extractPreferences("smoking\nalcohol", false);
        assertTrue(tags.contains("-smoking"));
        assertTrue(tags.contains("-alcohol"));
    }

    @Test
    public void duplicateTagsAreDeduplicated() {
        List<String> tags = UserInsightsManager.extractPreferences("smoking, smokes a lot", false);
        assertEquals(1, tags.size());
        assertEquals("-smoking", tags.get(0));
    }

    @Test
    public void eachItemMatchesAtMostOneRule() {
        // One list item stops at the first matching rule
        List<String> tags = UserInsightsManager.extractPreferences("smoking", false);
        assertEquals(1, tags.size());
    }

    @Test
    public void nullAndEmptyInputsReturnEmptyList() {
        assertTrue(UserInsightsManager.extractPreferences(null, false).isEmpty());
        assertTrue(UserInsightsManager.extractPreferences("", false).isEmpty());
        assertTrue(UserInsightsManager.extractPreferences("   ", true).isEmpty());
    }

    @Test
    public void unmatchedTextProducesNoTags() {
        assertTrue(UserInsightsManager.extractPreferences("enjoys philately", false).isEmpty());
    }

    @Test
    public void defaultOverloadUsesDealbreakerDirection() {
        List<String> tags = UserInsightsManager.extractPreferences("smoking");
        assertEquals("-smoking", tags.get(0));
    }

    @Test
    public void nonPrefixCountsAsNegation() {
        // "non-christian" as a dealbreaker → author wants religion
        List<String> tags = UserInsightsManager.extractPreferences("non-christian", false);
        assertTrue(tags.contains("+religion"));
    }

    // --- Insight lookups for unknown users are safe no-ops ---

    @Test
    public void getInsightsForUnknownUserReturnsNull() {
        assertNull(UserInsightsManager.getInsights("test_user_does_not_exist_xyz"));
    }
}
