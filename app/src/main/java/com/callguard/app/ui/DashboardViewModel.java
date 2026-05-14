package com.callguard.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.callguard.app.data.CallGuardDatabase;
import com.callguard.app.data.CallLog;
import com.callguard.app.data.CallLogDao;
import com.callguard.app.data.CallLogWithTranscript;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DashboardViewModel — Phase 2 ViewModel for the main dashboard.
 *
 * Owns all LiveData observed by {@link MainActivity}.
 * Performs DB writes on a background thread via a single-threaded executor.
 */
public class DashboardViewModel extends AndroidViewModel {

    private final CallLogDao dao;
    private final ExecutorService executor;

    public final LiveData<List<CallLogWithTranscript>> recentCalls;
    public final LiveData<Integer> totalCalls;
    public final LiveData<Integer> spamCalls;
    public final LiveData<Integer> totalScreeningSeconds;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        dao = CallGuardDatabase.getInstance(application).callLogDao();
        executor = Executors.newSingleThreadExecutor();

        recentCalls          = dao.getRecentCallLogsWithTranscripts(50);
        totalCalls           = dao.getTotalCallCount();
        spamCalls            = dao.getSpamCallCount();
        totalScreeningSeconds = dao.getTotalScreeningSeconds();
    }

    /**
     * Inserts a new call log entry on a background thread.
     *
     * @param callLog The fully populated {@link CallLog} to persist.
     */
    public void insertCallLog(CallLog callLog) {
        executor.execute(() -> dao.insertCallLog(callLog));
    }

    /**
     * Updates an existing call log (e.g. to set duration/outcome after the call ends).
     *
     * @param callLog The updated {@link CallLog}.
     */
    public void updateCallLog(CallLog callLog) {
        executor.execute(() -> dao.updateCallLog(callLog));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
