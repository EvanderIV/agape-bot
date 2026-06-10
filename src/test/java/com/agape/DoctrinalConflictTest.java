package com.agape;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Regression tests for doctrinal-conflict detection and alias/blend resolution.
 * These warnings appear on match previews and influence matchmaker decisions.
 */
public class DoctrinalConflictTest {

    private static boolean hasIssue(List<DenominationCompatibility.DoctrinalConflict> conflicts, String issue) {
        for (DenominationCompatibility.DoctrinalConflict c : conflicts) {
            if (issue.equals(c.issue)) return true;
        }
        return false;
    }

    @Test
    public void baptistAndCatholicConflictOnBaptismAndJustification() {
        List<DenominationCompatibility.DoctrinalConflict> conflicts =
            DenominationCompatibility.getDoctrinalConflicts("Baptist", "Catholic");
        assertTrue(hasIssue(conflicts, "Baptism"));
        assertTrue(hasIssue(conflicts, "Justification"));
        assertEquals(2, conflicts.size());
    }

    @Test
    public void adventistAndBaptistConflictOnDayOfWorshipOnly() {
        List<DenominationCompatibility.DoctrinalConflict> conflicts =
            DenominationCompatibility.getDoctrinalConflicts("Seventh-day Adventist", "Baptist");
        assertTrue(hasIssue(conflicts, "Day of Worship"));
        assertEquals(1, conflicts.size());
    }

    @Test
    public void onenessPentecostalAndBaptistConflictOnTrinity() {
        List<DenominationCompatibility.DoctrinalConflict> conflicts =
            DenominationCompatibility.getDoctrinalConflicts(
                "United Pentecostal Church International", "Baptist");
        assertTrue(hasIssue(conflicts, "Trinitarian Doctrine"));
    }

    @Test
    public void sameDenominationHasNoConflicts() {
        assertTrue(DenominationCompatibility.getDoctrinalConflicts("Catholic", "Catholic").isEmpty());
        assertTrue(DenominationCompatibility.getDoctrinalConflicts("Baptist", "Baptist").isEmpty());
    }

    @Test
    public void nullOrEmptyInputsProduceNoConflicts() {
        assertTrue(DenominationCompatibility.getDoctrinalConflicts(null, "Catholic").isEmpty());
        assertTrue(DenominationCompatibility.getDoctrinalConflicts("Catholic", null).isEmpty());
        assertTrue(DenominationCompatibility.getDoctrinalConflicts("", "Catholic").isEmpty());
    }

    @Test
    public void unrecognizedDenominationProducesNoConflicts() {
        assertTrue(DenominationCompatibility.getDoctrinalConflicts(
            "Totally Made Up Fellowship XYZ", "Catholic").isEmpty());
    }

    @Test
    public void conflictDescriptionsNamePositionsOfBothSides() {
        List<DenominationCompatibility.DoctrinalConflict> conflicts =
            DenominationCompatibility.getDoctrinalConflicts("Baptist", "Catholic");
        for (DenominationCompatibility.DoctrinalConflict c : conflicts) {
            assertNotNull(c.description);
            assertTrue("Description should mention both denominations: " + c.description,
                c.description.contains("Baptist") && c.description.contains("Catholic"));
        }
    }

    // --- Alias and blend resolution ---

    @Test
    public void abbreviationAliasesResolve() {
        assertFalse("SBC alias should resolve to Southern Baptist Convention",
            DenominationCompatibility.getCompatibleDenominations("sbc", false).isEmpty());
        assertFalse("ELCA alias should resolve",
            DenominationCompatibility.getCompatibleDenominations("elca", false).isEmpty());
    }

    @Test
    public void blendedDenominationsResolveToUnionOfComponents() {
        List<String> blend = DenominationCompatibility.getCompatibleDenominations("Reformed Baptist", false);
        assertFalse("Reformed Baptist blend should resolve", blend.isEmpty());
        assertFalse("Blend results should not include its own components",
            blend.contains("Baptist"));
        assertFalse(blend.contains("Calvinist"));
    }

    @Test
    public void blendInheritsConflictsFromEitherComponent() {
        // Reformed Baptist = Baptist (credobaptist) + Calvinist (paedobaptist);
        // against Catholic the Baptist half still triggers the Baptism conflict.
        List<DenominationCompatibility.DoctrinalConflict> conflicts =
            DenominationCompatibility.getDoctrinalConflicts("Reformed Baptist", "Catholic");
        assertTrue(hasIssue(conflicts, "Baptism"));
    }
}
