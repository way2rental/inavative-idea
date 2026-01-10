package com.enterprise.ai.intelligence.config;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Intelligence module.
 * Sets up Freemarker for template processing.
 */
@org.springframework.context.annotation.Configuration
@ComponentScan(basePackages = "com.enterprise.ai.intelligence")
public class IntelligenceConfig {

    /**
     * Configure Freemarker for template processing.
     * Used for processing DB-driven templates.
     * 
     * Marked as @Primary to override Spring Boot's auto-configured Freemarker Configuration
     * since we need StringTemplateLoader for database-driven templates.
     */
    @Bean
    @Primary
    public Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(new Version(2, 3, 32));
        
        // Use StringTemplateLoader for templates from database
        StringTemplateLoader stringLoader = new StringTemplateLoader();
        cfg.setTemplateLoader(stringLoader);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        
        return cfg;
    }
}
