package com.callguard.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.fragment.app.Fragment;

import com.callguard.app.R;
import com.callguard.app.databinding.ActivityMainBinding;
import com.callguard.app.utils.BiometricLockManager;
import com.callguard.app.utils.PermissionManager;
import com.callguard.app.utils.PrivacyManager;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PrivacyManager privacyManager;
    private boolean isDashboardUnlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        privacyManager = new PrivacyManager(this);

        setupBottomNavigation();
        
        if (savedInstanceState == null) {
            loadFragment(new HistoryFragment());
        }

        checkAndRequestPermissions();
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_history) {
                fragment = new HistoryFragment();
            } else if (itemId == R.id.nav_analytics) {
                fragment = new AnalyticsFragment();
            } else if (itemId == R.id.nav_settings) {
                fragment = new SettingsFragment();
            }

            return loadFragment(fragment);
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (privacyManager.isBiometricLockEnabled() && !isDashboardUnlocked) {
            lockDashboard();
            BiometricLockManager.authenticate(this, new BiometricLockManager.AuthCallback() {
                @Override public void onSuccess() {
                    isDashboardUnlocked = true;
                    unlockDashboard();
                }
                @Override public void onFailed(String reason) {
                    // Dashboard stays locked
                }
            });
        }
    }

    private void lockDashboard() {
        binding.fragmentContainer.setVisibility(View.GONE);
        binding.bottomNavigation.setVisibility(View.GONE);
    }

    private void unlockDashboard() {
        binding.fragmentContainer.setVisibility(View.VISIBLE);
        binding.bottomNavigation.setVisibility(View.VISIBLE);
    }

    private void checkAndRequestPermissions() {
        if (!PermissionManager.hasAllPhase1Permissions(this)) {
            PermissionManager.requestPhase1Permissions(this);
        }
    }
}
