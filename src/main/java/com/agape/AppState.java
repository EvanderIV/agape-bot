package com.agape;

/**
 * A user's matchmaking profile and application progress.
 *
 * This is the central data record of the bot. It is serialized with Gson to
 * {@code user_content/profiles/<userId>.json} (completed applications) and
 * {@code user_content/in_progress/<userId>.json} (mid-questionnaire recovery).
 *
 * IMPORTANT: field names ARE the JSON schema. Renaming any field silently
 * orphans the data already on disk for every existing user. Add new fields
 * freely (Gson defaults missing fields), but never rename or repurpose one.
 */
public class AppState {
    public AppStep currentStep = AppStep.LANGUAGE;

    public String language;
    public String username; // The Discord handle (not the display name)
    public String name;
    public String country;
    public String birthday; // stored as M/D/YYYY
    public boolean sex;     // true = Female, false = Male
    public String sect;     // Christian denomination
    public String physicalDescription;
    public String hobbies;
    public String strengths;
    public String weaknesses;
    public String photoPath; // Local file path OR avatar URL

    // Normalized focal point of the applicant's face within their photo, in
    // [0,1] (x = left→right, y = top→bottom). Computed by FaceDetector at upload
    // time so the profile-card renderer can crop toward the face instead of
    // center-cropping. Defaults to dead center (0.5, 0.5) for placeholders,
    // Discord avatars, and any older profile whose JSON predates this field.
    public float photoFocusX = 0.5f;
    public float photoFocusY = 0.5f;

    public String targetAge;  // e.g. "25" or "18-25"
    public String targetSect;
    public String lookFor;
    public String dealBreakers;
    public String designCode; // Profile-card design, e.g. "BTW-PST" (see assets/design_codes.json)

    // Application status tracking
    public String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, CHANGES_REQUESTED
    public String submittedAt;
    public String reviewedAt;
    public String reviewedBy;      // Matchmaker's ID who reviewed it
    public String rejectionReason; // Reason for rejection with request for change
    public String guildId;         // Guild where /apply was used

    // Quickmatch system
    public boolean quickmatchEnrolled = false;
    public boolean quickmatchPromptSent = false;

    // Soft-delete: when true, the profile is invisible to all matchmaking systems
    public boolean softDeleted = false;

    // ID of this profile's message on the "display-board" channel (null if none is
    // currently posted). Set when the card is posted, cleared when it is removed.
    public String displayBoardMessageId;

    // Manual matchmaking opt-in; false blocks /match and compat-algo but not quickmatch
    public boolean manualMatchEnrolled = true;

    // Track which field is being edited (stores an AppStep enum name)
    public String fieldBeingEdited;
}
