package com.agape;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * Builds the manual-match preview embed a matchmaker sees before confirming
 * a /match (or the "Matchmake" button on a compat-algo breakdown).
 *
 * The preview validates both profiles, shows the compatibility score, and
 * surfaces warnings (extreme distance, deal-breaker conflicts, doctrinal
 * conflicts) with Confirm/Cancel buttons. The actual thread creation happens
 * later in {@link MatchThreadService} when the confirm button is pressed.
 */
public final class MatchPreviewService {

    private MatchPreviewService() {}

    /** Loads both profiles, validates eligibility, and sends the preview embed to the hook. */
    public static void sendMatchPreview(String uid1, String uid2, InteractionHook hook) {
        new Thread(() -> {
            AppState p1 = ProfileRepository.load(uid1);
            AppState p2 = ProfileRepository.load(uid2);

            if (p1 == null) { hook.sendMessage("❌ No profile found for <@" + uid1 + ">.").queue(); return; }
            if (p2 == null) { hook.sendMessage("❌ No profile found for <@" + uid2 + ">.").queue(); return; }
            if (!"ACCEPTED".equals(p1.status)) { hook.sendMessage("❌ <@" + uid1 + ">'s profile is not accepted (status: " + p1.status + ").").queue(); return; }
            if (!"ACCEPTED".equals(p2.status)) { hook.sendMessage("❌ <@" + uid2 + ">'s profile is not accepted (status: " + p2.status + ").").queue(); return; }
            if (p1.softDeleted) { hook.sendMessage("❌ <@" + uid1 + ">'s profile is soft-deleted and cannot be matched.").queue(); return; }
            if (p2.softDeleted) { hook.sendMessage("❌ <@" + uid2 + ">'s profile is soft-deleted and cannot be matched.").queue(); return; }
            if (!p1.manualMatchEnrolled) { hook.sendMessage("❌ <@" + uid1 + "> is not enrolled in manual matchmaking.").queue(); return; }
            if (!p2.manualMatchEnrolled) { hook.sendMessage("❌ <@" + uid2 + "> is not enrolled in manual matchmaking.").queue(); return; }
            if (p1.sex == p2.sex) { hook.sendMessage("❌ Both users are the same sex — this server only supports opposite-sex matches.").queue(); return; }

            CompatibilityEngine.ScoreDetail denom  = CompatibilityEngine.scoreDenomination(p1, p2);
            CompatibilityEngine.ScoreDetail age    = CompatibilityEngine.scoreAge(p1, p2);
            CompatibilityEngine.ScoreDetail dist   = CompatibilityEngine.scoreDistance(p1, p2);
            CompatibilityEngine.ScoreDetail values = CompatibilityEngine.scoreValues(p1, p2);
            CompatibilityEngine.ScoreDetail db     = CompatibilityEngine.scoreDealBreakers(p1, p2);
            int total = denom.score + age.score + dist.score + values.score + db.score;

            String name1 = p1.name != null ? p1.name : uid1;
            String name2 = p2.name != null ? p2.name : uid2;

            List<String> warnings = new ArrayList<>();
            if (dist.score <= -10) {
                warnings.add("**Extreme Distance** — These users appear to be on opposite sides of the globe. "
                    + "The time zone gap will likely make it very hard for them to find mutual availability.");
            }
            if (db.score < 0) {
                warnings.add("**Flagged Deal Breakers** — The compatibility check detected one or more potential "
                    + "deal breaker conflicts between these users' profiles. Review the details before proceeding.");
            }
            List<DenominationCompatibility.DoctrinalConflict> doctrinalConflicts =
                DenominationCompatibility.getDoctrinalConflicts(p1.sect, p2.sect);
            if (!doctrinalConflicts.isEmpty()) {
                StringBuilder dcMsg = new StringBuilder(
                    "**Doctrinal Conflicts** — Significant theological incompatibilities were found"
                    + " between " + name1 + "'s and " + name2 + "'s denominations:\n");
                for (DenominationCompatibility.DoctrinalConflict dc : doctrinalConflicts) {
                    dcMsg.append("• **").append(dc.issue).append("** — ").append(dc.description).append("\n");
                }
                warnings.add(dcMsg.toString().trim());
            }

            String scoreLine = "🙏 " + denom.score + "   👶 " + age.score + "   🌍 " + dist.score
                + "   💛 " + values.score + "   🚩 " + db.score;

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💘 Match Preview: " + name1 + " & " + name2)
                .setDescription("<@" + uid1 + "> × <@" + uid2 + ">")
                .setColor(warnings.isEmpty() ? 0xFF6699 : 0xFF8800)
                .addField("Compatibility Score", "**" + total + " / " + CompatibilityEngine.MAX_TOTAL + "**\n" + scoreLine, false);

            if (!warnings.isEmpty()) {
                StringBuilder wb = new StringBuilder();
                for (String w : warnings) wb.append("⚠️  ").append(w).append("\n\n");
                embed.addField("━━━━━━━━━━━━━━━━━━\n⚠️  WARNINGS  ⚠️\n━━━━━━━━━━━━━━━━━━", wb.toString().trim(), false);
            }
            embed.setTimestamp(java.time.Instant.now());

            String confirmLabel = warnings.isEmpty() ? "Continue" : "I understand, continue anyway";
            Button confirmBtn = Button.success("match_confirm_" + uid1 + "_" + uid2, confirmLabel);
            Button cancelBtn  = Button.danger("match_cancel_" + uid1 + "_" + uid2, "Cancel");

            // Render both candidates' profile cards to attach alongside the breakdown.
            File card1 = ImageGenerator.generateProfileCard(p1, uid1, null, ImageGenerator.DEFAULT_FONT_PATH);
            File card2 = ImageGenerator.generateProfileCard(p2, uid2, null, ImageGenerator.DEFAULT_FONT_PATH);
            final File c1 = card1, c2 = card2;

            List<FileUpload> cards = new ArrayList<>();
            if (card1 != null && card1.exists()) cards.add(FileUpload.fromData(card1, uid1 + "_card.png"));
            if (card2 != null && card2.exists()) cards.add(FileUpload.fromData(card2, uid2 + "_card.png"));

            WebhookMessageCreateAction<Message> action = hook.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(confirmBtn, cancelBtn));
            if (!cards.isEmpty()) action = action.addFiles(cards);

            action.queue(
                ok  -> { if (c1 != null) c1.delete(); if (c2 != null) c2.delete(); },
                err -> {
                    System.err.println("MatchPreviewService: Failed to send preview for " + uid1 + " & " + uid2 + ": " + err.getMessage());
                    if (c1 != null) c1.delete();
                    if (c2 != null) c2.delete();
                });

        }, "match-preview").start();
    }
}
