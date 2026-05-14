package com.callguard.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * CallGuardDatabase — Room database singleton for Phase 2 persistence.
 *
 * Stores:
 * - {@link CallLog}: Metadata for every screened call.
 * - {@link Transcript}: STT output for each call session.
 *
 * Access via {@link #getInstance(Context)} — thread-safe singleton.
 */
@Database(
    entities = { CallLog.class, Transcript.class },
    version = 1,
    exportSchema = true
)
public abstract class CallGuardDatabase extends RoomDatabase {

    private static final String DB_NAME = "callguard.db";
    private static volatile CallGuardDatabase INSTANCE;

    public abstract CallLogDao callLogDao();

    /**
     * Returns the singleton database instance, creating it if necessary.
     * Thread-safe via double-checked locking.
     *
     * @param context Application context.
     * @return The shared {@link CallGuardDatabase} instance.
     */
    public static CallGuardDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CallGuardDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            CallGuardDatabase.class,
                            DB_NAME
                    )
                    .fallbackToDestructiveMigration() // Phase 2: acceptable for MVP
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
