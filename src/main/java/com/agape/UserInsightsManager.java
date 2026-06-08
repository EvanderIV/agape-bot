package com.agape;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class UserInsightsManager {

    private static final String INSIGHTS_DIR  = "data/insights/";
    private static final String PROFILES_DIR  = "user_content/profiles/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ─── Data structures ──────────────────────────────────────────────────────

    static class DeclineEntry {
        String matchPartnerId;
        String threadId;
        String reasons;
        String timestamp;
    }

    static class UserInsightsRecord {
        String userId;
        List<String> preferences    = new ArrayList<>();
        List<DeclineEntry> declineHistory = new ArrayList<>();
    }

    // ─── Preference extraction engine ─────────────────────────────────────────

    private static class TagRule {
        final String tag;
        final Pattern[] patterns;

        TagRule(String tag, String... regexes) {
            this.tag = tag;
            patterns = new Pattern[regexes.length];
            for (int i = 0; i < regexes.length; i++) {
                patterns[i] = Pattern.compile(regexes[i], Pattern.CASE_INSENSITIVE);
            }
        }

        boolean matches(String text) {
            for (Pattern p : patterns) {
                if (p.matcher(text).find()) return true;
            }
            return false;
        }
    }

    /**
     * Direction rule — applies to dealbreaker lists, decline reasons, AND lookFor
     * fields (via the invertDirection parameter):
     *
     *   Dealbreaker / decline (invertDirection=false):
     *     No negation → match HAS this trait → author dislikes it  → "-tag"
     *     Negation    → match LACKS this trait → author wants it   → "+tag"
     *
     *   lookFor (invertDirection=true):
     *     No negation → author actively wants this trait           → "+tag"
     *     Negation    → author actively avoids this trait          → "-tag"
     *
     *   In both modes, negated XOR invertDirection determines the sign:
     *     false XOR false → "-"   false XOR true → "+"
     *     true  XOR false → "+"   true  XOR true → "-"
     */
    private static final TagRule[] TAG_RULES = {
        new TagRule("touchy",
            "physical.{0,10}touch",
            "physical.{0,10}contact",
            "\\btouchy\\b",
            "\\bpda\\b",
            "\\baffection\\b",
            "\\baffectionate\\b",
            "\\bcuddle",
            "\\bclingy\\b",
            "\\bflirt",
            "\\bhugg?(?:ing|s)?\\b",
            "\\bintimacy\\b"),

        new TagRule("horror",
            "\\bhorror\\b",
            "scary\\s+movie",
            "\\bslasher\\b",
            "\\bgore\\b"),

        new TagRule("smoking",
            "\\bsmok",
            "\\bcigarette\\b",
            "\\btobacco\\b",
            "\\bvap"),

        new TagRule("drugs",
            "\\bdrug",
            "\\bweed\\b",
            "\\bmarijuana\\b",
            "\\bcannabis\\b",
            "\\bsubstance\\s+abuse"),

        new TagRule("alcohol",
            "\\balcohol\\b",
            "\\bdrunk\\b",
            "\\bdrinking\\b"),

        new TagRule("dogs",
            "\\bdogs?\\b",
            "\\bpuppy\\b",
            "\\bpuppies\\b",
            "\\bcanine\\b"),

        new TagRule("cats",
            "\\bcats?\\b",
            "\\bkitten\\b",
            "\\bfeline\\b"),

        new TagRule("pets",
            "\\bpets?\\b",
            "\\banimals?\\b"),

        new TagRule("religion",
            "\\bGod\\b",
            "\\bLord\\b",
            "\\bJesus\\b",
            "\\bChrist\\b",
            "\\bbibl",          // Bible, biblical, biblically
            "\\bdevout\\b",
            "\\bworship\\b",
            "\\bScripture\\b",
            "god.{0,5}fear",    // God-fearing, God fearing
            "\\breligious\\b",
            "\\bchurch\\b",
            "\\bmosque\\b",
            "\\bfaith\\b",
            "\\bprayer\\b",
            "\\bchristian\\b",
            "\\bmuslim\\b",
            "\\bjewish\\b",
            "\\bbuddh"),

        new TagRule("introvert",
            "\\bintrovert",
            "\\bantisocial\\b",
            "\\banti-social\\b"),

        new TagRule("kids",
            "\\bkids?\\b",
            "\\bchildren\\b",
            "\\bbabies\\b",
            "\\bbaby\\b"),

        new TagRule("lying",
            "\\blying\\b",
            "\\bliar\\b",
            "\\bdishonest\\b",
            "\\bcheating\\b",
            "\\bcheat\\b"),

        new TagRule("manipulative",
            "\\bmanipulat"),

        new TagRule("controlling",
            "\\bcontrol\\s+freak\\b",
            "\\bcontrolling\\b",
            "\\bbossy\\b",
            "\\bpossessive\\b",
            "\\bdomineering\\b"),

        new TagRule("loyal",
            "\\bloyal",
            "\\bfaithful\\b",
            "\\bfidelity\\b",
            "\\btrust.{0,5}worthy\\b",
            "\\bcommitted\\b",
            "\\bcommitment\\b"),

        new TagRule("funny",
            "\\bfunny\\b",
            "\\bhumou?r\\b",
            "\\bhilarious\\b",
            "\\bwitty\\b",
            "\\bmake.{0,10}laugh"),

        new TagRule("fun",
            "\\bfun\\b",
            "\\bplayful\\b",
            "\\bgoofball\\b",
            "enjoyable",
            "easy.{0,5}to be with",
            "easy.{0,5}to hang"),

        new TagRule("communicative",
            "communicat",          // communication, communicator, communicative
            "good\\s+listener",
            "open.{0,10}communicat"),

        new TagRule("kind",
            "\\bkind\\b",
            "\\bkindness\\b",
            "\\bcaring\\b"),

        new TagRule("compassionate",
            "compassionat",
            "\\bwarm\\b",
            "\\bnurturing\\b",
            "\\bgentle\\b",
            "\\bsoft\\b",
            "\\bsupportive\\b",
            "\\bloving\\b"),

        new TagRule("traditional",
            "\\btraditional\\b",
            "\\bconservative\\b",
            "\\bsubmissive\\b"),

        new TagRule("respectful",
            "\\brespectful\\b",
            "\\brespect\\b"),

        new TagRule("honest",
            "\\bhonest",
            "\\bintegrity\\b",
            "\\btruthful\\b"),

        new TagRule("empathetic",
            "\\bempathy\\b",
            "\\bempathetic\\b",
            "\\bunderstanding\\b"),

        new TagRule("family",
            "\\bfamily\\b"),

        new TagRule("patient",
            "\\bpatient\\b",
            "\\bpatience\\b"),

        new TagRule("mature",
            "\\bmature\\b",
            "\\bmaturity\\b",
            "emotionally\\s+mature"),

        new TagRule("accountable",
            "\\baccountabl",            // accountable, accountability
            "\\bresponsible\\b",
            "takes?\\s+responsibility",
            "owns?\\s+up"),

        new TagRule("dramatic",
            "\\bdramatic\\b",
            "\\bdrama\\b",
            "overly\\s+emotional",
            "high\\s+maintenance"),

        new TagRule("temperamental",
            "quick.{0,5}temper",
            "\\btemper\\b",
            "\\bwrathful\\b",
            "\\bangry\\b",
            "\\brage\\b"),

        new TagRule("arrogant",
            "\\barrogant\\b",
            "\\barrogance\\b",
            "\\bconceit",
            "\\bself.{0,5}righteous\\b",
            "\\begotistical\\b"),

        new TagRule("selfish",
            "\\bselfish",
            "\\bself.{0,5}centred?\\b",
            "\\bself.{0,5}absorbed\\b"),
    };

    // Matches negation words/contractions within a single list item.
    // Also catches "non-" prefix (e.g. "non-christian" → negated → +religion).
    private static final Pattern NEGATION_RE = Pattern.compile(
        "\\b(?:doesn?'?t|don?'?t|isn?'?t|aren?'?t|won?'?t|can'?t|cannot"
        + "|never|without|hates?|dislike[sd]?|hating|disliking)\\b"
        + "|\\bnot\\b"
        + "|\\bnon-",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Splits text on commas and newlines, then for each item checks for negation
     * and matches against tag rules to emit "+tag" or "-tag" strings.
     *
     * @param text            the raw text to parse
     * @param invertDirection true for lookFor fields (wants = "+"), false for
     *                        dealbreaker/decline fields (has = "-")
     * @return deduplicated list of inferred preference tags
     */
    public static List<String> extractPreferences(String text, boolean invertDirection) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;

        String[] items = text.split("[,\n]+");
        for (String raw : items) {
            String item = raw.replaceAll("[:()\\.!?*\"']", " ").trim();
            if (item.isEmpty()) continue;

            boolean negated = NEGATION_RE.matcher(item).find();
            boolean wantsIt = negated ^ invertDirection;

            for (TagRule rule : TAG_RULES) {
                if (rule.matches(item)) {
                    String tag = (wantsIt ? "+" : "-") + rule.tag;
                    if (!result.contains(tag)) result.add(tag);
                    break;
                }
            }
        }
        return result;
    }

    /** Convenience overload for dealbreaker/decline context (invertDirection=false). */
    public static List<String> extractPreferences(String text) {
        return extractPreferences(text, false);
    }

    /**
     * Scans all profiles in user_content/profiles/ and runs processProfile on each
     * accepted one. Safe to call on startup — duplicate tags are silently skipped.
     */
    public static void syncAllProfiles() {
        File dir = new File(PROFILES_DIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        int count = 0;
        for (File f : files) {
            String userId = f.getName().replace(".json", "");
            processProfile(userId);
            count++;
        }
        System.out.println("UserInsightsManager: Synced insights for " + count + " profile(s).");
    }

    /**
     * Reads a user's accepted profile and extracts preference tags from their
     * dealBreakers (dealbreaker direction) and lookFor (inverted direction) fields.
     * Safe to call multiple times — duplicate tags are silently skipped.
     */
    public static void processProfile(String userId) {
        File f = new File(PROFILES_DIR + userId + ".json");
        if (!f.exists()) return;
        try (FileReader reader = new FileReader(f)) {
            ApplicationHandler.AppState state = new Gson().fromJson(reader, ApplicationHandler.AppState.class);
            if (state == null || !"ACCEPTED".equals(state.status)) return;

            if (state.dealBreakers != null && !state.dealBreakers.trim().isEmpty()) {
                addTagsIfAbsent(userId, extractPreferences(state.dealBreakers, false));
            }
            if (state.lookFor != null && !state.lookFor.trim().isEmpty()) {
                addTagsIfAbsent(userId, extractPreferences(state.lookFor, true));
            }
        } catch (Exception e) {
            System.err.println("UserInsightsManager: Failed to process profile for " + userId + ": " + e.getMessage());
        }
    }

    /**
     * Scans an archived thread's messages and applies extracted preference tags
     * to each participant's insight file. Only processes messages that look like
     * dealbreaker lists (3+ commas or 3+ numbered/bulleted lines).
     */
    public static void processThreadMessages(ThreadManager.QMThread record) {
        if (record.messages == null || record.messages.isEmpty()) return;
        for (ThreadManager.ThreadMessage msg : record.messages) {
            if (msg.authorId == null || msg.content == null) continue;
            if (!msg.authorId.equals(record.maleId) && !msg.authorId.equals(record.femaleId)) continue;
            if (!looksLikeList(msg.content)) continue;
            addTagsIfAbsent(msg.authorId, extractPreferences(msg.content, false));
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Records decline reasons and auto-extracts preference tags from the text.
     */
    public static void recordDecline(String userId, String matchPartnerId, String threadId, String reasons) {
        UserInsightsRecord record = loadOrCreate(userId);
        DeclineEntry entry = new DeclineEntry();
        entry.matchPartnerId = matchPartnerId;
        entry.threadId       = threadId;
        entry.reasons        = reasons.trim();
        entry.timestamp      = LocalDateTime.now().format(FMT);
        record.declineHistory.add(entry);
        save(userId, record);

        addTagsIfAbsent(userId, extractPreferences(reasons, false));
    }

    /**
     * Toggles a preference tag for a user. Passing a tag that already exists removes it.
     * Used by the /tag-user matchmaker command for manual overrides.
     */
    public static UserInsightsRecord applyTag(String userId, String tag) {
        UserInsightsRecord record = loadOrCreate(userId);
        if (record.preferences.contains(tag)) {
            record.preferences.remove(tag);
        } else {
            record.preferences.add(tag);
        }
        save(userId, record);
        return record;
    }

    public static UserInsightsRecord getInsights(String userId) {
        File f = new File(INSIGHTS_DIR + userId + ".json");
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f)) {
            return GSON.fromJson(reader, UserInsightsRecord.class);
        } catch (Exception e) {
            System.err.println("UserInsightsManager: Failed to load insights for " + userId + ": " + e.getMessage());
            return null;
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static void addTagsIfAbsent(String userId, List<String> tags) {
        if (tags.isEmpty()) return;
        UserInsightsRecord record = loadOrCreate(userId);
        boolean changed = false;
        for (String tag : tags) {
            if (!record.preferences.contains(tag)) {
                record.preferences.add(tag);
                changed = true;
                System.out.println("UserInsightsManager: Inferred tag " + tag + " for user " + userId);
            }
        }
        if (changed) save(userId, record);
    }

    private static boolean looksLikeList(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        long commas = text.chars().filter(c -> c == ',').count();
        if (commas >= 3) return true;
        String[] lines = text.split("\n");
        int listLines = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.matches("^\\d+[.):].*") || t.startsWith("- ") || t.startsWith("• ")) listLines++;
        }
        return listLines >= 3;
    }

    private static UserInsightsRecord loadOrCreate(String userId) {
        File f = new File(INSIGHTS_DIR + userId + ".json");
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                UserInsightsRecord r = GSON.fromJson(reader, UserInsightsRecord.class);
                if (r != null) {
                    if (r.preferences    == null) r.preferences    = new ArrayList<>();
                    if (r.declineHistory == null) r.declineHistory = new ArrayList<>();
                    return r;
                }
            } catch (Exception ignored) {}
        }
        UserInsightsRecord r = new UserInsightsRecord();
        r.userId = userId;
        return r;
    }

    private static void save(String userId, UserInsightsRecord record) {
        new File(INSIGHTS_DIR).mkdirs();
        record.userId = userId;
        try (FileWriter writer = new FileWriter(new File(INSIGHTS_DIR + userId + ".json"))) {
            GSON.toJson(record, writer);
        } catch (Exception e) {
            System.err.println("UserInsightsManager: Failed to save insights for " + userId + ": " + e.getMessage());
        }
    }
}
