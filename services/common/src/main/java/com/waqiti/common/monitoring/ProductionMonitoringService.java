package com.waqiti.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionMonitoringService {
    
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final AlertingService alertingService;
    
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> transactionCounts = new ConcurrentHashMap<>();
    
    private static final long ERROR_THRESHOLD = 100;
    private static final long TRANSACTION_FAILURE_THRESHOLD = 50;
    private static final double TRANSACTION_SUCCESS_RATE_THRESHOLD = 0.95;
    
    public void recordPaymentSuccess(String provider, BigDecimal amount, long durationMs) {
        meterRegistry.counter("payments.success",
            "provider", provider).increment();
        
        meterRegistry.timer("payments.duration",
            "provider", provider,
            "status", "success").record(durationMs, TimeUnit.MILLISECONDS);
        
        meterRegistry.summary("payments.amount",
            "provider", provider,
            "status", "success").record(amount.doubleValue());
        
        transactionCounts.computeIfAbsent("payment_success", k -> new AtomicLong()).incrementAndGet();
        
        log.debug("Payment success recorded: provider={}, amount={}, duration={}ms", 
            provider, amount, durationMs);
    }
    
    public void recordPaymentFailure(String provider, String errorCode, BigDecimal amount, long durationMs) {
        meterRegistry.counter("payments.failure",
            "provider", provider,
            "errorCode", errorCode).increment();
        
        meterRegistry.timer("payments.duration",
            "provider", provider,
            "status", "failure").record(durationMs, TimeUnit.MILLISECONDS);
        
        String errorKey = String.format("payment_failure_%s_%s", provider, errorCode);
        long errorCount = errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong()).incrementAndGet();
        
        transactionCounts.computeIfAbsent("payment_failure", k -> new AtomicLong()).incrementAndGet();
        
        if (errorCount >= ERROR_THRESHOLD) {
            alertingService.sendCriticalAlert(
                "Payment Failure Threshold Exceeded",
                String.format("Provider: %s, Error: %s, Count: %d", provider, errorCode, errorCount),
                Map.of(
                    "provider", provider,
                    "errorCode", errorCode,
                    "errorCount", String.valueOf(errorCount),
                    "threshold", String.valueOf(ERROR_THRESHOLD)
                )
            );
            errorCounts.get(errorKey).set(0);
        }
        
        log.warn("Payment failure recorded: provider={}, errorCode={}, amount={}, duration={}ms",
            provider, errorCode, amount, durationMs);
    }
    
    public void recordTransactionProcessed(String type, String status, long durationMs) {
        meterRegistry.counter("transactions.processed",
            "type", type,
            "status", status).increment();
        
        meterRegistry.timer("transactions.duration",
            "type", type,
            "status", status).record(durationMs, TimeUnit.MILLISECONDS);
        
        String key = String.format("transaction_%s_%s", type, status);
        transactionCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }
    
    public void recordFraudCheck(String decision, double riskScore, long durationMs) {
        meterRegistry.counter("fraud.checks",
            "decision", decision).increment();
        
        meterRegistry.summary("fraud.riskScore",
            "decision", decision).record(riskScore);
        
        meterRegistry.timer("fraud.duration",
            "decision", decision).record(durationMs, TimeUnit.MILLISECONDS);
        
        if ("REJECTED".equals(decision)) {
            alertingService.sendHighPriorityAlert(
                "Fraudulent Transaction Detected",
                String.format("Risk Score: %.2f", riskScore),
                Map.of(
                    "riskScore", String.valueOf(riskScore),
                    "decision", decision
                )
            );
        }
    }
    
    public void recordKYCVerification(String status, String level, long durationMs) {
        meterRegistry.counter("kyc.verifications",
            "status", status,
            "level", level).increment();
        
        meterRegistry.timer("kyc.duration",
            "status", status).record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordApiCall(String endpoint, String method, int statusCode, long durationMs) {
        meterRegistry.counter("api.calls",
            "endpoint", endpoint,
            "method", method,
            "status", String.valueOf(statusCode)).increment();
        
        meterRegistry.timer("api.duration",
            "endpoint", endpoint,
            "method", method).record(durationMs, TimeUnit.MILLISECONDS);
        
        if (statusCode >= 500) {
            String errorKey = String.format("api_error_%s_%d", endpoint, statusCode);
            long errorCount = errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong()).incrementAndGet();
            
            if (errorCount >= 10) {
                alertingService.sendCriticalAlert(
                    "API Error Rate Exceeded",
                    String.format("Endpoint: %s, Status: %d, Count: %d", endpoint, statusCode, errorCount),
                    Map.of(
                        "endpoint", endpoint,
                        "statusCode", String.valueOf(statusCode),
                        "errorCount", String.valueOf(errorCount)
                    )
                );
                errorCounts.get(errorKey).set(0);
            }
        }
    }
    
    public void recordDatabaseQuery(String operation, String table, long durationMs) {
        meterRegistry.counter("database.queries",
            "operation", operation,
            "table", table).increment();
        
        meterRegistry.timer("database.query.duration",
            "operation", operation,
            "table", table).record(durationMs, TimeUnit.MILLISECONDS);
        
        if (durationMs > 1000) {
            log.warn("Slow database query detected: operation={}, table={}, duration={}ms",
                operation, table, durationMs);
            
            alertingService.sendMediumPriorityAlert(
                "Slow Database Query",
                String.format("Operation: %s, Table: %s, Duration: %dms", operation, table, durationMs),
                Map.of(
                    "operation", operation,
                    "table", table,
                    "duration", String.valueOf(durationMs)
                )
            );
        }
    }
    
    public void recordCacheOperation(String operation, String cacheName, boolean hit) {
        meterRegistry.counter("cache.operations",
            "operation", operation,
            "cache", cacheName,
            "hit", String.valueOf(hit)).increment();
    }
    
    public void recordEventPublished(String topic, boolean success, long durationMs) {
        meterRegistry.counter("events.published",
            "topic", topic,
            "success", String.valueOf(success)).increment();
        
        meterRegistry.timer("events.publish.duration",
            "topic", topic).record(durationMs, TimeUnit.MILLISECONDS);
        
        if (!success) {
            String errorKey = String.format("event_failure_%s", topic);
            long errorCount = errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong()).incrementAndGet();
            
            if (errorCount >= 20) {
                alertingService.sendCriticalAlert(
                    "Event Publishing Failure",
                    String.format("Topic: %s, Failures: %d", topic, errorCount),
                    Map.of(
                        "topic", topic,
                        "errorCount", String.valueOf(errorCount)
                    )
                );
                errorCounts.get(errorKey).set(0);
            }
        }
    }
    
    public void recordSecurityEvent(String eventType, String severity) {
        meterRegistry.counter("security.events",
            "type", eventType,
            "severity", severity).increment();
        
        if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
            alertingService.sendCriticalAlert(
                "Security Event Detected",
                String.format("Type: %s, Severity: %s", eventType, severity),
                Map.of(
                    "eventType", eventType,
                    "severity", severity,
                    "timestamp", LocalDateTime.now().toString()
                )
            );
        }
    }
    
    @Scheduled(fixedRate = 60000)
    public void monitorSystemHealth() {
        try {
            monitorDatabaseHealth();
            monitorTransactionSuccessRate();
            monitorMemoryUsage();
            monitorThreadPool();
            
        } catch (Exception e) {
            log.error("Error monitoring system health", e);
        }
    }
    
    private void monitorDatabaseHealth() {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
            
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                meterRegistry.counter("database.health.check", "status", "success").increment();
            }
            
        } catch (Exception e) {
            log.error("Database health check failed", e);
            meterRegistry.counter("database.health.check", "status", "failure").increment();
            
            alertingService.sendCriticalAlert(
                "Database Health Check Failed",
                e.getMessage(),
                Map.of("error", e.getClass().getSimpleName())
            );
            
        } finally {
            sample.stop(meterRegistry.timer("database.health.check.duration"));
        }
    }
    
    private void monitorTransactionSuccessRate() {
        long successCount = transactionCounts.getOrDefault("payment_success", new AtomicLong()).get();
        long failureCount = transactionCounts.getOrDefault("payment_failure", new AtomicLong()).get();
        long totalCount = successCount + failureCount;
        
        if (totalCount > 100) {
            double successRate = (double) successCount / totalCount;
            
            meterRegistry.gauge("transactions.success.rate", successRate);
            
            if (successRate < TRANSACTION_SUCCESS_RATE_THRESHOLD) {
                alertingService.sendCriticalAlert(
                    "Transaction Success Rate Below Threshold",
                    String.format("Success Rate: %.2f%%, Threshold: %.2f%%", 
                        successRate * 100, TRANSACTION_SUCCESS_RATE_THRESHOLD * 100),
                    Map.of(
                        "successRate", String.format("%.2f", successRate),
                        "successCount", String.valueOf(successCount),
                        "failureCount", String.valueOf(failureCount),
                        "threshold", String.valueOf(TRANSACTION_SUCCESS_RATE_THRESHOLD)
                    )
                );
            }
            
            transactionCounts.clear();
        }
    }
    
    private void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsagePercent = (double) usedMemory / maxMemory;
        
        meterRegistry.gauge("jvm.memory.used", usedMemory);
        meterRegistry.gauge("jvm.memory.usage.percent", memoryUsagePercent);
        
        if (memoryUsagePercent > 0.90) {
            alertingService.sendHighPriorityAlert(
                "High Memory Usage",
                String.format("Memory usage: %.2f%%", memoryUsagePercent * 100),
                Map.of(
                    "usedMemory", String.valueOf(usedMemory),
                    "maxMemory", String.valueOf(maxMemory),
                    "usagePercent", String.format("%.2f", memoryUsagePercent * 100)
                )
            );
        }
    }
    
    private void monitorThreadPool() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        
        int activeThreadCount = rootGroup.activeCount();
        meterRegistry.gauge("jvm.threads.active", activeThreadCount);
        
        if (activeThreadCount > 500) {
            alertingService.sendHighPriorityAlert(
                "High Thread Count",
                String.format("Active threads: %d", activeThreadCount),
                Map.of("threadCount", String.valueOf(activeThreadCount))
            );
        }
    }
    
    @Scheduled(cron = "0 0 * * * *")
    public void resetHourlyCounters() {
        errorCounts.clear();
        log.info("Hourly monitoring counters reset");
    }
}