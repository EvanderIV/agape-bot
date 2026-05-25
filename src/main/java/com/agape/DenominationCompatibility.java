package com.agape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DenominationCompatibility {

    private static final Map<String, List<String>> COMPATIBILITY_MAP = new HashMap<>();

    static {
        // --- 1. CATHOLIC CHURCH ---
        COMPATIBILITY_MAP.put("Catholic Church", Arrays.asList(
            "Catholic Church",
            "Roman Catholic Church - The primary expression of the Catholic tradition",
            "Eastern Catholic Churches - Full communion with the Pope but different liturgy",
            "Eastern Orthodox - Shared ancient apostolic succession and sacraments",
            "Anglican - Similar traditional liturgy and high church structures"
        ));
        COMPATIBILITY_MAP.put("Roman Catholic Church", Arrays.asList(
            "Roman Catholic Church",
            "Eastern Catholic Churches - Full communion with the Pope but different liturgy",
            "Eastern Orthodox - Shared ancient apostolic succession and sacraments",
            "Anglican - Similar traditional liturgy and high church structures"
        ));
        COMPATIBILITY_MAP.put("Eastern Catholic Churches", Arrays.asList(
            "Eastern Catholic Churches",
            "Roman Catholic Church - Full theological communion under the Pope",
            "Eastern Orthodox - Nearly identical liturgical practices and heritage",
            "Oriental Orthodox - Shared ancient Eastern Christian traditions"
        ));
        COMPATIBILITY_MAP.put("Maronite Catholic Church", Arrays.asList(
            "Maronite Catholic Church",
            "Eastern Catholic Churches - Shared Eastern liturgical rites in communion with Rome",
            "Roman Catholic Church - Full theological communion under the Pope",
            "Syriac Orthodox Church - Deep shared historical and regional heritage"
        ));
        COMPATIBILITY_MAP.put("Melkite Greek Catholic Church", Arrays.asList(
            "Melkite Greek Catholic Church",
            "Eastern Catholic Churches - Shared Eastern liturgical rites in communion with Rome",
            "Antiochian Orthodox Church - Deep shared regional and liturgical heritage",
            "Roman Catholic Church - Full theological communion under the Pope"
        ));
        COMPATIBILITY_MAP.put("Ukrainian Greek Catholic Church", Arrays.asList(
            "Ukrainian Greek Catholic Church",
            "Eastern Catholic Churches - Shared Eastern liturgical rites in communion with Rome",
            "Russian Orthodox Church - Shared Byzantine liturgical heritage",
            "Roman Catholic Church - Full theological communion under the Pope"
        ));
        COMPATIBILITY_MAP.put("Syro-Malabar Catholic Church", Arrays.asList(
            "Syro-Malabar Catholic Church",
            "Eastern Catholic Churches - Shared Eastern liturgical rites in communion with Rome",
            "Malankara Orthodox Syrian Church - Shared ancient Indian Christian heritage",
            "Roman Catholic Church - Full theological communion under the Pope"
        ));

        // --- 2. EASTERN ORTHODOX ---
        COMPATIBILITY_MAP.put("Eastern Orthodox", Arrays.asList(
            "Eastern Orthodox",
            "Oriental Orthodox - Shared ancient traditions and sacramental focus",
            "Catholic Church - Shared ancient apostolic succession and sacraments",
            "Anglican - Similar traditional liturgical structures"
        ));
        COMPATIBILITY_MAP.put("Greek Orthodox Church", Arrays.asList(
            "Greek Orthodox Church",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion",
            "Antiochian Orthodox Church - Shared Byzantine liturgical heritage",
            "Catholic Church - Shared ancient apostolic succession and sacraments"
        ));
        COMPATIBILITY_MAP.put("Russian Orthodox Church", Arrays.asList(
            "Russian Orthodox Church",
            "Serbian Orthodox Church - Shared Slavic Orthodox traditions and communion",
            "Orthodox Church in America - Direct historical and theological origins",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion"
        ));
        COMPATIBILITY_MAP.put("Serbian Orthodox Church", Arrays.asList(
            "Serbian Orthodox Church",
            "Russian Orthodox Church - Shared Slavic Orthodox traditions and communion",
            "Romanian Orthodox Church - Deep shared Eastern European Orthodox ties",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion"
        ));
        COMPATIBILITY_MAP.put("Bulgarian Orthodox Church", Arrays.asList(
            "Bulgarian Orthodox Church",
            "Serbian Orthodox Church - Shared Eastern European Orthodox heritage",
            "Russian Orthodox Church - Shared Slavic Orthodox traditions and communion",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion"
        ));
        COMPATIBILITY_MAP.put("Romanian Orthodox Church", Arrays.asList(
            "Romanian Orthodox Church",
            "Serbian Orthodox Church - Deep shared Eastern European Orthodox ties",
            "Bulgarian Orthodox Church - Shared Eastern European Orthodox heritage",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion"
        ));
        COMPATIBILITY_MAP.put("Georgian Orthodox Church", Arrays.asList(
            "Georgian Orthodox Church",
            "Russian Orthodox Church - Shared regional Orthodox traditions",
            "Greek Orthodox Church - Shared Byzantine liturgical heritage",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion"
        ));
        COMPATIBILITY_MAP.put("Antiochian Orthodox Church", Arrays.asList(
            "Antiochian Orthodox Church",
            "Greek Orthodox Church - Shared Byzantine liturgical heritage",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion",
            "Melkite Greek Catholic Church - Deep shared regional and liturgical heritage"
        ));
        COMPATIBILITY_MAP.put("Orthodox Church in America", Arrays.asList(
            "Orthodox Church in America",
            "Russian Orthodox Church - Direct historical and theological origins",
            "Eastern Orthodox - Core member of the Eastern Orthodox communion",
            "Anglican - Historically friendly dialogue and traditional liturgy"
        ));

        // --- 3. PROTESTANT ---
        COMPATIBILITY_MAP.put("Protestant", Arrays.asList(
            "Protestant",
            "Evangelical - Shared focus on personal faith and scripture",
            "Non-denominational - Common origins in the Reformation",
            "Baptist - Shared emphasis on biblical authority"
        ));

        // 3.1 Lutheran
        COMPATIBILITY_MAP.put("Lutheran", Arrays.asList(
            "Lutheran",
            "Evangelical Lutheran Church in America - Major expression of the Lutheran tradition",
            "Anglican - Similar traditional liturgy and sacraments",
            "Presbyterian Church - Shared focus on historical confessions"
        ));
        COMPATIBILITY_MAP.put("Evangelical Lutheran Church in America", Arrays.asList(
            "Evangelical Lutheran Church in America",
            "Episcopal Church (U.S.) - Full communion and shared liturgical structure",
            "Presbyterian Church - Full communion and shared Protestant roots",
            "United Methodist Church - Full communion and shared mainline heritage"
        ));
        COMPATIBILITY_MAP.put("Lutheran Church-Missouri Synod", Arrays.asList(
            "Lutheran Church-Missouri Synod",
            "Wisconsin Evangelical Lutheran Synod - Deep shared conservative Lutheran theology",
            "Lutheran - Core adherence to the Book of Concord",
            "Presbyterian Church - Shared focus on historical confessions"
        ));
        COMPATIBILITY_MAP.put("Wisconsin Evangelical Lutheran Synod", Arrays.asList(
            "Wisconsin Evangelical Lutheran Synod",
            "Lutheran Church-Missouri Synod - Deep shared conservative Lutheran theology",
            "Lutheran - Core adherence to the Book of Concord",
            "Baptist - Shared emphasis on biblical inerrancy"
        ));

        // 3.2 Calvinist
        COMPATIBILITY_MAP.put("Calvinist", Arrays.asList(
            "Calvinist",
            "Presbyterian Church - Primary expression of Calvinist theology",
            "Christian Reformed Church - Deep shared Reformed theological roots",
            "Reformed Church in America - Deep shared Reformed theological roots"
        ));
        COMPATIBILITY_MAP.put("Presbyterian Church", Arrays.asList(
            "Presbyterian Church",
            "Reformed Church in America - Deep shared Calvinist theological roots",
            "Evangelical Lutheran Church in America - Full communion and shared Protestant roots",
            "United Methodist Church - Similar historical mainline Protestant origins"
        ));
        COMPATIBILITY_MAP.put("Reformed Church in America", Arrays.asList(
            "Reformed Church in America",
            "Christian Reformed Church - Direct historical and theological alignment",
            "Presbyterian Church - Deep shared Calvinist theological roots",
            "United Church of Christ - Full communion and shared mainline heritage"
        ));
        COMPATIBILITY_MAP.put("Christian Reformed Church", Arrays.asList(
            "Christian Reformed Church",
            "Reformed Church in America - Direct historical and theological alignment",
            "Presbyterian Church - Deep shared Calvinist theological roots",
            "Calvinist - Core adherence to Reformed confessions"
        ));

        // 3.3 Anglican
        COMPATIBILITY_MAP.put("Anglican", Arrays.asList(
            "Anglican",
            "Church of England - The mother church of the Anglican Communion",
            "Episcopal Church (U.S.) - The primary American branch of the Anglican Communion",
            "Catholic Church - Shared ancient apostolic traditions and liturgy",
            "Lutheran - Similar traditional liturgy and sacraments"
        ));
        COMPATIBILITY_MAP.put("Church of England", Arrays.asList(
            "Church of England",
            "Anglican - The mother church of the Anglican Communion",
            "Catholic Church - Shared ancient apostolic traditions and liturgy",
            "Methodist - Deep historical ties via John Wesley"
        ));
        COMPATIBILITY_MAP.put("Episcopal Church (U.S.)", Arrays.asList(
            "Episcopal Church (U.S.)",
            "Evangelical Lutheran Church in America - Full communion and shared liturgical structure",
            "Church of England - Direct historical and theological communion",
            "United Methodist Church - Similar historical mainline Protestant origins"
        ));
        COMPATIBILITY_MAP.put("Anglican Church in North America", Arrays.asList(
            "Anglican Church in North America",
            "Anglican - Shared Anglican liturgy and theological heritage",
            "Lutheran Church-Missouri Synod - Shared conservative liturgical values",
            "Southern Baptist Convention - Shared conservative theological alignment"
        ));

        // 3.4 Baptist
        COMPATIBILITY_MAP.put("Baptist", Arrays.asList(
            "Baptist",
            "Southern Baptist Convention - Major expression of the Baptist tradition",
            "Non-denominational - Shared focus on local church autonomy",
            "Churches of Christ - Similar views on believer's baptism"
        ));
        COMPATIBILITY_MAP.put("Southern Baptist Convention", Arrays.asList(
            "Southern Baptist Convention",
            "Baptist - Direct historical and theological alignment",
            "Non-denominational - Shared focus on local church autonomy",
            "Assemblies of God - Shared conservative evangelical values"
        ));
        COMPATIBILITY_MAP.put("American Baptist Churches U.S.A.", Arrays.asList(
            "American Baptist Churches U.S.A.",
            "National Baptist Convention - Shared historical Baptist roots in America",
            "United Methodist Church - Similar historical mainline Protestant origins",
            "Disciples of Christ - Shared emphasis on local church autonomy"
        ));
        COMPATIBILITY_MAP.put("National Baptist Convention", Arrays.asList(
            "National Baptist Convention",
            "African Methodist Episcopal Church - Shared African American church heritage",
            "American Baptist Churches U.S.A. - Shared historical Baptist roots in America",
            "Church of God in Christ - Shared cultural worship expressions"
        ));

        // 3.5 Methodist
        COMPATIBILITY_MAP.put("Methodist", Arrays.asList(
            "Methodist",
            "United Methodist Church - Major expression of the Methodist tradition",
            "Nazarene - Shared Wesleyan-Holiness theological roots",
            "Anglican - Deep historical ties via John Wesley"
        ));
        COMPATIBILITY_MAP.put("United Methodist Church", Arrays.asList(
            "United Methodist Church",
            "African Methodist Episcopal Church - Direct historical and theological ties",
            "Evangelical Lutheran Church in America - Full communion and shared mainline heritage",
            "Presbyterian Church - Similar historical mainline Protestant origins"
        ));
        COMPATIBILITY_MAP.put("African Methodist Episcopal Church", Arrays.asList(
            "African Methodist Episcopal Church",
            "United Methodist Church - Direct historical and theological ties",
            "National Baptist Convention - Shared African American church heritage",
            "Methodist - Core adherence to Wesleyan theology"
        ));
        COMPATIBILITY_MAP.put("Free Methodist Church", Arrays.asList(
            "Free Methodist Church",
            "Methodist - Core adherence to Wesleyan theology",
            "Nazarene - Shared Wesleyan-Holiness theological roots",
            "Assemblies of God - Shared evangelical and holiness roots"
        ));

        // 3.6 Pentecostal
        COMPATIBILITY_MAP.put("Pentecostal", Arrays.asList(
            "Pentecostal",
            "Assemblies of God - Major expression of the Pentecostal tradition",
            "Charismatic - Shared emphasis on spiritual gifts",
            "Non-denominational - Common expressive contemporary worship"
        ));
        COMPATIBILITY_MAP.put("Assemblies of God", Arrays.asList(
            "Assemblies of God",
            "Pentecostal - Broad shared theological movement",
            "International Church of the Foursquare Gospel - Direct historical and theological ties",
            "Non-denominational - Similar contemporary church structures"
        ));
        COMPATIBILITY_MAP.put("Church of God", Arrays.asList(
            "Church of God",
            "Pentecostal - Broad shared theological movement",
            "Assemblies of God - Shared emphasis on spiritual gifts",
            "Free Methodist Church - Shared Wesleyan-Holiness theological roots"
        ));
        COMPATIBILITY_MAP.put("United Pentecostal Church International", Arrays.asList(
            "United Pentecostal Church International",
            "Pentecostal - Similar expressive contemporary worship styles",
            "Assemblies of God - Shared emphasis on spiritual gifts",
            "Non-denominational - Shared focus on local church autonomy"
        ));
        COMPATIBILITY_MAP.put("International Church of the Foursquare Gospel", Arrays.asList(
            "International Church of the Foursquare Gospel",
            "Assemblies of God - Direct historical and theological ties",
            "Pentecostal - Broad shared theological movement",
            "Non-denominational - Common expressive contemporary worship"
        ));

        // 3.7 Other Notable Protestant Movements
        COMPATIBILITY_MAP.put("Anabaptist", Arrays.asList(
            "Anabaptist",
            "Quaker - Shared historic peace church traditions",
            "Baptist - Shared views on believer's baptism and separation of church and state",
            "Non-denominational - Shared focus on local church autonomy"
        ));

        // 3.8 Restorationist
        COMPATIBILITY_MAP.put("Restorationist", Arrays.asList(
            "Restorationist",
            "Churches of Christ - Major expression of the Restoration movement",
            "Disciples of Christ - Direct historical and theological ties",
            "Baptist - Shared views on believer's baptism"
        ));
        COMPATIBILITY_MAP.put("Churches of Christ", Arrays.asList(
            "Churches of Christ",
            "Christian Church - Direct historical and theological ties",
            "Disciples of Christ - Shared origins in the Stone-Campbell movement",
            "Baptist - Similar views on believer's baptism and autonomy"
        ));
        COMPATIBILITY_MAP.put("Disciples of Christ", Arrays.asList(
            "Disciples of Christ",
            "Christian Church - Shared origins in the Stone-Campbell movement",
            "United Church of Christ - Full communion and shared mainline heritage",
            "American Baptist Churches U.S.A. - Shared emphasis on local church autonomy"
        ));
        COMPATIBILITY_MAP.put("Christian Church", Arrays.asList(
            "Christian Church",
            "Churches of Christ - Direct historical and theological ties",
            "Disciples of Christ - Shared origins in the Stone-Campbell movement",
            "Non-denominational - Shared focus on local church autonomy"
        ));
        COMPATIBILITY_MAP.put("Adventist", Arrays.asList(
            "Adventist",
            "Restorationist - Shared roots in 19th-century American movements",
            "Baptist - Shared views on believer's baptism",
            "Methodist - Shared historical evangelical and holiness roots"
        ));
        COMPATIBILITY_MAP.put("Quaker", Arrays.asList(
            "Quaker",
            "Anabaptist - Shared historic peace church traditions",
            "Methodist - Shared historical roots in English dissenting movements",
            "Non-denominational - Shared focus on personal spiritual experience"
        ));

        // --- 4. ORIENTAL ORTHODOX ---
        COMPATIBILITY_MAP.put("Oriental Orthodox", Arrays.asList(
            "Oriental Orthodox",
            "Coptic Orthodox Church - Major expression of the Oriental Orthodox communion",
            "Eastern Orthodox - Shared ancient traditions and sacramental focus",
            "Catholic Church - Shared ancient apostolic succession and sacraments"
        ));
        COMPATIBILITY_MAP.put("Coptic Orthodox Church", Arrays.asList(
            "Coptic Orthodox Church",
            "Ethiopian Orthodox Church - Deep shared historical and theological communion",
            "Oriental Orthodox - Core member of the Oriental Orthodox communion",
            "Eastern Orthodox - Shared ancient Eastern Christian traditions"
        ));
        COMPATIBILITY_MAP.put("Armenian Orthodox Church", Arrays.asList(
            "Armenian Orthodox Church",
            "Syriac Orthodox Church - Core member of the Oriental Orthodox communion",
            "Oriental Orthodox - Core member of the Oriental Orthodox communion",
            "Catholic Church - Friendly historical dialogue and apostolic succession"
        ));
        COMPATIBILITY_MAP.put("Ethiopian Orthodox Church", Arrays.asList(
            "Ethiopian Orthodox Church",
            "Coptic Orthodox Church - Deep shared historical and theological communion",
            "Oriental Orthodox - Core member of the Oriental Orthodox communion",
            "Eastern Orthodox - Shared ancient Eastern Christian traditions"
        ));
        COMPATIBILITY_MAP.put("Syriac Orthodox Church", Arrays.asList(
            "Syriac Orthodox Church",
            "Malankara Orthodox Syrian Church - Direct historical and theological origins",
            "Oriental Orthodox - Core member of the Oriental Orthodox communion",
            "Maronite Catholic Church - Deep shared historical and regional heritage"
        ));
        COMPATIBILITY_MAP.put("Malankara Orthodox Syrian Church", Arrays.asList(
            "Malankara Orthodox Syrian Church",
            "Syriac Orthodox Church - Direct historical and theological origins",
            "Syro-Malabar Catholic Church - Shared ancient Indian Christian heritage",
            "Oriental Orthodox - Core member of the Oriental Orthodox communion"
        ));

        // --- 5. ASSYRIAN CHURCH OF THE EAST ---
        COMPATIBILITY_MAP.put("Assyrian Church of the East", Arrays.asList(
            "Assyrian Church of the East",
            "Ancient Church of the East - Direct historical and theological origins",
            "Chaldean Syrian Church - Shared ancient East Syriac liturgical heritage",
            "Oriental Orthodox - Shared ancient Eastern Christian traditions"
        ));
        COMPATIBILITY_MAP.put("Ancient Church of the East", Arrays.asList(
            "Ancient Church of the East",
            "Assyrian Church of the East - Direct historical and theological origins",
            "Chaldean Syrian Church - Shared ancient East Syriac liturgical heritage",
            "Oriental Orthodox - Shared ancient Eastern Christian traditions"
        ));
        COMPATIBILITY_MAP.put("Chaldean Syrian Church", Arrays.asList(
            "Chaldean Syrian Church",
            "Assyrian Church of the East - Shared ancient East Syriac liturgical heritage",
            "Syro-Malabar Catholic Church - Shared historical Indian Christian heritage",
            "Oriental Orthodox - Shared ancient Eastern Christian traditions"
        ));
    }

    /**
     * Returns the canonical denomination name for a user's raw input.
     * If the input fuzzy-matches a known denomination, the corrected name is returned.
     * If no match is found, the original trimmed input is returned unchanged.
     */
    /**
     * Corrects slight typos in a user-entered denomination name.
     * Only auto-corrects if the input is very close to a known denomination (score ≥ 0.82,
     * roughly ≤1–2 character edits). Intentional or unrecognized names are returned as-is.
     * Substring matching is intentionally disabled here to avoid false corrections on
     * short fragments (e.g. "Church" matching "Catholic Church").
     */
    public static String normalizeDenomination(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String matched = findClosestMatch(input.trim(), false, 0.82);
        return matched != null ? matched : input.trim();
    }

    /**
     * Takes a raw user input string, fixes spelling, and returns an ordered list of compatible denominations.
     * Index 0 of each map entry is always the denomination itself and is excluded from results.
     * @param input The user's typed denomination.
     * @param includeReasons If true, includes the " - Reason" string. If false, returns only the denomination names.
     */
    public static List<String> getCompatibleDenominations(String input, boolean includeReasons) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String matchedDenomination = findClosestMatch(input.trim());
        if (matchedDenomination == null) {
            return Collections.emptyList();
        }

        List<String> rawList = COMPATIBILITY_MAP.get(matchedDenomination);

        // Start at index 1 — index 0 is always the denomination itself, not a suggestion
        List<String> result = new ArrayList<>();
        for (int i = 1; i < rawList.size(); i++) {
            String entry = rawList.get(i);
            if (includeReasons) {
                result.add(entry);
            } else {
                int sep = entry.indexOf(" - ");
                result.add(sep != -1 ? entry.substring(0, sep).trim() : entry.trim());
            }
        }
        return result;
    }

    /**
     * Fuzzy-matches input against all COMPATIBILITY_MAP keys.
     * Used for broad denomination lookups (compatibility suggestions).
     * Strategy: exact → substring → word-level Levenshtein at 0.6.
     */
    private static String findClosestMatch(String input) {
        return findClosestMatch(input, true, 0.6);
    }

    /**
     * Core fuzzy-match implementation.
     *
     * @param input             Raw user input.
     * @param useSubstringCheck Whether to match when input is a substring of a key (or vice versa).
     *                          Disable for typo-correction to avoid false matches on short fragments.
     * @param minScore          Minimum average token-similarity (0–1) required to accept a match.
     *                          Use ~0.82 for typo correction (≤1–2 edits); 0.6 for broad lookup.
     */
    private static String findClosestMatch(String input, boolean useSubstringCheck, double minScore) {
        String normalized = input.toLowerCase().trim();

        // 1. Case-insensitive exact match
        for (String key : COMPATIBILITY_MAP.keySet()) {
            if (key.equalsIgnoreCase(normalized)) return key;
        }

        // 2. Substring: input is contained in a key, or vice versa (broad lookup only)
        if (useSubstringCheck) {
            for (String key : COMPATIBILITY_MAP.keySet()) {
                String keyLower = key.toLowerCase();
                if (keyLower.contains(normalized) || normalized.contains(keyLower)) return key;
            }
        }

        // 3. Word-level fuzzy match using Levenshtein similarity
        String[] inputTokens = normalized.split("\\s+");
        String bestKey = null;
        double bestScore = 0.0;

        for (String key : COMPATIBILITY_MAP.keySet()) {
            String[] keyTokens = key.toLowerCase().split("[\\s()]+");

            double tokenScore = 0.0;
            for (String inputToken : inputTokens) {
                double best = 0.0;
                for (String keyToken : keyTokens) {
                    if (keyToken.isEmpty()) continue;
                    int maxLen = Math.max(inputToken.length(), keyToken.length());
                    double sim = 1.0 - (double) levenshteinDistance(inputToken, keyToken) / maxLen;
                    if (sim > best) best = sim;
                }
                tokenScore += best;
            }

            // In strict normalization mode, divide by the larger token count so that a
            // short input (e.g. "Church") cannot score 1.0 against a long key
            // (e.g. "Catholic Church") just because one token matches.
            // In broad lookup mode, divide only by input length (existing behavior).
            int denominator = useSubstringCheck
                ? inputTokens.length
                : Math.max(inputTokens.length, keyTokens.length);
            tokenScore /= denominator;

            if (tokenScore > bestScore) {
                bestScore = tokenScore;
                bestKey = key;
            }
        }

        return bestScore >= minScore ? bestKey : null;
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    // --- TEST METHOD ---
    public static void main(String[] args) {
        // Test with a typo!
        String userInput = "episcpalian"; 
        
        // Let's test both boolean variants!
        List<String> withReasons = getCompatibleDenominations(userInput, true);
        List<String> withoutReasons = getCompatibleDenominations(userInput, false);
        
        System.out.println("Input: '" + userInput + "'\n");
        
        if (!withReasons.isEmpty()) {
            System.out.println("--- WITH REASONS (true) ---");
            for (int i = 0; i < withReasons.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + withReasons.get(i));
            }
            
            System.out.println("\n--- WITHOUT REASONS (false) ---");
            for (int i = 0; i < withoutReasons.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + withoutReasons.get(i));
            }
        } else {
            System.out.println("No valid match found.");
        }
    }
}
