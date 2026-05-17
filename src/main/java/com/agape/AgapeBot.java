package com.agape;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.io.FileReader;
import java.util.EnumSet;

import com.google.gson.Gson;

public class AgapeBot extends ListenerAdapter {

    public static void main(String[] args) {
        // Retrieve the token from environment variables
        String token = System.getenv("AGAPE_DISCORD_TOKEN");
        if (token == null || token.isEmpty()) {
            System.err.println("CRITICAL ERROR: AGAPE_DISCORD_TOKEN environment variable is missing!");
            return;
        }

        try {
            // Build the JDA instance. Add the DIRECT_MESSAGES intent so it can hear users in DMs!
            JDABuilder.createLight(token, EnumSet.of(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                    .addEventListeners(new AgapeBot(), new ApplicationHandler())
                    .build();
        } catch (Exception e) {
            System.err.println("Failed to start the bot.");
            e.printStackTrace();
        }
    }

    /**
     * Helper method to check if a member has the matchmaker role
     */
    private boolean hasMatchmakerRole(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;
        
        for (Role role : event.getMember().getRoles()) {
            if (role.getName().toLowerCase().contains("matchmaker")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if a member is marked as single
     */
    private boolean isSingleStatus(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;
        
        for (Role role : event.getMember().getRoles()) {
            if (role.getName().toLowerCase().contains("single")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Bot is ready! Logged in as: " + event.getJDA().getSelfUser().getName());

        // Define your command(s) here
        SlashCommandData generateCmd = Commands.slash("generate", "Generates a matchmaking profile image for a user.")
                .addOption(OptionType.USER, "target", "The user to generate the profile for", true);

        SlashCommandData applyCmd = Commands.slash("apply", "Apply for matchmaking (Sends a DM)");

        // NEW: Matchmaker commands
        SlashCommandData messageCmd = Commands.slash("admin-message", "Send a message to an applicant (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The applicant to message", true)
                .addOption(OptionType.STRING, "message", "The message to send", true);

        SlashCommandData statusCmd = Commands.slash("app-status", "Check the status of an applicant's profile")
                .addOption(OptionType.USER, "user", "The user to check status for", true);

        SlashCommandData historyCmd = Commands.slash("message-history", "View message history with an applicant (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The applicant to view history with", true);

        // 1. Force refresh the commands on every specific server the bot is in (Updates
        // instantly!)
        event.getJDA().getGuilds().forEach(guild -> {
            guild.updateCommands()
                .addCommands(generateCmd, applyCmd, messageCmd, statusCmd, historyCmd)
                .queue();
            System.out.println("Refreshed commands for server: " + guild.getName());
        });

        // 2. Clear out the global commands cache to prevent duplicate entries
        event.getJDA().updateCommands().queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Check if the command used is "/generate"
        if (event.getName().equals("generate")) {

            // --- PERMISSION CHECK ---
            boolean hasPermission = false;
            // Ensure the command is being run in a server (where members and roles exist)
            if (event.getMember() != null) {
                for (Role role : event.getMember().getRoles()) {
                    if (role.getName().toLowerCase().contains("matchmaker")) {
                        hasPermission = true;
                        break;
                    }
                }
            }

            // If they don't have the role, reject them immediately and hide the message
            // from others
            if (!hasPermission) {
                event.reply("❌ You do not have permission to use this command.")
                        .setEphemeral(true)
                        .queue();
                return; // Stop execution here
            }
            // ------------------------

            // 1. Defer the reply. Image generation might take longer than Discord's
            // 3-second timeout limit!
            event.deferReply().queue();

            // 2. Extract the target user from the slash command option
            User targetUser = event.getOption("target").getAsUser();
            String avatarUrl = targetUser.getEffectiveAvatarUrl();
            String userId = targetUser.getId();
            String displayName = targetUser.getEffectiveName();

            // 3. Build the placeholder text based on your reference design requirements
            // Note: In a real app, you'd fetch this data from a database based on the
            // userId
            String placeholderText = "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:2}{f:Arial Rounded MT Bold}"
                    + displayName
                    + "{/}*\n"
                    + "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:2}{f:Arial Rounded MT Bold}@"
                    + targetUser.getName() + "{/}*\n\n"
                    + "20 | 2005\n"
                    + "M\n"
                    + "DISCORD USER\n"
                    + "EARTH / ENGLISH\n"
                    + "PROGRAMMER\n\n"
                    + "LOVE TO CHAT, PLAY GAMES, AND BUILD BOTS. 🧠\n\n"
                    + "{img:green_flag.png} PARTNER: KIND, COMMUNICATIVE, FUN\n"
                    + "{img:red_flag.png} PARTNER: TOXIC, UNAVAILABLE.";

            // Assuming your template is sitting in the root folder of your project
            String backgroundPath = "assets/backgrounds/default.png";
            String framePath = "assets/frames/default.png";
            String fontPath = "assets/fonts/VAG Rounded Next Shine Regular.ttf";

            // Dynamically pull the user's custom Design Code if they completed the application!
            File profileFile = new File("user_content/profiles/" + userId + ".json");
            if (profileFile.exists()) {
                try (FileReader reader = new FileReader(profileFile)) {
                    Gson gson = new Gson();
                    ApplicationHandler.AppState state = gson.fromJson(reader, ApplicationHandler.AppState.class);
                    if (state.designCode != null && !state.designCode.isEmpty()) {
                        // Decode the shortcode (e.g. "DEF-DEF") into actual file paths!
                        String[] decodedPaths = ImageGenerator.decodeDesignCode(state.designCode);
                        backgroundPath = decodedPaths[0];
                        framePath = decodedPaths[1];
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load user profile for design code.");
                }
            }

            // Print files in the specified path for debugging purposes
            File bgFile = new File(backgroundPath);
            if (!bgFile.exists()) {
                System.err.println("ERROR: Background image not found at path: " + backgroundPath);
                System.err.println("Current working directory: " + System.getProperty("user.dir"));
                System.err.println("Files in current directory:");
                File[] files = new File(".").listFiles();
                System.err.println("File count: " + (files != null ? files.length : 0));
                event.getHook().sendMessage("❌ Sorry, I couldn't find the background image for generation!").queue();
                return;
            }

            // Capture current values for use in lambda (lambda requires effectively final variables)
            final String bgPath = backgroundPath;
            final String fmPath = framePath;
            final String ftPath = fontPath;

            // 4. Run the generation in a new thread so it doesn't block JDA's main event
            // loop
            new Thread(() -> {
                File generatedImage = ImageGenerator.generateForUser(bgPath, avatarUrl, fmPath, ftPath,
                        placeholderText, userId);

                // 5. Send the result back to Discord
                if (generatedImage != null && generatedImage.exists()) {
                    event.getHook().sendFiles(FileUpload.fromData(generatedImage)).queue(
                            success -> {
                                // Successfully sent! Clean up the temporary file so we don't leak storage
                                // space.
                                generatedImage.delete();
                            },
                            error -> {
                                System.err.println("Failed to send image to Discord: " + error.getMessage());
                                // Clean up file even if sending failed
                                generatedImage.delete();
                            });
                } else {
                    // Image generation failed
                    event.getHook().sendMessage("❌ Sorry, I encountered an error while generating the image!").queue();
                }
            }).start();

        } else if (event.getName().equals("apply")) {

            // --- PERMISSION CHECK FOR /APPLY ---
            boolean isSingle = isSingleStatus(event);

            // If they don't have the role, reject them immediately
            if (!isSingle) {
                event.reply("❌ You need to be single to apply.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            // ------------------------

            // Defer the reply ephemerally (only the user will see the bot's loading/response)
            event.deferReply(true).queue();

            // Fire off the state-machine logic in the new handler class, passing the event so it can reply
            ApplicationHandler.startApplication(event.getUser(), event);

        } else if (event.getName().equals("admin-message")) {
            // MATCHMAKER COMMAND: Send a message to an applicant
            if (!hasMatchmakerRole(event)) {
                event.reply("❌ Only matchmakers can use this command.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            User applicant = event.getOption("user").getAsUser();
            String messageContent = event.getOption("message").getAsString();
            String matchmakerId = event.getUser().getId();
            String applicantId = applicant.getId();

            event.deferReply(true).queue();

            applicant.openPrivateChannel().queue(channel -> {
                // Create embed for the message
                net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                    .setTitle("💬 Message from Matchmaker")
                    .setColor(0xFF9999)
                    .setDescription(messageContent)
                    .setFooter("Matchmaker ID: " + matchmakerId)
                    .setTimestamp(java.time.Instant.now());

                // Add reply button to the DM
                net.dv8tion.jda.api.interactions.components.buttons.Button replyBtn = 
                    net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "convo_reply_" + applicantId + "_" + matchmakerId, "💬 Reply"
                    );
                net.dv8tion.jda.api.interactions.components.ActionRow actionRow = 
                    net.dv8tion.jda.api.interactions.components.ActionRow.of(replyBtn);
                
                channel.sendMessageEmbeds(embed.build())
                    .setComponents(actionRow)
                    .queue(success -> {
                        // Store the new message ID
                        String newMessageId = success.getId();
                        MessagingHandler.saveDMMessageId(applicantId, matchmakerId, newMessageId);
                        MessagingHandler.saveMessage(applicantId, matchmakerId, "matchmaker", messageContent);
                        
                        // Remove reply button from the previous message if it exists
                        String oldMessageId = MessagingHandler.getDMMessageId(applicantId, matchmakerId);
                        if (oldMessageId != null && !oldMessageId.equals(newMessageId)) {
                            channel.retrieveMessageById(oldMessageId).queue(oldMsg -> {
                                try {
                                    oldMsg.editMessageComponents(java.util.Collections.emptyList()).queue(
                                        editSuccess -> System.out.println("✅ Removed reply button from old message"),
                                        editError -> System.err.println("⚠️ Could not remove reply button from old message")
                                    );
                                } catch (Exception ex) {
                                    System.err.println("⚠️ Could not edit old message: " + ex.getMessage());
                                }
                            }, msgError -> System.err.println("⚠️ Could not retrieve old message"));
                        }
                        
                        event.getHook().sendMessage("✅ Message sent to " + applicant.getAsMention()).queue();
                    }, error -> {
                        event.getHook().sendMessage("❌ Could not send message - user may have DMs disabled.").queue();
                    });
            }, error -> {
                event.getHook().sendMessage("❌ Could not open DM channel with the user.").queue();
            });

            // Also post to the applications channel for the applicant to reply through
            if (event.getGuild() != null) {
                ApplicationHandler.postConversationStartToChannel(
                    applicant, messageContent, matchmakerId, event.getGuild().getId(), event.getJDA()
                );
            }

        } else if (event.getName().equals("app-status")) {
            // Check application status (anyone can use this)
            User targetUser = event.getOption("user").getAsUser();
            String userId = targetUser.getId();

            event.deferReply().queue();

            File profileFile = new File("user_content/profiles/" + userId + ".json");
            if (!profileFile.exists()) {
                event.getHook().sendMessage("❌ No application found for this user.").queue();
                return;
            }

            try {
                Gson gson = new Gson();
                ApplicationHandler.AppState state = gson.fromJson(
                    new FileReader(profileFile),
                    ApplicationHandler.AppState.class
                );

                String statusEmoji = "⏳";
                if ("ACCEPTED".equals(state.status)) {
                    statusEmoji = "✅";
                } else if ("REJECTED".equals(state.status)) {
                    statusEmoji = "❌";
                } else if ("CHANGES_REQUESTED".equals(state.status)) {
                    statusEmoji = "📝";
                }

                StringBuilder response = new StringBuilder();
                response.append(statusEmoji).append(" **Application Status for ").append(targetUser.getAsMention()).append("**\n\n");
                response.append("**Status:** ").append(state.status).append("\n");
                response.append("**Name:** ").append(state.name).append("\n");
                response.append("**Submitted:** ").append(state.submittedAt).append("\n");

                if (state.reviewedAt != null) {
                    response.append("**Reviewed:** ").append(state.reviewedAt).append("\n");
                    response.append("**Reviewed By:** <@").append(state.reviewedBy).append(">\n");
                }

                if ("REJECTED".equals(state.status) && state.rejectionReason != null) {
                    response.append("\n**Rejection Reason:** ").append(state.rejectionReason).append("\n");
                }

                event.getHook().sendMessage(response.toString()).queue();

            } catch (Exception e) {
                System.err.println("Error checking status: " + e.getMessage());
                event.getHook().sendMessage("❌ Error retrieving status.").queue();
            }

        } else if (event.getName().equals("message-history")) {
            // MATCHMAKER COMMAND: View message history with an applicant
            if (!hasMatchmakerRole(event)) {
                event.reply("❌ Only matchmakers can use this command.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            User applicant = event.getOption("user").getAsUser();
            String matchmakerId = event.getUser().getId();

            event.deferReply(true).queue();

            String history = MessagingHandler.getConversationHistory(applicant.getId(), matchmakerId);
            
            // Discord message length limit is 2000, so we might need to split
            if (history.length() > 1950) {
                event.getHook().sendMessage("📨 **Message History with " + applicant.getAsMention() + ":**\n```\n" 
                    + history.substring(0, 1950) + "...\n```").queue();
            } else {
                event.getHook().sendMessage("📨 **Message History with " + applicant.getAsMention() + ":**\n```\n" 
                    + history + "\n```").queue();
            }
        }
    }
}