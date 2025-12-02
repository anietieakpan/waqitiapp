package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for database data sources
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfig {
    private String name;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    
    // Connection pool settings
    private int minIdle;
    private int maxPoolSize;
    private long connectionTimeoutMs;
    private long idleTimeoutMs;
    private long maxLifetimeMs;
    private long validationTimeoutMs;
    private long leakDetectionThresholdMs;
    
    // Database-specific settings
    private String schema;
    private boolean readOnly;
    private String poolName;
    private int preparedStatementCacheSize;
    private boolean cachePreparedStatements;
    
    // Advanced settings
    private Map<String, String> connectionProperties;
    private String validationQuery;
    private boolean testOnBorrow;
    private boolean testOnReturn;
    private boolean testWhileIdle;
    
    // Monitoring settings
    private boolean enableMetrics;
    private boolean enableHealthCheck;
    private String healthCheckQuery;
    
    public static DataSourceConfig createDefault(String name) {
        return DataSourceConfig.builder()
            .name(name)
            .minIdle(5)
            .maxPoolSize(20)
            .connectionTimeoutMs(30000)
            .idleTimeoutMs(600000)
            .maxLifetimeMs(1800000)
            .validationTimeoutMs(5000)
            .leakDetectionThresholdMs(60000)
            .readOnly(false)
            .cachePreparedStatements(true)
            .preparedStatementCacheSize(250)
            .validationQuery("SELECT 1")
            .testOnBorrow(true)
            .testOnReturn(false)
            .testWhileIdle(true)
            .enableMetrics(true)
            .enableHealthCheck(true)
            .healthCheckQuery("SELECT 1")
            .build();
    }
    
    public static DataSourceConfig createReadOnlyConfig(String name) {
        DataSourceConfig config = createDefault(name);
        config.setReadOnly(true);
        config.setMinIdle(3);
        config.setMaxPoolSize(15);
        return config;
    }
    
    public static DataSourceConfig createAnalyticsConfig(String name) {
        DataSourceConfig config = createDefault(name);
        config.setReadOnly(true);
        config.setMinIdle(2);
        config.setMaxPoolSize(10);
        config.setConnectionTimeoutMs(300000);
        config.setIdleTimeoutMs(1800000);
        config.setMaxLifetimeMs(3600000);
        return config;
    }
    
    public static DataSourceConfig defaultWriteConfig() {
        return DataSourceConfig.builder()
            .name("primary-write")
            .driverClassName("org.postgresql.Driver")
            .minIdle(5)
            .maxPoolSize(30)
            .connectionTimeoutMs(30000)
            .idleTimeoutMs(600000)
            .maxLifetimeMs(1800000)
            .validationTimeoutMs(5000)
            .leakDetectionThresholdMs(60000)
            .readOnly(false)
            .cachePreparedStatements(true)
            .preparedStatementCacheSize(250)
            .validationQuery("SELECT 1")
            .testOnBorrow(true)
            .testWhileIdle(true)
            .enableMetrics(true)
            .enableHealthCheck(true)
            .build();
    }
    
    public static DataSourceConfig defaultReadConfig() {
        return DataSourceConfig.builder()
            .name("primary-read")
            .driverClassName("org.postgresql.Driver")
            .minIdle(10)
            .maxPoolSize(50)
            .connectionTimeoutMs(30000)
            .idleTimeoutMs(600000)
            .maxLifetimeMs(1800000)
            .validationTimeoutMs(5000)
            .leakDetectionThresholdMs(60000)
            .readOnly(true)
            .cachePreparedStatements(true)
            .preparedStatementCacheSize(250)
            .validationQuery("SELECT 1")
            .testOnBorrow(true)
            .testWhileIdle(true)
            .enableMetrics(true)
            .enableHealthCheck(true)
            .build();
    }
    
    public static DataSourceConfig defaultReadReplicaConfig() {
        return DataSourceConfig.builder()
            .name("read-replica")
            .driverClassName("org.postgresql.Driver")
            .minIdle(5)
            .maxPoolSize(25)
            .connectionTimeoutMs(30000)
            .idleTimeoutMs(600000)
            .maxLifetimeMs(1800000)
            .validationTimeoutMs(5000)
            .leakDetectionThresholdMs(60000)
            .readOnly(true)
            .cachePreparedStatements(true)
            .preparedStatementCacheSize(250)
            .validationQuery("SELECT 1")
            .testOnBorrow(true)
            .testWhileIdle(true)
            .enableMetrics(true)
            .enableHealthCheck(true)
            .build();
    }
}