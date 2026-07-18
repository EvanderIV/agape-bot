package com.agape;

/**
 * Matchmaker-facing profile field editing — the logic behind {@code /edit-profile}.
 *
 * <p>Lets admins and matchmakers correct a user's profile without backend file
 * access. Each editable {@link Field} knows its stored value (for pre-filling the
 * edit modal) and how to validate + apply a new value, mirroring the same rules
 * the DM questionnaire enforces (birthday parsing + 18+ check, target-age range
 * validation, denomination normalization, line-break normalization).
 *
 * <p>Only the 13 text fields are editable here; the profile photo (section 14)
 * needs a file upload and stays on the existing photo-change-request flow.
 * Applying never persists — the caller saves via {@link ProfileRepository} only
 * on a successful {@link Result}.
 */
public final class ProfileEditor {

    private ProfileEditor() {}

    /** An editable profile field: a stable key (for IDs/choices), a label, and modal hints. */
    public enum Field {
        NAME("name", "Name", false, true),
        BIRTHDAY("birthday", "Birthday (M/D/YYYY)", false, true),
        LOCATION("location", "Location / Country", false, false),
        GENDER("gender", "Gender (Male/Female)", false, true),
        DENOMINATION("denomination", "Denomination", false, true),
        TARGET_AGE("targetage", "Target Age Range", false, true),
        TARGET_DENOMINATION("targetsect", "Target Denomination", false, false),
        PHYSICAL("physical", "Physical Description", true, true),
        HOBBIES("hobbies", "Hobbies", true, true),
        STRENGTHS("strengths", "Strengths", true, false),
        WEAKNESSES("weaknesses", "Weaknesses", true, false),
        LOOK_FOR("lookfor", "What They're Looking For", true, false),
        DEAL_BREAKERS("dealbreakers", "Deal Breakers", true, true);

        public final String key;
        public final String label;
        public final boolean paragraph; // multi-line modal input?
        public final boolean required;   // must be non-empty?

        Field(String key, String label, boolean paragraph, boolean required) {
            this.key = key;
            this.label = label;
            this.paragraph = paragraph;
            this.required = required;
        }

        public static Field fromKey(String key) {
            for (Field f : values()) if (f.key.equals(key)) return f;
            return null;
        }
    }

    /** Outcome of an edit: {@code ok} plus a user-facing confirmation or error message. */
    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) { this.ok = ok; this.message = message; }
        static Result ok(String m)    { return new Result(true, m); }
        static Result error(String m) { return new Result(false, m); }
    }

    /** The field's current stored value, for pre-filling the edit modal (never null). */
    public static String currentValue(AppState s, Field field) {
        switch (field) {
            case NAME:                return nz(s.name);
            case BIRTHDAY:            return nz(s.birthday);
            case LOCATION:            return nz(s.country);
            case GENDER:              return s.sex ? "Female" : "Male";
            case DENOMINATION:        return nz(s.sect);
            case TARGET_AGE:          return nz(s.targetAge);
            case TARGET_DENOMINATION: return nz(s.targetSect);
            case PHYSICAL:            return nz(s.physicalDescription);
            case HOBBIES:             return nz(s.hobbies);
            case STRENGTHS:           return nz(s.strengths);
            case WEAKNESSES:          return nz(s.weaknesses);
            case LOOK_FOR:            return nz(s.lookFor);
            case DEAL_BREAKERS:       return nz(s.dealBreakers);
            default:                  return "";
        }
    }

    /**
     * Validates {@code raw} and, if valid, applies it to {@code state}. Mirrors the
     * questionnaire's own validation. Does NOT persist — the caller saves on success.
     */
    public static Result apply(AppState s, Field field, String raw) {
        String v = raw == null ? "" : raw.trim();

        switch (field) {
            case NAME:
                if (v.isEmpty()) return Result.error("Name cannot be empty.");
                s.name = v;
                break;
            case BIRTHDAY: {
                String bday = AgeUtils.parseBirthday(v);
                if (bday == null) return Result.error("Invalid birthday. Use **M/D/YYYY** (e.g. 4/17/2000).");
                if (AgeUtils.calculateAge(bday) < 18) return Result.error("That birthday is under 18 — profiles must be 18+.");
                s.birthday = bday;
                break;
            }
            case LOCATION:
                s.country = v.equalsIgnoreCase("skip") ? "" : v;
                break;
            case GENDER: {
                boolean female = LanguageManager.isFemale(v);
                String g = v.toLowerCase();
                boolean male = g.equals("male") || g.equals("m") || g.equals("man") || g.equals("boy");
                if (!female && !male) return Result.error("Enter **Male** or **Female**.");
                s.sex = female;
                break;
            }
            case DENOMINATION: {
                if (v.isEmpty()) return Result.error("Denomination cannot be empty.");
                String normalized = DenominationCompatibility.normalizeDenomination(v);
                s.sect = normalized;
                boolean changed = !normalized.equalsIgnoreCase(v);
                return Result.ok("Updated **Denomination**" + (changed ? " (normalized to **" + normalized + "**)" : "") + ".");
            }
            case TARGET_AGE:
                if (!AgeUtils.isValidTargetAge(v))
                    return Result.error("Invalid target age. Use a number (e.g. `25`) or range (e.g. `18-25`), within 18–70.");
                s.targetAge = v;
                break;
            case TARGET_DENOMINATION:
                s.targetSect = v;
                break;
            case PHYSICAL:
                if (v.isEmpty()) return Result.error("Physical description cannot be empty.");
                s.physicalDescription = v;
                break;
            case HOBBIES:
                if (v.isEmpty()) return Result.error("Hobbies cannot be empty.");
                s.hobbies = ApplicationHandler.normalizeLineBreaks(v);
                break;
            case STRENGTHS:
                s.strengths = v.equalsIgnoreCase("skip") ? "" : ApplicationHandler.normalizeLineBreaks(v);
                break;
            case WEAKNESSES:
                s.weaknesses = v.equalsIgnoreCase("skip") ? "" : ApplicationHandler.normalizeLineBreaks(v);
                break;
            case LOOK_FOR:
                s.lookFor = v.equalsIgnoreCase("skip") ? "" : ApplicationHandler.normalizeLineBreaks(v);
                break;
            case DEAL_BREAKERS:
                if (v.isEmpty()) return Result.error("Deal breakers cannot be empty.");
                s.dealBreakers = ApplicationHandler.normalizeLineBreaks(v);
                break;
            default:
                return Result.error("Unknown field.");
        }

        return Result.ok("Updated **" + field.label + "**.");
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
