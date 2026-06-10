package com.agape;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats archived match-thread logs and user-insight records into
 * Discord-safe message chunks (each under the 2000-character limit).
 * Used by the /qm-thread, /mm-thread, and /user-insights commands.
 */
public final class ThreadLogFormatter {

    private ThreadLogFormatter() {}

    public static List<String> buildMMThreadOutput(ThreadManager.QMThread log) {
        return buildThreadOutput(log, "MM Thread Log");
    }

    public static List<String> buildQMThreadOutput(ThreadManager.QMThread log) {
        return buildThreadOutput(log, "QM Thread Log");
    }

    private static List<String> buildThreadOutput(ThreadManager.QMThread log, String label) {
        List<String> chunks = new ArrayList<>();

        StringBuilder header = new StringBuilder();
        header.append("## ").append(label).append("\n");
        header.append("**Pair:** <@").append(log.maleId).append("> & <@").append(log.femaleId).append(">\n");
        header.append("**Status:** ").append(log.status);
        if (log.createdAt != null) {
            header.append("  ·  **Opened:** `").append(log.createdAt.replace('T', ' ')).append("`");
        }
        if (log.closedAt != null) {
            header.append("  ·  **Closed:** `").append(log.closedAt.replace('T', ' ')).append("`");
        }
        header.append("\n");

        if (log.messages == null || log.messages.isEmpty()) {
            if ("OPEN".equals(log.status)) {
                header.append("\n*This thread is still active — messages will be logged when it closes.*");
            } else {
                header.append("\n*No messages were recorded in this thread.*");
            }
            chunks.add(header.toString());
            return chunks;
        }

        header.append("**Messages:** ").append(log.messages.size()).append("\n");
        header.append("━━━━━━━━━━━━━━━━━━━━\n");

        StringBuilder current = new StringBuilder(header);

        for (ThreadManager.ThreadMessage msg : log.messages) {
            String ts = formatMsgTimestamp(msg.timestamp);
            String name = msg.authorName != null ? msg.authorName : (msg.authorId != null ? msg.authorId : "Unknown");
            String content = msg.content != null ? msg.content : "*[empty]*";
            String line = ts + " **" + name + "** (<@" + msg.authorId + ">)\n"
                + "> " + content.replace("\n", "\n> ") + "\n\n";

            if (current.length() + line.length() > 1950) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line);
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    public static List<String> buildUserInsightsOutput(String userId, UserInsightsManager.UserInsightsRecord record) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        current.append("## Preference Insights — <@").append(userId).append(">\n");
        current.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        // Preference tags
        if (record.preferences != null && !record.preferences.isEmpty()) {
            current.append("**Tags:**\n");
            for (String tag : record.preferences) {
                current.append(tag.startsWith("+") ? "✅ " : "❌ ").append("`").append(tag).append("`\n");
            }
        } else {
            current.append("*No preference tags yet. Use `/tag-user` to add some.*\n");
        }

        // Decline history
        if (record.declineHistory != null && !record.declineHistory.isEmpty()) {
            current.append("\n**Decline History:**\n");
            for (UserInsightsManager.DeclineEntry entry : record.declineHistory) {
                String ts = entry.timestamp != null ? "`" + entry.timestamp.replace('T', ' ') + "`" : "";
                String partner = entry.matchPartnerId != null ? " (match: <@" + entry.matchPartnerId + ">)" : "";
                String reasons = entry.reasons != null ? entry.reasons : "*[empty]*";
                String block = ts + partner + "\n> " + reasons.replace("\n", "\n> ") + "\n\n";

                if (current.length() + block.length() > 1950) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(block);
            }
        }

        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    /** Converts an ISO timestamp into a Discord <t:...:f> tag, or a literal fallback. */
    private static String formatMsgTimestamp(String raw) {
        if (raw == null) return "[unknown time]";
        try {
            long epoch = java.time.OffsetDateTime.parse(raw).toEpochSecond();
            return "<t:" + epoch + ":f>";
        } catch (Exception e) {
            return "`" + raw.replace('T', ' ') + "`";
        }
    }
}
