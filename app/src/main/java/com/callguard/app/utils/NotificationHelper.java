package com.callguard.app.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.callguard.app.R;
import com.callguard.app.screening.ScreeningForegroundService;
import com.callguard.app.ui.MainActivity;

/**
 * NotificationHelper — Centralized factory for all CallGuard notifications.
 *
 * Provides static methods to build and post notifications, keeping all
 * notification logic in one place and decoupled from service/activity code.
 *
 * Notification Channels (created in {@link com.callguard.app.CallGuardApplication}):
 * - {@link #CHANNEL_SCREENING}: High-priority, shown during active screening.
 * - {@link #CHANNEL_SUMMARY}: Default-priority, shown after screening ends.
 */
public class NotificationHelper {

    /** Channel ID for the active-screening foreground notification. */
    public static final String CHANNEL_SCREENING = "channel_active_screening";

    /** Channel ID for post-call summary notifications. */
    public static final String CHANNEL_SUMMARY = "channel_call_summary";

    /** Notification ID for post-call summaries (incremented per call). */
    private static int summaryNotificationId = 200;

    // Private constructor — utility class, not instantiable
    private NotificationHelper() {}

    // =========================================================================
    // Screening Notification (Foreground)
    // =========================================================================

    /**
     * Builds the foreground notification displayed during active call screening.
     * This notification is required for the foreground service and must be posted
     * within 5 seconds of calling {@code startForeground()}.
     *
     * Includes a "Take Call" quick action that stops screening and lets the
     * user answer the call themselves.
     *
     * @param context      The application context.
     * @param callerNumber The caller's number to display (may be null for unknown).
     * @return A fully built {@link Notification} ready for {@code startForeground()}.
     */
    public static Notification buildScreeningNotification(Context context, String callerNumber) {
        // Action: Open the app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action: User takes the call (stops the screening service)
        Intent takeCallIntent = new Intent(context, ScreeningForegroundService.class);
        takeCallIntent.setAction(ScreeningForegroundService.ACTION_USER_TAKE_CALL);
        PendingIntent takeCallPendingIntent = PendingIntent.getService(
                context, 1, takeCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String displayNumber = (callerNumber != null && !callerNumber.isEmpty())
                ? callerNumber : "Unknown number";

        return new NotificationCompat.Builder(context, CHANNEL_SCREENING)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle("📞 Screening call...")
                .setContentText("From: " + displayNumber)
                .setSubText("CallGuard is handling this call")
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)               // Cannot be dismissed by the user
                .setShowWhen(true)
                .setUsesChronometer(true)        // Shows elapsed time during screening
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
                .addAction(
                        R.drawable.ic_call_answer,
                        "Take Call",
                        takeCallPendingIntent
                )
                .build();
    }

    // =========================================================================
    // Call Summary Notification (Post-Screening)
    // =========================================================================

    /**
     * Posts a summary notification after a screening session ends.
     * Provides the user with a quick overview of what happened.
     *
     * @param context      The application context.
     * @param callerNumber The caller's number.
     * @param summary      A brief text summary of the screening outcome.
     */
    public static void postCallSummaryNotification(Context context, String callerNumber, String summary) {
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String displayNumber = (callerNumber != null && !callerNumber.isEmpty())
                ? callerNumber : "Unknown";

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle("Screened call from " + displayNumber)
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)            // Dismissed when tapped
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(summaryNotificationId++, notification);
    }
}
