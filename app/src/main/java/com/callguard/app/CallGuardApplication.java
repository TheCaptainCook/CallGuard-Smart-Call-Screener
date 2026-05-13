package com.callguard.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.callguard.app.utils.NotificationHelper;

/**
 * CallGuardApplication — Custom Application class.
 *
 * Responsible for:
 * - Creating all notification channels on startup (required for Android 8+).
 * - Holding any future app-wide singletons (repositories, shared prefs, etc.).
 *
 * Phase 1 scope: Notification channel setup only.
 */
public class CallGuardApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    /**
     * Creates all notification channels used by the app.
     * This must be called before any notification is posted.
     * Safe to call on every startup — Android is idempotent for existing channels.
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // --- Active Screening Channel ---
            // High priority so the notification heads-up appears during a call
            NotificationChannel screeningChannel = new NotificationChannel(
                    NotificationHelper.CHANNEL_SCREENING,
                    "Active Screening",
                    NotificationManager.IMPORTANCE_HIGH
            );
            screeningChannel.setDescription("Shown while CallGuard is actively screening a call.");
            screeningChannel.setShowBadge(false);
            manager.createNotificationChannel(screeningChannel);

            // --- Call Summary Channel ---
            // Default priority for post-call summaries
            NotificationChannel summaryChannel = new NotificationChannel(
                    NotificationHelper.CHANNEL_SUMMARY,
                    "Call Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            summaryChannel.setDescription("Summary notification after a call is screened.");
            manager.createNotificationChannel(summaryChannel);
        }
    }
}
