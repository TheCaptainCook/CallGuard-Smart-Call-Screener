package com.callguard.app.conversation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * SpeechToTextManager — Phase 2 real-time STT integration.
 *
 * Wraps Android's {@link SpeechRecognizer} to capture and transcribe
 * the caller's speech during an active screening session.
 *
 * Key design decisions:
 * - Must run on the Main Thread (SpeechRecognizer requirement).
 * - Uses continuous listening via {@code EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS}
 *   to capture as much speech as possible before the call ends.
 * - Notifies results via {@link TranscriptCallback} for storage in Room.
 *
 * Usage:
 * <pre>
 *   sttManager = new SpeechToTextManager(context, (text, confidence) -> {
 *       // store transcript in DB
 *   });
 *   sttManager.startListening();
 *   // ... later:
 *   sttManager.stopListening();
 *   sttManager.destroy();
 * </pre>
 */
public class SpeechToTextManager {

    private static final String TAG = "CG_STT";

    private final Context context;
    private final TranscriptCallback callback;
    private final Handler mainHandler;
    private final boolean preferOffline;

    private SpeechRecognizer recognizer;
    private boolean isListening = false;
    private boolean isDestroyed = false;

    // Buffer to accumulate all partial results into a final transcript
    private final StringBuilder transcriptBuffer = new StringBuilder();

    public SpeechToTextManager(Context context, TranscriptCallback callback) {
        this(context, callback, false);
    }

    /**
     * @param preferOffline If true, passes EXTRA_PREFER_OFFLINE so the recognizer
     *                      avoids sending audio to cloud servers (Phase 3 local-only mode).
     */
    public SpeechToTextManager(Context context, TranscriptCallback callback, boolean preferOffline) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.preferOffline = preferOffline;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Starts listening for speech. Must be called from the Main Thread.
     * If STT is not available on the device, logs a warning and returns.
     */
    public void startListening() {
        mainHandler.post(() -> {
            if (isDestroyed || isListening) return;

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition not available on this device.");
                return;
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new ScreeningRecognitionListener());

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            // Allow up to 30s of silence before auto-stopping
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30_000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            // Phase 3: respect local-only privacy mode
            if (preferOffline) {
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            }

            recognizer.startListening(intent);
            isListening = true;
            Log.d(TAG, "STT listening started.");
        });
    }

    /**
     * Stops listening and flushes the accumulated transcript buffer.
     */
    public void stopListening() {
        mainHandler.post(() -> {
            if (!isListening || recognizer == null) return;
            recognizer.stopListening();
            isListening = false;
            Log.d(TAG, "STT listening stopped.");
        });
    }

    /**
     * Releases the SpeechRecognizer resource. Must be called in onDestroy().
     */
    public void destroy() {
        mainHandler.post(() -> {
            isDestroyed = true;
            isListening = false;
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            Log.d(TAG, "STT manager destroyed.");
        });
    }

    /**
     * Returns the full transcript accumulated so far.
     */
    public String getTranscript() {
        return transcriptBuffer.toString().trim();
    }

    // =========================================================================
    // Recognition Listener
    // =========================================================================

    private class ScreeningRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech.");
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partial = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (partial != null && !partial.isEmpty()) {
                Log.d(TAG, "Partial: " + partial.get(0));
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                float confidence = (scores != null && scores.length > 0) ? scores[0] : 0.5f;

                if (transcriptBuffer.length() > 0) {
                    transcriptBuffer.append(" ");
                }
                transcriptBuffer.append(text);

                Log.i(TAG, "STT result: \"" + text + "\" (confidence: " + confidence + ")");
                if (callback != null) {
                    callback.onTranscriptResult(transcriptBuffer.toString().trim(), confidence);
                }
            }
            isListening = false;
        }

        @Override
        public void onError(int error) {
            Log.w(TAG, "STT error code: " + error + " — " + errorCodeToString(error));
            isListening = false;
            // For network errors or no-speech, don't crash — just report empty transcript
            if (callback != null && transcriptBuffer.length() == 0) {
                callback.onTranscriptResult("", 0f);
            }
        }

        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { Log.d(TAG, "End of speech detected."); }
        @Override public void onEvent(int eventType, Bundle params) {}

        private String errorCodeToString(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: return "ERROR_AUDIO";
                case SpeechRecognizer.ERROR_CLIENT: return "ERROR_CLIENT";
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "ERROR_INSUFFICIENT_PERMISSIONS";
                case SpeechRecognizer.ERROR_NETWORK: return "ERROR_NETWORK";
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "ERROR_NETWORK_TIMEOUT";
                case SpeechRecognizer.ERROR_NO_MATCH: return "ERROR_NO_MATCH";
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "ERROR_RECOGNIZER_BUSY";
                case SpeechRecognizer.ERROR_SERVER: return "ERROR_SERVER";
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "ERROR_SPEECH_TIMEOUT";
                default: return "UNKNOWN(" + error + ")";
            }
        }
    }

    // =========================================================================
    // Callback Interface
    // =========================================================================

    /**
     * Callback for STT results. Fired on the Main Thread.
     */
    public interface TranscriptCallback {
        /**
         * Called when a speech result is ready.
         *
         * @param text       The transcribed text (may be empty on error/silence).
         * @param confidence STT confidence score 0.0–1.0.
         */
        void onTranscriptResult(String text, float confidence);
    }
}
