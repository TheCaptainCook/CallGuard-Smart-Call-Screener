package com.callguard.app.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.callguard.app.databinding.ActivityIncomingCallBinding;

/**
 * IncomingCallActivity — Full-screen UI displayed over the lock screen
 * while an incoming call is being actively screened.
 *
 * This activity is launched by {@link com.callguard.app.screening.ScreeningForegroundService}
 * when the call is answered by the assistant. It:
 * - Shows the caller's phone number.
 * - Displays a live "Screening..." status indicator.
 * - Provides quick action buttons: Take Call, Block, Hang Up.
 *
 * Note: android:showOnLockScreen and android:turnScreenOn are set in the manifest
 * to ensure this appears even when the device is locked.
 *
 * Phase 2 will add the live transcript display to this screen.
 */
public class IncomingCallActivity extends AppCompatActivity {

    private ActivityIncomingCallBinding binding;

    /** Intent extra key — passed in by the screening service. */
    public static final String EXTRA_CALLER_NUMBER = "extra_caller_number";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIncomingCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String callerNumber = getIntent().getStringExtra(EXTRA_CALLER_NUMBER);
        displayCallerInfo(callerNumber);
        setupActionButtons();
    }

    /**
     * Populates the caller number / name fields.
     *
     * @param callerNumber Raw phone number string from the intent.
     */
    private void displayCallerInfo(String callerNumber) {
        if (callerNumber != null && !callerNumber.isEmpty()) {
            binding.textCallerNumber.setText(callerNumber);
        } else {
            binding.textCallerNumber.setText("Unknown Number");
        }
    }

    /**
     * Wires the quick-action buttons to their respective intents.
     * Full logic (blocking, marking spam) is implemented in Phase 2.
     */
    private void setupActionButtons() {
        // Take Call: stop screening and let the user answer
        binding.buttonTakeCall.setOnClickListener(v -> {
            sendStopScreeningBroadcast();
            finish();
        });

        // Hang Up: terminate the call via the screening service
        binding.buttonHangUp.setOnClickListener(v -> {
            sendStopScreeningBroadcast();
            finish();
        });
    }

    /**
     * Sends a stop-screening action to the foreground service,
     * which will clean up and hang up or release the call.
     */
    private void sendStopScreeningBroadcast() {
        android.content.Intent intent = new android.content.Intent(
                this,
                com.callguard.app.screening.ScreeningForegroundService.class
        );
        intent.setAction(com.callguard.app.screening.ScreeningForegroundService.ACTION_USER_TAKE_CALL);
        startService(intent);
    }
}
