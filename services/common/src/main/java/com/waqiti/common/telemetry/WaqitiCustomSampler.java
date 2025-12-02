package com.waqiti.common.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Sampler for Waqiti Platform with intelligent sampling decisions
 * 
 * Sampling Strategy:
 * 1. ALWAYS sample: Critical financial operations, errors, compliance checks
 * 2. NEVER sample: Health checks, metrics endpoints, static resources
 * 3. ADAPTIVE sample: Adjust rate based on system load and error rates
 * 4. PROBABILISTIC sample: Default sampling for regular operations
 * 
 * Features:
 * - Per-operation sampling rates
 * - Error-based sampling boost
 * - Load-adaptive sampling
 * - Trace priority propagation
 * - Sampling metrics collection
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
public class WaqitiCustomSampler implements Sampler {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    private final double baseSamplingProbability;
    private final Set<String> alwaysSampleOperations;
    private final Set<String> neverSampleOperations;
    private final ConcurrentHashMap<String, AtomicLong> errorCounts;
    private final ConcurrentHashMap<String, Double> adaptiveSamplingRates;

    // Metrics
    private final AtomicLong totalSpansConsidered = new AtomicLong(0);
    private final AtomicLong totalSpansSampled = new AtomicLong(0);

    // Configuration
    private static final double ERROR_BOOST_FACTOR = 2.0;
    private static final double MAX_SAMPLING_RATE = 1.0;
    private static final double MIN_SAMPLING_RATE = 0.001;
    private static final long ERROR_COUNT_THRESHOLD = 10;
    
    public WaqitiCustomSampler(double baseSamplingProbability) {
        this.baseSamplingProbability = Math.max(MIN_SAMPLING_RATE, 
                                               Math.min(MAX_SAMPLING_RATE, baseSamplingProbability));
        this.alwaysSampleOperations = initializeAlwaysSampleOperations();
        this.neverSampleOperations = initializeNeverSampleOperations();
        this.errorCounts = new ConcurrentHashMap<>();
        this.adaptiveSamplingRates = new ConcurrentHashMap<>();
        
        log.info("WaqitiCustomSampler initialized with base probability: {}", this.baseSamplingProbability);
    }
    
    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String spanName,
            SpanKind spanKind,
            Attributes attributes,
            List<LinkData> parentLinks) {
        
        totalSpansConsidered.incrementAndGet();
        
        // Step 1: Check if this is a forced sample operation
        if (shouldAlwaysSample(spanName, attributes)) {
            totalSpansSampled.incrementAndGet();
            return SamplingResult.recordAndSample();
        }
        
        // Step 2: Check if this should never be sampled
        if (shouldNeverSample(spanName, attributes)) {
            return SamplingResult.drop();
        }
        
        // Step 3: Check for error conditions
        if (hasErrorAttribute(attributes)) {
            recordError(spanName);
            if (shouldSampleError(spanName)) {
                totalSpansSampled.incrementAndGet();
                return SamplingResult.recordAndSample();
            }
        }
        
        // Step 4: Check trace priority from parent
        String tracePriority = extractTracePriority(attributes);
        if ("HIGH".equals(tracePriority) || "CRITICAL".equals(tracePriority)) {
            totalSpansSampled.incrementAndGet();
            return SamplingResult.recordAndSample();
        }
        
        // Step 5: Apply adaptive sampling
        double samplingRate = getAdaptiveSamplingRate(spanName, spanKind);
        
        // Step 6: Make probabilistic decision
        if (shouldSampleProbabilistic(traceId, samplingRate)) {
            totalSpansSampled.incrementAndGet();
            return SamplingResult.recordAndSample();
        }
        
        return SamplingResult.drop();
    }
    
    @Override
    public String getDescription() {
        return String.format("WaqitiCustomSampler{base=%.3f, sampled=%d/%d}",
            baseSamplingProbability, totalSpansSampled.get(), totalSpansConsidered.get());
    }
    
    /**
     * Initialize operations that should always be sampled
     */
    private Set<String> initializeAlwaysSampleOperations() {
        return Set.of(
            // Financial operations
            "POST /api/v1/payments",
            "POST /api/v1/transfers",
            "POST /api/v1/settlements",
            "PaymentProcessing",
            "SettlementExecution",
            "FundTransfer",
            
            // Compliance operations
            "ComplianceCheck",
            "SanctionsScreening",
            "FraudDetection",
            "KYCVerification",
            "AMLScreening",
            
            // Critical errors
            "PaymentFailed",
            "TransactionRollback",
            "SystemError",
            
            // Audit operations
            "AuditLog",
            "ComplianceReport",
            
            // Security operations
            "Authentication",
            "Authorization",
            "SecurityViolation"
        );
    }
    
    /**
     * Initialize operations that should never be sampled
     */
    private Set<String> initializeNeverSampleOperations() {
        return Set.of(
            // Health checks
            "GET /health",
            "GET /actuator/health",
            "GET /ready",
            "GET /live",
            
            // Metrics
            "GET /metrics",
            "GET /actuator/metrics",
            "GET /prometheus",
            
            // Static resources
            "GET /favicon.ico",
            "GET /robots.txt",
            "GET /swagger-ui",
            "GET /v3/api-docs",
            
            // Internal operations
            "HeartbeatCheck",
            "MetricsCollection",
            "CacheRefresh"
        );
    }
    
    /**
     * Check if span should always be sampled
     */
    private boolean shouldAlwaysSample(String spanName, Attributes attributes) {
        // Check operation name
        if (alwaysSampleOperations.contains(spanName)) {
            return true;
        }
        
        // Check for critical attributes
        String operationType = attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("operation.type"));
        if ("PAYMENT".equals(operationType) || "COMPLIANCE".equals(operationType)) {
            return true;
        }
        
        // Check for high-value transactions
        Long amount = attributes.get(io.opentelemetry.api.common.AttributeKey.longKey("transaction.amount"));
        if (amount != null && amount > 10000) { // High-value threshold
            return true;
        }
        
        // Check HTTP status for errors
        Long httpStatus = attributes.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code"));
        if (httpStatus != null && httpStatus >= 500) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if span should never be sampled
     */
    private boolean shouldNeverSample(String spanName, Attributes attributes) {
        // Check operation name
        if (neverSampleOperations.contains(spanName)) {
            return true;
        }
        
        // Check for health check patterns
        if (spanName.toLowerCase().contains("health") || 
            spanName.toLowerCase().contains("metrics") ||
            spanName.toLowerCase().contains("actuator")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if attributes indicate an error
     */
    private boolean hasErrorAttribute(Attributes attributes) {
        Boolean error = attributes.get(io.opentelemetry.api.common.AttributeKey.booleanKey("error"));
        if (Boolean.TRUE.equals(error)) {
            return true;
        }
        
        String status = attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("status"));
        if ("ERROR".equals(status) || "FAILED".equals(status)) {
            return true;
        }
        
        Long httpStatus = attributes.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code"));
        if (httpStatus != null && httpStatus >= 400) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Record error occurrence for adaptive sampling
     */
    private void recordError(String spanName) {
        errorCounts.computeIfAbsent(spanName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Determine if error should be sampled based on error rate
     */
    private boolean shouldSampleError(String spanName) {
        AtomicLong errorCount = errorCounts.get(spanName);
        if (errorCount == null) {
            return true; // Sample first error
        }
        
        // Always sample if error count exceeds threshold
        if (errorCount.get() > ERROR_COUNT_THRESHOLD) {
            return true;
        }

        // Sample with boosted probability for errors
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        return secureRandom.nextDouble() < (baseSamplingProbability * ERROR_BOOST_FACTOR);
    }
    
    /**
     * Extract trace priority from attributes
     */
    private String extractTracePriority(Attributes attributes) {
        return attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("trace.priority"));
    }
    
    /**
     * Get adaptive sampling rate based on operation and system state
     */
    private double getAdaptiveSamplingRate(String spanName, SpanKind spanKind) {
        // Check if we have an adaptive rate for this operation
        Double adaptiveRate = adaptiveSamplingRates.get(spanName);
        if (adaptiveRate != null) {
            return adaptiveRate;
        }
        
        // Adjust rate based on span kind
        double rate = baseSamplingProbability;
        
        switch (spanKind) {
            case CLIENT:
                rate *= 0.8; // Slightly reduce for client spans
                break;
            case SERVER:
                rate *= 1.0; // Full rate for server spans
                break;
            case PRODUCER:
            case CONSUMER:
                rate *= 0.9; // Slightly reduce for messaging
                break;
            case INTERNAL:
                rate *= 0.5; // Reduce for internal spans
                break;
        }
        
        // Check system load and adjust
        double systemLoad = getSystemLoad();
        if (systemLoad > 0.8) {
            rate *= 0.5; // Reduce sampling under high load
        } else if (systemLoad > 0.6) {
            rate *= 0.75;
        }
        
        return Math.max(MIN_SAMPLING_RATE, Math.min(MAX_SAMPLING_RATE, rate));
    }
    
    /**
     * Make probabilistic sampling decision
     */
    private boolean shouldSampleProbabilistic(String traceId, double samplingRate) {
        // Use trace ID for consistent decision
        long hash = traceId.hashCode() & 0x7fffffffffffffffL;
        double threshold = samplingRate * Long.MAX_VALUE;
        return hash < threshold;
    }
    
    /**
     * Get current system load (simplified)
     */
    private double getSystemLoad() {
        // This would typically check actual system metrics
        return 0.5; // Placeholder
    }
    
    /**
     * Update adaptive sampling rates based on metrics
     */
    public void updateAdaptiveSamplingRates(Map<String, Double> newRates) {
        adaptiveSamplingRates.clear();
        adaptiveSamplingRates.putAll(newRates);
        log.info("Updated adaptive sampling rates for {} operations", newRates.size());
    }
    
    /**
     * Get sampling metrics
     */
    public SamplingMetrics getMetrics() {
        return new SamplingMetrics(
            totalSpansConsidered.get(),
            totalSpansSampled.get(),
            getSamplingRate(),
            errorCounts.size()
        );
    }
    
    /**
     * Get current effective sampling rate
     */
    private double getSamplingRate() {
        long considered = totalSpansConsidered.get();
        if (considered == 0) {
            return baseSamplingProbability;
        }
        return (double) totalSpansSampled.get() / considered;
    }
    
    /**
     * Sampling metrics
     */
    public static class SamplingMetrics {
        public final long totalConsidered;
        public final long totalSampled;
        public final double effectiveRate;
        public final int errorOperations;
        
        public SamplingMetrics(long totalConsidered, long totalSampled, 
                             double effectiveRate, int errorOperations) {
            this.totalConsidered = totalConsidered;
            this.totalSampled = totalSampled;
            this.effectiveRate = effectiveRate;
            this.errorOperations = errorOperations;
        }
    }
}