package com.enterprise.ai.api.controller;

import com.enterprise.ai.api.dto.analytics.*;
import com.enterprise.ai.api.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/requests-over-time")
    public ResponseEntity<RequestsOverTimeDTO> getRequestsOverTime(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "4") int interval) {
        log.info("Getting requests over time analytics: hours={}, interval={}", hours, interval);
        return ResponseEntity.ok(analyticsService.getRequestsOverTime(hours, interval));
    }

    @GetMapping("/response-distribution")
    public ResponseEntity<ResponseDistributionDTO> getResponseDistribution() {
        log.info("Getting response time distribution analytics");
        return ResponseEntity.ok(analyticsService.getResponseDistribution());
    }

    @GetMapping("/success-rate-trend")
    public ResponseEntity<SuccessRateTrendDTO> getSuccessRateTrend(
            @RequestParam(defaultValue = "4") int weeks) {
        log.info("Getting success rate trend analytics: weeks={}", weeks);
        return ResponseEntity.ok(analyticsService.getSuccessRateTrend(weeks));
    }

    @GetMapping("/scenario-usage")
    public ResponseEntity<ScenarioUsageDTO> getScenarioUsage(
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Getting top scenario usage analytics: limit={}", limit);
        return ResponseEntity.ok(analyticsService.getScenarioUsage(limit));
    }
}
