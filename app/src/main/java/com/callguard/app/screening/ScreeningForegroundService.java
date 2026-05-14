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
import com.callguard.app.utils.NotificationHelper;
import com.callguard.app.utils.PreferencesManager;

/**
 * ScreeningForegroundService — The orchestrator of the Phase 1 screening flow.
 *
 * This foreground service is the backbone of the call screening engine. Running
 * as a foreground service ensures Android does not kill it during an active call.
 *
 * Screening Workflow:
 * 1. {@code ACTION_START_SCREENING} received → post foreground notification.
 * 2. Wait for the configurable ring delay (default: 5 rings ≈ 25s).
 * 3. After delay: start the {@link GreetingEngine} to play the TTS greeting.
 * 4. {@code ACTION_STOP_SCREENING} received → cancel timer, stop TTS, stop self.
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

    private String currentCallerNumber;
    private boolean isScreening = false;
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        greetingEngine = new GreetingEngine(this);

        // Acquire a partial wake lock so the CPU stays active during audio playback.
        // Guard against null in case PowerManager is unavailable (emulators).
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
            // User tapped "Take Call" in the notification — hand the call back
            stopScreening("user took the call");

        } else {
            Log.w(TAG, "Unknown action received: " + action);
            stopSelf();
        }

        // START_NOT_STICKY: if killed by system, do not restart without an explicit intent
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
        // Cancel timer and release TTS without calling stopSelf() again
        cancelAnswerTimer();
        if (greetingEngine != null) {
            greetingEngine.shutdown();
            greetingEngine = null;
        }
        releaseWakeLock();
        super.onDestroy();
        Log.d(TAG, "Service destroyed.");
    }

    // =========================================================================
    // Core Screening Logic
    // =========================================================================

    /**
     * Initiates the screening workflow:
     * 1. Promotes service to foreground with a notification.
     * 2. Acquires wake lock.
     * 3. Schedules the auto-answer timer.
     *
     * @param callerNumber The incoming caller's phone number (may be null).
     */
    private void startScreening(String callerNumber) {
        if (isScreening) {
            Log.w(TAG, "Screening already in progress — ignoring duplicate start.");
            return;
        }
        isScreening = true;
        Log.i(TAG, "--- SCREENING STARTED for: " + callerNumber + " ---");

        // Step 1: Promote to foreground immediately to avoid ANR.
        // On Android 14+ (API 34), startForeground() MUST include the service type flags
        // or the OS throws MissingForegroundServiceTypeException.
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
            // Max hold: ring delay + 1 min buffer so we never leak the lock
            wakeLock.acquire(DEFAULT_RING_DELAY_MS + 60_000L);
        }

        // Step 3: Schedule greeting playback after ring delay
        PreferencesManager prefs = new PreferencesManager(this);
        int delayMs = prefs.getRingDelayMs(DEFAULT_RING_DELAY_MS);
        scheduleAutoAnswer(delayMs);
    }

    /**
     * Schedules the Runnable that will trigger TTS greeting after a delay.
     *
     * @param delayMs Delay in milliseconds before playing the greeting.
     */
    private void scheduleAutoAnswer(int delayMs) {
        answerCallRunnable = () -> {
            if (isDestroyed || !isScreening) return;

            Log.i(TAG, "Ring delay elapsed — playing greeting.");

            if (greetingEngine == null) {
                stopScreening("greeting engine unavailable");
                return;
            }

            greetingEngine.playGreeting(currentCallerNumber, () -> {
                // GreetingCallback fires on the TTS engine's internal thread.
                // All UI/service operations must run on the main thread.
                mainHandler.post(() -> {
                    if (isDestroyed || !isScreening) return;
                    Log.i(TAG, "Greeting playback complete.");
                    NotificationHelper.postCallSummaryNotification(
                            ScreeningForegroundService.this,
                            currentCallerNumber,
                            "Caller greeted. Awaiting further input."
                    );
                    stopScreening("greeting complete");
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
     * Cleanly shuts down the screening session.
     *
     * @param reason A human-readable string explaining why screening stopped.
     */
    private void stopScreening(String reason) {
        if (!isScreening) return;
        isScreening = false;
        Log.i(TAG, "--- SCREENING STOPPED: " + reason + " ---");

        cancelAnswerTimer();
        if (greetingEngine != null) {
            greetingEngine.stopGreeting();
        }
        releaseWakeLock();

        // API 33+ deprecates stopForeground(boolean); use the int-flag overload instead.
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
