package com.waqiti.common.database.config;

import com.waqiti.common.database.repository.OptimizedJpaRepositoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration to enable optimized JPA repositories with N+1 query prevention
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.waqiti",
    repositoryBaseClass = OptimizedJpaRepositoryImpl.class
)
@ConditionalOnProperty(
    name = "waqiti.database.optimization.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OptimizedJpaConfiguration {
    
    // The configuration is automatically applied when this class is loaded
    // All repositories will now extend OptimizedJpaRepository by default
}