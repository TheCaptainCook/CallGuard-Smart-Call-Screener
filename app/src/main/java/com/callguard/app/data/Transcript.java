package com.callguard.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Transcript — Room entity storing the STT transcript for a screened call.
 *
 * Linked to its parent {@link CallLog} via a foreign key. Each call can
 * have one transcript (STT output captured during the screening session).
 */
@Entity(
    tableName = "transcripts",
    foreignKeys = @ForeignKey(
        entity = CallLog.class,
        parentColumns = "id",
        childColumns = "callLogId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = { @Index("callLogId") }
)
public class Transcript {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Foreign key linking this transcript to its parent CallLog. */
    public long callLogId;

    /**
     * Full text of what the caller said during the screening session.
     * May be empty if STT produced no results or the caller stayed silent.
     */
    public String text;

    /** Confidence score from the STT engine (0.0–1.0). */
    public float confidence;

    /** Unix timestamp (ms) when the transcript was captured. */
    public long timestampMs;

    public Transcript() {}

    public Transcript(long callLogId, String text, float confidence) {
        this.callLogId = callLogId;
        this.text = text;
        this.confidence = confidence;
        this.timestampMs = System.currentTimeMillis();
    }
}
