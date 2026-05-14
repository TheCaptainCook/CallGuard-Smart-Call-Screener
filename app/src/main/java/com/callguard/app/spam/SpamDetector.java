package com.callguard.app.spam;

import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SpamDetector — Phase 2 rule-based spam detection engine.
 *
 * Applies a stack of lightweight, zero-latency rules to decide whether
 * an incoming call is likely spam — before the network is involved.
 *
 * Rules applied (in order, each adds to the score):
 * 1. Hidden / withheld number (+0.8)
 * 2. Known spam prefix (+0.6) — e.g. common telemarketing patterns
 * 3. Short number length (+0.4) — typical of auto-dialers
 * 4. Repeated call within 5 minutes (not yet tracked in Phase 2, reserved)
 *
 * Threshold: score ≥ 0.6 → flagged as spam.
 *
 * Phase 3 will add ML-based analysis on top of these rules.
 */
public class SpamDetector {

    private static final String TAG = "CG_SpamDetector";

    /** Score threshold above which a call is classified as spam. */
    public static final float SPAM_THRESHOLD = 0.6f;

    /**
     * Known high-risk prefixes associated with telemarketing / robocalls.
     * This list is intentionally minimal for Phase 2 — Phase 4 will sync
     * from the community database.
     */
    private static final Set<String> SPAM_PREFIXES = new HashSet<>(Arrays.asList(
            "1800", "1900", "1600",   // Toll-free / premium in India
            "140", "141", "142",       // Common telemarketing prefixes in India
            "0800", "0808", "0843",   // UK toll-free / high-cost
            "1888", "1877", "1866",   // US toll-free blocks
            "900"                      // Premium-rate
    ));

    // Private constructor — utility class
    private SpamDetector() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Analyses a caller number and returns a {@link SpamResult}.
     *
     * @param callerNumber The raw phone number string, or null/"unknown" for withheld.
     * @return A {@link SpamResult} with the score and reason.
     */
    public static SpamResult analyse(String callerNumber) {
        float score = 0f;
        StringBuilder reasons = new StringBuilder();

        // Rule 1: Hidden / unknown / withheld number
        if (callerNumber == null
                || callerNumber.isEmpty()
                || "unknown".equalsIgnoreCase(callerNumber)) {
            score += 0.8f;
            reasons.append("Hidden number. ");
            Log.d(TAG, "Rule 1 hit: hidden number (+0.8)");
        } else {
            // Normalize — strip non-digits except leading +
            String normalized = normalize(callerNumber);

            // Rule 2: Matches a known spam prefix
            for (String prefix : SPAM_PREFIXES) {
                if (normalized.startsWith(prefix)) {
                    score += 0.6f;
                    reasons.append("Spam prefix '").append(prefix).append("'. ");
                    Log.d(TAG, "Rule 2 hit: prefix " + prefix + " (+0.6)");
                    break;
                }
            }

            // Rule 3: Very short number (less than 6 digits) — auto-dialler pattern
            if (normalized.length() < 6) {
                score += 0.4f;
                reasons.append("Short number (").append(normalized.length()).append(" digits). ");
                Log.d(TAG, "Rule 3 hit: short number (+0.4)");
            }
        }

        // Cap at 1.0
        score = Math.min(score, 1.0f);
        boolean isSpam = score >= SPAM_THRESHOLD;

        Log.i(TAG, "Spam analysis for '" + callerNumber + "': score=" + score
                + " isSpam=" + isSpam + " reasons=" + reasons);

        return new SpamResult(isSpam, score, reasons.toString().trim());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String normalize(String number) {
        // Remove spaces, dashes, parentheses; keep leading digits
        return number.replaceAll("[^0-9]", "");
    }

    // =========================================================================
    // Result
    // =========================================================================

    /**
     * Immutable result object returned by {@link #analyse(String)}.
     */
    public static class SpamResult {

        public final boolean isSpam;
        public final float score;
        public final String reason;

        SpamResult(boolean isSpam, float score, String reason) {
            this.isSpam = isSpam;
            this.score = score;
            this.reason = reason;
        }
    }
}
