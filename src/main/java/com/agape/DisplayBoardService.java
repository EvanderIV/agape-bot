package com.agape;

import java.io.File;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * Owns the "display-board" channel — the public gallery of accepted profile cards.
 *
 * <p>Each accepted profile has at most one board message, and its ID is stored on
 * the profile itself ({@link AppState#displayBoardMessageId}). That lets the board
 * stay in sync with a profile's opt-in state: when a matchmaker opts a user out
 * with {@code /set-opt … false} the message is deleted; when they opt back in it is
 * reposted (without the "new profile" caption) and the fresh message ID is saved
 * back to the same field.
 *
 * <p>Rendering runs off the JDA event loop, per the bot's convention.
 */
public final class DisplayBoardService {

    private DisplayBoardService() {}

    /** Caption used only when a brand-new profile is first accepted. */
    public static final String NEW_PROFILE_CAPTION = "𝒩𝑒𝓌 𝓅𝓇𝑜𝒻𝒾𝓁𝑒 𝓅𝑜𝓈𝓉𝑒𝒹~";

    /**
     * Renders the user's profile card and posts it to the display board, saving the
     * resulting message ID onto their profile. When {@code caption} is null or empty
     * only the image is posted (used for opt-in reposts); otherwise the caption
     * leads the message (used for first-time acceptance).
     *
     * <p>No-op (with a console log) when the channel or profile is missing.
     *
     * @param guild             the guild whose display board to post in
     * @param userId            the accepted applicant's Discord ID
     * @param fallbackAvatarUrl avatar URL used only if the profile has no photo
     * @param caption           leading text, or null/empty for image-only
     */
    public static void post(Guild guild, String userId, String fallbackAvatarUrl, String caption) {
        if (guild == null) return;

        TextChannel board = Channels.findByNormalizedName(guild, "displayboard");
        if (board == null) {
            System.out.println("DisplayBoardService: No 'display-board' channel in " + guild.getName()
                + " — skipping post for " + userId + ".");
            return;
        }

        AppState state = ProfileRepository.load(userId);
        if (state == null) {
            System.err.println("DisplayBoardService: No profile for " + userId + " — cannot post to display board.");
            return;
        }

        new Thread(() -> {
            File card = ImageGenerator.generateProfileCard(state, userId, fallbackAvatarUrl, ImageGenerator.DEFAULT_FONT_PATH);
            if (card == null || !card.exists()) {
                System.err.println("DisplayBoardService: Failed to render card for " + userId + ".");
                return;
            }

            MessageCreateAction action = (caption == null || caption.isEmpty())
                ? board.sendFiles(FileUpload.fromData(card))
                : board.sendMessage(caption).addFiles(FileUpload.fromData(card));

            action.queue(
                msg -> {
                    card.delete();
                    // Persist the message ID against the freshest profile snapshot.
                    AppState fresh = ProfileRepository.load(userId);
                    if (fresh != null) {
                        fresh.displayBoardMessageId = msg.getId();
                        ProfileRepository.save(userId, fresh);
                    }
                    System.out.println("DisplayBoardService: Posted profile " + userId
                        + " to display board (msg " + msg.getId() + ").");
                },
                err -> {
                    System.err.println("DisplayBoardService: Could not post " + userId
                        + " to display board: " + err.getMessage());
                    card.delete();
                });
        }, "displayboard-post-" + userId).start();
    }

    /**
     * Deletes the user's stored display-board message (if any) and clears the stored
     * ID. No-op if there is no channel, profile, or previously stored message. The ID
     * is cleared regardless of whether the delete succeeds, since the message may
     * already be gone.
     */
    public static void remove(Guild guild, String userId) {
        if (guild == null) return;

        AppState state = ProfileRepository.load(userId);
        if (state == null || state.displayBoardMessageId == null || state.displayBoardMessageId.isEmpty()) return;

        TextChannel board = Channels.findByNormalizedName(guild, "displayboard");
        if (board == null) {
            System.out.println("DisplayBoardService: No 'display-board' channel in " + guild.getName()
                + " — cannot remove message for " + userId + ".");
            return;
        }

        String msgId = state.displayBoardMessageId;
        board.deleteMessageById(msgId).queue(
            ok  -> System.out.println("DisplayBoardService: Removed display-board message " + msgId + " for " + userId + "."),
            err -> System.err.println("DisplayBoardService: Could not delete display-board message " + msgId
                + " for " + userId + ": " + err.getMessage())
        );

        state.displayBoardMessageId = null;
        ProfileRepository.save(userId, state);
    }
}
