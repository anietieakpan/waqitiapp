package com.waqiti.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration to enable JPA auditing
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class AuditConfig {
    // Configuration class to enable JPA auditing
}