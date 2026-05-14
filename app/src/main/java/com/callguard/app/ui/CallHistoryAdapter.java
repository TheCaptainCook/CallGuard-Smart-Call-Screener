package com.callguard.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.callguard.app.R;
import com.callguard.app.data.CallLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CallHistoryAdapter — Phase 2 RecyclerView adapter for the screening history list.
 *
 * Uses {@link ListAdapter} with {@link DiffUtil} for efficient, animated updates
 * when the Room LiveData emits a new list.
 */
public class CallHistoryAdapter extends ListAdapter<CallLog, CallHistoryAdapter.ViewHolder> {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    private OnItemClickListener clickListener;

    public CallHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallLog log = getItem(position);
        holder.bind(log, clickListener);
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textNumber;
        private final TextView textTime;
        private final TextView textOutcome;
        private final TextView textSpamBadge;

        ViewHolder(View itemView) {
            super(itemView);
            textNumber   = itemView.findViewById(R.id.textCallerNumber);
            textTime     = itemView.findViewById(R.id.textCallTime);
            textOutcome  = itemView.findViewById(R.id.textOutcome);
            textSpamBadge = itemView.findViewById(R.id.textSpamBadge);
        }

        void bind(CallLog log, OnItemClickListener listener) {
            textNumber.setText(
                (log.callerNumber != null && !log.callerNumber.isEmpty())
                    ? log.callerNumber : "Unknown Number"
            );
            textTime.setText(DATE_FORMAT.format(new Date(log.timestampMs)));
            textOutcome.setText(formatOutcome(log.outcome));

            if (log.isSpam) {
                textSpamBadge.setVisibility(View.VISIBLE);
            } else {
                textSpamBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(log);
            });
        }

        private String formatOutcome(String outcome) {
            if (outcome == null) return "Unknown";
            switch (outcome) {
                case "screened":      return "Screened";
                case "user_answered": return "Answered by user";
                case "blocked":       return "Blocked";
                case "missed":        return "Missed";
                default:              return outcome;
            }
        }
    }

    // =========================================================================
    // DiffUtil
    // =========================================================================

    private static final DiffUtil.ItemCallback<CallLog> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CallLog>() {
                @Override
                public boolean areItemsTheSame(@NonNull CallLog a, @NonNull CallLog b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull CallLog a, @NonNull CallLog b) {
                    return a.outcome.equals(b.outcome)
                            && a.isSpam == b.isSpam
                            && a.spamScore == b.spamScore;
                }
            };

    // =========================================================================
    // Click Interface
    // =========================================================================

    public interface OnItemClickListener {
        void onItemClick(CallLog callLog);
    }
}
