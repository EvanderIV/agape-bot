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
import java.io.FileWriter;
import java.util.EnumSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
            JDABuilder.createLight(token, EnumSet.of(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS))
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

    /**
     * Creates a private thread under the "quick-match" / "quickmatch" channel for a matched pair,
     * adds both users and all cached matchmaker-role members, then sends the intro message with profile card attachments.
     * user1 is always the runner; user2 is always the matched person (receives the DM).
     */
    private void createMatchThread(
            net.dv8tion.jda.api.entities.Guild guild,
            String user1Id, boolean user1IsMale, String user1Name, ApplicationHandler.AppState user1Profile,
            String user2Id, boolean user2IsMale, String user2Name, ApplicationHandler.AppState user2Profile) {

        // Find the channel (normalize "quick-match" → "quickmatch" for comparison)
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel qmChannel = null;
        for (net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch : guild.getTextChannels()) {
            if (ch.getName().toLowerCase().replace("-", "").equals("quickmatch")) {
                qmChannel = ch;
                break;
            }
        }
        if (qmChannel == null) {
            System.err.println("Quickmatch: No 'quick-match' or 'quickmatch' channel found in guild " + guild.getId());
            return;
        }

        // Order names/profiles: male first, female second (fall back to runner+matched if same sex)
        final String maleId, maleName, femaleId, femaleName;
        final ApplicationHandler.AppState maleProfile, femaleProfile;
        if (user1IsMale && !user2IsMale) {
            maleId = user1Id; maleName = user1Name; maleProfile = user1Profile;
            femaleId = user2Id; femaleName = user2Name; femaleProfile = user2Profile;
        } else if (!user1IsMale && user2IsMale) {
            maleId = user2Id; maleName = user2Name; maleProfile = user2Profile;
            femaleId = user1Id; femaleName = user1Name; femaleProfile = user1Profile;
        } else {
            maleId = user1Id; maleName = user1Name; maleProfile = user1Profile;
            femaleId = user2Id; femaleName = user2Name; femaleProfile = user2Profile;
        }

        String threadName = maleName + " + " + femaleName + " (Agape QM)";

        qmChannel.createThreadChannel(threadName, true)
            .setAutoArchiveDuration(net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
            .queue(thread -> {
                ThreadManager.registerThread(thread.getId(), guild.getId(), maleId, femaleId);

                thread.addThreadMemberById(maleId).queue();
                thread.addThreadMemberById(femaleId).queue();

                // Load full member list and add anyone with the matchmaker role
                guild.loadMembers().onSuccess(allMembers -> {
                    for (net.dv8tion.jda.api.entities.Member member : allMembers) {
                        for (Role role : member.getRoles()) {
                            if (role.getName().toLowerCase().contains("matchmaker")) {
                                thread.addThreadMemberById(member.getId()).queue();
                                break;
                            }
                        }
                    }
                });

                // Find a guidelines channel to reference in the intro message
                String guidelinesRef = "";
                for (net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch : guild.getTextChannels()) {
                    String normalized = ch.getName().toLowerCase().replace("-", "");
                    if (normalized.equals("howitworks") || normalized.equals("quickmatchrules")) {
                        guidelinesRef = "-# As always, please review the guidelines in <#" + ch.getId() + ">.\n\n";
                        break;
                    }
                }

                long closeTimestamp = java.time.Instant.now().getEpochSecond() + 86400L;
                final String message = "## Match Found!\n\n"
                    + "Take this opportunity to get to know each other—we want to see how you both will connect, "
                    + "and whether you are interested in potentially pursuing a relationship together.\n\n"
                    + guidelinesRef
                    + "-# This thread will automatically close <t:" + closeTimestamp + ":R>.\n"
                    + "||<@" + maleId + "> <@" + femaleId + ">||";

                // Generate profile cards on a background thread, then send the intro message with attachments
                final String fontPath = "assets/fonts/VAG Rounded Next Shine Regular.ttf";
                new Thread(() -> {
                    java.util.List<FileUpload> uploads = new java.util.ArrayList<>();
                    java.util.List<File> toDelete = new java.util.ArrayList<>();

                    File card1 = generateProfileCardFile(maleId, maleProfile, fontPath, guild);
                    File card2 = generateProfileCardFile(femaleId, femaleProfile, fontPath, guild);

                    if (card1 != null) { uploads.add(FileUpload.fromData(card1, maleName + "_profile.png")); toDelete.add(card1); }
                    if (card2 != null) { uploads.add(FileUpload.fromData(card2, femaleName + "_profile.png")); toDelete.add(card2); }

                    net.dv8tion.jda.api.requests.restaction.MessageCreateAction msgAction = thread.sendMessage(message);
                    if (!uploads.isEmpty()) msgAction = msgAction.addFiles(uploads);
                    msgAction.queue(
                        s -> toDelete.forEach(File::delete),
                        err -> { toDelete.forEach(File::delete); System.err.println("Quickmatch: Failed to send intro message: " + err.getMessage()); }
                    );
                }, "qm-card-gen").start();

                // DM the matched person (user2) with a direct link to the thread
                guild.getJDA().openPrivateChannelById(user2Id).queue(dmChannel -> {
                    net.dv8tion.jda.api.EmbedBuilder notifEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle("💘 Someone was matched with you!")
                        .setColor(0xFF6699)
                        .setDescription("**" + user1Name + "** (<@" + user1Id + ">) was matched with you through Agape Matchmaking!\n\n"
                            + "[Click here to open your match thread!](" + thread.getJumpUrl() + ")")
                        .setFooter("Agape Matchmaking • You have 24 hours before you can be matched again.");
                    dmChannel.sendMessageEmbeds(notifEmbed.build()).queue();
                }, err -> System.err.println("Quickmatch: Could not DM matched user " + user2Id));
            }, err -> System.err.println("Quickmatch: Failed to create match thread: " + err.getMessage()));
    }

    /** Generates a profile card image for the given user; returns the temp File or null on failure. */
    private File generateProfileCardFile(String userId, ApplicationHandler.AppState profile, String fontPath, net.dv8tion.jda.api.entities.Guild guild) {
        if (profile == null) return null;
        try {
            String pfpUri;
            if (profile.photoPath != null && !profile.photoPath.isEmpty()) {
                pfpUri = profile.photoPath.startsWith("http")
                    ? profile.photoPath
                    : new File(profile.photoPath).toURI().toURL().toString();
            } else {
                pfpUri = guild.getJDA().retrieveUserById(userId).complete().getEffectiveAvatarUrl();
            }
            String text = ApplicationHandler.buildCardText(profile);
            String[] designPaths = ImageGenerator.decodeDesignCode(profile.designCode);
            return ImageGenerator.generateForUser(designPaths[0], pfpUri, designPaths[1], fontPath, text, userId + "_qm_card");
        } catch (Exception e) {
            System.err.println("Quickmatch: Failed to generate profile card for " + userId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Bot is ready! Logged in as: " + event.getJDA().getSelfUser().getName());

        // Archive any threads that expired while the bot was offline
        ThreadManager.checkExpiredThreads(event.getJDA());

        // Schedule ongoing expiry checks every 5 minutes
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qm-thread-manager");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(
            () -> ThreadManager.checkExpiredThreads(event.getJDA()),
            5, 5, java.util.concurrent.TimeUnit.MINUTES
        );

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

        SlashCommandData quickmatchCmd = Commands.slash("quickmatch", "Find a random match from the quickmatch pool");

        SlashCommandData toggleQmCmd = Commands.slash("toggle-qm", "Enroll or unenroll yourself from the quickmatch pool");

        SlashCommandData compatAlgoCmd = Commands.slash("compat-algo", "Rank top compatibility matches across all registered users (Matchmakers only)");

        // 1. Force refresh the commands on every specific server the bot is in (Updates instantly!)
        event.getJDA().getGuilds().forEach(guild -> {
            guild.updateCommands()
                .addCommands(generateCmd, applyCmd, messageCmd, statusCmd, historyCmd, quickmatchCmd, toggleQmCmd, compatAlgoCmd)
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

        } else if (event.getName().equals("quickmatch")) {
            event.deferReply(true).queue();
            String userId = event.getUser().getId();

            MatchmakingEngine.MatchResult result = MatchmakingEngine.quickmatch(userId, event.getJDA());

            if (result == null) {
                event.getHook().sendMessage(
                    "💔 No match found right now. You may be on cooldown, not enrolled in quickmatch, or there are no eligible candidates at the moment."
                ).queue();
                return;
            }

            // Calculate matched person's age from birthday
            int matchedAge = 0;
            if (result.matchedProfile.birthday != null) {
                try {
                    String[] p = result.matchedProfile.birthday.split("/");
                    java.time.LocalDate bd = java.time.LocalDate.of(
                        Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                    matchedAge = (int) java.time.temporal.ChronoUnit.YEARS.between(bd, java.time.LocalDate.now());
                } catch (Exception ignored) {}
            }

            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("💘 You've been matched!")
                .setColor(0xFF6699)
                .setDescription("You've been matched with **" + result.matchedProfile.name
                    + "** (<@" + result.matchedUserId + ">)!")
                .addField("Age", matchedAge > 0 ? String.valueOf(matchedAge) : "N/A", true)
                .addField("Denomination", result.matchedProfile.sect != null ? result.matchedProfile.sect : "N/A", true)
                .addField("Country", result.matchedProfile.country != null ? result.matchedProfile.country : "N/A", true)
                .setFooter("Agape Matchmaking • You have 24 hours before you can be matched again.");

            event.getHook().sendMessageEmbeds(embed.build()).queue();

            // Create the match thread in the server
            if (event.getGuild() != null) {
                ApplicationHandler.AppState runnerProfile = null;
                try {
                    runnerProfile = new Gson().fromJson(
                        new FileReader("user_content/profiles/" + userId + ".json"),
                        ApplicationHandler.AppState.class
                    );
                } catch (Exception e) {
                    System.err.println("Quickmatch: Could not load runner profile for thread creation: " + e.getMessage());
                }

                String runnerName = (runnerProfile != null && runnerProfile.name != null)
                    ? runnerProfile.name : event.getUser().getEffectiveName();
                boolean runnerIsMale = runnerProfile == null || !runnerProfile.sex;
                boolean matchedIsMale = !result.matchedProfile.sex;
                String matchedName = result.matchedProfile.name != null
                    ? result.matchedProfile.name : "Unknown";

                createMatchThread(
                    event.getGuild(),
                    userId, runnerIsMale, runnerName, runnerProfile,
                    result.matchedUserId, matchedIsMale, matchedName, result.matchedProfile
                );
            }

        } else if (event.getName().equals("toggle-qm")) {
            event.deferReply(true).queue();
            String userId = event.getUser().getId();

            File profileFile = new File("user_content/profiles/" + userId + ".json");
            if (!profileFile.exists()) {
                event.getHook().sendMessage("❌ You don't have a profile on file. Use `/apply` to get started.").queue();
                return;
            }

            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                ApplicationHandler.AppState state = gson.fromJson(new FileReader(profileFile), ApplicationHandler.AppState.class);

                if (!"ACCEPTED".equals(state.status)) {
                    event.getHook().sendMessage("❌ Your profile must be accepted before you can manage quickmatch enrollment.").queue();
                    return;
                }

                state.quickmatchEnrolled = !state.quickmatchEnrolled;

                try (FileWriter writer = new FileWriter(profileFile)) {
                    gson.toJson(state, writer);
                }

                String statusMsg = state.quickmatchEnrolled
                    ? "✅ You are now **enrolled** in quickmatch. Run `/quickmatch` to find a match!"
                    : "✅ You are now **unenrolled** from quickmatch.";
                event.getHook().sendMessage(statusMsg).queue();

            } catch (Exception e) {
                System.err.println("Error toggling quickmatch for " + userId + ": " + e.getMessage());
                event.getHook().sendMessage("❌ Error updating your quickmatch status.").queue();
            }

        } else if (event.getName().equals("compat-algo")) {
            if (!hasMatchmakerRole(event)) {
                event.reply("❌ Only matchmakers can use this command.")
                        .setEphemeral(true).queue();
                return;
            }

            event.deferReply().queue();

            new Thread(() -> {
                CompatibilityEngine.ScoringResult result = CompatibilityEngine.findTopMatches(10);

                if (result.topPairs.isEmpty()) {
                    event.getHook().sendMessage(
                        "❌ Not enough accepted profiles to generate compatibility matches. "
                        + "(Found **" + result.profileCount + "** profile(s), "
                        + "**" + result.pairCount + "** opposite-sex pair(s).)"
                    ).queue();
                    return;
                }

                net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                    .setTitle("💘 Compatibility Analysis — Top Matches")
                    .setColor(0xFF6699)
                    .setDescription(
                        "Analyzed **" + result.profileCount + "** profiles · "
                        + "**" + result.pairCount + "** opposite-sex pair(s) evaluated.\n"
                        + "Scoring: Denomination (0–" + CompatibilityEngine.MAX_DENOM + ") "
                        + "+ Age (0–" + CompatibilityEngine.MAX_AGE + ") "
                        + "+ Distance (" + CompatibilityEngine.MIN_DIST + "–+" + CompatibilityEngine.MAX_DIST + ") "
                        + "= **" + CompatibilityEngine.MAX_TOTAL + " pts max**"
                    )
                    .setTimestamp(java.time.Instant.now());

                java.util.List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons = new java.util.ArrayList<>();

                for (int i = 0; i < result.topPairs.size(); i++) {
                    CompatibilityEngine.CompatPair pair = result.topPairs.get(i);
                    String name1 = pair.profile1.name != null ? pair.profile1.name : pair.userId1;
                    String name2 = pair.profile2.name != null ? pair.profile2.name : pair.userId2;
                    int rank = i + 1;

                    embed.addField(
                        "#" + rank + " · " + name1 + " & " + name2,
                        "**Score: " + pair.totalScore + " / " + CompatibilityEngine.MAX_TOTAL + "**"
                            + "  ·  Denom: " + pair.denom.score
                            + "  Age: " + pair.age.score
                            + "  Distance: " + pair.dist.score + "\n"
                            + "<@" + pair.userId1 + "> · <@" + pair.userId2 + ">",
                        false
                    );

                    buttons.add(net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(
                        "compat_breakdown_" + pair.userId1 + "_" + pair.userId2,
                        "#" + rank + " Breakdown"
                    ));
                }

                // Pack buttons into rows of up to 5
                java.util.List<net.dv8tion.jda.api.interactions.components.ActionRow> rows = new java.util.ArrayList<>();
                for (int i = 0; i < buttons.size(); i += 5) {
                    rows.add(net.dv8tion.jda.api.interactions.components.ActionRow.of(
                        buttons.subList(i, Math.min(i + 5, buttons.size()))
                    ));
                }

                if (!rows.isEmpty()) {
                    event.getHook().sendMessageEmbeds(embed.build()).setComponents(rows).queue();
                } else {
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                }

            }, "compat-algo").start();
        }
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("compat_breakdown_")) return;

        event.deferReply().queue();

        // Button ID format: compat_breakdown_<uid1>_<uid2>
        // Discord user IDs are numeric, so the first '_' after the prefix separates the two IDs.
        String rest = buttonId.substring("compat_breakdown_".length());
        int sep = rest.indexOf('_');
        if (sep < 0) {
            event.getHook().sendMessage("❌ Malformed breakdown button ID.").queue();
            return;
        }
        String uid1 = rest.substring(0, sep);
        String uid2 = rest.substring(sep + 1);

        ApplicationHandler.AppState p1 = null, p2 = null;
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            p1 = gson.fromJson(new FileReader("user_content/profiles/" + uid1 + ".json"), ApplicationHandler.AppState.class);
            p2 = gson.fromJson(new FileReader("user_content/profiles/" + uid2 + ".json"), ApplicationHandler.AppState.class);
        } catch (Exception e) {
            System.err.println("CompatBreakdown: could not load profiles: " + e.getMessage());
        }

        if (p1 == null || p2 == null) {
            event.getHook().sendMessage("❌ Could not load one or both profiles for this breakdown.").queue();
            return;
        }

        CompatibilityEngine.ScoreDetail denom = CompatibilityEngine.scoreDenomination(p1, p2);
        CompatibilityEngine.ScoreDetail age   = CompatibilityEngine.scoreAge(p1, p2);
        CompatibilityEngine.ScoreDetail dist  = CompatibilityEngine.scoreDistance(p1, p2);
        int total = denom.score + age.score + dist.score;

        String name1 = p1.name != null ? p1.name : uid1;
        String name2 = p2.name != null ? p2.name : uid2;

        net.dv8tion.jda.api.EmbedBuilder breakdown = new net.dv8tion.jda.api.EmbedBuilder()
            .setTitle("📊 Breakdown: " + name1 + " & " + name2)
            .setDescription("<@" + uid1 + "> × <@" + uid2 + ">")
            .setColor(0xFF9966)
            .addField("Total Score", "**" + total + " / " + CompatibilityEngine.MAX_TOTAL + "**", false)
            .addField("🙏 Denomination (" + denom.score + " / " + CompatibilityEngine.MAX_DENOM + ")", denom.detail, false)
            .addField("👶 Age (" + age.score + " / " + CompatibilityEngine.MAX_AGE + ")", age.detail, false)
            .addField("🌍 Distance (" + dist.score + " / " + CompatibilityEngine.MAX_DIST + ")", dist.detail, false)
            .setTimestamp(java.time.Instant.now());

        event.getHook().sendMessageEmbeds(breakdown.build()).queue();
    }
}