package com.waqiti.notification.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock Configuration for Distributed Locking
 *
 * CRITICAL: Prevents duplicate execution of scheduled tasks in multi-instance deployments
 *
 * Problem Without ShedLock:
 * - App deployed with 3 instances
 * - Scheduled task runs every minute: processBatchQueue()
 * - All 3 instances execute simultaneously = DUPLICATE PROCESSING
 * - Results: Race conditions, duplicate notifications, wasted resources
 *
 * Solution With ShedLock:
 * - Only ONE instance acquires lock and executes
 * - Other instances skip execution
 * - Lock automatically released after execution or timeout
 * - Guarantees: At-most-once execution per schedule interval
 *
 * Lock Providers Supported:
 * - JDBC (current): Database-backed locks
 * - Redis: Fast distributed locks
 * - MongoDB: NoSQL-based locks
 * - Zookeeper: Coordination service locks
 *
 * Configuration:
 * - defaultLockAtMostFor: Maximum lock duration (30s)
 * - defaultLockAtLeastFor: Minimum lock duration (5s)
 * - Prevents overlapping executions
 *
 * @author Waqiti Engineering Team
 * @since 2.0
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30s", defaultLockAtLeastFor = "5s")
public class ShedLockConfiguration {

    /**
     * Creates JDBC-based lock provider
     *
     * Lock Table Schema (auto-created):
     * - name: Lock name (unique)
     * - lock_until: Lock expiry timestamp
     * - locked_at: Lock acquisition timestamp
     * - locked_by: Instance that acquired lock
     *
     * @param dataSource The datasource for lock storage
     * @return Configured lock provider
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Use database time for consistency
                .build()
        );
    }
}
