package com.callguard.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PreferencesManager — Centralized accessor for all SharedPreferences settings.
 *
 * Encapsulates all preference keys and provides typed getters/setters,
 * preventing magic string keys from being scattered across the codebase.
 *
 * Phase 1 settings:
 * - {@code isScreeningEnabled}: Master toggle for the screening engine.
 * - {@code ringDelayMs}: How long to let the phone ring before auto-answering.
 * - {@code customGreeting}: The TTS greeting message.
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "callguard_prefs";

    // --- Preference Keys ---
    private static final String KEY_SCREENING_ENABLED = "screening_enabled";
    private static final String KEY_RING_DELAY_MS     = "ring_delay_ms";
    private static final String KEY_CUSTOM_GREETING   = "custom_greeting";
    private static final String KEY_LANGUAGE          = "language_preference";

    // --- Defaults ---
    public static final boolean DEFAULT_SCREENING_ENABLED = true;
    public static final int     DEFAULT_RING_DELAY_MS     = 25_000; // ~5 rings

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =========================================================================
    // Screening Toggle
    // =========================================================================

    /**
     * Returns whether the call screening engine is enabled.
     * Defaults to {@code true} on first launch.
     */
    public boolean isScreeningEnabled() {
        return prefs.getBoolean(KEY_SCREENING_ENABLED, DEFAULT_SCREENING_ENABLED);
    }

    /**
     * Sets the master screening toggle.
     *
     * @param enabled {@code true} to enable screening, {@code false} to disable.
     */
    public void setScreeningEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREENING_ENABLED, enabled).apply();
    }

    // =========================================================================
    // Ring Delay
    // =========================================================================

    /**
     * Returns the configured ring delay in milliseconds.
     * This is the time the phone will ring before the assistant auto-answers.
     *
     * @param defaultMs The fallback value if no preference is stored.
     * @return Ring delay in milliseconds.
     */
    public int getRingDelayMs(int defaultMs) {
        return prefs.getInt(KEY_RING_DELAY_MS, defaultMs);
    }

    /**
     * Sets the ring delay in milliseconds.
     *
     * @param delayMs The desired delay. Valid range: 5000ms (1 ring) to 60000ms.
     */
    public void setRingDelayMs(int delayMs) {
        prefs.edit().putInt(KEY_RING_DELAY_MS, delayMs).apply();
    }

    // =========================================================================
    // Custom Greeting
    // =========================================================================

    /**
     * Returns the user-configured TTS greeting text.
     *
     * @param defaultGreeting The fallback greeting text if none is configured.
     * @return The greeting string to be spoken by the TTS engine.
     */
    public String getCustomGreeting(String defaultGreeting) {
        return prefs.getString(KEY_CUSTOM_GREETING, defaultGreeting);
    }

    /**
     * Saves a custom TTS greeting message.
     *
     * @param greeting The new greeting text. Must not be null or empty.
     */
    public void setCustomGreeting(String greeting) {
        if (greeting != null && !greeting.trim().isEmpty()) {
            prefs.edit().putString(KEY_CUSTOM_GREETING, greeting.trim()).apply();
        }
    }

    // =========================================================================
    // Language
    // =========================================================================

    /**
     * Returns the selected language code for the TTS engine.
     *
     * @return BCP-47 language tag, defaulting to "en-US".
     */
    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "en-US");
    }

    /**
     * Sets the language for the TTS engine.
     *
     * @param languageCode BCP-47 language tag (e.g. "en-US", "es-ES", "fr-FR").
     */
    public void setLanguage(String languageCode) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    /**
     * Resets all preferences to their default values.
     * Used in testing and the "Reset Settings" option.
     */
    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
