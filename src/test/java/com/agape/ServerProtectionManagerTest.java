package com.agape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.Test;

import com.agape.ServerProtectionManager.JoinRisk;

/** Characterizes the new-account join-age severity tiers. */
public class ServerProtectionManagerTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 6, 19, 12, 0, 0, 0, ZoneOffset.UTC);

    private static JoinRisk riskForAgeHours(long hours) {
        return ServerProtectionManager.classifyAccountAge(NOW.minusHours(hours), NOW);
    }

    @Test
    public void brandNewAccountIsSevere() {
        assertEquals(JoinRisk.SEVERE, riskForAgeHours(0));   // just created
        assertEquals(JoinRisk.SEVERE, riskForAgeHours(1));
        assertEquals(JoinRisk.SEVERE, riskForAgeHours(24));  // exactly one day → still severe
    }

    @Test
    public void underOneWeekIsUrgent() {
        assertEquals(JoinRisk.URGENT, riskForAgeHours(25));        // just over a day
        assertEquals(JoinRisk.URGENT, riskForAgeHours(3 * 24));
        assertEquals(JoinRisk.URGENT, riskForAgeHours(7 * 24 - 1)); // just under a week
    }

    @Test
    public void underOneMonthIsNotice() {
        assertEquals(JoinRisk.NOTICE, riskForAgeHours(7 * 24));      // exactly one week
        assertEquals(JoinRisk.NOTICE, riskForAgeHours(20 * 24));
        assertEquals(JoinRisk.NOTICE, riskForAgeHours(30 * 24 - 1)); // just under a month
    }

    @Test
    public void monthOrOlderIsNotReported() {
        assertEquals(JoinRisk.NONE, riskForAgeHours(30 * 24));   // exactly a month
        assertEquals(JoinRisk.NONE, riskForAgeHours(365 * 24));  // a year
    }

    @Test
    public void futureCreationIsIgnored() {
        assertEquals(JoinRisk.NONE, ServerProtectionManager.classifyAccountAge(NOW.plusHours(5), NOW));
    }

    @Test
    public void underOneWeekGate() {
        // Jailed: anything in the SEVERE/URGENT band (< 1 week)
        assertTrue(ServerProtectionManager.isUnderOneWeek(NOW.minusHours(1), NOW));
        assertTrue(ServerProtectionManager.isUnderOneWeek(NOW.minusHours(24), NOW));
        assertTrue(ServerProtectionManager.isUnderOneWeek(NOW.minusHours(7 * 24 - 1), NOW));
        // Not jailed: a week or older
        assertFalse(ServerProtectionManager.isUnderOneWeek(NOW.minusHours(7 * 24), NOW));
        assertFalse(ServerProtectionManager.isUnderOneWeek(NOW.minusHours(30 * 24), NOW));
    }
}
