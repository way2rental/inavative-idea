package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Sample Account Summary executor.
 * This is a placeholder implementation - in production, it would query the database.
 */
public class AccountSummaryExecutor implements ScenarioExecutor {

    public static final String SCENARIO_CODE = "ACCOUNT_SUMMARY";

    @Override
    public String getScenarioCode() {
        return SCENARIO_CODE;
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request) {
        String accountId = (String) request.getParams().get("accountId");

        // In production, this would query the database
        // For now, return sample data
        Map<String, Object> data = new HashMap<>();
        data.put("accountId", accountId);
        data.put("maskedAccountNo", "XXXX-XXXX-1234");
        data.put("balance", 150000.00);
        data.put("currency", "INR");
        data.put("accountType", "SAVINGS");
        data.put("status", "ACTIVE");

        return ScenarioResult.builder()
                .scenario(SCENARIO_CODE)
                .data(data)
                .success(true)
                .build();
    }
}
