package com.callguard.app.spam;

import android.util.Log;

import com.callguard.app.data.CallLog;

import java.util.List;

/**
 * StirShakenVerifier — Phase 3 caller ID attestation verification.
 *
 * STIR/SHAKEN (Secure Telephone Identity Revisited / Signature-based
 * Handling of Asserted information using toKENs) is a framework used by
 * US and international carriers to combat caller ID spoofing.
 *
 * In a full carrier integration, the SIP INVITE contains a signed PASSporT
 * JWT in the "Identity" header. Attestation levels:
 *   A — Full Attestation: carrier verified the caller owns the number.
 *   B — Partial Attestation: carrier verified caller originated the call.
 *   C — Gateway Attestation: unknown origin, passed through a gateway.
 *
 * Phase 3 Implementation (device-side):
 * Android does not expose raw SIP headers at the app level. We therefore
 * derive a simulated attestation level from:
 *   1. Whether the number is a verified carrier format (E.164).
 *   2. Call frequency patterns from our own call log history.
 *   3. Whether the number matched a known spam prefix (from SpamDetector).
 *
 * Phase 4 will integrate with a real STIR/SHAKEN server-side API.
 */
public class StirShakenVerifier {

    private static final String TAG = "CG_StirShaken";

    /** The three attestation levels in descending order of trust. */
    public enum AttestationLevel {
        /** Full: caller ID is verified — safe to trust. */
        A,
        /** Partial: call origin verified but number ownership not confirmed. */
        B,
        /** Gateway: unknown origin — treat with caution. */
        C,
        /** Unverified: unable to determine attestation. */
        UNVERIFIED
    }

    /** Minimum E.164 digit count (including country code). */
    private static final int MIN_E164_DIGITS = 7;
    /** Maximum E.164 digit count. */
    private static final int MAX_E164_DIGITS = 15;

    private StirShakenVerifier() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Derives an attestation level for the given caller number by analysing
     * call patterns from the historical call logs.
     *
     * @param callerNumber  The incoming caller number (raw string).
     * @param recentHistory Last N call logs from Room DB for frequency analysis.
     * @return An {@link AttestationResult} with the derived level and reason.
     */
    public static AttestationResult verify(String callerNumber, List<CallLog> recentHistory) {
        if (callerNumber == null || callerNumber.isEmpty()
                || "unknown".equalsIgnoreCase(callerNumber)) {
            Log.d(TAG, "Hidden number — attestation: UNVERIFIED");
            return new AttestationResult(AttestationLevel.UNVERIFIED,
                    "Hidden/withheld number cannot be verified.");
        }

        String normalized = normalize(callerNumber);

        // Rule 1: Must be valid E.164 length for Level A
        if (!isValidE164Length(normalized)) {
            Log.d(TAG, "Number length invalid for E.164 — attestation: C");
            return new AttestationResult(AttestationLevel.C,
                    "Number does not conform to E.164 format.");
        }

        // Rule 2: Frequency analysis — repeated calls in a short window = Gateway
        if (recentHistory != null && isHighFrequency(callerNumber, recentHistory)) {
            Log.w(TAG, "High-frequency caller detected — attestation: C");
            return new AttestationResult(AttestationLevel.C,
                    "High call frequency pattern detected.");
        }

        // Rule 3: SpamDetector cross-check for prefix hits
        SpamDetector.SpamResult spam = SpamDetector.analyse(callerNumber);
        if (spam.isSpam) {
            Log.d(TAG, "Spam prefix match — attestation: B");
            return new AttestationResult(AttestationLevel.B,
                    "Number matches known spam prefix. Partial attestation.");
        }

        // Passes all checks — grant partial attestation (we can't do Full A without
        // carrier-side integration)
        Log.d(TAG, "Number passed all checks — attestation: B");
        return new AttestationResult(AttestationLevel.B,
                "Number format is valid. No spam signals detected.");
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private static String normalize(String number) {
        return number.replaceAll("[^0-9]", "");
    }

    private static boolean isValidE164Length(String digits) {
        return digits.length() >= MIN_E164_DIGITS && digits.length() <= MAX_E164_DIGITS;
    }

    /**
     * Checks if the caller has called more than 3 times in the last 10 minutes.
     * This is a strong spam/robocall signal.
     */
    private static boolean isHighFrequency(String callerNumber, List<CallLog> history) {
        long tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000L);
        int recentCount = 0;
        for (CallLog log : history) {
            if (callerNumber.equals(log.callerNumber) && log.timestampMs > tenMinutesAgo) {
                recentCount++;
            }
        }
        return recentCount >= 3;
    }

    // =========================================================================
    // Result
    // =========================================================================

    /**
     * Immutable result of a STIR/SHAKEN verification pass.
     */
    public static class AttestationResult {

        public final AttestationLevel level;
        public final String reason;

        AttestationResult(AttestationLevel level, String reason) {
            this.level = level;
            this.reason = reason;
        }

        /** @return true if the attestation level is safe (A or B). */
        public boolean isTrusted() {
            return level == AttestationLevel.A || level == AttestationLevel.B;
        }
    }
}
