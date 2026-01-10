package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.EntityPattern;
import com.enterprise.ai.data.repository.EntityPatternRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Entity Pattern admin operations.
 * Manages entity extraction patterns (regex, validation rules).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/entity-patterns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Entity Pattern Admin", description = "Admin API for managing entity extraction patterns")
public class EntityPatternAdminController {

    private final EntityPatternRepository patternRepository;

    @GetMapping
    @Operation(summary = "Get all entity patterns")
    public ResponseEntity<List<EntityPattern>> getPatterns(
            @RequestParam(required = false) String entityType) {
        log.info("Admin requested entity patterns, entityType: {}", entityType);
        
        List<EntityPattern> patterns = entityType != null
            ? patternRepository.findByEntityType(entityType)
            : patternRepository.findAll();
        
        return ResponseEntity.ok(patterns);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get entity pattern by ID")
    public ResponseEntity<EntityPattern> getPattern(@PathVariable Long id) {
        return patternRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create entity pattern")
    public ResponseEntity<EntityPattern> createPattern(@RequestBody EntityPattern pattern) {
        log.info("Admin creating entity pattern: {} - {}", pattern.getEntityType(), pattern.getPatternType());
        return ResponseEntity.ok(patternRepository.save(pattern));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update entity pattern")
    public ResponseEntity<EntityPattern> updatePattern(
            @PathVariable Long id,
            @RequestBody EntityPattern pattern) {
        log.info("Admin updating entity pattern: {}", id);
        
        return patternRepository.findById(id)
            .map(existing -> {
                pattern.setId(id);
                return ResponseEntity.ok(patternRepository.save(pattern));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete entity pattern")
    public ResponseEntity<Void> deletePattern(@PathVariable Long id) {
        log.info("Admin deleting entity pattern: {}", id);
        patternRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
