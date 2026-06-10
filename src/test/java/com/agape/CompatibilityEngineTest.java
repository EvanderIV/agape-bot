package com.agape;

import static org.junit.Assert.*;

import java.time.LocalDate;

import org.junit.Test;

/**
 * Regression tests for the pure scoring functions of the compatibility engine.
 *
 * These functions take two profiles and return a ScoreDetail; they perform no
 * file or Discord I/O, so they can be characterized exactly. The point totals
 * asserted here ARE the matchmaking behavior — any refactor must keep them.
 */
public class CompatibilityEngineTest {

    /** Builds a profile with the given core fields; everything else left null/default. */
    private AppState profile(String name, boolean sexIsFemale, String sect) {
        AppState s = new AppState();
        s.name = name;
        s.sex = sexIsFemale;
        s.sect = sect;
        return s;
    }

    /** Returns a birthday string (M/D/YYYY) that makes the person exactly {@code age} years old today. */
    private String birthdayForAge(int age) {
        LocalDate bd = LocalDate.now().minusYears(age);
        return bd.getMonthValue() + "/" + bd.getDayOfMonth() + "/" + bd.getYear();
    }

    // --- Score constants (displayed maximums on every embed) ---

    @Test
    public void maxScoreConstantsAreStable() {
        assertEquals(30,  CompatibilityEngine.MAX_DENOM);
        assertEquals(50,  CompatibilityEngine.MAX_AGE);
        assertEquals(10,  CompatibilityEngine.MAX_DIST);
        assertEquals(-15, CompatibilityEngine.MIN_DIST);
        assertEquals(20,  CompatibilityEngine.MAX_VALUES);
        assertEquals(110, CompatibilityEngine.MAX_TOTAL);
    }

    // --- Denomination scoring ---

    @Test
    public void sameDenominationScoresFullMarks() {
        // 20 base for same denomination + 5 + 5 implicit self-inclusion bonus
        AppState a = profile("A", false, "Catholic");
        AppState b = profile("B", true,  "Catholic");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(30, d.score);
        assertTrue(d.detail.contains("Same denomination"));
    }

    @Test
    public void mutuallyCompatibleDenominationsScore20Base() {
        // Catholic lists Eastern Orthodox and vice versa (mutual), no target-sect bonus
        AppState a = profile("A", false, "Catholic");
        AppState b = profile("B", true,  "Eastern Orthodox");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(20, d.score);
        assertTrue(d.detail.contains("Mutually compatible"));
    }

    @Test
    public void oneWayCompatibilityScores12() {
        // Lutheran lists Anglican; Anglican does not list Lutheran
        AppState a = profile("A", false, "Lutheran");
        AppState b = profile("B", true,  "Anglican");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(12, d.score);
        assertTrue(d.detail.contains("one-way"));
    }

    @Test
    public void incompatibleDenominationsScoreMinus5() {
        AppState a = profile("A", false, "Catholic");
        AppState b = profile("B", true,  "Baptist");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(-5, d.score);
        assertTrue(d.detail.contains("No listed compatibility"));
    }

    @Test
    public void anyTargetSectGrantsPreferenceBonus() {
        AppState a = profile("A", false, "Catholic");
        a.targetSect = "Any denomination";
        AppState b = profile("B", true, "Baptist");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        // -5 incompatible base + 5 because A is open to any denomination
        assertEquals(0, d.score);
    }

    @Test
    public void explicitTargetSectGrantsPreferenceBonus() {
        AppState a = profile("A", false, "Catholic");
        a.targetSect = "Baptist, Methodist";
        AppState b = profile("B", true, "Baptist");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(0, d.score); // -5 base + 5 explicit preference
    }

    @Test
    public void missingDenominationScoresZero() {
        AppState a = profile("A", false, null);
        AppState b = profile("B", true, "Catholic");
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDenomination(a, b);
        assertEquals(0, d.score);
        assertTrue(d.detail.contains("No denomination data available"));
    }

    // --- Age scoring ---

    @Test
    public void bothWithinRangeScores50() {
        AppState a = profile("A", false, null);
        a.birthday = birthdayForAge(25);
        a.targetAge = "20-30";
        AppState b = profile("B", true, null);
        b.birthday = birthdayForAge(27);
        b.targetAge = "20-30";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreAge(a, b);
        assertEquals(50, d.score);
    }

    @Test
    public void yearsOutsideRangeCostTwoPointsEach() {
        AppState a = profile("A", false, null);
        a.birthday = birthdayForAge(25);
        a.targetAge = "20-30";
        AppState b = profile("B", true, null);
        b.birthday = birthdayForAge(35); // 5 years above A's max → 25 - 10 = 15
        b.targetAge = "20-40";           // A (25) is within B's range → 25
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreAge(a, b);
        assertEquals(40, d.score);
    }

    @Test
    public void noAgePreferencesScoreZero() {
        AppState a = profile("A", false, null);
        a.birthday = birthdayForAge(25);
        AppState b = profile("B", true, null);
        b.birthday = birthdayForAge(27);
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreAge(a, b);
        assertEquals(0, d.score);
        assertTrue(d.detail.contains("No age preferences set"));
    }

    @Test
    public void singleNumberTargetAgeIsTreatedAsExactRange() {
        AppState a = profile("A", false, null);
        a.birthday = birthdayForAge(25);
        a.targetAge = "27";
        AppState b = profile("B", true, null);
        b.birthday = birthdayForAge(27); // exactly matches A's single-age preference
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreAge(a, b);
        assertEquals(25, d.score); // A's half only; B has no preference
    }

    // --- Distance scoring ---

    @Test
    public void sameCountryScoresMax() {
        AppState a = profile("A", false, null);
        a.country = "USA";
        AppState b = profile("B", true, null);
        b.country = "USA";
        assertEquals(10, CompatibilityEngine.scoreDistance(a, b).score);
    }

    @Test
    public void cityPrefixIsStrippedBeforeCountryComparison() {
        AppState a = profile("A", false, null);
        a.country = "Texas, USA";
        AppState b = profile("B", true, null);
        b.country = "USA";
        assertEquals(10, CompatibilityEngine.scoreDistance(a, b).score);
    }

    @Test
    public void sameContinentScores5() {
        AppState a = profile("A", false, null);
        a.country = "USA";
        AppState b = profile("B", true, null);
        b.country = "Canada";
        assertEquals(5, CompatibilityEngine.scoreDistance(a, b).score);
    }

    @Test
    public void oppositeSidesOfGlobeScoreMinus15() {
        AppState a = profile("A", false, null);
        a.country = "USA";
        AppState b = profile("B", true, null);
        b.country = "Australia";
        assertEquals(-15, CompatibilityEngine.scoreDistance(a, b).score);
    }

    @Test
    public void unknownCountryScoresZeroWithoutPenalty() {
        AppState a = profile("A", false, null);
        a.country = "Narnia";
        AppState b = profile("B", true, null);
        b.country = "USA";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDistance(a, b);
        assertEquals(0, d.score);
        assertTrue(d.detail.contains("Continent not recognized"));
    }

    @Test
    public void missingCountriesScoreZero() {
        AppState a = profile("A", false, null);
        AppState b = profile("B", true, null);
        assertEquals(0, CompatibilityEngine.scoreDistance(a, b).score);
    }

    // --- Deal-breaker scoring ---

    @Test
    public void noDealBreakerConflictsScoreZero() {
        AppState a = profile("A", false, null);
        a.dealBreakers = "smoking";
        AppState b = profile("B", true, null);
        b.hobbies = "reading and painting";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDealBreakers(a, b);
        assertEquals(0, d.score);
        assertTrue(d.detail.contains("No deal breaker conflicts"));
    }

    @Test
    public void oneDirectionalHitScoresMinus10() {
        AppState a = profile("A", false, null);
        a.dealBreakers = "smoking";
        AppState b = profile("B", true, null);
        b.hobbies = "smoking cigars on weekends";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDealBreakers(a, b);
        assertEquals(-10, d.score);
        assertTrue(d.detail.contains("smoking"));
    }

    @Test
    public void multipleHitsInOneDirectionAreCappedAtMinus15() {
        AppState a = profile("A", false, null);
        a.dealBreakers = "smoking and drinking alcohol";
        AppState b = profile("B", true, null);
        b.hobbies = "smoking, drinking beer";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDealBreakers(a, b);
        assertEquals(-15, d.score); // 2 hits × -10 = -20, capped at -15 per direction
    }

    @Test
    public void bidirectionalHitsScoreMinus20() {
        AppState a = profile("A", false, null);
        a.dealBreakers = "smoking";
        a.hobbies = "drinking wine";
        AppState b = profile("B", true, null);
        b.dealBreakers = "drinking alcohol";
        b.hobbies = "smoking";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDealBreakers(a, b);
        assertEquals(-20, d.score);
    }

    @Test
    public void dealBreakerSignalsAlsoComeFromWeaknesses() {
        AppState a = profile("A", false, null);
        a.dealBreakers = "dishonesty";
        AppState b = profile("B", true, null);
        b.weaknesses = "sometimes lying about small things";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreDealBreakers(a, b);
        assertEquals(-10, d.score);
    }

    // --- Values scoring ---

    @Test
    public void valueAlignmentScoresFourPointsPerMatch() {
        AppState a = profile("A", false, null);
        a.lookFor = "kind";
        AppState b = profile("B", true, null);
        b.strengths = "kind and caring";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreValues(a, b);
        assertEquals(4, d.score);
    }

    @Test
    public void valuesPerDirectionAreCappedAtTen() {
        AppState a = profile("A", false, null);
        a.lookFor = "kind, honest, patient, funny";
        AppState b = profile("B", true, null);
        b.strengths = "kind, honest, patient, funny";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreValues(a, b);
        assertEquals(10, d.score); // 4 matches × 4 = 16, capped at 10
    }

    @Test
    public void noValueAlignmentScoresZero() {
        AppState a = profile("A", false, null);
        a.lookFor = "kind";
        AppState b = profile("B", true, null);
        b.strengths = "good at chess";
        CompatibilityEngine.ScoreDetail d = CompatibilityEngine.scoreValues(a, b);
        assertEquals(0, d.score);
        assertTrue(d.detail.contains("No value alignment"));
    }

    // --- Precluded pairs (read-only checks; no files are written) ---

    @Test
    public void unknownPairIsNotPrecluded() {
        assertFalse(CompatibilityEngine.isPrecluded(
            "test_user_does_not_exist_1", "test_user_does_not_exist_2"));
    }
}
