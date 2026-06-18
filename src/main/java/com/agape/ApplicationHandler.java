package com.agape;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * The DM application questionnaire — a JDA listener that walks applicants
 * through the 15-step profile interview entirely in direct messages.
 *
 * State machine: each user's progress lives in {@link AppState#currentStep}
 * (an {@link AppStep}), held in the in-memory {@code activeApplications} map
 * and mirrored to user_content/in_progress/ after every answer so an
 * application survives bot restarts (see {@link #recoverInProgressApplications}).
 *
 * On final submission the profile is checked by {@link AutoModerator}; clean
 * submissions are saved via {@link ProfileRepository} and posted for review
 * by {@link ApplicationReview}. This class also owns the DM buttons for the
 * re-apply flow, per-section edits, the design customization prompt, and
 * quickmatch enrollment.
 */
public class ApplicationHandler extends ListenerAdapter {

    // Thread pool for scheduling delayed tasks (like the fake human auto-rejections)
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // This HashMap acts as the bot's short-term memory. Key = User ID, Value = Their AppState
    private static final Map<String, AppState> activeApplications = new HashMap<>();

    // ─── In-progress persistence ───────────────────────────────────────────────

    private static final String IN_PROGRESS_DIR = "user_content/in_progress/";

    private static void saveInProgress(String userId, AppState state) {
        new File(IN_PROGRESS_DIR).mkdirs();
        try (FileWriter w = new FileWriter(new File(IN_PROGRESS_DIR + userId + ".json"))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(state, w);
        } catch (IOException e) {
            System.err.println("ApplicationHandler: Failed to save in-progress state for " + userId + ": " + e.getMessage());
        }
    }

    private static void deleteInProgress(String userId) {
        File f = new File(IN_PROGRESS_DIR + userId + ".json");
        if (f.exists()) f.delete();
    }

    /**
     * Runs face detection on a freshly uploaded photo and records the resulting
     * focal point on the profile so the card renderer crops toward the face.
     * Best-effort: if no face is found (or detection fails) the focus is left at
     * its centered default. Detection is CPU-bound, so callers should invoke
     * this off the JDA event loop.
     */
    private static void applyFaceFocus(AppState state) {
        float[] focus = FaceDetector.computeFocus(state.photoPath);
        if (focus != null) {
            state.photoFocusX = focus[0];
            state.photoFocusY = focus[1];
        } else {
            // No detectable face (or a placeholder/URL): fall back to a centered crop.
            state.photoFocusX = 0.5f;
            state.photoFocusY = 0.5f;
        }
    }

    /**
     * On bot startup, restores any applications that were mid-flight when the bot last shut down.
     * Loads each in-progress state back into activeApplications and sends a DM notifying the user.
     */
    public static void recoverInProgressApplications(net.dv8tion.jda.api.JDA jda) {
        File dir = new File(IN_PROGRESS_DIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return;

        int recovered = 0;
        for (File f : files) {
            String userId = f.getName().replace(".json", "");
            try {
                AppState state = new Gson().fromJson(new java.io.FileReader(f), AppState.class);
                if (state == null || state.currentStep == null || state.currentStep == AppStep.COMPLETED) {
                    f.delete();
                    continue;
                }
                activeApplications.put(userId, state);
                recovered++;

                jda.retrieveUserById(userId).queue(user -> {
                    user.openPrivateChannel().queue(dm -> {
                        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                            .setTitle("🔄 System Restarted")
                            .setColor(0xFF6699)
                            .setDescription(
                                "We sincerely apologize for the inconvenience — the Agape Matchmaking bot was temporarily restarted.\n\n"
                                + "**Your information is safe.** Your application progress has been fully preserved and you can continue right where you left off.\n\n"
                                + "Simply reply to this DM with your next answer to pick back up.")
                            .setFooter("Agape Matchmaking • We appreciate your patience!");
                        dm.sendMessageEmbeds(embed.build()).queue(
                            s -> System.out.println("ApplicationHandler: Sent reboot notice to " + userId),
                            e -> System.err.println("ApplicationHandler: Could not DM reboot notice to " + userId + ": " + e.getMessage())
                        );
                    }, e -> System.err.println("ApplicationHandler: Could not open DM for reboot notice to " + userId));
                }, e -> System.err.println("ApplicationHandler: Could not retrieve user " + userId + " for reboot notice"));

            } catch (Exception e) {
                System.err.println("ApplicationHandler: Failed to recover in-progress state for " + userId + ": " + e.getMessage());
            }
        }
        if (recovered > 0) {
            System.out.println("ApplicationHandler: Recovered " + recovered + " in-progress application(s).");
        }
    }

    /**
     * Starts a new application for a user and sends the very first question.
     */
    public static void startApplication(User user, SlashCommandInteractionEvent event) {
        // Create a new blank state for this user and save it in memory
        AppState newState = new AppState();
        newState.username = user.getName(); // Capture their handle immediately
        newState.guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        activeApplications.put(user.getId(), newState);
        deleteInProgress(user.getId()); // clear any old recovery file — fresh start

        System.out.println("Started application for user: " + user.getName() + " (ID: " + user.getId() + ")");

        // Open a DM channel and send the very first question to kick off the chain!
        user.openPrivateChannel().queue(channel -> {

            // Send the message, providing both success and failure callbacks directly to queue()
            channel.sendMessage(LanguageManager.getWelcomeMessage()).queue(
                success -> {
                    // Only let them know in the server if the DM physically arrived
                    event.getHook().sendMessage("✅ I've sent you a DM to begin the application process!").queue();
                },
                error -> {
                    System.err.println("❌ Failed to send DM to user: " + user.getName() + " (ID: " + user.getId() + "). They might have DMs disabled.");
                    event.getHook().sendMessage("❌ I couldn't send you a DM. Please make sure your privacy settings allow direct messages from server members and try again.").queue();
                    // Clean up the application state since we can't proceed without DM access
                    activeApplications.remove(user.getId());
                    deleteInProgress(user.getId());
                }
            );

        }, error -> {
            // This catches if Discord blocks us from even opening the channel
            System.err.println("❌ Failed to open DM channel for user: " + user.getName() + " (ID: " + user.getId() + ")");
            event.getHook().sendMessage("❌ I couldn't open a DM with you. Please make sure your DMs are open and try again.").queue();
            activeApplications.remove(user.getId());
            deleteInProgress(user.getId());
        });
    }

    /**
     * Starts a fresh application from a DM button context (no slash-command event required).
     * Used when a user chooses "Delete and Continue" during re-apply.
     */
    public static void startApplicationFromDM(User user, String guildId) {
        AppState newState = new AppState();
        newState.username = user.getName();
        newState.guildId = guildId;
        activeApplications.put(user.getId(), newState);
        deleteInProgress(user.getId()); // clear any old recovery file — fresh start
        System.out.println("Started re-application for user: " + user.getName() + " (ID: " + user.getId() + ")");
        user.openPrivateChannel().queue(
            channel -> channel.sendMessage(LanguageManager.getWelcomeMessage()).queue(
                s -> {},
                e -> {
                    System.err.println("Failed to send welcome to " + user.getName() + ": " + e.getMessage());
                    activeApplications.remove(user.getId());
                    deleteInProgress(user.getId());
                }
            ),
            e -> {
                System.err.println("Failed to open DM for " + user.getName() + ": " + e.getMessage());
                activeApplications.remove(user.getId());
                deleteInProgress(user.getId());
            }
        );
    }

    /**
     * Sends the customization prompt with interactive buttons
     */
    private void sendCustomizationPromptWithButtons(MessageReceivedEvent event, String language) {
        String title = LanguageManager.getCustomizationTitle(language);
        String description = LanguageManager.getCustomizationDescription(language);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(0xFF6699);
        
        // Create buttons for the three actions
        Button customizeBtn = Button.primary("customize_design", "🎨 Customize Design");
        Button editBtn = Button.secondary("edit_answers", "✏️ Edit Answers");
        Button submitBtn = Button.success("submit_application", "✅ Submit");
        
        ActionRow actionRow = ActionRow.of(customizeBtn, editBtn, submitBtn);
        
        event.getChannel().sendMessageEmbeds(embed.build())
                .setComponents(actionRow)
                .queue();
    }

    /**
     * Loads design codes from design_codes.json and resolves a user's design code to actual file paths
     */
    private static class DesignPaths {
        public String backgroundPath;
        public String framePath;
        
        public DesignPaths(String bg, String frame) {
            this.backgroundPath = bg;
            this.framePath = frame;
        }
    }
    
    private DesignPaths resolveDesignCode(String designCode) {
        String bgPath = "assets/backgrounds/default.png";
        String framePath = "assets/frames/default.png";
        
        if (designCode == null || designCode.trim().isEmpty()) {
            return new DesignPaths(bgPath, framePath);
        }
        
        try {
            // Load design_codes.json
            File designFile = new File("assets/design_codes.json");
            if (!designFile.exists()) {
                System.err.println("⚠️ design_codes.json not found, using defaults");
                return new DesignPaths(bgPath, framePath);
            }
            
            Gson gson = new Gson();
            @SuppressWarnings("unchecked")
            java.util.Map<String, java.util.Map<String, String>> designData = 
                gson.fromJson(new java.io.FileReader(designFile), java.util.Map.class);
            
            java.util.Map<String, String> backgrounds = designData.get("backgrounds");
            java.util.Map<String, String> frames = designData.get("frames");
            
            // Split design code in case user provided multiple (e.g., "BLF_PST")
            String[] codes = designCode.trim().toUpperCase().split("[_\\-]");
            
            for (String code : codes) {
                code = code.trim();
                if (code.isEmpty()) continue;
                
                // Check if this code matches a background
                for (String bgFile : backgrounds.keySet()) {
                    if (backgrounds.get(bgFile).equals(code)) {
                        bgPath = "assets/backgrounds/" + bgFile;
                        System.out.println("✅ Matched background code: " + code + " -> " + bgFile);
                        break;
                    }
                }
                
                // Check if this code matches a frame
                for (String frameFile : frames.keySet()) {
                    if (frames.get(frameFile).equals(code)) {
                        framePath = "assets/frames/" + frameFile;
                        System.out.println("✅ Matched frame code: " + code + " -> " + frameFile);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error loading design codes: " + e.getMessage());
            e.printStackTrace();
        }
        
        return new DesignPaths(bgPath, framePath);
    }

    private void generateProfileCard(AppState state, MessageReceivedEvent event) {
        event.getChannel().sendMessage("⏳ " + LanguageManager.getGeneratingMessage(state.language)).queue(loadingMsg -> {
            new Thread(() -> {
                try {
                    User user = event.getAuthor();
                    // Ensure the image path is a valid URI if it's a local file
                    String pfpUri = state.photoPath.startsWith("http") ? state.photoPath : new File(state.photoPath).toURI().toURL().toString();

                    boolean hasStr = state.strengths != null && !state.strengths.isEmpty();
                    boolean hasWeak = state.weaknesses != null && !state.weaknesses.isEmpty();
                    String strAndWeak;
                    if (hasStr && hasWeak) {
                        strAndWeak = "{autoscale:3}" + state.strengths + "\n" + state.weaknesses + "{/autoscale}\n\n";
                    } else {
                        strAndWeak = "\n";
                    }

                    // Construct the beautiful rich text using their actual answers!
                    String text = "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}" + state.name + "{/}*\n" +
                                    "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:6.0}{f:Arial Rounded MT Bold}@" + user.getName() + "{/}*\n\n" +
                                    AgeUtils.calculateAge(state.birthday) + " | " + AgeUtils.birthYear(state.birthday) + "\n" +
                                    (state.sex ? "Female" : "Male") + "\n" +
                                    state.sect + "\n" +
                                    "{autoscale:2}" + state.physicalDescription + "{/autoscale}" + "\n\n" +
                                    "{autoscale:3}" + normalizeHobbies(state.hobbies) + "{/autoscale}" + "\n\n" +
                                    strAndWeak +
                                    "{autoscale:4}{img:green_flag.png} PARTNER: " + state.lookFor.replace("\n", ", ") + "\n" +
                                    "{img:red_flag.png} PARTNER: " + state.dealBreakers.replace("\n", ", ") + "{/autoscale}";
    
                    // Resolve design code to actual file paths
                    DesignPaths designPaths = resolveDesignCode(state.designCode);
                    String fontPath = "assets/fonts/VAG Rounded Next Shine Regular.ttf";
    
                    File generatedImage = ImageGenerator.generateForUser(designPaths.backgroundPath, pfpUri, designPaths.framePath, fontPath, text, user.getId(), state.photoFocusX, state.photoFocusY);
    
                    if (generatedImage != null && generatedImage.exists()) {
                        event.getChannel().sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImage)).queue(success -> {
                            generatedImage.delete();
                            sendCustomizationPromptWithButtons(event, state.language);
                        }, error -> {
                            System.err.println("Failed to send preview image: " + error.getMessage());
                            generatedImage.delete();
                            sendCustomizationPromptWithButtons(event, state.language);
                        });
                    } else {
                        event.getChannel().sendMessage("⚠️ Failed to generate preview.").queue();
                        sendCustomizationPromptWithButtons(event, state.language);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    event.getChannel().sendMessage("⚠️ Error generating preview.").queue();
                    sendCustomizationPromptWithButtons(event, state.language);
                }
            }).start();
        });
    }

    /** Builds the rich-text card string from a completed AppState. Used by both the signup preview and the match thread. */
    public static String buildCardText(AppState state) {
        boolean hasStrengths = state.strengths != null && !state.strengths.isEmpty();
        boolean hasWeaknesses = state.weaknesses != null && !state.weaknesses.isEmpty();
        String strAndWeak = "";
        if (hasStrengths) strAndWeak += state.strengths + "\n";
        if (hasWeaknesses) strAndWeak += state.weaknesses + "\n";

        String username = state.username != null ? state.username : "unknown";
        String lookFor = (state.lookFor != null && !state.lookFor.isEmpty())
            ? state.lookFor.replace("\n", ", ") : null;
        String location = (state.country != null && !state.country.isEmpty())
            ? state.country : null;
        String dealBreakers = state.dealBreakers != null ? state.dealBreakers.replace("\n", ", ") : "";

        // Strengths + Weaknesses are linked: shrink both if either is too long
        String strAndWeakSection = strAndWeak.isEmpty()
            ? "\n"
            : "{autoscale:3}" + strAndWeak.trim() + "{/autoscale}\n\n";

        // Looking For + Deal Breakers are linked: shrink both if either is too long.
        // Limit is 4 (2 lines per entry) since each flag is its own natural paragraph.
        String flagSection = lookFor != null
            ? "{autoscale:4}{img:green_flag.png} PARTNER: " + lookFor + "\n"
                + "{img:red_flag.png} PARTNER: " + dealBreakers + "{/autoscale}"
            : "{autoscale:2}{img:red_flag.png} PARTNER: " + dealBreakers + "{/autoscale}";

        return "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}" + state.name + "{/}*\n"
            + "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:6.0}{f:Arial Rounded MT Bold}@" + username + "{/}*\n\n"
            + AgeUtils.calculateAge(state.birthday) + " | " + AgeUtils.birthYear(state.birthday) + "\n"
            + (state.sex ? "Female" : "Male") + "\n"
            + state.sect + "\n"
            + (location != null ? location + "\n" : "")
            + "{autoscale:2}" + state.physicalDescription + "{/autoscale}" + "\n\n"
            + "{autoscale:3}" + normalizeHobbies(state.hobbies) + "{/autoscale}" + "\n\n"
            + strAndWeakSection
            + flagSection;
    }

    /**
     * Replaces newline-delimited lists with comma-delimited ones.
     * Strips trailing commas from each line and skips blank segments so
     * "item1,\nitem2\n\nitem3" becomes "item1, item2, item3".
     */
    static String normalizeLineBreaks(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.contains("\n") && !trimmed.contains("\r")) return trimmed;
        String[] parts = trimmed.split("[\\r\\n]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = part.replaceAll(",$", "").trim();
            if (!p.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
        }
        return sb.toString();
    }

    /** Strips common user-written hobby preambles and prepends the canonical "HOBBIES: " label. */
    private static String normalizeHobbies(String hobbies) {
        if (hobbies == null || hobbies.isEmpty()) return "HOBBIES: ";
        // Handles variants like:
        //   "Hobbies: " / "Hobbies include " / "My hobbies are " / "My hobbies consist of "
        //   "Some of my hobbies include " / "My hobbies and interests: "
        //   "My interests include " / "Interests: "
        //   "I enjoy " / "I like " / "I love "
        String cleaned = hobbies.replaceFirst(
            "(?i)^(?:" +
            "(?:(?:some\\s+of\\s+)?my\\s+|some\\s+of\\s+)?hobbies(?:\\s+and\\s+interests)?(?:\\s+(?:include|are|consist\\s+of)|\\s*:)?|" +
            "(?:my\\s+)?interests(?:\\s+(?:include|are)|\\s*:)?|" +
            "i\\s+(?:enjoy|like|love)" +
            ")\\s*",
            ""
        );
        if (!cleaned.isEmpty()) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        return "HOBBIES: " + cleaned;
    }

    /**
     * Suggests a design code based on the applicant's hobbies and self-descriptions.
     * Only called when the user has not manually chosen a code.
     *
     * Men:   Star Wars → FOD-SWR | Artsy → STN-PST | Gaming → BTW-PST / REA-WOV / MCJ-CMA | Adventure → MSR-CMA
     * Women: Calm/peaceful trait → SKA-SPR / SPC-SPK, then same hobby checks as men.
     * Fallback: HEG-SPK
     */
    public static String suggestDesignCode(AppState state) {
        String hobbies  = state.hobbies  != null ? state.hobbies.toLowerCase()  : "";
        String strengths = state.strengths != null ? state.strengths.toLowerCase() : "";

        boolean isFemale = state.sex;

        // Female calm/peaceful trait check (takes priority for women)
        if (isFemale && containsAny(strengths, "calm", "peaceful", "serene", "tranquil", "gentle", "relaxed", "laid-back", "easygoing", "easy-going", "soft-spoken")) {
            String[] opts = {"SKA-SPR", "SPC-SPK"};
            return opts[new Random().nextInt(opts.length)];
        }

        // Hobby-based checks (shared by both sexes)
        if (containsAny(hobbies, "star wars")) return "FOD-SWR";

        if (containsAny(hobbies, "paint", "draw", "sketch", "craft", "sculpt", "canvas", "watercolor", "artsy", "digital art")) return "STN-PST";

        if (containsAny(hobbies, "gaming", "video game", "gamer", "game", "minecraft", "playstation", "xbox", "nintendo", "pc games")) {
            String[] opts = {"BTW-PST", "REA-WOV", "MCJ-CMA"};
            return opts[new Random().nextInt(opts.length)];
        }

        if (containsAny(hobbies, "hike", "hiking", "adventure", "trail", "mountain", "camping", "outdoor", "backpack", "nature", "travel", "exploration", "walks", "walking")) return "MSR-CMA";

        return "HEG-SPK";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Centralized method to handle final submission, auto-mod checks, and JSON saving.
     */
    private void processFinalSubmission(AppState state, String userId, JDA jda, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        state.currentStep = AppStep.COMPLETED;
        state.submittedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (state.designCode == null || state.designCode.isEmpty()) {
            state.designCode = suggestDesignCode(state);
        }

        // Backfill the Brother/Sister role from the registered sex so the user can self-tag
        if (state.guildId != null) {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(state.guildId);
            if (guild != null) Roles.ensureGenderRole(guild, userId, state.sex);
        }
        activeApplications.remove(userId);
        deleteInProgress(userId);
        
        channel.sendMessage("✅ **" + LanguageManager.getCompletionMessage(state.language) + "**").queue();

        boolean sendQMPrompt = !state.quickmatchPromptSent;
        if (sendQMPrompt) state.quickmatchPromptSent = true;

        AutoModerator.AutoModResult autoMod = AutoModerator.check(state);

        if (autoMod != null) {
            // --- AUTO REJECTION TRIGGERED ---
            state.status = "CHANGES_REQUESTED";
            saveProfileJson(state, userId);
            cleanUpSrvJson(userId);

            // Calculate a random delay between 60 and 300 seconds (1 to 5 minutes)
            int delaySeconds = 60 + new Random().nextInt(241);

            scheduler.schedule(() -> {
                jda.retrieveUserById(userId).queue(user -> {
                    user.openPrivateChannel().queue(dm -> {
                        Button editBtn = Button.primary("user_edit_app_" + autoMod.sectionNum, "✏️ Edit Application");
                        Button deleteBtn = Button.danger("user_delete_app", "🗑️ Delete Application");

                        dm.sendMessage("⚠️ A matchmaker has requested changes to your application. Please review the feedback and update the requested section.\n\n**Matchmaker Note:** " + autoMod.reason + "\n**Section to Edit:** #" + autoMod.sectionNum + " (" + ApplicationReview.sectionName(autoMod.sectionNum) + ")")
                            .setComponents(ActionRow.of(editBtn, deleteBtn))
                            .queue();
                    });
                });
            }, delaySeconds, TimeUnit.SECONDS);

            if (sendQMPrompt) sendQuickmatchEnrollmentPrompt(userId, jda);
            System.out.println("🤖 Auto-Mod intercepted application for " + state.name + ". Ghost request scheduled in " + delaySeconds + " seconds.");
            return; // EXIT HERE! Do not post to the matchmaker channel!
        }

        // --- NORMAL SUBMISSION FLOW ---
        state.status = "PENDING";
        saveProfileJson(state, userId);
        cleanUpSrvJson(userId);
        ApplicationReview.postApplicationToChannel(state, userId, jda);
        if (sendQMPrompt) sendQuickmatchEnrollmentPrompt(userId, jda);
    }

    // Helper to save JSON
    private void saveProfileJson(AppState state, String userId) {
        ProfileRepository.save(userId, state);
    }

    // Helper to clean srv
    private void cleanUpSrvJson(String userId) {
        File srvFile = new File("user_content/srv/" + userId + ".json");
        if (srvFile.exists()) srvFile.delete();
    }

    private void completeApplication(AppState state, String userId, MessageReceivedEvent event) {
        processFinalSubmission(state, userId, event.getJDA(), event.getChannel());
    }

    /**
     * Sends the quickmatch enrollment prompt after successful profile submission
     */
    private void sendQuickmatchEnrollmentPrompt(String userId, JDA jda) {
        jda.retrieveUserById(userId).queue(user -> {
            user.openPrivateChannel().queue(dm -> {
                // Load the user's profile to get their language preference
                try {
                    String language = "english";
                    AppState state = ProfileRepository.load(userId);
                    if (state != null && state.language != null) {
                        language = state.language;
                    }
                    
                    Button enrollBtn = Button.success("quickmatch_enroll_" + userId, "✅ Enroll in Quickmatch");
                    Button passBtn = Button.secondary("quickmatch_pass_" + userId, "⏭️ Pass for Now");
                    
                    EmbedBuilder embed = new EmbedBuilder()
                        .setColor(0x9966FF)
                        .setTitle(LanguageManager.getQuickmatchTitle(language))
                        .setDescription(LanguageManager.getQuickmatchDescription(language))
                        .addField(LanguageManager.getQuickmatchField1Title(language), LanguageManager.getQuickmatchField1Value(language), false)
                        .addField(LanguageManager.getQuickmatchField2Title(language), LanguageManager.getQuickmatchField2Value(language), false)
                        .setFooter(LanguageManager.getQuickmatchFooter(language));
                    
                    dm.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(enrollBtn, passBtn))
                        .queue();
                } catch (Exception e) {
                    System.err.println("Error sending quickmatch prompt to user " + userId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
    }

    /**
     * Overload for completing application from button click
     */
    private void completeApplicationFromButton(AppState state, String userId, ButtonInteractionEvent event) {
        processFinalSubmission(state, userId, event.getJDA(), event.getChannel());
    }

    /**
     * Displays all user answers in a numbered list so they can select which to edit
     */
    private String displayAnswersForEditing(AppState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("✏️ **Which answer would you like to edit?**\n\n");
        
        sb.append("` 1)` Name: ").append(state.name).append("\n");
        sb.append("` 2)` Birthday: ").append(state.birthday != null ? state.birthday + " (age " + AgeUtils.calculateAge(state.birthday) + ")" : "N/A").append("\n");
        sb.append("` 3)` Location: ").append(state.country).append("\n");
        sb.append("` 4)` Sex: ").append(state.sex ? "Female" : "Male").append("\n");
        sb.append("` 5)` Denomination: ").append(state.sect).append("\n");
        sb.append("` 6)` Target Age Range: ").append(state.targetAge).append("\n");
        sb.append("` 7)` Target Denomination: ").append(state.targetSect).append("\n");
        sb.append("` 8)` Physical Description: ").append(state.physicalDescription).append("\n");
        sb.append("` 9)` Hobbies: ").append(state.hobbies).append("\n");
        sb.append("`10)` Strengths: ").append(state.strengths).append("\n");
        sb.append("`11)` Weaknesses: ").append(state.weaknesses).append("\n");
        sb.append("`12)` What They're Looking For: ").append(state.lookFor).append("\n");
        sb.append("`13)` Deal Breakers: ").append(state.dealBreakers).append("\n");
        sb.append("\n-# Reply with the number of what you'd like to edit (e.g., **1** to edit name).");
        
        return sb.toString();
    }

    /**
     * Gets the question prompt for a specific field being edited
     */
    private String getEditQuestionPrompt(int fieldNumber, String language) {
        // Map numbers to the questions array indices (in new embed order)
        int[][] fieldMap = {
            {0},    // 1: Name (index 0 in questions array)
            {2},    // 2: Age (index 2)
            {1},    // 3: Country (index 1)
            {3},    // 4: Sex (index 3)
            {4},    // 5: Denomination (index 4)
            {10},   // 6: Target Age (index 10)
            {11},   // 7: Target Denomination (index 11)
            {5},    // 8: Physical Description (index 5)
            {6},    // 9: Hobbies (index 6)
            {7},    // 10: Strengths (index 7)
            {8},    // 11: Weaknesses (index 8)
            {12},   // 12: What You Look For (index 12)
            {13},   // 13: Deal Breakers (index 13)
            {9},    // 14: Photo (index 9)
        };

        if (fieldNumber < 1 || fieldNumber > fieldMap.length) {
            return "Invalid selection. Please reply with a number between 1 and 14.";
        }
        
        int questionIndex = fieldMap[fieldNumber - 1][0];
        return "✏️ **Edit this field:** " + LanguageManager.getQuestions(language)[questionIndex];
    }

    /**
     * Maps the selected field number to the appropriate AppStep for editing
     */
    private AppStep getEditStepFromFieldNumber(int fieldNumber) {
        switch (fieldNumber) {
            case 1: return AppStep.NAME;
            case 2: return AppStep.AGE;
            case 3: return AppStep.COUNTRY;
            case 4: return AppStep.SEX;
            case 5: return AppStep.SECT;
            case 6: return AppStep.TARGET_AGE;
            case 7: return AppStep.TARGET_SECT;
            case 8: return AppStep.PHYSICAL;
            case 9: return AppStep.HOBBIES;
            case 10: return AppStep.STRENGTHS;
            case 11: return AppStep.WEAKNESSES;
            case 12: return AppStep.LOOK_FOR;
            case 13: return AppStep.DEAL_BREAKERS;
            case 14: return AppStep.PHOTO;
            default: return null;
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 1. Ignore messages from bots (including ourselves)
        if (event.getAuthor().isBot()) return;

        // 2. We only care about Direct Messages for the application
        if (event.isFromGuild()) return;

        User user = event.getAuthor();
        String userId = user.getId();

        // 3. Check if this user actually has an active application going on
        if (!activeApplications.containsKey(userId)) return;

        AppState state = activeApplications.get(userId);
        
        // 4. Validate guild context if the user has a stored guild ID
        if (state.guildId != null && !EnvironmentManager.isGuildAllowed(state.guildId)) {
            event.getChannel().sendMessage("❌ Your application was initiated in a server that is not available in the current environment. Please try again in the correct server.").queue();
            System.out.println("[SECURITY] Rejected DM response from user " + userId + " with mismatched guild " + state.guildId + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            activeApplications.remove(userId);
            deleteInProgress(userId);
            return;
        }
        
        String messageContent = event.getMessage().getContentRaw().trim();
        String[] currentQuestions = LanguageManager.getQuestions(state.language);

        // Process the answer based on what step they are currently on
        switch (state.currentStep) {
            case LANGUAGE:
                state.language = LanguageManager.normalizeLanguageName(messageContent);
                if (!LanguageManager.isSupportedLanguage(state.language) || state.language.equalsIgnoreCase("english") || state.language.equalsIgnoreCase("en") || state.language.equalsIgnoreCase("inglés") || state.language.equalsIgnoreCase("anglais") || state.language.equalsIgnoreCase("inglês") || state.language.equalsIgnoreCase("engels")) {
                    state.currentStep = AppStep.NAME;
                    event.getChannel().sendMessage("**(2/15)** " + LanguageManager.getQuestions(state.language)[0]).queue();
                } else {
                    state.currentStep = AppStep.APPLICATION_LANGUAGE;
                    String prepend = LanguageManager.getLanguageSwitchPrompt(state.language);
                    event.getChannel().sendMessage(prepend + "\n-# Would you like to continue the application in " + LanguageManager.toTitleCase(state.language) + "?").queue();
                }
                break;
            
            case APPLICATION_LANGUAGE:
                if (LanguageManager.isYes(messageContent)) {
                    String prepend = LanguageManager.getLanguageConfirmation(state.language);
                    event.getChannel().sendMessage(prepend + currentQuestions[0]).queue();
                    state.currentStep = AppStep.NAME;
                } else if (LanguageManager.isNo(messageContent)) {
                    state.language = "English";
                    event.getChannel().sendMessage("No problem! We'll continue in English.\n\n**(2/15)** " + LanguageManager.getQuestions("English")[0]).queue();
                    state.currentStep = AppStep.NAME;
                } else {
                    event.getChannel().sendMessage("⚠️ " + LanguageManager.getYesNoWarning(state.language) + "\n-# Please answer with yes or no.").queue();
                }
                break;

            case NAME:
                state.name = messageContent;
                state.currentStep = AppStep.COUNTRY;
                event.getChannel().sendMessage("**(3/15)** " + currentQuestions[1]).queue();
                break;

            case COUNTRY:
                state.country = messageContent.equalsIgnoreCase("skip") ? "" : messageContent;
                state.currentStep = AppStep.AGE;
                event.getChannel().sendMessage("**(4/15)** " + currentQuestions[2]).queue();
                break;

            case AGE:
                String parsedBirthday = AgeUtils.parseBirthday(messageContent);
                if (parsedBirthday == null) {
                    event.getChannel().sendMessage("⚠️ " + LanguageManager.getInvalidAgeWarning(state.language)).queue();
                    return;
                }
                if (AgeUtils.calculateAge(parsedBirthday) < 18) {
                    event.getChannel().sendMessage("❌ " + LanguageManager.getUnderageWarning(state.language)).queue();
                    activeApplications.remove(userId);
                    deleteInProgress(userId);
                    return;
                }
                state.birthday = parsedBirthday;
                state.currentStep = AppStep.SEX;
                event.getChannel().sendMessage("**(5/15)** " + currentQuestions[3]).queue();
                break;

            case SEX:
                state.sex = LanguageManager.isFemale(messageContent);
                state.currentStep = AppStep.SECT;
                event.getChannel().sendMessage("**(6/15)** " + currentQuestions[4]).queue();
                break;

            case SECT:
                state.sect = DenominationCompatibility.normalizeDenomination(messageContent);
                // if (!state.sect.equalsIgnoreCase(messageContent.trim())) {
                //     event.getChannel().sendMessage("-# ✅ Matched your denomination as **" + state.sect + "**").queue();
                // }
                state.currentStep = AppStep.PHYSICAL;

                event.getChannel().sendMessage("**(7/15)** " + currentQuestions[5]).queue();
                break;

            case PHYSICAL:
                state.physicalDescription = messageContent;
                state.currentStep = AppStep.HOBBIES;
                event.getChannel().sendMessage("**(8/15)** " + currentQuestions[6]).queue();
                break;

            case HOBBIES:
                state.hobbies = normalizeLineBreaks(messageContent);
                state.currentStep = AppStep.STRENGTHS;
                event.getChannel().sendMessage("**(9/15)** " + currentQuestions[7]).queue();
                break;

            case STRENGTHS:
                state.strengths = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                state.currentStep = AppStep.WEAKNESSES;
                event.getChannel().sendMessage("**(10/15)** " + currentQuestions[8]).queue();
                break;

            case WEAKNESSES:
                state.weaknesses = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                state.currentStep = AppStep.PHOTO;
                event.getChannel().sendMessage("**(11/15)** " + currentQuestions[9]).queue();
                break;

            case PHOTO:
                // Check if they opted to skip
                if (messageContent.equalsIgnoreCase("skip") || messageContent.equalsIgnoreCase("no")) {
                    state.photoPath = state.sex ? "assets/female.png" : "assets/male.png";
                    advanceToTargetAge(state, event);
                } 
                // Check if they actually attached an image
                else if (!event.getMessage().getAttachments().isEmpty()) {
                    Message.Attachment attachment = event.getMessage().getAttachments().get(0);
                    
                    // Verify it's an image
                    if (attachment.isImage()) {
                        // Ensure the directory exists
                        File directory = new File("user_content/images/");
                        if (!directory.exists()) {
                            directory.mkdirs();
                        }

                        // Save the file as their User ID + extension (e.g., 123456.png)
                        String extension = attachment.getFileExtension();
                        File destFile = new File(directory, userId + "." + extension);
                        
                        // Download the file from Discord's servers
                        attachment.getProxy().downloadToFile(destFile).thenAccept(file -> {
                            state.photoPath = file.getAbsolutePath();
                            applyFaceFocus(state); // center the card crop on the applicant's face
                            advanceToTargetAge(state, event);
                            saveInProgress(userId, state);
                        }).exceptionally(ex -> {
                            event.getChannel().sendMessage("❌ Something went wrong saving your image. We'll use a placeholder profile picture instead.").queue();
                            state.photoPath = state.sex ? "assets/female.png" : "assets/male.png";
                            advanceToTargetAge(state, event);
                            saveInProgress(userId, state);
                            return null;
                        });
                    } else {
                        event.getChannel().sendMessage("⚠️ That attachment doesn't look like an image. Please upload a picture or type **skip**.").queue();
                    }
                } else {
                    event.getChannel().sendMessage("⚠️ Please upload an image file, or type **skip** to use your Discord avatar.").queue();
                }
                break;

            case TARGET_AGE:
                if (AgeUtils.isValidTargetAge(messageContent)) {
                    state.targetAge = messageContent;
                    state.currentStep = AppStep.TARGET_SECT;
                    // List<String> denomSuggestions = DenominationCompatibility.getCompatibleDenominations(state.sect, false);
                    // if (!denomSuggestions.isEmpty()) {
                    //     StringBuilder hint = new StringBuilder(LanguageManager.getDenominationSuggestionHint(state.language) + "\n");
                    //     for (String denom : denomSuggestions) {
                    //         hint.append("• ").append(denom).append("\n");
                    //     }
                    //     event.getChannel().sendMessage(hint.toString().trim()).queue();
                    // }
                    event.getChannel().sendMessage("**(13/15)** " + currentQuestions[11]).queue();
                } else {
                    event.getChannel().sendMessage("⚠️ " + LanguageManager.getTargetAgeValidationError(state.language)).queue();
                }
                break;

            case TARGET_SECT:
                state.targetSect = messageContent;
                state.currentStep = AppStep.LOOK_FOR;
                event.getChannel().sendMessage("**(14/15)** " + currentQuestions[12]).queue();
                break;

            case LOOK_FOR:
                state.lookFor = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                state.currentStep = AppStep.DEAL_BREAKERS;
                event.getChannel().sendMessage("**(15/15)** " + currentQuestions[13]).queue();
                break;

            case DEAL_BREAKERS:
                state.dealBreakers = normalizeLineBreaks(messageContent);
                state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                if (state.designCode == null || state.designCode.isEmpty()) {
                    state.designCode = suggestDesignCode(state);
                }
                generateProfileCard(state, event);
                break;

            case CUSTOMIZE_PROMPT:
                if (LanguageManager.isYes(messageContent)) {
                    state.currentStep = AppStep.WAITING_FOR_DESIGN_CODE;
                    
                    String encodedId = Base64.getEncoder().encodeToString(userId.getBytes());
                    String url = "https://eminich.com/apps/ccm/?id=" + encodedId;
                    event.getChannel().sendMessage(LanguageManager.getDesignCodePrompt(state.language, url)).queue();
                    
                    // Save temporary JSON to the 'srv' directory for the web app to access!
                    File srvDir = new File("user_content/srv/");
                    if (!srvDir.exists()) {
                        srvDir.mkdirs();
                    }

                    File srvFile = new File(srvDir, userId + ".json");
                    try (FileWriter writer = new FileWriter(srvFile)) {
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        gson.toJson(state, writer);
                        System.out.println("✅ Saved temporary application data for " + state.name + " to " + srvFile.getPath());
                    } catch (IOException e) {
                        System.err.println("❌ Failed to save temp profile JSON for user: " + userId);
                        e.printStackTrace();
                    }
                } else if (LanguageManager.isNo(messageContent)) {
                    completeApplication(state, userId, event);
                } else if (messageContent.equalsIgnoreCase("edit")) {
                    state.currentStep = AppStep.EDIT_WHICH_FIELD;
                    event.getChannel().sendMessage(displayAnswersForEditing(state)).queue();
                } else {
                    event.getChannel().sendMessage("⚠️ Please reply with **customize**, **edit**, or **submit**, or use the buttons above.").queue();
                }
                break;

            case EDIT_WHICH_FIELD:
                if (LanguageManager.isCancel(messageContent)) {
                    state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                    sendCustomizationPromptWithButtons(event, state.language);
                    saveInProgress(userId, state);
                    return;
                }
                try {
                    int fieldNumber = Integer.parseInt(messageContent.trim());
                    AppStep editStep = getEditStepFromFieldNumber(fieldNumber);
                    
                    if (editStep == null) {
                        event.getChannel().sendMessage("⚠️ Please reply with a number between 1 and 13.").queue();
                    } else {
                        state.currentStep = AppStep.EDITING_FIELD;
                        state.fieldBeingEdited = editStep.toString();
                        event.getChannel().sendMessage(getEditQuestionPrompt(fieldNumber, state.language)).queue();
                    }
                } catch (NumberFormatException e) {
                    event.getChannel().sendMessage("⚠️ Please reply with a number between 1 and 13.").queue();
                }
                break;

            case EDITING_FIELD:
                // Handle updating the specific field
                AppStep fieldStep = AppStep.valueOf(state.fieldBeingEdited);
                switch (fieldStep) {
                    case NAME:
                        state.name = messageContent;
                        break;
                    case COUNTRY:
                        state.country = messageContent.equalsIgnoreCase("skip") ? "" : messageContent;
                        break;
                    case AGE:
                        String editedBirthday = AgeUtils.parseBirthday(messageContent);
                        if (editedBirthday == null) {
                            event.getChannel().sendMessage("⚠️ " + LanguageManager.getInvalidAgeWarning(state.language)).queue();
                            return;
                        }
                        if (AgeUtils.calculateAge(editedBirthday) < 18) {
                            event.getChannel().sendMessage("❌ " + LanguageManager.getUnderageWarning(state.language)).queue();
                            activeApplications.remove(userId);
                            deleteInProgress(userId);
                            return;
                        }
                        state.birthday = editedBirthday;
                        break;
                    case SEX:
                        state.sex = LanguageManager.isFemale(messageContent);
                        break;
                    case SECT:
                        state.sect = DenominationCompatibility.normalizeDenomination(messageContent);
                        if (!state.sect.equalsIgnoreCase(messageContent.trim())) {
                            event.getChannel().sendMessage("-# ✅ Matched your denomination as **" + state.sect + "**").queue();
                        }
                        break;
                    case PHYSICAL:
                        state.physicalDescription = messageContent;
                        break;
                    case HOBBIES:
                        state.hobbies = normalizeLineBreaks(messageContent);
                        break;
                    case STRENGTHS:
                        state.strengths = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                        break;
                    case WEAKNESSES:
                        state.weaknesses = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                        break;
                    case TARGET_AGE:
                        if (!AgeUtils.isValidTargetAge(messageContent)) {
                            event.getChannel().sendMessage("⚠️ " + LanguageManager.getTargetAgeValidationError(state.language)).queue();
                            return;
                        }
                        state.targetAge = messageContent;
                        break;
                    case TARGET_SECT:
                        state.targetSect = messageContent;
                        break;
                    case LOOK_FOR:
                        state.lookFor = messageContent.equalsIgnoreCase("skip") ? "" : normalizeLineBreaks(messageContent);
                        break;
                    case DEAL_BREAKERS:
                        state.dealBreakers = normalizeLineBreaks(messageContent);
                        break;
                    case PHOTO:
                        if (messageContent.equalsIgnoreCase("skip")) {
                            state.photoPath = state.sex ? "assets/female.png" : "assets/male.png";
                            state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                            generateProfileCard(state, event);
                        } else if (!event.getMessage().getAttachments().isEmpty()) {
                            Message.Attachment attachment = event.getMessage().getAttachments().get(0);
                            if (attachment.isImage()) {
                                File directory = new File("user_content/images/");
                                if (!directory.exists()) directory.mkdirs();
                                String extension = attachment.getFileExtension();
                                File destFile = new File(directory, userId + "." + extension);
                                attachment.getProxy().downloadToFile(destFile).thenAccept(file -> {
                                    state.photoPath = file.getAbsolutePath();
                                    applyFaceFocus(state); // re-detect the face for the new photo
                                    state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                                    generateProfileCard(state, event);
                                    saveInProgress(userId, state);
                                }).exceptionally(ex -> {
                                    event.getChannel().sendMessage("❌ Something went wrong saving your image.").queue();
                                    return null;
                                });
                            } else {
                                event.getChannel().sendMessage("⚠️ That doesn't look like an image. Please upload a picture or type **skip**.").queue();
                            }
                        } else {
                            event.getChannel().sendMessage("⚠️ Please upload an image file, or type **skip** to use a placeholder.").queue();
                        }
                        return; // Async or re-prompt — skip the state transition below
                    default:
                        break;
                }

                // After editing, regenerate the preview image
                state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                generateProfileCard(state, event);
                break;
                
            case WAITING_FOR_DESIGN_CODE:
                if (LanguageManager.isCancel(messageContent)) {
                    state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                    sendCustomizationPromptWithButtons(event, state.language);
                } else {
                    state.designCode = messageContent;
                    state.currentStep = AppStep.CUSTOMIZE_PROMPT;
                    generateProfileCard(state, event);
                }
                break;

            case COMPLETED:
                // Application is complete, ignore further messages
                event.getChannel().sendMessage("Your application has already been submitted. Please wait for a matchmaker to review it.").queue();
                break;
        }

        // Persist state after every step so it survives a bot restart
        if (activeApplications.containsKey(userId)) {
            saveInProgress(userId, activeApplications.get(userId));
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Validate guild for this environment (if in a guild)
        if (event.getGuild() != null && !EnvironmentManager.isGuildAllowed(event.getGuild().getId())) {
            event.reply("❌ This interaction is not available in this server.").setEphemeral(true).queue();
            System.out.println("[SECURITY] Rejected button interaction from guild " + event.getGuild().getId() + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            return;
        }
        
        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();
        
        // Handle quickmatch enrollment buttons
        if (buttonId.startsWith("quickmatch_enroll_")) {
            handleQuickmatchEnrollment(userId, true, event);
            return;
        } else if (buttonId.startsWith("quickmatch_pass_")) {
            handleQuickmatchEnrollment(userId, false, event);
            return;
        }
        
        // Check if it's a matchmaker review button
        if (buttonId.startsWith("app_accept_") || buttonId.startsWith("app_request_change_") || buttonId.startsWith("app_request_photo_change_") || buttonId.startsWith("app_reject_")) {
            ApplicationReview.handleReviewButton(event);
            return;
        }

        // Check if it's a conversation reply button
        if (buttonId.startsWith("convo_reply_")) {
            ConversationRelay.handleReplyButton(event);
            return;
        }

        // Re-apply flow (existing profile found on /apply)
        if (buttonId.startsWith("reapply_")) {
            handleReapplyAction(event);
            return;
        }

        // NEW: Check if it's a user application edit/delete button
        if (buttonId.startsWith("user_edit_app_") || buttonId.equals("user_delete_app")) {
            handleUserAppAction(event);
            return;
        }
        
        // Only process if the button belongs to the customization prompt
        if (!buttonId.equals("customize_design") && !buttonId.equals("edit_answers") && !buttonId.equals("submit_application")) {
            return;
        }
        
        event.deferEdit().queue(); // Acknowledge the button click

        if (!activeApplications.containsKey(userId)) {
            event.getHook().sendMessage("❌ No active application found.").queue();
            return;
        }
        
        AppState state = activeApplications.get(userId);
        
        // Simulate the text message by triggering the appropriate action
        if (buttonId.equals("customize_design")) {
            // Trigger "yes" action
            state.currentStep = AppStep.WAITING_FOR_DESIGN_CODE;
            
            String encodedId = Base64.getEncoder().encodeToString(userId.getBytes());
            String url = "https://eminich.com/apps/ccm/?id=" + encodedId;
            event.getChannel().sendMessage(LanguageManager.getDesignCodePrompt(state.language, url)).queue();
            
            // Save temporary JSON to the 'srv' directory for the web app to access!
            File srvDir = new File("user_content/srv/");
            if (!srvDir.exists()) {
                srvDir.mkdirs();
            }

            File srvFile = new File(srvDir, userId + ".json");
            try (FileWriter writer = new FileWriter(srvFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(state, writer);
                System.out.println("✅ Saved temporary application data for " + state.name + " to " + srvFile.getPath());
            } catch (IOException e) {
                System.err.println("❌ Failed to save temp profile JSON for user: " + userId);
                e.printStackTrace();
            }
            saveInProgress(userId, state);
        } else if (buttonId.equals("edit_answers")) {
            // Trigger "edit" action
            state.currentStep = AppStep.EDIT_WHICH_FIELD;
            event.getChannel().sendMessage(displayAnswersForEditing(state)).queue();
            saveInProgress(userId, state);
        } else if (buttonId.equals("submit_application")) {
            // Trigger "no" action (submit)
            completeApplicationFromButton(state, userId, event);
        }
    }

    // Helper method to progress from Photo to Target Age, since it can be triggered from multiple branches above
    private void advanceToTargetAge(AppState state, MessageReceivedEvent event) {
        state.currentStep = AppStep.TARGET_AGE;
        String[] currentQuestions = LanguageManager.getQuestions(state.language);
        event.getChannel().sendMessage(currentQuestions[10].replaceAll("__PROGRESS_MAP__", "**(12/15)**")).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        // Validate guild for this environment (if in a guild)
        if (event.getGuild() != null && !EnvironmentManager.isGuildAllowed(event.getGuild().getId())) {
            event.reply("❌ This interaction is not available in this server.").setEphemeral(true).queue();
            System.out.println("[SECURITY] Rejected modal interaction from guild " + event.getGuild().getId() + " in " + EnvironmentManager.getEnvironmentName() + " environment");
            return;
        }
        
        String modalId = event.getModalId();

        if (modalId.startsWith("modal_request_change_")) {
            ApplicationReview.handleRequestChangeModal(event);
        } else if (modalId.startsWith("modal_request_photo_change_")) {
            ApplicationReview.handlePhotoChangeModal(event);
        } else if (modalId.startsWith("modal_convo_reply_mm_")) {
            ConversationRelay.handleMatchmakerReplyModal(event);
        } else if (modalId.startsWith("modal_convo_reply_")) {
            ConversationRelay.handleApplicantReplyModal(event);
        }
    }

    /**
     * Handles the three re-apply options sent when the user runs /apply with an existing profile.
     */
    private void handleReapplyAction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();

        // Security: the button owner must be the one pressing it
        String ownerId = buttonId.substring(buttonId.lastIndexOf('_') + 1);
        if (!ownerId.equals(userId)) {
            event.reply("❌ These options belong to a different user.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        event.getHook().editOriginalComponents(Collections.emptyList()).queue();

        if (buttonId.startsWith("reapply_edit_")) {
            if (!ProfileRepository.exists(userId)) {
                event.getHook().sendMessage("❌ Could not find your profile. It may have already been deleted.").queue();
                return;
            }
            AppState state = ProfileRepository.load(userId);
            if (state == null) {
                event.getHook().sendMessage("❌ Failed to load your profile data.").queue();
                return;
            }
            // Mark as pending review so they're invisible to matchmaking during editing
            state.status = "CHANGES_REQUESTED";
            activeApplications.put(userId, state);
            saveProfileJson(state, userId);
            saveInProgress(userId, state);

            // 14 section buttons across 3 rows (5 + 5 + 4)
            java.util.List<Button> btns = java.util.Arrays.asList(
                Button.secondary("user_edit_app_1",  "Name"),
                Button.secondary("user_edit_app_2",  "Birthday"),
                Button.secondary("user_edit_app_3",  "Location"),
                Button.secondary("user_edit_app_4",  "Gender"),
                Button.secondary("user_edit_app_5",  "Denomination"),
                Button.secondary("user_edit_app_6",  "Target Age"),
                Button.secondary("user_edit_app_7",  "Target Denom."),
                Button.secondary("user_edit_app_8",  "Physical Desc."),
                Button.secondary("user_edit_app_9",  "Hobbies"),
                Button.secondary("user_edit_app_10", "Strengths"),
                Button.secondary("user_edit_app_11", "Weaknesses"),
                Button.secondary("user_edit_app_12", "Looking For"),
                Button.secondary("user_edit_app_13", "Deal Breakers"),
                Button.secondary("user_edit_app_14", "Photo")
            );

            event.getChannel()
                .sendMessage("Select a section to update. When you're satisfied, use the **submit** option to send your profile for review again.")
                .setComponents(
                    ActionRow.of(btns.subList(0, 5)),
                    ActionRow.of(btns.subList(5, 10)),
                    ActionRow.of(btns.subList(10, 14))
                )
                .queue();

        } else if (buttonId.startsWith("reapply_delete_")) {
            String guildId = null;
            AppState existing = ProfileRepository.load(userId);
            if (existing != null) guildId = existing.guildId;
            ProfileRepository.delete(userId);
            activeApplications.remove(userId);
            deleteInProgress(userId);

            event.getHook().sendMessage("✅ Your previous profile has been deleted. Starting your new application now...").queue();
            startApplicationFromDM(event.getUser(), guildId);

        } else if (buttonId.startsWith("reapply_cancel_")) {
            event.getHook().sendMessage("👍 No changes were made to your profile.").queue();
        }
    }

    /**
     * Handles the Edit/Delete actions triggered by the applicant inside their DM
     */
    private void handleUserAppAction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();

        event.deferEdit().queue();
        event.getHook().editOriginalComponents(Collections.emptyList()).queue();

        if (buttonId.startsWith("user_edit_app_")) {
            int sectionNum = Integer.parseInt(buttonId.substring("user_edit_app_".length()));
            
            // Load application back into short-term memory if it's not already there
            if (!activeApplications.containsKey(userId)) {
                if (!ProfileRepository.exists(userId)) {
                    event.getHook().sendMessage("❌ Could not find your application data.").queue();
                    return;
                }
                AppState loaded = ProfileRepository.load(userId);
                if (loaded == null) {
                    event.getHook().sendMessage("❌ Failed to load your application data.").queue();
                    return;
                }
                activeApplications.put(userId, loaded);
            }
            
            AppState state = activeApplications.get(userId);
            state.currentStep = AppStep.EDITING_FIELD;
            
            AppStep editStep = getEditStepFromFieldNumber(sectionNum);
            if (editStep == null) {
                event.getHook().sendMessage("❌ Invalid section number requested.").queue();
                return;
            }
            
            state.fieldBeingEdited = editStep.toString();
            event.getChannel().sendMessage(getEditQuestionPrompt(sectionNum, state.language)).queue();
            saveInProgress(userId, state);
        }
        else if (buttonId.equals("user_delete_app")) {
            String guildIdToNotify = null;
            String userName = event.getUser().getName();

            AppState state = ProfileRepository.load(userId);
            if (state != null) {
                guildIdToNotify = state.guildId;
                userName = state.name;
            }
            ProfileRepository.delete(userId); // Physically delete the application

            activeApplications.remove(userId);
            deleteInProgress(userId);
            event.getHook().sendMessage("🗑️ Your application has been successfully deleted.").queue();
            
            // Notify matchmaker channel that the application was withdrawn
            if (guildIdToNotify != null) {
                try {
                    net.dv8tion.jda.api.entities.Guild guild = event.getJDA().getGuildById(guildIdToNotify);
                    if (guild != null) {
                        TextChannel mmChannel = Channels.findMatchmakerChannel(guild);
                        if (mmChannel != null) {
                            mmChannel.sendMessage("🗑️ Applicant **" + userName + "** (<@" + userId + ">) has deleted/withdrawn their application.").queue();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to notify matchmakers of deleted application.");
                }
            }
        }
    }

    /**
     * Handles quickmatch enrollment/unenrollment
     */
    private void handleQuickmatchEnrollment(String userId, boolean enrolled, ButtonInteractionEvent event) {
        event.deferEdit().queue();

        if (!ProfileRepository.exists(userId)) {
            event.getHook().sendMessage("❌ Could not find your profile. Please contact support.").queue();
            return;
        }

        AppState state = ProfileRepository.load(userId);
        if (state == null) {
            System.err.println("Error updating quickmatch enrollment for user " + userId);
            event.getHook().sendMessage("❌ Something went wrong. Please try again later.").queue();
            return;
        }

        state.quickmatchEnrolled = enrolled;
        ProfileRepository.save(userId, state);

        // Get the appropriate message based on language
        String message = enrolled
            ? LanguageManager.getQuickmatchEnrollSuccess(state.language)
            : LanguageManager.getQuickmatchDeclineMessage(state.language);

        event.getHook().sendMessage(message).queue();
    }
}