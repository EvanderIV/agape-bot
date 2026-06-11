# Agape Bot — Command Reference

## Public Commands
*Available to any server member (some require the "Single" role or an accepted profile).*

---

### `/apply`
Begins the DM questionnaire to submit a matchmaking profile.

- Requires the **Single** role
- If the user already has an **accepted** or **pending** profile, returns an ephemeral notice and suggests `/toggle-qm`
- If the profile has any other status (e.g. rejected), DMs the user options to edit or delete and restart
- If the profile is **soft-deleted**, blocks the attempt and instructs them to contact a matchmaker

**Example:** A new member runs `/apply` to start their application.

---

### `/toggle-qm`
Enrolls or unenrolls the caller from the quickmatch pool.

- Requires an **accepted** profile
- Re-enrollment is blocked if the user's **net strike count** (strikes − pardons) is **3 or higher**

**Example:** A user who runs `/toggle-qm` to opt out while they're taking a break from QMs.

---

### `/quickmatch`
Draws a random eligible candidate from the quickmatch pool and opens a match thread.

**Requirements for the caller:**
- Enrolled in quickmatch with an accepted profile
- Has the **Single** role
- Has not been matched in the last **24 hours**
- Has not used all spins in the current **biweekly window** (1 basic · 5 for boosters/high-level members)

**Candidate eligibility:**
- Enrolled, accepted profile
- Falls within the same age bracket (18–22 · 23–27 · 28–32 · 33–40 · 41–55 · 56+)
- Not matched in the last 24 hours
- Not part of a precluded pair with the caller

**Example:** A user runs `/quickmatch` to see who the bot pairs them with this week.

---

### `/confirm` *(inside a match thread)*
Records the calling user's confirmation of a match.

- Both users must confirm to trigger the closing sequence
- **Manual match:** Closes the thread, assigns the **Matched** role, and sends congratulatory DMs to both participants
- **Quickmatch:** Closes the thread when both confirm
- Logs a `[/confirm]` entry in the thread archive

**Example:** Alice and Bob both run `/confirm` in their match thread to finalize the match.

---

### `/decline` *(inside a manual match thread only)*
Opens a modal prompting the user to provide at least 3 reasons for declining the match.

- Only available in **manual match** threads
- Submitting the modal closes the thread and stores the reasons for preference insights
- Logs a `[/decline]` entry in the thread archive

**Example:** A user runs `/decline` in their manual match thread because the match isn't a good fit.

---

## Matchmaker / Admin Commands
*Require the Matchmaker or Admin role.*

---

### `/app-status user:@User`
Shows a user's current application status, submission and review timestamps, and rejection reason if applicable.

- Statuses: `PENDING` ⏳ · `ACCEPTED` ✅ · `REJECTED` ❌ · `CHANGES_REQUESTED` 📝

**Example:** A matchmaker runs `/app-status user:@Alice` to confirm her profile was accepted before creating a match.

---

### `/generate target:@User`
Renders a profile card image using the target's saved profile data (photo, design code, and bio fields).

- Falls back to a placeholder card if the user has no profile on file
- Runs on a background thread to avoid Discord's 3-second interaction timeout

**Example:** A matchmaker runs `/generate target:@Bob` to produce a card to share in a pairing announcement.

---

### `/admin-message user:@User message:"..."`
Sends a formatted DM to an applicant on behalf of the matchmaker team.

- Includes an embedded **Reply** button in the DM (only one active at a time per conversation)
- Also posts the message to the matchmaker channel for record-keeping and relay replies

**Example:** A matchmaker sends a follow-up question to a pending applicant about their denomination.

---

### `/message-history user:@User`
Retrieves the saved back-and-forth conversation log between the calling matchmaker and a specified applicant.

**Example:** A matchmaker checks the history before reaching out again to avoid repeating a question.

---

### `/compat-algo`
Scores every opposite-sex pair from all enrolled, accepted profiles and displays the **top 10** by compatibility score.

**Score components:** Denomination · Age · Distance · Values · Deal-breakers (max 110 pts)

- Unmatched users receive an invisible **+5 sort boost** per person (not displayed)
- Precluded pairs are excluded from results
- Each result includes a **Breakdown** button for a detailed per-category breakdown, which also offers **💘 Matchmake** and **❌ Preclude Match** buttons

**Example:** A matchmaker opens the board at the start of the week to see who to prioritize.

---

### `/match user1:@User user2:@User`
Shows a compatibility preview with warnings before officially creating a manual match thread.

- Identical to clicking **💘 Matchmake** on a compat-algo breakdown
- Displays a **Confirm / Cancel** prompt before the thread is created

**Example:** A matchmaker manually pairs Alice and Bob after reviewing their profiles.

---

### `/pardon user:@User`
Issues a pardon record on a user's strike file, offsetting **one active strike** for **6 months**.

- Multiple pardons can stack
- Posts an aggregate strike/pardon summary (net standing) to the matchmaker channel
- DMs the pardoned user with their updated account standing

**Example:** A matchmaker pardons a user who received a strike due to a technical glitch.

---

### `/qm-thread user1:@User user2:@User`
Displays the full archived log for a **quickmatch** thread between two users, including messages, timestamps, and `/confirm`/`/decline` events.

- Output is split into chunks if it exceeds 2000 characters

**Example:** Staff reviews what was said in a quickmatch thread after a report was filed.

---

### `/mm-thread user1:@User user2:@User`
Same as `/qm-thread`, but for **manual match** threads.

**Example:** A matchmaker pulls the thread log to evaluate how a manual match progressed.

---

### `/close-thread` *(inside a match thread)*
Immediately archives and closes the current match thread **without issuing strikes or penalties**.

- Posts a force-close notice (closer, match type, users involved) to the matchmaker channel

**Example:** A matchmaker closes a thread that was opened in error.

---

### `/view-matches`
Lists every match record in the system across all thread types, with outcomes and timestamps.

**Example:** An admin runs a quarterly audit of all matches ever logged.

---

### `/user-insights user:@User`
Displays the collected preference tags and decline history for a user.

- Tags are extracted automatically from past match thread messages or added manually via `/tag-user`
- Output is chunked if needed

**Example:** A matchmaker reviews Alice's insights before selecting her next manual match candidate.

---

### `/tag-user user:@User tags:"+tag1 -tag2"`
Manually adds or removes preference tags for a user.

- Tags must start with `+` (add) or `-` (remove), space-separated
- Updates the user's insights record immediately and confirms changes

**Example:** A matchmaker runs `/tag-user user:@Bob tags:"+athletic -nightlife"` after reading his profile.
