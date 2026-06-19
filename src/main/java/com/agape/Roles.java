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

    /** Removes the "Matchmaking|Not enrolled" role from a guild member (retrieves them first). */
    public static void removeNotEnrolledRole(Guild guild, String userId) {
        guild.retrieveMemberById(userId).queue(
            m -> removeNotEnrolledRole(guild, m),
            e -> System.err.println("Roles: Could not retrieve member " + userId + " to remove role: " + e.getMessage())
        );
    }

    /**
     * Removes the "not enrolled" role from an already-resolved member, but only
     * if they actually have it — avoiding a pointless REST write per user.
     * Returns true if a removal was issued.
     */
    public static boolean removeNotEnrolledRole(Guild guild, Member member) {
        Role role = findRoleContaining(guild, "not enrolled");
        if (role == null || !member.getRoles().contains(role)) return false;
        guild.removeRoleFromMember(member, role).queue(
            v -> System.out.println("Roles: Removed 'not enrolled' role from " + member.getId()),
            e -> System.err.println("Roles: Could not remove 'not enrolled' role from " + member.getId() + ": " + e.getMessage())
        );
        return true;
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

    /**
     * Ensures the member has a gender role matching their profile sex.
     *
     * <p>If the member already has a role containing "brother" or "sister",
     * nothing changes (we never override a gender they may have set themselves).
     * Otherwise the matching role — "Sister" for {@code isFemale}, else
     * "Brother" — is added. No-op if that role does not exist in the guild.
     *
     * <p>Gender roles gate self-service tagging, so a member missing one can't
     * apply tags; this backfills it from {@code AppState.sex}.
     */
    public static void ensureGenderRole(Guild guild, String userId, boolean isFemale) {
        guild.retrieveMemberById(userId).queue(
            m -> ensureGenderRole(guild, m, isFemale),
            e -> System.err.println("Roles: Could not retrieve member " + userId + " to assign gender role: " + e.getMessage())
        );
    }

    /**
     * Ensures an already-resolved member has the gender role matching their
     * profile sex, only adding when they have neither "Brother" nor "Sister".
     * Returns true if a role was added.
     */
    public static boolean ensureGenderRole(Guild guild, Member member, boolean isFemale) {
        for (Role r : member.getRoles()) {
            String n = r.getName().toLowerCase();
            if (n.contains("brother") || n.contains("sister")) return false; // already has one
        }
        String keyword = isFemale ? "sister" : "brother";
        Role target = findRoleContaining(guild, keyword);
        if (target == null) {
            System.err.println("Roles: No '" + keyword + "' role found in guild " + guild.getName()
                + " — cannot assign gender role to " + member.getId());
            return false;
        }
        guild.addRoleToMember(member, target).queue(
            v -> System.out.println("Roles: Added '" + target.getName() + "' role to " + member.getId()),
            e -> System.err.println("Roles: Could not add '" + target.getName() + "' role to " + member.getId() + ": " + e.getMessage())
        );
        return true;
    }

    /**
     * Adds the "dungeon" jail role to a member, quarantining a brand-new account
     * so it can only see the dungeon channel (and cannot read or report messages
     * elsewhere) until staff vet it.
     *
     * <p>No-op — returning false — if no "dungeon" role exists in the guild or the
     * member already has it. The channel-permission side of the jail (hiding every
     * other channel from this role) is configured on the Discord server itself;
     * this method only applies the role. Returns true if a role add was issued.
     */
    public static boolean assignDungeonRole(Guild guild, Member member) {
        Role role = findRoleContaining(guild, "/dungeon\\");
        if (role == null) {
            System.err.println("Roles: No '/dungeon\\' role found in guild " + guild.getName()
                + " — cannot jail " + member.getId());
            return false;
        }
        if (member.getRoles().contains(role)) return false;
        guild.addRoleToMember(member, role).queue(
            v -> System.out.println("Roles: Jailed " + member.getId() + " with '" + role.getName() + "' role"),
            e -> System.err.println("Roles: Could not assign 'dungeon' role to " + member.getId() + ": " + e.getMessage())
        );
        return true;
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
