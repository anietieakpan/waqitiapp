package com.waqiti.discovery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing Configuration
 * Enables automatic auditing of entities with @CreatedDate and @LastModifiedDate
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // JPA Auditing is now enabled for all entities extending BaseEntity
}
