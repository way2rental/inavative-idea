package com.enterprise.ai.data.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Data layer configuration.
 */
@Configuration
@EntityScan(basePackages = "com.enterprise.ai.data.entity")
@EnableJpaRepositories(basePackages = "com.enterprise.ai.data.repository")
public class DataConfig {
}
