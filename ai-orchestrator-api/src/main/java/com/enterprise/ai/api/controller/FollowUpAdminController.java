package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.FollowUpGroup;
import com.enterprise.ai.data.repository.FollowUpGroupRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Admin controller for follow-up group management.
 * Enables dynamic follow-up question configuration without code changes.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/followups")
@RequiredArgsConstructor
@Tag(name = "Follow-up Admin", description = "Follow-up group management")
@PreAuthorize("hasRole('ADMIN')")
public class FollowUpAdminController {

    private final FollowUpGroupRepository followUpRepository;
    private final ObjectMapper objectMapper;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all follow-up groups")
    public ResponseEntity<List<FollowUpGroupDTO>> getAllFollowUpGroups() {
        log.info("Fetching all follow-up groups");
        List<FollowUpGroup> groups = followUpRepository.findAll();
        List<FollowUpGroupDTO> dtos = groups.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active follow-up groups")
    public ResponseEntity<List<FollowUpGroupDTO>> getActiveFollowUpGroups() {
        log.info("Fetching active follow-up groups");
        List<FollowUpGroup> groups = followUpRepository.findByActiveTrue();
        List<FollowUpGroupDTO> dtos = groups.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get follow-up group by ID")
    public ResponseEntity<FollowUpGroupDTO> getFollowUpGroupById(@PathVariable Long id) {
        log.info("Fetching follow-up group with id: {}", id);
        return followUpRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{groupKey}")
    @Operation(summary = "Get follow-up group by key")
    public ResponseEntity<FollowUpGroupDTO> getFollowUpGroupByKey(@PathVariable String groupKey) {
        log.info("Fetching follow-up group with key: {}", groupKey);
        return followUpRepository.findByGroupKey(groupKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new follow-up group")
    public ResponseEntity<FollowUpGroupDTO> createFollowUpGroup(@RequestBody FollowUpGroupFormDTO form) {
        log.info("Creating new follow-up group: {}", form.groupKey);
        
        if (followUpRepository.existsByGroupKey(form.groupKey)) {
            return ResponseEntity.badRequest().build();
        }

        FollowUpGroup group = FollowUpGroup.builder()
                .groupKey(form.groupKey)
                .description(form.description)
                .scenarioCodes(toJsonArray(form.scenarioCodes))
                .questions(toJsonObject(form.questions))
                .questionOrder(toJsonArray(form.questionOrder))
                .active(form.active != null ? form.active : true)
                .createdBy(form.createdBy)
                .build();

        FollowUpGroup saved = followUpRepository.save(group);
        log.info("Follow-up group created: {}", saved.getGroupKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update follow-up group")
    public ResponseEntity<FollowUpGroupDTO> updateFollowUpGroup(@PathVariable Long id, @RequestBody FollowUpGroupFormDTO form) {
        log.info("Updating follow-up group with id: {}", id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    group.setDescription(form.description);
                    group.setScenarioCodes(toJsonArray(form.scenarioCodes));
                    group.setQuestions(toJsonObject(form.questions));
                    group.setQuestionOrder(toJsonArray(form.questionOrder));
                    group.setActive(form.active != null ? form.active : true);
                    group.setUpdatedAt(Instant.now());
                    
                    FollowUpGroup saved = followUpRepository.save(group);
                    log.info("Follow-up group updated: {}", saved.getGroupKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete follow-up group")
    public ResponseEntity<Void> deleteFollowUpGroup(@PathVariable Long id) {
        log.info("Deleting follow-up group with id: {}", id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    followUpRepository.delete(group);
                    log.info("Follow-up group deleted: {}", group.getGroupKey());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== QUESTIONS MANAGEMENT =====================

    @PostMapping("/{id}/questions")
    @Operation(summary = "Add question to follow-up group")
    public ResponseEntity<FollowUpGroupDTO> addQuestion(
            @PathVariable Long id, 
            @RequestBody FollowUpQuestionDTO question) {
        log.info("Adding question to follow-up group: {}", id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    List<FollowUpQuestionDTO> questions = parseQuestions(group.getQuestions());
                    questions.add(question);
                    group.setQuestions(toJsonObject(questions));
                    
                    // Update order
                    List<String> order = parseJsonArray(group.getQuestionOrder());
                    order.add(question.key);
                    group.setQuestionOrder(toJsonArray(order));
                    
                    group.setUpdatedAt(Instant.now());
                    FollowUpGroup saved = followUpRepository.save(group);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/questions/{questionKey}")
    @Operation(summary = "Update specific question in follow-up group")
    public ResponseEntity<FollowUpGroupDTO> updateQuestion(
            @PathVariable Long id, 
            @PathVariable String questionKey,
            @RequestBody FollowUpQuestionDTO question) {
        log.info("Updating question {} in follow-up group: {}", questionKey, id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    List<FollowUpQuestionDTO> questions = parseQuestions(group.getQuestions());
                    questions.removeIf(q -> questionKey.equals(q.key));
                    question.key = questionKey;
                    questions.add(question);
                    group.setQuestions(toJsonObject(questions));
                    group.setUpdatedAt(Instant.now());
                    
                    FollowUpGroup saved = followUpRepository.save(group);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/questions/{questionKey}")
    @Operation(summary = "Remove question from follow-up group")
    public ResponseEntity<FollowUpGroupDTO> removeQuestion(
            @PathVariable Long id, 
            @PathVariable String questionKey) {
        log.info("Removing question {} from follow-up group: {}", questionKey, id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    List<FollowUpQuestionDTO> questions = parseQuestions(group.getQuestions());
                    questions.removeIf(q -> questionKey.equals(q.key));
                    group.setQuestions(toJsonObject(questions));
                    
                    List<String> order = parseJsonArray(group.getQuestionOrder());
                    order.remove(questionKey);
                    group.setQuestionOrder(toJsonArray(order));
                    
                    group.setUpdatedAt(Instant.now());
                    FollowUpGroup saved = followUpRepository.save(group);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/order")
    @Operation(summary = "Update question order in follow-up group")
    public ResponseEntity<FollowUpGroupDTO> updateQuestionOrder(
            @PathVariable Long id, 
            @RequestBody List<String> order) {
        log.info("Updating question order for follow-up group: {}", id);
        
        return followUpRepository.findById(id)
                .map(group -> {
                    group.setQuestionOrder(toJsonArray(order));
                    group.setUpdatedAt(Instant.now());
                    FollowUpGroup saved = followUpRepository.save(group);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle follow-up group active status")
    public ResponseEntity<FollowUpGroupDTO> toggleFollowUpGroup(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling follow-up group status for id: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return followUpRepository.findById(id)
                .map(group -> {
                    group.setActive(active);
                    group.setUpdatedAt(Instant.now());
                    FollowUpGroup saved = followUpRepository.save(group);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== HELPERS =====================

    private FollowUpGroupDTO toDTO(FollowUpGroup group) {
        FollowUpGroupDTO dto = new FollowUpGroupDTO();
        dto.id = group.getId();
        dto.groupKey = group.getGroupKey();
        dto.description = group.getDescription();
        dto.scenarioCodes = parseJsonArray(group.getScenarioCodes());
        dto.questions = parseQuestions(group.getQuestions());
        dto.questionOrder = parseJsonArray(group.getQuestionOrder());
        dto.active = group.getActive();
        dto.createdAt = group.getCreatedAt() != null ? group.getCreatedAt().toString() : null;
        dto.updatedAt = group.getUpdatedAt() != null ? group.getUpdatedAt().toString() : null;
        dto.createdBy = group.getCreatedBy();
        return dto;
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String toJsonObject(Object obj) {
        if (obj == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON array", e);
            return new ArrayList<>();
        }
    }

    private List<FollowUpQuestionDTO> parseQuestions(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FollowUpQuestionDTO.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse questions JSON", e);
            return new ArrayList<>();
        }
    }

    // ===================== DTOs =====================

    @Data
    public static class FollowUpGroupDTO {
        public Long id;
        public String groupKey;
        public String description;
        public List<String> scenarioCodes;
        public List<FollowUpQuestionDTO> questions;
        public List<String> questionOrder;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
        public String createdBy;
    }

    @Data
    public static class FollowUpGroupFormDTO {
        public String groupKey;
        public String description;
        public List<String> scenarioCodes;
        public List<FollowUpQuestionDTO> questions;
        public List<String> questionOrder;
        public Boolean active;
        public String createdBy;
    }

    @Data
    public static class FollowUpQuestionDTO {
        public String key;
        public String question;
        public String type;  // string, date, number, boolean
        public Boolean required;
        public String validationPattern;
        public String placeholder;
        public String defaultValue;
    }
}
