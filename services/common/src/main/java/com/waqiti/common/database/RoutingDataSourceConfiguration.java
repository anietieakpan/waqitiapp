package com.waqiti.common.database;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Routing DataSource Configuration for Read Replica Routing
 *
 * Configures two datasources:
 * 1. Primary (master) - for read-write operations
 * 2. Replica - for read-only operations
 *
 * And wraps them in a routing datasource that automatically
 * selects the appropriate one based on transaction type.
 *
 * Configuration in application.yml or application-database-optimized.yml:
 * <pre>
 * spring:
 *   datasource:
 *     url: jdbc:postgresql://pgbouncer:6432/waqiti_payments
 *     username: ${DB_USERNAME}
 *     password: ${DB_PASSWORD}
 *     hikari:
 *       maximum-pool-size: 20
 *
 *   datasource-replica:
 *     url: jdbc:postgresql://pgbouncer-read:6432/waqiti_payments
 *     username: ${DB_USERNAME}
 *     password: ${DB_PASSWORD}
 *     hikari:
 *       maximum-pool-size: 30
 *       read-only: true
 * </pre>
 */
@Slf4j
@Configuration
public class RoutingDataSourceConfiguration {

    /**
     * Primary datasource properties (from spring.datasource)
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Replica datasource properties (from spring.datasource-replica)
     */
    @Bean
    @ConfigurationProperties("spring.datasource-replica")
    public DataSourceProperties replicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Primary datasource (master database)
     *
     * Used for all write operations and read-write transactions
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource primaryDataSource() {
        log.info("=== Configuring PRIMARY DataSource ===");

        HikariDataSource dataSource = primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        dataSource.setPoolName("PrimaryPool");

        log.info("Primary DataSource configured: url={}, poolSize={}",
                dataSource.getJdbcUrl(), dataSource.getMaximumPoolSize());

        return dataSource;
    }

    /**
     * Replica datasource (read replica database)
     *
     * Used for read-only transactions and queries
     */
    @Bean
    @ConfigurationProperties("spring.datasource-replica.hikari")
    public DataSource replicaDataSource() {
        log.info("=== Configuring REPLICA DataSource ===");

        HikariDataSource dataSource = replicaDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        dataSource.setPoolName("ReplicaPool");
        dataSource.setReadOnly(true);  // Enforce read-only at connection level

        log.info("Replica DataSource configured: url={}, poolSize={}",
                dataSource.getJdbcUrl(), dataSource.getMaximumPoolSize());

        return dataSource;
    }

    /**
     * Routing datasource (automatically selects primary or replica)
     *
     * This is the datasource actually used by the application
     */
    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            @Qualifier("replicaDataSource") DataSource replicaDataSource) {

        log.info("=== Configuring ROUTING DataSource ===");

        ReadReplicaRoutingDataSource routingDataSource = new ReadReplicaRoutingDataSource();

        // Map datasource keys to actual datasources
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(ReadReplicaRoutingDataSource.DataSourceType.PRIMARY, primaryDataSource);
        targetDataSources.put(ReadReplicaRoutingDataSource.DataSourceType.REPLICA, replicaDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);  // Default to primary for safety

        routingDataSource.afterPropertiesSet();

        log.info("Routing DataSource configured successfully");
        log.info("  PRIMARY → {}", ((HikariDataSource) primaryDataSource).getJdbcUrl());
        log.info("  REPLICA → {}", ((HikariDataSource) replicaDataSource).getJdbcUrl());

        return routingDataSource;
    }
}
