package com.agape;

import static org.junit.Assert.*;
import org.junit.Test;

public class ApplicationHandlerTest {

    /** Minimal valid AppState for card text tests. Override individual fields per test. */
    private ApplicationHandler.AppState makeState() {
        ApplicationHandler.AppState s = new ApplicationHandler.AppState();
        s.name = "Test Person";
        s.username = "testperson";
        s.birthday = "1/1/2000";   // M/D/YYYY
        s.sex = false;             // Male
        s.sect = "Protestant";
        s.physicalDescription = "Average build";
        s.hobbies = "Reading";
        s.dealBreakers = "Smoking";
        s.country = "United States";
        s.strengths = "";
        s.weaknesses = "";
        s.lookFor = "";
        return s;
    }

    // --- buildCardText: strengths/weaknesses omission ---

    @Test
    public void buildCardText_emptyStrengthsAndWeaknessesProduceNoTextFromThoseFields() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "";
        s.weaknesses = "";
        String card = ApplicationHandler.buildCardText(s);
        // Neither field had content — just verify no "null" leaks and the card still renders
        assertFalse("Null literal should not appear in card", card.contains("null"));
        assertTrue("Card should still have name with empty optional fields", card.contains(s.name));
    }

    @Test
    public void buildCardText_strengthsAppearsWhenSet() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "Kind hearted";
        s.weaknesses = "";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Strengths text should appear in card", card.contains("Kind hearted"));
    }

    @Test
    public void buildCardText_weaknessesAppearsWhenSet() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "";
        s.weaknesses = "Easily distracted";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Weaknesses text should appear in card", card.contains("Easily distracted"));
    }

    @Test
    public void buildCardText_emptyStrengthsOmittedWhenWeaknessSet() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "";
        s.weaknesses = "Procrastinator";
        String card = ApplicationHandler.buildCardText(s);
        // The weakness text appears but there's no separate blank line for the missing strengths
        assertTrue(card.contains("Procrastinator"));
        // Card should not start the str/weak block with a blank line
        int procPos = card.indexOf("Procrastinator");
        String before = card.substring(0, procPos);
        assertFalse("No triple newline before weaknesses", before.endsWith("\n\n\n"));
    }

    @Test
    public void buildCardText_emptyWeaknessOmittedWhenStrengthSet() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "Patient";
        s.weaknesses = "";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue(card.contains("Patient"));
        assertFalse("Empty weakness text should not appear", card.contains("null"));
    }

    @Test
    public void buildCardText_bothStrengthsAndWeaknessesAppearWhenSet() {
        ApplicationHandler.AppState s = makeState();
        s.strengths = "Generous";
        s.weaknesses = "Impatient";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue(card.contains("Generous"));
        assertTrue(card.contains("Impatient"));
    }

    // --- buildCardText: lookFor / green flag ---

    @Test
    public void buildCardText_emptyLookForOmitsGreenFlagLine() {
        ApplicationHandler.AppState s = makeState();
        s.lookFor = "";
        String card = ApplicationHandler.buildCardText(s);
        assertFalse("Empty lookFor should omit green flag line", card.contains("green_flag.png"));
    }

    @Test
    public void buildCardText_nullLookForOmitsGreenFlagLine() {
        ApplicationHandler.AppState s = makeState();
        s.lookFor = null;
        String card = ApplicationHandler.buildCardText(s);
        assertFalse("Null lookFor should omit green flag line", card.contains("green_flag.png"));
    }

    @Test
    public void buildCardText_lookForAppearsWithGreenFlag() {
        ApplicationHandler.AppState s = makeState();
        s.lookFor = "Kind, patient";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Non-empty lookFor should include green flag line", card.contains("green_flag.png"));
        assertTrue("LookFor text should appear in card", card.contains("Kind, patient"));
    }

    // --- buildCardText: country / location ---

    @Test
    public void buildCardText_countryAppearsWhenSet() {
        ApplicationHandler.AppState s = makeState();
        s.country = "Canada";
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Country should appear in card", card.contains("Canada"));
    }

    @Test
    public void buildCardText_emptyCountryOmitted() {
        ApplicationHandler.AppState s = makeState();
        s.country = "";
        String card = ApplicationHandler.buildCardText(s);
        // Should not have "null" or a blank country line
        assertFalse("Null country string should not appear", card.contains("null"));
    }

    // --- buildCardText: structural invariants ---

    @Test
    public void buildCardText_alwaysContainsNameAndUsername() {
        ApplicationHandler.AppState s = makeState();
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Card should contain the name", card.contains("Test Person"));
        assertTrue("Card should contain @username", card.contains("@testperson"));
    }

    @Test
    public void buildCardText_alwaysContainsRedFlagLine() {
        ApplicationHandler.AppState s = makeState();
        String card = ApplicationHandler.buildCardText(s);
        assertTrue("Card should always contain red flag (deal breakers) line",
            card.contains("red_flag.png"));
    }

    // --- suggestDesignCode ---

    @Test
    public void suggestDesignCode_returnsNonNullCode() {
        ApplicationHandler.AppState s = makeState();
        s.hobbies = "I like reading";
        String code = ApplicationHandler.suggestDesignCode(s);
        assertNotNull("Design code should not be null", code);
        assertFalse("Design code should not be empty", code.isEmpty());
    }

    @Test
    public void suggestDesignCode_codeContainsDash() {
        ApplicationHandler.AppState s = makeState();
        String code = ApplicationHandler.suggestDesignCode(s);
        assertTrue("Design code should contain a dash separator", code.contains("-"));
    }

    @Test
    public void suggestDesignCode_gamingHobbiesReturnKnownCode() {
        ApplicationHandler.AppState s = makeState();
        s.hobbies = "I love gaming and video games";
        String code = ApplicationHandler.suggestDesignCode(s);
        // Gaming codes are BTW-PST, REA-WOV, or MCJ-CMA
        assertTrue("Gaming hobbies should produce a known gaming code",
            code.equals("BTW-PST") || code.equals("REA-WOV") || code.equals("MCJ-CMA"));
    }

    @Test
    public void suggestDesignCode_hikingHobbiesReturnMSRCode() {
        ApplicationHandler.AppState s = makeState();
        s.hobbies = "I love hiking and outdoor adventures";
        String code = ApplicationHandler.suggestDesignCode(s);
        assertEquals("Hiking/adventure hobbies should produce MSR-CMA", "MSR-CMA", code);
    }

    @Test
    public void suggestDesignCode_starWarsHobbiesReturnFODCode() {
        ApplicationHandler.AppState s = makeState();
        s.hobbies = "I am obsessed with star wars";
        String code = ApplicationHandler.suggestDesignCode(s);
        assertEquals("Star Wars hobbies should produce FOD-SWR", "FOD-SWR", code);
    }

    @Test
    public void suggestDesignCode_femaleCalmStrengthsReturnExpectedCode() {
        ApplicationHandler.AppState s = makeState();
        s.sex = true;   // Female
        s.strengths = "I am very calm and peaceful";
        String code = ApplicationHandler.suggestDesignCode(s);
        assertTrue("Female calm strengths should produce SKA-SPR or SPC-SPK",
            code.equals("SKA-SPR") || code.equals("SPC-SPK"));
    }
}
