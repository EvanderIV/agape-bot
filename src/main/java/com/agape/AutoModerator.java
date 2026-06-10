package com.agape;

import java.util.regex.Pattern;

/**
 * Automated quality rules applied to every submitted application.
 *
 * When a rule trips, the submission is quietly held back: the profile is
 * saved as CHANGES_REQUESTED and a delayed "matchmaker note" DM asks the
 * user to fix the offending section — it is never posted to the matchmaker
 * channel. Add new rules inside {@link #check}.
 */
public final class AutoModerator {

    /** A failed auto-mod check: the user-facing reason and the section to edit (1–14). */
    public static class AutoModResult {
        public final String reason;
        public final int sectionNum;

        public AutoModResult(String reason, int sectionNum) {
            this.reason = reason;
            this.sectionNum = sectionNum;
        }
    }

    // Compiled once. Matches MBTI type codes like ISFP, ENTJ-A, etc.
    private static final Pattern MBTI_PATTERN =
        Pattern.compile("(?i)\\b(I|E)(N|S)(F|T)(J|P)(-[TA])?\\b");

    private AutoModerator() {}

    /** True when the text names an MBTI/Enneagram type or mentions either system. */
    public static boolean containsMBTI(String text) {
        if (text == null) return false;
        String upper = text.toUpperCase();
        if (upper.contains("MBTI") || upper.contains("ENNEAGRAM")) return true;
        // .find() scans the whole string, unlike .matches() which requires an exact match
        return MBTI_PATTERN.matcher(text).find();
    }

    /**
     * Evaluates the application against all automated quality rules.
     * Returns an AutoModResult describing the first failure, or null if it passes.
     */
    public static AutoModResult check(AppState state) {

        // 1. Check all relevant fields for MBTI / Enneagram types
        if (containsMBTI(state.physicalDescription)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 6);
        }
        if (containsMBTI(state.hobbies)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 7);
        }
        if (containsMBTI(state.strengths)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 8);
        }
        if (containsMBTI(state.weaknesses)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 9);
        }
        if (containsMBTI(state.lookFor)) {
            return new AutoModResult("Don't use MBTI or Enneagram as a benchmark. Describe what makes a good partner in your own words!", 12);
        }
        if (containsMBTI(state.dealBreakers)) {
            return new AutoModResult("Don't use MBTI or Enneagram as a benchmark. Describe what makes a good partner in your own words!", 13);
        }

        // 2. Length checks
        // Require at least 6 characters to prevent things like "5'11"" from slipping through
        if (state.physicalDescription != null && state.physicalDescription.trim().length() < 6) {
            System.out.println("Auto-Mod: Physical description too short for user " + state.username + " | Content: `" + state.physicalDescription + "`");
            return new AutoModResult("Your physical description is a bit too brief. Please provide a few more details so potential matches have a better idea of what you look like!", 6);
        }

        // Require at least 6 characters to force a real answer
        if (state.dealBreakers != null && state.dealBreakers.trim().length() < 6) {
            System.out.println("Auto-Mod: Deal breakers too short for user " + state.username + " | Content: `" + state.dealBreakers + "`");
            return new AutoModResult("Please list at least one or two specific deal breakers / red flags", 13);
        }

        // 3. Obscene content
        if (state.hobbies != null && state.hobbies.toLowerCase().contains("gooning")) {
            System.out.println("Auto-Mod: Bro is GOONING??? - " + state.username + " | Content: `" + state.hobbies + "`");
            return new AutoModResult("Gooning? Really??? Please change that", 13);
        }

        if (state.physicalDescription != null && state.physicalDescription.toLowerCase().contains("breedable")) {
            System.out.println("Auto-Mod: I... really didn't think anyone would ever type that: " + state.username + " | Content: `" + state.physicalDescription + "`");
            return new AutoModResult("Please don't include obscene or sexual language in your self-description", 6);
        }

        // Add more regex or length checks here!

        return null; // Passed all auto-mod rules
    }
}
