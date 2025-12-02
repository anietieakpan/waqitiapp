package com.waqiti.common.resilience;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Optimized connection pooling with adaptive sizing and monitoring
 */
@Configuration
@Slf4j
public class OptimizedConnectionPooling {
    
    // Shared scheduled executor for resilience4j operations
    private static final ScheduledExecutorService RESILIENCE_SCHEDULER = 
        Executors.newScheduledThreadPool(10, r -> {
            Thread thread = new Thread(r);
            thread.setName("resilience4j-scheduler-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    
    @Value("${datasource.primary.url}")
    private String primaryUrl;
    
    @Value("${datasource.primary.username}")
    private String primaryUsername;
    
    @Value("${datasource.primary.password}")
    private String primaryPassword;
    
    @Value("${datasource.readonly.url}")
    private String readonlyUrl;
    
    @Value("${datasource.readonly.username}")
    private String readonlyUsername;
    
    @Value("${datasource.readonly.password}")
    private String readonlyPassword;
    
    private final Map<String, AdaptiveConnectionPool> connectionPools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitoringExecutor = Executors.newScheduledThreadPool(2);
    
    /**
     * Primary database connection pool
     */
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        AdaptiveConnectionPool pool = createAdaptivePool("primary", primaryUrl, 
            primaryUsername, primaryPassword, true);
        connectionPools.put("primary", pool);
        return pool.getDataSource();
    }
    
    /**
     * Read-only database connection pool
     */
    @Bean(name = "readonlyDataSource")
    public DataSource readonlyDataSource() {
        AdaptiveConnectionPool pool = createAdaptivePool("readonly", readonlyUrl, 
            readonlyUsername, readonlyPassword, false);
        connectionPools.put("readonly", pool);
        return pool.getDataSource();
    }
    
    /**
     * Circuit breaker registry
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .recordExceptions(Exception.class)
            .build();
        
        return CircuitBreakerRegistry.of(defaultConfig);
    }
    
    /**
     * Adaptive circuit breaker manager
     */
    @Bean
    public AdaptiveCircuitBreakerManager adaptiveCircuitBreakerManager() {
        return new AdaptiveCircuitBreakerManager(circuitBreakerRegistry());
    }
    
    /**
     * Create adaptive connection pool
     */
    private AdaptiveConnectionPool createAdaptivePool(String poolName, String url, 
                                                     String username, String password, 
                                                     boolean isWritePool) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Pool sizing based on pool type
        if (isWritePool) {
            config.setMinimumIdle(5);
            config.setMaximumPoolSize(30);
        } else {
            config.setMinimumIdle(10);
            config.setMaximumPoolSize(50);
        }
        
        // Connection management
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setValidationTimeout(5000);
        config.setLeakDetectionThreshold(60000);
        
        // Performance optimizations
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
        
        // Health check
        config.setConnectionTestQuery("SELECT 1");
        // Health check registry is now configured differently in HikariCP
        // config.setHealthCheckRegistry(...);
        
        config.setPoolName(poolName + "-pool");
        config.setRegisterMbeans(true);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        return new AdaptiveConnectionPool(poolName, dataSource, isWritePool);
    }
    
    /**
     * Monitor and adjust connection pools
     */
    @Scheduled(fixedDelay = 60000)
    public void monitorAndAdjustPools() {
        connectionPools.values().forEach(this::adjustPoolSize);
    }
    
    private void adjustPoolSize(AdaptiveConnectionPool pool) {
        HikariPoolMXBean poolMBean = pool.getDataSource().getHikariPoolMXBean();
        
        int activeConnections = poolMBean.getActiveConnections();
        int idleConnections = poolMBean.getIdleConnections();
        int totalConnections = poolMBean.getTotalConnections();
        int maxPoolSize = pool.getDataSource().getMaximumPoolSize();
        
        double utilization = (double) activeConnections / totalConnections;
        
        log.debug("Pool {} - Active: {}, Idle: {}, Total: {}, Max: {}, Utilization: {:.2f}%",
            pool.getPoolName(), activeConnections, idleConnections, totalConnections, 
            maxPoolSize, utilization * 100);
        
        // Adjust pool size based on utilization
        if (utilization > 0.8 && totalConnections < maxPoolSize * 0.9) {
            // Increase pool size if high utilization
            int newSize = Math.min(maxPoolSize, totalConnections + 5);
            pool.getDataSource().setMaximumPoolSize(newSize);
            log.info("Increased pool {} size to {}", pool.getPoolName(), newSize);
        } else if (utilization < 0.3 && totalConnections > pool.getMinimumPoolSize()) {
            // Decrease pool size if low utilization
            int newSize = Math.max(pool.getMinimumPoolSize(), totalConnections - 2);
            pool.getDataSource().setMaximumPoolSize(newSize);
            log.info("Decreased pool {} size to {}", pool.getPoolName(), newSize);
        }
        
        // Check for connection leaks
        if (poolMBean.getTotalConnections() == maxPoolSize && 
            poolMBean.getActiveConnections() > maxPoolSize * 0.9) {
            log.warn("Potential connection leak detected in pool {}", pool.getPoolName());
        }
    }
    
    /**
     * Adaptive connection pool wrapper
     */
    public static class AdaptiveConnectionPool {
        private final String poolName;
        private final HikariDataSource dataSource;
        private final boolean isWritePool;
        private final int minimumPoolSize;
        
        public AdaptiveConnectionPool(String poolName, HikariDataSource dataSource, boolean isWritePool) {
            this.poolName = poolName;
            this.dataSource = dataSource;
            this.isWritePool = isWritePool;
            this.minimumPoolSize = dataSource.getMinimumIdle();
        }
        
        public String getPoolName() { return poolName; }
        public HikariDataSource getDataSource() { return dataSource; }
        public boolean isWritePool() { return isWritePool; }
        public int getMinimumPoolSize() { return minimumPoolSize; }
    }
    
    /**
     * Adaptive circuit breaker manager
     */
    public static class AdaptiveCircuitBreakerManager {
        private final CircuitBreakerRegistry registry;
        private final Map<String, CircuitBreakerConfig> serviceConfigs = new ConcurrentHashMap<>();
        
        public AdaptiveCircuitBreakerManager(CircuitBreakerRegistry registry) {
            this.registry = registry;
            initializeServiceConfigs();
        }
        
        private void initializeServiceConfigs() {
            // High-priority services - stricter thresholds
            serviceConfigs.put("payment-service", CircuitBreakerConfig.custom()
                .failureRateThreshold(30)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallRateThreshold(30)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .build());
            
            // Medium-priority services - balanced thresholds
            serviceConfigs.put("user-service", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(15)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .build());
            
            // Low-priority services - relaxed thresholds
            serviceConfigs.put("notification-service", CircuitBreakerConfig.custom()
                .failureRateThreshold(70)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .permittedNumberOfCallsInHalfOpenState(2)
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .build());
        }
        
        public CircuitBreaker getCircuitBreaker(String serviceName) {
            CircuitBreakerConfig config = serviceConfigs.getOrDefault(serviceName, 
                registry.getDefaultConfig());
            return registry.circuitBreaker(serviceName, config);
        }
        
        /**
         * Execute with circuit breaker protection
         */
        public <T> CompletableFuture<T> executeAsync(String serviceName, Supplier<T> supplier) {
            CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
            
            // Add retry capability
            RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .retryOnResult(result -> result == null)
                .build();
            
            Retry retry = Retry.of(serviceName + "-retry", retryConfig);
            
            // Add timeout capability
            TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build();
            
            TimeLimiter timeLimiter = TimeLimiter.of(timeLimiterConfig);
            
            // Create the supplier that will be decorated
            Supplier<CompletionStage<T>> futureSupplier = () -> 
                CompletableFuture.supplyAsync(supplier);
            
            // Decorate with circuit breaker first
            Supplier<CompletionStage<T>> circuitBreakerSupplier = () ->
                circuitBreaker.executeCompletionStage(futureSupplier);
            
            // Then decorate with retry (requires ScheduledExecutorService)
            Supplier<CompletionStage<T>> retrySupplier = () ->
                retry.executeCompletionStage(RESILIENCE_SCHEDULER, circuitBreakerSupplier);
            
            // Finally apply time limiter
            return timeLimiter.executeCompletionStage(RESILIENCE_SCHEDULER, retrySupplier)
                .toCompletableFuture();
        }
        
        /**
         * Get circuit breaker metrics
         */
        public CircuitBreakerMetrics getMetrics(String serviceName) {
            CircuitBreaker cb = registry.circuitBreaker(serviceName);
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            
            return new CircuitBreakerMetrics(
                serviceName,
                cb.getState().name(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(),
                metrics.getNumberOfNotPermittedCalls()
            );
        }
    }
    
    /**
     * Circuit breaker metrics
     */
    public static class CircuitBreakerMetrics {
        private final String serviceName;
        private final String state;
        private final float failureRate;
        private final float slowCallRate;
        private final long successfulCalls;
        private final long failedCalls;
        private final long slowCalls;
        private final long notPermittedCalls;
        
        public CircuitBreakerMetrics(String serviceName, String state, float failureRate,
                                   float slowCallRate, long successfulCalls, long failedCalls,
                                   long slowCalls, long notPermittedCalls) {
            this.serviceName = serviceName;
            this.state = state;
            this.failureRate = failureRate;
            this.slowCallRate = slowCallRate;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.slowCalls = slowCalls;
            this.notPermittedCalls = notPermittedCalls;
        }
        
        // Getters
        public String getServiceName() { return serviceName; }
        public String getState() { return state; }
        public float getFailureRate() { return failureRate; }
        public float getSlowCallRate() { return slowCallRate; }
        public long getSuccessfulCalls() { return successfulCalls; }
        public long getFailedCalls() { return failedCalls; }
        public long getSlowCalls() { return slowCalls; }
        public long getNotPermittedCalls() { return notPermittedCalls; }
    }
    
    /**
     * Connection pool health monitor
     */
    @Bean
    public ConnectionPoolHealthMonitor connectionPoolHealthMonitor() {
        return new ConnectionPoolHealthMonitor(connectionPools);
    }
    
    public static class ConnectionPoolHealthMonitor {
        private final Map<String, AdaptiveConnectionPool> pools;
        
        public ConnectionPoolHealthMonitor(Map<String, AdaptiveConnectionPool> pools) {
            this.pools = pools;
        }
        
        @Scheduled(fixedDelay = 30000)
        public void checkPoolHealth() {
            pools.forEach((name, pool) -> {
                try {
                    HikariPoolMXBean mBean = pool.getDataSource().getHikariPoolMXBean();
                    
                    // Check for unhealthy conditions
                    if (mBean.getActiveConnections() == pool.getDataSource().getMaximumPoolSize()) {
                        log.warn("Connection pool {} is at maximum capacity", name);
                    }
                    
                    if (mBean.getIdleConnections() == 0 && mBean.getActiveConnections() > 0) {
                        log.warn("Connection pool {} has no idle connections", name);
                    }
                    
                    // Log health status
                    log.debug("Pool {} health - Active: {}, Idle: {}, Total: {}, Max: {}",
                        name, mBean.getActiveConnections(), mBean.getIdleConnections(),
                        mBean.getTotalConnections(), pool.getDataSource().getMaximumPoolSize());
                        
                } catch (Exception e) {
                    log.error("Failed to check health of pool {}", name, e);
                }
            });
        }
    }
    
    /**
     * Register MBeans for monitoring
     */
    @Bean
    public ConnectionPoolMBeanRegistrar mBeanRegistrar() {
        return new ConnectionPoolMBeanRegistrar(connectionPools);
    }
    
    @Slf4j
    public static class ConnectionPoolMBeanRegistrar {
        public ConnectionPoolMBeanRegistrar(Map<String, AdaptiveConnectionPool> pools) {
            registerMBeans(pools);
        }
        
        private void registerMBeans(Map<String, AdaptiveConnectionPool> pools) {
            try {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                
                pools.forEach((name, pool) -> {
                    try {
                        ObjectName objectName = new ObjectName(
                            "com.waqiti:type=ConnectionPool,name=" + name);
                        
                        ConnectionPoolMBean mBean = new ConnectionPoolMBean(pool);
                        mbs.registerMBean(mBean, objectName);
                        
                        log.info("Registered MBean for connection pool: {}", name);
                    } catch (Exception e) {
                        log.error("Failed to register MBean for pool {}", name, e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to register connection pool MBeans", e);
            }
        }
    }
    
    /**
     * MBean interface for connection pool monitoring
     */
    public interface ConnectionPoolMXBean {
        int getActiveConnections();
        int getIdleConnections();
        int getTotalConnections();
        int getMaximumPoolSize();
        double getUtilization();
        void resizePool(int newSize);
        void evictIdleConnections();
    }
    
    @Slf4j
    public static class ConnectionPoolMBean implements ConnectionPoolMXBean {
        private final AdaptiveConnectionPool pool;
        
        public ConnectionPoolMBean(AdaptiveConnectionPool pool) {
            this.pool = pool;
        }
        
        @Override
        public int getActiveConnections() {
            return pool.getDataSource().getHikariPoolMXBean().getActiveConnections();
        }
        
        @Override
        public int getIdleConnections() {
            return pool.getDataSource().getHikariPoolMXBean().getIdleConnections();
        }
        
        @Override
        public int getTotalConnections() {
            return pool.getDataSource().getHikariPoolMXBean().getTotalConnections();
        }
        
        @Override
        public int getMaximumPoolSize() {
            return pool.getDataSource().getMaximumPoolSize();
        }
        
        @Override
        public double getUtilization() {
            int active = getActiveConnections();
            int total = getTotalConnections();
            return total > 0 ? (double) active / total : 0.0;
        }
        
        @Override
        public void resizePool(int newSize) {
            pool.getDataSource().setMaximumPoolSize(newSize);
            log.info("Resized pool {} to {}", pool.getPoolName(), newSize);
        }
        
        @Override
        public void evictIdleConnections() {
            pool.getDataSource().getHikariPoolMXBean().softEvictConnections();
            log.info("Evicted idle connections from pool {}", pool.getPoolName());
        }
    }
}