package com.callguard.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * CallLogDao — Room Data Access Object for call history operations.
 *
 * Provides CRUD operations and reactive LiveData queries for the
 * call log and transcript tables.
 */
@Dao
public interface CallLogDao {

    // ── CallLog operations ──────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertCallLog(CallLog callLog);

    @Update
    void updateCallLog(CallLog callLog);

    @Query("SELECT * FROM call_logs ORDER BY timestampMs DESC")
    LiveData<List<CallLog>> getAllCallLogs();

    @Query("SELECT * FROM call_logs ORDER BY timestampMs DESC LIMIT :limit")
    LiveData<List<CallLog>> getRecentCallLogs(int limit);

    @androidx.room.Transaction
    @Query("SELECT * FROM call_logs ORDER BY timestampMs DESC LIMIT :limit")
    LiveData<List<CallLogWithTranscript>> getRecentCallLogsWithTranscripts(int limit);

    /** Synchronous variant for background-thread use (ML + STIR/SHAKEN). */
    @Query("SELECT * FROM call_logs ORDER BY timestampMs DESC LIMIT :limit")
    List<CallLog> getRecentHistorySync(int limit);

    @Query("SELECT * FROM call_logs WHERE id = :id")
    CallLog getCallLogById(long id);

    @Query("SELECT COUNT(*) FROM call_logs WHERE isSpam = 1")
    LiveData<Integer> getSpamCallCount();

    @Query("SELECT COUNT(*) FROM call_logs")
    LiveData<Integer> getTotalCallCount();

    @Query("SELECT SUM(durationSeconds) FROM call_logs")
    LiveData<Integer> getTotalScreeningSeconds();

    @Query("DELETE FROM call_logs WHERE timestampMs < :cutoffMs")
    void deleteOlderThan(long cutoffMs);

    // ── Transcript operations ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTranscript(Transcript transcript);

    @Query("SELECT * FROM transcripts WHERE callLogId = :callLogId")
    Transcript getTranscriptForCall(long callLogId);

    @Query("SELECT * FROM transcripts ORDER BY timestampMs DESC")
    LiveData<List<Transcript>> getAllTranscripts();
}
