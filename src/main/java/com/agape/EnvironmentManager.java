package com.agape;

/**
 * Manages environment detection and guild validation.
 * Separates dev (Windows) and production (Debian 13) environments.
 */
public class EnvironmentManager {
    
    // Guild IDs
    private static final String DEV_GUILD_ID = "1503648946696749198";           // Windows/dev environment
    private static final String PRODUCTION_GUILD_ID = "1487179725657997544";     // Debian 13/production environment
    
    /**
     * Detects the current environment based on OS and returns the allowed guild ID.
     * @return DEV_GUILD_ID if running on Windows, PRODUCTION_GUILD_ID if running on Debian 13 or other Unix-like systems
     */
    public static String getAllowedGuildId() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        // Windows detection
        if (osName.contains("win")) {
            return DEV_GUILD_ID;
        }
        
        // Unix-like systems (including Debian 13)
        return PRODUCTION_GUILD_ID;
    }
    
    /**
     * Gets the environment name for logging purposes.
     * @return "DEVELOPMENT" for Windows, "PRODUCTION" for other systems
     */
    public static String getEnvironmentName() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("win") ? "DEVELOPMENT" : "PRODUCTION";
    }
    
    /**
     * Checks if a guild ID is allowed for the current environment.
     * @param guildId The guild ID to validate
     * @return true if the guild is allowed, false otherwise
     */
    public static boolean isGuildAllowed(String guildId) {
        if (guildId == null) return false;
        return guildId.equals(getAllowedGuildId());
    }
    
    /**
     * Checks if the environment is development (Windows).
     * @return true if running on Windows, false otherwise
     */
    public static boolean isDevelopment() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("win");
    }
    
    /**
     * Checks if the environment is production (Debian 13 or other Unix-like systems).
     * @return true if not running on Windows, false otherwise
     */
    public static boolean isProduction() {
        return !isDevelopment();
    }
}
