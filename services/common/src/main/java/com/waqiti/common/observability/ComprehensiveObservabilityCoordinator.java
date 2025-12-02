package com.waqiti.common.observability;

import com.waqiti.common.servicemesh.ObservabilityManager;
import com.waqiti.common.tracing.OpenTelemetryTracingService;
import com.waqiti.common.tracing.TracingMetricsExporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive Observability Coordinator that integrates all observability patterns
 * for the Waqiti P2P payment platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveObservabilityCoordinator {

    private final MeterRegistry meterRegistry;
    private final OpenTelemetryTracingService tracingService;
    private final ObservabilityManager observabilityManager;
    private final TracingMetricsExporter metricsExporter;
    private final Tracer tracer;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // Business Metrics
    private final Map<String, LongAdder> businessMetrics = new ConcurrentHashMap<>();
    private final Map<String, Timer> operationTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> eventCounters = new ConcurrentHashMap<>();
    
    // Service Health Tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalRevenue = new AtomicLong(0); // in cents
    private final AtomicLong activeTransactions = new AtomicLong(0);
    private final AtomicLong completedTransactions = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Comprehensive Observability Coordinator");
        
        setupBusinessMetrics();
        setupHealthMetrics();
        setupPerformanceMetrics();
        setupSecurityMetrics();
        setupPaymentMetrics();
        startMetricsCollection();
        
        log.info("Comprehensive Observability Coordinator initialized successfully");
    }
    
    /**
     * Setup business-specific metrics for P2P payments
     */
    private void setupBusinessMetrics() {
        // Payment Volume Metrics
        Gauge.builder("waqiti.payments.total_volume", totalRevenue, AtomicLong::get)
            .description("Total payment volume processed (in cents)")
            .register(meterRegistry);
            
        Gauge.builder("waqiti.payments.active_transactions", activeTransactions, AtomicLong::get)
            .description("Number of active payment transactions")
            .register(meterRegistry);
            
        Gauge.builder("waqiti.payments.completed_transactions", completedTransactions, AtomicLong::get)
            .description("Total number of completed transactions")
            .register(meterRegistry);
            
        // User Activity Metrics
        businessMetrics.put("active_users_daily", new LongAdder());
        businessMetrics.put("new_user_registrations", new LongAdder());
        businessMetrics.put("wallet_creations", new LongAdder());
        businessMetrics.put("kyc_verifications", new LongAdder());
        
        // Register business metrics as gauges
        businessMetrics.forEach((name, adder) -> 
            Gauge.builder("waqiti.business." + name, adder, LongAdder::doubleValue)
                .description("Business metric: " + name)
                .register(meterRegistry)
        );
    }
    
    /**
     * Setup health and availability metrics
     */
    private void setupHealthMetrics() {
        Gauge.builder("waqiti.health.request_success_rate", this, obj -> {
                long total = totalRequests.get();
                long successful = successfulRequests.get();
                return total > 0 ? (successful * 100.0) / total : 100.0;
            })
            .description("Request success rate percentage")
            .register(meterRegistry);
            
        Gauge.builder("waqiti.health.error_rate", this, obj -> {
                long total = totalRequests.get();
                long failed = failedRequests.get();
                return total > 0 ? (failed * 100.0) / total : 0.0;
            })
            .description("Request error rate percentage")
            .register(meterRegistry);
    }
    
    /**
     * Setup performance metrics
     */
    private void setupPerformanceMetrics() {
        // Create performance timers for key operations
        String[] operations = {
            "payment.process", "wallet.balance_check", "kyc.verification", 
            "fraud.detection", "compliance.screening", "blockchain.transaction",
            "notification.send", "report.generation"
        };
        
        for (String operation : operations) {
            operationTimers.put(operation, 
                Timer.builder("waqiti.performance." + operation + ".duration")
                    .description("Duration of " + operation + " operations")
                    .register(meterRegistry)
            );
        }
    }
    
    /**
     * Setup security metrics
     */
    private void setupSecurityMetrics() {
        String[] securityEvents = {
            "login_attempts", "failed_logins", "suspicious_activities",
            "fraud_detections", "compliance_violations", "token_validations",
            "unauthorized_accesses", "rate_limit_hits"
        };
        
        for (String event : securityEvents) {
            eventCounters.put("security." + event,
                Counter.builder("waqiti.security." + event)
                    .description("Count of " + event + " events")
                    .register(meterRegistry)
            );
        }
    }
    
    /**
     * Setup payment-specific metrics
     */
    private void setupPaymentMetrics() {
        String[] paymentEvents = {
            "payments.initiated", "payments.completed", "payments.failed",
            "refunds.requested", "refunds.processed", "chargebacks.received",
            "settlements.completed", "cryptocurrency.transactions"
        };
        
        for (String event : paymentEvents) {
            eventCounters.put("payment." + event,
                Counter.builder("waqiti.payments." + event)
                    .description("Count of " + event + " events")
                    .register(meterRegistry)
            );
        }
    }
    
    /**
     * Start scheduled metrics collection
     */
    private void startMetricsCollection() {
        // Collect system metrics every 30 seconds
        scheduler.scheduleAtFixedRate(this::collectSystemMetrics, 0, 30, TimeUnit.SECONDS);
        
        // Collect business metrics every minute
        scheduler.scheduleAtFixedRate(this::collectBusinessMetrics, 0, 60, TimeUnit.SECONDS);
        
        // Export custom metrics every 2 minutes
        scheduler.scheduleAtFixedRate(this::exportCustomMetrics, 0, 120, TimeUnit.SECONDS);
        
        log.info("Started scheduled metrics collection");
    }
    
    /**
     * Record a payment transaction
     */
    public void recordPaymentTransaction(String type, long amountCents, boolean success) {
        totalRequests.incrementAndGet();
        
        if (success) {
            successfulRequests.incrementAndGet();
            completedTransactions.incrementAndGet();
            totalRevenue.addAndGet(amountCents);
            
            eventCounters.get("payment.payments.completed").increment();
        } else {
            failedRequests.incrementAndGet();
            eventCounters.get("payment.payments.failed").increment();
        }
        
        // Record with tracing
        Span span = tracer.spanBuilder("payment.transaction")
            .setAttribute("payment.type", type)
            .setAttribute("payment.amount", amountCents)
            .setAttribute("payment.success", success)
            .startSpan();
            
        try {
            log.debug("Recorded payment transaction: type={}, amount={}, success={}", 
                type, amountCents, success);
        } finally {
            span.end();
        }
    }
    
    /**
     * Record operation timing
     */
    public Timer.Sample startTimer(String operation) {
        Timer timer = operationTimers.get(operation);
        if (timer != null) {
            return Timer.start(meterRegistry);
        }
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop timer and record duration
     */
    public void stopTimer(Timer.Sample sample, String operation) {
        Timer timer = operationTimers.get(operation);
        if (timer != null) {
            sample.stop(timer);
        }
    }
    
    /**
     * Record a business event
     */
    public void recordBusinessEvent(String event, long value) {
        LongAdder adder = businessMetrics.get(event);
        if (adder != null) {
            adder.add(value);
            log.debug("Recorded business event: {}={}", event, value);
        }
    }
    
    /**
     * Record security event
     */
    public void recordSecurityEvent(String event) {
        Counter counter = eventCounters.get("security." + event);
        if (counter != null) {
            counter.increment();
            log.debug("Recorded security event: {}", event);
        }
        
        // Create security trace
        Span span = tracer.spanBuilder("security.event")
            .setAttribute("security.event_type", event)
            .setAttribute("security.timestamp", Instant.now().toString())
            .startSpan();
        span.end();
    }
    
    /**
     * Create and manage distributed trace
     */
    public Span startDistributedTrace(String operationName, Map<String, String> attributes) {
        Span span = tracer.spanBuilder(operationName).startSpan();
        
        if (attributes != null) {
            attributes.forEach(span::setAttribute);
        }
        
        return span;
    }
    
    /**
     * Collect system metrics
     */
    private void collectSystemMetrics() {
        try {
            // JVM metrics are automatically collected by Micrometer
            // Record custom system metrics
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            
            Gauge.builder("waqiti.system.memory_used_bytes", () -> usedMemory)
                .register(meterRegistry);
                
        } catch (Exception e) {
            log.error("Error collecting system metrics", e);
        }
    }
    
    /**
     * Collect business metrics
     */
    private void collectBusinessMetrics() {
        try {
            // Calculate derived business metrics
            long totalPayments = completedTransactions.get();
            double avgPaymentValue = totalPayments > 0 ? 
                (double) totalRevenue.get() / totalPayments : 0.0;
                
            Gauge.builder("waqiti.business.average_payment_value", () -> avgPaymentValue)
                .description("Average payment value in cents")
                .register(meterRegistry);
                
            log.debug("Collected business metrics: total_payments={}, avg_value={}", 
                totalPayments, avgPaymentValue);
                
        } catch (Exception e) {
            log.error("Error collecting business metrics", e);
        }
    }
    
    /**
     * Export custom metrics
     */
    private void exportCustomMetrics() {
        try {
            if (metricsExporter != null) {
                Map<String, Double> customMetrics = new ConcurrentHashMap<>();
                
                // Add business metrics
                businessMetrics.forEach((key, value) -> 
                    customMetrics.put("business." + key, value.doubleValue()));
                    
                // Add computed metrics
                customMetrics.put("health.success_rate", 
                    totalRequests.get() > 0 ? 
                        (successfulRequests.get() * 100.0) / totalRequests.get() : 100.0);
                        
                customMetrics.put("payments.total_volume_dollars", totalRevenue.get() / 100.0);
                
                // Export metrics
                metricsExporter.exportMetrics();
                
                log.debug("Exported {} custom metrics", customMetrics.size());
            }
        } catch (Exception e) {
            log.error("Error exporting custom metrics", e);
        }
    }
    
    /**
     * Get current observability status
     */
    public ObservabilityStatus getStatus() {
        return ObservabilityStatus.builder()
            .totalRequests(totalRequests.get())
            .successfulRequests(successfulRequests.get())
            .failedRequests(failedRequests.get())
            .totalRevenue(totalRevenue.get())
            .activeTransactions(activeTransactions.get())
            .completedTransactions(completedTransactions.get())
            .metricsCount(businessMetrics.size() + operationTimers.size() + eventCounters.size())
            .tracingEnabled(tracingService != null)
            .build();
    }
    
    /**
     * Observability status DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ObservabilityStatus {
        private long totalRequests;
        private long successfulRequests; 
        private long failedRequests;
        private long totalRevenue;
        private long activeTransactions;
        private long completedTransactions;
        private int metricsCount;
        private boolean tracingEnabled;
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (successfulRequests * 100.0) / totalRequests : 100.0;
        }
        
        public double getErrorRate() {
            return totalRequests > 0 ? (failedRequests * 100.0) / totalRequests : 0.0;
        }
    }
    
    /**
     * Shutdown cleanup
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down Comprehensive Observability Coordinator");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Observability Coordinator shutdown complete");
    }
}