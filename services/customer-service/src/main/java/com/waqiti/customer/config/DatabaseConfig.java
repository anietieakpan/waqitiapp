package com.waqiti.customer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database Configuration for Customer Service.
 * Configures HikariCP connection pool with optimized settings
 * for high-performance database operations.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.connection-test-query:SELECT 1}")
    private String connectionTestQuery;

    @Value("${spring.datasource.hikari.pool-name:CustomerServiceHikariCP}")
    private String poolName;

    @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
    private long leakDetectionThreshold;

    /**
     * Configures HikariCP DataSource for production environment.
     * Optimized for high-throughput transactional workloads.
     *
     * @return Configured DataSource
     */
    @Bean
    @Profile("prod")
    public DataSource productionDataSource() {
        HikariConfig config = createBaseHikariConfig();

        // Production-specific optimizations
        config.setMaximumPoolSize(30);
        config.setMinimumIdle(10);
        config.setLeakDetectionThreshold(30000);
        config.setConnectionTimeout(20000);

        // Enable statement caching
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

        log.info("Production DataSource configured with HikariCP: pool={}, maxPoolSize={}, minIdle={}",
            poolName, 30, 10);

        return new HikariDataSource(config);
    }

    /**
     * Configures HikariCP DataSource for development environment.
     * Includes additional logging and debugging features.
     *
     * @return Configured DataSource
     */
    @Bean
    @Profile("dev")
    public DataSource developmentDataSource() {
        HikariConfig config = createBaseHikariConfig();

        // Development-specific settings
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setLeakDetectionThreshold(leakDetectionThreshold);

        // Enable additional logging in development
        config.setRegisterMbeans(true);

        log.info("Development DataSource configured with HikariCP: pool={}, maxPoolSize={}, minIdle={}",
            poolName, maximumPoolSize, minimumIdle);

        return new HikariDataSource(config);
    }

    /**
     * Creates base HikariCP configuration shared across all profiles.
     *
     * @return Base HikariConfig
     */
    private HikariConfig createBaseHikariConfig() {
        HikariConfig config = new HikariConfig();

        // JDBC connection settings
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // Pool configuration
        config.setPoolName(poolName);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setConnectionTestQuery(connectionTestQuery);

        // Connection health check
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(1);

        // Transaction and auto-commit
        config.setAutoCommit(false);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

        // Connection leak detection
        config.setLeakDetectionThreshold(leakDetectionThreshold);

        // Allow pool suspension
        config.setAllowPoolSuspension(true);

        return config;
    }

    /**
     * Provides additional Hibernate properties for optimization.
     *
     * @return Hibernate properties
     */
    @Bean
    public Properties hibernateProperties() {
        Properties properties = new Properties();

        // Hibernate batch processing
        properties.setProperty("hibernate.jdbc.batch_size", "20");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");

        // Query optimization
        properties.setProperty("hibernate.query.in_clause_parameter_padding", "true");
        properties.setProperty("hibernate.query.fail_on_pagination_over_collection_fetch", "true");

        // Connection handling
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");

        // Statistics (enable in dev, disable in prod)
        properties.setProperty("hibernate.generate_statistics", "false");

        // Second-level cache (disabled as we use Redis)
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");

        log.info("Hibernate properties configured for batch processing and query optimization");
        return properties;
    }
}
