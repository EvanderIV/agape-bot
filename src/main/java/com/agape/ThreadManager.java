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

    static class UserStrikes {
        String userId;
        List<Strike> strikes = new ArrayList<>();
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
        String firstMessageAt;      // ISO-8601 local datetime of first non-bot message
        List<String> confirmedBy    = new ArrayList<>();
        List<String> declinedBy     = new ArrayList<>();
        List<String> notificationsSent = new ArrayList<>();
        List<ThreadMessage> messages = new ArrayList<>();
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
            return;
        }

        // Unenroll from quickmatch
        File profileFile = new File("user_content/profiles/" + userId + ".json");
        if (profileFile.exists()) {
            try {
                com.google.gson.Gson g = new GsonBuilder().setPrettyPrinting().create();
                ApplicationHandler.AppState state = g.fromJson(new FileReader(profileFile), ApplicationHandler.AppState.class);
                if (state != null && state.quickmatchEnrolled) {
                    state.quickmatchEnrolled = false;
                    try (FileWriter w = new FileWriter(profileFile)) {
                        g.toJson(state, w);
                    }
                }
            } catch (Exception e) {
                System.err.println("ThreadManager: Failed to unenroll " + userId + " from QM: " + e.getMessage());
            }
        }

        int recentStrikes = getRecentStrikeCount(userId);
        String banNote = recentStrikes >= 3
            ? "\n\n⛔ You have **" + recentStrikes + " strikes** in the past 6 months and cannot re-enroll in quickmatch until your oldest strike ages out."
            : "\n\nYou currently have **" + recentStrikes + "/3** strike(s) in the past 6 months. You may re-enroll using `/toggle-qm` when ready.";

        jda.openPrivateChannelById(userId).queue(
            ch -> ch.sendMessage(
                "⚠️ **Quickmatch Strike**\n\n"
                + "You received a strike on your matchmaking record because you did not use `/confirm` or `/decline` before your quickmatch thread closed.\n"
                + "You have been unenrolled from the quickmatch pool."
                + banNote
            ).queue(),
            e -> System.err.println("ThreadManager: Could not DM strike notice to " + userId + ": " + e.getMessage())
        );
        System.out.println("ThreadManager: Strike issued to " + userId + " (" + recentStrikes + " in last 6 months).");
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

                // Lazily populate firstMessageAt by fetching the thread history
                if (record.firstMessageAt == null) {
                    net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel thread =
                        jda.getThreadChannelById(record.threadId);
                    if (thread != null) {
                        final QMThread rec = record;
                        thread.getHistoryFromBeginning(10).queue(history -> {
                            List<Message> msgs = new ArrayList<>(history.getRetrievedHistory());
                            msgs.sort(Comparator.comparing(Message::getTimeCreated));
                            for (Message msg : msgs) {
                                if (!msg.getAuthor().isBot()) {
                                    rec.firstMessageAt = msg.getTimeCreated()
                                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime().format(FMT);
                                    save(rec.maleId, rec.femaleId, rec);
                                    break;
                                }
                            }
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

    private static void sendMatchNotification(JDA jda, QMThread record, int level) {
        net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel thread =
            jda.getThreadChannelById(record.threadId);
        if (thread == null) {
            System.err.println("ThreadManager: Cannot send level-" + level
                + " notification — thread " + record.threadId + " not in cache.");
            return;
        }

        String mention = buildPendingMention(record);
        String message;
        switch (level) {
            case 1:
                message = "👋 " + mention + " — now that you've had a chance to get to know each other, "
                    + "please let us know what you think! Use **/confirm** if you'd like to pursue this match, "
                    + "or **/decline** if you'd prefer to pass.";
                break;
            case 2:
                message = "👋 " + mention + " — just a reminder to respond to your match! "
                    + "Use **/confirm** if you'd like to continue, or **/decline** if not.\n\n"
                    + "⚠️ **Note:** If you fail to respond, you may be removed from all matchmaking pools.";
                break;
            case 3:
                message = "⏰ **Urgent — " + mention + ":** You have not yet responded to this match. "
                    + "Please use **/confirm** or **/decline** as soon as possible.\n\n"
                    + "⚠️ **Warning:** Failure to respond before this thread closes will result "
                    + "in removal from all matchmaking pools.";
                break;
            case 4:
                message = "🚨 **Final Warning — " + mention + ":** This match thread closes in less than 1 hour "
                    + "and you have not responded. This is your last chance to use **/confirm** or **/decline**.\n\n"
                    + "⛔ Non-response will result in immediate removal from all matchmaking pools.";
                break;
            default:
                message = "👋 " + mention + " — please use **/confirm** or **/decline** to respond to your match.";
        }

        thread.sendMessage(message).queue(
            s  -> System.out.println("ThreadManager: Sent level-" + level + " notification for thread " + record.threadId),
            e  -> System.err.println("ThreadManager: Could not send notification for thread " + record.threadId + ": " + e.getMessage())
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
            File profileFile = new File("user_content/profiles/" + uid + ".json");
            if (!profileFile.exists()) continue;
            try {
                com.google.gson.Gson g = new GsonBuilder().setPrettyPrinting().create();
                ApplicationHandler.AppState state = g.fromJson(new FileReader(profileFile), ApplicationHandler.AppState.class);
                if (state == null || state.softDeleted) continue;
                state.softDeleted = true;
                try (FileWriter w = new FileWriter(profileFile)) {
                    g.toJson(state, w);
                }
                final String finalUid = uid;
                jda.openPrivateChannelById(uid).queue(
                    ch -> ch.sendMessage(
                        "⚠️ **Matchmaking Profile Suspended**\n\n"
                        + "Your profile has been removed from all matchmaking because you did not use `/confirm` or `/decline` before your manual match thread closed.\n\n"
                        + "Please reach out to a matchmaker if you believe this was in error."
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
            if (uid != null && !responded.contains(uid)) {
                addStrike(uid, record.threadId, jda);
            }
        }
    }

    private static void sendPostMatchDMs(JDA jda, QMThread record) {
        if (!"QUICKMATCH".equals(record.matchType)) return;
        String guidelinesRef = "the guidelines";
        if (record.guildId != null) {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(record.guildId);
            if (guild != null) {
                for (net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch : guild.getTextChannels()) {
                    if (ch.getName().toLowerCase().replace("-", "").contains("guideline")) {
                        guidelinesRef = "<#" + ch.getId() + ">";
                        break;
                    }
                }
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
        File f = new File("user_content/profiles/" + userId + ".json");
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f)) {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("name") && !obj.get("name").isJsonNull()) return obj.get("name").getAsString();
        } catch (Exception ignored) {}
        return null;
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
