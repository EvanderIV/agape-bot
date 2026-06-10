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
}
