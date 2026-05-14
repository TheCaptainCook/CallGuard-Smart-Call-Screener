package com.callguard.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.callguard.app.R;
import com.callguard.app.databinding.FragmentSettingsBinding;
import com.callguard.app.utils.PermissionManager;
import com.callguard.app.utils.PreferencesManager;

import com.callguard.app.utils.PrivacyManager;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private PreferencesManager prefs;
    private PrivacyManager privacyManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = new PreferencesManager(requireContext());
        privacyManager = new PrivacyManager(requireContext());
        setupSettingsControls();
        updatePermissionWarning();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionWarning();
    }

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
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int seconds = 5 + (progress * 5);
                        updateRingDelayLabel(seconds);
                        if (fromUser) {
                            prefs.setRingDelayMs(seconds * 1000);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        // --- Language Selection ---
        String[] languages = { "en-US", "es-ES", "fr-FR", "de-DE", "hi-IN", "it-IT" };
        String[] languageNames = { "English (US)", "Spanish (Spain)", "French (France)", "German", "Hindi", "Italian" };
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                languageNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerLanguage.setAdapter(adapter);

        String currentLang = prefs.getLanguage();
        int selectedIndex = 0;
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(currentLang)) {
                selectedIndex = i;
                break;
            }
        }
        binding.spinnerLanguage.setSelection(selectedIndex);

        binding.spinnerLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.setLanguage(languages[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // --- Privacy & Security ---
        binding.switchLocalOnly.setChecked(privacyManager.isLocalOnlyEnabled());
        binding.switchLocalOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            privacyManager.setLocalOnlyEnabled(isChecked);
        });

        binding.switchBiometric.setChecked(privacyManager.isBiometricLockEnabled());
        binding.switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            privacyManager.setBiometricLockEnabled(isChecked);
        });
    }

    private void updateRingDelayLabel(int seconds) {
        binding.textRingDelayValue.setText(getString(R.string.ring_delay_format, seconds));
    }

    private void updatePermissionWarning() {
        boolean missingRegular = !PermissionManager.hasAllPhase1Permissions(requireContext());
        boolean missingOverlay = !Settings.canDrawOverlays(requireContext());

        if (missingRegular || missingOverlay) {
            binding.cardPermissionWarning.setVisibility(View.VISIBLE);
            binding.buttonGrantPermissions.setOnClickListener(v -> {
                if (missingRegular) {
                    PermissionManager.requestPhase1Permissions(requireActivity());
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
                Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
