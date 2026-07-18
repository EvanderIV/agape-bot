package com.agape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.agape.ProfileEditor.Field;

/** Characterizes the matchmaker profile-edit validation and apply logic. */
public class ProfileEditorTest {

    private static AppState profile() {
        AppState s = new AppState();
        s.name = "Old Name";
        s.sex = false; // Male
        s.birthday = "1/1/1995";
        s.targetAge = "20-30";
        s.sect = "Baptist";
        return s;
    }

    @Test
    public void fieldKeysRoundTrip() {
        for (Field f : Field.values()) {
            assertEquals(f, Field.fromKey(f.key));
        }
    }

    @Test
    public void nameRejectsEmptyAndAcceptsValue() {
        AppState s = profile();
        assertFalse(ProfileEditor.apply(s, Field.NAME, "   ").ok);
        assertEquals("Old Name", s.name); // unchanged on failure

        assertTrue(ProfileEditor.apply(s, Field.NAME, "  New Name  ").ok);
        assertEquals("New Name", s.name); // trimmed
    }

    @Test
    public void birthdayValidatesFormatAndAge() {
        AppState s = profile();
        assertFalse("garbage rejected", ProfileEditor.apply(s, Field.BIRTHDAY, "not a date").ok);
        assertFalse("under-18 rejected", ProfileEditor.apply(s, Field.BIRTHDAY, "1/1/2015").ok);
        assertEquals("1/1/1995", s.birthday); // unchanged on failure

        assertTrue(ProfileEditor.apply(s, Field.BIRTHDAY, "6/15/1990").ok);
        assertEquals("6/15/1990", s.birthday);
    }

    @Test
    public void genderParsing() {
        AppState s = profile();
        assertTrue(ProfileEditor.apply(s, Field.GENDER, "Female").ok);
        assertTrue(s.sex);
        assertTrue(ProfileEditor.apply(s, Field.GENDER, "male").ok);
        assertFalse(s.sex);
        assertFalse(ProfileEditor.apply(s, Field.GENDER, "attack helicopter").ok);
        assertFalse(s.sex); // unchanged from last valid
    }

    @Test
    public void targetAgeValidation() {
        AppState s = profile();
        assertFalse(ProfileEditor.apply(s, Field.TARGET_AGE, "25-18").ok); // reversed
        assertEquals("20-30", s.targetAge);
        assertTrue(ProfileEditor.apply(s, Field.TARGET_AGE, "22-28").ok);
        assertEquals("22-28", s.targetAge);
    }

    @Test
    public void optionalFieldsClearOnEmptyOrSkip() {
        AppState s = profile();
        s.strengths = "patient";
        assertTrue(ProfileEditor.apply(s, Field.STRENGTHS, "skip").ok);
        assertEquals("", s.strengths);

        s.country = "USA";
        assertTrue(ProfileEditor.apply(s, Field.LOCATION, "skip").ok);
        assertEquals("", s.country);
    }

    @Test
    public void requiredParagraphFieldsRejectEmpty() {
        AppState s = profile();
        s.dealBreakers = "must love dogs";
        assertFalse(ProfileEditor.apply(s, Field.DEAL_BREAKERS, "   ").ok);
        assertEquals("must love dogs", s.dealBreakers); // unchanged
    }

    @Test
    public void currentValueReflectsGenderAsWords() {
        AppState s = profile();
        assertEquals("Male", ProfileEditor.currentValue(s, Field.GENDER));
        s.sex = true;
        assertEquals("Female", ProfileEditor.currentValue(s, Field.GENDER));
    }

    @Test
    public void currentValueNeverNull() {
        AppState blank = new AppState();
        for (Field f : Field.values()) {
            assertTrue("null for " + f, ProfileEditor.currentValue(blank, f) != null);
        }
    }
}
