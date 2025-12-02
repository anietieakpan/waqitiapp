package com.waqiti.ledger.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database Configuration
 * 
 * Configures database connections, connection pooling, and JPA settings
 * with optimizations for production workloads.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.waqiti.ledger.repository")
@EnableTransactionManagement
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Connection settings
        config.setJdbcUrl(databaseUrl);
        config.setUsername(databaseUsername);
        config.setPassword(databasePassword);
        config.setDriverClassName(driverClassName);
        
        // Pool settings for production
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Performance optimizations
        config.setAutoCommit(false);
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(1);
        
        // Connection pool name
        config.setPoolName("WaqitiLedgerCP");
        
        // Additional PostgreSQL-specific settings
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

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.waqiti.ledger.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        // JPA/Hibernate properties
        Properties properties = new Properties();
        
        // Database dialect
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        
        // Schema management
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", false);
        
        // Performance settings
        properties.put("hibernate.jdbc.batch_size", "25");
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");
        properties.put("hibernate.jdbc.batch_versioned_data", "true");
        properties.put("hibernate.generate_statistics", "false");
        
        // Connection settings
        properties.put("hibernate.connection.provider_disables_autocommit", "true");
        properties.put("hibernate.query.in_clause_parameter_padding", "true");
        properties.put("hibernate.query.fail_on_pagination_over_collection_fetch", "true");
        properties.put("hibernate.query.plan_cache_max_size", "4096");
        properties.put("hibernate.query.plan_parameter_metadata_max_size", "128");
        
        // Second-level cache (if Redis is available)
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.region.factory_class", 
                      "org.hibernate.cache.jcache.JCacheRegionFactory");
        
        // Timezone setting
        properties.put("hibernate.jdbc.time_zone", "UTC");
        
        // Connection handling
        properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION");
        
        em.setJpaProperties(properties);
        
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        
        // Transaction timeout (30 seconds)
        transactionManager.setDefaultTimeout(30);
        
        return transactionManager;
    }
}