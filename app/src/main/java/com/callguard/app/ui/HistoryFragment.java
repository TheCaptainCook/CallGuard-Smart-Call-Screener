package com.callguard.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.callguard.app.R;
import com.callguard.app.databinding.FragmentHistoryBinding;
import com.callguard.app.utils.PreferencesManager;

public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private PreferencesManager prefs;
    private DashboardViewModel viewModel;
    private CallHistoryAdapter historyAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = new PreferencesManager(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        setupStatusCard();
        setupHistoryList();
    }

    private void setupStatusCard() {
        boolean isEnabled = prefs.isScreeningEnabled();
        binding.switchScreening.setChecked(isEnabled);
        updateStatusCard(isEnabled);

        binding.switchScreening.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setScreeningEnabled(isChecked);
            updateStatusCard(isChecked);
        });
    }

    private void updateStatusCard(boolean isActive) {
        if (isActive) {
            binding.textStatusLabel.setText(R.string.status_active);
            binding.textStatusSubtitle.setText(R.string.status_active_subtitle);
            binding.cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_active, requireContext().getTheme()));
        } else {
            binding.textStatusLabel.setText(R.string.status_inactive);
            binding.textStatusSubtitle.setText(R.string.status_inactive_subtitle);
            binding.cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_inactive, requireContext().getTheme()));
        }
    }

    private void setupHistoryList() {
        historyAdapter = new CallHistoryAdapter();
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerHistory.setAdapter(historyAdapter);

        viewModel.recentCalls.observe(getViewLifecycleOwner(), callLogs -> {
            if (callLogs != null && !callLogs.isEmpty()) {
                historyAdapter.submitList(callLogs);
                binding.textHistoryEmpty.setVisibility(View.GONE);
                binding.recyclerHistory.setVisibility(View.VISIBLE);
            } else {
                binding.textHistoryEmpty.setVisibility(View.VISIBLE);
                binding.recyclerHistory.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
