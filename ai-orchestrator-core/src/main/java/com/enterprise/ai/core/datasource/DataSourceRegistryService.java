package com.enterprise.ai.core.datasource;

import com.enterprise.ai.common.exception.SecurityViolationException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic DataSource Registry Service.
 * 
 * Enables multi-database routing for enterprise scenarios:
 * - internal: Internal AI orchestrator database
 * - retail: Retail banking database
 * - pfms: PFMS database
 * - upi: UPI database
 * - wallet: Wallet database
 * - cbs: Core Banking System database
 * 
 * MANDATORY for production:
 * - dbKey MUST be present in scenario config
 * - dbKey MUST be resolved via this service
 * - No SQL execution allowed without proper routing
 */
@Slf4j
@Service
public class DataSourceRegistryService {

    /**
     * Registry mapping dbKey to JdbcTemplate.
     */
    private final Map<String, NamedParameterJdbcTemplate> registry = new ConcurrentHashMap<>();

    /**
     * Default/internal JdbcTemplate (always available).
     */
    private final NamedParameterJdbcTemplate internalJdbcTemplate;

    /**
     * Constructor with default internal datasource.
     * Additional datasources can be registered during startup.
     */
    public DataSourceRegistryService(NamedParameterJdbcTemplate internalJdbcTemplate) {
        this.internalJdbcTemplate = internalJdbcTemplate;
    }

    /**
     * Initialize registry with default datasource.
     */
    @PostConstruct
    public void init() {
        // Register the default/internal datasource
        registry.put("internal", internalJdbcTemplate);
        registry.put("default", internalJdbcTemplate);  // Alias
        
        log.info("DataSourceRegistryService initialized with {} datasource(s): {}", 
                registry.size(), registry.keySet());
    }

    /**
     * Resolve JdbcTemplate by database key.
     * 
     * @param dbKey Database key from ai_scenarios.db_key
     * @return NamedParameterJdbcTemplate for the database
     * @throws SecurityViolationException if dbKey is unknown
     */
    public NamedParameterJdbcTemplate resolveByDbKey(String dbKey) {
        if (dbKey == null || dbKey.isBlank()) {
            throw new SecurityViolationException(
                    "Database routing not configured for this scenario",
                    "MISSING_DB_KEY",
                    null
            );
        }

        String normalizedKey = dbKey.toLowerCase().trim();
        NamedParameterJdbcTemplate template = registry.get(normalizedKey);

        if (template == null) {
            log.error("Unknown database key: {}. Available keys: {}", dbKey, registry.keySet());
            throw new SecurityViolationException(
                    "Unknown database key: " + dbKey,
                    "UNKNOWN_DB_KEY",
                    dbKey
            );
        }

        log.debug("Resolved database key '{}' to JdbcTemplate", dbKey);
        return template;
    }

    /**
     * Register a new datasource with the registry.
     * Called during application configuration to add enterprise databases.
     * 
     * @param dbKey Database key (e.g., "retail", "pfms", "upi")
     * @param dataSource DataSource to register
     */
    public void registerDataSource(String dbKey, DataSource dataSource) {
        if (dbKey == null || dbKey.isBlank()) {
            throw new IllegalArgumentException("dbKey cannot be null or empty");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }

        String normalizedKey = dbKey.toLowerCase().trim();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        registry.put(normalizedKey, template);
        
        log.info("Registered datasource with key: {}. Total datasources: {}", dbKey, registry.size());
    }

    /**
     * Register a JdbcTemplate directly.
     * 
     * @param dbKey Database key
     * @param jdbcTemplate JdbcTemplate to register
     */
    public void registerJdbcTemplate(String dbKey, NamedParameterJdbcTemplate jdbcTemplate) {
        if (dbKey == null || dbKey.isBlank()) {
            throw new IllegalArgumentException("dbKey cannot be null or empty");
        }
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate cannot be null");
        }

        String normalizedKey = dbKey.toLowerCase().trim();
        registry.put(normalizedKey, jdbcTemplate);
        
        log.info("Registered JdbcTemplate with key: {}. Total datasources: {}", dbKey, registry.size());
    }

    /**
     * Check if a database key is registered.
     */
    public boolean hasDataSource(String dbKey) {
        if (dbKey == null) return false;
        return registry.containsKey(dbKey.toLowerCase().trim());
    }

    /**
     * Get all registered database keys.
     */
    public Set<String> getRegisteredKeys() {
        return Set.copyOf(registry.keySet());
    }

    /**
     * Get count of registered datasources.
     */
    public int getDataSourceCount() {
        return registry.size();
    }

    /**
     * Unregister a datasource (for testing/cleanup).
     */
    public void unregisterDataSource(String dbKey) {
        if (dbKey != null) {
            registry.remove(dbKey.toLowerCase().trim());
            log.info("Unregistered datasource: {}", dbKey);
        }
    }
}
