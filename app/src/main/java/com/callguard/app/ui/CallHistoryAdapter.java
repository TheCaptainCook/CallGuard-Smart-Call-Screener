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
import com.callguard.app.data.CallLogWithTranscript;

import java.util.Date;


/**
 * CallHistoryAdapter — Phase 2 RecyclerView adapter for the screening history list.
 *
 * Uses {@link ListAdapter} with {@link DiffUtil} for efficient, animated updates
 * when the Room LiveData emits a new list.
 */
public class CallHistoryAdapter extends ListAdapter<CallLogWithTranscript, CallHistoryAdapter.ViewHolder> {

    private java.text.DateFormat getDateFormat() {
        return java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, 
            java.text.DateFormat.SHORT
        );
    }

    public CallHistoryAdapter() {
        super(DIFF_CALLBACK);
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
        CallLogWithTranscript item = getItem(position);
        holder.bind(item, getDateFormat());
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textNumber;
        private final TextView textTime;
        private final TextView textOutcome;
        private final TextView textSpamBadge;
        private final View layoutTranscript;
        private final TextView textTranscript;
        private final TextView iconExpand;
        private boolean isExpanded = false;

        ViewHolder(View itemView) {
            super(itemView);
            textNumber   = itemView.findViewById(R.id.textCallerNumber);
            textTime     = itemView.findViewById(R.id.textCallTime);
            textOutcome  = itemView.findViewById(R.id.textOutcome);
            textSpamBadge = itemView.findViewById(R.id.textSpamBadge);
            layoutTranscript = itemView.findViewById(R.id.layoutTranscript);
            textTranscript = itemView.findViewById(R.id.textTranscript);
            iconExpand = itemView.findViewById(R.id.iconExpand);
        }

        @android.annotation.SuppressLint("SetTextI18n")
        void bind(CallLogWithTranscript item, java.text.DateFormat dateFormat) {
            CallLog log = item.callLog;
            textNumber.setText(
                (log.callerNumber != null && !log.callerNumber.isEmpty())
                    ? log.callerNumber : "Unknown Number"
            );
            textTime.setText(dateFormat.format(new Date(log.timestampMs)));
            textOutcome.setText(formatOutcome(log.outcome));

            if (log.isSpam) {
                textSpamBadge.setVisibility(View.VISIBLE);
            } else {
                textSpamBadge.setVisibility(View.GONE);
            }
            
            if (item.transcript != null && item.transcript.text != null && !item.transcript.text.isEmpty()) {
                textTranscript.setText(item.transcript.text);
            } else {
                textTranscript.setText("No transcript available.");
            }
            
            updateExpansion();

            itemView.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                updateExpansion();
            });
        }
        
        @android.annotation.SuppressLint("SetTextI18n")
        private void updateExpansion() {
            if (isExpanded) {
                layoutTranscript.setVisibility(View.VISIBLE);
                iconExpand.setText("▲");
            } else {
                layoutTranscript.setVisibility(View.GONE);
                iconExpand.setText("▼");
            }
        }

        private String formatOutcome(String outcome) {
            if (outcome == null) return "Unknown";
            return switch (outcome) {
                case "screened" -> "Screened";
                case "user_answered" -> "Answered by user";
                case "blocked" -> "Blocked";
                case "missed" -> "Missed";
                default -> outcome;
            };
        }
    }

    // =========================================================================
    // DiffUtil
    // =========================================================================

    private static final DiffUtil.ItemCallback<CallLogWithTranscript> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull CallLogWithTranscript a, @NonNull CallLogWithTranscript b) {
                    return a.callLog.id == b.callLog.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull CallLogWithTranscript a, @NonNull CallLogWithTranscript b) {
                    return java.util.Objects.equals(a.callLog.outcome, b.callLog.outcome)
                            && a.callLog.isSpam == b.callLog.isSpam
                            && a.callLog.spamScore == b.callLog.spamScore
                            && ((a.transcript == null && b.transcript == null) || 
                                (a.transcript != null && b.transcript != null && java.util.Objects.equals(a.transcript.text, b.transcript.text)));
                }
            };
}
