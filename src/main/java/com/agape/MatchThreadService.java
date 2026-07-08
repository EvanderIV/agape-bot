package com.agape;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * Creates and announces Discord match threads for both match types:
 *
 *   Quickmatch ("QM"): created under the #quick-match channel after /quickmatch.
 *   Manual match ("MM"): created under a #matchmaking* channel after a
 *   matchmaker confirms a /match or compat-algo pairing.
 *
 * Each created thread is registered with {@link ThreadManager}, which owns the
 * 24-hour lifecycle (reminders, strikes, archiving). This class also sends the
 * "It's a Match!" congratulations DMs when a manual match is confirmed by both.
 */
public final class MatchThreadService {

    private static final String FONT_PATH = "assets/fonts/VAG Rounded Next Shine Regular.ttf";

    private MatchThreadService() {}

    /**
     * Creates a private thread under the "quick-match" / "quickmatch" channel for a matched pair,
     * adds both users and all cached matchmaker-role members, then sends the intro message with profile card attachments.
     * user1 is always the runner; user2 is always the matched person (receives the DM).
     */
    public static void createMatchThread(
            Guild guild,
            String user1Id, boolean user1IsMale, String user1Name, AppState user1Profile,
            String user2Id, boolean user2IsMale, String user2Name, AppState user2Profile,
            boolean isManualMatch) {

        // Find the appropriate parent channel
        TextChannel qmChannel = null;
        for (TextChannel ch : guild.getTextChannels()) {
            String normalized = ch.getName().toLowerCase().replace("-", "");
            boolean matches = isManualMatch
                ? normalized.startsWith("matchmaking")
                : normalized.equals("quickmatch");
            if (matches) {
                qmChannel = ch;
                break;
            }
        }
        if (qmChannel == null) {
            String expected = isManualMatch ? "'matchmaking' / 'match-making' / 'matchmaking-1'" : "'quick-match' or 'quickmatch'";
            System.err.println("Quickmatch: No " + expected + " channel found in guild " + guild.getId());
            return;
        }

        // Order names/profiles: male first, female second (fall back to runner+matched if same sex)
        final String maleId, maleName, femaleId, femaleName;
        final AppState maleProfile, femaleProfile;
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

        String threadName = maleName + " + " + femaleName + (isManualMatch ? " (Agape MM)" : " (Agape QM)");

        qmChannel.createThreadChannel(threadName, true)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
            .queue(thread -> {
                ThreadManager.registerThread(thread.getId(), guild.getId(), maleId, femaleId, isManualMatch ? "MANUAL" : "QUICKMATCH");

                thread.addThreadMemberById(maleId).queue();
                thread.addThreadMemberById(femaleId).queue();

                // Load full member list and add anyone with the matchmaker role
                guild.loadMembers().onSuccess(allMembers -> {
                    for (Member member : allMembers) {
                        if (Roles.isMatchmaker(member)) {
                            thread.addThreadMemberById(member.getId()).queue();
                        }
                    }
                });

                // Find a guidelines channel to reference in the intro message
                String guidelinesRef = "";
                TextChannel guidelinesCh = Channels.findByNormalizedName(
                    guild, "howitworks", "matchmakingguidelines", "matchmakingrules");
                if (guidelinesCh != null) {
                    guidelinesRef = "-# As always, please review the guidelines in <#" + guidelinesCh.getId() + ">.\n\n";
                }

                // Find a quickmatch rules channel to reference in the intro message
                String quickmatchRules = "";
                TextChannel qmRulesCh = Channels.findByNormalizedName(guild, "quickmatch", "quickmatchrules");
                if (qmRulesCh != null) {
                    quickmatchRules = "-# As always, please review the guidelines in <#" + qmRulesCh.getId() + ">.\n\n";
                }

                long closeTimestamp = java.time.Instant.now().getEpochSecond() + 86400L;
                final String message;
                if (isManualMatch) {
                    message = "## Match Found! (Main Match)\n\n"
                        + "In evaluating the match, you both should briefly discuss your top 3-5 dealbreakers in a partner *here in this thread*. Be realistic and only include the **dealbreakers/non-negotiables**.\n\n"
                        + "When you have finished discussing (should take <15 minutes), you must confirm or decline the match:\n\n"
                        + "`/confirm` - You think this match is a viable fit, and you're interested in pursuing it further. *(both parties must* /confirm *to match)*\n"
                        + "`/decline` - You think this match is strictly incompatible, and you are uninterested in pursuing this further. *(you will be required to explain your decision)*\n\n"
                        + "**Reminder that a match is NOT dating/courting/an exclusive relationship!**\n\n"
                        + guidelinesRef
                        + "-# This thread will automatically lock <t:" + closeTimestamp + ":R>.\n"
                        + "||<@" + maleId + "> <@" + femaleId + ">||";
                } else {
                    message = "## Quickmatch Found! Found! ⚡\n\n"
                        + "First, you both need to reach out to each other via direct message (DM). Once you have messaged each other, return here and type the `/confirm` command to make your match official.\n\n"
                        + "**Reminder that a match is NOT dating/courting/an exclusive relationship!**\n\n"
                        + quickmatchRules
                        + "-# This thread will automatically lock <t:" + closeTimestamp + ":R>.\n"
                        + "||<@" + maleId + "> <@" + femaleId + ">||";
                }

                // Generate profile cards on a background thread, then send the intro message with attachments
                new Thread(() -> {
                    List<FileUpload> uploads = new ArrayList<>();
                    List<File> toDelete = new ArrayList<>();

                    File card1 = generateProfileCardFile(maleId, maleProfile, guild);
                    File card2 = generateProfileCardFile(femaleId, femaleProfile, guild);

                    if (card1 != null) { uploads.add(FileUpload.fromData(card1, maleName + "_profile.png")); toDelete.add(card1); }
                    if (card2 != null) { uploads.add(FileUpload.fromData(card2, femaleName + "_profile.png")); toDelete.add(card2); }

                    MessageCreateAction msgAction = thread.sendMessage(message);
                    if (!uploads.isEmpty()) msgAction = msgAction.addFiles(uploads);
                    msgAction.queue(
                        s -> toDelete.forEach(File::delete),
                        err -> { toDelete.forEach(File::delete); System.err.println("Quickmatch: Failed to send intro message: " + err.getMessage()); }
                    );
                }, "qm-card-gen").start();

                // DM the matched person (user2) with a direct link to the thread
                guild.getJDA().openPrivateChannelById(user2Id).queue(dmChannel -> {
                    EmbedBuilder notifEmbed = new EmbedBuilder()
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
    private static File generateProfileCardFile(String userId, AppState profile, Guild guild) {
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
            return ImageGenerator.generateForUser(designPaths[0], pfpUri, designPaths[1], FONT_PATH, text, userId + "_qm_card", profile.photoFocusX, profile.photoFocusY);
        } catch (Exception e) {
            System.err.println("Quickmatch: Failed to generate profile card for " + userId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Sends the "Congratulations on the match!" DM (with feedback/report buttons)
     * to both members of a fully-confirmed manual match.
     */
    public static void sendManualMatchDMs(Guild guild, String maleId, String femaleId) {
        String guidelinesRef = "the guidelines";
        TextChannel guidelineCh = Channels.findByNameContaining(guild, "guideline");
        if (guidelineCh != null) guidelinesRef = "<#" + guidelineCh.getId() + ">";
        final String ref = guidelinesRef;

        for (String[] pair : new String[][]{{maleId, femaleId}, {femaleId, maleId}}) {
            final String userId    = pair[0];
            final String matchedId = pair[1];

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 Congratulations on the match!")
                .setColor(0xFF6699)
                .setDescription("Per the rules of engagement, you should remain in contact with your match via DMs. Love is commitment, not just a feeling.\n\n"
                    + "-# As always, remember to read the " + ref + ". Ghosting and abuse are strictly forbidden.")
                .setFooter("Agape Matchmaking • Your feedback helps us improve!");

            Button feedbackBtn = Button.primary("qm_feedback_" + userId + "_" + matchedId, "💬 Give Feedback");
            Button reportBtn   = Button.danger("qm_report_" + userId + "_" + matchedId, "🚩 Report  User Behavior");

            guild.getJDA().openPrivateChannelById(userId).queue(
                ch -> ch.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(feedbackBtn, reportBtn))
                        .queue(
                            s -> System.out.println("Match: Sent manual match congratulations DM to " + userId),
                            e -> System.err.println("Match: Failed to send DM to " + userId + ": " + e.getMessage())
                        ),
                e -> System.err.println("Match: Could not open DM for " + userId + ": " + e.getMessage())
            );
        }
    }

    /**
     * Adds the guild's "Matched" role to both users of a confirmed manual match.
     * Logs an error when the guild has no role containing "matched".
     */
    public static void assignMatchedRole(Guild guild, String maleId, String femaleId) {
        Role matchedRole = Roles.findRoleContaining(guild, "matched");
        if (matchedRole == null) {
            System.err.println("Match: No role containing 'matched' found in guild " + guild.getId());
            return;
        }
        for (String userId : new String[]{maleId, femaleId}) {
            final String uid = userId;
            guild.retrieveMemberById(uid).queue(
                m -> guild.addRoleToMember(m, matchedRole).queue(
                    v -> System.out.println("Match: Added 'Matched' role to " + uid),
                    e -> System.err.println("Match: Could not add role to " + uid + ": " + e.getMessage())
                ),
                e -> System.err.println("Match: Could not retrieve member " + uid + ": " + e.getMessage())
            );
        }
    }
}
