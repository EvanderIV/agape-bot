package com.agape;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * Entry point and slash-command router for the Agape matchmaking bot.
 *
 * This class deliberately contains NO business logic — every slash command,
 * button, and modal is validated here (guild gating + role checks) and then
 * handed to a focused service or manager class:
 *
 *   Application flow (DM questionnaire)  → {@link ApplicationHandler}
 *   Quickmatch pairing                   → {@link MatchmakingEngine}
 *   Compatibility scoring (/compat-algo) → {@link CompatibilityEngine}
 *   Match thread creation/announcement   → {@link MatchThreadService}
 *   Match preview (/match)               → {@link MatchPreviewService}
 *   Thread lifecycle, strikes, pardons   → {@link ThreadManager}
 *   Feedback + reports                   → {@link FeedbackReportService}
 *   Preference insights (/tag-user)      → {@link UserInsightsManager}
 *
 * To add a new slash command: register it in {@link #onReady}, add a case to
 * the switch in {@link #onSlashCommandInteraction}, and write a private
 * handle...() method that delegates to a service class.
 */
public class AgapeBot extends ListenerAdapter {

    private static final String FONT_PATH = "assets/fonts/VAG Rounded Next Shine Regular.ttf";

    public static void main(String[] args) {
        // Retrieve the token from environment variables
        String token = System.getenv("AGAPE_DISCORD_TOKEN");
        if (token == null || token.isEmpty()) {
            System.err.println("CRITICAL ERROR: AGAPE_DISCORD_TOKEN environment variable is missing!");
            return;
        }

        // Analytics mode: `-s`/`--stats <query>` runs one membership stat and exits.
        for (int i = 0; i < args.length; i++) {
            if ("-s".equals(args[i]) || "--stats".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("Usage: -s|--stats <query>   e.g. -s \"role{'Sister'}:%\"");
                    return;
                }
                runStats(token, args[i + 1]);
                return;
            }
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
     * Runs a single {@link StatsQuery} against every allowed guild's full member
     * list, prints the result to the console, and shuts down. Used by the
     * {@code -s}/{@code --stats} boot argument; never starts the normal bot.
     */
    private static void runStats(String token, String queryStr) {
        StatsQuery query;
        try {
            query = StatsQuery.parse(queryStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid --stats query: " + e.getMessage());
            return;
        }

        JDA jda;
        try {
            jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MEMBERS))
                    .setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.ALL)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            System.err.println("Stats: could not connect to Discord: " + e.getMessage());
            return;
        }

        try {
            boolean ranAny = false;
            for (Guild guild : jda.getGuilds()) {
                if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;
                ranAny = true;

                java.util.List<net.dv8tion.jda.api.entities.Member> members = guild.loadMembers().get().stream()
                        .filter(m -> !m.getUser().isBot()) // analytics never counts bots
                        .collect(java.util.stream.Collectors.toList());
                long total = members.size();
                long matched = members.stream()
                        .filter(m -> m.getRoles().stream().anyMatch(r -> query.matchesRole(r.getName())))
                        .count();

                System.out.println("Stats [" + guild.getName() + "] " + query + "  →  " + query.format(matched, total));
            }
            if (!ranAny) System.err.println("Stats: no allowed guild found for this environment.");
        } catch (Exception e) {
            System.err.println("Stats: failed to evaluate query: " + e.getMessage());
        } finally {
            jda.shutdownNow();
        }
    }

    // ─── Startup ──────────────────────────────────────────────────────────────

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("================================================================================");
        System.out.println("Bot is ready! Logged in as: " + event.getJDA().getSelfUser().getName());
        System.out.println("Environment: " + EnvironmentManager.getEnvironmentName());
        System.out.println("Allowed Guild ID: " + EnvironmentManager.getAllowedGuildId());
        System.out.println("Available Guilds:");
        event.getJDA().getGuilds().forEach(guild -> {
            String marker = EnvironmentManager.isGuildAllowed(guild.getId()) ? "✅" : "❌";
            System.out.println("  " + marker + " " + guild.getName() + " (ID: " + guild.getId() + ")");
        });
        System.out.println("================================================================================");

        // One-shot diagnostics: what can Agape actually see/do for the Arcane XP integration?
        IntegrationDiagnostics.logArcaneXpContext(event.getJDA());

        // Archive any threads that expired while the bot was offline, and catch up on notifications
        ThreadManager.checkExpiredThreads(event.getJDA());
        ThreadManager.checkManualMatchNotifications(event.getJDA());
        ThreadManager.checkQuickmatchNotifications(event.getJDA());

        // Sync preference insights from all accepted profiles
        UserInsightsManager.syncAllProfiles();

        // Role backfill sweep. Loads each allowed guild's members ONCE via gateway
        // chunking (not per-user REST, which previously caused 429s), then fixes
        // roles only where actually needed:
        //   • removes "not enrolled" from accepted members who still have it
        //   • adds the Brother/Sister role to members missing a gender role
        new Thread(() -> {
            java.io.File dir = new java.io.File("user_content/profiles/");
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) return;

            java.util.Map<String, net.dv8tion.jda.api.entities.Member> membersById = new java.util.HashMap<>();
            for (net.dv8tion.jda.api.entities.Guild guild : event.getJDA().getGuilds()) {
                if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;
                try {
                    for (net.dv8tion.jda.api.entities.Member m : guild.loadMembers().get()) membersById.put(m.getId(), m);
                } catch (Exception e) {
                    System.err.println("AgapeBot: Could not load members for role sweep: " + e.getMessage());
                }
            }

            int unenrolled = 0, gendered = 0;
            for (java.io.File f : files) {
                try {
                    String uid = f.getName().replace(".json", "");
                    AppState state = ProfileRepository.load(uid);
                    if (state == null || state.softDeleted) continue;
                    net.dv8tion.jda.api.entities.Guild guild = state.guildId != null ? event.getJDA().getGuildById(state.guildId) : null;
                    if (guild == null) continue;
                    net.dv8tion.jda.api.entities.Member member = membersById.get(uid);
                    if (member == null) continue; // not in the guild (left / never joined)

                    if (Roles.ensureGenderRole(guild, member, state.sex)) gendered++;
                    if ("ACCEPTED".equals(state.status) && Roles.removeNotEnrolledRole(guild, member)) unenrolled++;
                } catch (Exception e) {
                    System.err.println("AgapeBot: Error in role sweep for " + f.getName() + ": " + e.getMessage());
                }
            }
            System.out.println("AgapeBot: Role sweep complete — removed 'not enrolled' from " + unenrolled
                + " member(s), added gender role to " + gendered + " member(s).");
        }, "role-sweep").start();

        // Restore any applications that were in-flight when the bot last shut down
        ApplicationHandler.recoverInProgressApplications(event.getJDA());

        // Post today's Let's Chat question if the window has passed and it hasn't been posted yet
        LetsChatManager.checkAndPost(event.getJDA());

        // Scan recent channel history for controversial content (report-only for now)
        ServerProtectionManager.scanRecentMessages(event.getJDA());

        // Schedule ongoing expiry and notification checks every 5 minutes
        final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qm-thread-manager");
                t.setDaemon(true);
                return t;
            });
        scheduler.scheduleAtFixedRate(
            () -> {
                ThreadManager.checkExpiredThreads(event.getJDA());
                ThreadManager.checkManualMatchNotifications(event.getJDA());
                ThreadManager.checkQuickmatchNotifications(event.getJDA());
                LetsChatManager.checkAndPost(event.getJDA());
            },
            5, 5, TimeUnit.MINUTES
        );

        // Scan channel history for controversial content on a slower cadence (every 6 hours)
        scheduler.scheduleAtFixedRate(
            () -> ServerProtectionManager.scanRecentMessages(event.getJDA()),
            6, 6, TimeUnit.HOURS
        );

        // Register a JVM shutdown hook so SIGTERM (e.g. systemctl restart/stop) waits
        // for any in-progress thread archival to finish before the process exits.
        final JDA jda = event.getJDA();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("AgapeBot: Shutdown signal received — stopping scheduler...");
            scheduler.shutdown();
            ThreadManager.initiateShutdown(10_000);
            jda.shutdown();
            System.out.println("AgapeBot: Shutdown complete.");
        }, "agape-shutdown-hook"));

        registerSlashCommands(event.getJDA());
    }

    /** Declares every slash command and pushes them to each guild (instant refresh). */
    private void registerSlashCommands(JDA jda) {
        SlashCommandData generateCmd = Commands.slash("generate", "Generates a matchmaking profile image for a user.")
                .addOption(OptionType.USER, "target", "The user to generate the profile for", true);

        SlashCommandData applyCmd = Commands.slash("apply", "Apply for matchmaking (Sends a DM)");

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

        SlashCommandData matchCmd = Commands.slash("match", "Manually match two users (Matchmakers only)")
                .addOption(OptionType.USER, "user1", "First user to match", true)
                .addOption(OptionType.USER, "user2", "Second user to match", true);

        SlashCommandData qmThreadCmd = Commands.slash("qm-thread", "View the QM thread log for two users (Matchmakers only)")
                .addOption(OptionType.USER, "user1", "First user", true)
                .addOption(OptionType.USER, "user2", "Second user", true);

        SlashCommandData mmThreadCmd = Commands.slash("mm-thread", "View the Manual Match thread log for two users (Matchmakers only)")
                .addOption(OptionType.USER, "user1", "First user", true)
                .addOption(OptionType.USER, "user2", "Second user", true);

        SlashCommandData confirmCmd = Commands.slash("confirm", "Confirm this match (use inside a match thread)");
        SlashCommandData declineCmd = Commands.slash("decline", "Decline this match (use inside a match thread)");
        SlashCommandData closeThreadCmd = Commands.slash("close-thread", "Immediately close and archive this match thread without issuing penalties (Matchmakers only)");
        SlashCommandData viewMatchesCmd = Commands.slash("view-matches", "View all matches logged in the system (Matchmakers only)");
        SlashCommandData userInsightsCmd = Commands.slash("user-insights", "View collected preference insights for a user (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The user to look up", true);
        SlashCommandData tagUserCmd = Commands.slash("tag-user", "Add or remove preference tags for a user (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The user to tag", true)
                .addOption(OptionType.STRING, "tags", "Space-separated tags, e.g. +touchy -horror -smoking", true);

        SlashCommandData pardonCmd = Commands.slash("pardon", "Issue a pardon to a user, offsetting one active strike (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The user to pardon", true);

        SlashCommandData endMatchCmd = Commands.slash("end-match", "Mark a confirmed match as Ended in the match log (Matchmakers only)")
                .addOption(OptionType.USER, "user1", "One member of the match", true)
                .addOption(OptionType.USER, "user2", "The other member of the match", true)
                .addOption(OptionType.USER, "ghosted-by", "If ended due to ghosting, the user who ghosted", false);

        SlashCommandData setOptCmd = Commands.slash("set-opt", "Opt a user's profile in or out of matchmaking (Matchmakers only)")
                .addOption(OptionType.USER, "user", "The user whose profile to opt in or out", true)
                .addOption(OptionType.BOOLEAN, "opted-in", "true to opt in (restore), false to opt out (soft-delete)", true);

        // 1. Force refresh the commands on every specific server the bot is in (Updates instantly!)
        jda.getGuilds().forEach(guild -> {
            guild.updateCommands()
                .addCommands(generateCmd, applyCmd, messageCmd, statusCmd, historyCmd, quickmatchCmd, toggleQmCmd, compatAlgoCmd, matchCmd, qmThreadCmd, mmThreadCmd, confirmCmd, declineCmd, closeThreadCmd, viewMatchesCmd, userInsightsCmd, tagUserCmd, pardonCmd, endMatchCmd, setOptCmd)
                .queue();
            System.out.println("Refreshed commands for server: " + guild.getName());
        });

        // 2. Clear out the global commands cache to prevent duplicate entries
        jda.updateCommands().queue();
    }

    /** Report joins from very new accounts (raid / mass-reporter tell). */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (!EnvironmentManager.isGuildAllowed(event.getGuild().getId())) return;
        ServerProtectionManager.reportNewAccount(event.getMember());
        // Jail accounts younger than a week in the "dungeon" role. Disabled for now —
        // uncomment to enable quarantining of brand-new accounts on join:
        // ServerProtectionManager.jailIfTooNew(event.getMember());
    }

    // ─── Slash command routing ────────────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Validate guild for this environment
        if (event.getGuild() != null && !EnvironmentManager.isGuildAllowed(event.getGuild().getId())) {
            event.reply("❌ This command is not available in this server.").setEphemeral(true).queue();
            System.out.println("[SECURITY] Rejected command '" + event.getName() + "' from guild " + event.getGuild().getId() + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            return;
        }

        switch (event.getName()) {
            case "generate":        handleGenerate(event); break;
            case "apply":           handleApply(event); break;
            case "admin-message":   handleAdminMessage(event); break;
            case "app-status":      handleAppStatus(event); break;
            case "message-history": handleMessageHistory(event); break;
            case "quickmatch":      handleQuickmatch(event); break;
            case "toggle-qm":       handleToggleQm(event); break;
            case "compat-algo":     handleCompatAlgo(event); break;
            case "match":           handleMatch(event); break;
            case "qm-thread":       handleQmThread(event); break;
            case "mm-thread":       handleMmThread(event); break;
            case "confirm":         handleConfirm(event); break;
            case "decline":         handleDecline(event); break;
            case "close-thread":    handleCloseThread(event); break;
            case "view-matches":    handleViewMatches(event); break;
            case "user-insights":   handleUserInsights(event); break;
            case "tag-user":        handleTagUser(event); break;
            case "pardon":          handlePardon(event); break;
            case "end-match":       handleEndMatch(event); break;
            case "set-opt":         handleSetOpt(event); break;
            default: break;
        }
    }

    /** /generate — render a user's profile card image (matchmakers only). */
    private void handleGenerate(SlashCommandInteractionEvent event) {
        // Only members with the matchmaker role may generate arbitrary cards
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ You do not have permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Defer the reply. Image generation might take longer than Discord's 3-second timeout limit!
        event.deferReply().queue();

        User targetUser = event.getOption("target").getAsUser();
        String userId = targetUser.getId();
        String displayName = targetUser.getEffectiveName();

        // Load user profile and build text from actual profile data, or use placeholder
        String cardText;
        String avatarUrl = targetUser.getEffectiveAvatarUrl(); // Default to Discord PFP
        String backgroundPath = "assets/backgrounds/default.png";
        String framePath = "assets/frames/default.png";
        float focusX = 0.5f; // face focal point; stays centered for placeholders/avatars
        float focusY = 0.5f;

        if (ProfileRepository.exists(userId)) {
            AppState state = ProfileRepository.load(userId);
            if (state != null) {
                // Use the actual profile data to build the card text
                cardText = ApplicationHandler.buildCardText(state);

                // Use the profile's photoPath if available and it's not a placeholder
                if (state.photoPath != null && !state.photoPath.isEmpty()) {
                    if (state.photoPath.equalsIgnoreCase("assets/male.png") || state.photoPath.equalsIgnoreCase("assets/female.png")) {
                        // Use sex-based placeholder (female if sex=true, male if sex=false)
                        String placeholderPath = state.sex ? "assets/female.png" : "assets/male.png";
                        try {
                            avatarUrl = new File(placeholderPath).toURI().toURL().toString();
                            System.out.println("ℹ️ Using sex-specific placeholder: " + placeholderPath);
                        } catch (Exception e) {
                            System.err.println("⚠️ Failed to convert placeholder path to URL: " + placeholderPath);
                        }
                    } else if (state.photoPath.startsWith("http")) {
                        // It's already a URL, use it directly
                        avatarUrl = state.photoPath;
                    } else {
                        // It's a local file path, convert to URL
                        try {
                            avatarUrl = new File(state.photoPath).toURI().toURL().toString();
                        } catch (Exception e) {
                            System.err.println("⚠️ Failed to convert photo path to URL: " + state.photoPath);
                            // Keep default Discord PFP if conversion fails
                        }
                    }
                }

                // Crop the card photo toward the applicant's detected face
                focusX = state.photoFocusX;
                focusY = state.photoFocusY;

                // Load their custom Design Code if it exists
                if (state.designCode != null && !state.designCode.isEmpty()) {
                    String[] decodedPaths = ImageGenerator.decodeDesignCode(state.designCode);
                    backgroundPath = decodedPaths[0];
                    framePath = decodedPaths[1];
                }
                System.out.println("✅ Generating profile image for " + state.name + " (ID: " + userId + ") using actual profile data");
            } else {
                System.err.println("⚠️ Failed to load profile for " + userId + ", using placeholder");
                cardText = placeholderCardText(displayName, targetUser.getName());
            }
        } else {
            cardText = placeholderCardText(displayName, targetUser.getName());
            System.out.println("ℹ️ No profile found for " + userId + ", using placeholder text");
        }

        // Verify assets exist before generating
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
        final String profileCardText = cardText;
        final String photoUrl = avatarUrl;
        final float pfpFocusX = focusX;
        final float pfpFocusY = focusY;

        // Run the generation in a new thread so it doesn't block JDA's main event loop
        new Thread(() -> {
            File generatedImage = ImageGenerator.generateForUser(bgPath, photoUrl, fmPath, FONT_PATH,
                    profileCardText, userId, pfpFocusX, pfpFocusY);

            if (generatedImage != null && generatedImage.exists()) {
                event.getHook().sendFiles(FileUpload.fromData(generatedImage)).queue(
                        success -> generatedImage.delete(), // Clean up the temp file so we don't leak storage
                        error -> {
                            System.err.println("Failed to send image to Discord: " + error.getMessage());
                            generatedImage.delete();
                        });
            } else {
                event.getHook().sendMessage("❌ Sorry, I encountered an error while generating the image!").queue();
            }
        }).start();
    }

    /** The demo card used by /generate when the target has no profile on file. */
    private static String placeholderCardText(String displayName, String handle) {
        return "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:2}{f:Arial Rounded MT Bold}"
                + displayName
                + "{/}*\n"
                + "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:2}{f:Arial Rounded MT Bold}@"
                + handle + "{/}*\n\n"
                + "20 | 2005\n"
                + "M\n"
                + "DISCORD USER\n"
                + "EARTH / ENGLISH\n"
                + "PROGRAMMER\n\n"
                + "LOVE TO CHAT, PLAY GAMES, AND BUILD BOTS. 🧠\n\n"
                + "{img:green_flag.png} PARTNER: KIND, COMMUNICATIVE, FUN\n"
                + "{img:red_flag.png} PARTNER: TOXIC, UNAVAILABLE.";
    }

    /** /apply — start (or manage) a matchmaking application. */
    private void handleApply(SlashCommandInteractionEvent event) {
        // Only single members may apply
        if (!Roles.isSingle(event.getMember())) {
            event.reply("❌ You need to be single to apply.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue();

        String userId = event.getUser().getId();

        if (!ProfileRepository.exists(userId)) {
            // No existing profile — start fresh immediately
            ApplicationHandler.startApplication(event.getUser(), event);
            return;
        }

        // Existing profile found — warn the user before overwriting
        String currentStatus = "PENDING";
        boolean isSoftDeleted = false;
        AppState existing = ProfileRepository.load(userId);
        if (existing != null) {
            if (existing.status != null) currentStatus = existing.status;
            isSoftDeleted = existing.softDeleted;
        }

        if (isSoftDeleted) {
            event.getHook().sendMessage(
                "❌ Your profile has been deactivated and cannot be resubmitted. Please contact a matchmaker if you believe this is an error."
            ).queue();
            return;
        }

        if ("ACCEPTED".equals(currentStatus) || "PENDING".equals(currentStatus)) {
            event.getHook().sendMessage(
                "⚠️ You already have a **" + currentStatus.toLowerCase() + "** profile on file — no need to apply again!\n\n"
                + "If you'd like to opt in or out of Quickmatch, use **/toggle-qm**."
            ).queue();
            return;
        }

        final String statusDisplay = currentStatus;
        event.getUser().openPrivateChannel().queue(channel -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚠️ You Already Have a Profile")
                .setColor(0xFF8800)
                .setDescription("You already have a profile in our system.\n\n"
                    + "**Current status:** " + statusDisplay + "\n\n"
                    + "What would you like to do?\n\n"
                    + "• **Edit Current Profile** — update specific fields and re-submit for review\n"
                    + "• **Delete and Continue** — permanently delete your profile and start over\n"
                    + "• **Cancel** — keep things as they are")
                .setFooter("Agape Matchmaking");

            Button editBtn   = Button.primary("reapply_edit_" + userId, "✏️ Edit Current Profile");
            Button deleteBtn = Button.danger("reapply_delete_" + userId, "🗑️ Delete and Continue");
            Button cancelBtn = Button.secondary("reapply_cancel_" + userId, "❌ Cancel");

            channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(editBtn, deleteBtn, cancelBtn))
                .queue(
                    s -> event.getHook().sendMessage("✅ Check your DMs — I've sent you options for your existing profile.").queue(),
                    e -> event.getHook().sendMessage("❌ I couldn't send you a DM. Please ensure your DMs are open and try again.").queue()
                );
        }, err -> event.getHook().sendMessage("❌ I couldn't open a DM with you. Please ensure your DMs are open and try again.").queue());
    }

    /** /admin-message — DM an applicant on behalf of the matchmaker team. */
    private void handleAdminMessage(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
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
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💬 Message from Matchmaker")
                .setColor(0xFF9999)
                .setDescription(messageContent)
                .setFooter("Matchmaker ID: " + matchmakerId)
                .setTimestamp(java.time.Instant.now());

            // Add reply button to the DM
            Button replyBtn = Button.primary("convo_reply_" + applicantId + "_" + matchmakerId, "💬 Reply");

            channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(replyBtn))
                .queue(success -> {
                    // Store the new message ID
                    String newMessageId = success.getId();
                    MessagingHandler.saveDMMessageId(applicantId, matchmakerId, newMessageId);
                    MessagingHandler.saveMessage(applicantId, matchmakerId, "matchmaker", messageContent);
                    if (event.getGuild() != null) {
                        MessagingHandler.saveConversationGuildId(applicantId, matchmakerId, event.getGuild().getId());
                    }

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
            ConversationRelay.postConversationStartToChannel(
                applicant, messageContent, matchmakerId, event.getGuild().getId(), event.getJDA()
            );
        }
    }

    /** /app-status — show a user's application status (matchmakers only). */
    private void handleAppStatus(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String userId = targetUser.getId();

        event.deferReply().queue();

        if (!ProfileRepository.exists(userId)) {
            event.getHook().sendMessage("❌ No application found for this user.").queue();
            return;
        }

        AppState state = ProfileRepository.load(userId);
        if (state == null) {
            System.err.println("Error checking status for " + userId);
            event.getHook().sendMessage("❌ Error retrieving status.").queue();
            return;
        }

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
    }

    /** /message-history — show the saved matchmaker↔applicant conversation. */
    private void handleMessageHistory(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
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

    /** /quickmatch — find a random eligible match and open a QM thread. */
    private void handleQuickmatch(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userId = event.getUser().getId();

        MatchmakingEngine.MatchResult result = MatchmakingEngine.quickmatch(userId, event.getJDA());

        if (result == null) {
            event.getHook().sendMessage(
                "💔 No match found right now. You may be on cooldown, not enrolled in quickmatch, or there are no eligible candidates at the moment."
            ).queue();
            return;
        }

        int matchedAge = AgeUtils.calculateAge(result.matchedProfile.birthday);

        EmbedBuilder embed = new EmbedBuilder()
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
            AppState runnerProfile = ProfileRepository.load(userId);

            String runnerName = (runnerProfile != null && runnerProfile.name != null)
                ? runnerProfile.name : event.getUser().getEffectiveName();
            boolean runnerIsMale = runnerProfile == null || !runnerProfile.sex;
            boolean matchedIsMale = !result.matchedProfile.sex;
            String matchedName = result.matchedProfile.name != null
                ? result.matchedProfile.name : "Unknown";

            MatchThreadService.createMatchThread(
                event.getGuild(),
                userId, runnerIsMale, runnerName, runnerProfile,
                result.matchedUserId, matchedIsMale, matchedName, result.matchedProfile,
                false
            );
        }
    }

    /** /toggle-qm — flip the user's quickmatch enrollment (with strike gating). */
    private void handleToggleQm(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userId = event.getUser().getId();

        if (!ProfileRepository.exists(userId)) {
            event.getHook().sendMessage("❌ You don't have a profile on file. Use `/apply` to get started.").queue();
            return;
        }

        AppState state = ProfileRepository.load(userId);
        if (state == null) {
            System.err.println("Error toggling quickmatch for " + userId);
            event.getHook().sendMessage("❌ Error updating your quickmatch status.").queue();
            return;
        }

        if (!"ACCEPTED".equals(state.status)) {
            event.getHook().sendMessage("❌ Your profile must be accepted before you can manage quickmatch enrollment.").queue();
            return;
        }

        if (!state.quickmatchEnrolled) {
            int netStrikes = ThreadManager.getNetStrikeCount(userId);
            if (netStrikes >= 3) {
                int rawStrikes = ThreadManager.getRecentStrikeCount(userId);
                int rawPardons = ThreadManager.getRecentPardonCount(userId);
                event.getHook().sendMessage(
                    "❌ You cannot re-enroll in quickmatch — your aggregate standing is **" + netStrikes
                    + "/3 strikes** (" + rawStrikes + " strike(s) − " + rawPardons + " pardon(s)). "
                    + "Strikes and pardons expire after 6 months."
                ).queue();
                return;
            }
        }

        state.quickmatchEnrolled = !state.quickmatchEnrolled;
        ProfileRepository.save(userId, state);

        String statusMsg = state.quickmatchEnrolled
            ? "✅ You are now **enrolled** in quickmatch. Run `/quickmatch` to find a match!"
            : "✅ You are now **unenrolled** from quickmatch.";
        event.getHook().sendMessage(statusMsg).queue();
    }

    /** /compat-algo — rank the top compatibility pairs across all profiles. */
    private void handleCompatAlgo(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        new Thread(() -> {
            CompatibilityEngine.ScoringResult result = CompatibilityEngine.findTopMatches(10, event.getJDA());

            if (result.topPairs.isEmpty()) {
                event.getHook().sendMessage(
                    "❌ Not enough accepted profiles to generate compatibility matches. "
                    + "(Found **" + result.profileCount + "** profile(s), "
                    + "**" + result.pairCount + "** opposite-sex pair(s).)"
                ).queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💘 Compatibility Analysis — Top Matches")
                .setColor(0xFF6699)
                .setDescription(
                    "Analyzed **" + result.profileCount + "** profiles · "
                    + "**" + result.pairCount + "** opposite-sex pair(s) evaluated."
                )
                .setTimestamp(java.time.Instant.now());

            java.util.List<Button> buttons = new java.util.ArrayList<>();

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
                        + "  Dist: " + pair.dist.score
                        + "  Val: " + pair.values.score
                        + "  DB: " + pair.dealBreakers.score + "\n"
                        + "<@" + pair.userId1 + "> · <@" + pair.userId2 + ">",
                    false
                );

                buttons.add(Button.secondary(
                    "compat_breakdown_" + pair.userId1 + "_" + pair.userId2,
                    "#" + rank + " Breakdown"
                ));
            }

            // Pack buttons into rows of up to 5
            java.util.List<ActionRow> rows = new java.util.ArrayList<>();
            for (int i = 0; i < buttons.size(); i += 5) {
                rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
            }

            if (!rows.isEmpty()) {
                event.getHook().sendMessageEmbeds(embed.build()).setComponents(rows).queue();
            } else {
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }

        }, "compat-algo").start();
    }

    /** /match — manually propose a match between two users (shows preview first). */
    private void handleMatch(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can use this command.").setEphemeral(true).queue();
            return;
        }

        User user1 = event.getOption("user1").getAsUser();
        User user2 = event.getOption("user2").getAsUser();
        String uid1 = user1.getId();
        String uid2 = user2.getId();

        if (uid1.equals(uid2)) {
            event.reply("❌ You cannot match a user with themselves.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        MatchPreviewService.sendMatchPreview(uid1, uid2, event.getHook());
    }

    /** /qm-thread — print the archived quickmatch thread log for a pair. */
    private void handleQmThread(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }

        String uid1 = event.getOption("user1").getAsUser().getId();
        String uid2 = event.getOption("user2").getAsUser().getId();

        event.deferReply().queue();

        ThreadManager.QMThread log = ThreadManager.findThread(uid1, uid2);
        if (log == null) {
            event.getHook().sendMessage(
                "❌ No QM thread log found between <@" + uid1 + "> and <@" + uid2 + ">."
            ).queue();
            return;
        }

        sendChunks(event, ThreadLogFormatter.buildQMThreadOutput(log));
    }

    /** /mm-thread — print the archived manual match thread log for a pair. */
    private void handleMmThread(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }

        String uid1 = event.getOption("user1").getAsUser().getId();
        String uid2 = event.getOption("user2").getAsUser().getId();

        event.deferReply().queue();

        ThreadManager.QMThread log = ThreadManager.findMMThread(uid1, uid2);
        if (log == null) {
            event.getHook().sendMessage(
                "❌ No Manual Match thread log found between <@" + uid1 + "> and <@" + uid2 + ">."
            ).queue();
            return;
        }

        sendChunks(event, ThreadLogFormatter.buildMMThreadOutput(log));
    }

    /** Sends the first chunk as the deferred reply and the rest as follow-up channel messages. */
    private static void sendChunks(SlashCommandInteractionEvent event, java.util.List<String> chunks) {
        event.getHook().sendMessage(chunks.get(0)).queue(sent -> {
            for (int i = 1; i < chunks.size(); i++) {
                final String chunk = chunks.get(i);
                event.getChannel().sendMessage(chunk).queue();
            }
        });
    }

    /** /confirm — confirm a match from inside its thread. */
    private void handleConfirm(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof ThreadChannel)) {
            event.reply("❌ This command can only be used inside a match thread.").setEphemeral(true).queue();
            return;
        }

        String threadId = event.getChannel().getId();
        String userId = event.getUser().getId();

        ThreadManager.QMThread record = ThreadManager.findThreadByChannelId(threadId);
        if (record == null || (!"MANUAL".equals(record.matchType) && !"QUICKMATCH".equals(record.matchType))) {
            event.reply("❌ This command can only be used inside a match thread.").setEphemeral(true).queue();
            return;
        }
        if (!"OPEN".equals(record.status)) {
            event.reply("❌ This thread is no longer active.").setEphemeral(true).queue();
            return;
        }
        if (!userId.equals(record.maleId) && !userId.equals(record.femaleId)) {
            event.reply("❌ Only the two matched users can use this command.").setEphemeral(true).queue();
            return;
        }
        if (record.confirmedBy != null && record.confirmedBy.contains(userId)) {
            event.reply("✅ You have already confirmed this match.").setEphemeral(true).queue();
            return;
        }

        boolean bothConfirmed = ThreadManager.recordConfirmation(threadId, userId);

        String displayName = event.getMember() != null
            ? event.getMember().getEffectiveName()
            : event.getUser().getEffectiveName();
        ThreadManager.logEvent(threadId, userId, displayName, "[/confirm]");
        event.reply("✅ **" + displayName + "** has confirmed the match!").queue();

        if (bothConfirmed && "MANUAL".equals(record.matchType) && event.getGuild() != null) {
            Guild guild = event.getGuild();
            final String finalMaleId = record.maleId;
            final String finalFemaleId = record.femaleId;
            final String finalThreadId = threadId;

            event.getChannel().sendMessage(
                "## 💍 It's a Match!\n\n"
                + "<@" + finalMaleId + "> and <@" + finalFemaleId + "> have both confirmed! "
                + "Congratulations — we're all rooting for you! 🎉"
            ).queue(msg -> ThreadManager.closeThread(finalThreadId, event.getJDA()));

            MatchThreadService.sendManualMatchDMs(guild, finalMaleId, finalFemaleId);
            MatchThreadService.assignMatchedRole(guild, finalMaleId, finalFemaleId);
        }

        if (bothConfirmed && "QUICKMATCH".equals(record.matchType)) {
            final String finalThreadId = threadId;
            event.getChannel().sendMessage(
                "✅ Both parties have confirmed. This thread will now be closed."
            ).queue(msg -> ThreadManager.closeThread(finalThreadId, event.getJDA()));
        }
    }

    /** /decline — decline a manual match (opens a reasons modal). */
    private void handleDecline(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof ThreadChannel)) {
            event.reply("❌ This command can only be used inside a match thread.").setEphemeral(true).queue();
            return;
        }

        String threadId = event.getChannel().getId();
        String userId = event.getUser().getId();

        ThreadManager.QMThread record = ThreadManager.findThreadByChannelId(threadId);
        if (record == null || !"MANUAL".equals(record.matchType)) {
            event.reply("❌ `/decline` is only available in manual match threads.").setEphemeral(true).queue();
            return;
        }
        if (!"OPEN".equals(record.status)) {
            event.reply("❌ This thread is no longer active.").setEphemeral(true).queue();
            return;
        }
        if (!userId.equals(record.maleId) && !userId.equals(record.femaleId)) {
            event.reply("❌ Only the two matched users can use this command.").setEphemeral(true).queue();
            return;
        }

        TextInput reasonsInput = TextInput
            .create("decline_reasons", "Reasons for declining (list at least 3)", TextInputStyle.PARAGRAPH)
            .setPlaceholder("1. \n2. \n3. ")
            .setMinLength(30)
            .setMaxLength(1000)
            .setRequired(true)
            .build();

        event.replyModal(
            Modal.create("match_decline_" + threadId, "Decline Match")
                .addActionRow(reasonsInput)
                .build()
        ).queue();
    }

    /** /close-thread — staff force-close of a match thread (no penalties). */
    private void handleCloseThread(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }
        if (!(event.getChannel() instanceof ThreadChannel)) {
            event.reply("❌ This command must be run inside a match thread.").setEphemeral(true).queue();
            return;
        }
        String threadId = event.getChannel().getId();
        ThreadManager.QMThread record = ThreadManager.findThreadByChannelId(threadId);
        if (record == null) {
            event.reply("❌ No match thread record found for this channel.").setEphemeral(true).queue();
            return;
        }
        if (!"OPEN".equals(record.status)) {
            event.reply("❌ This thread is already closed.").setEphemeral(true).queue();
            return;
        }
        event.reply("🔒 Closing thread — no strikes or penalties will be issued.").queue();

        // Notify the matchmaker channel
        if (event.getGuild() != null) {
            TextChannel mmChannel = Channels.findMatchmakerChannel(event.getGuild());
            if (mmChannel != null) {
                String closer = event.getMember() != null ? event.getMember().getAsMention() : event.getUser().getAsMention();
                String matchType = "MANUAL".equals(record.matchType) ? "Manual Match" : "Quickmatch";
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Thread Force-Closed")
                    .setColor(0xFF9900)
                    .addField("Closed by", closer, true)
                    .addField("Match type", matchType, true)
                    .addField("Users", "<@" + record.maleId + "> & <@" + record.femaleId + ">", false)
                    .setFooter("Thread ID: " + threadId)
                    .setTimestamp(java.time.Instant.now());
                mmChannel.sendMessageEmbeds(embed.build()).queue();
            }
        }

        ThreadManager.adminCloseThread(threadId, event.getJDA());
    }

    /** /view-matches — list every match record with its outcome. */
    private void handleViewMatches(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();

        sendChunks(event, ThreadManager.buildMatchesReport());
    }

    /** /end-match — mark a confirmed match as Ended (or Ghosted) in the match log. */
    private void handleEndMatch(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }
        String id1 = event.getOption("user1").getAsUser().getId();
        String id2 = event.getOption("user2").getAsUser().getId();
        net.dv8tion.jda.api.interactions.commands.OptionMapping ghostOpt = event.getOption("ghosted-by");
        String ghostedBy = ghostOpt != null ? ghostOpt.getAsUser().getId() : null;

        boolean found = ThreadManager.markMatchEnded(id1, id2, ghostedBy);
        if (!found) {
            event.reply("❌ No match record found for <@" + id1 + "> and <@" + id2 + ">.").setEphemeral(true).queue();
            return;
        }
        if (ghostedBy != null) {
            event.reply("✅ Match between <@" + id1 + "> and <@" + id2 + "> marked as **Confirmed (Ghosted by <@" + ghostedBy + ">**)").setEphemeral(true).queue();
        } else {
            event.reply("✅ Match between <@" + id1 + "> and <@" + id2 + "> marked as **Confirmed (Ended)**.").setEphemeral(true).queue();
        }
    }

    /**
     * /set-opt — opt a user's profile in or out of matchmaking.
     *
     * <p>{@code opted-in = true} restores a soft-deleted profile (clears the flag);
     * {@code opted-in = false} soft-deletes it, hiding the user from all matchmaking
     * systems. This is the manual counterpart to {@link MembershipVerifier}, which
     * soft-deletes profiles of members who leave the guild.
     */
    private void handleSetOpt(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String targetId = targetUser.getId();
        boolean optedIn = event.getOption("opted-in").getAsBoolean();

        AppState state = ProfileRepository.load(targetId);
        if (state == null) {
            event.reply("❌ No profile found for <@" + targetId + ">.").setEphemeral(true).queue();
            return;
        }

        boolean shouldSoftDelete = !optedIn; // opted in → active; opted out → soft-deleted
        if (state.softDeleted == shouldSoftDelete) {
            event.reply("ℹ️ <@" + targetId + "> is already opted **" + (optedIn ? "in" : "out") + "**. No change made.")
                    .setEphemeral(true).queue();
            return;
        }

        state.softDeleted = shouldSoftDelete;
        ProfileRepository.save(targetId, state);
        System.out.println("set-opt: " + event.getUser().getId() + " opted " + (optedIn ? "in" : "out")
                + " profile " + targetId + " (softDeleted=" + shouldSoftDelete + ")");

        event.reply("✅ <@" + targetId + ">'s profile is now opted **" + (optedIn ? "in" : "out") + "**"
                + (optedIn ? " — visible to matchmaking again." : " — soft-deleted and hidden from all matchmaking."))
                .setEphemeral(true).queue();
    }

    /** /user-insights — show collected preference tags and decline history. */
    private void handleUserInsights(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String targetId = targetUser.getId();

        event.deferReply().queue();

        UserInsightsManager.UserInsightsRecord record = UserInsightsManager.getInsights(targetId);
        boolean hasPrefs    = record != null && record.preferences    != null && !record.preferences.isEmpty();
        boolean hasDeclines = record != null && record.declineHistory != null && !record.declineHistory.isEmpty();
        if (!hasPrefs && !hasDeclines) {
            event.getHook().sendMessage("📭 No insights collected yet for <@" + targetId + ">.").queue();
            return;
        }

        sendChunks(event, ThreadLogFormatter.buildUserInsightsOutput(targetId, record));
    }

    /** /tag-user — manually toggle preference tags (e.g. "+touchy -horror"). */
    private void handleTagUser(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers and admins can use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String targetId  = targetUser.getId();
        String tagsInput = event.getOption("tags").getAsString().trim();

        String[] tags = tagsInput.split("\\s+");
        java.util.List<String> invalid = new java.util.ArrayList<>();
        for (String tag : tags) {
            if (!tag.startsWith("+") && !tag.startsWith("-")) invalid.add(tag);
        }
        if (!invalid.isEmpty()) {
            event.reply("❌ Each tag must start with `+` or `-` (e.g. `+touchy -horror`). Invalid: `"
                + String.join("`, `", invalid) + "`").setEphemeral(true).queue();
            return;
        }

        java.util.List<String> added   = new java.util.ArrayList<>();
        java.util.List<String> removed = new java.util.ArrayList<>();
        UserInsightsManager.UserInsightsRecord record = null;
        for (String tag : tags) {
            record = UserInsightsManager.applyTag(targetId, tag);
            if (record.preferences.contains(tag)) added.add(tag);
            else removed.add(tag);
        }

        StringBuilder reply = new StringBuilder("Updated tags for <@" + targetId + ">:");
        if (!added.isEmpty())   reply.append("\n✅ Added: `").append(String.join("`, `", added)).append("`");
        if (!removed.isEmpty()) reply.append("\n❌ Removed: `").append(String.join("`, `", removed)).append("`");
        if (record != null && record.preferences != null && !record.preferences.isEmpty()) {
            reply.append("\n\n**Current tags:** `").append(String.join("`, `", record.preferences)).append("`");
        } else {
            reply.append("\n\n*No tags remaining.*");
        }
        event.reply(reply.toString()).queue();
    }

    /** /pardon — offset one of a user's active strikes. */
    private void handlePardon(SlashCommandInteractionEvent event) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can issue pardons.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();

        User targetUser = event.getOption("user").getAsUser();
        String targetId    = targetUser.getId();
        String grantedById = event.getUser().getId();

        ThreadManager.addPardon(targetId, grantedById, event.getJDA());

        int strikes = ThreadManager.getRecentStrikeCount(targetId);
        int pardons = ThreadManager.getRecentPardonCount(targetId);
        int net     = ThreadManager.getNetStrikeCount(targetId);

        if (event.getGuild() != null) {
            TextChannel mmChannel = Channels.findMatchmakerChannel(event.getGuild());
            if (mmChannel != null) {
                String grantor = event.getMember() != null
                    ? event.getMember().getAsMention()
                    : event.getUser().getAsMention();
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Pardon Issued")
                    .setColor(0x00CC66)
                    .setDescription("Aggregate standing: **" + net + "/3** active strikes")
                    .addField("User", targetUser.getAsMention(), true)
                    .addField("Pardoned by", grantor, true)
                    .addField("Active strikes", String.valueOf(strikes), true)
                    .addField("Active pardons", String.valueOf(pardons), true)
                    .setFooter("User ID: " + targetId)
                    .setTimestamp(java.time.Instant.now());
                mmChannel.sendMessageEmbeds(embed.build()).queue();
            }
        }

        event.getHook().sendMessage(
            "✅ Pardon issued to " + targetUser.getAsMention()
            + ". Aggregate standing: **" + net + "/3 strikes** ("
            + strikes + " strike(s) − " + pardons + " pardon(s))."
        ).queue();
    }

    // ─── Modal routing ────────────────────────────────────────────────────────

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        // Validate guild for this environment
        if (event.getGuild() != null && !EnvironmentManager.isGuildAllowed(event.getGuild().getId())) {
            event.reply("❌ This interaction is not available in this server.").setEphemeral(true).queue();
            System.out.println("[SECURITY] Rejected modal interaction from guild " + event.getGuild().getId() + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            return;
        }

        String modalId = event.getModalId();

        if (modalId.startsWith("qm_modal_feedback_")) {
            handleFeedbackModal(event, modalId);
        } else if (modalId.startsWith("qm_modal_report_")) {
            handleReportModal(event, modalId);
        } else if (modalId.startsWith("match_decline_")) {
            handleDeclineModal(event, modalId);
        }
    }

    /** Post-match feedback text submitted from a DM button. */
    private void handleFeedbackModal(ModalInteractionEvent event, String modalId) {
        String rest = modalId.substring("qm_modal_feedback_".length());
        int sep = rest.indexOf('_');
        if (sep < 0) { event.reply("❌ Invalid submission.").setEphemeral(true).queue(); return; }
        String userId = rest.substring(0, sep);
        String matchedId = rest.substring(sep + 1);
        String feedbackText = event.getValue("feedback_text").getAsString();
        long epochMs = java.time.Instant.now().toEpochMilli();
        String timestamp = java.time.Instant.ofEpochMilli(epochMs).toString();

        FeedbackReportService.saveFeedbackFile(userId, matchedId, feedbackText, timestamp, epochMs);
        FeedbackReportService.postFeedbackToMatchmakers(event.getJDA(), userId, matchedId, feedbackText, timestamp);
        event.reply("✅ Thank you for your feedback! It has been submitted to our matchmakers.").setEphemeral(true).queue();
    }

    /** Post-match user report submitted from a DM button. */
    private void handleReportModal(ModalInteractionEvent event, String modalId) {
        String rest = modalId.substring("qm_modal_report_".length());
        int sep = rest.indexOf('_');
        if (sep < 0) { event.reply("❌ Invalid submission.").setEphemeral(true).queue(); return; }
        String userId = rest.substring(0, sep);
        String matchedId = rest.substring(sep + 1);
        String reason = event.getValue("report_reason").getAsString();
        net.dv8tion.jda.api.interactions.modals.ModalMapping detailsMapping = event.getValue("report_details");
        String details = detailsMapping != null ? detailsMapping.getAsString() : "";
        long epochMs = java.time.Instant.now().toEpochMilli();
        String timestamp = java.time.Instant.ofEpochMilli(epochMs).toString();

        FeedbackReportService.saveReportFile(userId, matchedId, reason, details, timestamp, epochMs);
        FeedbackReportService.postReportToMatchmakers(event.getJDA(), userId, matchedId, reason, details, timestamp);
        event.reply("✅ Your report has been submitted. Our matchmakers will review it shortly.").setEphemeral(true).queue();
    }

    /** Decline reasons submitted from the /decline modal. */
    private void handleDeclineModal(ModalInteractionEvent event, String modalId) {
        String threadId = modalId.substring("match_decline_".length());
        String userId = event.getUser().getId();
        String reasons = event.getValue("decline_reasons").getAsString();

        ThreadManager.recordDecline(threadId, userId);
        String declinerName = event.getMember() != null
            ? event.getMember().getEffectiveName()
            : event.getUser().getName();
        ThreadManager.logEvent(threadId, userId, declinerName, "[/decline]");

        // Acknowledge the submission immediately
        event.reply("✅ Your decline has been submitted. Matchmakers have been notified.").setEphemeral(true).queue();

        // Post a visible message in the thread
        ThreadChannel thread = event.getJDA().getThreadChannelById(threadId);
        if (thread != null) {
            thread.sendMessage("❌ <@" + userId + "> has declined this match.").queue();
        }

        // Record insights and alert matchmakers
        ThreadManager.QMThread record = ThreadManager.findThreadByChannelId(threadId);
        if (record != null) {
            String partnerId = userId.equals(record.maleId) ? record.femaleId : record.maleId;
            UserInsightsManager.recordDecline(userId, partnerId, threadId, reasons);
        }
        if (record != null && record.guildId != null) {
            Guild guild = event.getJDA().getGuildById(record.guildId);
            if (guild != null) {
                TextChannel mmChannel = Channels.findMatchmakerChannel(guild);
                if (mmChannel != null) {
                    // Find a matchmaker role to ping
                    net.dv8tion.jda.api.entities.Role mmRole = Roles.findRoleContaining(guild, "matchmaker");
                    String ping = mmRole != null ? mmRole.getAsMention() : "";

                    String otherUserId = userId.equals(record.maleId) ? record.femaleId : record.maleId;
                    String threadLink = thread != null ? thread.getJumpUrl() : "`" + threadId + "`";

                    EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("❌ Manual Match Declined")
                        .setColor(0xFF3300)
                        .setDescription("<@" + userId + "> has declined their manual match.")
                        .addField("Declined user", "<@" + userId + ">", true)
                        .addField("Other user", "<@" + otherUserId + ">", true)
                        .addField("Match Thread", "[View Thread](" + threadLink + ")", false)
                        .addField("Reasons given", reasons, false)
                        .setFooter("User ID: " + userId)
                        .setTimestamp(java.time.Instant.now());

                    mmChannel.sendMessage(ping).setEmbeds(embed.build()).queue();
                }
            }
        }
    }

    // ─── Button routing ───────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Validate guild for this environment
        if (event.getGuild() != null && !EnvironmentManager.isGuildAllowed(event.getGuild().getId())) {
            event.reply("❌ This interaction is not available in this server.").setEphemeral(true).queue();
            System.out.println("[SECURITY] Rejected button interaction from guild " + event.getGuild().getId() + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            return;
        }

        String buttonId = event.getComponentId();

        if (buttonId.startsWith("compat_breakdown_")) {
            handleCompatBreakdownButton(event, buttonId);
        } else if (buttonId.startsWith("breakdown_matchmake_")) {
            handleBreakdownMatchmakeButton(event, buttonId);
        } else if (buttonId.startsWith("preclude_match_")) {
            handlePrecludeMatchButton(event, buttonId);
        } else if (buttonId.startsWith("match_confirm_")) {
            handleMatchConfirmButton(event, buttonId);
        } else if (buttonId.startsWith("match_cancel_")) {
            handleMatchCancelButton(event, buttonId);
        } else if (buttonId.startsWith("qm_feedback_")) {
            handleFeedbackButton(event, buttonId);
        } else if (buttonId.startsWith("qm_report_")) {
            handleReportButton(event, buttonId);
        }
    }

    /** Splits a "{prefix}{uid1}_{uid2}" button ID into the two user IDs, or null when malformed. */
    private static String[] parseUserPair(String buttonId, String prefix) {
        String rest = buttonId.substring(prefix.length());
        int sep = rest.indexOf('_');
        if (sep < 0) return null;
        return new String[]{rest.substring(0, sep), rest.substring(sep + 1)};
    }

    /** "#N Breakdown" button on the /compat-algo embed — show per-category scoring. */
    private void handleCompatBreakdownButton(ButtonInteractionEvent event, String buttonId) {
        event.deferReply().queue();

        String[] pair = parseUserPair(buttonId, "compat_breakdown_");
        if (pair == null) {
            event.getHook().sendMessage("❌ Malformed breakdown button ID.").queue();
            return;
        }
        String uid1 = pair[0], uid2 = pair[1];

        AppState p1 = ProfileRepository.load(uid1);
        AppState p2 = ProfileRepository.load(uid2);

        if (p1 == null || p2 == null) {
            event.getHook().sendMessage("❌ Could not load one or both profiles for this breakdown.").queue();
            return;
        }

        CompatibilityEngine.ScoreDetail denom  = CompatibilityEngine.scoreDenomination(p1, p2);
        CompatibilityEngine.ScoreDetail age    = CompatibilityEngine.scoreAge(p1, p2);
        CompatibilityEngine.ScoreDetail dist   = CompatibilityEngine.scoreDistance(p1, p2);
        CompatibilityEngine.ScoreDetail values = CompatibilityEngine.scoreValues(p1, p2);
        CompatibilityEngine.ScoreDetail db     = CompatibilityEngine.scoreDealBreakers(p1, p2);
        int total = denom.score + age.score + dist.score + values.score + db.score;

        String name1 = p1.name != null ? p1.name : uid1;
        String name2 = p2.name != null ? p2.name : uid2;

        EmbedBuilder breakdown = new EmbedBuilder()
            .setTitle("📊 Breakdown: " + name1 + " & " + name2)
            .setDescription("<@" + uid1 + "> × <@" + uid2 + ">")
            .setColor(0xFF9966)
            .addField("Total Score", "**" + total + " / " + CompatibilityEngine.MAX_TOTAL + "**", false)
            .addField("🙏 Denomination (" + denom.score + " / " + CompatibilityEngine.MAX_DENOM + ")", denom.detail, false)
            .addField("👶 Age (" + age.score + " / " + CompatibilityEngine.MAX_AGE + ")", age.detail, false)
            .addField("🌍 Distance (" + dist.score + " / " + CompatibilityEngine.MAX_DIST + ")", dist.detail, false)
            .addField("💛 Values (" + values.score + " / " + CompatibilityEngine.MAX_VALUES + ")", values.detail, false)
            .addField("🚩 Deal Breakers (" + db.score + ")", db.detail, false)
            .setTimestamp(java.time.Instant.now());

        Button matchmakeBtn = Button.success("breakdown_matchmake_" + uid1 + "_" + uid2, "💘 Matchmake");
        Button precludeBtn  = Button.danger("preclude_match_" + uid1 + "_" + uid2, "❌ Preclude Match");
        event.getHook().sendMessageEmbeds(breakdown.build())
            .addActionRow(matchmakeBtn, precludeBtn)
            .queue();
    }

    /** "Matchmake" button on a breakdown — open the standard match preview. */
    private void handleBreakdownMatchmakeButton(ButtonInteractionEvent event, String buttonId) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can use this command.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();
        String[] pair = parseUserPair(buttonId, "breakdown_matchmake_");
        if (pair == null) {
            event.getHook().sendMessage("❌ Malformed button ID.").queue();
            return;
        }
        MatchPreviewService.sendMatchPreview(pair[0], pair[1], event.getHook());
    }

    /** "Preclude Match" button — permanently exclude a pair from future matching. */
    private void handlePrecludeMatchButton(ButtonInteractionEvent event, String buttonId) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can preclude a match.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();
        String[] pair = parseUserPair(buttonId, "preclude_match_");
        if (pair == null) {
            event.getHook().sendMessage("❌ Malformed button ID.").queue();
            return;
        }
        String uid1 = pair[0], uid2 = pair[1];
        CompatibilityEngine.addPrecludedPair(uid1, uid2);

        String name1 = uid1, name2 = uid2;
        AppState p1 = ProfileRepository.load(uid1);
        AppState p2 = ProfileRepository.load(uid2);
        if (p1 != null && p1.name != null) name1 = p1.name;
        if (p2 != null && p2.name != null) name2 = p2.name;

        event.getHook().sendMessage(
            "✅ **" + name1 + "** and **" + name2 + "** have been precluded from future matches."
        ).queue();
    }

    /** "Continue" button on a match preview — actually create the manual match thread. */
    private void handleMatchConfirmButton(ButtonInteractionEvent event, String buttonId) {
        if (!Roles.isMatchmakerOrAdmin(event.getMember())) {
            event.reply("❌ Only matchmakers can confirm a match.").setEphemeral(true).queue();
            return;
        }

        String[] pair = parseUserPair(buttonId, "match_confirm_");
        if (pair == null) { event.reply("❌ Malformed button ID.").setEphemeral(true).queue(); return; }
        String uid1 = pair[0], uid2 = pair[1];

        event.deferEdit().queue();

        AppState p1 = ProfileRepository.load(uid1);
        AppState p2 = ProfileRepository.load(uid2);

        if (p1 == null || p2 == null) {
            event.getHook().sendMessage("❌ Could not reload profiles to execute the match.").queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.getHook().sendMessage("❌ Match must be confirmed from within a server.").queue();
            return;
        }

        String name1 = p1.name != null ? p1.name : uid1;
        String name2 = p2.name != null ? p2.name : uid2;

        EmbedBuilder confirmed = new EmbedBuilder()
            .setTitle("✅ Match Initiated: " + name1 + " & " + name2)
            .setDescription("A match thread is being created for <@" + uid1 + "> and <@" + uid2 + ">.")
            .setColor(0x57F287)
            .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(confirmed.build()).setComponents().queue();

        MatchThreadService.createMatchThread(guild, uid1, !p1.sex, name1, p1, uid2, !p2.sex, name2, p2, true);
    }

    /** "Cancel" button on a match preview. */
    private void handleMatchCancelButton(ButtonInteractionEvent event, String buttonId) {
        event.deferEdit().queue();

        String rest = buttonId.substring("match_cancel_".length());
        int sep = rest.indexOf('_');
        String uid1 = sep >= 0 ? rest.substring(0, sep) : rest;
        String uid2 = sep >= 0 ? rest.substring(sep + 1) : "";

        EmbedBuilder cancelled = new EmbedBuilder()
            .setTitle("Match Cancelled")
            .setDescription("The proposed match between <@" + uid1 + "> and <@" + uid2 + "> was cancelled.")
            .setColor(0x888888)
            .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(cancelled.build()).setComponents().queue();
    }

    /** "Give Feedback" DM button — open the feedback modal. */
    private void handleFeedbackButton(ButtonInteractionEvent event, String buttonId) {
        String[] pair = parseUserPair(buttonId, "qm_feedback_");
        if (pair == null) { event.reply("❌ Malformed button ID.").setEphemeral(true).queue(); return; }

        TextInput feedbackInput = TextInput
            .create("feedback_text", "How did the match go?", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Share your experience, suggestions, or any concerns...")
            .setMinLength(10)
            .setMaxLength(1000)
            .setRequired(true)
            .build();

        event.replyModal(
            Modal.create("qm_modal_feedback_" + pair[0] + "_" + pair[1], "Match Feedback")
                .addActionRow(feedbackInput)
                .build()
        ).queue();
    }

    /** "Report User Behavior" DM button — open the report modal. */
    private void handleReportButton(ButtonInteractionEvent event, String buttonId) {
        String[] pair = parseUserPair(buttonId, "qm_report_");
        if (pair == null) { event.reply("❌ Malformed button ID.").setEphemeral(true).queue(); return; }

        TextInput reasonInput = TextInput
            .create("report_reason", "What are you reporting?", TextInputStyle.SHORT)
            .setPlaceholder("e.g. Ghosting, rude behavior, inappropriate content, other...")
            .setMaxLength(100)
            .setRequired(true)
            .build();

        TextInput detailsInput = TextInput
            .create("report_details", "Additional details", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Provide any additional context or details...")
            .setMaxLength(1000)
            .setRequired(false)
            .build();

        event.replyModal(
            Modal.create("qm_modal_report_" + pair[0] + "_" + pair[1], "Report")
                .addActionRow(reasonInput)
                .addActionRow(detailsInput)
                .build()
        ).queue();
    }
}
