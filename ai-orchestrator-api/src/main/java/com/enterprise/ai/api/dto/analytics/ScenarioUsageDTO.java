package com.enterprise.ai.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioUsageDTO {
    private List<String> labels;
    private List<Integer> data;
    private int total;

    public ScenarioUsageDTO(List<String> labels, List<Integer> data) {
        this.labels = labels;
        this.data = data;
        this.total = data.stream().mapToInt(Integer::intValue).sum();
    }
}
