package com.agape;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

/**
 * Handles messaging between Matchmakers and Applicants.
 * Allows matchmakers to communicate with users about their applications.
 */
public class MessagingHandler {

    // Track open messaging conversations
    private static final Map<String, Boolean> activeConversations = new HashMap<>();

    /**
     * Stores a message exchange between a matchmaker and an applicant.
     * @param applicantId The ID of the applicant
     * @param matchmakerId The ID of the matchmaker
     * @param sender Who sent it ("matchmaker" or "applicant")
     * @param message The message content
     */
    public static void saveMessage(String applicantId, String matchmakerId, String sender, String message) {
        File messagesDir = new File("user_content/messages/");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }

        // Store messages in a file named [applicantId]_[matchmakerId].json
        File messageFile = new File(messagesDir, applicantId + "_" + matchmakerId + ".json");
        
        JsonArray messages = new JsonArray();
        
        // Load existing messages if the file exists
        if (messageFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(messageFile.toPath()));
                messages = JsonParser.parseString(content).getAsJsonArray();
            } catch (IOException e) {
                System.err.println("Error reading existing messages: " + e.getMessage());
            }
        }

        // Create new message entry
        JsonObject msgEntry = new JsonObject();
        msgEntry.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        msgEntry.addProperty("sender", sender);
        msgEntry.addProperty("message", message);

        messages.add(msgEntry);

        // Save back to file
        try (FileWriter writer = new FileWriter(messageFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(messages, writer);
            System.out.println("✅ Saved message in conversation: " + applicantId + " <-> " + matchmakerId);
        } catch (IOException e) {
            System.err.println("❌ Failed to save message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a message from a matchmaker to an applicant (in their DMs)
     * @param event The slash command event
     * @param applicantUser The applicant to message
     * @param matchmakerId The matchmaker's ID
     * @param message The message to send
     */
    public static void sendMessageToApplicant(MessageReceivedEvent event, User applicantUser, String matchmakerId, String message) {
        applicantUser.openPrivateChannel().queue(channel -> {
            channel.sendMessage("📨 **Message from Matchmaker:**\n" + message).queue(success -> {
                // Save this message exchange
                saveMessage(applicantUser.getId(), matchmakerId, "matchmaker", message);
                event.getChannel().sendMessage("✅ Message sent to " + applicantUser.getAsMention()).queue();
            }, error -> {
                event.getChannel().sendMessage("❌ Could not send message - user may have DMs disabled.").queue();
            });
        }, error -> {
            event.getChannel().sendMessage("❌ Could not open DM channel with the user.").queue();
        });
    }

    /**
     * Gets all messages in a conversation thread
     * @param applicantId The applicant's ID
     * @param matchmakerId The matchmaker's ID
     * @return String representation of the conversation
     */
    public static String getConversationHistory(String applicantId, String matchmakerId) {
        File messagesDir = new File("user_content/messages/");
        File messageFile = new File(messagesDir, applicantId + "_" + matchmakerId + ".json");

        if (!messageFile.exists()) {
            return "No messages in this conversation yet.";
        }

        try {
            String content = new String(java.nio.file.Files.readAllBytes(messageFile.toPath()));
            JsonArray messages = JsonParser.parseString(content).getAsJsonArray();
            
            StringBuilder history = new StringBuilder();
            for (JsonElement msgElement : messages) {
                JsonObject msgObj = msgElement.getAsJsonObject();
                String timestamp = msgObj.get("timestamp").getAsString();
                String sender = msgObj.get("sender").getAsString();
                String message = msgObj.get("message").getAsString();
                
                history.append(String.format("[%s] **%s**: %s\n", timestamp, sender, message));
            }
            
            return history.toString();
        } catch (IOException e) {
            System.err.println("Error reading conversation: " + e.getMessage());
            return "Error loading conversation history.";
        }
    }

    /**
     * Opens a messaging conversation thread
     */
    public static void openConversation(String applicantId, String matchmakerId) {
        activeConversations.put(applicantId + "_" + matchmakerId, true);
        System.out.println("💬 Opened messaging conversation: " + applicantId + " <-> " + matchmakerId);
    }

    /**
     * Checks if there's an active conversation
     */
    public static boolean hasActiveConversation(String applicantId, String matchmakerId) {
        return activeConversations.getOrDefault(applicantId + "_" + matchmakerId, false);
    }

    /**
     * Closes a messaging conversation
     */
    public static void closeConversation(String applicantId, String matchmakerId) {
        activeConversations.remove(applicantId + "_" + matchmakerId);
        System.out.println("💬 Closed messaging conversation: " + applicantId + " <-> " + matchmakerId);
    }
}
