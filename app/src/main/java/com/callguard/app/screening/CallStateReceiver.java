package com.callguard.app.screening;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.callguard.app.utils.PreferencesManager;

/**
 * CallStateReceiver — BroadcastReceiver for PHONE_STATE changes.
 *
 * This receiver acts as the entry-point for incoming call detection on
 * devices running Android 9 (API 28) and below, or on devices where
 * CallGuard is not yet set as the default dialer (Mode B / Standalone).
 *
 * On Android 10+ with InCallService integration (Mode A), the system
 * will directly bind to {@link CallScreeningService} instead. Both
 * paths ultimately trigger the same screening workflow.
 *
 * Phase 1 responsibilities:
 * - Detect RINGING state and launch {@link ScreeningForegroundService}.
 * - Detect IDLE state to signal screening end and clean up.
 */
public class CallStateReceiver extends BroadcastReceiver {

    private static final String TAG = "CG_CallStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (state == null) {
            return;
        }

        Log.d(TAG, "Phone state changed: " + state + " | Number: " + (incomingNumber != null ? incomingNumber : "unknown"));

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            handleRinging(context, incomingNumber);

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            // Call was answered by the user before screening completed — stop service
            Log.d(TAG, "Call answered by user (OFFHOOK) — cancelling screening.");
            stopScreeningService(context);

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            // Call ended — stop service and clean up
            Log.d(TAG, "Phone is IDLE — call ended, stopping screening service.");
            stopScreeningService(context);

        } else {
            Log.w(TAG, "Unknown phone state: " + state);
        }
    }

    /**
     * Handles the RINGING state by starting the foreground screening service.
     *
     * @param context        The application context.
     * @param incomingNumber The caller's number (may be null for private/unknown).
     */
    private void handleRinging(Context context, String incomingNumber) {
        // Check if screening is enabled in preferences before doing anything
        PreferencesManager prefs = new PreferencesManager(context);
        if (!prefs.isScreeningEnabled()) {
            Log.d(TAG, "Screening is disabled in settings — ignoring incoming call.");
            return;
        }

        Log.i(TAG, "Incoming call detected — starting screening service for: "
                + (incomingNumber != null ? incomingNumber : "[private number]"));

        Intent serviceIntent = new Intent(context, ScreeningForegroundService.class);
        serviceIntent.setAction(ScreeningForegroundService.ACTION_START_SCREENING);
        serviceIntent.putExtra(ScreeningForegroundService.EXTRA_CALLER_NUMBER, incomingNumber);

        // Must use startForegroundService on API 26+ to ensure the service
        // can post its foreground notification within 5 seconds.
        context.startForegroundService(serviceIntent);
    }

    /**
     * Sends a stop command to the foreground screening service.
     *
     * @param context The application context.
     */
    private void stopScreeningService(Context context) {
        Intent serviceIntent = new Intent(context, ScreeningForegroundService.class);
        serviceIntent.setAction(ScreeningForegroundService.ACTION_STOP_SCREENING);
        context.startService(serviceIntent);
    }
}
