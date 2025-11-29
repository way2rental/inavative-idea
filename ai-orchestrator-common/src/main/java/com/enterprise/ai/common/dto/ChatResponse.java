package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat interactions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String sessionId;
    private String message;
    private boolean followUpRequired;
    private List<String> missingParams;
    private String scenario;
    private ResponseMeta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMeta {
        private List<String> sources;
        private String executionId;
        private Map<String, Object> additionalInfo;
    }
}
