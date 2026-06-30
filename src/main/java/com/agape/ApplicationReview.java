package com.agape;

import java.io.File;
import java.util.Collections;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * The matchmaker review workflow for submitted applications.
 *
 * When an application is submitted, {@link #postApplicationToChannel} posts
 * its full embed to the matchmaker channel with four buttons:
 *   app_accept_{userId}                — accept the profile
 *   app_reject_{userId}                — reject the profile
 *   app_request_change_{userId}        — request edits to a numbered section (modal)
 *   app_request_photo_change_{userId}  — request a new photo (modal)
 *
 * Accept/reject update the profile's status on disk and DM the applicant.
 * Change requests set status CHANGES_REQUESTED and DM the applicant with
 * edit/delete buttons handled by {@link ApplicationHandler}.
 */
public final class ApplicationReview {

    private ApplicationReview() {}

    /** Human-readable name for an application section number (1–14). */
    public static String sectionName(int sectionNum) {
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
            case 14: return "Photo";
            default: return "Unknown Section";
        }
    }

    /**
     * Posts the submitted application to the guild's applications channel with review buttons.
     */
    public static void postApplicationToChannel(AppState state, String userId, JDA jda) {
        if (state.guildId == null) {
            System.out.println("⚠️ No guild ID found for application - skipping channel post");
            return;
        }

        try {
            Guild guild = jda.getGuildById(state.guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + state.guildId);
                return;
            }

            TextChannel channel = Channels.findMatchmakerChannel(guild);
            if (channel == null) {
                System.err.println("⚠️ No 'applications' or 'pending-applications' channel found in guild: " + guild.getName());
                return;
            }

            // Determine photo display
            boolean isLocalPhoto = state.photoPath != null && !state.photoPath.startsWith("http");
            String photoDesc;
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 New Application: " + state.name)
                    .setColor(state.sex ? 0xFF6699 : 0x9966FF)
                    .addField("1. Name", state.name + " (<@" + userId + ">)", true)
                    .addField("2. Birthday", state.birthday != null ? state.birthday + " (age " + AgeUtils.calculateAge(state.birthday) + ")" : "N/A", true)
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

            if (state.photoPath == null || state.photoPath.isEmpty()) {
                photoDesc = "N/A";
            } else if (isLocalPhoto) {
                photoDesc = "(Uploaded file — see image below)";
                embed.setImage("attachment://photo.png");
            } else {
                photoDesc = state.photoPath;
                embed.setImage(state.photoPath);
            }
            embed.addField("14. Photo", photoDesc, false);

            // Create action buttons
            Button acceptBtn = Button.success("app_accept_" + userId, "✅ Accept");
            Button requestChangeBtn = Button.secondary("app_request_change_" + userId, "⚠️ Request Change");
            Button requestPhotoChangeBtn = Button.primary("app_request_photo_change_" + userId, "📷 Request Photo Change");
            Button rejectBtn = Button.danger("app_reject_" + userId, "❌ Reject");

            ActionRow actionRow = ActionRow.of(acceptBtn, requestChangeBtn, requestPhotoChangeBtn, rejectBtn);

            File photoFile = isLocalPhoto ? new File(state.photoPath) : null;

            if (photoFile != null && photoFile.exists()) {
                channel.sendMessageEmbeds(embed.build())
                        .addFiles(FileUpload.fromData(photoFile, "photo.png"))
                        .setComponents(actionRow)
                        .queue(
                            msg -> System.out.println("✅ Application posted to channel"),
                            err -> System.err.println("❌ Failed to post application: " + err.getMessage())
                        );
            } else {
                channel.sendMessageEmbeds(embed.build())
                        .setComponents(actionRow)
                        .queue(
                            msg -> System.out.println("✅ Application posted to channel"),
                            err -> System.err.println("❌ Failed to post application: " + err.getMessage())
                        );
            }

        } catch (Exception e) {
            System.err.println("❌ Error posting application to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles the Accept/Reject/Request Change buttons pressed by matchmakers.
     */
    public static void handleReviewButton(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        // Extract the userId from the button ID (e.g., "app_reject_123456789")
        String targetUserId = buttonId.substring(buttonId.lastIndexOf("_") + 1);

        if (buttonId.startsWith("app_request_photo_change_")) {
            TextInput reason = TextInput.create("reason", "Reason for Photo Change", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Why does the photo need to be changed?")
                .setRequired(true)
                .build();

            Modal modal = Modal.create("modal_request_photo_change_" + targetUserId, "Request Photo Change")
                .addActionRow(reason)
                .build();

            event.replyModal(modal).queue();
        } else if (buttonId.startsWith("app_request_change_")) {
            // 1. Send Modal for Request Change (This acts as the interaction ACK!)
            TextInput reason = TextInput.create("reason", "Reason for Change", TextInputStyle.PARAGRAPH)
                .setPlaceholder("What needs to be changed in their application?")
                .setRequired(true)
                .build();

            TextInput section = TextInput.create("section_number", "Section Number (1-14)", TextInputStyle.SHORT)
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
            AppState state = ProfileRepository.load(targetUserId);
            if (state != null) {
                state.status = newStatus;
                ProfileRepository.save(targetUserId, state);
                System.out.println("✅ Profile status updated to " + newStatus + " for user " + targetUserId);
                if ("ACCEPTED".equals(newStatus)) {
                    UserInsightsManager.processProfile(targetUserId);
                    Guild guild = event.getGuild();
                    if (guild != null) Roles.removeNotEnrolledRole(guild, targetUserId);
                }
            }

            // Now safely fetch user using the API (bypassing local cache) and send DM
            event.getJDA().retrieveUserById(targetUserId).queue(user -> {
                user.openPrivateChannel().queue(channel -> {
                    if (buttonId.startsWith("app_accept_")) {
                        channel.sendMessage("🎉 Good news! Your matchmaking application has been **ACCEPTED**!\n\n"
                            + "Now that your profile has been accepted and posted in our board, you can now participate "
                            + "in the server chats. The more active you are, the more quickly you will get a match. "
                            + "Open a Matchmaking ticket for any questions related to matchmaking.").queue();
                        event.getHook().sendMessage("✅ Accepted application for " + user.getName()).queue();
                    } else if (buttonId.startsWith("app_reject_")) {
                        channel.sendMessage("❌ We're sorry, but your matchmaking application has been **REJECTED**.").queue();
                        event.getHook().sendMessage("❌ Rejected application for " + user.getName()).queue();
                    }
                }, error -> {
                    event.getHook().sendMessage("⚠️ Processed, but could not send a DM to user ID " + targetUserId + " (DMs closed).").queue();
                });

                // On acceptance, auto-post the rendered profile card to the display board
                // (independent of DM delivery). No-op + console log if the channel is absent.
                if (buttonId.startsWith("app_accept_")) {
                    postToDisplayBoard(event.getGuild(), targetUserId, user.getEffectiveAvatarUrl());
                }
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not find user with ID " + targetUserId + " from Discord API.").queue();
            });
        }
    }

    /**
     * On acceptance, render the applicant's profile card and post it to the
     * "display-board" channel. If no such channel exists, the event is simply
     * logged to the console. Image generation runs on a background thread (it can
     * exceed Discord's 3-second interaction window), per the bot's convention of
     * keeping long work off the JDA event loop.
     *
     * @param guild             the guild the application was accepted in
     * @param userId            the accepted applicant's Discord ID
     * @param fallbackAvatarUrl avatar URL used only if the profile has no photo
     */
    private static void postToDisplayBoard(Guild guild, String userId, String fallbackAvatarUrl) {
        if (guild == null) return;

        TextChannel board = Channels.findByNormalizedName(guild, "displayboard");
        if (board == null) {
            System.out.println("ApplicationReview: No 'display-board' channel found in " + guild.getName()
                + " — skipping auto-post of accepted profile " + userId + ".");
            return;
        }

        AppState state = ProfileRepository.load(userId);
        if (state == null) {
            System.err.println("ApplicationReview: Cannot post profile " + userId
                + " to display-board — profile not found on disk.");
            return;
        }

        new Thread(() -> {
            File card = ImageGenerator.generateProfileCard(state, userId, fallbackAvatarUrl, ImageGenerator.DEFAULT_FONT_PATH);
            if (card == null || !card.exists()) {
                System.err.println("ApplicationReview: Failed to render display-board image for " + userId + ".");
                return;
            }
            board.sendMessage("𝒩𝑒𝓌 𝓅𝓇𝑜𝒻𝒾𝓁𝑒 𝓅𝑜𝓈𝓉𝑒𝒹~")
                .addFiles(FileUpload.fromData(card))
                .queue(
                    ok -> card.delete(),
                    err -> {
                        System.err.println("ApplicationReview: Could not post profile " + userId
                            + " to display-board: " + err.getMessage());
                        card.delete();
                    });
        }, "displayboard-post-" + userId).start();
    }

    /** Handles the "Request Application Change" modal (modal_request_change_{userId}). */
    public static void handleRequestChangeModal(ModalInteractionEvent event) {
        String targetUserId = event.getModalId().substring(event.getModalId().lastIndexOf("_") + 1);
        String reason = event.getValue("reason").getAsString();
        String sectionStr = event.getValue("section_number").getAsString().trim();

        int sectionNum;
        try {
            sectionNum = Integer.parseInt(sectionStr);
            if (sectionNum < 1 || sectionNum > 14) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid section number. Please provide a number between 1 and 14.").setEphemeral(true).queue();
            return;
        }

        // Defer the edit to ACK the modal, then remove the buttons from the original message!
        event.deferEdit().queue();
        event.getHook().editOriginalComponents(Collections.emptyList()).queue();

        // Update user profile status
        AppState state = ProfileRepository.load(targetUserId);
        if (state != null) {
            state.status = "CHANGES_REQUESTED";
            ProfileRepository.save(targetUserId, state);
        }

        final int section = sectionNum;
        // Guaranteed API fetch to prevent the null pointer cache errors
        event.getJDA().retrieveUserById(targetUserId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                Button editBtn = Button.primary("user_edit_app_" + section, "✏️ Edit Application");
                Button deleteBtn = Button.danger("user_delete_app", "🗑️ Delete Application");

                channel.sendMessage("⚠️ A matchmaker has requested changes to your application. Please review the feedback and update the requested section.\n\n**Matchmaker Note:** " + reason + "\n**Section to Edit:** #" + section + " (" + sectionName(section) + ")")
                       .setComponents(ActionRow.of(editBtn, deleteBtn))
                       .queue();

                event.getHook().sendMessage("⚠️ Requested changes from " + user.getName() + " for reason: " + reason + " (Section #" + section + " - " + sectionName(section) + ")").queue();
            }, error -> {
                event.getHook().sendMessage("⚠️ Processed change request, but could not send a DM to user ID " + targetUserId + " (DMs closed).").queue();
            });
        }, error -> {
            event.getHook().sendMessage("❌ Error: Could not find user with ID " + targetUserId + " from Discord API.").queue();
        });
    }

    /** Handles the "Request Photo Change" modal (modal_request_photo_change_{userId}). */
    public static void handlePhotoChangeModal(ModalInteractionEvent event) {
        String targetUserId = event.getModalId().substring("modal_request_photo_change_".length());
        String reason = event.getValue("reason").getAsString();

        event.deferEdit().queue();
        event.getHook().editOriginalComponents(Collections.emptyList()).queue();

        AppState pState = ProfileRepository.load(targetUserId);
        if (pState != null) {
            pState.status = "CHANGES_REQUESTED";
            ProfileRepository.save(targetUserId, pState);
        }

        event.getJDA().retrieveUserById(targetUserId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                Button editBtn = Button.primary("user_edit_app_14", "📷 Re-upload Photo");
                Button deleteBtn = Button.danger("user_delete_app", "🗑️ Delete Application");

                channel.sendMessage("⚠️ A matchmaker has requested a change to your **profile photo**.\n\n**Matchmaker Note:** " + reason + "\n\nPlease re-upload your photo using the button below.")
                       .setComponents(ActionRow.of(editBtn, deleteBtn))
                       .queue();

                event.getHook().sendMessage("📷 Requested photo change from " + user.getName() + " for reason: " + reason).queue();
            }, error -> {
                event.getHook().sendMessage("⚠️ Processed photo change request, but could not DM user ID " + targetUserId + " (DMs closed).").queue();
            });
        }, error -> {
            event.getHook().sendMessage("❌ Error: Could not find user with ID " + targetUserId + " from Discord API.").queue();
        });
    }
}
