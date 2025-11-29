package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample File Status executor.
 * This is a placeholder implementation - in production, it would query the database.
 */
public class FileStatusExecutor implements ScenarioExecutor {

    public static final String SCENARIO_CODE = "FILE_STATUS";

    @Override
    public String getScenarioCode() {
        return SCENARIO_CODE;
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request) {
        String fileName = (String) request.getParams().get("fileName");

        // In production, this would query the database
        // For now, return sample data
        Map<String, Object> data = new HashMap<>();
        data.put("fileName", fileName);
        data.put("status", "PROCESSED");
        data.put("totalRecords", 1000);
        data.put("successRecords", 985);
        data.put("failedRecords", 15);
        data.put("processedAt", Instant.now().toString());

        return ScenarioResult.builder()
                .scenario(SCENARIO_CODE)
                .data(data)
                .success(true)
                .build();
    }
}
