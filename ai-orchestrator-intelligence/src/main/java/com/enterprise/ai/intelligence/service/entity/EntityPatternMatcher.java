package com.enterprise.ai.intelligence.service.entity;

import com.enterprise.ai.data.entity.EntityPattern;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches entity patterns against text.
 * Supports REGEX, NER_MODEL, CONTEXT_BASED, and VALIDATION patterns.
 * 
 * NO HARDCODING - All patterns from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityPatternMatcher {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Match a pattern against text.
     * 
     * @param pattern Entity pattern from database
     * @param text Text to match against
     * @return Optional EntityMatch if pattern matches
     */
    public Optional<EntityMatch> match(EntityPattern pattern, String text) {
        try {
            switch (pattern.getPatternType().toUpperCase()) {
                case "REGEX":
                    return matchRegex(pattern, text);
                case "NER_MODEL":
                    // NER_MODEL pattern type: Uses ML-based NER model
                    // Note: MlNerService integration should be done via EntityExtractionService
                    // EntityPatternMatcher focuses on pattern matching, not ML inference
                    // ML NER is handled separately by MlNerService and integrated at service level
                    log.debug("NER_MODEL pattern type - ML NER handled by MlNerService (pattern: {})", pattern.getId());
                    return Optional.empty();
                case "CONTEXT_BASED":
                    // CONTEXT_BASED pattern type: Will be implemented for context-aware entity extraction
                    // This requires access to ContextMemoryService to match entities based on conversation history
                    // Placeholder for future enhancement - will use session-based context for entity matching
                    log.debug("CONTEXT_BASED pattern type not yet implemented for pattern: {} (requires context memory integration)", pattern.getId());
                    return Optional.empty();
                case "VALIDATION":
                    // Validation patterns are used for validation, not extraction
                    return Optional.empty();
                default:
                    log.warn("Unknown pattern type: {} for pattern: {}", pattern.getPatternType(), pattern.getId());
                    return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error matching pattern {}: {}", pattern.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Match regex pattern.
     */
    private Optional<EntityMatch> matchRegex(EntityPattern pattern, String text) {
        try {
            // Parse config JSON for regex flags
            Map<String, Object> config = parseConfig(pattern.getConfigJson());
            int flags = 0;
            if (config != null) {
                if (Boolean.TRUE.equals(config.get("caseInsensitive"))) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                if (Boolean.TRUE.equals(config.get("multiline"))) {
                    flags |= Pattern.MULTILINE;
                }
                if (Boolean.TRUE.equals(config.get("dotall"))) {
                    flags |= Pattern.DOTALL;
                }
            }

            Pattern regexPattern = Pattern.compile(pattern.getPatternDefinition(), flags);
            Matcher matcher = regexPattern.matcher(text);

            if (matcher.find()) {
                String matchedValue = matcher.group(0);
                
                // Calculate confidence (base confidence + boost)
                BigDecimal confidence = BigDecimal.valueOf(0.8); // Base confidence for regex
                if (pattern.getConfidenceBoost() != null) {
                    confidence = confidence.add(pattern.getConfidenceBoost());
                }
                // Cap at 1.0
                if (confidence.compareTo(BigDecimal.ONE) > 0) {
                    confidence = BigDecimal.ONE;
                }

                // Build metadata
                Map<String, Object> metadata = new HashMap<>();
                if (matcher.groupCount() > 0) {
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        metadata.put("group" + i, matcher.group(i));
                    }
                }

                return Optional.of(EntityMatch.builder()
                    .entityType(pattern.getEntityType())
                    .value(matchedValue)
                    .confidence(confidence)
                    .matchedPattern(String.valueOf(pattern.getId()))
                    .startPosition(matcher.start())
                    .endPosition(matcher.end())
                    .metadata(metadata)
                    .build());
            }

            return Optional.empty();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern {}: {}", pattern.getId(), pattern.getPatternDefinition());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error matching regex pattern {}: {}", pattern.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse config JSON.
     */
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse config JSON: {}", e.getMessage());
            return null;
        }
    }
}
