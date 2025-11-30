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
public class RequestsOverTimeDTO {
    private List<String> labels;
    private List<Integer> data;
    private String period;
}

