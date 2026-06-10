package com.agape;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

/**
 * Confirms users are still members of their guild before matchmaking
 * includes them, and soft-deletes the profiles of users who have left.
 */
public final class MembershipVerifier {

    private MembershipVerifier() {}

    /**
     * Confirms a user is still a member of their guild before including them in any matchmaking operation.
     * Uses the GUILD_MEMBERS cache as a fast path; falls back to the Discord API only when not cached.
     * If the user has left, their profile is soft-deleted automatically and false is returned.
     * Returns true whenever membership cannot be determined (missing guildId, API errors, etc.) so that
     * a network hiccup never accidentally soft-deletes an active user.
     */
    public static boolean verifyMembership(String userId, String guildId, JDA jda) {
        if (guildId == null || jda == null) return true;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return true;

        // Fast path: GUILD_MEMBERS intent keeps the cache current
        if (guild.getMemberById(userId) != null) return true;

        // Slow path: not in cache — confirm with the API before acting
        try {
            guild.retrieveMemberById(userId).complete();
            return true;
        } catch (ErrorResponseException e) {
            // 10007 = Unknown Member  |  10013 = Unknown User
            if (e.getErrorCode() == 10007 || e.getErrorCode() == 10013) {
                softDeleteAbsentMember(userId);
                return false;
            }
            // Any other code is an API or permissions issue — do not soft-delete
            System.err.println("Membership check: API error for " + userId
                + " (code " + e.getErrorCode() + "): " + e.getMessage());
            return true;
        } catch (Exception e) {
            System.err.println("Membership check: Unexpected error for " + userId + ": " + e.getMessage());
            return true;
        }
    }

    private static void softDeleteAbsentMember(String userId) {
        AppState state = ProfileRepository.load(userId);
        if (state == null || state.softDeleted) return;
        state.softDeleted = true;
        ProfileRepository.save(userId, state);
        System.out.println("Membership check: User " + userId + " has left the server — profile soft-deleted.");
    }
}
