package com.agape;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;

/**
 * Quickmatch: picks a random eligible candidate for a user who runs
 * /quickmatch, enforcing enrollment, the "single" role, biweekly spin
 * limits (1 normal / 5 premium), the 24-hour match cooldown, age brackets,
 * and precluded pairs. Every attempt (including blocked ones) is logged to
 * user_content/matches/{userId}.json.
 */
public class MatchmakingEngine {

    private static final String MATCHES_DIR  = "user_content/matches/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final int MATCH_COOLDOWN_DAYS      = 1; // 24 hours
    private static final int QUICKMATCH_SPINS_BASIC   = 1;
    private static final int QUICKMATCH_SPINS_PREMIUM = 5;

    // Anchor for biweekly windows — must be a Monday; all windows are 14-day multiples from this date.
    private static final java.time.LocalDate WINDOW_ANCHOR = java.time.LocalDate.of(2025, 1, 6);

    // ─── Public result type ───────────────────────────────────────────────────

    public static class MatchResult {
        public final String matchedUserId;
        public final AppState matchedProfile;

        MatchResult(String matchedUserId, AppState matchedProfile) {
            this.matchedUserId = matchedUserId;
            this.matchedProfile = matchedProfile;
        }
    }

    // ─── Log structures ───────────────────────────────────────────────────────

    static class MatchEntry {
        String type;        // "RAN_QUICKMATCH" or "RECEIVED_MATCH"
        String result;      // "FOUND_MATCH" or "NO_MATCH" (null for RECEIVED_MATCH)
        String matchedWith; // userId of the other person (null when result is NO_MATCH)
        String timestamp;   // ISO-8601 local datetime
    }

    static class MatchLog {
        List<MatchEntry> entries = new ArrayList<>();
    }

    /**
     * Quickmatch: picks and returns one eligible match for the given user, logging the result.
     *
     * Pre-checks (user must pass all):
     *   1. Has an ACCEPTED profile with quickmatchEnrolled = true
     *   2. Has a Discord role containing "single" in their home guild
     *   3. Has NOT run quickmatch in the last 14 days
     *   4. Has NOT been part of a successful match in the last 24 hours
     *
     * Candidate eligibility:
     *   - ACCEPTED profile, quickmatchEnrolled = true
     *   - Falls within the same age bracket as the requesting user
     *   - Has NOT been part of a successful match in the last 24 hours
     *
     * Age brackets: 18-22 | 23-27 | 28-32 | 33-40 | 41-55 | 56+
     *
     * @param userId Discord user ID of the person running quickmatch
     * @param jda    JDA instance used to verify the "single" role
     * @return a MatchResult with the matched user's ID and profile, or null if prerequisites fail or no candidates found
     */
    public static MatchResult quickmatch(String userId, JDA jda) {

        // Load log first so every exit path can be recorded
        MatchLog userLog = loadMatchLog(userId);

        // 1. Load and validate the requesting user
        AppState user = ProfileRepository.load(userId);
        if (user == null) {
            System.out.println("Quickmatch: No profile found for user " + userId);
            logBlocked(userId, userLog, "No profile found");
            return null;
        }
        if (!"ACCEPTED".equals(user.status)) {
            System.out.println("Quickmatch: User " + userId + " does not have an accepted profile.");
            logBlocked(userId, userLog, "Profile not accepted");
            return null;
        }
        if (user.softDeleted) {
            System.out.println("Quickmatch: User " + userId + " has a soft-deleted profile.");
            logBlocked(userId, userLog, "Profile soft-deleted");
            return null;
        }
        if (!user.quickmatchEnrolled) {
            System.out.println("Quickmatch: User " + userId + " is not enrolled in quickmatch.");
            logBlocked(userId, userLog, "Not enrolled in quickmatch");
            return null;
        }
        if (!isSingleInGuild(userId, user.guildId, jda)) {
            System.out.println("Quickmatch: User " + userId + " does not have the 'single' role.");
            logBlocked(userId, userLog, "Missing single role");
            return null;
        }

        // 2. Cooldown checks
        int spinsAllowed = isPremiumUser(userId, user.guildId, jda) ? QUICKMATCH_SPINS_PREMIUM : QUICKMATCH_SPINS_BASIC;
        if (ranQuickmatchWithin(userLog, spinsAllowed)) {
            System.out.println("Quickmatch: User " + userId + " has used all " + spinsAllowed + " quickmatch spin(s) this window.");
            logBlocked(userId, userLog, "Spin limit reached for this window");
            return null;
        }
        if (wasMatchedWithin(userLog, MATCH_COOLDOWN_DAYS)) {
            System.out.println("Quickmatch: User " + userId + " was matched within the last 24 hours.");
            logBlocked(userId, userLog, "Match cooldown active");
            return null;
        }

        // 3. Determine user's age bracket
        int userAge = AgeUtils.calculateAge(user.birthday);
        int[] bracket = ageBracket(userAge);
        if (bracket == null) {
            System.err.println("Quickmatch: User " + userId + " has an invalid or missing birthday (computed age = " + userAge + ").");
            logBlocked(userId, userLog, "Invalid or missing birthday");
            return null;
        }

        // 4. Scan all profiles for eligible candidates
        File[] files = ProfileRepository.listProfileFiles();
        if (files.length == 0) {
            logQuickmatch(userId, userLog, "NO_MATCH", null);
            return null;
        }

        List<AppState> candidates = new ArrayList<>();
        List<String> candidateIds = new ArrayList<>();
        for (File file : files) {
            String candidateId = ProfileRepository.userIdFromFile(file);
            if (candidateId.equals(userId)) continue;

            AppState candidate = ProfileRepository.load(candidateId);
            if (candidate == null) continue;
            if (!"ACCEPTED".equals(candidate.status)) continue;
            if (candidate.softDeleted) continue;
            if (!candidate.quickmatchEnrolled) continue;
            if (!MembershipVerifier.verifyMembership(candidateId, candidate.guildId, jda)) continue;

            int candidateAge = AgeUtils.calculateAge(candidate.birthday);
            if (candidateAge < bracket[0] || candidateAge > bracket[1]) continue;

            MatchLog candidateLog = loadMatchLog(candidateId);
            if (wasMatchedWithin(candidateLog, MATCH_COOLDOWN_DAYS)) continue;
            if (CompatibilityEngine.isPrecluded(userId, candidateId)) continue;

            candidates.add(candidate);
            candidateIds.add(candidateId);
        }

        if (candidates.isEmpty()) {
            System.out.println("Quickmatch: No eligible candidates for user " + userId
                    + " (age bracket " + bracket[0] + "-" + (bracket[1] == Integer.MAX_VALUE ? "∞" : bracket[1]) + ").");
            logQuickmatch(userId, userLog, "NO_MATCH", null);
            return null;
        }

        // 5. Pick a random candidate
        int idx = new Random().nextInt(candidates.size());
        AppState match = candidates.get(idx);
        String matchId = candidateIds.get(idx);

        System.out.println("Quickmatch: Matched user " + userId + " with " + matchId + ".");

        // 6. Log for both parties
        logQuickmatch(userId, userLog, "FOUND_MATCH", matchId);
        MatchLog matchLog = loadMatchLog(matchId);
        logReceivedMatch(matchId, matchLog, userId);

        return new MatchResult(matchId, match);
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    private static void logBlocked(String userId, MatchLog log, String reason) {
        MatchEntry entry = new MatchEntry();
        entry.type = "RAN_QUICKMATCH";
        entry.result = "BLOCKED: " + reason;
        entry.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        log.entries.add(entry);
        saveMatchLog(userId, log);
    }

    private static void logQuickmatch(String userId, MatchLog log, String result, String matchedWith) {
        MatchEntry entry = new MatchEntry();
        entry.type = "RAN_QUICKMATCH";
        entry.result = result;
        entry.matchedWith = matchedWith;
        entry.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        log.entries.add(entry);
        saveMatchLog(userId, log);
    }

    private static void logReceivedMatch(String userId, MatchLog log, String matchedWith) {
        MatchEntry entry = new MatchEntry();
        entry.type = "RECEIVED_MATCH";
        entry.matchedWith = matchedWith;
        entry.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        log.entries.add(entry);
        saveMatchLog(userId, log);
    }

    /** Returns the start (Monday midnight) of the current biweekly window. */
    private static LocalDateTime currentWindowStart() {
        long daysSinceAnchor = ChronoUnit.DAYS.between(WINDOW_ANCHOR, java.time.LocalDate.now());
        long windowIndex = Math.max(0, daysSinceAnchor / 14);
        return WINDOW_ANCHOR.plusDays(windowIndex * 14).atStartOfDay();
    }

    /** Returns true if the user has used {@code maxSpins} or more successful quickmatches in the current biweekly window. NO_MATCH/BLOCKED runs are ignored. */
    private static boolean ranQuickmatchWithin(MatchLog log, int maxSpins) {
        LocalDateTime windowStart = currentWindowStart();
        int count = 0;
        for (MatchEntry entry : log.entries) {
            if (!"RAN_QUICKMATCH".equals(entry.type)) continue;
            if (!"FOUND_MATCH".equals(entry.result)) continue;
            try {
                if (LocalDateTime.parse(entry.timestamp, TIMESTAMP_FMT).isAfter(windowStart)) count++;
            } catch (Exception ignored) {}
        }
        return count >= maxSpins;
    }

    /** Returns true if the user was part of any successful match (as runner or recipient) within the last {@code days} days. */
    private static boolean wasMatchedWithin(MatchLog log, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        for (MatchEntry entry : log.entries) {
            if (entry.matchedWith == null) continue; // NO_MATCH entries have no matchedWith
            try {
                if (LocalDateTime.parse(entry.timestamp, TIMESTAMP_FMT).isAfter(cutoff)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private static MatchLog loadMatchLog(String userId) {
        File file = new File(MATCHES_DIR + userId + ".json");
        if (!file.exists()) return new MatchLog();
        try {
            MatchLog log = GSON.fromJson(new FileReader(file), MatchLog.class);
            if (log == null || log.entries == null) return new MatchLog();
            return log;
        } catch (Exception e) {
            System.err.println("Quickmatch: Failed to read match log for " + userId + ": " + e.getMessage());
            return new MatchLog();
        }
    }

    private static void saveMatchLog(String userId, MatchLog log) {
        try {
            File dir = new File(MATCHES_DIR);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(MATCHES_DIR + userId + ".json"))) {
                GSON.toJson(log, writer);
            }
        } catch (Exception e) {
            System.err.println("Quickmatch: Failed to save match log for " + userId + ": " + e.getMessage());
        }
    }

    // ─── Profile / age helpers ────────────────────────────────────────────────

    /** Returns [min, max] for the age bracket, or null if age < 18. Package-private for tests. */
    static int[] ageBracket(int age) {
        if (age >= 56) return new int[]{56, Integer.MAX_VALUE};
        if (age >= 41) return new int[]{41, 55};
        if (age >= 33) return new int[]{33, 40};
        if (age >= 28) return new int[]{28, 32};
        if (age >= 23) return new int[]{23, 27};
        if (age >= 18) return new int[]{18, 22};
        return null;
    }

    /**
     * Returns true if the user qualifies for premium quickmatch spins
     * (see {@link Roles#isPremium}). Retrieves the member via the API.
     */
    private static boolean isPremiumUser(String userId, String guildId, JDA jda) {
        if (guildId == null || jda == null) return false;
        try {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) return false;
            net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(userId).complete();
            return Roles.isPremium(member);
        } catch (Exception e) {
            System.err.println("Quickmatch: Error checking premium roles for user " + userId + ": " + e.getMessage());
        }
        return false;
    }

    /** Returns true if the Discord member has the "single" role (see {@link Roles#isSingle}). */
    private static boolean isSingleInGuild(String userId, String guildId, JDA jda) {
        if (guildId == null || jda == null) return false;
        try {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) return false;
            net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(userId).complete();
            return Roles.isSingle(member);
        } catch (Exception e) {
            System.err.println("Quickmatch: Error checking roles for user " + userId + ": " + e.getMessage());
        }
        return false;
    }
}
