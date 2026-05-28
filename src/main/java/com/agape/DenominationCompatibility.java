package com.agape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DenominationCompatibility {

    private static final Map<String, List<String>> COMPATIBILITY_MAP = new HashMap<>();

    static {
        // --- 1. CATHOLIC CHURCH ---
        map("Catholic",
            c("Roman Catholic", "The primary expression of the Catholic tradition"),
            c("Eastern Catholic Churches", "Full communion with the Pope but different liturgy"),
            c("Eastern Orthodox", "Shared ancient apostolic succession and sacraments"),
            c("Anglican", "Similar traditional liturgy and high church structures")
        );
        map("Roman Catholic",
            c("Eastern Catholic Churches", "Full communion with the Pope but different liturgy"),
            c("Eastern Orthodox", "Shared ancient apostolic succession and sacraments"),
            c("Anglican", "Similar traditional liturgy and high church structures")
        );
        map("Eastern Catholic Churches",
            c("Roman Catholic", "Full theological communion under the Pope"),
            c("Eastern Orthodox", "Nearly identical liturgical practices and heritage"),
            c("Oriental Orthodox", "Shared ancient Eastern Christian traditions")
        );
        map("Maronite Catholic",
            c("Eastern Catholic Churches", "Shared Eastern liturgical rites in communion with Rome"),
            c("Roman Catholic", "Full theological communion under the Pope"),
            c("Syriac Orthodox", "Deep shared historical and regional heritage")
        );
        map("Melkite Greek Catholic",
            c("Eastern Catholic Churches", "Shared Eastern liturgical rites in communion with Rome"),
            c("Antiochian Orthodox", "Deep shared regional and liturgical heritage"),
            c("Roman Catholic", "Full theological communion under the Pope")
        );
        map("Ukrainian Greek Catholic",
            c("Eastern Catholic Churches", "Shared Eastern liturgical rites in communion with Rome"),
            c("Russian Orthodox", "Shared Byzantine liturgical heritage"),
            c("Roman Catholic", "Full theological communion under the Pope")
        );
        map("Syro-Malabar Catholic",
            c("Eastern Catholic Churches", "Shared Eastern liturgical rites in communion with Rome"),
            c("Malankara Orthodox Syrian", "Shared ancient Indian Christian heritage"),
            c("Roman Catholic", "Full theological communion under the Pope")
        );
        map("Chaldean Catholic",
            c("Eastern Catholic Churches", "Shared Eastern liturgical rites in communion with Rome"),
            c("Assyrian Church of the East", "Shared East Syriac liturgical heritage"),
            c("Roman Catholic", "Full theological communion under the Pope")
        );

        // --- 2. EASTERN ORTHODOX ---
        map("Eastern Orthodox",
            c("Oriental Orthodox", "Shared ancient traditions and sacramental focus"),
            c("Catholic", "Shared ancient apostolic succession and sacraments"),
            c("Anglican", "Similar traditional liturgical structures")
        );
        map("Greek Orthodox",
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion"),
            c("Antiochian Orthodox", "Shared Byzantine liturgical heritage"),
            c("Catholic", "Shared ancient apostolic succession and sacraments")
        );
        map("Russian Orthodox",
            c("Serbian Orthodox", "Shared Slavic Orthodox traditions and communion"),
            c("Orthodox Church in America", "Direct historical and theological origins"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion")
        );
        map("Serbian Orthodox",
            c("Russian Orthodox", "Shared Slavic Orthodox traditions and communion"),
            c("Romanian Orthodox", "Deep shared Eastern European Orthodox ties"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion")
        );
        map("Bulgarian Orthodox",
            c("Serbian Orthodox", "Shared Eastern European Orthodox heritage"),
            c("Russian Orthodox", "Shared Slavic Orthodox traditions and communion"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion")
        );
        map("Romanian Orthodox",
            c("Serbian Orthodox", "Deep shared Eastern European Orthodox ties"),
            c("Bulgarian Orthodox", "Shared Eastern European Orthodox heritage"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion")
        );
        map("Georgian Orthodox",
            c("Russian Orthodox", "Shared regional Orthodox traditions"),
            c("Greek Orthodox", "Shared Byzantine liturgical heritage"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion")
        );
        map("Antiochian Orthodox",
            c("Greek Orthodox", "Shared Byzantine liturgical heritage"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion"),
            c("Melkite Greek Catholic", "Deep shared regional and liturgical heritage")
        );
        map("Orthodox Church in America",
            c("Russian Orthodox", "Direct historical and theological origins"),
            c("Eastern Orthodox", "Core member of the Eastern Orthodox communion"),
            c("Anglican", "Historically friendly dialogue and traditional liturgy")
        );

        // --- 3. PROTESTANT (BROAD CATEGORIES) ---
        map("Protestant",
            c("Evangelical", "Shared focus on personal faith and scripture"),
            c("Non-denominational", "Common origins in the Reformation"),
            c("Baptist", "Shared emphasis on biblical authority")
        );
        map("Evangelical",
            c("Non-denominational", "Shared emphasis on personal conversion and scripture"),
            c("Baptist", "Strong theological alignment on believer's church"),
            c("Charismatic", "Frequent cross-pollination in contemporary worship styles")
        );
        map("Non-denominational",
            c("Evangelical", "The primary theological umbrella for non-denominational churches"),
            c("Baptist", "Shared congregational polity and believer's baptism"),
            c("Charismatic", "Shared contemporary worship and expressive faith")
        );

        // 3.1 Lutheran
        map("Lutheran",
            c("Evangelical Lutheran Church in America", "Major expression of the Lutheran tradition"),
            c("Anglican", "Similar traditional liturgy and sacraments"),
            c("Presbyterian (USA)", "Shared focus on historical confessions")
        );
        map("Evangelical Lutheran Church in America", // Mainline/Progressive
            c("Episcopal (U.S.)", "Formal Full Communion agreement (Called to Common Mission)"),
            c("Presbyterian (USA)", "Formal Full Communion agreement (Formula of Agreement)"),
            c("United Methodist", "Formal Full Communion agreement"),
            c("United Church of Christ", "Formal Full Communion agreement")
        );
        map("Lutheran Church-Missouri Synod", // Conservative
            c("Wisconsin Evangelical Lutheran Synod", "Deep shared conservative Lutheran theology"),
            c("Anglican Church in North America", "Shared conservative liturgical values"),
            c("Presbyterian Church in America (PCA)", "Shared focus on historical confessions")
        );
        map("Wisconsin Evangelical Lutheran Synod",
            c("Lutheran Church-Missouri Synod", "Deep shared conservative Lutheran theology"),
            c("Baptist", "Shared emphasis on biblical inerrancy")
        );

        // 3.2 Calvinist / Reformed
        map("Presbyterian",
            c("Presbyterian (USA)", "Primary mainline expression of the Presbyterian tradition"),
            c("Presbyterian Church in America (PCA)", "Primary conservative expression of the Presbyterian tradition"),
            c("Calvinist", "Shared Reformed theological roots")
        );
        map("Calvinist",
            c("Presbyterian (USA)", "Primary mainline expression of Calvinist theology"),
            c("Presbyterian Church in America (PCA)", "Primary conservative expression of Calvinist theology"),
            c("Christian Reformed", "Deep shared Reformed theological roots")
        );
        map("Presbyterian (USA)", // Mainline
            c("Reformed Church in America", "Formal Full Communion agreement"),
            c("Evangelical Lutheran Church in America", "Formal Full Communion agreement"),
            c("United Church of Christ", "Formal Full Communion agreement")
        );
        map("Presbyterian Church in America (PCA)", // Conservative
            c("Orthodox Presbyterian Church", "Deep shared conservative Calvinist theology"),
            c("Christian Reformed", "Shared conservative Reformed theological roots"),
            c("Southern Baptist Convention", "Shared conservative evangelical values")
        );
        map("United Church of Christ",
            c("Presbyterian (USA)", "Formal Full Communion agreement"),
            c("Evangelical Lutheran Church in America", "Formal Full Communion agreement"),
            c("Disciples of Christ", "Formal Full Communion agreement")
        );
        map("Reformed Church in America",
            c("Christian Reformed", "Direct historical and theological alignment"),
            c("Presbyterian (USA)", "Formal Full Communion agreement")
        );
        map("Christian Reformed",
            c("Reformed Church in America", "Direct historical and theological alignment"),
            c("Presbyterian Church in America (PCA)", "Deep shared conservative Calvinist theological roots")
        );

        // 3.3 Anglican
        map("Anglican",
            c("Church of England", "The mother church of the Anglican Communion"),
            c("Episcopal (U.S.)", "The primary American branch of the Anglican Communion"),
            c("Catholic", "Shared ancient apostolic traditions and liturgy")
        );
        map("Church of England",
            c("Catholic", "Shared ancient apostolic traditions and liturgy"),
            c("Methodist", "Deep historical ties via John Wesley")
        );
        map("Episcopal (U.S.)", // Mainline
            c("Evangelical Lutheran Church in America", "Formal Full Communion agreement"),
            c("United Methodist", "Similar historical mainline Protestant origins"),
            c("Moravian Church", "Formal Full Communion agreement")
        );
        map("Anglican Church in North America", // Conservative
            c("Lutheran Church-Missouri Synod", "Shared conservative liturgical values"),
            c("Global Methodist", "Shared traditional orthodox theology"),
            c("Southern Baptist Convention", "Shared conservative theological alignment")
        );

        // 3.4 Baptist
        map("Baptist",
            c("Southern Baptist Convention", "Major expression of the Baptist tradition"),
            c("Non-denominational", "Shared focus on local church autonomy"),
            c("Churches of Christ", "Similar views on believer's baptism")
        );
        map("Southern Baptist Convention",
            c("Non-denominational", "Shared focus on local church autonomy and evangelicalism"),
            c("Presbyterian Church in America (PCA)", "Shared conservative theological alignment"),
            c("Assemblies of God", "Shared conservative evangelical values")
        );
        map("American Baptist Churches U.S.A.",
            c("National Baptist Convention", "Shared historical Baptist roots in America"),
            c("United Church of Christ", "Shared historical mainline Protestant origins"),
            c("Disciples of Christ", "Shared emphasis on local church autonomy")
        );
        map("National Baptist Convention",
            c("African Methodist Episcopal", "Shared African American church heritage"),
            c("Church of God in Christ", "Shared cultural worship expressions and demographics")
        );

        // 3.5 Methodist
        map("Methodist",
            c("United Methodist", "Major expression of the Methodist tradition"),
            c("Nazarene", "Shared Wesleyan-Holiness theological roots"),
            c("Anglican", "Deep historical ties via John Wesley")
        );
        map("United Methodist", // Mainline
            c("African Methodist Episcopal", "Formal Full Communion agreement"),
            c("Evangelical Lutheran Church in America", "Formal Full Communion agreement"),
            c("Episcopal (U.S.)", "Shared historical ties and dialogue")
        );
        map("Global Methodist", // Conservative
            c("Free Methodist", "Shared orthodox Wesleyan theology"),
            c("Anglican Church in North America", "Shared conservative alignment and historical ties"),
            c("Nazarene", "Shared Wesleyan-Holiness theological roots")
        );
        map("African Methodist Episcopal",
            c("United Methodist", "Formal Full Communion agreement"),
            c("National Baptist Convention", "Shared African American church heritage")
        );
        map("Free Methodist",
            c("Nazarene", "Shared Wesleyan-Holiness theological roots"),
            c("Salvation Army", "Shared Holiness movement origins")
        );
        map("Salvation Army",
            c("Methodist", "Direct historical origins in Wesleyan theology"),
            c("Nazarene", "Shared Wesleyan-Holiness theological roots")
        );

        // 3.6 Pentecostal / Charismatic
        map("Pentecostal",
            c("Assemblies of God", "Major expression of the Pentecostal tradition"),
            c("Charismatic", "Shared emphasis on spiritual gifts"),
            c("Non-denominational", "Common expressive contemporary worship")
        );
        map("Charismatic",
            c("Pentecostal", "Shared emphasis on the Holy Spirit and spiritual gifts"),
            c("Non-denominational", "The primary church structure for Charismatic worship")
        );
        map("Assemblies of God",
            c("Pentecostal", "Broad shared theological movement"),
            c("International Church of the Foursquare Gospel", "Direct historical and theological ties"),
            c("Non-denominational", "Similar contemporary church structures")
        );
        map("Church of God in Christ",
            c("Pentecostal", "Broad shared theological movement"),
            c("Assemblies of God", "Shared historical roots in the Azusa Street Revival"),
            c("National Baptist Convention", "Shared cultural demographics and heritage")
        );
        map("Church of God",
            c("Pentecostal", "Broad shared theological movement"),
            c("Assemblies of God", "Shared emphasis on spiritual gifts")
        );
        map("United Pentecostal Church International",
            c("Pentecostal", "Similar expressive contemporary worship styles"),
            c("Apostolic", "Shared Oneness Pentecostal theology")
        );
        map("International Church of the Foursquare Gospel",
            c("Assemblies of God", "Direct historical and theological ties"),
            c("Non-denominational", "Common expressive contemporary worship")
        );

        // 3.7 Anabaptist & Related
        map("Anabaptist",
            c("Mennonite", "Primary expression of Anabaptist tradition"),
            c("Quaker", "Shared historic peace church traditions"),
            c("Baptist", "Shared views on believer's baptism")
        );
        map("Mennonite",
            c("Anabaptist", "Core theological alignment"),
            c("Brethren", "Shared historic peace church traditions"),
            c("Amish", "Direct historical and theological origins")
        );
        map("Quaker",
            c("Anabaptist", "Shared historic peace church traditions"),
            c("Mennonite", "Shared emphasis on pacifism")
        );

        // 3.8 Restorationist & Others
        map("Restorationist",
            c("Churches of Christ", "Major expression of the Restoration movement"),
            c("Disciples of Christ", "Direct historical and theological ties")
        );
        map("Churches of Christ",
            c("Christian Church", "Direct historical and theological ties"),
            c("Disciples of Christ", "Shared origins in the Stone-Campbell movement")
        );
        map("Disciples of Christ",
            c("United Church of Christ", "Formal Full Communion agreement"),
            c("Christian Church", "Shared origins in the Stone-Campbell movement")
        );
        map("Christian Church",
            c("Churches of Christ", "Direct historical and theological ties"),
            c("Non-denominational", "Shared focus on local church autonomy")
        );
        map("Adventist",
            c("Seventh-day Adventist", "Primary expression of Adventist theology"),
            c("Baptist", "Shared views on believer's baptism")
        );
        map("Seventh-day Adventist",
            c("Adventist", "Core adherence to Adventist theology"),
            c("Baptist", "Shared views on believer's baptism and evangelism")
        );

        // --- 4. ORIENTAL ORTHODOX ---
        map("Oriental Orthodox",
            c("Coptic Orthodox", "Major expression of the Oriental Orthodox communion"),
            c("Eastern Orthodox", "Shared ancient traditions and sacramental focus"),
            c("Catholic", "Shared ancient apostolic succession")
        );
        map("Coptic Orthodox",
            c("Ethiopian Orthodox", "Deep shared historical and theological communion"),
            c("Eastern Orthodox", "Shared ancient Eastern Christian traditions")
        );
        map("Armenian Orthodox",
            c("Syriac Orthodox", "Core member of the Oriental Orthodox communion"),
            c("Catholic", "Friendly historical dialogue and apostolic succession")
        );
        map("Ethiopian Orthodox",
            c("Coptic Orthodox", "Deep shared historical and theological communion"),
            c("Eastern Orthodox", "Shared ancient Eastern Christian traditions")
        );
        map("Syriac Orthodox",
            c("Malankara Orthodox Syrian", "Direct historical and theological origins"),
            c("Maronite Catholic", "Deep shared historical and regional heritage")
        );
        map("Malankara Orthodox Syrian",
            c("Syriac Orthodox", "Direct historical and theological origins"),
            c("Syro-Malabar Catholic", "Shared ancient Indian Christian heritage")
        );

        // --- 5. ASSYRIAN CHURCH OF THE EAST ---
        map("Assyrian Church of the East",
            c("Ancient Church of the East", "Direct historical and theological origins"),
            c("Chaldean Catholic", "Shared ancient East Syriac liturgical heritage")
        );
        map("Ancient Church of the East",
            c("Assyrian Church of the East", "Direct historical and theological origins"),
            c("Chaldean Catholic", "Shared ancient East Syriac liturgical heritage")
        );
    }

    // ── Doctrinal Conflicts ───────────────────────────────────────────────────

    /** A specific theological incompatibility found between two denominations. */
    public static class DoctrinalConflict {
        public final String issue;
        public final String description;
        DoctrinalConflict(String issue, String description) {
            this.issue = issue;
            this.description = description;
        }
    }

    private static class Issue {
        final String name, positionA, positionB, implication;
        final Set<String> groupA, groupB;

        Issue(String name, String positionA, String[] rawA,
              String positionB, String[] rawB, String implication) {
            this.name = name;
            this.positionA = positionA;
            this.positionB = positionB;
            this.implication = implication;
            this.groupA = new HashSet<>(Arrays.asList(rawA));
            this.groupB = new HashSet<>(Arrays.asList(rawB));
        }

        boolean conflicts(String k1, String k2) {
            return (groupA.contains(k1) && groupB.contains(k2))
                || (groupA.contains(k2) && groupB.contains(k1));
        }

        String descriptionFor(String k1, String k2) {
            boolean k1inA = groupA.contains(k1);
            String holderA = k1inA ? k1 : k2;
            String holderB = k1inA ? k2 : k1;
            return holderA + " holds " + positionA + "; "
                + holderB + " holds " + positionB + ". " + implication;
        }
    }

    // Denominations that baptize infants (paedobaptist traditions).
    private static final String[] PAEDOBAPTIST = {
        "Catholic", "Roman Catholic", "Eastern Catholic Churches",
        "Maronite Catholic", "Melkite Greek Catholic", "Ukrainian Greek Catholic",
        "Syro-Malabar Catholic", "Chaldean Catholic",
        "Eastern Orthodox", "Greek Orthodox", "Russian Orthodox", "Serbian Orthodox",
        "Romanian Orthodox", "Bulgarian Orthodox", "Georgian Orthodox",
        "Antiochian Orthodox", "Orthodox Church in America",
        "Oriental Orthodox", "Coptic Orthodox", "Armenian Orthodox",
        "Ethiopian Orthodox", "Syriac Orthodox", "Malankara Orthodox Syrian",
        "Assyrian Church of the East", "Ancient Church of the East",
        "Lutheran", "Evangelical Lutheran Church in America",
        "Lutheran Church-Missouri Synod", "Wisconsin Evangelical Lutheran Synod",
        "Anglican", "Church of England", "Episcopal (U.S.)", "Anglican Church in North America",
        "Methodist", "United Methodist", "Global Methodist",
        "Free Methodist", "African Methodist Episcopal", "Salvation Army", "Nazarene",
        "Presbyterian", "Calvinist", "Presbyterian (USA)",
        "Presbyterian Church in America (PCA)", "Orthodox Presbyterian Church",
        "Reformed Church in America", "Christian Reformed", "United Church of Christ",
    };

    // Denominations that baptize only professing believers (credobaptist traditions).
    private static final String[] CREDOBAPTIST = {
        "Baptist", "Southern Baptist Convention",
        "American Baptist Churches U.S.A.", "National Baptist Convention",
        "Non-denominational", "Evangelical", "Protestant",
        "Pentecostal", "Charismatic", "Assemblies of God",
        "Church of God in Christ", "Church of God",
        "International Church of the Foursquare Gospel",
        "United Pentecostal Church International",
        "Anabaptist", "Mennonite", "Brethren", "Amish", "Quaker",
        "Restorationist", "Churches of Christ", "Disciples of Christ", "Christian Church",
        "Adventist", "Seventh-day Adventist", "Apostolic",
    };

    // Catholic and Orthodox traditions that hold a sacramental soteriology.
    private static final String[] SACRAMENTAL_GRACE = {
        "Catholic", "Roman Catholic", "Eastern Catholic Churches",
        "Maronite Catholic", "Melkite Greek Catholic", "Ukrainian Greek Catholic",
        "Syro-Malabar Catholic", "Chaldean Catholic",
        "Eastern Orthodox", "Greek Orthodox", "Russian Orthodox", "Serbian Orthodox",
        "Romanian Orthodox", "Bulgarian Orthodox", "Georgian Orthodox",
        "Antiochian Orthodox", "Orthodox Church in America",
        "Oriental Orthodox", "Coptic Orthodox", "Armenian Orthodox",
        "Ethiopian Orthodox", "Syriac Orthodox", "Malankara Orthodox Syrian",
        "Assyrian Church of the East", "Ancient Church of the East",
    };

    // Protestant traditions that hold salvation is by faith alone, apart from sacramental works.
    private static final String[] SOLA_FIDE = {
        "Lutheran", "Evangelical Lutheran Church in America",
        "Lutheran Church-Missouri Synod", "Wisconsin Evangelical Lutheran Synod",
        "Baptist", "Southern Baptist Convention",
        "American Baptist Churches U.S.A.", "National Baptist Convention",
        "Non-denominational", "Evangelical", "Protestant",
        "Pentecostal", "Charismatic", "Assemblies of God",
        "Church of God in Christ", "Church of God",
        "International Church of the Foursquare Gospel",
        "United Pentecostal Church International",
        "Anabaptist", "Mennonite", "Brethren",
        "Presbyterian", "Calvinist", "Presbyterian (USA)",
        "Presbyterian Church in America (PCA)", "Orthodox Presbyterian Church",
        "Reformed Church in America", "Christian Reformed",
        "Anglican Church in North America",
        "Restorationist", "Churches of Christ", "Disciples of Christ", "Christian Church",
        "Seventh-day Adventist", "Adventist", "Apostolic",
    };

    // Denominations observing Sunday as the primary day of corporate worship.
    private static final String[] SUNDAY_WORSHIP = {
        "Catholic", "Roman Catholic", "Eastern Catholic Churches",
        "Maronite Catholic", "Melkite Greek Catholic", "Ukrainian Greek Catholic",
        "Syro-Malabar Catholic", "Chaldean Catholic",
        "Eastern Orthodox", "Greek Orthodox", "Russian Orthodox", "Serbian Orthodox",
        "Romanian Orthodox", "Bulgarian Orthodox", "Georgian Orthodox",
        "Antiochian Orthodox", "Orthodox Church in America",
        "Oriental Orthodox", "Coptic Orthodox", "Armenian Orthodox",
        "Ethiopian Orthodox", "Syriac Orthodox", "Malankara Orthodox Syrian",
        "Assyrian Church of the East", "Ancient Church of the East",
        "Lutheran", "Evangelical Lutheran Church in America",
        "Lutheran Church-Missouri Synod", "Wisconsin Evangelical Lutheran Synod",
        "Anglican", "Church of England", "Episcopal (U.S.)", "Anglican Church in North America",
        "Methodist", "United Methodist", "Global Methodist",
        "Free Methodist", "African Methodist Episcopal", "Salvation Army", "Nazarene",
        "Presbyterian", "Calvinist", "Presbyterian (USA)",
        "Presbyterian Church in America (PCA)", "Orthodox Presbyterian Church",
        "Reformed Church in America", "Christian Reformed", "United Church of Christ",
        "Baptist", "Southern Baptist Convention",
        "American Baptist Churches U.S.A.", "National Baptist Convention",
        "Non-denominational", "Evangelical", "Protestant",
        "Pentecostal", "Charismatic", "Assemblies of God",
        "Church of God in Christ", "Church of God",
        "International Church of the Foursquare Gospel",
        "United Pentecostal Church International",
        "Anabaptist", "Mennonite", "Brethren", "Amish", "Quaker",
        "Restorationist", "Churches of Christ", "Disciples of Christ", "Christian Church",
        "Apostolic",
    };

    // All Trinitarian denominations (every listed denomination except Oneness groups).
    private static final String[] TRINITARIAN = {
        "Catholic", "Roman Catholic", "Eastern Catholic Churches",
        "Maronite Catholic", "Melkite Greek Catholic", "Ukrainian Greek Catholic",
        "Syro-Malabar Catholic", "Chaldean Catholic",
        "Eastern Orthodox", "Greek Orthodox", "Russian Orthodox", "Serbian Orthodox",
        "Romanian Orthodox", "Bulgarian Orthodox", "Georgian Orthodox",
        "Antiochian Orthodox", "Orthodox Church in America",
        "Oriental Orthodox", "Coptic Orthodox", "Armenian Orthodox",
        "Ethiopian Orthodox", "Syriac Orthodox", "Malankara Orthodox Syrian",
        "Assyrian Church of the East", "Ancient Church of the East",
        "Lutheran", "Evangelical Lutheran Church in America",
        "Lutheran Church-Missouri Synod", "Wisconsin Evangelical Lutheran Synod",
        "Anglican", "Church of England", "Episcopal (U.S.)", "Anglican Church in North America",
        "Methodist", "United Methodist", "Global Methodist",
        "Free Methodist", "African Methodist Episcopal", "Salvation Army", "Nazarene",
        "Presbyterian", "Calvinist", "Presbyterian (USA)",
        "Presbyterian Church in America (PCA)", "Orthodox Presbyterian Church",
        "Reformed Church in America", "Christian Reformed", "United Church of Christ",
        "Baptist", "Southern Baptist Convention",
        "American Baptist Churches U.S.A.", "National Baptist Convention",
        "Non-denominational", "Evangelical", "Protestant",
        "Pentecostal", "Charismatic", "Assemblies of God",
        "Church of God in Christ", "Church of God",
        "International Church of the Foursquare Gospel",
        "Anabaptist", "Mennonite", "Brethren", "Amish", "Quaker",
        "Restorationist", "Churches of Christ", "Disciples of Christ", "Christian Church",
        "Adventist", "Seventh-day Adventist",
    };

    private static final Issue[] DOCTRINAL_ISSUES = {

        new Issue(
            "Baptism",
            "infant baptism (paedobaptism) — children of believers are baptized into the covenant",
            PAEDOBAPTIST,
            "believer's baptism (credobaptism) — only those who personally profess faith are baptized",
            CREDOBAPTIST,
            "This couple will need to agree on whether to baptize their children as infants."
        ),

        new Issue(
            "Trinitarian Doctrine",
            "Oneness theology — God is a single person who manifested as Father, Son, and Spirit;"
                + " baptism must be in Jesus' name only",
            new String[] { "United Pentecostal Church International", "Apostolic" },
            "Trinitarian theology — one God in three co-equal, co-eternal persons;"
                + " baptism is in the name of the Father, Son, and Holy Spirit",
            TRINITARIAN,
            "This is a foundational disagreement about the very nature of God."
        ),

        new Issue(
            "Justification",
            "salvation by faith alone (sola fide) — the sacraments are signs and seals,"
                + " not channels that convey saving grace",
            SOLA_FIDE,
            "salvation through faith cooperating with sacramental grace — the sacraments"
                + " are necessary means by which God confers saving grace",
            SACRAMENTAL_GRACE,
            "This couple may hold fundamentally different views on how one enters and remains"
                + " in saving relationship with God, and what role the Church's sacraments play."
        ),

        new Issue(
            "Day of Worship",
            "Saturday Sabbath — the seventh-day Sabbath remains binding under the new covenant",
            new String[] { "Seventh-day Adventist", "Adventist" },
            "Sunday worship — the Lord's Day (first day of the week) replaced the Sabbath"
                + " at Christ's resurrection",
            SUNDAY_WORSHIP,
            "This couple would observe worship on different days of the week."
        ),
    };

    /**
     * Returns theological incompatibilities between two denominations that bear on
     * salvation, the new covenant, or foundational doctrine.
     * Returns an empty list if either denomination is unrecognized or no conflicts are found.
     */
    public static List<DoctrinalConflict> getDoctrinalConflicts(String rawDenom1, String rawDenom2) {
        List<DoctrinalConflict> result = new ArrayList<>();
        if (rawDenom1 == null || rawDenom2 == null
                || rawDenom1.trim().isEmpty() || rawDenom2.trim().isEmpty()) {
            return result;
        }
        List<String> keys1 = resolveToKeys(rawDenom1.trim());
        List<String> keys2 = resolveToKeys(rawDenom2.trim());
        if (keys1.isEmpty() || keys2.isEmpty()) return result;

        // Check every cross-pair of blend components; deduplicate by issue name.
        Set<String> seenIssues = new HashSet<>();
        for (String k1 : keys1) {
            for (String k2 : keys2) {
                for (Issue issue : DOCTRINAL_ISSUES) {
                    if (!seenIssues.contains(issue.name) && issue.conflicts(k1, k2)) {
                        result.add(new DoctrinalConflict(issue.name, issue.descriptionFor(k1, k2)));
                        seenIssues.add(issue.name);
                    }
                }
            }
        }
        return result;
    }

    // ── Blend / alias resolution ──────────────────────────────────────────────

    /**
     * Maps lowercased shorthand or known blend strings to canonical key(s).
     * Values containing "/" represent blends (e.g. "Baptist/Calvinist").
     */
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        // Initialisms / abbreviations
        ALIASES.put("elca",   "Evangelical Lutheran Church in America");
        ALIASES.put("lcms",   "Lutheran Church-Missouri Synod");
        ALIASES.put("wels",   "Wisconsin Evangelical Lutheran Synod");
        ALIASES.put("pca",    "Presbyterian Church in America (PCA)");
        ALIASES.put("opc",    "Orthodox Presbyterian Church");
        ALIASES.put("sbc",    "Southern Baptist Convention");
        ALIASES.put("abc",    "American Baptist Churches U.S.A.");
        ALIASES.put("ucc",    "United Church of Christ");
        ALIASES.put("sda",    "Seventh-day Adventist");
        ALIASES.put("upci",   "United Pentecostal Church International");
        ALIASES.put("aog",    "Assemblies of God");
        ALIASES.put("ag",     "Assemblies of God");
        ALIASES.put("cogic",  "Church of God in Christ");
        ALIASES.put("ame",    "African Methodist Episcopal");
        ALIASES.put("umc",    "United Methodist");

        // Common shorthands that fuzzy-match poorly or ambiguously
        ALIASES.put("anglo",        "Anglican");
        ALIASES.put("episcopal",    "Episcopal (U.S.)");
        ALIASES.put("episcopalian", "Episcopal (U.S.)");
        ALIASES.put("reformed",     "Calvinist");
        ALIASES.put("presb",        "Presbyterian");
        ALIASES.put("foursquare",   "International Church of the Foursquare Gospel");

        // Named blends — value uses "/" to delimit the two component keys
        ALIASES.put("reformed baptist",          "Baptist/Calvinist");
        ALIASES.put("calvinist baptist",         "Baptist/Calvinist");
        ALIASES.put("baptist-calvinist",         "Baptist/Calvinist");
        ALIASES.put("calvinist-baptist",         "Baptist/Calvinist");
        ALIASES.put("reformed-baptist",          "Baptist/Calvinist");
        ALIASES.put("anglo-catholic",            "Anglican/Catholic");
        ALIASES.put("anglo catholic",            "Anglican/Catholic");
        ALIASES.put("high church anglican",      "Anglican/Catholic");
        ALIASES.put("evangelical catholic",      "Evangelical/Catholic");
        ALIASES.put("charismatic catholic",      "Catholic/Charismatic");
        ALIASES.put("catholic charismatic",      "Catholic/Charismatic");
        ALIASES.put("lutheran charismatic",      "Lutheran/Charismatic");
        ALIASES.put("charismatic lutheran",      "Lutheran/Charismatic");
        ALIASES.put("charismatic episcopal",     "Episcopal (U.S.)/Charismatic");
        ALIASES.put("episcopal charismatic",     "Episcopal (U.S.)/Charismatic");
        ALIASES.put("presbyterian evangelical",  "Presbyterian/Evangelical");
        ALIASES.put("evangelical presbyterian",  "Presbyterian/Evangelical");
        ALIASES.put("baptist evangelical",       "Baptist/Evangelical");
        ALIASES.put("evangelical baptist",       "Baptist/Evangelical");
        ALIASES.put("methodist evangelical",     "Methodist/Evangelical");
        ALIASES.put("evangelical methodist",     "Methodist/Evangelical");
        ALIASES.put("anabaptist mennonite",      "Anabaptist/Mennonite");
    }

    /**
     * Resolves a denomination string — which may be a blend — to a list of canonical
     * COMPATIBILITY_MAP keys.  Returns a single-element list for plain denominations,
     * a multi-element list for detected blends, and an empty list for unrecognized input.
     *
     * Detection order:
     *   1. Whole-string alias lookup (handles abbreviations and named blends)
     *   2. Direct fuzzy match against COMPATIBILITY_MAP
     *   3. Explicit blend separators: "/" → " and " → "+"
     *   4. Hyphen split (tries every hyphen position; accepts first pair that both resolve)
     */
    private static List<String> resolveToKeys(String input) {
        if (input == null || input.trim().isEmpty()) return Collections.emptyList();
        String trimmed = input.trim();

        // 1. Whole-string alias (may be "A/B" for a named blend)
        String aliasVal = ALIASES.get(trimmed.toLowerCase());
        if (aliasVal != null) return resolveFromAliasValue(aliasVal);

        // 2. Direct fuzzy match
        String direct = findClosestMatch(trimmed);
        if (direct != null) return Collections.singletonList(direct);

        // 3. Explicit separators
        String[] parts = null;
        if (trimmed.contains("/")) {
            parts = trimmed.split("/", -1);
        } else if (trimmed.toLowerCase().contains(" and ")) {
            parts = trimmed.split("(?i) and ", -1);
        } else if (trimmed.contains("+")) {
            parts = trimmed.split("\\+", -1);
        } else {
            parts = trySplitOnHyphen(trimmed);
        }

        if (parts == null || parts.length < 2) return Collections.emptyList();

        List<String> resolved = new ArrayList<>();
        for (String part : parts) {
            String key = resolveSingleToken(part.trim());
            if (key != null && !resolved.contains(key)) resolved.add(key);
        }
        return resolved;
    }

    /** Resolves a single token (no blend separators expected): alias first, then fuzzy match. */
    private static String resolveSingleToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String aliasVal = ALIASES.get(token.toLowerCase());
        // Only use alias if it's a simple (non-blend) mapping
        if (aliasVal != null && !aliasVal.contains("/")) return aliasVal;
        return findClosestMatch(token);
    }

    /** Expands an alias value — either a plain key or a "Key1/Key2" blend string. */
    private static List<String> resolveFromAliasValue(String aliasVal) {
        if (!aliasVal.contains("/")) {
            String key = findClosestMatch(aliasVal);
            return key != null ? Collections.singletonList(key) : Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String part : aliasVal.split("/")) {
            String key = findClosestMatch(part.trim());
            if (key != null && !result.contains(key)) result.add(key);
        }
        return result;
    }

    /**
     * Tries every hyphen position in {@code input} and returns the first [left, right]
     * split where both halves resolve to a known denomination.
     * This lets canonical names that contain hyphens (e.g. "Seventh-day Adventist") pass
     * through the direct fuzzy match before reaching this method.
     */
    private static String[] trySplitOnHyphen(String input) {
        int start = 0;
        while (start < input.length()) {
            int pos = input.indexOf('-', start);
            if (pos < 0) break;
            String left  = input.substring(0, pos).trim();
            String right = input.substring(pos + 1).trim();
            if (!left.isEmpty() && !right.isEmpty()
                    && resolveSingleToken(left)  != null
                    && resolveSingleToken(right) != null) {
                return new String[]{ left, right };
            }
            start = pos + 1;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String c(String denomination, String reason) {
        return denomination + " - " + reason;
    }

    private static void map(String denomination, String... entries) {
        List<String> list = new ArrayList<>();
        list.add(denomination);
        list.addAll(Arrays.asList(entries));
        COMPATIBILITY_MAP.put(denomination, list);
    }

    /**
     * Corrects slight typos in a user-entered denomination name.
     * Only auto-corrects if the input is very close to a known denomination (score ≥ 0.82,
     * roughly ≤1–2 character edits). Intentional or unrecognized names are returned as-is.
     * Substring matching is intentionally disabled here to avoid false corrections on
     * short fragments (e.g. "Church" matching "Catholic").
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
        if (input == null || input.trim().isEmpty()) return Collections.emptyList();

        List<String> keys = resolveToKeys(input.trim());
        if (keys.isEmpty()) return Collections.emptyList();

        // Union of compatible denominations for every component of a blend, deduped by name.
        // Index 0 of each COMPATIBILITY_MAP entry is the denomination itself — skip it.
        Set<String> seenDenoms = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            List<String> rawList = COMPATIBILITY_MAP.get(key);
            if (rawList == null) continue;
            for (int i = 1; i < rawList.size(); i++) {
                String entry = rawList.get(i);
                int sep = entry.indexOf(" - ");
                String denomName = sep >= 0 ? entry.substring(0, sep).trim() : entry.trim();
                if (keys.contains(denomName)) continue; // exclude blend components from results
                if (seenDenoms.add(denomName)) {
                    result.add(includeReasons ? entry : denomName);
                }
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
            // (e.g. "Catholic") just because one token matches.
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
