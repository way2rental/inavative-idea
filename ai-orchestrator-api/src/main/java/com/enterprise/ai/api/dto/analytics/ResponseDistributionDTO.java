package com.enterprise.ai.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDistributionDTO {
    private List<String> labels = new ArrayList<>();
    private List<Long> data = new ArrayList<>();
    private double average;

    public ResponseDistributionDTO(Map<String, Long> distribution, double average) {
        this.labels = new ArrayList<>(distribution.keySet());
        this.data = new ArrayList<>(distribution.values());
        this.average = average;
    }
}

