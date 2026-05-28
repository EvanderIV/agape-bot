package com.agape;

import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;

public class CompatibilityEngine {

    private static final String PROFILES_DIR = "user_content/profiles/";
    private static final Gson GSON = new Gson();

    public static final int MAX_DENOM   = 30; // 20 base + up to 5+5 targetSect bonus
    public static final int MAX_AGE     = 50;
    public static final int MAX_DIST    = 10;
    public static final int MIN_DIST    = -15;
    public static final int MAX_VALUES  = 20;
    public static final int MAX_TOTAL   = MAX_DENOM + MAX_AGE + MAX_DIST + MAX_VALUES; // 110

    // ─── Deal-breaker keyword tables ─────────────────────────────────────────
    // Each row: triggers checked in the person's dealBreakers text (lowercase substrings),
    //           signals checked in the partner's hobbies+weaknesses text.

    private static final String[][] DB_TRIGGERS = {
        { "smok" },
        { "drink", "alcohol" },
        { "drug", "substance" },
        { "children", "kids", "has kid" },
        { "divorc" },
        { "dishonest", "liar", "lying" },
        { "disrespect", "mean", "rude" },
        { "dramatic", "emotional instab", "overl" },
        { "selfish" },
        { "materialist" },
    };
    private static final String[][] DB_SIGNALS = {
        { "smoke", "smoking", "smoker", "cigarette", "tobacco", "vape", "vaping" },
        { "drinking", "drinker", "alcohol", "drunk", "beer", "wine", "liquor" },
        { "drug", "marijuana", "weed", "cannabis", "cocaine", "420" },
        { "my kids", "my children", "my son", "my daughter", "single parent", "baby mama", "baby daddy" },
        { "divorced", "divorce" },
        { "dishonest", "lying", "liar", "deceptive", "manipulat" },
        { "disrespectful", "disrespect", "rude", "mean ", "bully" },
        { "dramatic", "overreact", "explosive", "anger issue", "easily angered", "emotional outburst" },
        { "selfish", "self-centered", "self centered" },
        { "materialist", "luxury brands", "designer" },
    };
    private static final String[] DB_LABELS = {
        "smoking", "alcohol", "drug use", "having children", "being divorced",
        "dishonesty", "disrespect", "emotional instability", "selfishness", "materialism",
    };

    // ─── Values / lookFor keyword tables ─────────────────────────────────────
    // Triggers checked in the person's lookFor text; signals checked in partner's strengths+hobbies.

    private static final String[][] VAL_TRIGGERS = {
        { "faith", "christ", "god", "jesus", "spiritual", "priorit" },
        { "kind", "warm", "caring", "compassion" },
        { "respect" },
        { "empathy", "empath", "compassionate" },
        { "communicat", "listen", "conversation" },
        { "leader", "leadership" },
        { "intellig", "smart", "wisdom", "wise" },
        { "passion" },
        { "patient", "patience" },
        { "humble", "humility" },
        { "honest", "sincer", "genuine", "authentic" },
        { "sport", "active", "athletic", "fitness", "exercise" },
        { "funny", "humor", "laugh" },
        { "nurtur", "support" },
    };
    private static final String[][] VAL_SIGNALS = {
        { "faith", "faithful", "christian", "god", "jesus", "christ", "church", "prayer", "spiritual", "bible", "holy", "devotion" },
        { "kind", "caring", "warmth", "warm", "compassionate", "loving", "gentle" },
        { "respectful", "respect", "polite", "courteous" },
        { "empathetic", "empathy", "compassionate", "compassion", "understanding" },
        { "communicat", "listen", "dialogue", "conversation" },
        { "leader", "leadership", "initiative", "driven", "takes charge" },
        { "intelligent", "intellect", "smart", "wisdom", "wise", "knowledge" },
        { "passionate", "passion", "enthusiast" },
        { "patient", "patience" },
        { "humble", "humility" },
        { "honest", "honesty", "sincere", "sincerity", "genuine", "authentic" },
        { "sport", "athletic", "active", "fitness", "gym", "exercise", "run", "football", "basketball", "workout" },
        { "funny", "humor", "humour", "laugh", "joke", "witty" },
        { "nurtur", "support", "encouraging" },
    };
    private static final String[] VAL_LABELS = {
        "faith/spirituality", "kindness/warmth", "respectfulness", "empathy/compassion",
        "communication", "leadership", "intelligence", "passion", "patience", "humility",
        "honesty/sincerity", "active/athletic", "humor", "nurturing/support",
    };

    // ─── Public result types ──────────────────────────────────────────────────

    public static class ScoreDetail {
        public final int score;
        public final String detail;
        ScoreDetail(int score, String detail) { this.score = score; this.detail = detail; }
    }

    public static class CompatPair {
        public final String userId1, userId2;
        public final ApplicationHandler.AppState profile1, profile2;
        public final int totalScore;
        public final ScoreDetail denom, age, dist, values, dealBreakers;

        CompatPair(String u1, String u2, ApplicationHandler.AppState p1, ApplicationHandler.AppState p2,
                   ScoreDetail denom, ScoreDetail age, ScoreDetail dist,
                   ScoreDetail values, ScoreDetail dealBreakers) {
            this.userId1 = u1; this.userId2 = u2;
            this.profile1 = p1; this.profile2 = p2;
            this.denom = denom; this.age = age; this.dist = dist;
            this.values = values; this.dealBreakers = dealBreakers;
            this.totalScore = denom.score + age.score + dist.score + values.score + dealBreakers.score;
        }
    }

    public static class ScoringResult {
        public final List<CompatPair> topPairs;
        public final int profileCount;
        public final int pairCount;
        ScoringResult(List<CompatPair> topPairs, int profileCount, int pairCount) {
            this.topPairs = topPairs; this.profileCount = profileCount; this.pairCount = pairCount;
        }
    }

    // ─── Main entry point ─────────────────────────────────────────────────────

    /**
     * Loads all ACCEPTED profiles, scores every opposite-sex pair, and returns
     * the top {@code limit} pairs sorted by score descending.
     */
    public static ScoringResult findTopMatches(int limit) {
        File dir = new File(PROFILES_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0)
            return new ScoringResult(Collections.emptyList(), 0, 0);

        List<String> ids = new ArrayList<>();
        List<ApplicationHandler.AppState> profiles = new ArrayList<>();
        for (File f : files) {
            String uid = f.getName().replace(".json", "");
            ApplicationHandler.AppState p = loadProfile(uid);
            if (p != null && "ACCEPTED".equals(p.status)) {
                ids.add(uid);
                profiles.add(p);
            }
        }

        List<CompatPair> pairs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                ApplicationHandler.AppState a = profiles.get(i);
                ApplicationHandler.AppState b = profiles.get(j);
                if (a.sex == b.sex) continue; // opposite-sex pairs only
                pairs.add(new CompatPair(
                    ids.get(i), ids.get(j), a, b,
                    scoreDenomination(a, b), scoreAge(a, b), scoreDistance(a, b),
                    scoreValues(a, b), scoreDealBreakers(a, b)
                ));
            }
        }

        pairs.sort((x, y) -> y.totalScore - x.totalScore);
        List<CompatPair> top = pairs.subList(0, Math.min(limit, pairs.size()));
        return new ScoringResult(top, profiles.size(), pairs.size());
    }

    // ─── Denomination scoring (-5–30) ────────────────────────────────────────

    public static ScoreDetail scoreDenomination(ApplicationHandler.AppState a, ApplicationHandler.AppState b) {
        String n1 = a.name != null ? a.name : "User 1";
        String n2 = b.name != null ? b.name : "User 2";

        String rawA = a.sect != null ? a.sect.trim() : null;
        String rawB = b.sect != null ? b.sect.trim() : null;
        String denomA = rawA != null ? DenominationCompatibility.normalizeDenomination(rawA) : null;
        String denomB = rawB != null ? DenominationCompatibility.normalizeDenomination(rawB) : null;

        if (denomA == null || denomB == null) {
            return new ScoreDetail(0,
                "**" + n1 + "**: " + (rawA != null ? rawA : "N/A") + "\n"
                + "**" + n2 + "**: " + (rawB != null ? rawB : "N/A") + "\n"
                + "No denomination data available.");
        }

        int score = 0;
        String compatNote;

        if (denomA.equalsIgnoreCase(denomB)) {
            score = 20;
            compatNote = "Same denomination ✅";
        } else {
            List<String> compatA = DenominationCompatibility.getCompatibleDenominations(denomA, false);
            List<String> compatB = DenominationCompatibility.getCompatibleDenominations(denomB, false);

            boolean bInA = compatA.stream().anyMatch(c -> c.equalsIgnoreCase(denomB));
            boolean aInB = compatB.stream().anyMatch(c -> c.equalsIgnoreCase(denomA));

            if (bInA && aInB) {
                score = 20;
                compatNote = "Mutually compatible ✅";
            } else if (bInA) {
                score = 12;
                compatNote = denomB + " lists " + denomA + " as compatible (one-way) ⚠️";
            } else if (aInB) {
                score = 12;
                compatNote = denomA + " lists " + denomB + " as compatible (one-way) ⚠️";
            } else {
                score = -5;
                compatNote = "No listed compatibility (-5) ❌";
            }
        }

        boolean aPrefsB = matchesTargetSect(a.targetSect, denomB);
        boolean bPrefsA = matchesTargetSect(b.targetSect, denomA);
        if (aPrefsB) score += 5;
        if (bPrefsA) score += 5;

        String detail = "**" + n1 + "**: " + denomA + "\n"
            + "**" + n2 + "**: " + denomB + "\n"
            + compatNote + "\n"
            + n1 + " prefers " + denomB + ": " + (aPrefsB ? "✅" : "❌") + "\n"
            + n2 + " prefers " + denomA + ": " + (bPrefsA ? "✅" : "❌");

        return new ScoreDetail(score, detail);
    }

    private static boolean matchesTargetSect(String targetSect, String denom) {
        if (targetSect == null || denom == null) return false;
        for (String part : targetSect.split("[,;]")) {
            String normalized = DenominationCompatibility.normalizeDenomination(part.trim());
            if (denom.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    // ─── Age scoring (0–50) ───────────────────────────────────────────────────

    public static ScoreDetail scoreAge(ApplicationHandler.AppState a, ApplicationHandler.AppState b) {
        String n1 = a.name != null ? a.name : "User 1";
        String n2 = b.name != null ? b.name : "User 2";

        int ageA = calculateAge(a.birthday);
        int ageB = calculateAge(b.birthday);
        int[] rangeA = parseAgeRange(a.targetAge);
        int[] rangeB = parseAgeRange(b.targetAge);

        if (rangeA == null && rangeB == null)
            return new ScoreDetail(0, "No age preferences set for either user.");

        int half = 25;
        int scoreA = 0, scoreB = 0;
        String noteA, noteB;

        if (rangeA != null && ageB > 0) {
            int yr = yearsOutside(ageB, rangeA);
            if (yr == 0) {
                scoreA = half;
                noteA = n2 + " (age " + ageB + ") is within " + n1 + "'s range ("
                    + rangeA[0] + "–" + rangeA[1] + ") ✅";
            } else {
                scoreA = Math.max(0, half - yr * 2);
                noteA = n2 + " (age " + ageB + ") is " + yr + "yr outside " + n1 + "'s range ("
                    + rangeA[0] + "–" + rangeA[1] + ") ❌";
            }
        } else {
            noteA = n1 + " has no age preference set";
        }

        if (rangeB != null && ageA > 0) {
            int yr = yearsOutside(ageA, rangeB);
            if (yr == 0) {
                scoreB = half;
                noteB = n1 + " (age " + ageA + ") is within " + n2 + "'s range ("
                    + rangeB[0] + "–" + rangeB[1] + ") ✅";
            } else {
                scoreB = Math.max(0, half - yr * 2);
                noteB = n1 + " (age " + ageA + ") is " + yr + "yr outside " + n2 + "'s range ("
                    + rangeB[0] + "–" + rangeB[1] + ") ❌";
            }
        } else {
            noteB = n2 + " has no age preference set";
        }

        return new ScoreDetail(scoreA + scoreB, noteA + "\n" + noteB);
    }

    private static int yearsOutside(int age, int[] range) {
        if (age < range[0]) return range[0] - age;
        if (age > range[1]) return age - range[1];
        return 0;
    }

    private static int[] parseAgeRange(String targetAge) {
        if (targetAge == null || targetAge.trim().isEmpty()) return null;
        targetAge = targetAge.trim();
        if (targetAge.contains("-")) {
            String[] p = targetAge.split("-", 2);
            try { return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())}; }
            catch (Exception e) { return null; }
        }
        try { int v = Integer.parseInt(targetAge); return new int[]{v, v}; }
        catch (Exception e) { return null; }
    }

    private static int calculateAge(String birthday) {
        if (birthday == null) return 0;
        try {
            String[] p = birthday.split("/");
            LocalDate bd = LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            return (int) ChronoUnit.YEARS.between(bd, LocalDate.now());
        } catch (Exception e) { return 0; }
    }

    // ─── Distance / geography scoring (−15 to +10) ───────────────────────────

    private enum Continent { NA, SA, EU, AF, AS, OC }

    // Intercontinental penalty matrix indexed by Continent.ordinal()
    private static final int[][] CONTINENT_PENALTY = {
        //  NA   SA   EU   AF   AS   OC
        {    0,  -5,  -8, -10, -12, -15 }, // NA
        {   -5,   0, -10, -10, -12, -12 }, // SA
        {   -8, -10,   0,  -5,  -8, -12 }, // EU
        {  -10, -10,  -5,   0,  -8, -12 }, // AF
        {  -12, -12,  -8,  -8,   0,  -5 }, // AS
        {  -15, -12, -12, -12,  -5,   0 }, // OC
    };

    public static ScoreDetail scoreDistance(ApplicationHandler.AppState a, ApplicationHandler.AppState b) {
        String n1 = a.name != null ? a.name : "User 1";
        String n2 = b.name != null ? b.name : "User 2";
        String cA = a.country != null ? a.country.trim() : "";
        String cB = b.country != null ? b.country.trim() : "";

        if (cA.isEmpty() && cB.isEmpty())
            return new ScoreDetail(0, "No country data available.");

        if (!cA.isEmpty() && cA.equalsIgnoreCase(cB))
            return new ScoreDetail(MAX_DIST, n1 + " and " + n2 + " are both in **" + cA + "** ✅");

        Continent contA = cA.isEmpty() ? null : getContinent(cA);
        Continent contB = cB.isEmpty() ? null : getContinent(cB);

        String labelA = cA.isEmpty() ? "Unknown" : cA + (contA != null ? " (" + contA + ")" : " (unknown region)");
        String labelB = cB.isEmpty() ? "Unknown" : cB + (contB != null ? " (" + contB + ")" : " (unknown region)");

        if (contA == null || contB == null) {
            return new ScoreDetail(0,
                "**" + n1 + "**: " + labelA + "\n**" + n2 + "**: " + labelB
                + "\nContinent not recognized; no distance penalty applied.");
        }

        if (contA == contB) {
            return new ScoreDetail(5,
                "**" + n1 + "**: " + labelA + "\n**" + n2 + "**: " + labelB
                + "\nSame continent (+5)");
        }

        int penalty = CONTINENT_PENALTY[contA.ordinal()][contB.ordinal()];
        return new ScoreDetail(penalty,
            "**" + n1 + "**: " + labelA + "\n**" + n2 + "**: " + labelB
            + "\nDifferent continents (" + (penalty >= 0 ? "+" : "") + penalty + " pts)");
    }

    // ─── Deal-breaker scoring (≤0, floor −30) ────────────────────────────────

    public static ScoreDetail scoreDealBreakers(ApplicationHandler.AppState a, ApplicationHandler.AppState b) {
        String n1 = a.name != null ? a.name : "User 1";
        String n2 = b.name != null ? b.name : "User 2";

        String dbA = a.dealBreakers != null ? a.dealBreakers.toLowerCase() : "";
        String dbB = b.dealBreakers != null ? b.dealBreakers.toLowerCase() : "";
        String profileA = ((a.hobbies != null ? a.hobbies : "") + " " + (a.weaknesses != null ? a.weaknesses : "")).toLowerCase();
        String profileB = ((b.hobbies != null ? b.hobbies : "") + " " + (b.weaknesses != null ? b.weaknesses : "")).toLowerCase();

        List<String> hitsAtoB = new ArrayList<>();
        List<String> hitsBtoA = new ArrayList<>();

        for (int i = 0; i < DB_TRIGGERS.length; i++) {
            if (containsAny(dbA, DB_TRIGGERS[i]) && containsAny(profileB, DB_SIGNALS[i]))
                hitsAtoB.add(DB_LABELS[i]);
            if (containsAny(dbB, DB_TRIGGERS[i]) && containsAny(profileA, DB_SIGNALS[i]))
                hitsBtoA.add(DB_LABELS[i]);
        }

        int scoreAtoB = Math.max(-15, hitsAtoB.size() * -10);
        int scoreBtoA = Math.max(-15, hitsBtoA.size() * -10);
        int total     = Math.max(-30, scoreAtoB + scoreBtoA);

        StringBuilder sb = new StringBuilder();
        if (hitsAtoB.isEmpty() && hitsBtoA.isEmpty()) {
            sb.append("No deal breaker conflicts detected ✅");
        } else {
            if (!hitsAtoB.isEmpty())
                sb.append(n1).append(" flags ").append(n2).append(": ").append(String.join(", ", hitsAtoB));
            if (!hitsBtoA.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(n2).append(" flags ").append(n1).append(": ").append(String.join(", ", hitsBtoA));
            }
        }
        return new ScoreDetail(total, sb.toString());
    }

    // ─── Values / lookFor scoring (0–20) ─────────────────────────────────────

    public static ScoreDetail scoreValues(ApplicationHandler.AppState a, ApplicationHandler.AppState b) {
        String n1 = a.name != null ? a.name : "User 1";
        String n2 = b.name != null ? b.name : "User 2";

        String lookA = a.lookFor != null ? a.lookFor.toLowerCase() : "";
        String lookB = b.lookFor != null ? b.lookFor.toLowerCase() : "";
        String profileA = ((a.strengths != null ? a.strengths : "") + " " + (a.hobbies != null ? a.hobbies : "")).toLowerCase();
        String profileB = ((b.strengths != null ? b.strengths : "") + " " + (b.hobbies != null ? b.hobbies : "")).toLowerCase();

        List<String> matchesAtoB = new ArrayList<>();
        List<String> matchesBtoA = new ArrayList<>();

        for (int i = 0; i < VAL_TRIGGERS.length; i++) {
            if (containsAny(lookA, VAL_TRIGGERS[i]) && containsAny(profileB, VAL_SIGNALS[i]))
                matchesAtoB.add(VAL_LABELS[i]);
            if (containsAny(lookB, VAL_TRIGGERS[i]) && containsAny(profileA, VAL_SIGNALS[i]))
                matchesBtoA.add(VAL_LABELS[i]);
        }

        int scoreA = Math.min(10, matchesAtoB.size() * 4);
        int scoreB = Math.min(10, matchesBtoA.size() * 4);
        int total  = scoreA + scoreB;

        StringBuilder sb = new StringBuilder();
        if (matchesAtoB.isEmpty() && matchesBtoA.isEmpty()) {
            sb.append("No value alignment detected.");
        } else {
            if (!matchesAtoB.isEmpty())
                sb.append(n2).append(" fits what ").append(n1).append(" looks for: ").append(String.join(", ", matchesAtoB));
            if (!matchesBtoA.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(n1).append(" fits what ").append(n2).append(" looks for: ").append(String.join(", ", matchesBtoA));
            }
        }
        return new ScoreDetail(total, sb.toString());
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String t : terms) if (text.contains(t)) return true;
        return false;
    }

    private static Continent getContinent(String country) {
        if (country == null) return null;
        String c = country.toLowerCase().trim();
        if (c.startsWith("the ")) c = c.substring(4);

        switch (c) {
            // North America
            case "usa": case "united states": case "united states of america": case "us":
            case "canada": case "mexico": case "cuba": case "jamaica": case "haiti":
            case "dominican republic": case "puerto rico": case "trinidad": case "trinidad and tobago":
            case "barbados": case "bahamas": case "belize": case "costa rica": case "panama":
            case "honduras": case "guatemala": case "el salvador": case "nicaragua":
                return Continent.NA;

            // South America
            case "brazil": case "colombia": case "argentina": case "peru": case "venezuela":
            case "chile": case "ecuador": case "bolivia": case "paraguay": case "uruguay":
            case "guyana": case "suriname": case "french guiana":
                return Continent.SA;

            // Europe
            case "uk": case "united kingdom": case "england": case "scotland": case "wales":
            case "france": case "germany": case "italy": case "spain": case "portugal":
            case "netherlands": case "belgium": case "switzerland": case "austria":
            case "sweden": case "norway": case "denmark": case "finland": case "poland":
            case "czech republic": case "czechia": case "slovakia": case "hungary":
            case "romania": case "bulgaria": case "greece": case "croatia": case "serbia":
            case "ukraine": case "ireland": case "russia": case "georgia": case "armenia":
            case "azerbaijan": case "latvia": case "lithuania": case "estonia":
            case "slovenia": case "albania": case "north macedonia": case "montenegro":
            case "bosnia": case "moldova": case "belarus": case "luxembourg":
            case "iceland": case "liechtenstein": case "monaco": case "andorra":
                return Continent.EU;

            // Africa
            case "nigeria": case "ethiopia": case "south africa": case "kenya":
            case "tanzania": case "uganda": case "ghana": case "cameroon":
            case "mozambique": case "madagascar": case "zimbabwe": case "zambia":
            case "senegal": case "mali": case "guinea": case "rwanda": case "burundi":
            case "somalia": case "sudan": case "south sudan": case "egypt": case "libya":
            case "tunisia": case "algeria": case "morocco": case "angola": case "malawi":
            case "niger": case "chad": case "dr congo": case "democratic republic of congo":
            case "republic of congo": case "congo": case "ivory coast": case "benin":
            case "togo": case "eritrea": case "djibouti": case "namibia": case "botswana":
            case "lesotho": case "eswatini": case "sierra leone": case "liberia":
            case "mauritania": case "gambia":
                return Continent.AF;

            // Asia (includes Middle East)
            case "china": case "india": case "japan": case "south korea": case "north korea":
            case "indonesia": case "philippines": case "vietnam": case "thailand":
            case "malaysia": case "singapore": case "pakistan": case "bangladesh":
            case "sri lanka": case "nepal": case "myanmar": case "taiwan": case "hong kong":
            case "cambodia": case "laos": case "mongolia": case "kazakhstan":
            case "uzbekistan": case "kyrgyzstan": case "tajikistan": case "turkmenistan":
            case "afghanistan": case "saudi arabia": case "iran": case "iraq":
            case "turkey": case "uae": case "united arab emirates": case "jordan":
            case "kuwait": case "bahrain": case "qatar": case "oman": case "yemen":
            case "syria": case "lebanon": case "israel": case "palestine": case "cyprus":
            case "brunei": case "east timor": case "timor-leste":
                return Continent.AS;

            // Oceania
            case "australia": case "new zealand": case "papua new guinea": case "fiji":
            case "samoa": case "tonga": case "solomon islands": case "vanuatu":
            case "kiribati": case "micronesia": case "palau":
                return Continent.OC;

            default:
                return null;
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private static ApplicationHandler.AppState loadProfile(String userId) {
        File file = new File(PROFILES_DIR + userId + ".json");
        if (!file.exists()) return null;
        try {
            return GSON.fromJson(new FileReader(file), ApplicationHandler.AppState.class);
        } catch (Exception e) {
            System.err.println("CompatAlgo: Failed to read profile for " + userId + ": " + e.getMessage());
            return null;
        }
    }
}
