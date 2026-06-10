package com.agape;

/**
 * Where a user currently is in the DM application questionnaire.
 *
 * The flow is driven by {@link ApplicationHandler#onMessageReceived}: each DM
 * the applicant sends is interpreted according to their current step, then the
 * step advances. Serialized by name into the profile/in-progress JSON files,
 * so renaming a constant breaks recovery of existing in-flight applications.
 */
public enum AppStep {
    LANGUAGE,
    APPLICATION_LANGUAGE,
    NAME,
    COUNTRY,
    AGE,
    SEX,
    SECT,
    PHYSICAL,
    HOBBIES,
    STRENGTHS,
    WEAKNESSES,
    PHOTO,
    TARGET_AGE,
    TARGET_SECT,
    LOOK_FOR,
    DEAL_BREAKERS,
    CUSTOMIZE_PROMPT,
    EDIT_WHICH_FIELD,      // User is selecting which field to edit
    EDITING_FIELD,         // User is editing a specific field
    WAITING_FOR_DESIGN_CODE,
    COMPLETED
}
