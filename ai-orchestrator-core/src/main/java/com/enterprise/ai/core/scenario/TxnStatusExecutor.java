package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample Transaction Status executor.
 * This is a placeholder implementation - in production, it would query the database.
 */
public class TxnStatusExecutor implements ScenarioExecutor {

    public static final String SCENARIO_CODE = "TXN_STATUS";

    @Override
    public String getScenarioCode() {
        return SCENARIO_CODE;
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request) {
        String txnId = (String) request.getParams().get("txnId");

        // In production, this would query the database
        // For now, return sample data
        Map<String, Object> data = new HashMap<>();
        data.put("txnId", txnId);
        data.put("status", "SUCCESS");
        data.put("amount", 5000);
        data.put("currency", "INR");
        data.put("channel", "UPI");
        data.put("timestamp", Instant.now().toString());

        return ScenarioResult.builder()
                .scenario(SCENARIO_CODE)
                .data(data)
                .success(true)
                .build();
    }
}
