package com.agape;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

/**
 * Owns the 24-hour lifecycle of match threads and the strike/pardon system.
 *
 * Records live in user_content/qm_threads/ (quickmatch) and
 * user_content/mm_threads/ (manual), keyed {maleId}_{femaleId}.json.
 * A 5-minute scheduler in AgapeBot calls the check* methods, which send
 * escalating reminders and archive expired threads. On expiry,
 * non-responders are penalized: quickmatch → strike + QM unenrollment;
 * manual match → profile soft-delete. Strikes/pardons expire after 6 months
 * and live in data/strikes/.
 */
public class ThreadManager {

    private static final String THREADS_DIR    = "user_content/qm_threads/";
    private static final String MM_THREADS_DIR = "user_content/mm_threads/";
    private static final String STRIKES_DIR    = "data/strikes/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    static final long THREAD_LIFESPAN_HOURS = 24;

    // ─── Shutdown coordination ─────────────────────────────────────────────────
    static volatile boolean shuttingDown = false;
    private static final AtomicInteger activeClosures = new AtomicInteger(0);

    // ─── Data structures ──────────────────────────────────────────────────────

    static class Strike {
        String timestamp; // ISO-8601 local datetime
        String threadId;
    }

    static class Pardon {
        String timestamp; // ISO-8601 local datetime
        String grantedBy; // matchmaker userId
    }

    static class UserStrikes {
        String userId;
        List<Strike> strikes = new ArrayList<>();
        List<Pardon> pardons = new ArrayList<>();
    }

    static class ThreadMessage {
        String authorId;
        String authorName;
        String content;
        String timestamp; // ISO-8601 offset datetime
    }

    static class QMThread {
        String threadId;
        String guildId;
        String maleId;
        String femaleId;
        String createdAt;  // ISO-8601 local datetime
        String closedAt;   // null while OPEN
        String status;     // "OPEN" or "ARCHIVED"
        String matchType;           // "QUICKMATCH" or "MANUAL"
        String closeReason;         // "FORCE_CLOSED" if admin-closed; null for natural expiry
        String firstMessageAt;      // ISO-8601 local datetime of first non-bot message
        List<String> messagedBy     = new ArrayList<>(); // user IDs (maleId/femaleId) who have sent ≥1 message
        List<String> confirmedBy    = new ArrayList<>();
        List<String> declinedBy     = new ArrayList<>();
        List<String> notificationsSent = new ArrayList<>();
        List<ThreadMessage> messages = new ArrayList<>();
        boolean goodNewsDMSent = false; // true once the "your match replied" DM has been sent
        String endedReason;   // null = not ended; "ENDED" = manually ended; "GHOSTED:{userId}" = ghosted
    }

    // ─── Shutdown management ──────────────────────────────────────────────────

    /**
     * Signals shutdown and blocks until all in-progress archiveAndDelete calls complete
     * or {@code timeoutMs} elapses. Call this from a JVM shutdown hook before exiting.
     */
    public static void initiateShutdown(long timeoutMs) {
        shuttingDown = true;
        int pending = activeClosures.get();
        if (pending > 0) {
            System.out.println("ThreadManager: Shutdown initiated — waiting for " + pending + " active closure(s)...");
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (activeClosures.get() > 0 && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
        }
        int remaining = activeClosures.get();
        if (remaining > 0) {
            System.err.println("ThreadManager: Shutdown timed out — " + remaining + " closure(s) may be incomplete.");
        } else {
            System.out.println("ThreadManager: All closures complete, safe to exit.");
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Called immediately after a match thread is created in Discord. */
    public static void registerThread(String threadId, String guildId, String maleId, String femaleId, String matchType) {
        QMThread record = new QMThread();
        record.threadId  = threadId;
        record.guildId   = guildId;
        record.maleId    = maleId;
        record.femaleId  = femaleId;
        record.createdAt = LocalDateTime.now().format(FMT);
        record.status    = "OPEN";
        record.matchType = matchType;
        save(maleId, femaleId, record);
        System.out.println("ThreadManager: Registered thread " + threadId + " (" + maleId + " + " + femaleId + ") [" + matchType + "].");
    }

    /**
     * Scans all OPEN thread records and archives any that are past THREAD_LIFESPAN_HOURS.
     * Safe to call on boot and on a recurring schedule.
     */
    public static void checkExpiredThreads(JDA jda) {
        if (shuttingDown) return;
        LocalDateTime cutoff = LocalDateTime.now().minusHours(THREAD_LIFESPAN_HOURS);
        int expired = 0;

        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;

            for (File file : files) {
                try {
                    QMThread record = GSON.fromJson(new FileReader(file), QMThread.class);
                    if (record == null || !"OPEN".equals(record.status) || record.createdAt == null) continue;

                    LocalDateTime created = LocalDateTime.parse(record.createdAt, FMT);
                    if (created.isAfter(cutoff)) continue;

                    archiveAndDelete(record, jda);
                    expired++;
                } catch (Exception e) {
                    System.err.println("ThreadManager: Error processing " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        if (expired > 0) {
            System.out.println("ThreadManager: Processing " + expired + " expired thread(s).");
        }
    }

    // ─── Public API (continued) ───────────────────────────────────────────────

    /**
     * Finds the QMThread log for the given pair (either ordering), searching both directories.
     * Returns {@code null} if no log file exists for the pair.
     */
    public static QMThread findThread(String userId1, String userId2) {
        QMThread r = tryLoad(userId1, userId2);
        return r != null ? r : tryLoad(userId2, userId1);
    }

    /** Finds the Manual Match thread log for the given pair, looking only in mm_threads/. */
    public static QMThread findMMThread(String userId1, String userId2) {
        QMThread r = tryLoadMM(userId1, userId2);
        return r != null ? r : tryLoadMM(userId2, userId1);
    }

    private static QMThread tryLoadMM(String id1, String id2) {
        File f = new File(MM_THREADS_DIR + id1 + "_" + id2 + ".json");
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f)) {
            return GSON.fromJson(reader, QMThread.class);
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to load " + f.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the most recent time the user was matched — the newest {@code createdAt}
     * across every quickmatch and manual thread record that includes them — or
     * {@code null} if they have never been matched. Used by /compat-algo to
     * invisibly de-prioritize users who matched in the last 72 hours.
     */
    public static LocalDateTime lastMatchTime(String userId) {
        LocalDateTime latest = null;
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    QMThread record = GSON.fromJson(reader, QMThread.class);
                    if (record == null || record.createdAt == null) continue;
                    if (!userId.equals(record.maleId) && !userId.equals(record.femaleId)) continue;
                    LocalDateTime created = LocalDateTime.parse(record.createdAt, FMT);
                    if (latest == null || created.isAfter(latest)) latest = created;
                } catch (Exception ignored) {}
            }
        }
        return latest;
    }

    /**
     * Returns {@code true} if the user is currently in an active (non-archived)
     * Manual Match thread. Used by /compat-algo to heavily de-prioritize users
     * who already occupy a matchmaking room, steering matchmakers away from
     * creating a second Manual Match thread for the same person.
     */
    public static boolean hasActiveManualMatch(String userId) {
        File dir = new File(MM_THREADS_DIR);
        if (!dir.exists()) return false;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return false;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                QMThread record = GSON.fromJson(reader, QMThread.class);
                if (record == null || !"OPEN".equals(record.status)) continue;
                if (userId.equals(record.maleId) || userId.equals(record.femaleId)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Returns {@code true} if the user has a confirmed match (both parties confirmed)
     * whose thread has since been closed (archived) without being marked as Ended —
     * i.e. a relationship that quietly fell apart (ghosting or breakup) but was never
     * recorded via /end-match. Used by /compat-algo for a small de-prioritization nudge.
     */
    public static boolean hasUnendedClosedMatch(String userId) {
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    QMThread record = GSON.fromJson(reader, QMThread.class);
                    if (record == null || !"ARCHIVED".equals(record.status)) continue;
                    if (record.endedReason != null) continue;
                    if (!userId.equals(record.maleId) && !userId.equals(record.femaleId)) continue;
                    if (record.confirmedBy != null
                            && record.confirmedBy.contains(record.maleId)
                            && record.confirmedBy.contains(record.femaleId)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    /**
     * True if a record represents a still-active match that ought to be ended
     * when a participant leaves: not already ended, and either an OPEN thread
     * (awaiting confirmation) or a confirmed pairing. Declined/timed-out closed
     * threads are not active matches and are left untouched. Package-private for
     * testing.
     */
    static boolean isActiveMatch(QMThread r) {
        if (r == null || r.endedReason != null) return false;
        if ("OPEN".equals(r.status)) return true;
        return r.confirmedBy != null
            && r.confirmedBy.contains(r.maleId)
            && r.confirmedBy.contains(r.femaleId);
    }

    /**
     * Forcibly ends every still-active match involving {@code userId}, used when
     * that user has left the server (admins cannot run {@code /end-match} against
     * someone who can no longer be @-mentioned). Each affected record is tagged
     * {@code LEFT_SERVER:{userId}} and any still-open thread is force-closed.
     * Returns the number of matches ended.
     */
    public static int endMatchesForDepartedMember(String userId, JDA jda) {
        int ended = 0;
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                QMThread record;
                try (FileReader reader = new FileReader(file)) {
                    record = GSON.fromJson(reader, QMThread.class);
                } catch (Exception e) { continue; }
                if (record == null) continue;
                if (!userId.equals(record.maleId) && !userId.equals(record.femaleId)) continue;
                if (!isActiveMatch(record)) continue;
                forceEndForDeparture(record, userId, jda);
                ended++;
            }
        }
        if (ended > 0) {
            System.out.println("ThreadManager: Force-ended " + ended + " match(es) — user "
                + userId + " has left the server.");
        }
        return ended;
    }

    /**
     * Scans every active match and force-ends any whose participant has left the
     * guild. This catches departures that happened while the bot was offline (the
     * live {@code GuildMemberRemove} event only fires while connected). Membership
     * is checked via {@link MembershipVerifier#verifyMembership}, which also
     * soft-deletes the absent member's profile. Returns the number ended.
     */
    public static int sweepDepartedMemberMatches(JDA jda) {
        int ended = 0;
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                QMThread record;
                try (FileReader reader = new FileReader(file)) {
                    record = GSON.fromJson(reader, QMThread.class);
                } catch (Exception e) { continue; }
                if (!isActiveMatch(record)) continue;
                for (String pid : new String[]{record.maleId, record.femaleId}) {
                    if (!MembershipVerifier.verifyMembership(pid, record.guildId, jda)) {
                        forceEndForDeparture(record, pid, jda);
                        ended++;
                        break; // one departed participant is enough to end the match
                    }
                }
            }
        }
        if (ended > 0) {
            System.out.println("ThreadManager: Departed-member sweep force-ended " + ended + " match(es).");
        }
        return ended;
    }

    /**
     * Marks a record as ended because {@code departedUserId} left the server, and
     * force-closes the thread if it is still open. The {@code LEFT_SERVER} tag is
     * saved before any close so it survives the archival write.
     */
    private static void forceEndForDeparture(QMThread record, String departedUserId, JDA jda) {
        record.endedReason = "LEFT_SERVER:" + departedUserId;
        save(record.maleId, record.femaleId, record);
        if ("OPEN".equals(record.status) && jda != null) {
            adminCloseThread(record.threadId, jda); // archives + preserves endedReason
        }
    }

    /**
     * Scans all thread records for one whose Discord thread ID matches the given channel ID.
     * Returns {@code null} if not found.
     */
    public static QMThread findThreadByChannelId(String threadId) {
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    QMThread record = GSON.fromJson(reader, QMThread.class);
                    if (record != null && threadId.equals(record.threadId)) return record;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Records a confirmation from {@code userId} in the thread with the given Discord channel ID.
     * Returns {@code true} if both matched users have now confirmed.
     */
    public static boolean recordConfirmation(String threadId, String userId) {
        QMThread record = findThreadByChannelId(threadId);
        if (record == null) return false;
        if (record.confirmedBy == null) record.confirmedBy = new ArrayList<>();
        if (!record.confirmedBy.contains(userId)) {
            record.confirmedBy.add(userId);
            save(record.maleId, record.femaleId, record);
        }
        return record.confirmedBy.contains(record.maleId) && record.confirmedBy.contains(record.femaleId);
    }

    /** Records that {@code userId} used /decline in the given thread. */
    public static void recordDecline(String threadId, String userId) {
        QMThread record = findThreadByChannelId(threadId);
        if (record == null) return;
        if (record.declinedBy == null) record.declinedBy = new ArrayList<>();
        if (!record.declinedBy.contains(userId)) {
            record.declinedBy.add(userId);
            save(record.maleId, record.femaleId, record);
        }
    }

    /**
     * Issues a strike to {@code userId} for failing to /confirm or /decline in a quickmatch thread.
     * Unenrolls them from quickmatch and sends a DM notice.
     */
    public static void addStrike(String userId, String threadId, JDA jda) {
        UserStrikes us = writeStrikeRecord(userId, threadId);

        // Unenroll from quickmatch
        AppState state = ProfileRepository.load(userId);
        if (state != null && state.quickmatchEnrolled) {
            state.quickmatchEnrolled = false;
            ProfileRepository.save(userId, state);
        }

        int recentStrikes = getRecentStrikeCount(userId);
        String expiryNote = buildExpiryNote(getNextStrikeExpiry(us));
        String statusNote = recentStrikes >= 3
            ? "\n\n⛔ You have reached **" + recentStrikes + "/3 strikes** in the past 6 months and cannot re-enroll in the quickmatch pool until your oldest strike ages out."
            : "\n\n📊 You currently have **" + recentStrikes + "/3 strike(s)** in the past 6 months. You may re-enroll in the quickmatch pool at any time using `/toggle-qm`.";

        jda.openPrivateChannelById(userId).queue(
            ch -> ch.sendMessage(
                "⚠️ **Quickmatch Strike**\n\n"
                + "You received a strike on your matchmaking record because you did not use `/confirm` before your quickmatch thread closed. "
                + "You have been unenrolled from the quickmatch pool."
                + statusNote + expiryNote
            ).queue(),
            e -> System.err.println("ThreadManager: Could not DM strike notice to " + userId + ": " + e.getMessage())
        );
        System.out.println("ThreadManager: Strike issued to " + userId + " (" + recentStrikes + " in last 6 months).");
    }

    /** Writes a new strike entry for {@code userId} and returns the updated UserStrikes. */
    private static UserStrikes writeStrikeRecord(String userId, String threadId) {
        new File(STRIKES_DIR).mkdirs();
        File strikeFile = new File(STRIKES_DIR + userId + ".json");
        UserStrikes us;
        if (strikeFile.exists()) {
            try (FileReader r = new FileReader(strikeFile)) {
                us = GSON.fromJson(r, UserStrikes.class);
                if (us == null) us = new UserStrikes();
            } catch (Exception e) {
                us = new UserStrikes();
            }
        } else {
            us = new UserStrikes();
        }
        if (us.strikes == null) us.strikes = new ArrayList<>();
        us.userId = userId;
        Strike s = new Strike();
        s.timestamp = LocalDateTime.now().format(FMT);
        s.threadId  = threadId;
        us.strikes.add(s);
        try (FileWriter w = new FileWriter(strikeFile)) {
            GSON.toJson(us, w);
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to save strike for " + userId + ": " + e.getMessage());
        }
        return us;
    }

    /** Returns the expiry datetime of the oldest still-active (within 6 months) strike, or null if none. */
    private static java.time.LocalDateTime getNextStrikeExpiry(UserStrikes us) {
        if (us == null || us.strikes == null) return null;
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
        LocalDateTime oldest = null;
        for (Strike st : us.strikes) {
            if (st.timestamp == null) continue;
            try {
                LocalDateTime ts = LocalDateTime.parse(st.timestamp, FMT);
                if (ts.isAfter(cutoff) && (oldest == null || ts.isBefore(oldest))) oldest = ts;
            } catch (Exception ignored) {}
        }
        return oldest != null ? oldest.plusMonths(6) : null;
    }

    /** Formats the next-expiry datetime into a DM note, or returns "" if null. */
    private static String buildExpiryNote(java.time.LocalDateTime expiry) {
        if (expiry == null) return "";
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiry);
        String dateStr = expiry.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        return "\n\n⏳ Your oldest active strike expires on **" + dateStr + "** (in approximately " + days + " day(s)).";
    }

    /** Returns the number of strikes {@code userId} has received in the past 6 months. */
    public static int getRecentStrikeCount(String userId) {
        File strikeFile = new File(STRIKES_DIR + userId + ".json");
        if (!strikeFile.exists()) return 0;
        try (FileReader r = new FileReader(strikeFile)) {
            UserStrikes us = GSON.fromJson(r, UserStrikes.class);
            if (us == null || us.strikes == null) return 0;
            LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
            int count = 0;
            for (Strike st : us.strikes) {
                if (st.timestamp != null && LocalDateTime.parse(st.timestamp, FMT).isAfter(cutoff)) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns the number of pardons {@code userId} has received in the past 6 months. */
    public static int getRecentPardonCount(String userId) {
        File strikeFile = new File(STRIKES_DIR + userId + ".json");
        if (!strikeFile.exists()) return 0;
        try (FileReader r = new FileReader(strikeFile)) {
            UserStrikes us = GSON.fromJson(r, UserStrikes.class);
            if (us == null || us.pardons == null) return 0;
            LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
            int count = 0;
            for (Pardon p : us.pardons) {
                if (p.timestamp != null && LocalDateTime.parse(p.timestamp, FMT).isAfter(cutoff)) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns max(0, recent strikes − recent pardons) — the effective standing used for eligibility gates. */
    public static int getNetStrikeCount(String userId) {
        return Math.max(0, getRecentStrikeCount(userId) - getRecentPardonCount(userId));
    }

    /**
     * Issues a pardon to {@code userId}, recorded under their strike file.
     * Pardons offset strikes 1-for-1 and expire after 6 months, same as strikes.
     * Sends a DM to the pardoned user summarising their updated standing.
     */
    public static void addPardon(String userId, String grantedBy, JDA jda) {
        new File(STRIKES_DIR).mkdirs();
        File strikeFile = new File(STRIKES_DIR + userId + ".json");
        UserStrikes us;
        if (strikeFile.exists()) {
            try (FileReader r = new FileReader(strikeFile)) {
                us = GSON.fromJson(r, UserStrikes.class);
                if (us == null) us = new UserStrikes();
            } catch (Exception e) {
                us = new UserStrikes();
            }
        } else {
            us = new UserStrikes();
        }
        if (us.strikes == null) us.strikes = new ArrayList<>();
        if (us.pardons == null) us.pardons = new ArrayList<>();
        us.userId = userId;
        Pardon pardon = new Pardon();
        pardon.timestamp = LocalDateTime.now().format(FMT);
        pardon.grantedBy = grantedBy;
        us.pardons.add(pardon);
        try (FileWriter w = new FileWriter(strikeFile)) {
            GSON.toJson(us, w);
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to save pardon for " + userId + ": " + e.getMessage());
        }

        int strikes = getRecentStrikeCount(userId);
        int pardons = getRecentPardonCount(userId);
        int net     = Math.max(0, strikes - pardons);
        String standing = net < 3
            ? "✅ Your account is in good standing (**" + net + "/3**). You may re-enroll in the quickmatch pool using `/toggle-qm`."
            : "⛔ Your aggregate standing is still **" + net + "/3 strikes**. Additional pardons are needed before you can re-enroll.";

        jda.openPrivateChannelById(userId).queue(
            ch -> ch.sendMessage(
                "✅ **Matchmaking Pardon**\n\n"
                + "A matchmaker has issued a pardon on your matchmaking record.\n\n"
                + "📊 You currently have **" + strikes + " active strike(s)** and **" + pardons + " active pardon(s)** in the past 6 months.\n"
                + standing
            ).queue(),
            e -> System.err.println("ThreadManager: Could not DM pardon notice to " + userId + ": " + e.getMessage())
        );
        System.out.println("ThreadManager: Pardon issued to " + userId + " (net standing: " + net + "/3).");
    }

    /**
     * Checks all OPEN MANUAL match threads and sends nudge notifications at the
     * following milestones (measured from thread creation), skipping any already sent
     * and skipping entirely once both parties have responded:
     *
     *   "first"  — 20 min after first non-bot message, OR 1 hr with no messages
     *   "6hr"    — 6 hours
     *   "12hr"   — 12 hours (urgent)
     *   "23hr"   — 23 hours (final warning)
     *
     * Safe to call on boot and on a recurring schedule.
     */
    public static void checkManualMatchNotifications(JDA jda) {
        List<File> allFiles = new ArrayList<>();
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File dir = new File(dirPath);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) for (File f : files) allFiles.add(f);
        }
        if (allFiles.isEmpty()) return;

        for (File file : allFiles) {
            try {
                QMThread record = GSON.fromJson(new FileReader(file), QMThread.class);
                if (record == null
                        || !"OPEN".equals(record.status)
                        || !"MANUAL".equals(record.matchType)
                        || record.createdAt == null) continue;

                if (bothResponded(record)) continue;

                LocalDateTime created = LocalDateTime.parse(record.createdAt, FMT);
                LocalDateTime now     = LocalDateTime.now();

                if (record.notificationsSent == null) record.notificationsSent = new ArrayList<>();

                // Lazily populate firstMessageAt and messagedBy by fetching the thread history.
                // Keep fetching until both matched users have messaged (or firstMessageAt is set).
                boolean needsHistoryUpdate = record.firstMessageAt == null
                    || record.messagedBy == null
                    || !(record.messagedBy.contains(record.maleId) && record.messagedBy.contains(record.femaleId));
                if (needsHistoryUpdate) {
                    net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel thread =
                        jda.getThreadChannelById(record.threadId);
                    if (thread != null) {
                        final QMThread rec = record;
                        final int messagedByBefore = rec.messagedBy != null ? rec.messagedBy.size() : 0;
                        thread.getHistoryFromBeginning(50).queue(history -> {
                            List<Message> msgs = new ArrayList<>(history.getRetrievedHistory());
                            msgs.sort(Comparator.comparing(Message::getTimeCreated));
                            if (rec.messagedBy == null) rec.messagedBy = new ArrayList<>();
                            for (Message msg : msgs) {
                                if (msg.getAuthor().isBot()) continue;
                                String authorId = msg.getAuthor().getId();
                                if (rec.firstMessageAt == null) {
                                    rec.firstMessageAt = msg.getTimeCreated()
                                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime().format(FMT);
                                }
                                if ((authorId.equals(rec.maleId) || authorId.equals(rec.femaleId))
                                        && !rec.messagedBy.contains(authorId)) {
                                    rec.messagedBy.add(authorId);
                                }
                            }
                            // Both parties have now posted and the first poster hasn't been notified yet
                            if (messagedByBefore < 2 && rec.messagedBy.size() >= 2 && !rec.goodNewsDMSent) {
                                rec.goodNewsDMSent = true;
                                String firstPosterId = rec.messagedBy.get(0);
                                String goodNewsMsg = "🎉 **Good news!** Your match in Agape has replied in your thread — go check it out! "
                                    + "Once you've both had a chance to connect and share your deal-breakers, "
                                    + "use **/confirm** if you'd like to continue, or **/decline** if not.";
                                jda.retrieveUserById(firstPosterId).queue(
                                    u -> u.openPrivateChannel().queue(ch -> ch.sendMessage(goodNewsMsg).queue()),
                                    err -> System.err.println("ThreadManager: Could not send good-news DM to " + firstPosterId + ": " + err.getMessage())
                                );
                            }
                            save(rec.maleId, rec.femaleId, rec);
                        }, err -> {});
                    }
                    // firstMessageAt not yet set — only the 1-hr fallback below is evaluated this cycle
                }

                // ── First nudge: 20 min after first message OR 1 hr with no messages ──
                if (!record.notificationsSent.contains("first")) {
                    boolean send;
                    if (record.firstMessageAt != null) {
                        send = now.isAfter(LocalDateTime.parse(record.firstMessageAt, FMT).plusMinutes(20));
                    } else {
                        send = now.isAfter(created.plusHours(1));
                    }
                    if (send) {
                        sendMatchNotification(jda, record, 1);
                        record.notificationsSent.add("first");
                        save(record.maleId, record.femaleId, record);
                    }
                }

                // ── 6-hour reminder ──
                if (!record.notificationsSent.contains("6hr") && now.isAfter(created.plusHours(6))) {
                    sendMatchNotification(jda, record, 2);
                    record.notificationsSent.add("6hr");
                    save(record.maleId, record.femaleId, record);
                }

                // ── 12-hour urgent ──
                if (!record.notificationsSent.contains("12hr") && now.isAfter(created.plusHours(12))) {
                    sendMatchNotification(jda, record, 3);
                    record.notificationsSent.add("12hr");
                    save(record.maleId, record.femaleId, record);
                }

                // ── 23-hour final warning ──
                if (!record.notificationsSent.contains("23hr") && now.isAfter(created.plusHours(23))) {
                    sendMatchNotification(jda, record, 4);
                    record.notificationsSent.add("23hr");
                    save(record.maleId, record.femaleId, record);
                }

            } catch (Exception e) {
                System.err.println("ThreadManager: Notification check error for " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Checks all OPEN QUICKMATCH threads and sends nudge notifications at the
     * following milestones (measured from thread creation):
     *
     *   "1hr"  — 1 hour
     *   "6hr"  — 6 hours
     *   "12hr" — 12 hours (urgent)
     *   "23hr" — 23 hours (final warning)
     *
     * Only users who have NOT yet run /confirm or /decline are mentioned.
     * Safe to call on boot and on a recurring schedule.
     */
    public static void checkQuickmatchNotifications(JDA jda) {
        File dir = new File(THREADS_DIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try {
                QMThread record = GSON.fromJson(new FileReader(file), QMThread.class);
                if (record == null
                        || !"OPEN".equals(record.status)
                        || !"QUICKMATCH".equals(record.matchType)
                        || record.createdAt == null) continue;

                if (bothResponded(record)) continue;

                LocalDateTime created = LocalDateTime.parse(record.createdAt, FMT);
                LocalDateTime now     = LocalDateTime.now();

                if (record.notificationsSent == null) record.notificationsSent = new ArrayList<>();

                if (!record.notificationsSent.contains("1hr") && now.isAfter(created.plusHours(1))) {
                    sendQMNotification(jda, record, 1);
                    record.notificationsSent.add("1hr");
                    save(record.maleId, record.femaleId, record);
                }

                if (!record.notificationsSent.contains("6hr") && now.isAfter(created.plusHours(6))) {
                    sendQMNotification(jda, record, 2);
                    record.notificationsSent.add("6hr");
                    save(record.maleId, record.femaleId, record);
                }

                if (!record.notificationsSent.contains("12hr") && now.isAfter(created.plusHours(12))) {
                    sendQMNotification(jda, record, 3);
                    record.notificationsSent.add("12hr");
                    save(record.maleId, record.femaleId, record);
                }

                if (!record.notificationsSent.contains("23hr") && now.isAfter(created.plusHours(23))) {
                    sendQMNotification(jda, record, 4);
                    record.notificationsSent.add("23hr");
                    save(record.maleId, record.femaleId, record);
                }

            } catch (Exception e) {
                System.err.println("ThreadManager: QM notification check error for " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Returns true if both maleId and femaleId have each sent /confirm or /decline. */
    private static boolean bothResponded(QMThread record) {
        Set<String> responded = new HashSet<>();
        if (record.confirmedBy != null) responded.addAll(record.confirmedBy);
        if (record.declinedBy  != null) responded.addAll(record.declinedBy);
        return responded.contains(record.maleId) && responded.contains(record.femaleId);
    }

    /** Returns a mention string for users who have NOT yet responded. */
    private static String buildPendingMention(QMThread record) {
        Set<String> responded = new HashSet<>();
        if (record.confirmedBy != null) responded.addAll(record.confirmedBy);
        if (record.declinedBy  != null) responded.addAll(record.declinedBy);
        StringBuilder sb = new StringBuilder();
        if (!responded.contains(record.maleId))   sb.append("<@").append(record.maleId).append("> ");
        if (!responded.contains(record.femaleId))  sb.append("<@").append(record.femaleId).append(">");
        return sb.toString().trim();
    }

    /**
     * If exactly one of the two matched users has sent a message, returns the ID of the
     * one who has NOT spoken yet. Returns null if neither or both have messaged.
     * Used to target the silent participant directly in follow-up reminders.
     */
    private static String getSilentUserId(QMThread record) {
        if (record.messagedBy == null || record.messagedBy.size() != 1) return null;
        String messager = record.messagedBy.get(0);
        if (messager.equals(record.maleId))   return record.femaleId;
        if (messager.equals(record.femaleId)) return record.maleId;
        return null;
    }

    /** True if both matched users have each sent at least one message in the thread. */
    private static boolean bothMessaged(QMThread record) {
        return record.messagedBy != null
            && record.messagedBy.contains(record.maleId)
            && record.messagedBy.contains(record.femaleId);
    }

    private static void sendMatchNotification(JDA jda, QMThread record, int level) {
        net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel thread =
            jda.getThreadChannelById(record.threadId);
        if (thread == null) {
            System.err.println("ThreadManager: Cannot send level-" + level
                + " notification — thread " + record.threadId + " not in cache.");
            return;
        }

        // After the first reminder, if only one person has spoken, ping only the silent
        // user directly. Once they message, both are in messagedBy and we switch back to
        // pinging everyone who hasn't yet confirmed or declined.
        boolean firstAlreadySent = record.notificationsSent != null && record.notificationsSent.contains("first");
        String silentUserId  = (level >= 2 && firstAlreadySent) ? getSilentUserId(record) : null;
        String mention = (silentUserId != null) ? "<@" + silentUserId + ">" : buildPendingMention(record);

        String message;
        switch (level) {
            case 1:
                if (record.firstMessageAt == null) {
                    message = "👋 " + mention + " — welcome to your match thread! Introduce yourselves and each share 3–5 of your biggest relationship deal-breakers right here. "
                        + "Once you've had a chance to connect, use **/confirm** if you'd like to continue, or **/decline** if not.";
                } else {
                    message = "👋 " + mention + " — your match has introduced themselves in this thread above! "
                        + "Take a moment to scroll up, reply, and share your own 3–5 biggest relationship deal-breakers. "
                        + "Once you've both had a chance to connect, use **/confirm** if you'd like to continue, or **/decline** if not.";

                    // DM the person who already posted — ask them to be patient
                    if (record.messagedBy != null && !record.messagedBy.isEmpty()) {
                        final String firstPosterId  = record.messagedBy.get(0);
                        final String silentPartnerId = firstPosterId.equals(record.maleId) ? record.femaleId : record.maleId;

                        AppState posterProfile = ProfileRepository.load(firstPosterId);
                        AppState silentProfile = ProfileRepository.load(silentPartnerId);

                        String countryPoster = posterProfile != null && posterProfile.country != null
                            ? CompatibilityEngine.normalizeCountry(posterProfile.country) : "";
                        String countrySilent = silentProfile != null && silentProfile.country != null
                            ? CompatibilityEngine.normalizeCountry(silentProfile.country) : "";

                        boolean differentCountries = !countryPoster.isEmpty() && !countrySilent.isEmpty()
                            && !countryPoster.equalsIgnoreCase(countrySilent);

                        String dmMsg = "💌 **Heads up!** Your match in Agape hasn't replied yet — no worries, these things take time!"
                            + (differentCountries
                                ? " Keep in mind that your match may be in a different time zone, so it might take a little longer for them to see your message."
                                : "")
                            + " Sit tight and we'll let you know when they respond! 😊\n\n"
                            + "-# Just so you know — if your match doesn't respond, **you won't be penalized in any way**. "
                            + "You'll have plenty of opportunities to be matched again!";

                        jda.retrieveUserById(firstPosterId).queue(
                            u -> u.openPrivateChannel().queue(ch -> ch.sendMessage(dmMsg).queue()),
                            err -> System.err.println("ThreadManager: Could not DM first-poster " + firstPosterId + ": " + err.getMessage())
                        );
                    }
                }
                break;
            case 2:
                if (silentUserId != null) {
                    message = "👋 " + mention + " — your match is waiting for you in this thread! "
                        + "Take a moment to scroll up, say hi, and share your 3–5 biggest deal-breakers. "
                        + "Then use **/confirm** or **/decline** to let us know your decision.\n\n"
                        + "⚠️ **Note:** If you fail to respond, you may be removed from all matchmaking pools.";
                } else if (bothMessaged(record)) {
                    message = "👋 " + mention + " — you've both introduced yourselves, great! "
                        + "When you're ready, use **/confirm** to keep going or **/decline** if it's not a fit.\n\n"
                        + "⚠️ **Note:** If you fail to respond, you may be removed from all matchmaking pools.";
                } else {
                    message = "👋 " + mention + " — a friendly nudge! Neither of you has connected yet. "
                        + "Take a moment to introduce yourselves and share 3–5 deal-breakers in this thread, "
                        + "then use **/confirm** or **/decline**.\n\n"
                        + "⚠️ **Note:** If you fail to respond, you may be removed from all matchmaking pools.";
                }
                break;
            case 3:
                if (silentUserId != null) {
                    message = "⏰ **Urgent — " + mention + ":** Your match is waiting for you to respond. "
                        + "Please reply to them as soon as possible — this thread closes in about 11 hours. "
                        + "Use **/confirm** or **/decline** when you're ready.\n\n"
                        + "⚠️ **Warning:** Failure to respond before this thread closes will result "
                        + "in removal from all matchmaking pools.";
                } else if (bothMessaged(record)) {
                    message = "⏰ **Urgent — " + mention + ":** You've both connected, but haven't made a decision yet. "
                        + "Please use **/confirm** or **/decline** as soon as possible — this thread closes in about 11 hours.\n\n"
                        + "⚠️ **Warning:** Failure to respond before this thread closes will result "
                        + "in removal from all matchmaking pools.";
                } else {
                    message = "⏰ **Urgent — " + mention + ":** You have not yet responded to this match. "
                        + "Please use **/confirm** or **/decline** as soon as possible.\n\n"
                        + "⚠️ **Warning:** Failure to respond before this thread closes will result "
                        + "in removal from all matchmaking pools.";
                }
                break;
            case 4:
                if (silentUserId != null) {
                    message = "🚨 **Final Warning — " + mention + ":** This thread closes in less than 1 hour "
                        + "and you have not replied to your match. This is your last chance — please respond and use **/confirm** or **/decline** before it closes.\n\n"
                        + "⛔ Non-response will result in immediate removal from all matchmaking pools.";
                } else if (bothMessaged(record)) {
                    message = "🚨 **Final Warning — " + mention + ":** This match thread closes in less than 1 hour. "
                        + "You've both connected, but haven't confirmed or declined yet. This is your last chance — please use **/confirm** or **/decline** before it closes.\n\n"
                        + "⛔ Non-response will result in immediate removal from all matchmaking pools.";
                } else {
                    message = "🚨 **Final Warning — " + mention + ":** This match thread closes in less than 1 hour "
                        + "and you have not responded. This is your last chance to use **/confirm** or **/decline**.\n\n"
                        + "⛔ Non-response will result in immediate removal from all matchmaking pools.";
                }
                break;
            default:
                message = "👋 " + mention + " — please use **/confirm** or **/decline** to respond to your match.";
        }

        thread.sendMessage(message).queue(
            s  -> System.out.println("ThreadManager: Sent level-" + level + " notification for thread " + record.threadId),
            e  -> System.err.println("ThreadManager: Could not send notification for thread " + record.threadId + ": " + e.getMessage())
        );
    }

    private static void sendQMNotification(JDA jda, QMThread record, int level) {
        ThreadChannel thread = jda.getThreadChannelById(record.threadId);
        if (thread == null) {
            System.err.println("ThreadManager: Cannot send QM level-" + level
                + " notification — thread " + record.threadId + " not in cache.");
            return;
        }

        String mention = buildPendingMention(record);
        if (mention.isEmpty()) return;

        // True when exactly one party has already confirmed — the other just needs to respond
        boolean partnerConfirmed = record.confirmedBy != null && record.confirmedBy.size() == 1;

        String message;
        switch (level) {
            case 1:
                message = "👋 " + mention + " — have you had a chance to DM your match yet? "
                    + "Take some time to get to know each other, and use **/confirm** to let us know you reached out!";
                break;
            case 2:
                if (partnerConfirmed) {
                    message = "👋 " + mention + " — your match has already confirmed! "
                        + "Reach out to them via DMs if you haven't yet, and use **/confirm** when you've done so.\n\n"
                        + "⚠️ **Note:** If you fail to respond, you may receive a strike on your matchmaking record.";
                } else {
                    message = "👋 " + mention + " — just a reminder to connect with your match via DMs! "
                        + "Use **/confirm** once you've reached out to your match.\n\n"
                        + "⚠️ **Note:** If you fail to respond, you may receive a strike on your matchmaking record.";
                }
                break;
            case 3:
                if (partnerConfirmed) {
                    message = "⏰ **Urgent — " + mention + ":** Your match has already confirmed — they're "
                        + "waiting on you! Please DM your match and use **/confirm** as soon as possible.\n\n"
                        + "⚠️ **Warning:** Failure to respond before this thread closes will result in a "
                        + "strike on your matchmaking record and removal from the QM Pool.";
                } else {
                    message = "⏰ **Urgent — " + mention + ":** You have not yet responded to this match. "
                        + "Please DM your match and use **/confirm** as soon as possible.\n\n"
                        + "⚠️ **Warning:** Failure to respond before this thread closes will result in a "
                        + "strike on your matchmaking record and removal from the QM Pool.";
                }
                break;
            case 4:
                if (partnerConfirmed) {
                    message = "🚨 **Final Warning — " + mention + ":** This thread closes in less than 1 hour "
                        + "and your match is still waiting for you. This is your last chance — please reach "
                        + "out and use **/confirm** before it closes.\n\n"
                        + "⛔ Non-response will result in a strike on your matchmaking record and immediate removal from the QM Pool.";
                } else {
                    message = "🚨 **Final Warning — " + mention + ":** This match thread closes in less than 1 hour. "
                        + "This is your last chance to connect with each other and use **/confirm**.\n\n"
                        + "⛔ Non-response will result in a strike on your matchmaking record and immediate removal from the QM Pool.";
                }
                break;
            default:
                message = "👋 " + mention + " — please use **/confirm** to respond to your match.";
        }

        thread.sendMessage(message).queue(
            s  -> System.out.println("ThreadManager: Sent QM level-" + level + " notification for thread " + record.threadId),
            e  -> System.err.println("ThreadManager: Could not send QM notification for thread " + record.threadId + ": " + e.getMessage())
        );
    }

    private static QMThread tryLoad(String id1, String id2) {
        for (String dirPath : new String[]{THREADS_DIR, MM_THREADS_DIR}) {
            File f = new File(dirPath + id1 + "_" + id2 + ".json");
            if (!f.exists()) continue;
            try (FileReader reader = new FileReader(f)) {
                return GSON.fromJson(reader, QMThread.class);
            } catch (Exception e) {
                System.err.println("ThreadManager: Failed to load " + f.getName() + ": " + e.getMessage());
            }
        }
        return null;
    }

    /** Immediately archives and deletes the thread with the given Discord channel ID. */
    public static void closeThread(String threadId, JDA jda) {
        QMThread record = findThreadByChannelId(threadId);
        if (record != null) archiveAndDelete(record, jda);
    }

    /** Admin-closes a thread without issuing any strikes or soft-deletes. */
    public static void adminCloseThread(String threadId, JDA jda) {
        QMThread record = findThreadByChannelId(threadId);
        if (record == null) return;
        if (shuttingDown) return;
        activeClosures.incrementAndGet();
        ThreadChannel thread = jda.getThreadChannelById(record.threadId);
        if (thread == null) {
            record.status      = "ARCHIVED";
            record.closedAt    = LocalDateTime.now().format(FMT);
            record.closeReason = "FORCE_CLOSED";
            save(record.maleId, record.femaleId, record);
            activeClosures.decrementAndGet();
            return;
        }
        thread.getHistoryFromBeginning(100).queue(history -> {
            List<Message> msgs = new ArrayList<>(history.getRetrievedHistory());
            msgs.sort(Comparator.comparing(Message::getTimeCreated));
            for (Message msg : msgs) {
                if (msg.getAuthor().isBot()) continue;
                ThreadMessage tm = new ThreadMessage();
                tm.authorId   = msg.getAuthor().getId();
                tm.authorName = msg.getAuthor().getName();
                tm.content    = msg.getContentRaw();
                tm.timestamp  = msg.getTimeCreated().toString();
                record.messages.add(tm);
            }
            record.status      = "ARCHIVED";
            record.closedAt    = LocalDateTime.now().format(FMT);
            record.closeReason = "FORCE_CLOSED";
            save(record.maleId, record.femaleId, record);
            UserInsightsManager.processThreadMessages(record);
            thread.delete().queue(
                v   -> { activeClosures.decrementAndGet(); System.out.println("ThreadManager: Admin-closed thread " + record.threadId + "."); },
                err -> { activeClosures.decrementAndGet(); System.err.println("ThreadManager: Could not delete thread " + record.threadId + ": " + err.getMessage()); }
            );
        }, err -> {
            record.status      = "ARCHIVED";
            record.closedAt    = LocalDateTime.now().format(FMT);
            record.closeReason = "FORCE_CLOSED";
            save(record.maleId, record.femaleId, record);
            thread.delete().queue(
                v   -> activeClosures.decrementAndGet(),
                err2 -> activeClosures.decrementAndGet()
            );
        });
    }

    private static void archiveAndDelete(QMThread record, JDA jda) {
        if (shuttingDown) return;
        activeClosures.incrementAndGet();

        ThreadChannel thread = jda.getThreadChannelById(record.threadId);

        if (thread == null) {
            // Already gone from Discord — just update the record
            record.status   = "ARCHIVED";
            record.closedAt = LocalDateTime.now().format(FMT);
            save(record.maleId, record.femaleId, record);
            issueQuickmatchStrikes(record, jda);
            softDeleteNonResponders(record, jda);
            sendPostMatchDMs(jda, record);
            activeClosures.decrementAndGet();
            System.out.println("ThreadManager: Thread " + record.threadId + " not found in cache; marked archived.");
            return;
        }

        // Fetch message history, save, then delete the thread
        thread.getHistoryFromBeginning(100).queue(history -> {
            List<Message> msgs = new ArrayList<>(history.getRetrievedHistory());
            msgs.sort(Comparator.comparing(Message::getTimeCreated)); // oldest first

            for (Message msg : msgs) {
                if (msg.getAuthor().isBot()) continue;
                ThreadMessage tm = new ThreadMessage();
                tm.authorId   = msg.getAuthor().getId();
                tm.authorName = msg.getAuthor().getName();
                tm.content    = msg.getContentRaw();
                tm.timestamp  = msg.getTimeCreated().toString();
                record.messages.add(tm);
            }

            record.status   = "ARCHIVED";
            record.closedAt = LocalDateTime.now().format(FMT);
            save(record.maleId, record.femaleId, record);
            UserInsightsManager.processThreadMessages(record);
            issueQuickmatchStrikes(record, jda);
            softDeleteNonResponders(record, jda);
            sendPostMatchDMs(jda, record);

            thread.delete().queue(
                v   -> {
                    activeClosures.decrementAndGet();
                    System.out.println("ThreadManager: Archived and deleted thread " + record.threadId + ".");
                },
                err -> {
                    activeClosures.decrementAndGet();
                    System.err.println("ThreadManager: Could not delete thread " + record.threadId + ": " + err.getMessage());
                }
            );
        }, err -> {
            // History fetch failed — still archive and delete
            System.err.println("ThreadManager: Could not fetch history for thread " + record.threadId + ": " + err.getMessage());
            record.status   = "ARCHIVED";
            record.closedAt = LocalDateTime.now().format(FMT);
            save(record.maleId, record.femaleId, record);
            issueQuickmatchStrikes(record, jda);
            softDeleteNonResponders(record, jda);
            sendPostMatchDMs(jda, record);
            thread.delete().queue(
                v   -> activeClosures.decrementAndGet(),
                err2 -> activeClosures.decrementAndGet()
            );
        });
    }

    private static void softDeleteNonResponders(QMThread record, JDA jda) {
        if (!"MANUAL".equals(record.matchType)) return;
        Set<String> responded = new HashSet<>();
        if (record.confirmedBy != null) responded.addAll(record.confirmedBy);
        if (record.declinedBy  != null) responded.addAll(record.declinedBy);
        for (String uid : new String[]{record.maleId, record.femaleId}) {
            if (uid == null || responded.contains(uid)) continue;
            // Don't penalize a user who actively participated — they were waiting for the other person
            if (record.messagedBy != null && record.messagedBy.contains(uid)) continue;
            try {
                AppState state = ProfileRepository.load(uid);
                if (state == null || state.softDeleted) continue;
                state.softDeleted = true;
                ProfileRepository.save(uid, state);
                UserStrikes us = writeStrikeRecord(uid, record.threadId);
                int recentStrikes = getRecentStrikeCount(uid);
                String expiryNote = buildExpiryNote(getNextStrikeExpiry(us));
                final String finalUid = uid;
                jda.openPrivateChannelById(uid).queue(
                    ch -> ch.sendMessage(
                        "⚠️ **Matchmaking Profile Suspended**\n\n"
                        + "Your profile has been removed from all matchmaking pools because you did not use `/confirm` or `/decline` before your manual match thread closed.\n\n"
                        + "📊 You currently have **" + recentStrikes + "/3 active strike(s)** in the past 6 months."
                        + expiryNote + "\n\n"
                        + "📩 **To appeal:** Open a support ticket in the server and a matchmaker will review your case. "
                        + "Your profile will remain suspended until it is restored."
                    ).queue(),
                    e -> System.err.println("ThreadManager: Could not DM soft-delete notice to " + finalUid + ": " + e.getMessage())
                );
                System.out.println("ThreadManager: Soft-deleted profile for " + uid + " (no response in manual match thread " + record.threadId + ").");
            } catch (Exception e) {
                System.err.println("ThreadManager: Failed to soft-delete profile for " + uid + ": " + e.getMessage());
            }
        }
    }

    private static void issueQuickmatchStrikes(QMThread record, JDA jda) {
        if (!"QUICKMATCH".equals(record.matchType)) return;
        Set<String> responded = new HashSet<>();
        if (record.confirmedBy != null) responded.addAll(record.confirmedBy);
        if (record.declinedBy  != null) responded.addAll(record.declinedBy);
        for (String uid : new String[]{record.maleId, record.femaleId}) {
            if (uid == null || responded.contains(uid)) continue;
            // Don't strike a user who actively participated — they were waiting for the other person
            if (record.messagedBy != null && record.messagedBy.contains(uid)) continue;
            addStrike(uid, record.threadId, jda);
        }
    }

    private static void sendPostMatchDMs(JDA jda, QMThread record) {
        if (!"QUICKMATCH".equals(record.matchType)) return;
        String guidelinesRef = "the guidelines";
        if (record.guildId != null) {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(record.guildId);
            if (guild != null) {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch =
                    Channels.findByNameContaining(guild, "guideline");
                if (ch != null) guidelinesRef = "<#" + ch.getId() + ">";
            }
        }
        sendPostMatchDM(jda, record.maleId, record.femaleId, guidelinesRef);
        sendPostMatchDM(jda, record.femaleId, record.maleId, guidelinesRef);
    }

    private static void sendPostMatchDM(JDA jda, String userId, String matchedId, String guidelinesRef) {
        final String ref = guidelinesRef;

        String matchedName = getProfileName(matchedId);
        String displayName = matchedName != null ? "**" + matchedName + "**" : "<@" + matchedId + ">";

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setTitle("💬 How did your match go?")
            .setColor(0xFF6699)
            .setDescription("Your Quick Match thread with " + displayName + " has closed!\n\n"
                + "Per the rules of engagement, you should remain in contact with your match via DMs. Love is commitment, not just a feeling.\n\n"
                    + "-# As always, remember to read the " + ref + ". Ghosting and abuse are strictly forbidden.\n\n"
                + "We'd love to hear how the experience went. "
                + "Share your thoughts or report any issues using the buttons below.")
            .setFooter("Agape Matchmaking • Your feedback helps us improve!");

        net.dv8tion.jda.api.interactions.components.buttons.Button feedbackBtn =
            net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                "qm_feedback_" + userId + "_" + matchedId, "💬 Give Feedback");
        net.dv8tion.jda.api.interactions.components.buttons.Button reportBtn =
            net.dv8tion.jda.api.interactions.components.buttons.Button.danger(
                "qm_report_" + userId + "_" + matchedId, "🚩 Report User Behavior");

        jda.openPrivateChannelById(userId).queue(
            ch -> ch.sendMessageEmbeds(embed.build())
                    .setComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(feedbackBtn, reportBtn))
                    .queue(
                        s  -> System.out.println("ThreadManager: Sent post-match DM to " + userId),
                        e  -> System.err.println("ThreadManager: Failed to send post-match DM to " + userId + ": " + e.getMessage())
                    ),
            e -> System.err.println("ThreadManager: Could not open DM for " + userId + ": " + e.getMessage())
        );
    }

    private static String getProfileName(String userId) {
        AppState state = ProfileRepository.load(userId);
        return state != null ? state.name : null;
    }

    // ─── /view-matches report ────────────────────────────────────────────────

    /**
     * Builds a formatted match report for both match types, split into Discord-safe
     * chunks (≤1990 chars each).
     */
    public static List<String> buildMatchesReport() {
        List<QMThread> manual = loadAllFromDir(MM_THREADS_DIR);
        List<QMThread> quick  = loadAllFromDir(THREADS_DIR);

        Comparator<QMThread> byDate = (a, b) -> {
            if (a.createdAt == null && b.createdAt == null) return 0;
            if (a.createdAt == null) return 1;
            if (b.createdAt == null) return -1;
            return a.createdAt.compareTo(b.createdAt);
        };
        manual.sort(byDate);
        quick.sort(byDate);

        StringBuilder sb = new StringBuilder();
        sb.append("**Manual Matches:**\n\n").append(buildMatchSection(manual));
        sb.append("\n**Quickmatches:**\n\n").append(buildMatchSection(quick));
        String text = sb.toString().trim();

        List<String> chunks = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (block.length() + line.length() + 1 > 1990) {
                chunks.add(block.toString().trim());
                block = new StringBuilder();
            }
            block.append(line).append("\n");
        }
        if (block.length() > 0) chunks.add(block.toString().trim());
        return chunks.isEmpty() ? java.util.Collections.singletonList("No matches on record.") : chunks;
    }

    private static List<QMThread> loadAllFromDir(String dirPath) {
        List<QMThread> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists()) return result;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File f : files) {
            try (FileReader reader = new FileReader(f)) {
                QMThread r = GSON.fromJson(reader, QMThread.class);
                if (r != null) result.add(r);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static String buildMatchSection(List<QMThread> records) {
        if (records.isEmpty()) return "(none)\n";
        StringBuilder sb = new StringBuilder();
        for (QMThread r : records) {
            sb.append("<@").append(r.maleId).append("> & <@").append(r.femaleId).append(">")
              .append(" | ").append(matchOutcome(r)).append("\n");
        }
        return sb.toString();
    }

    static String matchOutcome(QMThread r) {
        if ("OPEN".equals(r.status)) return "Active";

        if (r.endedReason != null && r.endedReason.startsWith("LEFT_SERVER:")) {
            String leftId = r.endedReason.substring("LEFT_SERVER:".length());
            boolean bothConfirmed = r.confirmedBy != null
                && r.confirmedBy.contains(r.maleId) && r.confirmedBy.contains(r.femaleId);
            return (bothConfirmed ? "Confirmed (Ended — <@" : "Ended (<@")
                + leftId + "> left server)";
        }

        boolean m1Confirmed = r.confirmedBy != null && r.confirmedBy.contains(r.maleId);
        boolean m2Confirmed = r.confirmedBy != null && r.confirmedBy.contains(r.femaleId);
        boolean m1Declined  = r.declinedBy  != null && r.declinedBy.contains(r.maleId);
        boolean m2Declined  = r.declinedBy  != null && r.declinedBy.contains(r.femaleId);

        if (m1Confirmed && m2Confirmed) {
            if (r.endedReason != null && r.endedReason.startsWith("GHOSTED:")) {
                String ghosterId = r.endedReason.substring("GHOSTED:".length());
                return "Confirmed (Ghosted by <@" + ghosterId + ">)";
            }
            if ("ENDED".equals(r.endedReason)) return "Confirmed (Ended)";
            return "Confirmed";
        }
        if (m1Declined  || m2Declined)  return "Declined";
        if ("FORCE_CLOSED".equals(r.closeReason)) return "Force-Closed";

        // Fallback for records created before closeReason was tracked: if the thread closed
        // more than 30 minutes before its natural expiry it was almost certainly force-closed.
        if (r.closeReason == null && r.createdAt != null && r.closedAt != null) {
            try {
                LocalDateTime created = LocalDateTime.parse(r.createdAt, FMT);
                LocalDateTime closed  = LocalDateTime.parse(r.closedAt,  FMT);
                if (closed.isBefore(created.plusHours(THREAD_LIFESPAN_HOURS).minusMinutes(30))) {
                    return "Force-Closed";
                }
            } catch (Exception ignored) {}
        }

        boolean m1Responded = m1Confirmed || m1Declined;
        boolean m2Responded = m2Confirmed || m2Declined;

        if (!m1Responded && !m2Responded) {
            boolean m1Tried = r.messagedBy != null && r.messagedBy.contains(r.maleId);
            boolean m2Tried = r.messagedBy != null && r.messagedBy.contains(r.femaleId);
            if (m1Tried && !m2Tried) return "Timed Out (<@" + r.femaleId + "> failed to respond)";
            if (m2Tried && !m1Tried) return "Timed Out (<@" + r.maleId + "> failed to respond)";
            return "Timed Out (Both failed to respond)";
        }
        if (!m1Responded) return "Timed Out (<@" + r.maleId + "> failed to respond)";
        if (!m2Responded) return "Timed Out (<@" + r.femaleId + "> failed to respond)";
        return "Closed";
    }

    /** Appends a command event entry to the thread's message log and saves the record. */
    public static void logEvent(String threadId, String userId, String userName, String content) {
        QMThread record = findThreadByChannelId(threadId);
        if (record == null) return;
        ThreadMessage tm = new ThreadMessage();
        tm.authorId   = userId;
        tm.authorName = userName;
        tm.content    = content;
        tm.timestamp  = LocalDateTime.now().format(FMT);
        if (record.messages == null) record.messages = new ArrayList<>();
        record.messages.add(tm);
        save(record.maleId, record.femaleId, record);
    }

    /**
     * Marks a confirmed match as ended, optionally attributing ghosting to a specific user.
     * @param userId1 either member of the pair (order-independent)
     * @param userId2 the other member of the pair
     * @param ghostedByUserId the user who ghosted, or null for a plain "Ended" marker
     * @return true if the record was found and updated, false if no matching thread exists
     */
    public static boolean markMatchEnded(String userId1, String userId2, String ghostedByUserId) {
        QMThread record = findThread(userId1, userId2);
        if (record == null) record = findMMThread(userId1, userId2);
        if (record == null) return false;
        record.endedReason = ghostedByUserId != null ? "GHOSTED:" + ghostedByUserId : "ENDED";
        save(record.maleId, record.femaleId, record);
        return true;
    }

    private static void save(String maleId, String femaleId, QMThread record) {
        String dirPath = "MANUAL".equals(record.matchType) ? MM_THREADS_DIR : THREADS_DIR;
        try {
            new File(dirPath).mkdirs();
            try (FileWriter writer = new FileWriter(new File(dirPath + maleId + "_" + femaleId + ".json"))) {
                GSON.toJson(record, writer);
            }
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to save record for " + maleId + "_" + femaleId + ": " + e.getMessage());
        }
    }
}
