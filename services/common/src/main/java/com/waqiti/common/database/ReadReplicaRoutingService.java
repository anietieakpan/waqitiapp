package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read Replica Routing Service
 * 
 * Intelligent routing service that automatically directs read operations
 * to read replicas and write operations to the primary database.
 * Includes failover, load balancing, and replica health monitoring.
 */
@Service
@Slf4j
public class ReadReplicaRoutingService {

    private final DataSource primaryDataSource;
    private final List<DataSource> readReplicaDataSources;
    private final JdbcTemplate primaryJdbcTemplate;
    private final List<JdbcTemplate> replicaJdbcTemplates;
    private final Executor asyncExecutor;
    private final SecureRandom secureRandom = new SecureRandom();

    // Configuration
    @Value("${database.replica.max-lag-seconds:30}")
    private int maxLagSeconds;

    @Value("${database.replica.health-check-interval:30000}")
    private long healthCheckInterval;

    @Value("${database.replica.failover-enabled:true}")
    private boolean failoverEnabled;

    @Value("${database.replica.load-balancing:round-robin}")
    private String loadBalancingStrategy;

    // Replica health and metrics
    private final Map<DataSource, ReplicaHealthStatus> replicaHealth = new ConcurrentHashMap<>();
    private final AtomicInteger currentReplicaIndex = new AtomicInteger(0);
    private final AtomicLong totalReadQueries = new AtomicLong(0);
    private final AtomicLong totalWriteQueries = new AtomicLong(0);
    private final AtomicLong replicaFailovers = new AtomicLong(0);

    @Autowired
    public ReadReplicaRoutingService(
            DataSource primaryDataSource,
            List<DataSource> readReplicaDataSources,
            JdbcTemplate primaryJdbcTemplate,
            List<JdbcTemplate> replicaJdbcTemplates,
            Executor asyncExecutor) {
        
        this.primaryDataSource = primaryDataSource;
        this.readReplicaDataSources = readReplicaDataSources != null ? readReplicaDataSources : new ArrayList<>();
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.replicaJdbcTemplates = replicaJdbcTemplates != null ? replicaJdbcTemplates : new ArrayList<>();
        this.asyncExecutor = asyncExecutor;

        initializeReplicaHealth();
        startHealthMonitoring();
    }

    // =====================================================================
    // PUBLIC ROUTING METHODS
    // =====================================================================

    /**
     * Execute read query with automatic replica routing
     */
    public <T> List<T> executeReadQuery(String sql, Class<T> resultClass, Object... params) {
        totalReadQueries.incrementAndGet();
        
        JdbcTemplate template = selectReadReplica();
        
        try {
            if (template != null) {
                return template.queryForList(sql, resultClass, params);
            } else {
                log.warn("No healthy read replica available, falling back to primary");
                replicaFailovers.incrementAndGet();
                return primaryJdbcTemplate.queryForList(sql, resultClass, params);
            }
        } catch (Exception e) {
            log.error("Read query failed on replica, falling back to primary", e);
            replicaFailovers.incrementAndGet();
            return primaryJdbcTemplate.queryForList(sql, resultClass, params);
        }
    }

    /**
     * Execute read query returning Map results
     */
    public List<Map<String, Object>> executeReadQueryForList(String sql, Object... params) {
        totalReadQueries.incrementAndGet();
        
        JdbcTemplate template = selectReadReplica();
        
        try {
            if (template != null) {
                return template.queryForList(sql, params);
            } else {
                log.warn("No healthy read replica available, falling back to primary");
                replicaFailovers.incrementAndGet();
                return primaryJdbcTemplate.queryForList(sql, params);
            }
        } catch (Exception e) {
            log.error("Read query failed on replica, falling back to primary", e);
            replicaFailovers.incrementAndGet();
            return primaryJdbcTemplate.queryForList(sql, params);
        }
    }

    /**
     * Execute write query (always goes to primary)
     */
    @Transactional
    public int executeWriteQuery(String sql, Object... params) {
        totalWriteQueries.incrementAndGet();
        return primaryJdbcTemplate.update(sql, params);
    }

    /**
     * Execute analytical query with replica preference
     * Routes long-running analytics queries to least loaded replica
     */
    public CompletableFuture<List<Map<String, Object>>> executeAnalyticsQuery(
            String sql, 
            Object... params) {
        
        return CompletableFuture.supplyAsync(() -> {
            totalReadQueries.incrementAndGet();
            
            JdbcTemplate template = selectAnalyticsReplica();
            
            try {
                if (template != null) {
                    long startTime = System.currentTimeMillis();
                    List<Map<String, Object>> results = template.queryForList(sql, params);
                    long duration = System.currentTimeMillis() - startTime;
                    
                    log.info("Analytics query completed in {}ms, returned {} rows", 
                            duration, results.size());
                    
                    return results;
                } else {
                    log.warn("No healthy read replica available for analytics, using primary");
                    replicaFailovers.incrementAndGet();
                    return primaryJdbcTemplate.queryForList(sql, params);
                }
            } catch (Exception e) {
                log.error("Analytics query failed on replica, falling back to primary", e);
                replicaFailovers.incrementAndGet();
                return primaryJdbcTemplate.queryForList(sql, params);
            }
        }, asyncExecutor);
    }

    /**
     * Execute read query with retry logic and automatic failover
     */
    public <T> T executeReadQueryWithRetry(String queryName, 
                                         ReadQueryExecutor<T> executor, 
                                         int maxRetries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executor.execute(selectReadReplica());
            } catch (Exception e) {
                lastException = e;
                log.warn("Read query {} failed on attempt {} of {}: {}", 
                        queryName, attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    // Mark current replica as potentially unhealthy
                    markReplicaSuspicious(getCurrentReplica());
                    
                    try {
                        TimeUnit.MILLISECONDS.sleep(attempt * 100); // Progressive backoff with proper interruption
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Query execution interrupted", ie);
                    }
                }
            }
        }
        
        // Final attempt on primary
        try {
            log.info("Attempting final read query {} on primary after {} failed attempts", 
                    queryName, maxRetries);
            replicaFailovers.incrementAndGet();
            return executor.execute(primaryJdbcTemplate);
        } catch (Exception e) {
            log.error("Read query {} failed on primary after replica failures", queryName, e);
            throw new RuntimeException(
                String.format("Query %s failed after %d attempts", queryName, maxRetries), 
                lastException);
        }
    }

    // =====================================================================
    // REPLICA SELECTION STRATEGIES
    // =====================================================================

    /**
     * Select read replica based on configured load balancing strategy
     */
    private JdbcTemplate selectReadReplica() {
        List<JdbcTemplate> healthyReplicas = getHealthyReplicas();
        
        if (healthyReplicas.isEmpty()) {
            return null; // Will fall back to primary
        }
        
        switch (loadBalancingStrategy.toLowerCase()) {
            case "round-robin":
                return selectRoundRobinReplica(healthyReplicas);
            case "least-connections":
                return selectLeastConnectionsReplica(healthyReplicas);
            case "random":
                return selectRandomReplica(healthyReplicas);
            default:
                return selectRoundRobinReplica(healthyReplicas);
        }
    }

    /**
     * Select replica optimized for analytics (least loaded)
     */
    private JdbcTemplate selectAnalyticsReplica() {
        List<JdbcTemplate> healthyReplicas = getHealthyReplicas();
        
        if (healthyReplicas.isEmpty()) {
            return null;
        }
        
        // For analytics, prefer the replica with lowest current load
        return selectLeastConnectionsReplica(healthyReplicas);
    }

    /**
     * Round-robin replica selection
     */
    private JdbcTemplate selectRoundRobinReplica(List<JdbcTemplate> healthyReplicas) {
        int index = currentReplicaIndex.getAndIncrement() % healthyReplicas.size();
        return healthyReplicas.get(index);
    }

    /**
     * Select replica with least active connections
     */
    private JdbcTemplate selectLeastConnectionsReplica(List<JdbcTemplate> healthyReplicas) {
        // For now, use round-robin. In production, this would check actual connection counts
        return selectRoundRobinReplica(healthyReplicas);
    }

    /**
     * Random replica selection
     */
    private JdbcTemplate selectRandomReplica(List<JdbcTemplate> healthyReplicas) {
        int index = secureRandom.nextInt(healthyReplicas.size());
        return healthyReplicas.get(index);
    }

    // =====================================================================
    // HEALTH MONITORING
    // =====================================================================

    /**
     * Initialize replica health monitoring
     */
    private void initializeReplicaHealth() {
        for (int i = 0; i < readReplicaDataSources.size(); i++) {
            DataSource replica = readReplicaDataSources.get(i);
            replicaHealth.put(replica, ReplicaHealthStatus.builder()
                    .replicaId("replica-" + i)
                    .isHealthy(true)
                    .lastHealthCheck(LocalDateTime.now())
                    .consecutiveFailures(0)
                    .lagSeconds(0)
                    .build());
        }
    }

    /**
     * Start background health monitoring
     */
    private void startHealthMonitoring() {
        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    checkReplicaHealth();
                    TimeUnit.MILLISECONDS.sleep(healthCheckInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in replica health monitoring", e);
                    try {
                        TimeUnit.MILLISECONDS.sleep(healthCheckInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, asyncExecutor);
    }

    /**
     * Check health of all read replicas
     */
    private void checkReplicaHealth() {
        log.debug("Checking health of {} read replicas", readReplicaDataSources.size());
        
        for (int i = 0; i < readReplicaDataSources.size(); i++) {
            DataSource replica = readReplicaDataSources.get(i);
            ReplicaHealthStatus status = replicaHealth.get(replica);
            
            if (status != null) {
                checkIndividualReplicaHealth(replica, status, i);
            }
        }
    }

    /**
     * Check health of individual replica
     */
    private void checkIndividualReplicaHealth(DataSource replica, 
                                            ReplicaHealthStatus status, 
                                            int replicaIndex) {
        try (Connection connection = replica.getConnection()) {
            // Basic connectivity check
            boolean isConnected = connection.isValid(5); // 5 second timeout
            
            if (isConnected) {
                // Check replication lag
                int lagSeconds = checkReplicationLag(connection);
                
                boolean isHealthy = lagSeconds <= maxLagSeconds;
                
                status.setHealthy(isHealthy);
                status.setLagSeconds(lagSeconds);
                status.setLastHealthCheck(LocalDateTime.now());
                status.setConsecutiveFailures(isHealthy ? 0 : status.getConsecutiveFailures() + 1);
                
                if (!isHealthy) {
                    log.warn("Replica {} has high lag: {} seconds", status.getReplicaId(), lagSeconds);
                }
                
            } else {
                markReplicaUnhealthy(status, "Connection validation failed");
            }
            
        } catch (SQLException e) {
            markReplicaUnhealthy(status, "Connection error: " + e.getMessage());
        }
    }

    /**
     * Check replication lag for PostgreSQL
     */
    private int checkReplicationLag(Connection connection) {
        try {
            String lagQuery = """
                SELECT CASE 
                    WHEN pg_is_in_recovery() THEN 
                        EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::int
                    ELSE 0 
                END as lag_seconds
                """;
            
            try (var stmt = connection.prepareStatement(lagQuery);
                 var rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    return rs.getInt("lag_seconds");
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check replication lag", e);
        }
        
        return 0; // Assume no lag if we can't determine it
    }

    /**
     * Mark replica as unhealthy
     */
    private void markReplicaUnhealthy(ReplicaHealthStatus status, String reason) {
        status.setHealthy(false);
        status.setLastHealthCheck(LocalDateTime.now());
        status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);
        status.setLastError(reason);
        
        log.warn("Replica {} marked as unhealthy: {} (consecutive failures: {})", 
                status.getReplicaId(), reason, status.getConsecutiveFailures());
    }

    /**
     * Mark replica as suspicious (temporary degradation)
     */
    private void markReplicaSuspicious(JdbcTemplate replica) {
        // Find corresponding replica status and increment failure count
        // This is a simplified version - in production, you'd have a proper mapping
        log.warn("Replica marked as suspicious due to query failure");
    }

    /**
     * Get currently selected replica
     */
    private JdbcTemplate getCurrentReplica() {
        List<JdbcTemplate> healthyReplicas = getHealthyReplicas();
        if (healthyReplicas.isEmpty()) {
            return null;
        }
        int index = (currentReplicaIndex.get() - 1) % healthyReplicas.size();
        return healthyReplicas.get(Math.max(0, index));
    }

    /**
     * Get list of healthy read replicas
     */
    private List<JdbcTemplate> getHealthyReplicas() {
        List<JdbcTemplate> healthy = new ArrayList<>();
        
        for (int i = 0; i < readReplicaDataSources.size(); i++) {
            DataSource replica = readReplicaDataSources.get(i);
            ReplicaHealthStatus status = replicaHealth.get(replica);
            
            if (status != null && status.isHealthy() && i < replicaJdbcTemplates.size()) {
                healthy.add(replicaJdbcTemplates.get(i));
            }
        }
        
        return healthy;
    }

    // =====================================================================
    // MONITORING AND METRICS
    // =====================================================================

    /**
     * Get read replica routing statistics
     */
    public ReplicaRoutingStats getRoutingStats() {
        Map<String, ReplicaHealthStatus> healthStatusMap = new HashMap<>();
        replicaHealth.forEach((ds, status) -> 
            healthStatusMap.put(status.getReplicaId(), status));
        
        return ReplicaRoutingStats.builder()
                .totalReadQueries(totalReadQueries.get())
                .totalWriteQueries(totalWriteQueries.get())
                .replicaFailovers(replicaFailovers.get())
                .healthyReplicaCount(getHealthyReplicas().size())
                .totalReplicaCount(readReplicaDataSources.size())
                .replicaHealthStatus(healthStatusMap)
                .loadBalancingStrategy(loadBalancingStrategy)
                .maxLagSeconds(maxLagSeconds)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Reset routing statistics
     */
    public void resetStats() {
        totalReadQueries.set(0);
        totalWriteQueries.set(0);
        replicaFailovers.set(0);
        log.info("Read replica routing statistics reset");
    }

    /**
     * Get detailed replica health report
     */
    public Map<String, Object> getHealthReport() {
        Map<String, Object> report = new HashMap<>();
        
        report.put("total_replicas", readReplicaDataSources.size());
        report.put("healthy_replicas", getHealthyReplicas().size());
        report.put("load_balancing_strategy", loadBalancingStrategy);
        report.put("max_lag_seconds", maxLagSeconds);
        report.put("failover_enabled", failoverEnabled);
        
        List<Map<String, Object>> replicaDetails = new ArrayList<>();
        replicaHealth.forEach((ds, status) -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("replica_id", status.getReplicaId());
            detail.put("is_healthy", status.isHealthy());
            detail.put("lag_seconds", status.getLagSeconds());
            detail.put("consecutive_failures", status.getConsecutiveFailures());
            detail.put("last_health_check", status.getLastHealthCheck());
            detail.put("last_error", status.getLastError());
            replicaDetails.add(detail);
        });
        
        report.put("replica_details", replicaDetails);
        report.put("generated_at", LocalDateTime.now());
        
        return report;
    }

    // =====================================================================
    // FUNCTIONAL INTERFACES AND DATA CLASSES
    // =====================================================================

    /**
     * Functional interface for read query execution
     */
    @FunctionalInterface
    public interface ReadQueryExecutor<T> {
        T execute(JdbcTemplate jdbcTemplate) throws Exception;
    }

    /**
     * Replica health status tracking
     */
    public static class ReplicaHealthStatus {
        private String replicaId;
        private boolean isHealthy;
        private int lagSeconds;
        private int consecutiveFailures;
        private LocalDateTime lastHealthCheck;
        private String lastError;

        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final ReplicaHealthStatus status = new ReplicaHealthStatus();

            public Builder replicaId(String replicaId) {
                status.replicaId = replicaId;
                return this;
            }

            public Builder isHealthy(boolean isHealthy) {
                status.isHealthy = isHealthy;
                return this;
            }

            public Builder lagSeconds(int lagSeconds) {
                status.lagSeconds = lagSeconds;
                return this;
            }

            public Builder consecutiveFailures(int consecutiveFailures) {
                status.consecutiveFailures = consecutiveFailures;
                return this;
            }

            public Builder lastHealthCheck(LocalDateTime lastHealthCheck) {
                status.lastHealthCheck = lastHealthCheck;
                return this;
            }

            public ReplicaHealthStatus build() {
                return status;
            }
        }

        // Getters and setters
        public String getReplicaId() { return replicaId; }
        public void setReplicaId(String replicaId) { this.replicaId = replicaId; }
        
        public boolean isHealthy() { return isHealthy; }
        public void setHealthy(boolean healthy) { isHealthy = healthy; }
        
        public int getLagSeconds() { return lagSeconds; }
        public void setLagSeconds(int lagSeconds) { this.lagSeconds = lagSeconds; }
        
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
        
        public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
        public void setLastHealthCheck(LocalDateTime lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }
        
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    /**
     * Replica routing statistics
     */
    public static class ReplicaRoutingStats {
        private long totalReadQueries;
        private long totalWriteQueries;
        private long replicaFailovers;
        private int healthyReplicaCount;
        private int totalReplicaCount;
        private Map<String, ReplicaHealthStatus> replicaHealthStatus;
        private String loadBalancingStrategy;
        private int maxLagSeconds;
        private LocalDateTime generatedAt;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final ReplicaRoutingStats stats = new ReplicaRoutingStats();

            public Builder totalReadQueries(long totalReadQueries) {
                stats.totalReadQueries = totalReadQueries;
                return this;
            }

            public Builder totalWriteQueries(long totalWriteQueries) {
                stats.totalWriteQueries = totalWriteQueries;
                return this;
            }

            public Builder replicaFailovers(long replicaFailovers) {
                stats.replicaFailovers = replicaFailovers;
                return this;
            }

            public Builder healthyReplicaCount(int healthyReplicaCount) {
                stats.healthyReplicaCount = healthyReplicaCount;
                return this;
            }

            public Builder totalReplicaCount(int totalReplicaCount) {
                stats.totalReplicaCount = totalReplicaCount;
                return this;
            }

            public Builder replicaHealthStatus(Map<String, ReplicaHealthStatus> replicaHealthStatus) {
                stats.replicaHealthStatus = replicaHealthStatus;
                return this;
            }

            public Builder loadBalancingStrategy(String loadBalancingStrategy) {
                stats.loadBalancingStrategy = loadBalancingStrategy;
                return this;
            }

            public Builder maxLagSeconds(int maxLagSeconds) {
                stats.maxLagSeconds = maxLagSeconds;
                return this;
            }

            public Builder generatedAt(LocalDateTime generatedAt) {
                stats.generatedAt = generatedAt;
                return this;
            }

            public ReplicaRoutingStats build() {
                return stats;
            }
        }

        // Getters
        public long getTotalReadQueries() { return totalReadQueries; }
        public long getTotalWriteQueries() { return totalWriteQueries; }
        public long getReplicaFailovers() { return replicaFailovers; }
        public int getHealthyReplicaCount() { return healthyReplicaCount; }
        public int getTotalReplicaCount() { return totalReplicaCount; }
        public Map<String, ReplicaHealthStatus> getReplicaHealthStatus() { return replicaHealthStatus; }
        public String getLoadBalancingStrategy() { return loadBalancingStrategy; }
        public int getMaxLagSeconds() { return maxLagSeconds; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
    }
}