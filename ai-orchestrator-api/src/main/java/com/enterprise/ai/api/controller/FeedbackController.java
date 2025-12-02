package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.MessageFeedback;
import com.enterprise.ai.data.repository.MessageFeedbackRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for message feedback operations.
 * Handles storing user reactions and providing admin analytics.
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final MessageFeedbackRepository feedbackRepository;

    /**
     * Submit feedback for a message
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitFeedback(@RequestBody FeedbackRequest request) {
        log.info("Received feedback: type={}, sessionId={}, userId={}",
                request.getFeedbackType(), request.getSessionId(), request.getUserId());

        MessageFeedback feedback = MessageFeedback.builder()
                .sessionId(request.getSessionId())
                .messageId(request.getMessageId())
                .userId(request.getUserId())
                .feedbackType(request.getFeedbackType())
                .comment(request.getComment())
                .scenarioCode(request.getScenarioCode())
                .userQuery(request.getUserQuery())
                .aiResponse(truncateIfNeeded(request.getAiResponse(), 4000))
                .build();

        feedbackRepository.save(feedback);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("feedbackId", feedback.getId());
        response.put("message", "Feedback recorded successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Get feedback statistics (Admin only)
     */
    @GetMapping("/stats")
    public ResponseEntity<FeedbackStats> getStats() {
        FeedbackStats stats = new FeedbackStats();

        // Overall stats
        List<Object[]> overallStats = feedbackRepository.getOverallFeedbackStats();
        long totalLikes = 0;
        long totalDislikes = 0;
        for (Object[] row : overallStats) {
            String type = (String) row[0];
            Long count = (Long) row[1];
            if ("like".equals(type)) {
                totalLikes = count;
            } else if ("dislike".equals(type)) {
                totalDislikes = count;
            }
        }
        stats.setTotalLikes(totalLikes);
        stats.setTotalDislikes(totalDislikes);
        stats.setTotalFeedback(totalLikes + totalDislikes);
        stats.setSatisfactionRate(totalLikes + totalDislikes > 0 
                ? (double) totalLikes / (totalLikes + totalDislikes) * 100 
                : 0);

        // Stats for last 7 days
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Object[]> weeklyStats = feedbackRepository.getFeedbackStatsSince(sevenDaysAgo);
        long weeklyLikes = 0;
        long weeklyDislikes = 0;
        for (Object[] row : weeklyStats) {
            String type = (String) row[0];
            Long count = (Long) row[1];
            if ("like".equals(type)) {
                weeklyLikes = count;
            } else if ("dislike".equals(type)) {
                weeklyDislikes = count;
            }
        }
        stats.setWeeklyLikes(weeklyLikes);
        stats.setWeeklyDislikes(weeklyDislikes);

        // Scenario breakdown
        List<Object[]> scenarioStats = feedbackRepository.getFeedbackByScenario();
        Map<String, Map<String, Long>> scenarioBreakdown = new HashMap<>();
        for (Object[] row : scenarioStats) {
            String scenario = (String) row[0];
            String type = (String) row[1];
            Long count = (Long) row[2];
            
            scenarioBreakdown.computeIfAbsent(scenario, k -> new HashMap<>())
                    .put(type, count);
        }
        stats.setScenarioBreakdown(scenarioBreakdown);

        return ResponseEntity.ok(stats);
    }

    /**
     * Get recent negative feedback for review (Admin only)
     */
    @GetMapping("/negative")
    public ResponseEntity<List<MessageFeedback>> getRecentNegativeFeedback(
            @RequestParam(defaultValue = "50") int limit) {
        List<MessageFeedback> feedback = feedbackRepository.getRecentNegativeFeedback();
        if (feedback.size() > limit) {
            feedback = feedback.subList(0, limit);
        }
        return ResponseEntity.ok(feedback);
    }

    /**
     * Get all feedback with optional filters (Admin only)
     */
    @GetMapping
    public ResponseEntity<List<MessageFeedback>> getAllFeedback(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(required = false) String userId) {
        
        List<MessageFeedback> feedback;
        
        if (feedbackType != null) {
            feedback = feedbackRepository.findByFeedbackType(feedbackType);
        } else if (scenarioCode != null) {
            feedback = feedbackRepository.findByScenarioCode(scenarioCode);
        } else if (userId != null) {
            feedback = feedbackRepository.findByUserId(userId);
        } else {
            feedback = feedbackRepository.findAll();
        }
        
        return ResponseEntity.ok(feedback);
    }

    private String truncateIfNeeded(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    // Request/Response DTOs
    @Data
    public static class FeedbackRequest {
        private String sessionId;
        private Long messageId;
        private String userId;
        private String feedbackType; // "like" or "dislike"
        private String comment;
        private String scenarioCode;
        private String userQuery;
        private String aiResponse;
    }

    @Data
    public static class FeedbackStats {
        private long totalFeedback;
        private long totalLikes;
        private long totalDislikes;
        private double satisfactionRate;
        private long weeklyLikes;
        private long weeklyDislikes;
        private Map<String, Map<String, Long>> scenarioBreakdown;
    }
}
