package com.agape;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Bulk role assignment/removal across an entire guild, paced to stay under
 * Discord's rate limits.
 *
 * <p>Discord answers with HTTP 429 when role writes arrive too quickly, so every
 * add/remove issued here is separated by {@value #OPERATION_DELAY_MS} ms. The
 * work loads all guild members once via gateway chunking (cheap, not per-user
 * REST) and runs on a background thread, so it never blocks the JDA event loop.
 * Members who already have the role (for {@link Action#ADD}) or already lack it
 * (for {@link Action#REMOVE}) are skipped without an API call.
 *
 * <p>Three scopes are offered:
 * <ul>
 *   <li>{@link #applyToAll} — every member.</li>
 *   <li>{@link #applyIgnoringAccountsUnder} — skip accounts younger than N days.</li>
 *   <li>{@link #applyIgnoringAccountsOver} — skip accounts older than N days.</li>
 * </ul>
 * "Account age" is the Discord account creation age (snowflake timestamp), the
 * same measure used by {@link ServerProtectionManager}.
 */
public final class MassRoleManager {

    private MassRoleManager() {}

    /** Delay between consecutive role writes, to avoid 429 rate-limit responses. */
    public static final long OPERATION_DELAY_MS = 220;

    /** Whether a sweep adds or removes the target role. */
    public enum Action { ADD, REMOVE }

    /** Apply {@code action} for {@code role} to every member of the guild. */
    public static void applyToAll(Guild guild, Role role, Action action) {
        run(guild, role, action, m -> true, "all members");
    }

    /**
     * Apply only to members whose account is at least {@code minAgeDays} old,
     * ignoring younger accounts.
     */
    public static void applyIgnoringAccountsUnder(Guild guild, Role role, Action action, int minAgeDays) {
        OffsetDateTime now = OffsetDateTime.now();
        run(guild, role, action,
            m -> accountAgeDays(m, now) >= minAgeDays,
            "accounts at least " + minAgeDays + " day(s) old");
    }

    /**
     * Apply only to members whose account is younger than {@code maxAgeDays},
     * ignoring older accounts.
     */
    public static void applyIgnoringAccountsOver(Guild guild, Role role, Action action, int maxAgeDays) {
        OffsetDateTime now = OffsetDateTime.now();
        run(guild, role, action,
            m -> accountAgeDays(m, now) < maxAgeDays,
            "accounts younger than " + maxAgeDays + " day(s)");
    }

    /** Whole-day account age (creation timestamp → now). */
    private static long accountAgeDays(Member member, OffsetDateTime now) {
        return ChronoUnit.DAYS.between(member.getUser().getTimeCreated(), now);
    }

    /**
     * Loads all members once, then walks them on a background thread applying
     * {@code action} to those passing {@code filter}, pausing
     * {@link #OPERATION_DELAY_MS} ms after each actual API write.
     */
    private static void run(Guild guild, Role role, Action action,
                            Predicate<Member> filter, String scopeLabel) {
        if (!guild.getSelfMember().canInteract(role)) {
            System.err.println("MassRoleManager: Cannot manage role '" + role.getName()
                + "' in " + guild.getName() + " — it sits above the bot's highest role. Aborting.");
            return;
        }

        new Thread(() -> {
            List<Member> members;
            try {
                members = guild.loadMembers().get();
            } catch (Exception e) {
                System.err.println("MassRoleManager: Could not load members for "
                    + guild.getName() + ": " + e.getMessage());
                return;
            }

            System.out.println("MassRoleManager: Starting " + action + " of '" + role.getName()
                + "' for " + scopeLabel + " in " + guild.getName()
                + " (" + members.size() + " member(s), ~" + OPERATION_DELAY_MS + "ms apart)");

            int changed = 0, skipped = 0, failed = 0;
            for (Member member : members) {
                if (member.getUser().isBot()) { skipped++; continue; } // never touch bots
                if (!filter.test(member)) { skipped++; continue; }

                boolean hasRole = member.getRoles().contains(role);
                boolean needsWrite = (action == Action.ADD) ? !hasRole : hasRole;
                if (!needsWrite) { skipped++; continue; }

                try {
                    if (action == Action.ADD) guild.addRoleToMember(member, role).complete();
                    else                      guild.removeRoleFromMember(member, role).complete();
                    changed++;
                } catch (Exception e) {
                    failed++;
                    System.err.println("MassRoleManager: Failed to " + action + " '" + role.getName()
                        + "' for " + member.getId() + ": " + e.getMessage());
                }

                // We just issued a REST write — pace before the next one.
                try {
                    Thread.sleep(OPERATION_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("MassRoleManager: Interrupted — aborting after "
                        + changed + " change(s).");
                    return;
                }
            }

            System.out.println("MassRoleManager: Done — " + action + " '" + role.getName()
                + "' for " + scopeLabel + ": " + changed + " changed, "
                + skipped + " skipped, " + failed + " failed.");
        }, "mass-role-" + action).start();
    }
}
