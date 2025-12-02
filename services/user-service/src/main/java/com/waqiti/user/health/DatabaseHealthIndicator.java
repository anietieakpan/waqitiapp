package com.waqiti.user.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database Health Indicator
 *
 * Checks database connectivity and query responsiveness
 *
 * KUBERNETES CONTEXT:
 * - Used by readiness probe to prevent traffic during DB outage
 * - Used by liveness probe to restart pod if DB connection stuck
 *
 * PRODUCTION REQUIREMENTS:
 * - Must complete in <1 second (Kubernetes timeout)
 * - Should not cache results (real-time health check)
 * - Must not impact application performance
 */
@Slf4j
@Component("database")
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    // Simple query to test database connectivity
    private static final String HEALTH_CHECK_QUERY = "SELECT 1";
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 2;

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            // Test database connectivity with timeout
            boolean isHealthy = checkDatabaseHealth();

            long responseTime = System.currentTimeMillis() - startTime;

            if (isHealthy) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("query", HEALTH_CHECK_QUERY)
                    .build();
            } else {
                log.error("HEALTH: Database health check failed");
                return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("error", "Query failed or timed out")
                    .withDetail("timeout", HEALTH_CHECK_TIMEOUT_SECONDS + "s")
                    .build();
            }

        } catch (Exception e) {
            log.error("HEALTH: Database health check error", e);
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    /**
     * Execute health check query with timeout
     */
    private boolean checkDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            // Set query timeout
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(HEALTH_CHECK_TIMEOUT_SECONDS);

                try (ResultSet rs = statement.executeQuery(HEALTH_CHECK_QUERY)) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            }
        } catch (SQLException e) {
            log.error("HEALTH: Database connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
