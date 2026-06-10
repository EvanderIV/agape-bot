package com.agape;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * The anonymous matchmaker ↔ applicant conversation relay.
 *
 * A matchmaker starts a conversation with /admin-message. The applicant gets
 * a DM with a Reply button; their replies are posted into the matchmaker
 * channel with another Reply button, and so on. Both sides interact only
 * through these buttons/modals — they never see each other's identity (the
 * applicant only sees "Matchmaker"). Message history is persisted by
 * {@link MessagingHandler}.
 *
 * Component ID formats:
 *   convo_reply_{applicantId}_{matchmakerId}        — DM button for applicants
 *   convo_reply_mm_{matchmakerId}_{applicantId}     — channel button for matchmakers
 *   modal_convo_reply_... / modal_convo_reply_mm_...— the matching modals
 */
public final class ConversationRelay {

    private ConversationRelay() {}

    // ─── Buttons ──────────────────────────────────────────────────────────────

    /** Handles the reply button click (either side) — shows the reply modal. */
    public static void handleReplyButton(ButtonInteractionEvent event) {
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

    // ─── Modals ───────────────────────────────────────────────────────────────

    /** Handles a matchmaker's reply modal (modal_convo_reply_mm_...). */
    public static void handleMatchmakerReplyModal(ModalInteractionEvent event) {
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
        postReplyToChannel(applicantId, matchmakerId, replyContent, event.getJDA(), "matchmaker");

        // Send reply to applicant via DM
        sendReplyToApplicant(applicantId, matchmakerId, replyContent, event.getJDA());

        event.getHook().sendMessage("✅ Your reply has been sent to the applicant.").queue();
    }

    /** Handles an applicant's reply modal (modal_convo_reply_...). */
    public static void handleApplicantReplyModal(ModalInteractionEvent event) {
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
        postReplyToChannel(applicantId, matchmakerId, replyContent, event.getJDA(), "applicant");

        event.getHook().sendMessage("✅ Your reply has been sent to the matchmaker.").queue();
    }

    // ─── Channel + DM delivery ────────────────────────────────────────────────

    /** Posts a conversation start message to the applications channel. */
    public static void postConversationStartToChannel(User applicant, String messageContent, String matchmakerId, String guildId, JDA jda) {
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + guildId);
                return;
            }

            TextChannel channel = Channels.findMatchmakerChannel(guild);
            if (channel == null) {
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
            channel.sendMessageEmbeds(embed.build()).queue(
                msg -> System.out.println("✅ Conversation message posted to channel"),
                err -> System.err.println("❌ Failed to post conversation message: " + err.getMessage())
            );
        } catch (Exception e) {
            System.err.println("❌ Error posting conversation to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Posts a conversation reply to the applications channel. */
    private static void postReplyToChannel(String applicantId, String matchmakerId, String replyContent, JDA jda, String sender) {
        try {
            // Try to get the guild ID from the applicant's profile first
            String guildId = null;
            AppState applicantProfile = ProfileRepository.load(applicantId);
            if (applicantProfile != null) {
                guildId = applicantProfile.guildId;
            }

            // Fall back to the guild ID stored when the conversation was initiated
            if (guildId == null) {
                guildId = MessagingHandler.getConversationGuildId(applicantId, matchmakerId);
            }

            if (guildId == null) {
                System.err.println("❌ Could not determine guild ID for conversation: " + applicantId + " <-> " + matchmakerId);
                return;
            }

            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                System.err.println("❌ Guild not found for ID: " + guildId);
                return;
            }

            TextChannel channel = Channels.findMatchmakerChannel(guild);
            if (channel == null) {
                System.err.println("⚠️ No applications channel found in guild: " + guild.getName());
                return;
            }

            // Create embed for reply
            String senderLabel = "applicant".equals(sender) ? "Applicant Reply" : "Matchmaker Reply";
            int embedColor = "applicant".equals(sender) ? 0x99FF99 : 0xFF9999;

            // Ping the matchmaker in message content (not embed) so Discord delivers the notification
            String mention = "applicant".equals(sender) ? "<@" + matchmakerId + ">" : "";

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(senderLabel)
                    .setColor(embedColor)
                    .setDescription(replyContent)
                    .setFooter("Applicant ID: " + applicantId)
                    .setTimestamp(java.time.Instant.now());

            // Send to channel
            if ("applicant".equals(sender)) {
                Button replyBtn = Button.primary("convo_reply_mm_" + matchmakerId + "_" + applicantId, "💬 Reply");
                channel.sendMessage(mention).setEmbeds(embed.build())
                        .setComponents(ActionRow.of(replyBtn))
                        .queue(
                            msg -> System.out.println("✅ Applicant reply posted to channel"),
                            err -> System.err.println("❌ Failed to post applicant reply: " + err.getMessage())
                        );
            } else {
                channel.sendMessageEmbeds(embed.build())
                        .queue(
                            msg -> System.out.println("✅ Matchmaker reply posted to channel"),
                            err -> System.err.println("❌ Failed to post matchmaker reply: " + err.getMessage())
                        );
            }
        } catch (Exception e) {
            System.err.println("❌ Error posting conversation reply to channel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Sends a matchmaker's reply to the applicant via DM (with a fresh Reply button). */
    private static void sendReplyToApplicant(String applicantId, String matchmakerId, String replyContent, JDA jda) {
        try {
            User applicant = jda.retrieveUserById(applicantId).complete();
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

                dm.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(replyBtn))
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

    /** Edits the original DM message to include the applicant's reply and removes its button. */
    private static void editDMWithApplicantReply(String applicantId, String matchmakerId, String replyContent, JDA jda) {
        try {
            User applicant = jda.retrieveUserById(applicantId).complete();
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
}
