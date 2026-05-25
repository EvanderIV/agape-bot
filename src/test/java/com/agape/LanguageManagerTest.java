package com.agape;

import static org.junit.Assert.*;
import org.junit.Test;

public class LanguageManagerTest {

    private static final String[] ALL_LANGUAGES = {
        "english", "spanish", "french", "portuguese", "dutch",
        "german", "italian", "tagalog", "japanese", "chinese",
        "swahili", "afrikaans", "romanian"
    };

    // Question array indices for optional questions
    private static final int IDX_COUNTRY    = 1;
    private static final int IDX_STRENGTHS  = 7;
    private static final int IDX_WEAKNESSES = 8;
    private static final int IDX_PHOTO      = 9;
    private static final int IDX_LOOK_FOR   = 12;

    // --- Question count ---

    @Test
    public void allLanguagesReturnExactly15Questions() {
        for (String lang : ALL_LANGUAGES) {
            String[] q = LanguageManager.getQuestions(lang);
            assertNotNull("Questions null for: " + lang, q);
            assertEquals("Wrong question count for: " + lang, 14, q.length);
        }
    }

    @Test
    public void noQuestionIsNullOrEmpty() {
        for (String lang : ALL_LANGUAGES) {
            String[] q = LanguageManager.getQuestions(lang);
            for (int i = 0; i < q.length; i++) {
                assertNotNull("Question " + i + " is null for: " + lang, q[i]);
                assertFalse("Question " + i + " is empty for: " + lang, q[i].trim().isEmpty());
            }
        }
    }

    // --- Optional tag placement: tag must appear AFTER the main question text ---

    @Test
    public void photoQuestion_optionalTagIsAfterMainText() {
        for (String lang : ALL_LANGUAGES) {
            String q = LanguageManager.getQuestions(lang)[IDX_PHOTO];
            assertTrue("Photo question missing optional marker for: " + lang, q.contains("-# **"));
            assertFalse("Photo question optional marker must not be at start for: " + lang,
                q.startsWith("-# **"));
            assertTrue("Photo question must use \\n\\n before optional tag for: " + lang,
                q.contains("\n\n-# **"));
        }
    }

    @Test
    public void countryQuestion_optionalTagIsAfterMainText() {
        for (String lang : ALL_LANGUAGES) {
            String q = LanguageManager.getQuestions(lang)[IDX_COUNTRY];
            assertTrue("Country question missing optional marker for: " + lang, q.contains("-# **"));
            assertFalse("Country question optional marker must not be at start for: " + lang,
                q.startsWith("-# **"));
            assertTrue("Country question must use \\n\\n before optional tag for: " + lang,
                q.contains("\n\n-# **"));
        }
    }

    @Test
    public void strengthsQuestion_optionalTagIsAfterEgSection() {
        for (String lang : ALL_LANGUAGES) {
            String q = LanguageManager.getQuestions(lang)[IDX_STRENGTHS];
            assertTrue("Strengths question missing optional marker for: " + lang, q.contains("-# **"));
            assertFalse("Strengths question optional marker must not be at start for: " + lang,
                q.startsWith("-# **"));
            assertTrue("Strengths question must use \\n\\n before optional tag for: " + lang,
                q.contains("\n\n-# **"));
        }
    }

    @Test
    public void weaknessesQuestion_optionalTagIsAfterEgSection() {
        for (String lang : ALL_LANGUAGES) {
            String q = LanguageManager.getQuestions(lang)[IDX_WEAKNESSES];
            assertTrue("Weaknesses question missing optional marker for: " + lang, q.contains("-# **"));
            assertFalse("Weaknesses question optional marker must not be at start for: " + lang,
                q.startsWith("-# **"));
            assertTrue("Weaknesses question must use \\n\\n before optional tag for: " + lang,
                q.contains("\n\n-# **"));
        }
    }

    @Test
    public void lookForQuestion_optionalTagIsAfterEgSection() {
        for (String lang : ALL_LANGUAGES) {
            String q = LanguageManager.getQuestions(lang)[IDX_LOOK_FOR];
            assertTrue("LookFor question missing optional marker for: " + lang, q.contains("-# **"));
            assertFalse("LookFor question optional marker must not be at start for: " + lang,
                q.startsWith("-# **"));
            assertTrue("LookFor question must use \\n\\n before optional tag for: " + lang,
                q.contains("\n\n-# **"));
        }
    }

    // --- isSupportedLanguage ---

    @Test
    public void isSupportedLanguage_acceptsAllKnownLanguages() {
        for (String lang : ALL_LANGUAGES) {
            assertTrue("Should support: " + lang, LanguageManager.isSupportedLanguage(lang));
        }
    }

    @Test
    public void isSupportedLanguage_caseInsensitive() {
        assertTrue(LanguageManager.isSupportedLanguage("English"));
        assertTrue(LanguageManager.isSupportedLanguage("SPANISH"));
        assertTrue(LanguageManager.isSupportedLanguage("French"));
    }

    @Test
    public void isSupportedLanguage_rejectsUnknown() {
        assertFalse(LanguageManager.isSupportedLanguage("klingon"));
        assertFalse(LanguageManager.isSupportedLanguage(""));
        assertFalse(LanguageManager.isSupportedLanguage(null));
    }

    // --- normalizeLanguageName ---

    @Test
    public void normalizeLanguageName_mapsNativeNames() {
        assertEquals("spanish",    LanguageManager.normalizeLanguageName("español"));
        assertEquals("french",     LanguageManager.normalizeLanguageName("français"));
        assertEquals("portuguese", LanguageManager.normalizeLanguageName("português"));
        assertEquals("dutch",      LanguageManager.normalizeLanguageName("nederlands"));
        assertEquals("german",     LanguageManager.normalizeLanguageName("deutsch"));
        assertEquals("italian",    LanguageManager.normalizeLanguageName("italiano"));
        assertEquals("chinese",    LanguageManager.normalizeLanguageName("mandarin"));
    }

    @Test
    public void normalizeLanguageName_caseInsensitive() {
        assertEquals("english", LanguageManager.normalizeLanguageName("English"));
        assertEquals("english", LanguageManager.normalizeLanguageName("ENGLISH"));
        assertEquals("spanish", LanguageManager.normalizeLanguageName("Spanish"));
    }

    @Test
    public void normalizeLanguageName_handlesNull() {
        assertNull(LanguageManager.normalizeLanguageName(null));
    }

    // --- isYes / isNo ---

    @Test
    public void isYes_acceptsCommonInputs() {
        assertTrue(LanguageManager.isYes("yes"));
        assertTrue(LanguageManager.isYes("YES"));
        assertTrue(LanguageManager.isYes("y"));
    }

    @Test
    public void isNo_acceptsCommonInputs() {
        assertTrue(LanguageManager.isNo("no"));
        assertTrue(LanguageManager.isNo("NO"));
        assertTrue(LanguageManager.isNo("n"));
    }

    @Test
    public void isYesAndIsNoAreDisjoint() {
        assertFalse("'no' should not be isYes", LanguageManager.isYes("no"));
        assertFalse("'yes' should not be isNo", LanguageManager.isNo("yes"));
    }

    // --- isFemale ---

    @Test
    public void isFemale_recognizesFemaleInputs() {
        assertTrue(LanguageManager.isFemale("female"));
        assertTrue(LanguageManager.isFemale("Female"));
        assertTrue(LanguageManager.isFemale("f"));
        // Multi-language
        assertTrue(LanguageManager.isFemale("mujer"));   // Spanish
        assertTrue(LanguageManager.isFemale("femme"));   // French
        assertTrue(LanguageManager.isFemale("mulher"));  // Portuguese
        assertTrue(LanguageManager.isFemale("vrouw"));   // Dutch
        assertTrue(LanguageManager.isFemale("frau"));    // German
    }

    @Test
    public void isFemale_rejectsMaleInputs() {
        assertFalse(LanguageManager.isFemale("male"));
        assertFalse(LanguageManager.isFemale("m"));
        assertFalse(LanguageManager.isFemale(""));
        assertFalse(LanguageManager.isFemale(null));
    }

    // --- toTitleCase ---

    @Test
    public void toTitleCase_capitalizesFirstLetter() {
        assertEquals("Hello", LanguageManager.toTitleCase("hello"));
        assertEquals("Hello", LanguageManager.toTitleCase("HELLO"));
        assertEquals("A", LanguageManager.toTitleCase("a"));
    }

    @Test
    public void toTitleCase_handlesNullAndEmpty() {
        assertNull(LanguageManager.toTitleCase(null));
        assertEquals("", LanguageManager.toTitleCase(""));
    }
}
