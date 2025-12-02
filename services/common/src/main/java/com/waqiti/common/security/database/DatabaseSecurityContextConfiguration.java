package com.waqiti.common.security.database;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * CRITICAL SECURITY: Database Security Context Configuration
 * 
 * Enables automatic database security context management for Row-Level Security (RLS).
 * This configuration is essential for financial services compliance and data protection.
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(
    name = "waqiti.security.database.rls.enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
public class DatabaseSecurityContextConfiguration {

    /**
     * Database security context service bean
     */
    @Bean
    public DatabaseSecurityContextService databaseSecurityContextService(JdbcTemplate jdbcTemplate) {
        return new DatabaseSecurityContextService(jdbcTemplate);
    }

    /**
     * Database security context aspect bean
     */
    @Bean
    public DatabaseSecurityContextAspect databaseSecurityContextAspect(
            DatabaseSecurityContextService contextService) {
        return new DatabaseSecurityContextAspect(contextService);
    }

    /**
     * RLS validation service for testing and monitoring
     */
    @Bean
    public RLSValidationService rlsValidationService(JdbcTemplate jdbcTemplate) {
        return new RLSValidationService(jdbcTemplate);
    }

    /**
     * Database security context health indicator
     */
    @Bean
    public DatabaseSecurityHealthIndicator databaseSecurityHealthIndicator(
            RLSValidationService validationService) {
        return new DatabaseSecurityHealthIndicator(validationService);
    }
}