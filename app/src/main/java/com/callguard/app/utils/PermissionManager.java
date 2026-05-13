package com.callguard.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * PermissionManager — Utility class for handling runtime permission checks and requests.
 *
 * Android requires runtime permission requests for dangerous permissions on API 23+.
 * This class centralises all permission logic so activities and services only need to
 * call a single method rather than duplicating boilerplate across the app.
 *
 * Phase 1 required permissions:
 * - {@code READ_PHONE_STATE}   — Detect incoming calls via BroadcastReceiver (Mode B).
 * - {@code ANSWER_PHONE_CALLS} — Programmatically answer calls (API 26+).
 * - {@code POST_NOTIFICATIONS} — Show screening notifications (API 33+).
 * - {@code SYSTEM_ALERT_WINDOW} — Overlay UI during calls (requested via Settings).
 */
public class PermissionManager {

    /** Request code used when calling requestPermissions() from MainActivity. */
    public static final int REQUEST_CODE_PHASE1_PERMISSIONS = 1001;

    // Permissions required for Phase 1 core functionality
    public static final String[] PHASE1_PERMISSIONS;

    static {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(Manifest.permission.ANSWER_PHONE_CALLS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        PHASE1_PERMISSIONS = perms.toArray(new String[0]);
    }

    // Private constructor — utility class
    private PermissionManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether all Phase 1 permissions are currently granted.
     *
     * @param context Any valid context.
     * @return {@code true} if all required permissions are granted.
     */
    public static boolean hasAllPhase1Permissions(Context context) {
        for (String permission : PHASE1_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a list of Phase 1 permissions that have not yet been granted.
     *
     * @param context Any valid context.
     * @return A list of missing permission strings (empty if all are granted).
     */
    public static List<String> getMissingPhase1Permissions(Context context) {
        List<String> missing = new ArrayList<>();
        for (String permission : PHASE1_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing;
    }

    /**
     * Requests all missing Phase 1 permissions from the given Activity.
     * The result is delivered to the Activity's {@code onRequestPermissionsResult()}.
     *
     * @param activity The hosting Activity.
     */
    public static void requestPhase1Permissions(Activity activity) {
        List<String> missing = getMissingPhase1Permissions(activity);
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    missing.toArray(new String[0]),
                    REQUEST_CODE_PHASE1_PERMISSIONS
            );
        }
    }

    /**
     * Checks whether a specific permission is granted.
     *
     * @param context    Any valid context.
     * @param permission The permission string (e.g. {@link Manifest.permission#READ_PHONE_STATE}).
     * @return {@code true} if the permission is granted.
     */
    public static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
