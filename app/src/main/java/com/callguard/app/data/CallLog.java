package com.callguard.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * CallLog — Room entity representing a single screened call.
 *
 * Each row stores the metadata for one call that was processed
 * by the CallGuard screening engine.
 */
@Entity(tableName = "call_logs")
public class CallLog {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Caller's phone number, or "unknown" for private/withheld numbers. */
    public String callerNumber;

    /** Unix timestamp (ms) when the call was received. */
    public long timestampMs;

    /** Duration of the screening session in seconds. */
    public int durationSeconds;

    /** Outcome: "screened", "user_answered", "blocked", "missed" */
    public String outcome;

    /** Whether the number was flagged as spam by the spam detector. */
    public boolean isSpam;

    /** Spam confidence score 0.0–1.0 (0 = not spam, 1.0 = definite spam). */
    public float spamScore;

    /** Whether the caller was in the user's contacts (whitelisted). */
    public boolean isKnownContact;

    /**
     * STIR/SHAKEN attestation level derived by {@link com.callguard.app.spam.StirShakenVerifier}.
     * Values: "A", "B", "C", "UNVERIFIED"
     */
    public String attestationLevel;

    /**
     * Combined ML spam score from {@link com.callguard.app.spam.MlSpamAnalyser}.
     * 0.0 = clean, 1.0 = definite spam.
     */
    public float mlSpamScore;

    public CallLog() {}

    public CallLog(String callerNumber, long timestampMs) {
        this.callerNumber = callerNumber;
        this.timestampMs = timestampMs;
        this.outcome = "screened";
        this.isSpam = false;
        this.spamScore = 0f;
        this.isKnownContact = false;
    }
}
