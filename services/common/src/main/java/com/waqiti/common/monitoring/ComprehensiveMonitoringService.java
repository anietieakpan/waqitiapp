package com.waqiti.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveMonitoringService implements HealthIndicator {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    
    // Business metrics
    private final Counter paymentCounter;
    private final Counter fraudDetectionCounter;
    private final Counter userRegistrationCounter;
    private final Timer paymentProcessingTimer;
    private final Timer databaseQueryTimer;
    
    // System metrics
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final Map<String, AtomicLong> serviceMetrics = new ConcurrentHashMap<>();
    
    public ComprehensiveMonitoringService(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        
        // Initialize counters
        this.paymentCounter = Counter.builder("waqiti.payments.total")
            .description("Total number of payment transactions")
            .register(meterRegistry);
            
        this.fraudDetectionCounter = Counter.builder("waqiti.fraud.detections")
            .description("Total number of fraud detections")
            .register(meterRegistry);
            
        this.userRegistrationCounter = Counter.builder("waqiti.users.registrations")
            .description("Total number of user registrations")
            .register(meterRegistry);
            
        // Initialize timers
        this.paymentProcessingTimer = Timer.builder("waqiti.payments.processing.time")
            .description("Payment processing time")
            .register(meterRegistry);
            
        this.databaseQueryTimer = Timer.builder("waqiti.database.query.time")
            .description("Database query execution time")
            .register(meterRegistry);
        
        // Register gauges
        Gauge.builder("waqiti.connections.active", this, ComprehensiveMonitoringService::getActiveConnections)
            .description("Active database connections")
            .register(meterRegistry);
            
        Gauge.builder("waqiti.transactions.total", this, ComprehensiveMonitoringService::getTotalTransactions)
            .description("Total transactions processed")
            .register(meterRegistry);
    }

    public void recordPaymentTransaction(String status, double amount, String currency) {
        Counter.builder("waqiti.payments.total")
            .tag("status", status)
            .tag("currency", currency)
            .tag("amount_range", categorizeAmount(amount))
            .register(meterRegistry)
            .increment();
        totalTransactions.incrementAndGet();
        
        log.info("Payment transaction recorded: status={}, amount={} {}", status, amount, currency);
    }

    public void recordFraudDetection(String riskLevel, String reason) {
        Counter.builder("waqiti.fraud.detections")
            .tag("risk_level", riskLevel)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
        
        log.warn("Fraud detection recorded: risk_level={}, reason={}", riskLevel, reason);
    }

    public void recordUserRegistration(String source, boolean verified) {
        Counter.builder("waqiti.users.registrations")
            .tag("source", source)
            .tag("verified", String.valueOf(verified))
            .register(meterRegistry)
            .increment();
        
        log.info("User registration recorded: source={}, verified={}", source, verified);
    }

    public Timer.Sample startPaymentTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordPaymentTime(Timer.Sample sample, String paymentType, String status) {
        sample.stop(Timer.builder("waqiti.payments.processing.time")
            .tag("type", paymentType)
            .tag("status", status)
            .register(meterRegistry));
    }

    public Timer.Sample startDatabaseTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDatabaseQueryTime(Timer.Sample sample, String operation, String table) {
        sample.stop(Timer.builder("waqiti.database.query.time")
            .tag("operation", operation)
            .tag("table", table)
            .register(meterRegistry));
    }

    public void recordServiceCall(String service, String operation, boolean success, long duration) {
        Counter.builder("waqiti.service.calls")
            .tag("service", service)
            .tag("operation", operation)
            .tag("status", success ? "success" : "failure")
            .register(meterRegistry)
            .increment();

        Timer.builder("waqiti.service.call.duration")
            .tag("service", service)
            .tag("operation", operation)
            .register(meterRegistry)
            .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

        String key = service + "." + operation + "." + (success ? "success" : "failure");
        serviceMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordApiCall(String endpoint, String method, int statusCode, long duration) {
        Counter.builder("waqiti.api.requests")
            .tag("endpoint", endpoint)
            .tag("method", method)
            .tag("status", String.valueOf(statusCode))
            .register(meterRegistry)
            .increment();

        Timer.builder("waqiti.api.request.duration")
            .tag("endpoint", endpoint)
            .tag("method", method)
            .register(meterRegistry)
            .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordCacheHit(String cacheName, boolean hit) {
        Counter.builder("waqiti.cache.requests")
            .tag("cache", cacheName)
            .tag("result", hit ? "hit" : "miss")
            .register(meterRegistry)
            .increment();
    }

    public void recordQueueMessage(String queue, String operation, boolean success) {
        Counter.builder("waqiti.queue.messages")
            .tag("queue", queue)
            .tag("operation", operation)
            .tag("status", success ? "success" : "failure")
            .register(meterRegistry)
            .increment();
    }

    public void recordBusinessEvent(String eventType, Map<String, String> attributes) {
        Counter.Builder builder = Counter.builder("waqiti.business.events")
            .tag("event_type", eventType);
            
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            builder.tag(entry.getKey(), entry.getValue());
        }
        
        builder.register(meterRegistry).increment();
        
        log.info("Business event recorded: type={}, attributes={}", eventType, attributes);
    }

    public void recordSecurityEvent(String eventType, String severity, String source) {
        Counter.builder("waqiti.security.events")
            .tag("event_type", eventType)
            .tag("severity", severity)
            .tag("source", source)
            .register(meterRegistry)
            .increment();

        log.warn("Security event recorded: type={}, severity={}, source={}", eventType, severity, source);
    }

    public void recordErrorEvent(String service, String errorType, String errorCode) {
        Counter.builder("waqiti.errors")
            .tag("service", service)
            .tag("error_type", errorType)
            .tag("error_code", errorCode)
            .register(meterRegistry)
            .increment();

        log.error("Error event recorded: service={}, type={}, code={}", service, errorType, errorCode);
    }

    @Override
    public Health health() {
        try {
            // Check database connectivity
            boolean dbHealthy = checkDatabaseHealth();
            
            // Check system resources
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = (double) usedMemory / totalMemory * 100;
            
            Health.Builder healthBuilder = new Health.Builder();
            
            if (dbHealthy && memoryUsagePercent < 90) {
                healthBuilder.up();
            } else {
                healthBuilder.down();
            }
            
            return healthBuilder
                .withDetail("database", dbHealthy ? "UP" : "DOWN")
                .withDetail("memory_usage_percent", Math.round(memoryUsagePercent * 100.0) / 100.0)
                .withDetail("active_connections", activeConnections.get())
                .withDetail("total_transactions", totalTransactions.get())
                .withDetail("timestamp", LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", LocalDateTime.now())
                .build();
        }
    }

    private boolean checkDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5); // 5 second timeout
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }

    private String categorizeAmount(double amount) {
        if (amount < 10) return "micro";
        if (amount < 100) return "small";
        if (amount < 1000) return "medium";
        if (amount < 10000) return "large";
        return "very_large";
    }

    private double getActiveConnections() {
        return activeConnections.get();
    }

    private double getTotalTransactions() {
        return totalTransactions.get();
    }

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public Map<String, Object> getServiceMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        serviceMetrics.forEach((key, value) -> metrics.put(key, value.get()));
        return metrics;
    }

    public void recordCustomMetric(String name, double value, Map<String, String> tags) {
        Gauge.Builder builder = Gauge.builder("waqiti.custom." + name, () -> value)
            .description("Custom metric: " + name);
            
        if (tags != null) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                builder.tag(entry.getKey(), entry.getValue());
            }
        }
        
        builder.register(meterRegistry);
        
        log.debug("Custom metric recorded: {}={}, tags={}", name, value, tags);
    }
}