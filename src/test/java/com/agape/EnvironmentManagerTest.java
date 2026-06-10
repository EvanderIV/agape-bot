package com.agape;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Security regression tests for environment/guild gating.
 *
 * EnvironmentManager decides which Discord guild the bot will serve based on
 * the host OS (Windows = dev guild, anything else = production guild). Every
 * interaction entry point relies on isGuildAllowed() to reject commands from
 * the wrong guild, so these IDs and rules must never drift silently.
 */
public class EnvironmentManagerTest {

    private static final String DEV_GUILD_ID        = "1503648946696749198";
    private static final String PRODUCTION_GUILD_ID = "1487179725657997544";

    private String originalOsName;

    @Before
    public void rememberOsName() {
        originalOsName = System.getProperty("os.name");
    }

    @After
    public void restoreOsName() {
        System.setProperty("os.name", originalOsName);
    }

    // --- Environment detection ---

    @Test
    public void windowsIsDevelopment() {
        System.setProperty("os.name", "Windows 11");
        assertTrue(EnvironmentManager.isDevelopment());
        assertFalse(EnvironmentManager.isProduction());
        assertEquals("DEVELOPMENT", EnvironmentManager.getEnvironmentName());
        assertEquals(DEV_GUILD_ID, EnvironmentManager.getAllowedGuildId());
    }

    @Test
    public void linuxIsProduction() {
        System.setProperty("os.name", "Linux");
        assertFalse(EnvironmentManager.isDevelopment());
        assertTrue(EnvironmentManager.isProduction());
        assertEquals("PRODUCTION", EnvironmentManager.getEnvironmentName());
        assertEquals(PRODUCTION_GUILD_ID, EnvironmentManager.getAllowedGuildId());
    }

    // --- Guild gating (security) ---

    @Test
    public void nullGuildIdIsNeverAllowed() {
        assertFalse(EnvironmentManager.isGuildAllowed(null));
    }

    @Test
    public void unknownGuildIdIsNeverAllowed() {
        assertFalse(EnvironmentManager.isGuildAllowed("999999999999999999"));
        assertFalse(EnvironmentManager.isGuildAllowed(""));
    }

    @Test
    public void devGuildRejectedInProduction() {
        System.setProperty("os.name", "Linux");
        assertFalse("Dev guild must be rejected in production",
            EnvironmentManager.isGuildAllowed(DEV_GUILD_ID));
        assertTrue(EnvironmentManager.isGuildAllowed(PRODUCTION_GUILD_ID));
    }

    @Test
    public void productionGuildRejectedInDevelopment() {
        System.setProperty("os.name", "Windows 11");
        assertFalse("Production guild must be rejected in development",
            EnvironmentManager.isGuildAllowed(PRODUCTION_GUILD_ID));
        assertTrue(EnvironmentManager.isGuildAllowed(DEV_GUILD_ID));
    }
}
