package com.waqiti.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database optimization configuration for production environments
 * Implements connection pooling, read replicas, and performance tuning
 */
@Configuration
@EnableCaching
@EnableJpaRepositories(basePackages = "com.waqiti")
@Profile({"production", "staging"})
public class DatabaseOptimizationConfig {

    @Value("${spring.datasource.url}")
    private String primaryDbUrl;

    @Value("${spring.datasource.read-replica.url:#{null}}")
    private String readReplicaUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    /**
     * Primary database connection pool (for writes and consistent reads)
     */
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(primaryDbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Connection pool optimization
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute

        // Performance tuning
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

        // PostgreSQL specific optimizations
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("loginTimeout", "10");

        return new HikariDataSource(config);
    }

    /**
     * Read replica data source (for read-only operations)
     */
    @Bean(name = "readOnlyDataSource")
    public DataSource readOnlyDataSource() {
        if (readReplicaUrl == null) {
            return primaryDataSource(); // Fallback to primary if no replica
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(readReplicaUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Read replica pool configuration (can be larger for reads)
        config.setMaximumPoolSize(80);
        config.setMinimumIdle(20);
        config.setConnectionTimeout(20000); // Faster timeout for reads
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(1200000); // 20 minutes

        // Read-only optimizations
        config.setReadOnly(true);
        config.addDataSourceProperty("defaultAutoCommit", "true");
        config.addDataSourceProperty("readOnly", "true");

        // Same performance tuning as primary
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500"); // Larger cache for reads
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(config);
    }

    /**
     * JPA Entity Manager Factory with Hibernate optimizations
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(primaryDataSource());
        em.setPackagesToScan("com.waqiti");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(hibernateProperties());

        return em;
    }

    /**
     * Hibernate properties for performance optimization
     */
    private Properties hibernateProperties() {
        Properties properties = new Properties();

        // Basic Hibernate configuration
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");

        // Performance optimizations
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.jdbc.fetch_size", "100");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.batch_versioned_data", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        
        // N+1 Query Prevention
        properties.setProperty("hibernate.default_batch_fetch_size", "16");
        properties.setProperty("hibernate.max_fetch_depth", "3");
        properties.setProperty("hibernate.batch_fetch_style", "DYNAMIC");
        
        // Query optimization
        properties.setProperty("hibernate.query.in_clause_parameter_padding", "true");
        properties.setProperty("hibernate.query.plan_cache_max_size", "2048");
        properties.setProperty("hibernate.query.plan_parameter_metadata_max_size", "128");

        // Second level cache configuration (Redis-backed)
        properties.setProperty("hibernate.cache.use_second_level_cache", "true");
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class", 
            "org.redisson.hibernate.RedissonRegionFactory");
        properties.setProperty("hibernate.cache.redisson.config", "redisson.yaml");

        // Connection provider optimizations
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        properties.setProperty("hibernate.connection.autocommit", "false");

        // Statistics and monitoring
        properties.setProperty("hibernate.generate_statistics", "true");
        properties.setProperty("hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS", "100");

        // Lazy loading optimization
        properties.setProperty("hibernate.enable_lazy_load_no_trans", "false");
        properties.setProperty("hibernate.bytecode.use_reflection_optimizer", "true");

        return properties;
    }

    /**
     * Transaction manager
     */
    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        transactionManager.setNestedTransactionAllowed(true);
        transactionManager.setValidateExistingTransaction(true);
        return transactionManager;
    }
    
    /**
     * Hibernate properties customizer for additional optimizations
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            // Additional batch fetching for collections
            hibernateProperties.put("hibernate.batch_fetch_style", "PADDED");
            hibernateProperties.put("hibernate.default_batch_fetch_size", "25");
            
            // Enable subselect fetching for large collections
            hibernateProperties.put("hibernate.use_subselect_fetch", "true");
            
            // Query hints for better performance
            hibernateProperties.put("hibernate.query.fail_on_pagination_over_collection_fetch", "true");
            
            // Enable query result caching
            hibernateProperties.put("hibernate.cache.use_minimal_puts", "true");
            hibernateProperties.put("hibernate.cache.use_structured_entries", "false");
        };
    }
}