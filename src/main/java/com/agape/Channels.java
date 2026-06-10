package com.agape;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * All channel-name lookup conventions in one place.
 *
 * The bot locates its working channels by name rather than by stored IDs so
 * the server can be rebuilt without reconfiguration. Channel-name searches
 * are case-insensitive; "normalized" searches additionally strip dashes
 * ("quick-match" and "quickmatch" are the same channel).
 */
public final class Channels {

    /** Channels checked (in priority order) for posting matchmaker-facing messages. */
    private static final String[] MATCHMAKER_CHANNEL_NAMES = {
        "matchmaker-backroom", "matchmakers", "applications", "pending-applications"
    };

    private Channels() {}

    /**
     * The staff channel where applications, reports, feedback, and decline
     * alerts are posted. Returns null when none of the known names exist.
     */
    public static TextChannel findMatchmakerChannel(Guild guild) {
        for (String name : MATCHMAKER_CHANNEL_NAMES) {
            List<TextChannel> chs = guild.getTextChannelsByName(name, true);
            if (!chs.isEmpty()) return chs.get(0);
        }
        return null;
    }

    /**
     * Finds the first text channel whose dash-stripped lowercase name exactly
     * equals one of the given normalized names (e.g. "quickmatch" matches
     * both "#quickmatch" and "#quick-match").
     */
    public static TextChannel findByNormalizedName(Guild guild, String... normalizedNames) {
        for (TextChannel ch : guild.getTextChannels()) {
            String normalized = ch.getName().toLowerCase().replace("-", "");
            for (String wanted : normalizedNames) {
                if (normalized.equals(wanted)) return ch;
            }
        }
        return null;
    }

    /**
     * Finds the first text channel whose dash-stripped lowercase name contains
     * the given fragment (e.g. "guideline" matches "#matchmaking-guidelines").
     */
    public static TextChannel findByNameContaining(Guild guild, String fragment) {
        for (TextChannel ch : guild.getTextChannels()) {
            if (ch.getName().toLowerCase().replace("-", "").contains(fragment)) return ch;
        }
        return null;
    }
}
