package com.callguard.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.callguard.app.R;
import com.callguard.app.databinding.ActivityMainBinding;
import com.callguard.app.utils.PermissionManager;
import com.callguard.app.utils.PreferencesManager;

/**
 * MainActivity — The launcher activity and primary UI for Phase 1.
 *
 * Phase 1 UI responsibilities:
 * - Display the app status card (Screening ON/OFF toggle).
 * - Request all necessary runtime permissions on first launch.
 * - Show the ring delay setting and custom greeting field.
 * - Guide the user to grant SYSTEM_ALERT_WINDOW if needed.
 *
 * This activity is deliberately minimal for Phase 1 (MVP). The full
 * analytics dashboard and history views are introduced in Phase 2.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PreferencesManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new PreferencesManager(this);

        setupStatusCard();
        setupSettingsControls();
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the permission warning banner every time the user returns
        updatePermissionWarning();
    }

    // =========================================================================
    // UI Setup
    // =========================================================================

    /**
     * Configures the main status card with the current screening state and
     * wires the toggle switch to persist state changes.
     */
    private void setupStatusCard() {
        boolean isEnabled = prefs.isScreeningEnabled();
        binding.switchScreening.setChecked(isEnabled);
        updateStatusCard(isEnabled);

        binding.switchScreening.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setScreeningEnabled(isChecked);
            updateStatusCard(isChecked);
        });
    }

    /**
     * Updates the status card visual state (icon, color, label) to reflect
     * whether screening is active.
     *
     * @param isActive {@code true} if screening is enabled.
     */
    private void updateStatusCard(boolean isActive) {
        if (isActive) {
            binding.textStatusLabel.setText(R.string.status_active);
            binding.textStatusSubtitle.setText(R.string.status_active_subtitle);
            binding.cardStatus.setCardBackgroundColor(
                    getResources().getColor(R.color.status_active, getTheme()));
        } else {
            binding.textStatusLabel.setText(R.string.status_inactive);
            binding.textStatusSubtitle.setText(R.string.status_inactive_subtitle);
            binding.cardStatus.setCardBackgroundColor(
                    getResources().getColor(R.color.status_inactive, getTheme()));
        }
    }

    /**
     * Wires up the settings controls (greeting text field and ring delay slider).
     */
    private void setupSettingsControls() {
        // Pre-populate greeting field with saved or default value
        String savedGreeting = prefs.getCustomGreeting(
                getString(R.string.default_greeting));
        binding.editGreeting.setText(savedGreeting);

        // Save greeting on focus loss
        binding.editGreeting.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newGreeting = binding.editGreeting.getText().toString().trim();
                if (!newGreeting.isEmpty()) {
                    prefs.setCustomGreeting(newGreeting);
                }
            }
        });

        // Ring delay slider (range: 5s to 60s, step: 5s)
        // Default position maps to 25s (5 rings)
        int savedDelayMs = prefs.getRingDelayMs(PreferencesManager.DEFAULT_RING_DELAY_MS);
        int sliderProgress = (savedDelayMs / 1000 - 5) / 5; // map to 0-11 range
        binding.sliderRingDelay.setProgress(sliderProgress);
        updateRingDelayLabel(savedDelayMs / 1000);

        binding.sliderRingDelay.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        int seconds = 5 + (progress * 5); // 5, 10, 15 ... 60
                        updateRingDelayLabel(seconds);
                        if (fromUser) {
                            prefs.setRingDelayMs(seconds * 1000);
                        }
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
                });
    }

    /**
     * Updates the ring delay label text to reflect the current slider value.
     *
     * @param seconds The delay in seconds.
     */
    private void updateRingDelayLabel(int seconds) {
        binding.textRingDelayValue.setText(getString(R.string.ring_delay_format, seconds));
    }

    // =========================================================================
    // Permission Handling
    // =========================================================================

    /**
     * Checks if all Phase 1 permissions are granted and requests them if not.
     * On first run, this will show the system permission dialog.
     */
    private void checkAndRequestPermissions() {
        if (!PermissionManager.hasAllPhase1Permissions(this)) {
            PermissionManager.requestPhase1Permissions(this);
        }
    }

    /**
     * Shows or hides the permission warning banner based on current grant state.
     * Also checks for SYSTEM_ALERT_WINDOW which requires a Settings page visit.
     */
    private void updatePermissionWarning() {
        boolean missingRegular = !PermissionManager.hasAllPhase1Permissions(this);
        boolean missingOverlay  = !Settings.canDrawOverlays(this);

        if (missingRegular || missingOverlay) {
            binding.cardPermissionWarning.setVisibility(View.VISIBLE);
            binding.buttonGrantPermissions.setOnClickListener(v -> {
                if (missingRegular) {
                    PermissionManager.requestPhase1Permissions(this);
                } else {
                    openOverlaySettings();
                }
            });
        } else {
            binding.cardPermissionWarning.setVisibility(View.GONE);
        }
    }

    /**
     * Opens the system "Display over other apps" settings screen so the user
     * can grant SYSTEM_ALERT_WINDOW permission, which cannot be requested via
     * the standard runtime permission flow.
     */
    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_CODE_PHASE1_PERMISSIONS) {
            updatePermissionWarning();
        }
    }
}
