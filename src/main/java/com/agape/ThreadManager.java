package com.agape;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

public class ThreadManager {

    private static final String THREADS_DIR = "user_content/qm_threads/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    static final long THREAD_LIFESPAN_HOURS = 24;

    // ─── Data structures ──────────────────────────────────────────────────────

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
        List<ThreadMessage> messages = new ArrayList<>();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Called immediately after a match thread is created in Discord. */
    public static void registerThread(String threadId, String guildId, String maleId, String femaleId) {
        QMThread record = new QMThread();
        record.threadId = threadId;
        record.guildId  = guildId;
        record.maleId   = maleId;
        record.femaleId = femaleId;
        record.createdAt = LocalDateTime.now().format(FMT);
        record.status = "OPEN";
        save(maleId, femaleId, record);
        System.out.println("ThreadManager: Registered thread " + threadId + " (" + maleId + " + " + femaleId + ").");
    }

    /**
     * Scans all OPEN thread records and archives any that are past THREAD_LIFESPAN_HOURS.
     * Safe to call on boot and on a recurring schedule.
     */
    public static void checkExpiredThreads(JDA jda) {
        File dir = new File(THREADS_DIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        LocalDateTime cutoff = LocalDateTime.now().minusHours(THREAD_LIFESPAN_HOURS);
        int expired = 0;

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

        if (expired > 0) {
            System.out.println("ThreadManager: Processing " + expired + " expired thread(s).");
        }
    }

    // ─── Public API (continued) ───────────────────────────────────────────────

    /**
     * Finds the QMThread log for the given pair (either ordering).
     * Returns {@code null} if no log file exists for the pair.
     */
    public static QMThread findThread(String userId1, String userId2) {
        QMThread r = tryLoad(userId1, userId2);
        return r != null ? r : tryLoad(userId2, userId1);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static QMThread tryLoad(String id1, String id2) {
        File f = new File(THREADS_DIR + id1 + "_" + id2 + ".json");
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f)) {
            return GSON.fromJson(reader, QMThread.class);
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to load " + f.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static void archiveAndDelete(QMThread record, JDA jda) {
        ThreadChannel thread = jda.getThreadChannelById(record.threadId);

        if (thread == null) {
            // Already gone from Discord — just update the record
            record.status   = "ARCHIVED";
            record.closedAt = LocalDateTime.now().format(FMT);
            save(record.maleId, record.femaleId, record);
            sendPostMatchDMs(jda, record);
            System.out.println("ThreadManager: Thread " + record.threadId + " not found in cache; marked archived.");
            return;
        }

        // Fetch message history, then save + delete
        thread.getHistoryFromBeginning(100).queue(history -> {
            List<Message> msgs = new ArrayList<>(history.getRetrievedHistory());
            msgs.sort(Comparator.comparing(Message::getTimeCreated)); // oldest first

            for (Message msg : msgs) {
                if (msg.getAuthor().isBot()) continue; // exclude the bot's intro message
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
            sendPostMatchDMs(jda, record);

            thread.delete().queue(
                v   -> System.out.println("ThreadManager: Archived and deleted thread " + record.threadId + "."),
                err -> System.err.println("ThreadManager: Could not delete thread " + record.threadId + ": " + err.getMessage())
            );
        }, err -> {
            // History fetch failed — still close it
            System.err.println("ThreadManager: Could not fetch history for thread " + record.threadId + ": " + err.getMessage());
            record.status   = "ARCHIVED";
            record.closedAt = LocalDateTime.now().format(FMT);
            save(record.maleId, record.femaleId, record);
            sendPostMatchDMs(jda, record);
            thread.delete().queue();
        });
    }

    private static void sendPostMatchDMs(JDA jda, QMThread record) {
        sendPostMatchDM(jda, record.maleId, record.femaleId);
        sendPostMatchDM(jda, record.femaleId, record.maleId);
    }

    private static void sendPostMatchDM(JDA jda, String userId, String matchedId) {
        String matchedName = getProfileName(matchedId);
        String displayName = matchedName != null ? "**" + matchedName + "**" : "<@" + matchedId + ">";

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
            .setTitle("💬 How did your match go?")
            .setColor(0xFF6699)
            .setDescription("Your Quick Match thread with " + displayName + " has ended!\n\n"
                + "We'd love to hear how the experience went. "
                + "Share your thoughts or report any issues using the buttons below.")
            .setFooter("Agape Matchmaking • Your feedback helps us improve!");

        net.dv8tion.jda.api.interactions.components.buttons.Button feedbackBtn =
            net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                "qm_feedback_" + userId + "_" + matchedId, "💬 Give Feedback");
        net.dv8tion.jda.api.interactions.components.buttons.Button reportBtn =
            net.dv8tion.jda.api.interactions.components.buttons.Button.danger(
                "qm_report_" + userId + "_" + matchedId, "🚩 Report");

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
        try {
            File dir = new File(THREADS_DIR);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(THREADS_DIR + maleId + "_" + femaleId + ".json"))) {
                GSON.toJson(record, writer);
            }
        } catch (Exception e) {
            System.err.println("ThreadManager: Failed to save record for " + maleId + "_" + femaleId + ": " + e.getMessage());
        }
    }
}
