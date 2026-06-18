package com.agape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Characterizes the {@code -s}/{@code --stats} query language against the spec examples. */
public class StatsQueryTest {

    // role{'Sister'}:%  — exact, case-sensitive, percentage
    @Test
    public void exactCaseSensitivePercent() {
        StatsQuery q = StatsQuery.parse("role{'Sister'}:%");
        assertEquals(StatsQuery.OutputType.PERCENT, q.outputType());
        assertTrue(q.matchesRole("Sister"));
        assertFalse(q.matchesRole("sister"));   // case-sensitive by default
        assertFalse(q.matchesRole("Sisters"));  // exact, no suffix allowed
    }

    // role{'brother'!c}  — exact, case-insensitive, count (default)
    @Test
    public void exactCaseInsensitiveNumberDefault() {
        StatsQuery q = StatsQuery.parse("role{'brother'!c}");
        assertEquals(StatsQuery.OutputType.NUMBER, q.outputType());
        assertTrue(q.matchesRole("brother"));
        assertTrue(q.matchesRole("Brother"));
        assertTrue(q.matchesRole("BROTHER"));
        assertFalse(q.matchesRole("brotherly"));
    }

    // role{'lvl'(#)!c}:percent  — prefix + any integer, case-insensitive, percentage
    @Test
    public void prefixAnyIntegerCaseInsensitive() {
        StatsQuery q = StatsQuery.parse("role{'lvl'(#)!c}:percent");
        assertEquals(StatsQuery.OutputType.PERCENT, q.outputType());
        assertTrue(q.matchesRole("lvl5"));
        assertTrue(q.matchesRole("LVL42"));     // case-insensitive prefix
        assertFalse(q.matchesRole("lvl"));       // needs an integer suffix
        assertFalse(q.matchesRole("lvlx"));      // suffix not an integer
        assertFalse(q.matchesRole("xlvl5"));     // must be a prefix
    }

    // role{'lvl'(#>=10)}:percentage  — prefix + integer >= 10, case-sensitive, percentage
    @Test
    public void prefixIntegerGreaterEqualCaseSensitive() {
        StatsQuery q = StatsQuery.parse("role{'lvl'(#>=10)}:percentage");
        assertEquals(StatsQuery.OutputType.PERCENT, q.outputType());
        assertTrue(q.matchesRole("lvl10"));
        assertTrue(q.matchesRole("lvl25"));
        assertFalse(q.matchesRole("lvl9"));      // below threshold
        assertFalse(q.matchesRole("LVL10"));     // case-sensitive prefix
    }

    @Test
    public void comparisonOperators() {
        assertTrue(StatsQuery.parse("role{'lvl'(#<5)}").matchesRole("lvl3"));
        assertFalse(StatsQuery.parse("role{'lvl'(#<5)}").matchesRole("lvl5"));
        assertTrue(StatsQuery.parse("role{'lvl'(#=7)}").matchesRole("lvl7"));
        assertTrue(StatsQuery.parse("role{'lvl'(#==7)}").matchesRole("lvl7"));
        assertFalse(StatsQuery.parse("role{'lvl'(#!=7)}").matchesRole("lvl7"));
        assertTrue(StatsQuery.parse("role{'lvl'(#>5)}").matchesRole("lvl6"));
        assertTrue(StatsQuery.parse("role{'lvl'(#<=5)}").matchesRole("lvl5"));
    }

    @Test
    public void modifierOrderIsFlexible() {
        // !c before the (#…) clause should parse identically
        StatsQuery q = StatsQuery.parse("role{'lvl'!c(#>=10)}");
        assertTrue(q.matchesRole("LVL10"));
        assertFalse(q.matchesRole("lvl9"));
    }

    @Test
    public void formatting() {
        StatsQuery pct = StatsQuery.parse("role{'x'}:%");
        assertTrue(pct.format(1, 4).startsWith("25.0%"));
        assertEquals("0.0% (0/0)", pct.format(0, 0)); // no divide-by-zero

        StatsQuery num = StatsQuery.parse("role{'x'}");
        assertTrue(num.format(3, 10).startsWith("3"));
    }

    @Test
    public void malformedQueriesThrow() {
        assertParseError("");
        assertParseError("role{'x'");           // missing }
        assertParseError("member{'x'}");         // wrong selector keyword
        assertParseError("role{x}");             // name not quoted
        assertParseError("role{'x'}:bogus");     // unknown output type
        assertParseError("role{'x'(#>=abc)}");   // non-integer comparison
        assertParseError("role{'x'(#>=10)zzz}"); // leftover modifier text
    }

    private static void assertParseError(String query) {
        try {
            StatsQuery.parse(query);
            fail("expected parse error for: " + query);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
