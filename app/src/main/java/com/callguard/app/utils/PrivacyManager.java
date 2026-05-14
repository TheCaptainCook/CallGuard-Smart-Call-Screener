package com.callguard.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PrivacyManager — Phase 3 privacy hardening: local-only processing flag.
 *
 * Controls whether transcript data and call logs are processed and stored
 * strictly on-device. When local-only mode is enabled:
 * - STT is restricted to on-device recognition only
 *   (SpeechRecognizer with EXTRA_PREFER_OFFLINE = true).
 * - No data is sent to any external API for Phase 4 community sync.
 * - Biometric lock enforcement for the dashboard is activated.
 *
 * This is the central source of truth for all privacy-sensitive decisions
 * across the app — all components should check these flags before sending
 * any data off-device.
 */
public class PrivacyManager {

    private static final String PREFS_NAME    = "callguard_privacy";
    private static final String KEY_LOCAL_ONLY    = "local_only_processing";
    private static final String KEY_BIOMETRIC_LOCK = "biometric_lock_enabled";

    private final SharedPreferences prefs;

    public PrivacyManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =========================================================================
    // Local-Only Processing
    // =========================================================================

    /**
     * Returns true if the user has opted into local-only processing mode.
     * Default: false (cloud-assisted STT is allowed).
     */
    public boolean isLocalOnlyEnabled() {
        return prefs.getBoolean(KEY_LOCAL_ONLY, false);
    }

    /**
     * Enables or disables local-only processing.
     * When enabled: STT uses EXTRA_PREFER_OFFLINE, community sync is suspended.
     */
    public void setLocalOnlyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCAL_ONLY, enabled).apply();
    }

    // =========================================================================
    // Biometric Lock
    // =========================================================================

    /**
     * Returns true if the biometric lock for the dashboard is enabled.
     * Default: false.
     */
    public boolean isBiometricLockEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK, false);
    }

    /**
     * Enables or disables the biometric lock for the dashboard.
     */
    public void setBiometricLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply();
    }
}
