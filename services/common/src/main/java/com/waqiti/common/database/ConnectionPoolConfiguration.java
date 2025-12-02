package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Duration;

/**
 * Represents database connection pool configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolConfiguration {
    private String poolName;
    private int minPoolSize;
    private int maxPoolSize;
    private int corePoolSize;
    private int queueCapacity;
    private int connectionTimeoutMs;
    private int idleTimeoutMs;
    private int maxLifetimeMs;
    private int validationTimeoutMs;
    private String validationQuery;
    private boolean testOnBorrow;
    private boolean testOnReturn;
    private boolean testWhileIdle;
    private int timeBetweenEvictionRunsMs;
    private int numTestsPerEvictionRun;
    private Duration connectionTimeout;
    private Duration idleTimeout;
    
    // Additional fields for IntelligentConnectionPool
    private DataSourceConfig primaryWriteConfig;
    private DataSourceConfig primaryReadConfig;
    private DataSourceConfig readReplicaConfig;
    private int readReplicaCount;
    
    public int getCorePoolSize() {
        return corePoolSize > 0 ? corePoolSize : Math.max(1, minPoolSize);
    }
    
    public int getQueueCapacity() {
        return queueCapacity > 0 ? queueCapacity : 100;
    }
    
    public static ConnectionPoolConfiguration createDefault() {
        return ConnectionPoolConfiguration.builder()
            .poolName("default")
            .minPoolSize(5)
            .maxPoolSize(20)
            .corePoolSize(10)
            .queueCapacity(100)
            .connectionTimeoutMs(30000)
            .idleTimeoutMs(600000)
            .maxLifetimeMs(1800000)
            .validationTimeoutMs(5000)
            .validationQuery("SELECT 1")
            .testOnBorrow(true)
            .testOnReturn(false)
            .testWhileIdle(true)
            .timeBetweenEvictionRunsMs(30000)
            .numTestsPerEvictionRun(3)
            .connectionTimeout(Duration.ofSeconds(30))
            .idleTimeout(Duration.ofMinutes(10))
            .primaryWriteConfig(DataSourceConfig.defaultWriteConfig())
            .primaryReadConfig(DataSourceConfig.defaultReadConfig())
            .readReplicaConfig(DataSourceConfig.defaultReadReplicaConfig())
            .readReplicaCount(2)
            .build();
    }
    
    public static class ConnectionPoolConfigurationBuilder {
        public ConnectionPoolConfigurationBuilder keepAliveSeconds(int seconds) {
            this.idleTimeoutMs = seconds * 1000;
            return this;
        }
    }
}