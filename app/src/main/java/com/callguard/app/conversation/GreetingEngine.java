package com.callguard.app.conversation;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.callguard.app.utils.PreferencesManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * GreetingEngine — Phase 1 TTS (Text-to-Speech) greeting playback module.
 *
 * Responsibilities:
 * - Initialize the Android TTS engine with the user's preferred locale.
 * - Play a configurable greeting message to the caller after the call is answered.
 * - Notify the caller site when playback is complete via a {@link GreetingCallback}.
 * - Support graceful shutdown and interruption.
 *
 * Default Greeting:
 *   "Hi, this is an automated assistant. The person you're calling cannot
 *   answer right now. Please state your name and reason for calling."
 *
 * The greeting can be customized by the user in settings (Phase 2+).
 */
public class GreetingEngine {

    private static final String TAG = "CG_GreetingEngine";

    /** Default greeting used when no custom greeting is configured. */
    public static final String DEFAULT_GREETING =
            "Hi, this is an automated assistant. " +
            "The person you're calling cannot answer right now. " +
            "Please state your name and reason for calling.";

    /** Default call termination message played after the screening window closes. */
    public static final String DEFAULT_FAREWELL =
            "Thank you for your message. I'll make sure they get back to you shortly.";

    // Utterance ID prefix for tracking TTS progress events
    private static final String UTTERANCE_ID_GREETING = "greeting_";

    private final Context context;
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    // Callback to invoke when greeting finishes playing
    private GreetingCallback pendingCallback;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Initializes the GreetingEngine and starts the TTS engine in the background.
     * TTS initialization is asynchronous; playback begins only after initialization
     * succeeds, making this safe to instantiate in {@code onCreate()}.
     *
     * @param context The application context.
     */
    public GreetingEngine(Context context) {
        this.context = context.getApplicationContext();
        initTts();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Plays the configured greeting to the active call.
     * If TTS is still initializing, the greeting will be queued and played
     * as soon as the engine is ready.
     *
     * @param callerNumber The caller's number (reserved for Phase 2 dynamic greetings).
     * @param callback     Called when greeting playback finishes or is interrupted.
     */
    public void playGreeting(String callerNumber, GreetingCallback callback) {
        this.pendingCallback = callback;

        PreferencesManager prefs = new PreferencesManager(context);
        String greetingText = prefs.getCustomGreeting(DEFAULT_GREETING);

        Log.i(TAG, "Playing greeting: \"" + greetingText + "\"");

        if (isTtsReady) {
            speak(greetingText, UTTERANCE_ID_GREETING + UUID.randomUUID());
        } else {
            // TTS engine is still warming up — it will play when ready via the init callback
            Log.w(TAG, "TTS not yet ready. Greeting is queued and will play on init completion.");
        }
    }

    /**
     * Plays the farewell message to gracefully end the screening.
     * Called after the screening window expires in Phase 2.
     */
    public void playFarewell() {
        if (isTtsReady) {
            speak(DEFAULT_FAREWELL, "farewell_" + UUID.randomUUID());
        }
    }

    /**
     * Stops any ongoing TTS playback immediately.
     * Should be called when the call ends or the user manually takes the call.
     */
    public void stopGreeting() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            Log.d(TAG, "TTS playback stopped.");
        }
    }

    /**
     * Shuts down the TTS engine and releases resources.
     * Must be called in the service's {@code onDestroy()} to prevent memory leaks.
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isTtsReady = false;
            Log.d(TAG, "TTS engine shut down.");
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Initializes the Android TTS engine asynchronously.
     * Sets up a progress listener to track utterance completion and fire callbacks.
     */
    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Default to the device locale; falls back to US English
                int langResult = tts.setLanguage(Locale.getDefault());
                if (langResult == TextToSpeech.LANG_MISSING_DATA
                        || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Device locale not supported — falling back to en-US.");
                    tts.setLanguage(Locale.US);
                }

                // Slightly slower speech rate for clearer communication
                tts.setSpeechRate(0.9f);
                tts.setPitch(1.0f);

                isTtsReady = true;
                Log.i(TAG, "TTS engine initialized successfully.");

                // Attach progress listener
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS utterance started: " + utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.d(TAG, "TTS utterance done: " + utteranceId);
                        if (utteranceId.startsWith(UTTERANCE_ID_GREETING) && pendingCallback != null) {
                            pendingCallback.onGreetingFinished();
                            pendingCallback = null;
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.e(TAG, "TTS error for utterance: " + utteranceId);
                        if (pendingCallback != null) {
                            // Still fire callback on error so screening doesn't hang
                            pendingCallback.onGreetingFinished();
                            pendingCallback = null;
                        }
                    }
                });

                // If playGreeting() was called before TTS was ready, a callback is waiting
                if (pendingCallback != null) {
                    Log.d(TAG, "TTS ready — playing queued greeting.");
                    PreferencesManager prefs = new PreferencesManager(context);
                    String greetingText = prefs.getCustomGreeting(DEFAULT_GREETING);
                    speak(greetingText, UTTERANCE_ID_GREETING + UUID.randomUUID());
                }

            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                if (pendingCallback != null) {
                    pendingCallback.onGreetingFinished();
                    pendingCallback = null;
                }
            }
        });
    }

    /**
     * Queues a text utterance for playback using the TTS engine.
     *
     * @param text        The text to speak.
     * @param utteranceId A unique ID for tracking this utterance's completion.
     */
    private void speak(String text, String utteranceId) {
        if (tts == null || !isTtsReady) {
            Log.e(TAG, "speak() called but TTS is not ready.");
            return;
        }
        // QUEUE_FLUSH discards any previous speech and starts this one immediately
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    // =========================================================================
    // Callback Interface
    // =========================================================================

    /**
     * Callback interface for greeting playback completion events.
     * Implemented by {@link com.callguard.app.screening.ScreeningForegroundService}.
     */
    public interface GreetingCallback {
        /** Invoked when the greeting has finished playing (or on error). */
        void onGreetingFinished();
    }
}
