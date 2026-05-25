package com.agape;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class DenominationCompatibilityTest {

    // --- normalizeDenomination ---

    @Test
    public void normalizeDenomination_exactMatchReturnsCanonical() {
        String result = DenominationCompatibility.normalizeDenomination("Catholic Church");
        assertEquals("Exact match should return the canonical name",
            "Catholic Church", result);
    }

    @Test
    public void normalizeDenomination_caseInsensitiveMatch() {
        String result = DenominationCompatibility.normalizeDenomination("catholic church");
        assertEquals("Case-insensitive match should return canonical name",
            "Catholic Church", result);
    }

    // Slight typos (≤1–2 edits) should be corrected
    @Test
    public void normalizeDenomination_oneCharTypoIsCorreected() {
        // "Baptit" is "Baptist" with the final 's' missing
        assertEquals("Baptist", DenominationCompatibility.normalizeDenomination("Baptit"));
    }

    @Test
    public void normalizeDenomination_oneCharSwapIsCorreected() {
        // "Babtist" swaps 'p' and 'b'
        assertEquals("Baptist", DenominationCompatibility.normalizeDenomination("Babtist"));
    }

    @Test
    public void normalizeDenomination_oneCharTypoInLongerWord() {
        // "Penticostal" inserts 'i' before the 'e'
        assertEquals("Pentecostal", DenominationCompatibility.normalizeDenomination("Penticostal"));
    }

    @Test
    public void normalizeDenomination_missingLetterInMultiWordKey() {
        // "Presbeterian Church" — one vowel off in "Presbyterian"
        String result = DenominationCompatibility.normalizeDenomination("Presbeterian Church");
        assertEquals("Presbyterian Church", result);
    }

    // Grotesque / intentional misspellings must be preserved
    @Test
    public void normalizeDenomination_grotesqueMisspellingPreserved() {
        // Far too many edits to be a simple typo
        String weird = "Cathloicism";
        String result = DenominationCompatibility.normalizeDenomination(weird);
        assertEquals("Grotesque misspelling should be preserved as-is", weird, result);
    }

    @Test
    public void normalizeDenomination_unknownDenominationPreserved() {
        String custom = "Grace Covenant Fellowship";
        String result = DenominationCompatibility.normalizeDenomination(custom);
        assertEquals("Unrecognized denomination should be preserved as-is", custom, result);
    }

    @Test
    public void normalizeDenomination_shortFragmentNotMatchedToLongerKey() {
        // "Church" is a substring of many keys but should NOT auto-correct to one of them
        String result = DenominationCompatibility.normalizeDenomination("Church");
        assertEquals("Generic fragment should not be auto-corrected", "Church", result);
    }

    @Test
    public void normalizeDenomination_nullReturnsNull() {
        assertNull(DenominationCompatibility.normalizeDenomination(null));
    }

    @Test
    public void normalizeDenomination_emptyReturnsEmpty() {
        assertEquals("", DenominationCompatibility.normalizeDenomination(""));
        assertEquals("   ", DenominationCompatibility.normalizeDenomination("   "));
    }

    @Test
    public void normalizeDenomination_unknownInputReturnedAsIs() {
        String unknown = "My Custom Church XYZ";
        String result = DenominationCompatibility.normalizeDenomination(unknown);
        assertNotNull("Unknown input should still return a non-null value", result);
        assertFalse("Unknown input should return a non-empty value", result.trim().isEmpty());
    }

    // --- getCompatibleDenominations ---

    @Test
    public void getCompatibleDenominations_knownDenominationReturnsSuggestions() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations("Catholic Church", false);
        assertNotNull("Results should not be null", results);
        assertFalse("Catholic Church should have compatible denominations", results.isEmpty());
    }

    @Test
    public void getCompatibleDenominations_doesNotIncludeSelf() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations("Catholic Church", false);
        assertFalse("Results should not contain the denomination itself",
            results.contains("Catholic Church"));
    }

    @Test
    public void getCompatibleDenominations_withReasons_containsDashSeparator() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations("Catholic Church", true);
        assertFalse("Results with reasons should not be empty", results.isEmpty());
        // At least one result should contain a reason (indicated by " - ")
        boolean anyHasReason = results.stream().anyMatch(r -> r.contains(" - "));
        assertTrue("At least one result should include a reason string", anyHasReason);
    }

    @Test
    public void getCompatibleDenominations_withoutReasons_containsNoDashSeparator() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations("Catholic Church", false);
        for (String r : results) {
            assertFalse("Results without reasons should not contain ' - ' separator", r.contains(" - "));
        }
    }

    @Test
    public void getCompatibleDenominations_nullInputReturnsEmptyList() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations(null, false);
        assertNotNull(results);
        assertTrue("Null input should return empty list", results.isEmpty());
    }

    @Test
    public void getCompatibleDenominations_emptyInputReturnsEmptyList() {
        List<String> results = DenominationCompatibility.getCompatibleDenominations("", false);
        assertNotNull(results);
        assertTrue("Empty input should return empty list", results.isEmpty());
    }

    @Test
    public void getCompatibleDenominations_caseInsensitiveInput() {
        List<String> lower = DenominationCompatibility.getCompatibleDenominations("catholic church", false);
        List<String> exact = DenominationCompatibility.getCompatibleDenominations("Catholic Church", false);
        assertEquals("Results should match regardless of input case", exact.size(), lower.size());
    }

    @Test
    public void getCompatibleDenominations_severalKnownDenominationsHaveSuggestions() {
        String[] denominations = {
            "Catholic Church",
            "Eastern Orthodox",
            "Baptist",
            "Anglican",
            "Lutheran",
            "Methodist"
        };
        for (String denom : denominations) {
            List<String> results = DenominationCompatibility.getCompatibleDenominations(denom, false);
            assertFalse("Should have suggestions for: " + denom, results.isEmpty());
        }
    }
}
