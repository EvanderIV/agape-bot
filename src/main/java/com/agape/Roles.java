package com.agape;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * All Discord role-name conventions in one place.
 *
 * The bot identifies staff and member status by case-insensitive substring
 * checks on role names (the server can rename roles freely as long as the
 * keyword survives):
 *   - "matchmaker"  → staff who review applications and run matches
 *   - "admin"       → server administrators (also via ADMINISTRATOR permission)
 *   - "single"      → eligible to apply (but not "not single" / "single but...")
 *   - "matched"     → currently in a confirmed match
 *   - "booster" or "lvl N" (N ≥ 100) → premium quickmatch perks
 */
public final class Roles {

    private static final Pattern LEVEL_PATTERN =
        Pattern.compile("lvl\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private Roles() {}

    /** True if the member has a role whose name contains "matchmaker". */
    public static boolean isMatchmaker(Member member) {
        return hasRoleContaining(member, "matchmaker");
    }

    /** True for ADMINISTRATOR permission, an "admin" role, or a matchmaker role. */
    public static boolean isMatchmakerOrAdmin(Member member) {
        if (member == null) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        if (hasRoleContaining(member, "admin")) return true;
        return isMatchmaker(member);
    }

    /** True if the member is marked single ("single" role, excluding "not single"/"single but..."). */
    public static boolean isSingle(Member member) {
        if (member == null) return false;
        for (Role role : member.getRoles()) {
            String name = role.getName().toLowerCase();
            if (name.contains("single") && !name.contains("not") && !name.contains("but")) {
                return true;
            }
        }
        return false;
    }

    /** True if the member has a role whose name contains "matched". */
    public static boolean isMatched(Member member) {
        return hasRoleContaining(member, "matched");
    }

    /**
     * True if the member qualifies for premium quickmatch spins:
     * a "booster" role, or a "lvl N" role with N ≥ 100.
     */
    public static boolean isPremium(Member member) {
        if (member == null) return false;
        for (Role role : member.getRoles()) {
            String name = role.getName();
            if (name.toLowerCase().contains("booster")) return true;
            Matcher m = LEVEL_PATTERN.matcher(name);
            if (m.find() && Integer.parseInt(m.group(1)) >= 100) return true;
        }
        return false;
    }

    /** Removes the "Matchmaking|Not enrolled" role from a guild member. */
    public static void removeNotEnrolledRole(Guild guild, String userId) {
        Role role = findRoleContaining(guild, "not enrolled");
        if (role == null) return;
        guild.retrieveMemberById(userId).queue(
            m -> guild.removeRoleFromMember(m, role).queue(
                v -> System.out.println("Roles: Removed 'not enrolled' role from " + userId),
                e -> System.err.println("Roles: Could not remove 'not enrolled' role from " + userId + ": " + e.getMessage())
            ),
            e -> System.err.println("Roles: Could not retrieve member " + userId + " to remove role: " + e.getMessage())
        );
    }

    /** Adds the "Matchmaking|Not enrolled" role back to a guild member. */
    public static void addNotEnrolledRole(Guild guild, String userId) {
        Role role = findRoleContaining(guild, "not enrolled");
        if (role == null) return;
        guild.retrieveMemberById(userId).queue(
            m -> guild.addRoleToMember(m, role).queue(
                v -> System.out.println("Roles: Added 'not enrolled' role to " + userId),
                e -> System.err.println("Roles: Could not add 'not enrolled' role to " + userId + ": " + e.getMessage())
            ),
            e -> System.err.println("Roles: Could not retrieve member " + userId + " to add role: " + e.getMessage())
        );
    }

    /** First guild role whose name contains the keyword (case-insensitive), or null. */
    public static Role findRoleContaining(Guild guild, String keyword) {
        String lower = keyword.toLowerCase();
        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase().contains(lower)) return role;
        }
        return null;
    }

    private static boolean hasRoleContaining(Member member, String keyword) {
        if (member == null) return false;
        for (Role role : member.getRoles()) {
            if (role.getName().toLowerCase().contains(keyword)) return true;
        }
        return false;
    }
}
