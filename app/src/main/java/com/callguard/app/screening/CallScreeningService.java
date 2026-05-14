package com.callguard.app.screening;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import com.callguard.app.utils.PreferencesManager;

/**
 * CallScreeningService — Android InCallService integration (Mode A).
 *
 * This service is the preferred integration path for Android 10+ (API 29+).
 * The system binds to it when CallGuard is set as the default Caller ID &
 * Spam app or default dialer. Unlike the {@link CallStateReceiver} (Mode B),
 * the InCallService gives us direct programmatic control over the Call object,
 * allowing us to answer, hold, and disconnect calls without additional tricks.
 *
 * Phase 1 responsibilities:
 * - Receive call objects from the Android Telecom framework.
 * - Trigger the screening workflow via {@link ScreeningForegroundService}.
 * - Answer the call automatically after the configured ring delay.
 *
 * Call lifecycle:
 *   onCallAdded() → RINGING state detected
 *   onCallStateChanged() → monitor state transitions
 *   onCallRemoved() → cleanup
 */
public class CallScreeningService extends InCallService {

    private static final String TAG = "CG_CallScreeningService";

    /**
     * Called by the Telecom framework when a new call is added to our session.
     * This is the primary entry point for incoming call handling in Mode A.
     *
     * @param call The new {@link Call} object managed by the Telecom framework.
     */
    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        if (call == null) {
            Log.w(TAG, "onCallAdded — received null call object, ignoring.");
            return;
        }

        Log.d(TAG, "onCallAdded — state: " + call.getState());

        // Register our callback to track state transitions for this call
        call.registerCallback(new CallCallback(this));

        // If the call is already ringing, trigger screening immediately
        if (call.getState() == Call.STATE_RINGING) {
            handleIncomingCall(call);
        }
    }

    /**
     * Called when a call is removed from our session (ended or transferred).
     *
     * @param call The {@link Call} that was removed.
     */
    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d(TAG, "onCallRemoved — stopping screening service.");
        stopScreeningService();
    }

    /**
     * Triggers the screening workflow for a ringing call.
     * Delegates to {@link ScreeningForegroundService} to ensure the screening
     * state is maintained even if the user switches away from the app.
     *
     * @param call The ringing {@link Call}.
     */
    void handleIncomingCall(Call call) {
        try {
            PreferencesManager prefs = new PreferencesManager(this);
            if (!prefs.isScreeningEnabled()) {
                Log.d(TAG, "Screening disabled — allowing call to ring normally.");
                return;
            }

            String callerNumber = extractCallerNumber(call);
            Log.i(TAG, "Mode A — incoming call from: " + callerNumber + ". Starting screening service.");

            Intent serviceIntent = new Intent(this, ScreeningForegroundService.class);
            serviceIntent.setAction(ScreeningForegroundService.ACTION_START_SCREENING);
            serviceIntent.putExtra(ScreeningForegroundService.EXTRA_CALLER_NUMBER, callerNumber);
            startForegroundService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting screening service: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a stop action to the screening foreground service.
     */
    void stopScreeningService() {
        try {
            Intent serviceIntent = new Intent(this, ScreeningForegroundService.class);
            serviceIntent.setAction(ScreeningForegroundService.ACTION_STOP_SCREENING);
            startService(serviceIntent);
        } catch (Exception e) {
            // Service may already be stopped or app may be in the background
            Log.w(TAG, "Could not stop screening service: " + e.getMessage());
        }
    }

    /**
     * Answers the call that is currently ringing. Called by
     * {@link ScreeningForegroundService} after the ring delay timer fires.
     *
     * @param call The {@link Call} to answer.
     */
    public void answerCall(Call call) {
        if (call != null && call.getState() == Call.STATE_RINGING) {
            Log.i(TAG, "Answering call for screening...");
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    /**
     * Disconnects (rejects) a call.
     *
     * @param call The {@link Call} to disconnect.
     */
    public void disconnectCall(Call call) {
        if (call != null) {
            Log.i(TAG, "Disconnecting screened call.");
            call.disconnect();
        }
    }

    /**
     * Safely extracts the caller's phone number from the call object.
     *
     * @param call The active {@link Call}.
     * @return The caller's number string, or "unknown" if unavailable.
     */
    private String extractCallerNumber(Call call) {
        try {
            if (call == null) return "unknown";
            Call.Details details = call.getDetails();
            if (details != null && details.getHandle() != null) {
                return details.getHandle().getSchemeSpecificPart();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not extract caller number: " + e.getMessage());
        }
        return "unknown";
    }

    // =========================================================================
    // Inner Class: CallCallback
    // =========================================================================

    /**
     * Monitors state changes for a specific {@link Call} and routes events
     * to the appropriate handler within the service.
     *
     * Note: Does NOT hold a strong reference to the Call object.
     * The Call is delivered fresh via the onStateChanged() parameter.
     */
    private static class CallCallback extends Call.Callback {

        private final CallScreeningService service;

        CallCallback(CallScreeningService service) {
            this.service = service;
        }

        @Override
        public void onStateChanged(Call call, int state) {
            Log.d(TAG, "Call state changed to: " + stateToString(state));

            if (call == null) return;

            switch (state) {
                case Call.STATE_RINGING:
                    service.handleIncomingCall(call);
                    break;
                case Call.STATE_DISCONNECTED:
                case Call.STATE_DISCONNECTING:
                    service.stopScreeningService();
                    // Unregister this callback — call is ending
                    try {
                        call.unregisterCallback(this);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not unregister callback: " + e.getMessage());
                    }
                    break;
                default:
                    break;
            }
        }

        private String stateToString(int state) {
            switch (state) {
                case Call.STATE_RINGING: return "RINGING";
                case Call.STATE_ACTIVE: return "ACTIVE";
                case Call.STATE_HOLDING: return "HOLDING";
                case Call.STATE_DISCONNECTED: return "DISCONNECTED";
                case Call.STATE_CONNECTING: return "CONNECTING";
                case Call.STATE_DISCONNECTING: return "DISCONNECTING";
                default: return "UNKNOWN(" + state + ")";
            }
        }
    }
}
