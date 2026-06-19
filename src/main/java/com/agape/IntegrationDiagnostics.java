package com.agape;

import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * One-shot boot diagnostics for the Arcane XP integration.
 *
 * <p><b>Why this exists:</b> Discord does not let one bot invoke another bot's
 * slash commands — there is no API for it. The member permissions "Use External
 * Apps" and "Use Application Commands" govern what <i>humans</i> may do in a
 * channel; they do not give Agape the ability to trigger Arcane's {@code /xp}
 * commands. The only supported integration surface is <b>shared roles</b>: Agape
 * assigns a role, and Arcane (configured on its own dashboard) treats that role
 * as an XP multiplier or level reward.
 *
 * <p>This logger prints, once on boot, what Agape can <i>actually</i> see and do
 * in each allowed guild — its own permissions, where it sits in the role
 * hierarchy, whether Arcane is present and outranks it, and which roles Agape is
 * able to assign — so the real integration surface is visible at a glance. It is
 * read-only and changes nothing.
 */
public final class IntegrationDiagnostics {

    private IntegrationDiagnostics() {}

    /** Substring used to locate the Arcane bot among guild members. */
    private static final String ARCANE_KEYWORD = "arcane";

    /** Logs the XP-integration context for every allowed guild. Read-only. */
    public static void logArcaneXpContext(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;
            logForGuild(guild);
        }
    }

    private static void logForGuild(Guild guild) {
        System.out.println("──── Arcane XP integration diagnostics: " + guild.getName() + " ────");

        Member self = guild.getSelfMember();
        System.out.println("  MANAGE_ROLES permission: " + self.hasPermission(Permission.MANAGE_ROLES)
                + "  (required to assign multiplier/level roles)");
        System.out.println("  MANAGE_SERVER permission: " + self.hasPermission(Permission.MANAGE_SERVER));
        List<Role> selfRoles = self.getRoles();
        String highest = selfRoles.isEmpty()
                ? "@everyone (no roles — cannot assign any role)"
                : selfRoles.get(0).getName() + " @ position " + selfRoles.get(0).getPosition();
        System.out.println("  Agape's highest role: " + highest);

        // Reminder of the hard limitation, printed where it's most useful.
        System.out.println("  NOTE: No Discord API lets Agape invoke Arcane's slash commands. "
                + "XP must flow through shared roles (Arcane multiplier / level rewards), "
                + "not by calling /xp.");

        // Which roles can Agape actually assign? (Must sit below Agape's highest role
        // and not be managed/integration-owned.) This is the menu for multiplier roles.
        int assignable = 0;
        StringBuilder assignableNames = new StringBuilder();
        for (Role role : guild.getRoles()) {
            if (role.isPublicRole()) continue; // skip @everyone
            if (self.canInteract(role) && !role.isManaged()) {
                assignable++;
                if (assignableNames.length() > 0) assignableNames.append(", ");
                assignableNames.append(role.getName());
            }
        }
        System.out.println("  Roles Agape can assign (" + assignable + "): "
                + (assignable == 0 ? "<none>" : assignableNames));

        // Locate Arcane via a targeted gateway lookup (cheap — no full member load).
        guild.retrieveMembersByPrefix(ARCANE_KEYWORD, 10).onSuccess(members -> {
            Member arcane = null;
            for (Member m : members) {
                if (m.getUser().isBot()) { arcane = m; break; }
            }
            if (arcane == null) {
                System.out.println("  Arcane bot: not found (searched members starting with \""
                        + ARCANE_KEYWORD + "\"). Verify its name, or it may be absent from this guild.");
            } else {
                boolean arcaneOutranks = !self.canInteract(arcane);
                System.out.println("  Arcane bot: " + arcane.getUser().getName()
                        + " (" + arcane.getId() + "), highest role position "
                        + (arcane.getRoles().isEmpty() ? "n/a" : arcane.getRoles().get(0).getPosition()));
                System.out.println("  Arcane outranks Agape in hierarchy: " + arcaneOutranks
                        + "  (multiplier roles Agape assigns must be readable by Arcane — role position "
                        + "rarely matters for that, but is logged here for visibility)");
            }
        }).onError(err ->
            System.out.println("  Arcane bot: lookup failed (" + err.getMessage()
                    + "). GUILD_MEMBERS intent is required for prefix search.")
        );

        // Demonstrate the command limit empirically: Agape can enumerate ONLY its own
        // registered commands, never Arcane's.
        guild.retrieveCommands().queue(
            cmds -> System.out.println("  Application commands visible to Agape (its OWN only): "
                    + cmds.size() + " — Arcane's commands are not enumerable via the API."),
            err -> System.out.println("  Could not retrieve Agape's own commands: " + err.getMessage())
        );
    }
}
