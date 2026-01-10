package com.enterprise.ai.intelligence.service.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Resolves references in queries (e.g., "same account", "that transaction").
 * Works with ContextMemoryService to resolve entity references.
 * 
 * NO HARDCODING - Reference patterns can be DB-driven in future.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceResolver {

    // Reference patterns (can be moved to database in future)
    private static final List<Pattern> REFERENCE_PATTERNS = Arrays.asList(
        Pattern.compile("\\b(same|that|this|it|them|the)\\s+(account|transaction|payment|transfer|amount|date|id)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(same|that|this)\\s+one", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(previous|last|earlier)\\s+(account|transaction|payment)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Detect if query contains a reference.
     * 
     * @param query User query
     * @return true if query contains a reference
     */
    public boolean hasReference(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        return REFERENCE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(query).find());
    }

    /**
     * Extract entity type from reference.
     * 
     * @param query User query
     * @return Optional entity type if reference detected
     */
    public Optional<String> extractEntityTypeFromReference(String query) {
        if (!hasReference(query)) {
            return Optional.empty();
        }
        
        String queryLower = query.toLowerCase();
        
        // Try to extract entity type from reference
        if (queryLower.contains("account")) {
            return Optional.of("ACCOUNT_ID");
        } else if (queryLower.contains("transaction")) {
            return Optional.of("TRANSACTION_ID");
        } else if (queryLower.contains("payment")) {
            return Optional.of("PAYMENT_ID");
        } else if (queryLower.contains("transfer")) {
            return Optional.of("TRANSFER_ID");
        } else if (queryLower.contains("amount")) {
            return Optional.of("AMOUNT");
        } else if (queryLower.contains("date")) {
            return Optional.of("DATE");
        }
        
        // Generic reference - return empty to try all entity types
        return Optional.empty();
    }

    /**
     * Get all possible entity types from reference.
     * 
     * @param query User query
     * @return List of possible entity types
     */
    public List<String> getPossibleEntityTypes(String query) {
        if (!hasReference(query)) {
            return Collections.emptyList();
        }
        
        List<String> types = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        if (queryLower.contains("account")) types.add("ACCOUNT_ID");
        if (queryLower.contains("transaction")) types.add("TRANSACTION_ID");
        if (queryLower.contains("payment")) types.add("PAYMENT_ID");
        if (queryLower.contains("transfer")) types.add("TRANSFER_ID");
        if (queryLower.contains("amount")) types.add("AMOUNT");
        if (queryLower.contains("date")) types.add("DATE");
        
        // If no specific type found, return common types
        if (types.isEmpty()) {
            types.add("ACCOUNT_ID");
            types.add("TRANSACTION_ID");
        }
        
        return types;
    }
}
