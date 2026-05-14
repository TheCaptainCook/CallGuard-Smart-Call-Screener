package com.callguard.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import com.callguard.app.R;
import com.callguard.app.databinding.FragmentAnalyticsBinding;

import java.util.ArrayList;

public class AnalyticsFragment extends Fragment {

    private FragmentAnalyticsBinding binding;
    private DashboardViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        setupStatsCards();
    }

    private void setupStatsCards() {
        viewModel.totalCalls.observe(getViewLifecycleOwner(), count -> {
            binding.textStatTotalValue.setText(String.valueOf(count != null ? count : 0));
            updateChart();
        });

        viewModel.spamCalls.observe(getViewLifecycleOwner(), count -> {
            binding.textStatSpamValue.setText(String.valueOf(count != null ? count : 0));
            updateChart();
        });

        viewModel.totalScreeningSeconds.observe(getViewLifecycleOwner(), seconds -> {
            int mins = (seconds != null ? seconds : 0) / 60;
            binding.textStatTimeValue.setText(getString(R.string.stats_minutes_format, mins));
        });
    }

    private void updateChart() {
        Integer total = viewModel.totalCalls.getValue();
        Integer spam = viewModel.spamCalls.getValue();

        int t = total != null ? total : 0;
        int s = spam != null ? spam : 0;
        int safe = t - s;

        if (t == 0) return;

        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(safe, "Safe Calls"));
        entries.add(new PieEntry(s, "Spam Blocked"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(Color.parseColor("#00D4AA"), Color.parseColor("#FF6B6B"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        binding.chartAnalytics.setData(data);
        binding.chartAnalytics.getDescription().setEnabled(false);
        binding.chartAnalytics.getLegend().setTextColor(Color.WHITE);
        binding.chartAnalytics.setHoleColor(Color.TRANSPARENT);
        binding.chartAnalytics.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
