package com.callguard.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import android.graphics.Color;

import com.callguard.app.R;
import com.callguard.app.databinding.ActivityMainBinding;

import java.util.ArrayList;
import com.callguard.app.utils.BiometricLockManager;
import com.callguard.app.utils.PermissionManager;
import com.callguard.app.utils.PreferencesManager;
import com.callguard.app.utils.PrivacyManager;

/**
 * MainActivity — The launcher activity and dashboard for CallGuard.
 *
 * Phase 1: Status toggle, greeting editor, ring delay slider, permissions.
 * Phase 2: Stats cards (Total, Spam, Time Saved), screening history list.
 * Phase 3: Biometric lock for dashboard, local-only privacy setting.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PreferencesManager prefs;
    private PrivacyManager privacyManager;
    private DashboardViewModel viewModel;
    private CallHistoryAdapter historyAdapter;
    /** Phase 3: tracks whether the dashboard was unlocked this session. */
    private boolean isDashboardUnlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new PreferencesManager(this);
        privacyManager = new PrivacyManager(this);

        setupStatusCard();
        setupSettingsControls();
        setupDashboard();
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionWarning();
        // Phase 3: enforce biometric lock on every resume if enabled
        if (privacyManager.isBiometricLockEnabled() && !isDashboardUnlocked) {
            lockDashboard();
            BiometricLockManager.authenticate(this, new BiometricLockManager.AuthCallback() {
                @Override public void onSuccess() {
                    isDashboardUnlocked = true;
                    unlockDashboard();
                }
                @Override public void onFailed(String reason) {
                    // Dashboard stays locked — user taps to retry
                }
            });
        }
    }

    /** Phase 3: Hides dashboard history and stats behind a lock overlay. */
    private void lockDashboard() {
        binding.recyclerHistory.setVisibility(View.GONE);
        binding.textHistoryEmpty.setVisibility(View.GONE);
    }

    /** Phase 3: Reveals dashboard content after successful auth. */
    private void unlockDashboard() {
        // Re-trigger the LiveData observer to restore visibility
        if (viewModel != null && viewModel.recentCalls.getValue() != null
                && !viewModel.recentCalls.getValue().isEmpty()) {
            binding.recyclerHistory.setVisibility(View.VISIBLE);
            binding.textHistoryEmpty.setVisibility(View.GONE);
        } else {
            binding.textHistoryEmpty.setVisibility(View.VISIBLE);
        }
    }

    // =========================================================================
    // Phase 1: Status Card
    // =========================================================================

    private void setupStatusCard() {
        boolean isEnabled = prefs.isScreeningEnabled();
        binding.switchScreening.setChecked(isEnabled);
        updateStatusCard(isEnabled);

        binding.switchScreening.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setScreeningEnabled(isChecked);
            updateStatusCard(isChecked);
        });
    }

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

    // =========================================================================
    // Phase 1: Settings Controls
    // =========================================================================

    private void setupSettingsControls() {
        String savedGreeting = prefs.getCustomGreeting(getString(R.string.default_greeting));
        binding.editGreeting.setText(savedGreeting);

        binding.editGreeting.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newGreeting = binding.editGreeting.getText().toString().trim();
                if (!newGreeting.isEmpty()) {
                    prefs.setCustomGreeting(newGreeting);
                }
            }
        });

        int savedDelayMs = prefs.getRingDelayMs(PreferencesManager.DEFAULT_RING_DELAY_MS);
        int sliderProgress = (savedDelayMs / 1000 - 5) / 5;
        binding.sliderRingDelay.setProgress(sliderProgress);
        updateRingDelayLabel(savedDelayMs / 1000);

        binding.sliderRingDelay.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        int seconds = 5 + (progress * 5);
                        updateRingDelayLabel(seconds);
                        if (fromUser) {
                            prefs.setRingDelayMs(seconds * 1000);
                        }
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
                });
    }

    private void updateRingDelayLabel(int seconds) {
        binding.textRingDelayValue.setText(getString(R.string.ring_delay_format, seconds));
    }

    // =========================================================================
    // Phase 2: Dashboard — Stats + History
    // =========================================================================

    private void setupDashboard() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // --- Stats cards ---
        viewModel.totalCalls.observe(this, count -> {
            binding.textStatTotalValue.setText(String.valueOf(count != null ? count : 0));
            updateChart();
        });

        viewModel.spamCalls.observe(this, count -> {
            binding.textStatSpamValue.setText(String.valueOf(count != null ? count : 0));
            updateChart();
        });

        viewModel.totalScreeningSeconds.observe(this, seconds -> {
            int mins = (seconds != null ? seconds : 0) / 60;
            binding.textStatTimeValue.setText(getString(R.string.stats_minutes_format, mins));
        });

        // --- History RecyclerView ---
        historyAdapter = new CallHistoryAdapter();
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(historyAdapter);

        viewModel.recentCalls.observe(this, callLogs -> {
            if (callLogs != null && !callLogs.isEmpty()) {
                historyAdapter.submitList(callLogs);
                binding.textHistoryEmpty.setVisibility(View.GONE);
                binding.recyclerHistory.setVisibility(View.VISIBLE);
            } else {
                binding.textHistoryEmpty.setVisibility(View.VISIBLE);
                binding.recyclerHistory.setVisibility(View.GONE);
            }
        });
    }

    private void updateChart() {
        Integer total = viewModel.totalCalls.getValue();
        Integer spam = viewModel.spamCalls.getValue();
        
        int t = total != null ? total : 0;
        int s = spam != null ? spam : 0;
        int safe = t - s;

        if (t == 0) return;

        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(safe, "Safe Calls"));
        entries.add(new PieEntry(s, "Spam Blocked"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(Color.parseColor("#00D4AA"), Color.parseColor("#FF6B6B"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        binding.chartAnalytics.setData(data);
        binding.chartAnalytics.getDescription().setEnabled(false);
        binding.chartAnalytics.getLegend().setTextColor(Color.WHITE);
        binding.chartAnalytics.setHoleColor(Color.TRANSPARENT);
        binding.chartAnalytics.invalidate(); // refresh
    }

    // =========================================================================
    // Permission Handling
    // =========================================================================

    private void checkAndRequestPermissions() {
        if (!PermissionManager.hasAllPhase1Permissions(this)) {
            PermissionManager.requestPhase1Permissions(this);
        }
    }

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
