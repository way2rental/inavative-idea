package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.BankingConcept;
import com.enterprise.ai.data.repository.BankingConceptRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Banking Concept admin operations.
 * Manages banking concept dictionary for enhanced concept extraction.
 * 
 * NO HARDCODING - All banking concepts from database.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/banking-concepts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Banking Concept Admin", description = "Admin API for managing banking concept dictionary")
public class BankingConceptAdminController {

    private final BankingConceptRepository repository;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all banking concepts")
    public ResponseEntity<List<BankingConceptDTO>> getAllConcepts(
            @RequestParam(required = false) String conceptType,
            @RequestParam(required = false) String parentConceptCode) {
        log.info("Admin requested banking concepts, type: {}, parent: {}", conceptType, parentConceptCode);
        
        List<BankingConcept> concepts;
        if (conceptType != null) {
            concepts = repository.findByConceptTypeAndActiveTrueOrderByPriorityDesc(conceptType);
        } else if (parentConceptCode != null) {
            concepts = repository.findByParentConceptCodeAndActiveTrue(parentConceptCode);
        } else {
            concepts = repository.findAllActiveOrderByPriorityDesc();
        }
        
        List<BankingConceptDTO> dtos = concepts.stream()
                .map(this::toDTO)
                .toList();
        
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active banking concepts")
    public ResponseEntity<List<BankingConceptDTO>> getActiveConcepts() {
        log.info("Admin requested active banking concepts");
        List<BankingConcept> concepts = repository.findAllActiveOrderByPriorityDesc();
        List<BankingConceptDTO> dtos = concepts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/type/{conceptType}")
    @Operation(summary = "Get concepts by type")
    public ResponseEntity<List<BankingConceptDTO>> getConceptsByType(@PathVariable String conceptType) {
        log.info("Admin requested banking concepts for type: {}", conceptType);
        List<BankingConcept> concepts = repository.findByConceptTypeAndActiveTrueOrderByPriorityDesc(conceptType);
        List<BankingConceptDTO> dtos = concepts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/parent/{parentConceptCode}")
    @Operation(summary = "Get child concepts by parent")
    public ResponseEntity<List<BankingConceptDTO>> getChildConcepts(@PathVariable String parentConceptCode) {
        log.info("Admin requested child concepts for parent: {}", parentConceptCode);
        List<BankingConcept> concepts = repository.findByParentConceptCodeAndActiveTrue(parentConceptCode);
        List<BankingConceptDTO> dtos = concepts.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get banking concept by ID")
    public ResponseEntity<BankingConceptDTO> getConceptById(@PathVariable Long id) {
        log.info("Admin requested banking concept with id: {}", id);
        return repository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{conceptCode}")
    @Operation(summary = "Get banking concept by code")
    public ResponseEntity<BankingConceptDTO> getConceptByCode(@PathVariable String conceptCode) {
        log.info("Admin requested banking concept with code: {}", conceptCode);
        return repository.findByConceptCode(conceptCode)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new banking concept")
    public ResponseEntity<BankingConceptDTO> createConcept(@RequestBody BankingConceptFormDTO form) {
        log.info("Admin creating banking concept: {} - {}", form.conceptCode, form.conceptName);
        
        // Check if concept code already exists
        if (repository.findByConceptCode(form.conceptCode).isPresent()) {
            log.warn("Banking concept code already exists: {}", form.conceptCode);
            return ResponseEntity.badRequest().build();
        }
        
        BankingConcept concept = BankingConcept.builder()
                .conceptCode(form.conceptCode)
                .conceptName(form.conceptName)
                .parentConceptCode(form.parentConceptCode)
                .conceptType(form.conceptType)
                .synonyms(form.synonyms)
                .description(form.description)
                .relatedConcepts(form.relatedConcepts)
                .priority(form.priority != null ? form.priority : 0)
                .active(form.active != null ? form.active : true)
                .build();
        
        BankingConcept saved = repository.save(concept);
        log.info("Banking concept created: id={}, code={}", saved.getId(), saved.getConceptCode());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update banking concept")
    public ResponseEntity<BankingConceptDTO> updateConcept(
            @PathVariable Long id,
            @RequestBody BankingConceptFormDTO form) {
        log.info("Admin updating banking concept: {}", id);
        
        return repository.findById(id)
                .map(concept -> {
                    // Allow updating concept name and other fields, but not concept code
                    concept.setConceptName(form.conceptName);
                    concept.setParentConceptCode(form.parentConceptCode);
                    concept.setConceptType(form.conceptType);
                    concept.setSynonyms(form.synonyms);
                    concept.setDescription(form.description);
                    concept.setRelatedConcepts(form.relatedConcepts);
                    if (form.priority != null) {
                        concept.setPriority(form.priority);
                    }
                    if (form.active != null) {
                        concept.setActive(form.active);
                    }
                    
                    BankingConcept saved = repository.save(concept);
                    log.info("Banking concept updated: id={}, code={}", saved.getId(), saved.getConceptCode());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete banking concept")
    public ResponseEntity<Void> deleteConcept(@PathVariable Long id) {
        log.info("Admin deleting banking concept: {}", id);
        
        return repository.findById(id)
                .map(concept -> {
                    repository.delete(concept);
                    log.info("Banking concept deleted: id={}, code={}", concept.getId(), concept.getConceptCode());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle concept active status")
    public ResponseEntity<BankingConceptDTO> toggleConcept(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        log.info("Admin toggling banking concept status: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return repository.findById(id)
                .map(concept -> {
                    concept.setActive(active);
                    BankingConcept saved = repository.save(concept);
                    log.info("Banking concept {} status set to: {}", saved.getId(), active);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== HELPERS =====================

    private BankingConceptDTO toDTO(BankingConcept concept) {
        if (concept == null) {
            return null;
        }
        
        BankingConceptDTO dto = new BankingConceptDTO();
        dto.id = concept.getId();
        dto.conceptCode = concept.getConceptCode();
        dto.conceptName = concept.getConceptName();
        dto.parentConceptCode = concept.getParentConceptCode();
        dto.conceptType = concept.getConceptType();
        dto.synonyms = concept.getSynonyms();
        dto.description = concept.getDescription();
        dto.relatedConcepts = concept.getRelatedConcepts();
        dto.priority = concept.getPriority();
        dto.active = concept.getActive();
        dto.createdAt = concept.getCreatedAt() != null ? concept.getCreatedAt().toString() : null;
        dto.updatedAt = concept.getUpdatedAt() != null ? concept.getUpdatedAt().toString() : null;
        
        return dto;
    }

    // ===================== DTOs =====================

    @Data
    public static class BankingConceptDTO {
        public Long id;
        public String conceptCode;
        public String conceptName;
        public String parentConceptCode;
        public String conceptType;
        public String synonyms;
        public String description;
        public String relatedConcepts;
        public Integer priority;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
    }

    @Data
    public static class BankingConceptFormDTO {
        public String conceptCode;
        public String conceptName;
        public String parentConceptCode;
        public String conceptType;
        public String synonyms;
        public String description;
        public String relatedConcepts;
        public Integer priority;
        public Boolean active;
    }
}
