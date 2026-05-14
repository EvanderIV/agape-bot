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
import java.util.EnumSet;

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
            JDABuilder.createLight(token, EnumSet.of(GatewayIntent.DIRECT_MESSAGES))
                    .addEventListeners(new AgapeBot(), new ApplicationHandler())
                    .build();
        } catch (Exception e) {
            System.err.println("Failed to start the bot.");
            e.printStackTrace();
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Bot is ready! Logged in as: " + event.getJDA().getSelfUser().getName());

        // Define your command(s) here
        SlashCommandData generateCmd = Commands.slash("generate", "Generates a matchmaking profile image for a user.")
                .addOption(OptionType.USER, "target", "The user to generate the profile for", true);

        SlashCommandData applyCmd = Commands.slash("apply", "Apply for matchmaking (Sends a DM)");

        // 1. Force refresh the commands on every specific server the bot is in (Updates
        // instantly!)
        event.getJDA().getGuilds().forEach(guild -> {
            guild.updateCommands().addCommands(generateCmd, applyCmd).queue();
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
            String placeholderText = "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:10.0}{f:Arial Rounded MT Bold}"
                    + displayName
                    + "{/}*\n"
                    + "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}@"
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

            // 4. Run the generation in a new thread so it doesn't block JDA's main event
            // loop
            new Thread(() -> {
                File generatedImage = ImageGenerator.generateForUser(backgroundPath, avatarUrl, framePath, fontPath,
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
            boolean isSingle = false;
            // Ensure the command is being run in a server (where members and roles exist)
            if (event.getMember() != null) {
                for (Role role : event.getMember().getRoles()) {
                    if (role.getName().toLowerCase().contains("single")) {
                        isSingle = true;
                        break;
                    }
                }
            }

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
        }
    }
}