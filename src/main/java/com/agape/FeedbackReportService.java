package com.agape;

import java.io.File;
import java.io.FileWriter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Persists post-match feedback and user reports (submitted via the DM
 * buttons after a match thread closes) and relays them to the guild's
 * matchmaker channel.
 *
 * Files land in user_content/feedback/ and user_content/reports/ as
 * {userId}_{epochMillis}.json.
 */
public final class FeedbackReportService {

    private FeedbackReportService() {}

    public static void saveFeedbackFile(String userId, String matchedId, String feedbackText, String timestamp, long epochMs) {
        try {
            File dir = new File("user_content/feedback/");
            if (!dir.exists()) dir.mkdirs();
            JsonObject obj = new JsonObject();
            obj.addProperty("userId", userId);
            obj.addProperty("matchedUserId", matchedId);
            obj.addProperty("feedback", feedbackText);
            obj.addProperty("timestamp", timestamp);
            try (FileWriter writer = new FileWriter("user_content/feedback/" + userId + "_" + epochMs + ".json")) {
                new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save feedback: " + e.getMessage());
        }
    }

    public static void saveReportFile(String userId, String matchedId, String reason, String details, String timestamp, long epochMs) {
        try {
            File dir = new File("user_content/reports/");
            if (!dir.exists()) dir.mkdirs();
            JsonObject obj = new JsonObject();
            obj.addProperty("userId", userId);
            obj.addProperty("matchedUserId", matchedId);
            obj.addProperty("reportReason", reason);
            obj.addProperty("details", details);
            obj.addProperty("timestamp", timestamp);
            try (FileWriter writer = new FileWriter("user_content/reports/" + userId + "_" + epochMs + ".json")) {
                new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    public static void postFeedbackToMatchmakers(JDA jda, String userId, String matchedId, String feedbackText, String timestamp) {
        TextChannel ch = findMatchmakerChannelForPair(jda, userId, matchedId);
        if (ch == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("💬 QM Feedback Received")
            .setColor(0x5865F2)
            .addField("From", "<@" + userId + ">", true)
            .addField("Matched with", "<@" + matchedId + ">", true)
            .addField("Feedback", feedbackText, false)
            .setFooter("User ID: " + userId)
            .setTimestamp(java.time.Instant.now());

        ch.sendMessageEmbeds(embed.build()).queue();
    }

    public static void postReportToMatchmakers(JDA jda, String userId, String matchedId, String reason, String details, String timestamp) {
        TextChannel ch = findMatchmakerChannelForPair(jda, userId, matchedId);
        if (ch == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🚩 QM Report Filed")
            .setColor(0xFF3300)
            .addField("Reported by", "<@" + userId + ">", true)
            .addField("Reported user", "<@" + matchedId + ">", true)
            .addField("Reason", reason, false);
        if (details != null && !details.isBlank()) {
            embed.addField("Details", details, false);
        }
        embed.setFooter("Reporter ID: " + userId)
             .setTimestamp(java.time.Instant.now());

        ch.sendMessageEmbeds(embed.build()).queue();
    }

    /** Locates the matchmaker channel of the guild where this pair's thread was created. */
    private static TextChannel findMatchmakerChannelForPair(JDA jda, String userId, String matchedId) {
        ThreadManager.QMThread record = ThreadManager.findThread(userId, matchedId);
        if (record == null || record.guildId == null) return null;
        Guild guild = jda.getGuildById(record.guildId);
        if (guild == null) return null;
        return Channels.findMatchmakerChannel(guild);
    }
}
