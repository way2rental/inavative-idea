package com.enterprise.ai.api.service;

import com.enterprise.ai.api.dto.analytics.*;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.enterprise.ai.data.repository.AiScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final AiAuditLogRepository auditLogRepository;
    private final AiScenarioRepository scenarioRepository;

    @Cacheable(value = "analytics", key = "'requests-' + #hours + '-' + #interval")
    public RequestsOverTimeDTO getRequestsOverTime(int hours, int interval) {
        log.info("Fetching requests over time: hours={}, interval={}", hours, interval);

        Instant now = Instant.now();
        Instant startTime = now.minus(hours, ChronoUnit.HOURS);

        List<String> labels = new ArrayList<>();
        List<Integer> data = new ArrayList<>();

        // Calculate number of intervals
        int numIntervals = hours / interval;

        for (int i = 0; i < numIntervals; i++) {
            Instant intervalStart = startTime.plus((long) i * interval, ChronoUnit.HOURS);
            Instant intervalEnd = intervalStart.plus(interval, ChronoUnit.HOURS);

            long count = auditLogRepository.countByRequestTimeBetween(intervalStart, intervalEnd);

            // Format label
            ZonedDateTime zdt = intervalStart.atZone(ZoneId.systemDefault());
            String label = zdt.format(DateTimeFormatter.ofPattern("h a"));

            labels.add(label);
            data.add((int) count);
        }

        return RequestsOverTimeDTO.builder()
                .labels(labels)
                .data(data)
                .period("Last " + hours + " hours")
                .build();
    }

    @Cacheable(value = "analytics", key = "'response-distribution'")
    public ResponseDistributionDTO getResponseDistribution() {
        log.info("Fetching response time distribution");

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("< 100ms", auditLogRepository.countByExecutionTimeBetween(0, 100));
        distribution.put("100-200ms", auditLogRepository.countByExecutionTimeBetween(100, 200));
        distribution.put("200-300ms", auditLogRepository.countByExecutionTimeBetween(200, 300));
        distribution.put("300-500ms", auditLogRepository.countByExecutionTimeBetween(300, 500));
        distribution.put("> 500ms", auditLogRepository.countByExecutionTimeGreaterThan(500));

        // Calculate average
        Instant last24Hours = Instant.now().minus(24, ChronoUnit.HOURS);
        Double average = auditLogRepository.getAverageExecutionTime(last24Hours);

        return new ResponseDistributionDTO(distribution, average != null ? average : 0.0);
    }

    @Cacheable(value = "analytics", key = "'success-rate-' + #weeks")
    public SuccessRateTrendDTO getSuccessRateTrend(int weeks) {
        log.info("Fetching success rate trend: weeks={}", weeks);

        Instant now = Instant.now();
        List<Double> weeklySuccessRates = new ArrayList<>();

        for (int i = weeks - 1; i >= 0; i--) {
            Instant weekEnd = now.minus((long) i, ChronoUnit.WEEKS);
            Instant weekStart = weekEnd.minus(7, ChronoUnit.DAYS);

            long total = auditLogRepository.countByRequestTimeBetween(weekStart, weekEnd);
            long successful = auditLogRepository.countSuccessfulByRequestTimeBetween(weekStart, weekEnd);

            double successRate = total > 0 ? (successful * 100.0 / total) : 100.0;
            weeklySuccessRates.add(Math.round(successRate * 10.0) / 10.0); // Round to 1 decimal
        }

        return new SuccessRateTrendDTO(weeklySuccessRates);
    }

    @Cacheable(value = "analytics", key = "'scenario-usage-' + #limit")
    public ScenarioUsageDTO getScenarioUsage(int limit) {
        log.info("Fetching scenario usage: limit={}", limit);

        List<Object[]> topScenarios = auditLogRepository.findTopScenariosByUsage(PageRequest.of(0, limit));

        List<String> labels = new ArrayList<>();
        List<Integer> data = new ArrayList<>();

        for (Object[] row : topScenarios) {
            String scenarioCode = (String) row[0];
            Long count = ((Number) row[1]).longValue();

            // Get scenario description from database
            String scenarioName = scenarioRepository.findByScenarioCode(scenarioCode)
                    .map(AiScenario::getDescription)
                    .orElse(scenarioCode);

            labels.add(scenarioName);
            data.add(count.intValue());
        }

        // Calculate "Others" category
        if (!topScenarios.isEmpty()) {
            long topTotal = data.stream().mapToLong(Integer::longValue).sum();
            long grandTotal = auditLogRepository.count();
            int totalOthers = (int) (grandTotal - topTotal);

            if (totalOthers > 0) {
                labels.add("Others");
                data.add(totalOthers);
            }
        }

        return new ScenarioUsageDTO(labels, data);
    }
}

