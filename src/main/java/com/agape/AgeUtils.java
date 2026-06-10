package com.agape;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Birthday and age-range parsing shared by the application flow, the
 * compatibility engine, and quickmatch.
 *
 * Birthdays are stored as "M/D/YYYY" strings (no leading zeros) inside
 * {@link AppState#birthday}. All methods here fail soft: bad input returns
 * null / 0 / false rather than throwing.
 */
public final class AgeUtils {

    private AgeUtils() {}

    /**
     * Parses birthday input from an applicant: an M/D/YYYY date string (also
     * accepts M-D-YYYY) or a plain age integer (which is converted to a
     * birthday of today minus N years). Two-digit years get 1900 added.
     *
     * @return the normalized "M/D/YYYY" string, or null if unparseable,
     *         the date is impossible, or the year is outside 1900–today.
     */
    public static String parseBirthday(String input) {
        if (input == null) return null;
        input = input.trim();
        // Fallback: plain age number
        try {
            int age = Integer.parseInt(input);
            if (age < 1 || age > 120) return null;
            LocalDate bd = LocalDate.now().minusYears(age);
            return bd.getMonthValue() + "/" + bd.getDayOfMonth() + "/" + bd.getYear();
        } catch (NumberFormatException ignored) {}
        // Primary: M/D/YYYY (also accepts M-D-YYYY)
        String[] parts = input.split("[/\\-]");
        if (parts.length == 3) {
            try {
                int month = Integer.parseInt(parts[0].trim());
                int day   = Integer.parseInt(parts[1].trim());
                int year  = Integer.parseInt(parts[2].trim());
                if (year < 100) year += 1900;
                LocalDate.of(year, month, day); // validates ranges
                if (year < 1900 || year > LocalDate.now().getYear()) return null;
                return month + "/" + day + "/" + year;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Calculates current age from an "M/D/YYYY" birthday string. Returns 0 on any failure. */
    public static int calculateAge(String birthday) {
        if (birthday == null) return 0;
        try {
            String[] p = birthday.split("/");
            LocalDate bd = LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            return (int) ChronoUnit.YEARS.between(bd, LocalDate.now());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Extracts the birth year from an "M/D/YYYY" birthday string. Returns 0 on any failure. */
    public static int birthYear(String birthday) {
        if (birthday == null) return 0;
        try {
            return Integer.parseInt(birthday.split("/")[2]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parses a target-age preference like "25" or "18-25" into {min, max}.
     * Returns null when the input is blank or unparseable. (Performs no
     * bounds checking — use {@link #isValidTargetAge} for that.)
     */
    public static int[] parseAgeRange(String targetAge) {
        if (targetAge == null || targetAge.trim().isEmpty()) return null;
        targetAge = targetAge.trim();
        if (targetAge.contains("-")) {
            String[] p = targetAge.split("-", 2);
            try { return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())}; }
            catch (Exception e) { return null; }
        }
        try { int v = Integer.parseInt(targetAge); return new int[]{v, v}; }
        catch (Exception e) { return null; }
    }

    /**
     * Validates target age input from an applicant. Accepts single ages
     * ("25") or ranges ("18-25", "18 - 25", en/em dashes). Every age must be
     * within 18–70 and ranges must be low-to-high.
     */
    public static boolean isValidTargetAge(String input) {
        input = input.trim();

        // Normalize en-dash (–) and em-dash (—) to a standard hyphen
        input = input.replaceAll("[–—]", "-");

        if (input.contains("-")) {
            String[] parts = input.split("-");
            if (parts.length != 2) {
                return false; // Invalid format with multiple dashes
            }
            try {
                int minAge = Integer.parseInt(parts[0].trim());
                int maxAge = Integer.parseInt(parts[1].trim());
                return minAge >= 18 && minAge <= 70 && maxAge >= 18 && maxAge <= 70 && minAge <= maxAge;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            try {
                int age = Integer.parseInt(input);
                return age >= 18 && age <= 70;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
