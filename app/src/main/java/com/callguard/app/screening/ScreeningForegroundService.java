package com.callguard.app.screening;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.callguard.app.conversation.GreetingEngine;
import com.callguard.app.conversation.IntentClassifier;
import com.callguard.app.conversation.SpeechToTextManager;
import com.callguard.app.data.CallGuardDatabase;
import com.callguard.app.data.CallLog;
import com.callguard.app.data.CallLogDao;
import com.callguard.app.data.Transcript;
import com.callguard.app.spam.MlSpamAnalyser;
import com.callguard.app.spam.SpamDetector;
import com.callguard.app.spam.StirShakenVerifier;
import com.callguard.app.utils.ContactsHelper;
import com.callguard.app.utils.NotificationHelper;
import com.callguard.app.utils.PreferencesManager;
import com.callguard.app.utils.PrivacyManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ScreeningForegroundService — The orchestrator of the call screening flow.
 *
 * This foreground service is the backbone of the call screening engine. Running
 * as a foreground service ensures Android does not kill it during an active call.
 *
 * Screening Workflow (Phase 1 + Phase 2):
 * 1. {@code ACTION_START_SCREENING} received → run spam/contact checks → post notification.
 * 2. Wait for the configurable ring delay (default: 5 rings ≈ 25s).
 * 3. After delay: start the {@link GreetingEngine} + {@link SpeechToTextManager}.
 * 4. On greeting complete → save CallLog + Transcript to Room DB → stop.
 * 5. {@code ACTION_STOP_SCREENING} received → cancel timer, stop TTS/STT, persist, stop self.
 */
public class ScreeningForegroundService extends Service {

    private static final String TAG = "CG_ScreeningService";

    // --- Intent Actions ---
    public static final String ACTION_START_SCREENING = "com.callguard.action.START_SCREENING";
    public static final String ACTION_STOP_SCREENING  = "com.callguard.action.STOP_SCREENING";
    public static final String ACTION_USER_TAKE_CALL  = "com.callguard.action.USER_TAKE_CALL";

    // --- Intent Extras ---
    public static final String EXTRA_CALLER_NUMBER = "extra_caller_number";

    // Notification ID must be > 0 for foreground services
    private static final int NOTIFICATION_ID = 101;

    // Default ring delay: ~5 rings. User-configurable in preferences.
    private static final int DEFAULT_RING_DELAY_MS = 25_000;

    private Handler mainHandler;
    private Runnable answerCallRunnable;
    private GreetingEngine greetingEngine;
    private PowerManager.WakeLock wakeLock;

    // Phase 2 components
    private SpeechToTextManager sttManager;
    private CallLogDao callLogDao;
    private ExecutorService dbExecutor;
    private long currentCallLogId = -1;
    private long screeningStartTimeMs;

    private String currentCallerNumber;
    private boolean isScreening = false;
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        greetingEngine = new GreetingEngine(this);

        // Phase 2: initialize DB access and STT
        callLogDao = CallGuardDatabase.getInstance(this).callLogDao();
        dbExecutor = Executors.newSingleThreadExecutor();

        // Acquire a partial wake lock so the CPU stays active during audio playback.
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CallGuard:ScreeningWakeLock"
            );
        } else {
            Log.w(TAG, "PowerManager unavailable — wake lock not acquired.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "Received null intent — service restarted by system. Stopping.");
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand — action: " + action);

        if (ACTION_START_SCREENING.equals(action)) {
            currentCallerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER);
            startScreening(currentCallerNumber);

        } else if (ACTION_STOP_SCREENING.equals(action)) {
            stopScreening("external stop command");

        } else if (ACTION_USER_TAKE_CALL.equals(action)) {
            stopScreening("user took the call");

        } else {
            Log.w(TAG, "Unknown action received: " + action);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        cancelAnswerTimer();

        // Phase 2: stop STT
        if (sttManager != null) {
            sttManager.stopListening();
            sttManager.destroy();
            sttManager = null;
        }

        if (greetingEngine != null) {
            greetingEngine.shutdown();
            greetingEngine = null;
        }
        releaseWakeLock();

        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }

        super.onDestroy();
        Log.d(TAG, "Service destroyed.");
    }

    // =========================================================================
    // Core Screening Logic
    // =========================================================================

    /**
     * Initiates the screening workflow:
     * 1. Phase 2: Run spam detection + contact whitelist check.
     * 2. Create a CallLog entry in Room.
     * 3. If known contact → skip screening, log as "user_answered".
     * 4. Promote to foreground with notification.
     * 5. Acquire wake lock + schedule auto-answer timer.
     *
     * @param callerNumber The incoming caller's phone number (may be null).
     */
    private void startScreening(String callerNumber) {
        if (isScreening) {
            Log.w(TAG, "Screening already in progress — ignoring duplicate start.");
            return;
        }
        isScreening = true;
        screeningStartTimeMs = System.currentTimeMillis();
        Log.i(TAG, "--- SCREENING STARTED for: " + callerNumber + " ---");

        // Phase 2: Run spam detection (zero-latency, on main thread is fine)
        SpamDetector.SpamResult spamResult = SpamDetector.analyse(callerNumber);

        // Phase 3: ML pattern analysis + STIR/SHAKEN run on BG thread with DB access
        dbExecutor.execute(() -> {
            // Load recent history for ML + STIR/SHAKEN frequency checks
            java.util.List<CallLog> history = callLogDao.getRecentHistorySync(50);

            // Phase 3a: ML spam analysis
            MlSpamAnalyser.MlResult mlResult = MlSpamAnalyser.analyse(callerNumber, history);

            // Phase 3b: STIR/SHAKEN attestation
            StirShakenVerifier.AttestationResult attestation =
                    StirShakenVerifier.verify(callerNumber, history);

            // Contact whitelist check
            boolean isContact = ContactsHelper.isKnownContact(this, callerNumber);

            // Create and persist the call log entry
            CallLog callLog = new CallLog(callerNumber, screeningStartTimeMs);
            // Phase 2 fields
            callLog.isSpam = spamResult.isSpam || mlResult.isSpam;
            callLog.spamScore = Math.max(spamResult.score, mlResult.score);
            callLog.isKnownContact = isContact;
            // Phase 3 fields
            callLog.mlSpamScore = mlResult.score;
            callLog.attestationLevel = attestation.level.name();

            if (isContact) {
                callLog.outcome = "user_answered";
                callLogDao.insertCallLog(callLog);
                Log.i(TAG, "Caller is a known contact — skipping screening.");
                mainHandler.post(() -> stopScreening("known contact"));
                return;
            }

            currentCallLogId = callLogDao.insertCallLog(callLog);
            Log.d(TAG, "CallLog inserted id=" + currentCallLogId
                    + " attestation=" + attestation.level
                    + " mlScore=" + mlResult.score);

            mainHandler.post(() -> {
                if (isDestroyed || !isScreening) return;
                continueScreeningSetup(callerNumber);
            });
        });
    }

    /**
     * Continues the screening setup after the background DB/contact work is done.
     * This runs on the main thread.
     */
    private void continueScreeningSetup(String callerNumber) {
        // Step 1: Promote to foreground immediately to avoid ANR.
        Notification notification = NotificationHelper.buildScreeningNotification(this, callerNumber);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Step 2: Acquire wake lock
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(DEFAULT_RING_DELAY_MS + 60_000L);
        }

        // Step 3: Schedule greeting playback after ring delay
        PreferencesManager prefs = new PreferencesManager(this);
        int delayMs = prefs.getRingDelayMs(DEFAULT_RING_DELAY_MS);
        scheduleAutoAnswer(delayMs);
    }

    /**
     * Schedules the Runnable that will trigger TTS greeting + STT after a delay.
     */
    private void scheduleAutoAnswer(int delayMs) {
        answerCallRunnable = () -> {
            if (isDestroyed || !isScreening) return;

            Log.i(TAG, "Ring delay elapsed — playing greeting.");

            if (greetingEngine == null) {
                stopScreening("greeting engine unavailable");
                return;
            }

            // Phase 2+3: Start STT to capture the caller's response after greeting
            boolean localOnly = new PrivacyManager(this).isLocalOnlyEnabled();
            sttManager = new SpeechToTextManager(this, (text, confidence) -> {
                Log.i(TAG, "STT transcript received: \"" + text + "\" (confidence=" + confidence + ")");
                // Persist transcript to Room
                if (currentCallLogId > 0 && text != null && !text.isEmpty()) {
                    dbExecutor.execute(() -> {
                        Transcript transcript = new Transcript(currentCallLogId, text, confidence);
                        callLogDao.insertTranscript(transcript);
                        Log.d(TAG, "Transcript saved for callLogId=" + currentCallLogId);
                    });
                }
            }, localOnly);

            greetingEngine.playGreeting(currentCallerNumber, () -> {
                // GreetingCallback fires on the TTS engine's internal thread.
                mainHandler.post(() -> {
                    if (isDestroyed || !isScreening) return;

                    Log.i(TAG, "Greeting playback complete — starting STT listener.");
                    // Start listening for the caller's response
                    if (sttManager != null) {
                        sttManager.startListening();
                    }

                    // Update notification to indicate we're now listening
                    NotificationHelper.postCallSummaryNotification(
                            ScreeningForegroundService.this,
                            currentCallerNumber,
                            "Caller greeted. Listening for response..."
                    );

                    // Auto-stop screening after 15s of listening (caller has had their turn)
                    mainHandler.postDelayed(() -> {
                        if (isScreening) {
                            if (sttManager != null) sttManager.stopListening();
                            String finalTranscript = sttManager != null ? sttManager.getTranscript() : "";
                            IntentClassifier.IntentType intent = IntentClassifier.classifyIntent(finalTranscript);
                            Log.i(TAG, "Caller intent classified as: " + intent);
                            
                            greetingEngine.playDynamicResponse(intent);
                            
                            // Allow 8s for dynamic response playback before ending
                            mainHandler.postDelayed(() -> {
                                if (isScreening) stopScreening("completed NLP screening");
                            }, 8000);
                        }
                    }, 15_000);
                });
            });
        };

        Log.d(TAG, "Greeting scheduled in " + (delayMs / 1000) + "s");
        mainHandler.postDelayed(answerCallRunnable, delayMs);
    }

    /**
     * Cancels a pending auto-answer if it has not fired yet.
     */
    private void cancelAnswerTimer() {
        if (answerCallRunnable != null) {
            mainHandler.removeCallbacks(answerCallRunnable);
            answerCallRunnable = null;
            Log.d(TAG, "Auto-answer timer cancelled.");
        }
    }

    /**
     * Cleanly shuts down the screening session. Persists final call duration to Room.
     *
     * @param reason A human-readable string explaining why screening stopped.
     */
    private void stopScreening(String reason) {
        if (!isScreening) return;
        isScreening = false;
        Log.i(TAG, "--- SCREENING STOPPED: " + reason + " ---");

        cancelAnswerTimer();

        // Phase 2: Stop STT
        if (sttManager != null) {
            sttManager.stopListening();
        }

        if (greetingEngine != null) {
            greetingEngine.stopGreeting();
        }

        // Phase 2: Persist final duration + outcome to Room
        if (currentCallLogId > 0) {
            int durationSeconds = (int) ((System.currentTimeMillis() - screeningStartTimeMs) / 1000);
            dbExecutor.execute(() -> {
                CallLog log = callLogDao.getCallLogById(currentCallLogId);
                if (log != null) {
                    log.durationSeconds = durationSeconds;
                    log.outcome = reason.contains("user") ? "user_answered" : "screened";
                    callLogDao.updateCallLog(log);
                    Log.d(TAG, "CallLog updated: duration=" + durationSeconds + "s outcome=" + log.outcome);
                }
            });
        }

        releaseWakeLock();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }

        stopSelf();
    }

    /**
     * Releases the CPU wake lock if it is currently held.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released.");
        }
    }
}
