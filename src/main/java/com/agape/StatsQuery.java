package com.agape;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed {@code --stats} / {@code -s} analytics query.
 *
 * <p>The bot can be booted with a single query argument to compute a one-off
 * membership statistic and exit (see {@code AgapeBot.runStats}). The query is a
 * small role-selector language:
 *
 * <pre>
 *   role{'Sister'}:%              members whose role == "Sister" (case-sensitive), as a percentage
 *   role{'brother'!c}             members whose role == "brother" (case-insensitive), as a count
 *   role{'lvl'(#)!c}:percent      role == "lvl" + an integer (case-insensitive), as a percentage
 *   role{'lvl'(#>=10)}:percentage role == "lvl" + an integer >= 10 (case-sensitive), as a percentage
 * </pre>
 *
 * Grammar:
 * <ul>
 *   <li><b>role{'name'}</b> — required; the single-quoted text is matched against
 *       each member's role names. Without {@code (#…)} the match is exact.</li>
 *   <li><b>(#)</b> — the role name must be the quoted text <i>immediately
 *       followed by an integer</i>. An optional comparison constrains that
 *       integer: {@code (#>=10)}, {@code (#<5)}, {@code (#=3)}, {@code (#!=0)},
 *       using {@code > < >= <= = == !=}.</li>
 *   <li><b>!c</b> — case-insensitive name matching (default is case-sensitive).
 *       May appear before or after the {@code (#…)} clause.</li>
 *   <li><b>:type</b> — optional output type after the closing brace.
 *       {@code %}/{@code percent}/{@code percentage} → percentage of all
 *       members; omitted (or {@code n}/{@code number}/{@code count}) → a raw
 *       count.</li>
 * </ul>
 *
 * Parsing is pure and fully unit-tested; the Discord member fetch lives in the
 * caller. {@link #parse(String)} throws {@link IllegalArgumentException} with a
 * human-readable message on any malformed query.
 */
public final class StatsQuery {

    public enum OutputType { NUMBER, PERCENT }

    private enum Op { ANY, GT, LT, GE, LE, EQ, NE }

    /** Constraint on the integer suffix in a {@code (#…)} clause. */
    private static final class IntMatcher {
        final Op op;
        final long value;
        IntMatcher(Op op, long value) { this.op = op; this.value = value; }

        boolean test(long n) {
            switch (op) {
                case ANY: return true;
                case GT:  return n >  value;
                case LT:  return n <  value;
                case GE:  return n >= value;
                case LE:  return n <= value;
                case EQ:  return n == value;
                case NE:  return n != value;
                default:  return false;
            }
        }

        /** Parses the text after the {@code #}, e.g. "" (any), ">=10", "=3". */
        static IntMatcher parse(String body) {
            String b = body.trim();
            if (b.isEmpty()) return new IntMatcher(Op.ANY, 0);

            Op op;
            int idx;
            if      (b.startsWith(">=")) { op = Op.GE; idx = 2; }
            else if (b.startsWith("<=")) { op = Op.LE; idx = 2; }
            else if (b.startsWith("==")) { op = Op.EQ; idx = 2; }
            else if (b.startsWith("!=")) { op = Op.NE; idx = 2; }
            else if (b.startsWith(">"))  { op = Op.GT; idx = 1; }
            else if (b.startsWith("<"))  { op = Op.LT; idx = 1; }
            else if (b.startsWith("="))  { op = Op.EQ; idx = 1; }
            else throw new IllegalArgumentException("invalid integer comparison: (#" + body + ")");

            String numStr = b.substring(idx).trim();
            try {
                return new IntMatcher(op, Long.parseLong(numStr));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not an integer in comparison: (#" + body + ")");
            }
        }
    }

    private static final Pattern INT_CLAUSE = Pattern.compile("\\(#([^)]*)\\)");

    private final String raw;
    private final String name;
    private final boolean caseInsensitive;
    private final IntMatcher intMatcher; // null when there is no (#…) clause
    private final OutputType outputType;

    private StatsQuery(String raw, String name, boolean caseInsensitive,
                       IntMatcher intMatcher, OutputType outputType) {
        this.raw = raw;
        this.name = name;
        this.caseInsensitive = caseInsensitive;
        this.intMatcher = intMatcher;
        this.outputType = outputType;
    }

    public OutputType outputType() { return outputType; }

    /** Parses a full query string, e.g. {@code role{'lvl'(#>=10)}:%}. */
    public static StatsQuery parse(String query) {
        if (query == null || query.trim().isEmpty())
            throw new IllegalArgumentException("empty query");
        String q = query.trim();

        int close = q.lastIndexOf('}');
        if (close < 0) throw new IllegalArgumentException("missing '}' in query: " + query);

        String selector = q.substring(0, close + 1);
        String tail     = q.substring(close + 1).trim();

        OutputType outputType = OutputType.NUMBER;
        if (!tail.isEmpty()) {
            if (!tail.startsWith(":"))
                throw new IllegalArgumentException("unexpected text after '}': " + tail);
            outputType = parseOutputType(tail.substring(1).trim());
        }

        int open = selector.indexOf('{');
        if (open < 0 || !selector.substring(0, open).trim().equalsIgnoreCase("role"))
            throw new IllegalArgumentException("query must start with role{...}: " + query);

        String inner = selector.substring(open + 1, selector.length() - 1).trim();
        if (inner.isEmpty() || inner.charAt(0) != '\'')
            throw new IllegalArgumentException("expected a single-quoted role name inside role{...}: " + query);
        int nameEnd = inner.indexOf('\'', 1);
        if (nameEnd < 0) throw new IllegalArgumentException("unterminated role name in: " + query);

        String name = inner.substring(1, nameEnd);
        String mods = inner.substring(nameEnd + 1).trim();

        // Extract an optional (#…) clause, then an optional !c flag; anything left over is an error.
        IntMatcher intMatcher = null;
        Matcher m = INT_CLAUSE.matcher(mods);
        if (m.find()) {
            intMatcher = IntMatcher.parse(m.group(1));
            mods = (mods.substring(0, m.start()) + mods.substring(m.end())).trim();
        }

        boolean caseInsensitive = false;
        if (mods.contains("!c")) {
            caseInsensitive = true;
            mods = mods.replace("!c", "").trim();
        }

        if (!mods.isEmpty())
            throw new IllegalArgumentException("unrecognized modifier '" + mods + "' in: " + query);

        return new StatsQuery(query.trim(), name, caseInsensitive, intMatcher, outputType);
    }

    private static OutputType parseOutputType(String t) {
        String s = t.toLowerCase(Locale.US);
        switch (s) {
            case "%":
            case "percent":
            case "percentage":
                return OutputType.PERCENT;
            case "":
            case "n":
            case "num":
            case "number":
            case "count":
                return OutputType.NUMBER;
            default:
                throw new IllegalArgumentException("unknown output type ':" + t + "'");
        }
    }

    /** True if a single role name satisfies this query's selector. */
    public boolean matchesRole(String roleName) {
        if (roleName == null) return false;

        if (intMatcher == null) {
            return caseInsensitive ? roleName.equalsIgnoreCase(name) : roleName.equals(name);
        }

        // Prefix must equal the name; the immediate remainder must be a (constrained) integer.
        if (roleName.length() <= name.length()) return false;
        boolean prefixOk = caseInsensitive
            ? roleName.regionMatches(true, 0, name, 0, name.length())
            : roleName.startsWith(name);
        if (!prefixOk) return false;

        String suffix = roleName.substring(name.length());
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return false;
        }
        try {
            return intMatcher.test(Long.parseLong(suffix));
        } catch (NumberFormatException e) {
            return false; // overflow — treat as non-match
        }
    }

    /** Formats the result, leading with the requested metric and appending the raw breakdown. */
    public String format(long matched, long total) {
        if (outputType == OutputType.PERCENT) {
            double pct = total == 0 ? 0.0 : (matched * 100.0 / total);
            return String.format(Locale.US, "%.1f%% (%d/%d)", pct, matched, total);
        }
        return matched + " (of " + total + ")";
    }

    @Override
    public String toString() { return raw; }
}
