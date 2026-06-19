package com.agape;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Server "house cleaning" — scans recent channel history for content that bad
 * actors could screenshot or mass-report out of context to get an innocent
 * member (or the whole server) actioned by Discord Trust &amp; Safety.
 *
 * <p>The goal is NOT to police members — it is to surface old messages that,
 * regardless of their original intent, touch the topics T&amp;S acts on most
 * aggressively when reported, so they can be reviewed and (eventually) cleaned
 * up before an invader weaponizes them. Keywords are grouped into
 * {@link #CATEGORIES} so each console hit says <i>why</i> it tripped, which makes
 * triage and tuning easy.
 *
 * <p><b>Matching is intentionally broad.</b> This is a defensive filter for our
 * own server; on a Christian dating server some categories (e.g. minor-safety,
 * which includes everyday words like "kid"/"child") will produce false
 * positives by design — over-flagging for human review is the safer failure mode
 * than missing the one message that gets someone banned. Trim a category's array
 * if a given channel proves too noisy.
 *
 * <p><b>Current behavior:</b> scan-and-report only. Every flagged message past
 * its category's lifespan ({@link #CATEGORY_MIN_AGE_HOURS}, default
 * {@link #DEFAULT_MIN_AGE_HOURS} hours) is logged to the system console. Nothing
 * is deleted yet — the deletion step is intentionally stubbed at
 * {@link #handleFlaggedMessage} so it can be enabled once vetted.
 *
 * <p>Work is done off the JDA event loop: history is retrieved asynchronously
 * via {@code getIterableHistory().takeAsync(...)} (same non-blocking convention
 * the rest of the bot follows), and only allowed guilds are scanned
 * (see {@link EnvironmentManager#isGuildAllowed}).
 */
public final class ServerProtectionManager {

    // Master switch — flip to false to disable scanning entirely.
    static final boolean ENABLED = true;

    /** Max number of recent messages to pull per channel (0.5k–1k range). */
    private static final int MESSAGE_SCAN_LIMIT = 1000;

    /**
     * Default minimum age (hours) before a flagged message is reported, used by
     * any category not overridden in {@link #CATEGORY_MIN_AGE_HOURS}.
     */
    private static final int DEFAULT_MIN_AGE_HOURS = 72;

    /**
     * Categories that are actually reported to the console. Every category's
     * keyword list below is still defined and compiled (kept for future use),
     * but only matches in these categories surface — all others are ignored.
     */
    private static final Set<String> ACTIVE_CATEGORIES = new HashSet<>(Arrays.asList(
        "hate/extremism", "sexuality/gender"
    ));

    /**
     * Per-category report lifespans (hours). A message is only reported for a
     * category once it is older than that category's threshold. Only the
     * {@link #ACTIVE_CATEGORIES} thresholds are consulted today; the rest remain
     * for when more categories are re-enabled. Categories absent here fall back
     * to {@link #DEFAULT_MIN_AGE_HOURS}.
     */
    private static final Map<String, Integer> CATEGORY_MIN_AGE_HOURS = new HashMap<>();
    static {
        CATEGORY_MIN_AGE_HOURS.put("slur", 2);
        CATEGORY_MIN_AGE_HOURS.put("hate/extremism", 48);
        CATEGORY_MIN_AGE_HOURS.put("sexuality/gender", 48);
        CATEGORY_MIN_AGE_HOURS.put("minor-safety", 48);
        CATEGORY_MIN_AGE_HOURS.put("sexual/nsfw", 48);
    }

    /**
     * Channel allowlist. When non-empty, ONLY channels whose name matches one of
     * these is scanned; every other channel is skipped. Leave the array empty to
     * scan every text channel the bot can read.
     *
     * <p>Matching is case-insensitive and dash-insensitive (the {@link Channels}
     * convention): "off-topic" here matches both #off-topic and #offtopic.
     * Channels listed here are still subject to the View Channel / Read Message
     * History permission check, so Discord-side permissions remain the hard gate.
     */
    private static final String[] SCAN_CHANNELS = {
        // e.g. "general", "off-topic", "prayer-requests"
        "welcome", "gen-chat", "exegetical-discussion", "leisure", "media-files-only", "other-media-embeds"
    };

    /**
     * Flaggable terms grouped by the kind of false report they could fuel.
     * Each term is matched case-insensitively as a <b>whole word/phrase</b>
     * (so "gay" will not fire inside "okay", nor "sex" inside "sexuality"),
     * which is why morphological variants are enumerated explicitly rather than
     * relying on stemming. Add or remove terms freely — the patterns are rebuilt
     * from this map at class load.
     */
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<>();
    static {
        // ── Sexuality & gender ── the original incident vector: benign in
        // context, but routinely reported as "hate" or used to bait a ban.
        CATEGORIES.put("sexuality/gender", new String[]{
            "gay", "gays", "lesbian", "lesbians", "lgbt", "lgbtq", "lgbtqia", "lgbtq+",
            "homosexual", "homosexuals", "homosexuality", "bisexual", "bisexuality",
            "pansexual", "pansexuality", "asexual", "demisexual", "queer", "trans",
            "transgender", "transsexual", "transman", "transwoman", "transphobia",
            "transphobic", "homophobia", "homophobic", "homophobe", "nonbinary",
            "non-binary", "enby", "genderfluid", "genderqueer", "cisgender",
            "two-spirit", "intersex", "sapphic", "twink", "femboy", "drag queen",
            "drag king", "gender identity", "sexual orientation", "same-sex",
            "same sex", "coming out", "closeted", "pronoun", "pronouns",
            "heteronormative",
        });

        // ── Slurs ── necessary for any moderation filter; widely-recognized terms only.
        CATEGORIES.put("slur", new String[]{
            "faggot", "faggots", "fag", "fags", "dyke", "tranny", "trannies",
            "nigger", "niggers", "nigga", "niggas", "spic", "chink", "gook", "kike",
            "wetback", "beaner", "coon", "raghead", "towelhead", "paki",
            "retard", "retarded", "retards", "tard",
        });

        // ── "Extremism" ── Nazi, supremacist, terror, and atrocity references.
        CATEGORIES.put("hate/extremism", new String[]{
            "nazi", "nazis", "neo-nazi", "neonazi", "hitler", "heil", "heil hitler",
            "holocaust", "swastika", "kkk", "ku klux klan", "white power",
            "white supremacy", "white supremacist", "supremacist", "aryan",
            "genocide", "ethnic cleansing", "jihad", "isis", "al-qaeda", "taliban",
            "terrorist", "terrorism", "lynch", "lynching", "apartheid",
            "segregation", "fascist", "fascism",
        });

        // ── Minor safety ── highest T&S risk; intentionally aggressive. The
        // everyday words (child/kid/teen/minor) WILL false-positive on a dating
        // server — that is the deliberate tradeoff for never missing the real thing.
        CATEGORIES.put("minor-safety", new String[]{
            "minor", "minors", "underage", "underaged", "child", "children", "kid",
            "kids", "toddler", "infant", "preteen", "pre-teen", "teen", "teens",
            "teenager", "teenagers", "loli", "lolicon", "shota", "shotacon",
            "jailbait", "cp", "csam", "pedo", "pedophile", "pedophiles",
            "pedophilia", "pedophilic", "paedophile", "paedophilia", "groom",
            "grooming", "groomer", "molest", "molested", "molester", "molestation",
            "statutory", "age of consent", "child porn", "child pornography",
            "cheese pizza", "minor attracted",
        });

        // ── Self-harm / suicide ──
        CATEGORIES.put("self-harm", new String[]{
            "suicide", "suicidal", "kys", "kill myself", "killing myself",
            "end my life", "self-harm", "self harm", "selfharm", "cut myself",
            "cutting myself", "slit my wrists", "overdose", "hang myself",
            "want to die", "better off dead", "no reason to live",
        });

        // ── Violence / threats ──
        CATEGORIES.put("violence", new String[]{
            "kill", "killing", "murder", "shoot", "shooting", "shooter", "stab",
            "stabbing", "bomb", "bombing", "massacre", "behead", "beheading",
            "rape", "raped", "raping", "rapist", "assault", "slaughter",
            "school shooting", "mass shooting", "death threat", "kill you",
        });

        // ── Drugs ──
        CATEGORIES.put("drugs", new String[]{
            "cocaine", "heroin", "meth", "methamphetamine", "crack", "weed",
            "marijuana", "cannabis", "420", "lsd", "acid", "mdma", "ecstasy",
            "molly", "shrooms", "psilocybin", "fentanyl", "opioid", "opioids",
            "xanax", "adderall", "ketamine", "dmt", "drug dealer", "smoke weed",
            "getting high",
        });

        // ── Sexual / NSFW ──
        CATEGORIES.put("sexual/nsfw", new String[]{
            "porn", "pornography", "nude", "nudes", "naked", "sex", "sexting",
            "onlyfans", "nsfw", "blowjob", "handjob", "masturbate", "masturbation",
            "orgasm", "ejaculate", "cum", "cumming", "horny", "fetish", "bdsm",
            "kink", "hentai", "rule34", "gooning", "goon", "breedable", "milf",
            "dilf", "thot", "slut", "whore", "escort", "prostitute", "hooker",
            "camgirl", "sugar daddy", "sugar baby", "dick pic",
        });
    }

    /** category name → compiled \b(term1|term2|...)\b pattern (case-insensitive). */
    private static final Map<String, Pattern> CATEGORY_PATTERNS = buildPatterns();

    private ServerProtectionManager() {}

    /**
     * Scans the recent message history of every readable text channel in each
     * allowed guild and reports flaggable messages past their category lifespan
     * to the console. Returns immediately; the actual scanning happens on JDA's async pool.
     */
    public static void scanRecentMessages(JDA jda) {
        if (!ENABLED) return;

        for (Guild guild : jda.getGuilds()) {
            if (!EnvironmentManager.isGuildAllowed(guild.getId())) continue;

            for (TextChannel channel : guild.getTextChannels()) {
                if (!isAllowlisted(channel)) continue;
                if (!guild.getSelfMember().hasPermission(channel,
                        Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)) {
                    continue;
                }
                scanChannel(channel);
            }
        }
    }

    /**
     * True if the channel should be scanned per {@link #SCAN_CHANNELS}: always
     * when the allowlist is empty, otherwise only on a dash-insensitive,
     * case-insensitive name match.
     */
    private static boolean isAllowlisted(TextChannel channel) {
        if (SCAN_CHANNELS.length == 0) return true;
        String normalized = channel.getName().toLowerCase().replace("-", "");
        for (String name : SCAN_CHANNELS) {
            if (normalized.equals(name.toLowerCase().replace("-", ""))) return true;
        }
        return false;
    }

    /** Retrieves up to MESSAGE_SCAN_LIMIT recent messages from one channel and reports matches. */
    private static void scanChannel(TextChannel channel) {
        channel.getIterableHistory().takeAsync(MESSAGE_SCAN_LIMIT).thenAccept(messages -> {
            OffsetDateTime now = OffsetDateTime.now();
            int flagged = 0;
            for (Message msg : messages) {
                if (msg.getAuthor().isBot()) continue;
                List<String> categories = reportableCategories(msg.getContentRaw(), msg.getTimeCreated(), now);
                if (categories.isEmpty()) continue;
                handleFlaggedMessage(channel, msg, categories);
                flagged++;
            }
            if (flagged > 0) {
                System.out.println("ServerProtection: #" + channel.getName() + " in "
                        + channel.getGuild().getName() + " — flagged " + flagged + " message(s).");
            }
        }).exceptionally(err -> {
            System.err.println("ServerProtection: Failed to scan #" + channel.getName()
                    + " in " + channel.getGuild().getName() + ": " + err.getMessage());
            return null;
        });
    }

    /**
     * Handles a single flagged message. For now this only reports to the
     * console; deletion will be wired in here once approved.
     */
    private static void handleFlaggedMessage(TextChannel channel, Message msg, List<String> categories) {
        System.out.println("ServerProtection: [FLAGGED] "
                + categories + " #" + channel.getName()
                + " | " + msg.getAuthor().getName()
                + " (" + msg.getAuthor().getId() + ") @ " + msg.getTimeCreated()
                + " | jump=" + msg.getJumpUrl()
                + " | \"" + oneLine(msg.getContentRaw()) + "\"");

        // TODO(security): delete flagged messages here once enabled, e.g.
        //   msg.delete().queue(...);
    }

    /**
     * Returns the categories the message should be reported for: every
     * {@link #ACTIVE_CATEGORIES} category whose terms appear (as whole words)
     * AND whose per-category lifespan ({@link #CATEGORY_MIN_AGE_HOURS}, default
     * {@link #DEFAULT_MIN_AGE_HOURS}) has elapsed since {@code createdAt}.
     * Empty if none. Package-private for testing.
     */
    static List<String> reportableCategories(String content, OffsetDateTime createdAt, OffsetDateTime now) {
        List<String> hits = new ArrayList<>();
        for (String category : matchedCategories(content)) {
            if (!ACTIVE_CATEGORIES.contains(category)) continue;
            int minAge = CATEGORY_MIN_AGE_HOURS.getOrDefault(category, DEFAULT_MIN_AGE_HOURS);
            if (createdAt.isBefore(now.minusHours(minAge))) hits.add(category);
        }
        return hits;
    }

    /**
     * Returns the names of every category whose terms appear (as whole words)
     * in the text, ignoring message age — empty if none. Package-private for testing.
     */
    static List<String> matchedCategories(String content) {
        List<String> hits = new ArrayList<>();
        if (content == null || content.isEmpty()) return hits;
        for (Map.Entry<String, Pattern> e : CATEGORY_PATTERNS.entrySet()) {
            if (e.getValue().matcher(content).find()) hits.add(e.getKey());
        }
        return hits;
    }

    /** True if the text trips any category. Package-private for testing. */
    static boolean containsControversialKeyword(String content) {
        return !matchedCategories(content).isEmpty();
    }

    private static Map<String, Pattern> buildPatterns() {
        Map<String, Pattern> map = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : CATEGORIES.entrySet()) {
            String[] terms = e.getValue();
            StringBuilder sb = new StringBuilder("\\b(");
            for (int i = 0; i < terms.length; i++) {
                if (i > 0) sb.append('|');
                sb.append(Pattern.quote(terms[i]));
            }
            sb.append(")\\b");
            map.put(e.getKey(), Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE));
        }
        return map;
    }

    /** Collapses newlines/tabs so a flagged message logs as a single console line. */
    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    // ─── New-account join reporting ───────────────────────────────────────────
    //
    // A freshly-created Discord account joining the server is a classic raid /
    // mass-reporter tell: throwaway accounts are spun up to brigade, screenshot,
    // and report members. We log every join from an account younger than a month
    // so staff can keep an eye on it, escalating the tag as the account gets
    // newer (and therefore more suspicious).

    /** Severity tier for a newly-joined account, by account age. */
    enum JoinRisk { NONE, NOTICE, URGENT, SEVERE }

    /**
     * Classifies a joining account purely by age:
     * <ul>
     *   <li><b>SEVERE</b> — one day old or less</li>
     *   <li><b>URGENT</b> — less than one week old</li>
     *   <li><b>NOTICE</b> — less than one month old</li>
     *   <li><b>NONE</b>   — a month or older (not reported)</li>
     * </ul>
     * Pure and package-private for testing. A {@code created} in the future
     * (clock skew) yields {@link JoinRisk#NONE}.
     */
    static JoinRisk classifyAccountAge(OffsetDateTime created, OffsetDateTime now) {
        long hours = ChronoUnit.HOURS.between(created, now);
        if (hours < 0)        return JoinRisk.NONE;   // created in the future — ignore
        if (hours <= 24)      return JoinRisk.SEVERE; // one day or less
        if (hours < 7 * 24)   return JoinRisk.URGENT; // under a week
        if (hours < 30 * 24)  return JoinRisk.NOTICE; // under a month
        return JoinRisk.NONE;
    }

    /**
     * Logs a console report if {@code member}'s account is younger than a month.
     * Older accounts (and bots) are ignored. The report tag escalates with how
     * new the account is — see {@link #classifyAccountAge}.
     */
    static void reportNewAccount(Member member) {
        if (member == null || member.getUser().isBot()) return;
        OffsetDateTime created = member.getUser().getTimeCreated();
        OffsetDateTime now = OffsetDateTime.now();
        JoinRisk risk = classifyAccountAge(created, now);
        if (risk == JoinRisk.NONE) return;

        System.out.println("ServerProtection: [NEW-ACCOUNT/" + risk + "] "
                + member.getUser().getName() + " (" + member.getId() + ") joined "
                + member.getGuild().getName()
                + " | account age " + describeAge(created, now)
                + " | created " + created);
    }

    /** True if the account is younger than a week (the SEVERE or URGENT tier). */
    static boolean isUnderOneWeek(OffsetDateTime created, OffsetDateTime now) {
        JoinRisk r = classifyAccountAge(created, now);
        return r == JoinRisk.SEVERE || r == JoinRisk.URGENT;
    }

    /**
     * Jails a joining account younger than a week with the "dungeon" role,
     * quarantining it until staff can vet it. Bots and accounts a week or older
     * are left alone. Delegates the actual role apply to
     * {@link Roles#assignDungeonRole}.
     */
    static void jailIfTooNew(Member member) {
        if (member == null || member.getUser().isBot()) return;
        if (!isUnderOneWeek(member.getUser().getTimeCreated(), OffsetDateTime.now())) return;
        Roles.assignDungeonRole(member.getGuild(), member);
    }

    /** Human-readable account age, e.g. "5 days, 3 hours" or "9 hours". */
    private static String describeAge(OffsetDateTime created, OffsetDateTime now) {
        long totalHours = Math.max(0, ChronoUnit.HOURS.between(created, now));
        long days = totalHours / 24;
        long hours = totalHours % 24;
        if (days == 0) return hours + (hours == 1 ? " hour" : " hours");
        return days + (days == 1 ? " day" : " days") + ", " + hours + (hours == 1 ? " hour" : " hours");
    }
}
