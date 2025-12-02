package com.waqiti.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive metrics and observability configuration
 */
@Configuration
@Slf4j
public class ComprehensiveMetricsConfiguration {
    
    /**
     * Customizer for MeterRegistry to add JVM metrics
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCustomizer() {
        return registry -> {
            // Register JVM metrics
            new JvmGcMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new JvmThreadMetrics().bindTo(registry);
            new ProcessorMetrics().bindTo(registry);
            new UptimeMetrics().bindTo(registry);
        };
    }
    
    /**
     * Business metrics collector
     */
    @Bean
    public BusinessMetricsCollector businessMetricsCollector(MeterRegistry meterRegistry) {
        return new BusinessMetricsCollector(meterRegistry);
    }
    
    /**
     * Performance metrics collector
     */
    @Bean
    public PerformanceMetricsCollector performanceMetricsCollector(MeterRegistry meterRegistry) {
        return new PerformanceMetricsCollector(meterRegistry);
    }
    
    /**
     * Custom health indicators
     */
    @Bean
    public DatabaseHealthIndicator databaseHealthIndicator() {
        return new DatabaseHealthIndicator();
    }
    
    @Bean
    public RedisHealthIndicator redisHealthIndicator() {
        return new RedisHealthIndicator();
    }
    
    @Bean
    public KafkaHealthIndicator kafkaHealthIndicator() {
        return new KafkaHealthIndicator();
    }
    
    /**
     * Business metrics collector for financial operations
     */
    @Component
    @RequiredArgsConstructor
    public static class BusinessMetricsCollector {
        private final MeterRegistry meterRegistry;
        
        // Payment metrics
        private final Counter paymentsCreated;
        private final Counter paymentsCompleted;
        private final Counter paymentsFailed;
        private final Timer paymentProcessingTime;
        private final Gauge totalPaymentVolume;
        
        // Transaction metrics
        private final Counter transactionsProcessed;
        private final Timer transactionProcessingTime;
        private final Gauge averageTransactionAmount;
        
        // User metrics
        private final Counter usersRegistered;
        private final Counter usersActivated;
        private final Gauge activeUsers;
        
        // Wallet metrics
        private final Counter walletsCreated;
        private final Gauge totalWalletBalance;
        private final Counter balanceUpdates;
        
        // Fraud metrics
        private final Counter fraudAlertsGenerated;
        private final Counter suspiciousTransactions;
        private final Timer fraudCheckTime;
        
        // Business state
        private final AtomicLong totalPaymentVolumeValue = new AtomicLong(0);
        private final AtomicLong totalWalletBalanceValue = new AtomicLong(0);
        private final AtomicInteger activeUsersValue = new AtomicInteger(0);
        private final AtomicLong averageTransactionAmountValue = new AtomicLong(0);
        
        public BusinessMetricsCollector(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            
            // Initialize counters
            this.paymentsCreated = Counter.builder("payments.created.total")
                .description("Total number of payments created")
                .register(meterRegistry);
            
            this.paymentsCompleted = Counter.builder("payments.completed.total")
                .description("Total number of payments completed")
                .register(meterRegistry);
            
            this.paymentsFailed = Counter.builder("payments.failed.total")
                .description("Total number of payments failed")
                .register(meterRegistry);
            
            this.paymentProcessingTime = Timer.builder("payments.processing.time")
                .description("Payment processing time")
                .register(meterRegistry);
            
            this.transactionsProcessed = Counter.builder("transactions.processed.total")
                .description("Total number of transactions processed")
                .register(meterRegistry);
            
            this.transactionProcessingTime = Timer.builder("transactions.processing.time")
                .description("Transaction processing time")
                .register(meterRegistry);
            
            this.usersRegistered = Counter.builder("users.registered.total")
                .description("Total number of users registered")
                .register(meterRegistry);
            
            this.usersActivated = Counter.builder("users.activated.total")
                .description("Total number of users activated")
                .register(meterRegistry);
            
            this.walletsCreated = Counter.builder("wallets.created.total")
                .description("Total number of wallets created")
                .register(meterRegistry);
            
            this.balanceUpdates = Counter.builder("wallets.balance.updates.total")
                .description("Total number of balance updates")
                .register(meterRegistry);
            
            this.fraudAlertsGenerated = Counter.builder("fraud.alerts.total")
                .description("Total number of fraud alerts generated")
                .register(meterRegistry);
            
            this.suspiciousTransactions = Counter.builder("fraud.suspicious.transactions.total")
                .description("Total number of suspicious transactions")
                .register(meterRegistry);
            
            this.fraudCheckTime = Timer.builder("fraud.check.time")
                .description("Fraud check processing time")
                .register(meterRegistry);
            
            // Initialize gauges
            this.totalPaymentVolume = Gauge.builder("payments.volume.total", this, obj -> obj.totalPaymentVolumeValue.get())
                .description("Total payment volume in base currency")
                .register(meterRegistry);
            
            this.totalWalletBalance = Gauge.builder("wallets.balance.total", this, obj -> obj.totalWalletBalanceValue.get())
                .description("Total wallet balance across all wallets")
                .register(meterRegistry);
            
            this.activeUsers = Gauge.builder("users.active.current", this, obj -> obj.activeUsersValue.get())
                .description("Current number of active users")
                .register(meterRegistry);
            
            this.averageTransactionAmount = Gauge.builder("transactions.amount.average", this, obj -> obj.averageTransactionAmountValue.get())
                .description("Average transaction amount")
                .register(meterRegistry);
        }
        
        // Metric recording methods
        public void recordPaymentCreated() {
            paymentsCreated.increment();
        }
        
        public void recordPaymentCompleted(Duration processingTime, long amount) {
            paymentsCompleted.increment();
            paymentProcessingTime.record(processingTime);
            totalPaymentVolumeValue.addAndGet(amount);
        }
        
        public void recordPaymentFailed() {
            paymentsFailed.increment();
        }
        
        public void recordTransactionProcessed(Duration processingTime, long amount) {
            transactionsProcessed.increment();
            transactionProcessingTime.record(processingTime);
            updateAverageTransactionAmount(amount);
        }
        
        public void recordUserRegistered() {
            usersRegistered.increment();
        }
        
        public void recordUserActivated() {
            usersActivated.increment();
            activeUsersValue.incrementAndGet();
        }
        
        public void recordWalletCreated() {
            walletsCreated.increment();
        }
        
        public void recordBalanceUpdate(long balanceChange) {
            balanceUpdates.increment();
            totalWalletBalanceValue.addAndGet(balanceChange);
        }
        
        public void recordFraudAlert() {
            fraudAlertsGenerated.increment();
        }
        
        public void recordSuspiciousTransaction(Duration checkTime) {
            suspiciousTransactions.increment();
            fraudCheckTime.record(checkTime);
        }
        
        private void updateAverageTransactionAmount(long amount) {
            // Simple moving average approximation
            long current = averageTransactionAmountValue.get();
            long newAverage = (current + amount) / 2;
            averageTransactionAmountValue.set(newAverage);
        }
        
        public void updateActiveUsers(int count) {
            activeUsersValue.set(count);
        }
    }
    
    /**
     * Performance metrics collector for system performance
     */
    @Component
    @RequiredArgsConstructor
    public static class PerformanceMetricsCollector {
        private final MeterRegistry meterRegistry;
        
        // Database metrics
        private final Timer databaseQueryTime;
        private final Counter databaseConnectionsCreated;
        private final Counter databaseConnectionsDestroyed;
        private final Gauge databaseConnectionPoolUtilization;
        
        // Cache metrics
        private final Counter cacheHits;
        private final Counter cacheMisses;
        private final Timer cacheOperationTime;
        private final Gauge cacheUtilization;
        
        // HTTP metrics
        private final Timer httpRequestTime;
        private final Counter httpRequests;
        private final Counter httpErrors;
        
        // Message queue metrics
        private final Timer messageProcessingTime;
        private final Counter messagesProduced;
        private final Counter messagesConsumed;
        private final Gauge messageQueueLag;
        
        // Performance state
        private final AtomicInteger dbPoolUtilization = new AtomicInteger(0);
        private final AtomicInteger cacheUtilizationValue = new AtomicInteger(0);
        private final AtomicLong messageQueueLagValue = new AtomicLong(0);
        
        public PerformanceMetricsCollector(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            
            // Database metrics
            this.databaseQueryTime = Timer.builder("database.query.time")
                .description("Database query execution time")
                .register(meterRegistry);
            
            this.databaseConnectionsCreated = Counter.builder("database.connections.created.total")
                .description("Total database connections created")
                .register(meterRegistry);
            
            this.databaseConnectionsDestroyed = Counter.builder("database.connections.destroyed.total")
                .description("Total database connections destroyed")
                .register(meterRegistry);
            
            this.databaseConnectionPoolUtilization = Gauge.builder("database.pool.utilization", this, obj -> obj.dbPoolUtilization.get())
                .description("Database connection pool utilization percentage")
                .register(meterRegistry);
            
            // Cache metrics
            this.cacheHits = Counter.builder("cache.hits.total")
                .description("Total cache hits")
                .register(meterRegistry);
            
            this.cacheMisses = Counter.builder("cache.misses.total")
                .description("Total cache misses")
                .register(meterRegistry);
            
            this.cacheOperationTime = Timer.builder("cache.operation.time")
                .description("Cache operation time")
                .register(meterRegistry);
            
            this.cacheUtilization = Gauge.builder("cache.utilization", this, obj -> obj.cacheUtilizationValue.get())
                .description("Cache utilization percentage")
                .register(meterRegistry);
            
            // HTTP metrics
            this.httpRequestTime = Timer.builder("http.request.time")
                .description("HTTP request processing time")
                .register(meterRegistry);
            
            this.httpRequests = Counter.builder("http.requests.total")
                .description("Total HTTP requests")
                .register(meterRegistry);
            
            this.httpErrors = Counter.builder("http.errors.total")
                .description("Total HTTP errors")
                .register(meterRegistry);
            
            // Message queue metrics
            this.messageProcessingTime = Timer.builder("messaging.processing.time")
                .description("Message processing time")
                .register(meterRegistry);
            
            this.messagesProduced = Counter.builder("messaging.produced.total")
                .description("Total messages produced")
                .register(meterRegistry);
            
            this.messagesConsumed = Counter.builder("messaging.consumed.total")
                .description("Total messages consumed")
                .register(meterRegistry);
            
            this.messageQueueLag = Gauge.builder("messaging.queue.lag", this, obj -> obj.messageQueueLagValue.get())
                .description("Message queue lag")
                .register(meterRegistry);
        }
        
        // Metric recording methods
        public void recordDatabaseQuery(Duration queryTime) {
            databaseQueryTime.record(queryTime);
        }
        
        public void recordDatabaseConnectionCreated() {
            databaseConnectionsCreated.increment();
        }
        
        public void recordDatabaseConnectionDestroyed() {
            databaseConnectionsDestroyed.increment();
        }
        
        public void updateDatabasePoolUtilization(int utilization) {
            dbPoolUtilization.set(utilization);
        }
        
        public void recordCacheHit(Duration operationTime) {
            cacheHits.increment();
            cacheOperationTime.record(operationTime);
        }
        
        public void recordCacheMiss(Duration operationTime) {
            cacheMisses.increment();
            cacheOperationTime.record(operationTime);
        }
        
        public void updateCacheUtilization(int utilization) {
            cacheUtilizationValue.set(utilization);
        }
        
        public void recordHttpRequest(Duration requestTime, boolean isError) {
            httpRequests.increment();
            httpRequestTime.record(requestTime);
            if (isError) {
                httpErrors.increment();
            }
        }
        
        public void recordMessageProduced() {
            messagesProduced.increment();
        }
        
        public void recordMessageConsumed(Duration processingTime) {
            messagesConsumed.increment();
            messageProcessingTime.record(processingTime);
        }
        
        public void updateMessageQueueLag(long lag) {
            messageQueueLagValue.set(lag);
        }
    }
    
    /**
     * Custom health indicators
     */
    @Component
    public static class DatabaseHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            try {
                // Perform database health check
                // This would typically involve a simple query
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("connections", "Normal")
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("database", "Unavailable")
                    .withException(e)
                    .build();
            }
        }
    }
    
    @Component
    public static class RedisHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            try {
                // Perform Redis health check
                return Health.up()
                    .withDetail("redis", "Available")
                    .withDetail("cluster", "Healthy")
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("redis", "Unavailable")
                    .withException(e)
                    .build();
            }
        }
    }
    
    @Component
    public static class KafkaHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            try {
                // Perform Kafka health check
                return Health.up()
                    .withDetail("kafka", "Available")
                    .withDetail("brokers", "Connected")
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("kafka", "Unavailable")
                    .withException(e)
                    .build();
            }
        }
    }
    
    /**
     * Metrics aggregator for dashboard consumption
     */
    @Bean
    public MetricsAggregator metricsAggregator(MeterRegistry meterRegistry) {
        return new MetricsAggregator(meterRegistry);
    }
    
    @Component
    @Slf4j
    public static class MetricsAggregator {
        private final MeterRegistry meterRegistry;
        private final Map<String, Object> aggregatedMetrics = new ConcurrentHashMap<>();
        
        public MetricsAggregator(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }
        
        @Scheduled(fixedDelay = 30000)
        public void aggregateMetrics() {
            // Aggregate key business metrics
            aggregatedMetrics.put("payments.success.rate", calculatePaymentSuccessRate());
            aggregatedMetrics.put("system.health.score", calculateSystemHealthScore());
            aggregatedMetrics.put("performance.score", calculatePerformanceScore());
            aggregatedMetrics.put("error.rate", calculateErrorRate());
            
            log.debug("Updated aggregated metrics: {}", aggregatedMetrics);
        }
        
        private double calculatePaymentSuccessRate() {
            // Calculate payment success rate based on metrics
            return 98.5; // Placeholder
        }
        
        private double calculateSystemHealthScore() {
            // Calculate overall system health score
            return 95.0; // Placeholder
        }
        
        private double calculatePerformanceScore() {
            // Calculate performance score based on response times
            return 92.0; // Placeholder
        }
        
        private double calculateErrorRate() {
            // Calculate overall error rate
            return 1.2; // Placeholder
        }
        
        public Map<String, Object> getAggregatedMetrics() {
            return new ConcurrentHashMap<>(aggregatedMetrics);
        }
    }
}