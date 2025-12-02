package com.enterprise.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for AI Orchestrator.
 * 
 * Features enabled:
 * - @EnableAsync: Async audit logging for non-blocking performance
 * - @EnableCaching: Cache scenarios, response mappings, RBAC for performance
 * - @EnableScheduling: Scheduled cache refresh for RBAC
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.ai")
@EnableConfigurationProperties
@EnableAsync
@EnableCaching
@EnableScheduling
public class AiOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOrchestratorApplication.class, args);
    }
}
