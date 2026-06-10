package com.agape;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The single place that reads and writes user profiles
 * ({@code user_content/profiles/<userId>.json}).
 *
 * Always go through this class instead of hand-rolling Gson + FileReader:
 * it guarantees consistent pretty-printing, error handling, and a single
 * directory constant. Loads fail soft (null) so callers must null-check.
 */
public final class ProfileRepository {

    public static final String PROFILES_DIR = "user_content/profiles/";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProfileRepository() {}

    /** The on-disk JSON file for this user's profile (may not exist). */
    public static File file(String userId) {
        return new File(PROFILES_DIR + userId + ".json");
    }

    /** True if the user has a profile file on disk. */
    public static boolean exists(String userId) {
        return file(userId).exists();
    }

    /** Loads a profile. Returns null when missing or unparseable (logged). */
    public static AppState load(String userId) {
        File f = file(userId);
        if (!f.exists()) return null;
        try (FileReader reader = new FileReader(f)) {
            return GSON.fromJson(reader, AppState.class);
        } catch (Exception e) {
            System.err.println("ProfileRepository: Failed to read profile for " + userId + ": " + e.getMessage());
            return null;
        }
    }

    /** Saves (creates or overwrites) a profile as pretty-printed JSON. */
    public static void save(String userId, AppState state) {
        File dir = new File(PROFILES_DIR);
        if (!dir.exists()) dir.mkdirs();
        try (FileWriter writer = new FileWriter(file(userId))) {
            GSON.toJson(state, writer);
        } catch (IOException e) {
            System.err.println("ProfileRepository: Failed to save profile for " + userId + ": " + e.getMessage());
        }
    }

    /** Permanently deletes the user's profile file, if present. */
    public static void delete(String userId) {
        File f = file(userId);
        if (f.exists()) f.delete();
    }

    /** All profile JSON files on disk (empty array when the directory is missing). */
    public static File[] listProfileFiles() {
        File dir = new File(PROFILES_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        return files != null ? files : new File[0];
    }

    /** The user ID encoded in a profile file's name ("123.json" → "123"). */
    public static String userIdFromFile(File profileFile) {
        return profileFile.getName().replace(".json", "");
    }
}
