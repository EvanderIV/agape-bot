package com.agape;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Regression tests for strike/pardon accounting on users with no records.
 * These getters gate quickmatch re-enrollment, so they must fail safe
 * (zero strikes) rather than crash when no file exists.
 */
public class ThreadManagerSafetyTest {

    private static final String UNKNOWN_USER = "test_user_does_not_exist_xyz";

    @Test
    public void unknownUserHasZeroStrikes() {
        assertEquals(0, ThreadManager.getRecentStrikeCount(UNKNOWN_USER));
    }

    @Test
    public void unknownUserHasZeroPardons() {
        assertEquals(0, ThreadManager.getRecentPardonCount(UNKNOWN_USER));
    }

    @Test
    public void unknownUserHasZeroNetStrikes() {
        assertEquals(0, ThreadManager.getNetStrikeCount(UNKNOWN_USER));
    }

    @Test
    public void findThreadForUnknownPairReturnsNull() {
        assertNull(ThreadManager.findThread(UNKNOWN_USER, UNKNOWN_USER + "_2"));
        assertNull(ThreadManager.findMMThread(UNKNOWN_USER, UNKNOWN_USER + "_2"));
    }

    private static ThreadManager.QMThread record(String status, boolean bothConfirmed, String endedReason) {
        ThreadManager.QMThread r = new ThreadManager.QMThread();
        r.maleId = "M";
        r.femaleId = "F";
        r.status = status;
        r.endedReason = endedReason;
        if (bothConfirmed) {
            r.confirmedBy.add("M");
            r.confirmedBy.add("F");
        }
        return r;
    }

    @Test
    public void openAndConfirmedAreActiveMatches() {
        assertTrue(ThreadManager.isActiveMatch(record("OPEN", false, null)));
        assertTrue(ThreadManager.isActiveMatch(record("ARCHIVED", true, null)));
    }

    @Test
    public void endedOrUnconfirmedClosedAreNotActiveMatches() {
        assertFalse(ThreadManager.isActiveMatch(record("ARCHIVED", true, "ENDED")));
        assertFalse(ThreadManager.isActiveMatch(record("ARCHIVED", true, "LEFT_SERVER:M")));
        assertFalse(ThreadManager.isActiveMatch(record("ARCHIVED", false, null))); // declined/timed-out
    }

    @Test
    public void leftServerOutcomeLabels() {
        // Confirmed match ended by a departure
        assertEquals("Confirmed (Ended — <@M> left server)",
            ThreadManager.matchOutcome(record("ARCHIVED", true, "LEFT_SERVER:M")));
        // Pending (force-closed) thread ended by a departure
        assertEquals("Ended (<@F> left server)",
            ThreadManager.matchOutcome(record("ARCHIVED", false, "LEFT_SERVER:F")));
    }
}
