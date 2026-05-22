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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class ApplicationHandler extends ListenerAdapter {

    // Enum to represent exactly where the user is in the application process
    public enum AppStep {
        LANGUAGE,
        APPLICATION_LANGUAGE,
        NAME,
        COUNTRY,
        AGE,
        SEX,
        SECT,
        PHYSICAL,
        HOBBIES,
        STRENGTHS,
        WEAKNESSES,
        PHOTO,
        TARGET_AGE,
        TARGET_SECT,
        LOOK_FOR,
        DEAL_BREAKERS,
        CUSTOMIZE_PROMPT,
        EDIT_WHICH_FIELD,      // User is selecting which field to edit
        EDITING_FIELD,          // User is editing a specific field
        WAITING_FOR_DESIGN_CODE,
        COMPLETED
    }

    // A simple data class to hold the user's answers as they progress
    public static class AppState {
        public AppStep currentStep = AppStep.LANGUAGE;
        
        public String language;
        public String username; // Store the Discord Handle
        public String name;
        public String country;
        public String birthday; // stored as M/D/YYYY
        public boolean sex;
        public String sect;
        public String physicalDescription;
        public String hobbies;
        public String strengths;
        public String weaknesses;
        public String photoPath; // Local file path OR avatar URL
        public String targetAge;
        public String targetSect;
        public String lookFor;
        public String dealBreakers;
        public String designCode;
        
        // Application status tracking
        public String status = "PENDING"; // PENDING, ACCEPTED, REJECTED
        public String submittedAt;
        public String reviewedAt;
        public String reviewedBy; // Matchmaker's ID who reviewed it
        public String rejectionReason; // Reason for rejection with request for change
        public String guildId; // Guild where /apply was used
        
        // Quickmatch system
        public boolean quickmatchEnrolled = false;
        
        // Track which field is being edited
        public String fieldBeingEdited; // Store the AppStep enum name of the field being edited
    }

    // Thread pool for scheduling delayed tasks (like the fake human auto-rejections)
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // Helper class to store auto-mod results
    private static class AutoModResult {
        public String reason;
        public int sectionNum;
        public AutoModResult(String reason, int sectionNum) {
            this.reason = reason;
            this.sectionNum = sectionNum;
        }
    }

    // This HashMap acts as the bot's short-term memory. Key = User ID, Value = Their AppState
    private static final Map<String, AppState> activeApplications = new HashMap<>();

    // Compile pattern once to save resources. Matches ISFP, ENTJ-A, etc.
    private static final java.util.regex.Pattern MBTI_PATTERN = java.util.regex.Pattern.compile("(?i)\\b(I|E)(N|S)(F|T)(J|P)(-[TA])?\\b");

    /**
     * Helper method to accurately scan a sentence for MBTI / Enneagram terms
     */
    private boolean containsMBTI(String text) {
        if (text == null) return false;
        String lower = text.toUpperCase();
        if (lower.contains("MBTI") || lower.contains("ENNEAGRAM")) return true;
        
        // .find() correctly scans the whole string, unlike .matches() which requires a 100% exact match
        return MBTI_PATTERN.matcher(text).find();
    }

    /**
     * Evaluates the application against automated quality rules.
     * Returns an AutoModResult if it fails, or null if it passes.
     */
    private AutoModResult checkAutoRules(AppState state) {
        
        // 1. Check all relevant fields for MBTI / Enneagram types
        if (containsMBTI(state.physicalDescription)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 6);
        }
        if (containsMBTI(state.hobbies)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 7);
        }
        if (containsMBTI(state.strengths)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 8);
        }
        if (containsMBTI(state.weaknesses)) {
            return new AutoModResult("Don't use MBTI or Enneagram to describe yourself. Tell us about yourself in your own words!", 9);
        }
        if (containsMBTI(state.lookFor)) {
            return new AutoModResult("Don't use MBTI or Enneagram as a benchmark. Describe what makes a good partner in your own words!", 12);
        }
        if (containsMBTI(state.dealBreakers)) {
            return new AutoModResult("Don't use MBTI or Enneagram as a benchmark. Describe what makes a good partner in your own words!", 13);
        }

        // 2. Length Checks
        // Require at least 6 characters to prevent things like "5'11"" from slipping through
        if (state.physicalDescription != null && state.physicalDescription.trim().length() < 6) {
            System.out.println("Auto-Mod: Physical description too short for user " + state.username + " | Content: `" + state.physicalDescription + "`");
            return new AutoModResult("Your physical description is a bit too brief. Please provide a few more details so potential matches have a better idea of what you look like!", 6);
        }
        
        // Require at least 6 characters to force a real answer
        if (state.dealBreakers != null && state.dealBreakers.trim().length() < 6) {
            System.out.println("Auto-Mod: Deal breakers too short for user " + state.username + " | Content: `" + state.dealBreakers + "`");
            return new AutoModResult("Please list at least one or two specific deal breakers / red flags", 13);
        }

        // Gooning? Really man?
        if (state.hobbies != null && state.hobbies.toLowerCase().contains("gooning")) {
            System.out.println("Auto-Mod: Bro is GOONING??? - " + state.username + " | Content: `" + state.hobbies + "`");
            return new AutoModResult("Gooning? Really??? Please change that", 13);
        }

        // Gooning? Really man?
        if (state.physicalDescription != null && state.physicalDescription.toLowerCase().contains("breedable")) {
            System.out.println("Auto-Mod: I... really didn't think anyone would ever type that: " + state.username + " | Content: `" + state.physicalDescription + "`");
            return new AutoModResult("Please don't include obscene or provocative language in your self-description", 6);
        }

        // Add more regex or length checks here!
        
        return null; // Null means it passed all auto-mod rules!
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
                }
            );
            
        }, error -> {
            // This catches if Discord blocks us from even opening the channel
            System.err.println("❌ Failed to open DM channel for user: " + user.getName() + " (ID: " + user.getId() + ")");
            event.getHook().sendMessage("❌ I couldn't open a DM with you. Please make sure your DMs are open and try again.").queue();
            activeApplications.remove(user.getId());
        });
    }

    /**
     * Validates target age input. Accepts single ages (e.g., "25") or ranges (e.g., "18-25").
     * Handles various dash formats and spacing: "18-25", "18 - 25", "18—25", etc.
     * @param input The user's target age input
     * @return true if valid (ages 18-70), false otherwise
     */
    private boolean isValidTargetAge(String input) {
        input = input.trim();
        
        // Replace all types of dashes and hyphens with a standard hyphen
        // This includes: - (hyphen), – (en-dash), — (em-dash)
        input = input.replaceAll("[–—]", "-");
        
        // Handle ranges (e.g., "18-25", "18 - 25", "18- 25", etc.)
        if (input.contains("-")) {
            // Split by hyphen and trim whitespace from each part
            String[] parts = input.split("-");
            if (parts.length != 2) {
                return false; // Invalid format with multiple dashes
            }
            
            try {
                int minAge = Integer.parseInt(parts[0].trim());
                int maxAge = Integer.parseInt(parts[1].trim());
                
                // Both ages must be 18-70
                return minAge >= 18 && minAge <= 70 && maxAge >= 18 && maxAge <= 70 && minAge <= maxAge;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            // Single age (e.g., "25")
            try {
                int age = Integer.parseInt(input);
                return age >= 18 && age <= 70;
            } catch (NumberFormatException e) {
                return false;
            }
        }
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

                    String strAndWeak = "\n";

                    if (state.strengths != null && !state.strengths.isEmpty() && state.weaknesses != null && !state.weaknesses.isEmpty()) {
                        strAndWeak = state.strengths + "\n" + state.weaknesses + "\n\n";
                    }

                    // Construct the beautiful rich text using their actual answers!
                    String text = "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}" + state.name + "{/}*\n" +
                                    "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:6.0}{f:Arial Rounded MT Bold}@" + user.getName() + "{/}*\n\n" +
                                    calculateAge(state.birthday) + " | " + getBirthYear(state.birthday) + "\n" +
                                    (state.sex ? "Female" : "Male") + "\n" +
                                    state.sect + "\n" +
                                    state.physicalDescription + "\n\n" +
                                    state.hobbies + "\n\n" +
                                    strAndWeak +
                                    "{img:green_flag.png} PARTNER: " + state.lookFor.replace("\n", ", ") + "\n" +
                                    "{img:red_flag.png} PARTNER: " + state.dealBreakers.replace("\n", ", ");
    
                    // Resolve design code to actual file paths
                    DesignPaths designPaths = resolveDesignCode(state.designCode);
                    String fontPath = "assets/fonts/VAG Rounded Next Shine Regular.ttf";
    
                    File generatedImage = ImageGenerator.generateForUser(designPaths.backgroundPath, pfpUri, designPaths.framePath, fontPath, text, user.getId());
    
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
        String strAndWeak = "\n";
        if (state.strengths != null && !state.strengths.isEmpty()
                && state.weaknesses != null && !state.weaknesses.isEmpty()) {
            strAndWeak = state.strengths + "\n" + state.weaknesses + "\n\n";
        }
        String username = state.username != null ? state.username : "unknown";
        String lookFor = state.lookFor != null ? state.lookFor.replace("\n", ", ") : "";
        String dealBreakers = state.dealBreakers != null ? state.dealBreakers.replace("\n", ", ") : "";
        return "{blob}{s:70}*{g:line:#FF6699:#9966FF}{o:#FFFFFF:8.0}{f:Arial Rounded MT Bold}" + state.name + "{/}*\n"
            + "{blob}{s:45}*{g:line:#FF6699:#FF9966}{o:#FFFFFF:6.0}{f:Arial Rounded MT Bold}@" + username + "{/}*\n\n"
            + calculateAge(state.birthday) + " | " + getBirthYear(state.birthday) + "\n"
            + (state.sex ? "Female" : "Male") + "\n"
            + state.sect + "\n"
            + state.physicalDescription + "\n\n"
            + state.hobbies + "\n\n"
            + strAndWeak
            + "{img:green_flag.png} PARTNER: " + lookFor + "\n"
            + "{img:red_flag.png} PARTNER: " + dealBreakers;
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
        activeApplications.remove(userId);
        
        channel.sendMessage("✅ **" + LanguageManager.getCompletionMessage(state.language) + "**").queue();

        AutoModResult autoMod = checkAutoRules(state);

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
                        
                        dm.sendMessage("⚠️ A matchmaker has requested changes to your application. Please review the feedback and update the requested section.\n\n**Matchmaker Note:** " + autoMod.reason + "\n**Section to Edit:** #" + autoMod.sectionNum + " (" + getSectionName(autoMod.sectionNum) + ")")
                            .setComponents(ActionRow.of(editBtn, deleteBtn))
                            .queue();
                    });
                });
            }, delaySeconds, TimeUnit.SECONDS);

            System.out.println("🤖 Auto-Mod intercepted application for " + state.name + ". Ghost request scheduled in " + delaySeconds + " seconds.");
            return; // EXIT HERE! Do not post to the matchmaker channel!
        }

        if ((state.status == null || !state.status.equals("CHANGES_REQUESTED")) && !state.quickmatchEnrolled) {
            sendQuickmatchEnrollmentPrompt(userId, jda);
        }
        // --- NORMAL SUBMISSION FLOW ---
        state.status = "PENDING";
        saveProfileJson(state, userId);
        cleanUpSrvJson(userId);
        postApplicationToChannel(state, userId, jda);
        // Only send QM enrollment prompt if this is the first time the user is submitting an application
    }

    // Helper to save JSON
    private void saveProfileJson(AppState state, String userId) {
        File profilesDir = new File("user_content/profiles/");
        if (!profilesDir.exists()) profilesDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(profilesDir, userId + ".json"))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(state, writer);
        } catch (IOException e) {
            System.err.println("❌ Failed to save profile JSON for user: " + userId);
        }
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
                    File profileFile = new File("user_content/profiles/" + userId + ".json");
                    String language = "english";
                    
                    if (profileFile.exists()) {
                        Gson gson = new Gson();
                        AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
                        if (state.language != null) {
                            language = state.language;
                        }
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
     * Posts the submitted application to the guild's applications channel with review buttons
     */
    private void postApplicationToChannel(AppState state, String userId, JDA jda) {
        if (state.guildId == null) {
            System.out.println("⚠️ No guild ID found for application - skipping channel post");
            return;
        }

        try {
            // Get the guild
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(state.guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + state.guildId);
                return;
            }

            // Look for a channel named "applications" or "pending-applications"
            java.util.List<?> applicationsList = guild.getTextChannelsByName("matchmaker-backroom", true);
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("matchmakers", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("applications", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("pending-applications", true);
            }

            if (applicationsList.isEmpty()) {
                System.err.println("⚠️ No 'applications' or 'pending-applications' channel found in guild: " + guild.getName());
                return;
            }

            // Create embed with application details (all 13 sections)
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 New Application: " + state.name)
                    .setColor(state.sex ? 0xFF6699 : 0x9966FF)
                    .addField("1. Name", state.name, true)
                    .addField("2. Birthday", state.birthday != null ? state.birthday + " (age " + calculateAge(state.birthday) + ")" : "N/A", true)
                    .addField("3. Location", state.country, true)
                    .addField("4. Gender", state.sex ? "Female" : "Male", true)
                    .addField("5. Denomination", state.sect, true)
                    .addField("6. Target Age Range", state.targetAge, true)
                    .addField("7. Target Denomination", state.targetSect != null ? state.targetSect : "N/A", true)
                    .addField("8. Physical Description", state.physicalDescription != null ? state.physicalDescription : "N/A", false)
                    .addField("9. Hobbies", state.hobbies != null ? state.hobbies : "N/A", false)
                    .addField("10. Strengths", state.strengths != null ? state.strengths : "N/A", false)
                    .addField("11. Weaknesses", state.weaknesses != null ? state.weaknesses : "N/A", false)
                    .addField("12. What They're Looking For", state.lookFor != null ? state.lookFor : "N/A", false)
                    .addField("13. Deal Breakers", state.dealBreakers != null ? state.dealBreakers : "N/A", false)
                    .setFooter("User ID: " + userId + " | Submitted: " + state.submittedAt)
                    .setTimestamp(java.time.Instant.now());

            // Create action buttons
            Button acceptBtn = Button.success("app_accept_" + userId, "✅ Accept");
            Button requestChangeBtn = Button.secondary("app_request_change_" + userId, "⚠️ Request Change");
            Button rejectBtn = Button.danger("app_reject_" + userId, "❌ Reject");

            ActionRow actionRow = ActionRow.of(acceptBtn, requestChangeBtn, rejectBtn);

            // Send the message with embed and buttons to the channel
            Object channelObj = applicationsList.get(0);
            
            // Use reflection to call sendMessageEmbeds and setComponents
            java.lang.reflect.Method sendEmbedMethod;
            java.lang.reflect.Method setComponentsMethod;
            java.lang.reflect.Method queueMethod;
            
            try {
                sendEmbedMethod = channelObj.getClass().getMethod("sendMessageEmbeds", java.util.Collection.class);
                Object messageAction = sendEmbedMethod.invoke(channelObj, java.util.Collections.singletonList(embed.build()));
                
                setComponentsMethod = messageAction.getClass().getMethod("setComponents", java.util.Collection.class);
                messageAction = setComponentsMethod.invoke(messageAction, java.util.Collections.singletonList(actionRow));
                
                queueMethod = messageAction.getClass().getMethod("queue");
                queueMethod.invoke(messageAction);
                
                System.out.println("✅ Application posted to channel");
            } catch (Exception ex) {
                System.err.println("❌ Failed to post application: " + ex.getMessage());
                ex.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("❌ Error posting application to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Posts a conversation start message to the applications channel
     */
    public static void postConversationStartToChannel(User applicant, String messageContent, String matchmakerId, String guildId, JDA jda) {
        try {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + guildId);
                return;
            }

            // Find the applications channel
            java.util.List<?> applicationsList = guild.getTextChannelsByName("matchmaker-backroom", true);
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("matchmakers", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("applications", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("pending-applications", true);
            }

            if (applicationsList.isEmpty()) {
                System.err.println("⚠️ No applications channel found in guild: " + guild.getName());
                return;
            }

            // Create embed for conversation start (without button - it goes in the DM instead)
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💬 Anonymous Conversation with " + applicant.getName())
                    .setColor(0x6699FF)
                    .addField("Applicant", applicant.getAsMention() + " (ID: " + applicant.getId() + ")", false)
                    .addField("Matchmaker Message", messageContent, false)
                    .setFooter("ID: " + applicant.getId() + " | Matchmaker: " + matchmakerId)
                    .setTimestamp(java.time.Instant.now());

            // Send to channel (no buttons - reply button is in the DM)
            Object channelObj = applicationsList.get(0);
            try {
                java.lang.reflect.Method sendEmbedMethod = channelObj.getClass().getMethod("sendMessageEmbeds", java.util.Collection.class);
                Object messageAction = sendEmbedMethod.invoke(channelObj, java.util.Collections.singletonList(embed.build()));
                
                java.lang.reflect.Method queueMethod = messageAction.getClass().getMethod("queue");
                queueMethod.invoke(messageAction);
                
                System.out.println("✅ Conversation message posted to channel");
            } catch (Exception ex) {
                System.err.println("❌ Failed to post conversation message: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("❌ Error posting conversation to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the reply button click from applicant's DM - shows modal
     */
    private void handleConversationReplyButton(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String applicantId, matchmakerId;
        String modalTitle = "Reply to Matchmaker";
        String modalId;
        
        if (buttonId.startsWith("convo_reply_mm_")) {
            // Matchmaker replying to applicant
            String[] parts = buttonId.substring("convo_reply_mm_".length()).split("_");
            if (parts.length < 2) {
                event.reply("❌ Invalid conversation reference.").setEphemeral(true).queue();
                return;
            }
            matchmakerId = parts[0];
            applicantId = parts[1];
            modalTitle = "Reply to Applicant";
            modalId = "modal_convo_reply_mm_" + matchmakerId + "_" + applicantId;
        } else {
            // Applicant replying to matchmaker
            String[] parts = buttonId.substring("convo_reply_".length()).split("_");
            if (parts.length < 2) {
                event.reply("❌ Invalid conversation reference.").setEphemeral(true).queue();
                return;
            }
            applicantId = parts[0];
            matchmakerId = parts[1];
            modalId = "modal_convo_reply_" + applicantId + "_" + matchmakerId;
        }
        
        // Create modal for reply
        TextInput replyInput = TextInput.create("reply_message", "Your Reply", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Type your response here...")
                .setRequired(true)
                .build();
        
        Modal modal = Modal.create(modalId, modalTitle)
                .addActionRow(replyInput)
                .build();
        
        event.replyModal(modal).queue();
    }

    /**
     * Posts a conversation reply to the applications channel when applicant replies via DM modal
     */
    private void postConversationReplyToChannel(String applicantId, String matchmakerId, String replyContent, JDA jda, String sender) {
        try {
            // Get the applicant's guild ID from their profile
            File profileFile = new File("user_content/profiles/" + applicantId + ".json");
            if (!profileFile.exists()) {
                System.err.println("❌ Profile not found for applicant: " + applicantId);
                return;
            }

            Gson gson = new Gson();
            AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
            String guildId = state.guildId;

            if (guildId == null) {
                System.err.println("❌ No guild ID found in applicant profile");
                return;
            }

            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + guildId);
                return;
            }

            // Find the applications channel
            java.util.List<?> applicationsList = guild.getTextChannelsByName("matchmaker-backroom", true);
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("matchmakers", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("applications", true);
            }
            if (applicationsList.isEmpty()) {
                applicationsList = guild.getTextChannelsByName("pending-applications", true);
            }

            if (applicationsList.isEmpty()) {
                System.err.println("⚠️ No applications channel found in guild: " + guild.getName());
                return;
            }

            // Create embed for reply
            String senderLabel = "applicant".equals(sender) ? "Applicant Reply" : "Matchmaker Reply";
            int embedColor = "applicant".equals(sender) ? 0x99FF99 : 0xFF9999;

            // If applicant replied, mention the matchmaker; if matchmaker, mention is in the embed
            String mention = "applicant".equals(sender) ? "<@" + matchmakerId + ">" : "";
            String description = mention.isEmpty() ? replyContent : mention + "\n\n" + replyContent;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(senderLabel)
                    .setColor(embedColor)
                    .setDescription(description)
                    .setFooter("Applicant ID: " + applicantId)
                    .setTimestamp(java.time.Instant.now());

            // Send to channel
            Object channelObj = applicationsList.get(0);
            try {
                java.lang.reflect.Method sendEmbedMethod = channelObj.getClass().getMethod("sendMessageEmbeds", java.util.Collection.class);
                Object messageAction = sendEmbedMethod.invoke(channelObj, java.util.Collections.singletonList(embed.build()));
                
                // Add reply button for applicant replies
                if ("applicant".equals(sender)) {
                    Button replyBtn = Button.primary("convo_reply_mm_" + matchmakerId + "_" + applicantId, "💬 Reply");
                    ActionRow actionRow = ActionRow.of(replyBtn);
                    
                    java.lang.reflect.Method setComponentsMethod = messageAction.getClass().getMethod("setComponents", java.util.Collection.class);
                    messageAction = setComponentsMethod.invoke(messageAction, java.util.Collections.singletonList(actionRow));
                }
                
                java.lang.reflect.Method queueMethod = messageAction.getClass().getMethod("queue");
                queueMethod.invoke(messageAction);
                
                System.out.println("✅ Conversation reply posted to channel");
            } catch (Exception ex) {
                System.err.println("❌ Failed to post conversation reply: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("❌ Error posting conversation reply to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a matchmaker's reply to the applicant via DM
     */
    private void sendConversationReplyToApplicant(String applicantId, String matchmakerId, String replyContent, JDA jda) {
        try {
            net.dv8tion.jda.api.entities.User applicant = jda.retrieveUserById(applicantId).complete();
            if (applicant == null) {
                System.err.println("❌ Could not retrieve applicant with ID: " + applicantId);
                return;
            }

            applicant.openPrivateChannel().queue(dm -> {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("💬 Message from Matchmaker")
                        .setColor(0xFF9999)
                        .setDescription(replyContent)
                        .setFooter("Matchmaker ID: " + matchmakerId)
                        .setTimestamp(java.time.Instant.now());

                Button replyBtn = Button.primary("convo_reply_" + applicantId + "_" + matchmakerId, "💬 Reply");
                ActionRow actionRow = ActionRow.of(replyBtn);

                dm.sendMessageEmbeds(embed.build())
                        .setComponents(actionRow)
                        .queue(
                            success -> System.out.println("✅ Conversation reply sent to applicant via DM"),
                            error -> System.err.println("❌ Failed to send conversation reply to applicant: " + error.getMessage())
                        );
            });
        } catch (Exception e) {
            System.err.println("❌ Error sending conversation reply to applicant: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Edits the original DM message to include the applicant's reply
     */
    private void editDMWithApplicantReply(String applicantId, String matchmakerId, String replyContent, JDA jda) {
        try {
            net.dv8tion.jda.api.entities.User applicant = jda.retrieveUserById(applicantId).complete();
            if (applicant == null) {
                System.err.println("❌ Could not retrieve applicant with ID: " + applicantId);
                return;
            }

            applicant.openPrivateChannel().queue(dm -> {
                String messageId = MessagingHandler.getDMMessageId(applicantId, matchmakerId);
                if (messageId == null) {
                    System.err.println("⚠️ No DM message ID found for editing");
                    return;
                }

                dm.retrieveMessageById(messageId).queue(msg -> {
                    try {
                        // Get the current embed
                        if (msg.getEmbeds().isEmpty()) {
                            System.err.println("⚠️ Original message has no embed");
                            return;
                        }

                        net.dv8tion.jda.api.entities.MessageEmbed originalEmbed = msg.getEmbeds().get(0);
                        String originalDescription = originalEmbed.getDescription();

                        // Append the reply to the embed
                        String updatedDescription = originalDescription + "\n\n✅ **Your reply:**\n" + replyContent;

                        EmbedBuilder embed = new EmbedBuilder(originalEmbed)
                                .setDescription(updatedDescription);

                        // Remove reply button (set components to empty)
                        msg.editMessageEmbeds(embed.build())
                                .setComponents(java.util.Collections.emptyList())
                                .queue(
                                    editSuccess -> System.out.println("✅ Edited DM to include applicant reply"),
                                    editError -> System.err.println("⚠️ Could not edit DM: " + editError.getMessage())
                                );
                    } catch (Exception ex) {
                        System.err.println("⚠️ Error editing message: " + ex.getMessage());
                    }
                }, msgError -> System.err.println("⚠️ Could not retrieve message for editing"));
            });
        } catch (Exception e) {
            System.err.println("❌ Error editing DM with reply: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays all user answers in a numbered list so they can select which to edit
     */
    private String displayAnswersForEditing(AppState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("✏️ **Which answer would you like to edit?**\n\n");
        
        sb.append("` 1)` Name: ").append(state.name).append("\n");
        sb.append("` 2)` Birthday: ").append(state.birthday != null ? state.birthday + " (age " + calculateAge(state.birthday) + ")" : "N/A").append("\n");
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
            {13}    // 13: Deal Breakers (index 13)
        };
        
        if (fieldNumber < 1 || fieldNumber > fieldMap.length) {
            return "Invalid selection. Please reply with a number between 1 and 13.";
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
            default: return null;
        }
    }

    /**
     * Helper to get a human-readable section name from its number
     */
    /**
     * Maps embed section numbers to edit field numbers.
     * This ensures matchmakers see the correct field names when entering section numbers in the modal.
     * The embed now displays all 13 sections in order, so this is a 1:1 mapping.
     */
    @SuppressWarnings("unused")
    private int mapEmbedSectionToEditField(int embedSection) {
        // Embed sections now match edit field numbers exactly
        if (embedSection >= 1 && embedSection <= 13) {
            return embedSection;
        }
        return 1; // Default to first field if invalid
    }

    private String getSectionName(int sectionNum) {
        switch (sectionNum) {
            case 1: return "Name";
            case 2: return "Birthday";
            case 3: return "Location";
            case 4: return "Gender";
            case 5: return "Denomination";
            case 6: return "Target Age Range";
            case 7: return "Target Denomination";
            case 8: return "Physical Description";
            case 9: return "Hobbies";
            case 10: return "Strengths";
            case 11: return "Weaknesses";
            case 12: return "What They're Looking For";
            case 13: return "Deal Breakers";
            default: return "Unknown Section";
        }
    }

    /** Parses birthday input: M/D/YYYY date string or plain age integer (fallback sets birthday to today minus N years). Returns null if unparseable. */
    private static String parseBirthday(String input) {
        if (input == null) return null;
        input = input.trim();
        // Fallback: plain age number
        try {
            int age = Integer.parseInt(input);
            if (age < 1 || age > 120) return null;
            java.time.LocalDate bd = java.time.LocalDate.now().minusYears(age);
            return bd.getMonthValue() + "/" + bd.getDayOfMonth() + "/" + bd.getYear();
        } catch (NumberFormatException ignored) {}
        // Primary: M/D/YYYY (also accepts M-D-YYYY)
        String[] parts = input.split("[/\\-]");
        if (parts.length == 3) {
            try {
                int month = Integer.parseInt(parts[0].trim());
                int day   = Integer.parseInt(parts[1].trim());
                int year  = Integer.parseInt(parts[2].trim());
                if (year < 100) year += 1900;
                java.time.LocalDate.of(year, month, day); // validates ranges
                if (year < 1900 || year > java.time.LocalDate.now().getYear()) return null;
                return month + "/" + day + "/" + year;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static int calculateAge(String birthday) {
        if (birthday == null) return 0;
        try {
            String[] p = birthday.split("/");
            java.time.LocalDate bd = java.time.LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            return (int) java.time.temporal.ChronoUnit.YEARS.between(bd, java.time.LocalDate.now());
        } catch (Exception e) { return 0; }
    }

    private static int getBirthYear(String birthday) {
        if (birthday == null) return 0;
        try {
            return Integer.parseInt(birthday.split("/")[2]);
        } catch (Exception e) { return 0; }
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
                state.country = messageContent;
                state.currentStep = AppStep.AGE;
                event.getChannel().sendMessage("**(4/15)** " + currentQuestions[2]).queue();
                break;

            case AGE:
                String parsedBirthday = parseBirthday(messageContent);
                if (parsedBirthday == null) {
                    event.getChannel().sendMessage("⚠️ " + LanguageManager.getInvalidAgeWarning(state.language)).queue();
                    return;
                }
                if (calculateAge(parsedBirthday) < 18) {
                    event.getChannel().sendMessage("❌ " + LanguageManager.getUnderageWarning(state.language)).queue();
                    activeApplications.remove(userId);
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
                state.hobbies = messageContent;
                state.currentStep = AppStep.STRENGTHS;
                event.getChannel().sendMessage("**(9/15)** " + currentQuestions[7]).queue();
                break;

            case STRENGTHS:
                state.strengths = messageContent;
                state.currentStep = AppStep.WEAKNESSES;
                event.getChannel().sendMessage("**(10/15)** " + currentQuestions[8]).queue();
                break;

            case WEAKNESSES:
                state.weaknesses = messageContent;
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
                            advanceToTargetAge(state, event);
                        }).exceptionally(ex -> {
                            event.getChannel().sendMessage("❌ Something went wrong saving your image. We'll use a placeholder profile picture instead.").queue();
                            state.photoPath = state.sex ? "assets/female.png" : "assets/male.png";
                            advanceToTargetAge(state, event);
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
                if (isValidTargetAge(messageContent)) {
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
                state.lookFor = messageContent;
                state.currentStep = AppStep.DEAL_BREAKERS;
                event.getChannel().sendMessage("**(15/15)** " + currentQuestions[13]).queue();
                break;

            case DEAL_BREAKERS:
                state.dealBreakers = messageContent;
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
                        state.country = messageContent;
                        break;
                    case AGE:
                        String editedBirthday = parseBirthday(messageContent);
                        if (editedBirthday == null) {
                            event.getChannel().sendMessage("⚠️ " + LanguageManager.getInvalidAgeWarning(state.language)).queue();
                            return;
                        }
                        if (calculateAge(editedBirthday) < 18) {
                            event.getChannel().sendMessage("❌ " + LanguageManager.getUnderageWarning(state.language)).queue();
                            activeApplications.remove(userId);
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
                        state.hobbies = messageContent;
                        break;
                    case STRENGTHS:
                        state.strengths = messageContent;
                        break;
                    case WEAKNESSES:
                        state.weaknesses = messageContent;
                        break;
                    case TARGET_AGE:
                        if (!isValidTargetAge(messageContent)) {
                            event.getChannel().sendMessage("⚠️ " + LanguageManager.getTargetAgeValidationError(state.language)).queue();
                            return;
                        }
                        state.targetAge = messageContent;
                        break;
                    case TARGET_SECT:
                        state.targetSect = messageContent;
                        break;
                    case LOOK_FOR:
                        state.lookFor = messageContent;
                        break;
                    case DEAL_BREAKERS:
                        state.dealBreakers = messageContent;
                        break;
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
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
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
        if (buttonId.startsWith("app_accept_") || buttonId.startsWith("app_request_change_") || buttonId.startsWith("app_reject_")) {
            handleMatchmakerAction(event);
            return;
        }

        // Check if it's a conversation reply button
        if (buttonId.startsWith("convo_reply_")) {
            handleConversationReplyButton(event);
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
        } else if (buttonId.equals("edit_answers")) {
            // Trigger "edit" action
            state.currentStep = AppStep.EDIT_WHICH_FIELD;
            event.getChannel().sendMessage(displayAnswersForEditing(state)).queue();
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

    /**
     * Handles the Accept/Reject/Request Change buttons pressed by matchmakers
     */
    private void handleMatchmakerAction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        // Extract the userId from the button ID (e.g., "app_reject_123456789")
        String targetUserId = buttonId.substring(buttonId.lastIndexOf("_") + 1);

        if (buttonId.startsWith("app_request_change_")) {
            // 1. Send Modal for Request Change (This acts as the interaction ACK!)
            TextInput reason = TextInput.create("reason", "Reason for Change", TextInputStyle.PARAGRAPH)
                .setPlaceholder("What needs to be changed in their application?")
                .setRequired(true)
                .build();

            TextInput section = TextInput.create("section_number", "Section Number (1-13)", TextInputStyle.SHORT)
                .setPlaceholder("Which section needs editing? (e.g., 5)")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(2)
                .build();

            Modal modal = Modal.create("modal_request_change_" + targetUserId, "Request Application Change")
                .addActionRow(reason)
                .addActionRow(section)
                .build();

            event.replyModal(modal).queue();
        } else {
            // 2. For Accept / Reject: Defer the edit, then remove buttons (This acts as the ACK!)
            event.deferEdit().queue();
            event.getHook().editOriginalComponents(Collections.emptyList()).queue();
            
            // Update profile status before sending DM
            String newStatus = buttonId.startsWith("app_accept_") ? "ACCEPTED" : "REJECTED";
            File profileFile = new File("user_content/profiles/" + targetUserId + ".json");
            if (profileFile.exists()) {
                try {
                    Gson gson = new Gson();
                    AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
                    state.status = newStatus;
                    try (FileWriter writer = new FileWriter(profileFile)) {
                        Gson gsonWriter = new GsonBuilder().setPrettyPrinting().create();
                        gsonWriter.toJson(state, writer);
                    }
                    System.out.println("✅ Profile status updated to " + newStatus + " for user " + targetUserId);
                } catch (Exception ex) {
                    System.err.println("❌ Error updating profile status: " + ex.getMessage());
                }
            }
            
            // Now safely fetch user using the API (bypassing local cache) and send DM
            event.getJDA().retrieveUserById(targetUserId).queue(user -> {
                user.openPrivateChannel().queue(channel -> {
                    if (buttonId.startsWith("app_accept_")) {
                        channel.sendMessage("🎉 Good news! Your matchmaking application has been **ACCEPTED**!").queue();
                        event.getHook().sendMessage("✅ Accepted application for " + user.getName()).queue();
                    } else if (buttonId.startsWith("app_reject_")) {
                        channel.sendMessage("❌ We're sorry, but your matchmaking application has been **REJECTED**.").queue();
                        event.getHook().sendMessage("❌ Rejected application for " + user.getName()).queue();
                    }
                }, error -> {
                    event.getHook().sendMessage("⚠️ Processed, but could not send a DM to user ID " + targetUserId + " (DMs closed).").queue();
                });
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not find user with ID " + targetUserId + " from Discord API.").queue();
            });
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("modal_request_change_")) {
            String targetUserId = event.getModalId().substring(event.getModalId().lastIndexOf("_") + 1);
            String reason = event.getValue("reason").getAsString();
            String sectionStr = event.getValue("section_number").getAsString().trim();

            int sectionNum;
            try {
                sectionNum = Integer.parseInt(sectionStr);
                if (sectionNum < 1 || sectionNum > 13) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                event.reply("❌ Invalid section number. Please provide a number between 1 and 13.").setEphemeral(true).queue();
                return;
            }

            // Defer the edit to ACK the modal, then remove the buttons from the original message!
            event.deferEdit().queue();
            event.getHook().editOriginalComponents(Collections.emptyList()).queue();

            // Update user profile status
            File profileFile = new File("user_content/profiles/" + targetUserId + ".json");
            if (profileFile.exists()) {
                try {
                    Gson gson = new Gson();
                    AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
                    state.status = "CHANGES_REQUESTED";
                    try (FileWriter writer = new FileWriter(profileFile)) {
                        Gson gsonWriter = new GsonBuilder().setPrettyPrinting().create();
                        gsonWriter.toJson(state, writer);
                    }
                } catch (Exception ex) {
                    System.err.println("Error updating profile status: " + ex.getMessage());
                }
            }

            // Guaranteed API fetch to prevent the null pointer cache errors
            event.getJDA().retrieveUserById(targetUserId).queue(user -> {
                user.openPrivateChannel().queue(channel -> {
                    Button editBtn = Button.primary("user_edit_app_" + sectionNum, "✏️ Edit Application");
                    Button deleteBtn = Button.danger("user_delete_app", "🗑️ Delete Application");
                    
                    channel.sendMessage("⚠️ A matchmaker has requested changes to your application. Please review the feedback and update the requested section.\n\n**Matchmaker Note:** " + reason + "\n**Section to Edit:** #" + sectionNum + " (" + getSectionName(sectionNum) + ")")
                           .setComponents(ActionRow.of(editBtn, deleteBtn))
                           .queue();
                           
                    event.getHook().sendMessage("⚠️ Requested changes from " + user.getName() + " for reason: " + reason + " (Section #" + sectionNum + " - " + getSectionName(sectionNum) + ")").queue();
                }, error -> {
                    event.getHook().sendMessage("⚠️ Processed change request, but could not send a DM to user ID " + targetUserId + " (DMs closed).").queue();
                });
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not find user with ID " + targetUserId + " from Discord API.").queue();
            });
        } else if (event.getModalId().startsWith("modal_convo_reply_mm_")) {
            // Handle conversation reply from matchmaker
            String[] parts = event.getModalId().substring("modal_convo_reply_mm_".length()).split("_");
            if (parts.length < 2) {
                event.reply("❌ Invalid conversation reference.").setEphemeral(true).queue();
                return;
            }
            
            String matchmakerId = parts[0];
            String applicantId = parts[1];
            String replyContent = event.getValue("reply_message").getAsString();
            
            event.deferReply(true).queue();
            
            // Save the conversation
            MessagingHandler.saveMessage(applicantId, matchmakerId, "matchmaker", replyContent);
            
            // Try to post to applications channel
            postConversationReplyToChannel(
                applicantId, matchmakerId, replyContent, 
                event.getJDA(), "matchmaker"
            );
            
            // Send reply to applicant via DM
            sendConversationReplyToApplicant(applicantId, matchmakerId, replyContent, event.getJDA());
            
            event.getHook().sendMessage("✅ Your reply has been sent to the applicant.").queue();
        } else if (event.getModalId().startsWith("modal_convo_reply_")) {
            // Handle conversation reply from applicant
            String[] parts = event.getModalId().substring("modal_convo_reply_".length()).split("_");
            if (parts.length < 2) {
                event.reply("❌ Invalid conversation reference.").setEphemeral(true).queue();
                return;
            }
            
            String applicantId = parts[0];
            String matchmakerId = parts[1];
            String replyContent = event.getValue("reply_message").getAsString();
            
            event.deferReply(true).queue();
            
            // Save the conversation
            MessagingHandler.saveMessage(applicantId, matchmakerId, "applicant", replyContent);
            
            // Edit the original DM to include the reply
            editDMWithApplicantReply(applicantId, matchmakerId, replyContent, event.getJDA());
            
            // Try to post to applications channel
            postConversationReplyToChannel(
                applicantId, matchmakerId, replyContent, 
                event.getJDA(), "applicant"
            );
            
            event.getHook().sendMessage("✅ Your reply has been sent to the matchmaker.").queue();
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
                File profileFile = new File("user_content/profiles/" + userId + ".json");
                if (profileFile.exists()) {
                    try {
                        Gson gson = new Gson();
                        AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
                        activeApplications.put(userId, state);
                    } catch (Exception e) {
                        event.getHook().sendMessage("❌ Failed to load your application data.").queue();
                        return;
                    }
                } else {
                    event.getHook().sendMessage("❌ Could not find your application data.").queue();
                    return;
                }
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
        } 
        else if (buttonId.equals("user_delete_app")) {
            File profileFile = new File("user_content/profiles/" + userId + ".json");
            String guildIdToNotify = null;
            String userName = event.getUser().getName();

            if (profileFile.exists()) {
                try {
                    Gson gson = new Gson();
                    AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
                    guildIdToNotify = state.guildId;
                    userName = state.name;
                } catch (Exception e) {
                    // Ignore
                }
                profileFile.delete(); // Physically delete the application
            }
            
            activeApplications.remove(userId);
            event.getHook().sendMessage("🗑️ Your application has been successfully deleted.").queue();
            
            // Notify matchmaker channel that the application was withdrawn
            if (guildIdToNotify != null) {
                try {
                    net.dv8tion.jda.api.entities.Guild guild = event.getJDA().getGuildById(guildIdToNotify);
                    if (guild != null) {
                        java.util.List<?> applicationsList = guild.getTextChannelsByName("matchmaker-backroom", true);
                        if (applicationsList.isEmpty()) applicationsList = guild.getTextChannelsByName("matchmakers", true);
                        if (applicationsList.isEmpty()) applicationsList = guild.getTextChannelsByName("applications", true);
                        if (applicationsList.isEmpty()) applicationsList = guild.getTextChannelsByName("pending-applications", true);
                        
                        if (!applicationsList.isEmpty()) {
                            Object channelObj = applicationsList.get(0);
                            java.lang.reflect.Method sendMsgMethod = channelObj.getClass().getMethod("sendMessage", CharSequence.class);
                            Object msgAction = sendMsgMethod.invoke(channelObj, "🗑️ Applicant **" + userName + "** (<@" + userId + ">) has deleted/withdrawn their application.");
                            msgAction.getClass().getMethod("queue").invoke(msgAction);
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
        
        File profileFile = new File("user_content/profiles/" + userId + ".json");
        if (!profileFile.exists()) {
            event.getHook().sendMessage("❌ Could not find your profile. Please contact support.").queue();
            return;
        }

        try {
            Gson gson = new Gson();
            AppState state = gson.fromJson(new java.io.FileReader(profileFile), AppState.class);
            
            state.quickmatchEnrolled = enrolled;
            
            // Save updated profile
            try (FileWriter writer = new FileWriter(profileFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(state, writer);
            }
            
            // Get the appropriate message based on language
            String message = enrolled 
                ? LanguageManager.getQuickmatchEnrollSuccess(state.language)
                : LanguageManager.getQuickmatchDeclineMessage(state.language);
            
            event.getHook().sendMessage(message).queue();
        } catch (Exception e) {
            System.err.println("Error updating quickmatch enrollment for user " + userId + ": " + e.getMessage());
            event.getHook().sendMessage("❌ Something went wrong. Please try again later.").queue();
        }
    }
}