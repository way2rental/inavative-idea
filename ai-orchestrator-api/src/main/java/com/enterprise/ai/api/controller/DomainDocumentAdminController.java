package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.DomainDocument;
import com.enterprise.ai.data.repository.DomainDocumentRepository;
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
 * REST controller for Domain Document admin operations.
 * Manages domain documents for RAG Engine (FAQs, Policies, Products, SOPs).
 * 
 * NO HARDCODING - All domain knowledge from database.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/domain-documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Domain Document Admin", description = "Admin API for managing domain documents (RAG Engine)")
public class DomainDocumentAdminController {

    private final DomainDocumentRepository repository;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all domain documents")
    public ResponseEntity<List<DomainDocumentDTO>> getAllDocuments(
            @RequestParam(required = false) String category) {
        log.info("Admin requested domain documents, category: {}", category);
        
        List<DomainDocument> documents = category != null
            ? repository.findByCategoryAndActiveTrueOrderByPriorityDesc(category)
            : repository.findAllActiveOrderByPriorityDesc();
        
        List<DomainDocumentDTO> dtos = documents.stream()
                .map(this::toDTO)
                .toList();
        
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active domain documents")
    public ResponseEntity<List<DomainDocumentDTO>> getActiveDocuments() {
        log.info("Admin requested active domain documents");
        List<DomainDocument> documents = repository.findAllActiveOrderByPriorityDesc();
        List<DomainDocumentDTO> dtos = documents.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get documents by category")
    public ResponseEntity<List<DomainDocumentDTO>> getDocumentsByCategory(@PathVariable String category) {
        log.info("Admin requested domain documents for category: {}", category);
        List<DomainDocument> documents = repository.findByCategoryAndActiveTrueOrderByPriorityDesc(category);
        List<DomainDocumentDTO> dtos = documents.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get domain document by ID")
    public ResponseEntity<DomainDocumentDTO> getDocumentById(@PathVariable Long id) {
        log.info("Admin requested domain document with id: {}", id);
        return repository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new domain document")
    public ResponseEntity<DomainDocumentDTO> createDocument(@RequestBody DomainDocumentFormDTO form) {
        log.info("Admin creating domain document: {} - {}", form.category, form.title);
        
        DomainDocument document = DomainDocument.builder()
                .title(form.title)
                .content(form.content)
                .category(form.category)
                .subcategory(form.subcategory)
                .tags(form.tags)
                .embeddingVector(form.embeddingVector)
                .embeddingModel(form.embeddingModel)
                .documentVersion(form.documentVersion != null ? form.documentVersion : 1)
                .sourceUrl(form.sourceUrl)
                .author(form.author)
                .priority(form.priority != null ? form.priority : 0)
                .active(form.active != null ? form.active : true)
                .build();
        
        DomainDocument saved = repository.save(document);
        log.info("Domain document created: id={}, title={}", saved.getId(), saved.getTitle());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update domain document")
    public ResponseEntity<DomainDocumentDTO> updateDocument(
            @PathVariable Long id,
            @RequestBody DomainDocumentFormDTO form) {
        log.info("Admin updating domain document: {}", id);
        
        return repository.findById(id)
                .map(document -> {
                    document.setTitle(form.title);
                    document.setContent(form.content);
                    document.setCategory(form.category);
                    document.setSubcategory(form.subcategory);
                    document.setTags(form.tags);
                    if (form.embeddingVector != null) {
                        document.setEmbeddingVector(form.embeddingVector);
                    }
                    if (form.embeddingModel != null) {
                        document.setEmbeddingModel(form.embeddingModel);
                    }
                    if (form.documentVersion != null) {
                        document.setDocumentVersion(form.documentVersion);
                    }
                    document.setSourceUrl(form.sourceUrl);
                    document.setAuthor(form.author);
                    if (form.priority != null) {
                        document.setPriority(form.priority);
                    }
                    if (form.active != null) {
                        document.setActive(form.active);
                    }
                    document.setUpdatedAt(Instant.now());
                    
                    DomainDocument saved = repository.save(document);
                    log.info("Domain document updated: id={}, title={}", saved.getId(), saved.getTitle());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete domain document")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("Admin deleting domain document: {}", id);
        
        return repository.findById(id)
                .map(document -> {
                    repository.delete(document);
                    log.info("Domain document deleted: id={}, title={}", document.getId(), document.getTitle());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle document active status")
    public ResponseEntity<DomainDocumentDTO> toggleDocument(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        log.info("Admin toggling domain document status: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return repository.findById(id)
                .map(document -> {
                    document.setActive(active);
                    document.setUpdatedAt(Instant.now());
                    DomainDocument saved = repository.save(document);
                    log.info("Domain document {} status set to: {}", saved.getId(), active);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== HELPERS =====================

    private DomainDocumentDTO toDTO(DomainDocument document) {
        if (document == null) {
            return null;
        }
        
        DomainDocumentDTO dto = new DomainDocumentDTO();
        dto.id = document.getId();
        dto.title = document.getTitle();
        dto.content = document.getContent();
        dto.category = document.getCategory();
        dto.subcategory = document.getSubcategory();
        dto.tags = document.getTags();
        dto.embeddingVector = document.getEmbeddingVector();
        dto.embeddingModel = document.getEmbeddingModel();
        dto.documentVersion = document.getDocumentVersion();
        dto.sourceUrl = document.getSourceUrl();
        dto.author = document.getAuthor();
        dto.priority = document.getPriority();
        dto.active = document.getActive();
        dto.createdAt = document.getCreatedAt() != null ? document.getCreatedAt().toString() : null;
        dto.updatedAt = document.getUpdatedAt() != null ? document.getUpdatedAt().toString() : null;
        
        return dto;
    }

    // ===================== DTOs =====================

    @Data
    public static class DomainDocumentDTO {
        public Long id;
        public String title;
        public String content;
        public String category;
        public String subcategory;
        public String tags;
        public String embeddingVector;
        public String embeddingModel;
        public Integer documentVersion;
        public String sourceUrl;
        public String author;
        public Integer priority;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
    }

    @Data
    public static class DomainDocumentFormDTO {
        public String title;
        public String content;
        public String category;
        public String subcategory;
        public String tags;
        public String embeddingVector;
        public String embeddingModel;
        public Integer documentVersion;
        public String sourceUrl;
        public String author;
        public Integer priority;
        public Boolean active;
    }
}
