package com.enterprise.ai.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuccessRateTrendDTO {
    private List<String> labels = new ArrayList<>();
    private List<Double> data = new ArrayList<>();
    private double overall;

    public SuccessRateTrendDTO(List<Double> weeklySuccessRates) {
        this.data = weeklySuccessRates;
        this.labels = generateWeekLabels(weeklySuccessRates.size());
        this.overall = calculateOverall(weeklySuccessRates);
    }

    private List<String> generateWeekLabels(int size) {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            labels.add("Week " + i);
        }
        return labels;
    }

    private double calculateOverall(List<Double> rates) {
        if (rates.isEmpty()) return 0.0;
        return Math.round(rates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 10.0) / 10.0;
    }
}

