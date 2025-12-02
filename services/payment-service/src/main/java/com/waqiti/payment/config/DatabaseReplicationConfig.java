package com.waqiti.payment.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready database configuration with read replica routing.
 * Automatically routes read-only queries to read replicas for better performance and availability.
 */
@Configuration
public class DatabaseReplicationConfig {

    @Value("${spring.datasource.primary.url}")
    private String primaryUrl;

    @Value("${spring.datasource.primary.username}")
    private String primaryUsername;

    @Value("${spring.datasource.primary.password}")
    private String primaryPassword;

    @Value("${spring.datasource.read-replica.url}")
    private String readReplicaUrl;

    @Value("${spring.datasource.read-replica.username}")
    private String readReplicaUsername;

    @Value("${spring.datasource.read-replica.password}")
    private String readReplicaPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:30}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:10}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    /**
     * Primary (write) database configuration
     */
    @Bean
    public DataSource primaryDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(primaryUrl);
        config.setUsername(primaryUsername);
        config.setPassword(primaryPassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Production optimizations
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        
        // Performance optimizations
        config.setAutoCommit(false);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("Payment-Primary-Pool");
        
        // PostgreSQL specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return new HikariDataSource(config);
    }

    /**
     * Read replica database configuration
     */
    @Bean
    public DataSource readReplicaDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(readReplicaUrl != null ? readReplicaUrl : primaryUrl); // Fallback to primary if no replica
        config.setUsername(readReplicaUsername != null ? readReplicaUsername : primaryUsername);
        config.setPassword(readReplicaPassword != null ? readReplicaPassword : primaryPassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Read replicas can have higher pool size for read-heavy workloads
        config.setMaximumPoolSize(maxPoolSize * 2);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        
        config.setAutoCommit(false);
        config.setReadOnly(true);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("Payment-ReadReplica-Pool");
        
        // PostgreSQL specific optimizations for read-only
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500"); // Higher cache for reads
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return new HikariDataSource(config);
    }

    /**
     * Routing data source that switches between primary and read replica
     */
    @Bean
    public DataSource routingDataSource() {
        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primaryDataSource());
        targetDataSources.put(DataSourceType.READ_REPLICA, readReplicaDataSource());
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        
        return routingDataSource;
    }

    /**
     * Lazy connection data source proxy for transaction routing
     */
    @Primary
    @Bean
    public DataSource dataSource() {
        return new LazyConnectionDataSourceProxy(routingDataSource());
    }

    /**
     * Custom routing data source implementation
     */
    public static class ReplicationRoutingDataSource extends AbstractRoutingDataSource {
        
        @Override
        protected Object determineCurrentLookupKey() {
            boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            
            if (isReadOnly) {
                return DataSourceType.READ_REPLICA;
            } else {
                return DataSourceType.PRIMARY;
            }
        }
    }

    /**
     * Data source types
     */
    public enum DataSourceType {
        PRIMARY,
        READ_REPLICA
    }

    /**
     * Health check for database connections
     */
    @Bean
    public DatabaseHealthIndicator databaseHealthIndicator() {
        return new DatabaseHealthIndicator(primaryDataSource(), readReplicaDataSource());
    }

    /**
     * Database health indicator for monitoring
     */
    public static class DatabaseHealthIndicator {
        private final DataSource primaryDataSource;
        private final DataSource readReplicaDataSource;

        public DatabaseHealthIndicator(DataSource primaryDataSource, DataSource readReplicaDataSource) {
            this.primaryDataSource = primaryDataSource;
            this.readReplicaDataSource = readReplicaDataSource;
        }

        public boolean isPrimaryHealthy() {
            try (var connection = primaryDataSource.getConnection()) {
                return connection.isValid(5);
            } catch (Exception e) {
                return false;
            }
        }

        public boolean isReadReplicaHealthy() {
            try (var connection = readReplicaDataSource.getConnection()) {
                return connection.isValid(5);
            } catch (Exception e) {
                return false;
            }
        }
    }
}