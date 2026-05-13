package com.callguard.app.screening;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.callguard.app.conversation.GreetingEngine;
import com.callguard.app.ui.MainActivity;
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
 * 3. After delay: command the call to be answered via {@link CallScreeningService}.
 * 4. Start the {@link GreetingEngine} to play the TTS greeting.
 * 5. {@code ACTION_STOP_SCREENING} received → cancel timer, stop TTS, stop self.
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

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        greetingEngine = new GreetingEngine(this);

        // Acquire wake lock so the CPU stays active during audio playback
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallGuard:ScreeningWakeLock");
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

        switch (action != null ? action : "") {
            case ACTION_START_SCREENING:
                currentCallerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER);
                startScreening(currentCallerNumber);
                break;

            case ACTION_STOP_SCREENING:
                stopScreening("external stop command");
                break;

            case ACTION_USER_TAKE_CALL:
                // User tapped "Take Call" in the notification — hand the call back
                stopScreening("user took the call");
                break;

            default:
                Log.w(TAG, "Unknown action received: " + action);
                stopSelf();
                break;
        }

        // START_NOT_STICKY: if killed by system, do not restart without an explicit intent
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This service is not bound by any clients
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAnswerTimer();
        if (greetingEngine != null) {
            greetingEngine.shutdown();
        }
        releaseWakeLock();
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

        // Step 1: Promote to foreground immediately to avoid ANR
        Notification notification = NotificationHelper.buildScreeningNotification(this, callerNumber);
        startForeground(NOTIFICATION_ID, notification);

        // Step 2: Acquire wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(DEFAULT_RING_DELAY_MS + 60_000L); // max hold: delay + 1 min buffer
        }

        // Step 3: Schedule call answer after ring delay
        PreferencesManager prefs = new PreferencesManager(this);
        int delayMs = prefs.getRingDelayMs(DEFAULT_RING_DELAY_MS);
        scheduleAutoAnswer(delayMs);
    }

    /**
     * Schedules the Runnable that will trigger the call answer after a delay.
     * The delay represents the number of rings before the assistant picks up.
     *
     * @param delayMs Delay in milliseconds before auto-answering.
     */
    private void scheduleAutoAnswer(int delayMs) {
        answerCallRunnable = () -> {
            Log.i(TAG, "Ring delay elapsed — answering call and playing greeting.");
            // The InCallService (Mode A) or ANSWER_PHONE_CALLS permission (Mode B)
            // is used to answer the call at this point.
            // The GreetingEngine handles TTS playback post-answer.
            greetingEngine.playGreeting(currentCallerNumber, () -> {
                // Greeting finished callback — post summary notification
                Log.i(TAG, "Greeting playback complete.");
                NotificationHelper.postCallSummaryNotification(
                        ScreeningForegroundService.this,
                        currentCallerNumber,
                        "Caller greeted. Awaiting further input."
                );
                stopScreening("greeting complete");
            });
        };

        Log.d(TAG, "Auto-answer scheduled in " + (delayMs / 1000) + "s");
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
     * @param reason A human-readable string explaining why screening stopped (for logs).
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

        // Remove the foreground notification and stop the service
        stopForeground(true);
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
