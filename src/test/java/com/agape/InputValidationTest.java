package com.agape;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Regression tests for user-input validation and auto-moderation rules.
 *
 * These are the rules that keep bad data out of profiles (age limits,
 * birthday formats, MBTI bans, minimum lengths). The thin wrappers below
 * point at where each rule lives; if a refactor moves one, update ONLY the
 * wrapper while keeping every assertion identical.
 */
public class InputValidationTest {

    private static String parseBirthday(String input) {
        return AgeUtils.parseBirthday(input);
    }

    private static int calculateAge(String birthday) {
        return AgeUtils.calculateAge(birthday);
    }

    private static boolean isValidTargetAge(String input) {
        return AgeUtils.isValidTargetAge(input);
    }

    private static boolean containsMBTI(String input) {
        return AutoModerator.containsMBTI(input);
    }

    private static int[] ageBracket(int age) {
        return MatchmakingEngine.ageBracket(age);
    }

    // --- Birthday parsing ---

    @Test
    public void birthday_validDateIsNormalized() throws Exception {
        assertEquals("5/21/1998", parseBirthday("5/21/1998"));
        assertEquals("5/21/1998", parseBirthday("05/21/1998"));
        assertEquals("5/21/1998", parseBirthday("5-21-1998"));
    }

    @Test
    public void birthday_twoDigitYearGets1900Added() throws Exception {
        assertEquals("5/21/1998", parseBirthday("5/21/98"));
    }

    @Test
    public void birthday_impossibleDateRejected() throws Exception {
        assertNull(parseBirthday("2/30/2000"));
        assertNull(parseBirthday("13/5/2000"));
    }

    @Test
    public void birthday_futureYearRejected() throws Exception {
        int next = java.time.LocalDate.now().getYear() + 1;
        assertNull(parseBirthday("1/1/" + next));
    }

    @Test
    public void birthday_plainAgeIsConvertedToBirthday() throws Exception {
        String bd = parseBirthday("25");
        assertNotNull(bd);
        assertEquals(25, calculateAge(bd));
    }

    @Test
    public void birthday_outOfRangeAgeRejected() throws Exception {
        assertNull(parseBirthday("0"));
        assertNull(parseBirthday("121"));
    }

    @Test
    public void birthday_garbageRejected() throws Exception {
        assertNull(parseBirthday("yesterday"));
        assertNull(parseBirthday(""));
    }

    // --- Target age validation (18–70 enforced) ---

    @Test
    public void targetAge_singleAgeWithinBoundsAccepted() throws Exception {
        assertTrue(isValidTargetAge("25"));
        assertTrue(isValidTargetAge("18"));
        assertTrue(isValidTargetAge("70"));
    }

    @Test
    public void targetAge_underageAndOverageRejected() throws Exception {
        assertFalse("Under 18 must be rejected", isValidTargetAge("17"));
        assertFalse("Over 70 must be rejected", isValidTargetAge("71"));
    }

    @Test
    public void targetAge_validRangesAccepted() throws Exception {
        assertTrue(isValidTargetAge("18-25"));
        assertTrue(isValidTargetAge("18 - 25"));
        assertTrue(isValidTargetAge("18–25")); // en-dash
        assertTrue(isValidTargetAge("18—25")); // em-dash
    }

    @Test
    public void targetAge_invalidRangesRejected() throws Exception {
        assertFalse("Reversed range must be rejected", isValidTargetAge("25-18"));
        assertFalse("Range crossing 70 must be rejected", isValidTargetAge("18-71"));
        assertFalse("Range below 18 must be rejected", isValidTargetAge("16-25"));
        assertFalse("Multiple dashes must be rejected", isValidTargetAge("18-25-30"));
        assertFalse(isValidTargetAge("abc"));
    }

    // --- MBTI / Enneagram auto-mod detection ---

    @Test
    public void mbti_typeCodesAreDetected() throws Exception {
        assertTrue(containsMBTI("I am an INFP"));
        assertTrue(containsMBTI("entj describes me"));
        assertTrue(containsMBTI("ISFP-T here"));
        assertTrue(containsMBTI("my MBTI says it all"));
        assertTrue(containsMBTI("enneagram type 4"));
    }

    @Test
    public void mbti_normalTextIsNotFlagged() throws Exception {
        assertFalse(containsMBTI("I am kind and I love hiking"));
        assertFalse(containsMBTI("went to school in Texas"));
    }

    @Test
    public void mbti_nullTextIsNotFlagged() {
        assertFalse(AutoModerator.containsMBTI(null));
    }

    // --- Auto-mod rules (check returns null when the profile passes) ---

    private static Object checkAutoRules(AppState state) {
        return AutoModerator.check(state);
    }

    private AppState cleanState() {
        AppState s = new AppState();
        s.username = "tester";
        s.physicalDescription = "tall with brown hair";
        s.hobbies = "hiking and reading";
        s.strengths = "patient";
        s.weaknesses = "stubborn";
        s.lookFor = "kindness";
        s.dealBreakers = "smoking and dishonesty";
        return s;
    }

    @Test
    public void autoMod_cleanProfilePasses() throws Exception {
        assertNull(checkAutoRules(cleanState()));
    }

    @Test
    public void autoMod_mbtiInDescriptionIsRejected() throws Exception {
        AppState s = cleanState();
        s.physicalDescription = "INTJ, tall";
        assertNotNull(checkAutoRules(s));
    }

    @Test
    public void autoMod_tooShortPhysicalDescriptionIsRejected() throws Exception {
        AppState s = cleanState();
        s.physicalDescription = "5'11";
        assertNotNull(checkAutoRules(s));
    }

    @Test
    public void autoMod_tooShortDealBreakersAreRejected() throws Exception {
        AppState s = cleanState();
        s.dealBreakers = "none";
        assertNotNull(checkAutoRules(s));
    }

    @Test
    public void autoMod_obsceneContentIsRejected() throws Exception {
        AppState s = cleanState();
        s.hobbies = "gaming and gooning";
        assertNotNull(checkAutoRules(s));

        AppState s2 = cleanState();
        s2.physicalDescription = "very breedable physique";
        assertNotNull(checkAutoRules(s2));
    }

    // --- Line-break normalization (package-private, called directly) ---

    @Test
    public void normalizeLineBreaks_joinsLinesWithCommas() {
        assertEquals("a, b, c", ApplicationHandler.normalizeLineBreaks("a,\nb\n\nc"));
    }

    @Test
    public void normalizeLineBreaks_singleLinePassesThrough() {
        assertEquals("hiking", ApplicationHandler.normalizeLineBreaks("hiking"));
    }

    @Test
    public void normalizeLineBreaks_nullPassesThrough() {
        assertNull(ApplicationHandler.normalizeLineBreaks(null));
    }

    // --- Quickmatch age brackets ---

    @Test
    public void ageBrackets_matchDocumentedBoundaries() throws Exception {
        assertArrayEquals(new int[]{18, 22}, ageBracket(18));
        assertArrayEquals(new int[]{18, 22}, ageBracket(22));
        assertArrayEquals(new int[]{23, 27}, ageBracket(23));
        assertArrayEquals(new int[]{28, 32}, ageBracket(28));
        assertArrayEquals(new int[]{33, 40}, ageBracket(40));
        assertArrayEquals(new int[]{41, 55}, ageBracket(41));
        assertArrayEquals(new int[]{56, Integer.MAX_VALUE}, ageBracket(56));
    }

    @Test
    public void ageBrackets_underageReturnsNull() throws Exception {
        assertNull(ageBracket(17));
        assertNull(ageBracket(0));
    }
}
