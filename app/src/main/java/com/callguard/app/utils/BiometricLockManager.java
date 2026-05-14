package com.callguard.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * BiometricLockManager — Phase 3 privacy hardening: biometric dashboard lock.
 *
 * Wraps the AndroidX {@link BiometricPrompt} API to require fingerprint,
 * face, or device PIN authentication before the user can access the
 * CallGuard dashboard (call logs and transcripts).
 *
 * Supports:
 * - Fingerprint (API 23+)
 * - Face unlock and device credential fallback (API 28+)
 * - Device PIN/pattern/password fallback when biometrics unavailable
 *
 * Usage:
 * <pre>
 *   BiometricLockManager.authenticate(activity, new BiometricLockManager.AuthCallback() {
 *       {@literal @}Override public void onSuccess() { // show content }
 *       {@literal @}Override public void onFailed(String reason) { // lock content }
 *   });
 * </pre>
 */
public class BiometricLockManager {

    private static final String TAG = "CG_Biometric";

    private BiometricLockManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether biometric or device credential authentication is available.
     *
     * @param context Application or Activity context.
     * @return {@code true} if the device can authenticate the user.
     */
    public static boolean isAuthAvailable(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        int result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Launches the biometric authentication prompt.
     *
     * Must be called from a {@link FragmentActivity} context.
     *
     * @param activity The host activity (needed for the fragment back-stack).
     * @param callback Receives {@link AuthCallback#onSuccess()} or
     *                 {@link AuthCallback#onFailed(String)}.
     */
    public static void authenticate(@NonNull FragmentActivity activity,
                                    @NonNull AuthCallback callback) {
        if (!isAuthAvailable(activity)) {
            // No biometrics or PIN set — skip lock, grant access
            Log.w(TAG, "No auth method available — granting access without auth.");
            callback.onSuccess();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        Log.i(TAG, "Biometric auth succeeded.");
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Log.w(TAG, "Biometric auth failed — bad biometric.");
                        // Do NOT call onFailed here — the prompt shows its own
                        // error feedback and lets the user retry.
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        Log.e(TAG, "Biometric auth error " + errorCode + ": " + errString);
                        callback.onFailed(errString.toString());
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("CallGuard Access")
                .setSubtitle("Authenticate to view your call history and transcripts")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build();

        prompt.authenticate(promptInfo);
        Log.d(TAG, "Biometric prompt displayed.");
    }

    // =========================================================================
    // Callback Interface
    // =========================================================================

    /**
     * Callback for biometric authentication results.
     */
    public interface AuthCallback {
        /** Called when the user successfully authenticated. */
        void onSuccess();

        /** Called when authentication was cancelled or permanently failed.
         *  @param reason Human-readable reason string from the system. */
        void onFailed(String reason);
    }
}
