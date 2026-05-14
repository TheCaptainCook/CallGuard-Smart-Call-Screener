package com.callguard.app.spam;

import android.util.Log;

import com.callguard.app.data.CallLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MlSpamAnalyser — Phase 3 pattern-recognition spam analysis.
 *
 * Implements a lightweight, on-device ML-style scoring model using
 * manually engineered features derived from call timing and frequency.
 *
 * No external ML library or network is required — all inference runs
 * synchronously on the calling thread in O(n) time.
 *
 * Feature set:
 *   1. Call count from same number in last 24h (frequency score).
 *   2. Time-of-day: calls outside 8am–8pm are higher risk.
 *   3. Pattern regularity: fixed-interval calls signal robodialers.
 *   4. First-time caller with no history = low baseline.
 *
 * Scoring: each feature contributes a weighted value; total ≥ 0.5 → spam.
 *
 * Phase 4 will optionally replace this with a TFLite model for on-device
 * neural inference trained on the community spam database.
 */
public class MlSpamAnalyser {

    private static final String TAG = "CG_MlSpam";

    /** Score threshold above which a call is classified as ML-spam. */
    public static final float ML_SPAM_THRESHOLD = 0.50f;

    // Feature weights (must sum to ≤ 1.0)
    private static final float WEIGHT_HIGH_FREQUENCY  = 0.35f;
    private static final float WEIGHT_ODD_HOUR        = 0.20f;
    private static final float WEIGHT_REGULARITY      = 0.25f;
    private static final float WEIGHT_REPEAT_AFTER_BLOCK = 0.20f;

    private MlSpamAnalyser() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Scores an incoming call using pattern-based ML features.
     *
     * @param callerNumber  The incoming caller number.
     * @param callHistory   Full call log history from Room (for feature extraction).
     * @return An {@link MlResult} with the score and feature breakdown.
     */
    public static MlResult analyse(String callerNumber, List<CallLog> callHistory) {
        float score = 0f;
        Map<String, Float> featureScores = new HashMap<>();

        // Filter history for this specific caller
        List<CallLog> callerHistory = filterByNumber(callerNumber, callHistory);

        // Feature 1: High call frequency in 24h
        float freqScore = scoreFrequency(callerHistory);
        score += freqScore * WEIGHT_HIGH_FREQUENCY;
        featureScores.put("frequency", freqScore);

        // Feature 2: Odd-hour call
        float hourScore = scoreCallHour();
        score += hourScore * WEIGHT_ODD_HOUR;
        featureScores.put("odd_hour", hourScore);

        // Feature 3: Regular interval pattern (robocaller detection)
        float regularityScore = scoreRegularity(callerHistory);
        score += regularityScore * WEIGHT_REGULARITY;
        featureScores.put("regularity", regularityScore);

        // Feature 4: Called again after being previously blocked
        float repeatAfterBlock = scoreRepeatAfterBlock(callerHistory);
        score += repeatAfterBlock * WEIGHT_REPEAT_AFTER_BLOCK;
        featureScores.put("repeat_after_block", repeatAfterBlock);

        score = Math.min(score, 1.0f);
        boolean isSpam = score >= ML_SPAM_THRESHOLD;

        Log.i(TAG, "ML spam score for '" + callerNumber + "': " + score
                + " | features=" + featureScores + " | isSpam=" + isSpam);

        return new MlResult(isSpam, score, featureScores);
    }

    // =========================================================================
    // Feature Scorers
    // =========================================================================

    /**
     * Scores based on how many times this number called in the last 24 hours.
     * 0 calls=0.0, 2-4 calls=0.4, 5+=1.0
     */
    private static float scoreFrequency(List<CallLog> callerHistory) {
        long yesterday = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        int count = 0;
        for (CallLog log : callerHistory) {
            if (log.timestampMs > yesterday) count++;
        }
        if (count == 0 || count == 1) return 0.0f;
        if (count <= 4)               return 0.4f;
        return 1.0f;
    }

    /**
     * Scores based on whether the current call is happening outside business hours
     * (before 8am or after 8pm local time).
     */
    private static float scoreCallHour() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        return (hour < 8 || hour >= 20) ? 1.0f : 0.0f;
    }

    /**
     * Detects robocaller patterns by checking if calls arrive at suspiciously
     * regular intervals (±30 seconds of a fixed cadence).
     */
    private static float scoreRegularity(List<CallLog> callerHistory) {
        if (callerHistory.size() < 3) return 0.0f;

        // Extract timestamps of recent calls
        long[] timestamps = new long[Math.min(callerHistory.size(), 5)];
        for (int i = 0; i < timestamps.length; i++) {
            timestamps[i] = callerHistory.get(i).timestampMs;
        }

        // Calculate intervals between calls
        long[] intervals = new long[timestamps.length - 1];
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = Math.abs(timestamps[i] - timestamps[i + 1]);
        }

        // Check if intervals are suspiciously uniform (std dev < 60s)
        double mean = 0;
        for (long interval : intervals) mean += interval;
        mean /= intervals.length;

        double variance = 0;
        for (long interval : intervals) {
            double diff = interval - mean;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / intervals.length);

        // Std deviation < 60 seconds = very regular = likely robocaller
        return (stdDev < TimeUnit.SECONDS.toMillis(60)) ? 1.0f : 0.0f;
    }

    /**
     * Scores high if this caller was previously blocked but called again.
     */
    private static float scoreRepeatAfterBlock(List<CallLog> callerHistory) {
        for (CallLog log : callerHistory) {
            if ("blocked".equals(log.outcome)) {
                return 1.0f;
            }
        }
        return 0.0f;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<CallLog> filterByNumber(String callerNumber, List<CallLog> all) {
        java.util.List<CallLog> result = new java.util.ArrayList<>();
        if (callerNumber == null) return result;
        for (CallLog log : all) {
            if (callerNumber.equals(log.callerNumber)) {
                result.add(log);
            }
        }
        return result;
    }

    // =========================================================================
    // Result
    // =========================================================================

    /**
     * Immutable result of an ML spam analysis pass.
     */
    public static class MlResult {

        public final boolean isSpam;
        public final float score;
        public final Map<String, Float> featureScores;

        MlResult(boolean isSpam, float score, Map<String, Float> featureScores) {
            this.isSpam = isSpam;
            this.score = score;
            this.featureScores = featureScores;
        }
    }
}
