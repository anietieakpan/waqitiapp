package com.waqiti.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Production-optimized database configuration with advanced connection pooling
 * Implements best practices for high-performance database connectivity
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

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${spring.application.name:waqiti-service}")
    private String applicationName;
    
    @Value("${database.pool.size.multiplier:2}")
    private int poolSizeMultiplier;
    
    @Value("${database.read-replica.enabled:false}")
    private boolean readReplicaEnabled;
    
    private final MeterRegistry meterRegistry;
    
    public DatabaseConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Creates an optimized HikariCP DataSource for production use with lazy connection proxy
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource hikariDataSource = createHikariDataSource();
        
        // Wrap with lazy connection proxy to optimize connection usage
        return new LazyConnectionDataSourceProxy(hikariDataSource);
    }
    
    /**
     * Creates a read-only data source for read replicas
     */
    @Bean(name = "readOnlyDataSource")
    @ConditionalOnProperty(name = "database.read-replica.enabled", havingValue = "true")
    public DataSource readOnlyDataSource(
            @Value("${database.read-replica.url}") String replicaUrl,
            @Value("${database.read-replica.username:${spring.datasource.username}}") String replicaUsername,
            @Value("${database.read-replica.password:${spring.datasource.password}}") String replicaPassword) {
        
        log.info("Configuring read-replica connection pool for {}", applicationName);
        
        HikariConfig config = createBaseHikariConfig();
        config.setJdbcUrl(replicaUrl);
        config.setUsername(replicaUsername);
        config.setPassword(replicaPassword);
        config.setPoolName(applicationName + "-read-replica-pool");
        config.setReadOnly(true);
        
        // Smaller pool for read replicas
        int coreCount = Runtime.getRuntime().availableProcessors();
        config.setMaximumPoolSize(Math.max(10, coreCount * 2));
        config.setMinimumIdle(Math.max(2, coreCount / 2));
        
        return new LazyConnectionDataSourceProxy(new HikariDataSource(config));
    }
    
    private HikariDataSource createHikariDataSource() {
        log.info("Configuring optimized HikariCP connection pool for {}", applicationName);
        
        HikariConfig config = createBaseHikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(applicationName + "-primary-pool");
        
        // Dynamic pool sizing based on CPU cores
        int optimalPoolSize = calculateOptimalPoolSize();
        config.setMaximumPoolSize(optimalPoolSize);
        config.setMinimumIdle(Math.max(5, optimalPoolSize / 4));
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        log.info("HikariCP connection pool configured: maxPoolSize={}, minIdle={}, connectionTimeout={}ms", 
            config.getMaximumPoolSize(), config.getMinimumIdle(), config.getConnectionTimeout());
        
        return dataSource;
    }
    
    private HikariConfig createBaseHikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Driver configuration
        config.setDriverClassName(driverClassName);
        
        // Production-optimized timeouts
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setValidationTimeout(5000); // 5 seconds
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Connection initialization
        config.setAutoCommit(false);
        config.setConnectionInitSql("SET TIME ZONE 'UTC'");
        config.setConnectionTestQuery("SELECT 1");
        
        // PostgreSQL optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("ApplicationName", applicationName);
        
        // Enable JMX monitoring
        config.setRegisterMbeans(true);
        
        // Configure Micrometer metrics if available
        if (meterRegistry != null) {
            config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
        }
        
        return config;
    }
    
    private int calculateOptimalPoolSize() {
        // Formula: connections = ((core_count * 2) + effective_spindle_count)
        int coreCount = Runtime.getRuntime().availableProcessors();
        int calculatedSize = (coreCount * poolSizeMultiplier) + 1;
        
        // Add slight randomness to prevent thundering herd
        calculatedSize += ThreadLocalRandom.current().nextInt(0, 3);
        
        // Apply reasonable bounds for production
        int minSize = 10;
        int maxSize = 30;
        
        return Math.min(Math.max(calculatedSize, minSize), maxSize);
    }
}