package com.waqiti.monitoring.alerting;

import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * CRITICAL MONITORING: SLA Monitoring and Alerting Service
 * PRODUCTION-READY: Real-time SLA tracking with automated alerting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SLAMonitoringService {

    private final CacheService cacheService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AlertingService alertingService;

    @Value("${waqiti.monitoring.sla.payment-processing.target:99.9}")
    private double paymentProcessingSLA;

    @Value("${waqiti.monitoring.sla.api-availability.target:99.95}")
    private double apiAvailabilitySLA;

    @Value("${waqiti.monitoring.sla.response-time.target:500}")
    private long responseTimeTarget; // milliseconds

    @Value("${waqiti.monitoring.sla.transaction-success.target:99.5}")
    private double transactionSuccessRateSLA;

    @Value("${waqiti.monitoring.sla.fraud-detection.target:95.0}")
    private double fraudDetectionAccuracySLA;

    // SLA Metrics Storage
    private final Map<String, SLAMetrics> slaMetrics = new ConcurrentHashMap<>();
    private final Map<String, AlertThresholds> alertThresholds = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeSLAMonitoring() {
        log.info("SLA_MONITORING: Initializing SLA monitoring service");

        // Initialize SLA metrics
        slaMetrics.put("payment_processing", new SLAMetrics());
        slaMetrics.put("api_availability", new SLAMetrics());
        slaMetrics.put("response_time", new SLAMetrics());
        slaMetrics.put("transaction_success", new SLAMetrics());
        slaMetrics.put("fraud_detection", new SLAMetrics());

        // Initialize alert thresholds
        initializeAlertThresholds();

        log.info("SLA_MONITORING: Service initialized with SLA targets - Payment: {}%, API: {}%, Response: {}ms, Transaction: {}%, Fraud: {}%",
                paymentProcessingSLA, apiAvailabilitySLA, responseTimeTarget, transactionSuccessRateSLA, fraudDetectionAccuracySLA);
    }

    /**
     * CRITICAL: Record payment processing metric
     */
    public void recordPaymentProcessing(boolean success, long processingTimeMs) {
        SLAMetrics metrics = slaMetrics.get("payment_processing");
        metrics.totalRequests.incrementAndGet();
        
        if (success) {
            metrics.successfulRequests.incrementAndGet();
        } else {
            metrics.failedRequests.incrementAndGet();
        }
        
        metrics.totalResponseTime.add(processingTimeMs);
        
        // Check for real-time SLA violations
        checkPaymentProcessingSLA();
        
        log.debug("SLA_MONITORING: Payment processing recorded - Success: {}, Time: {}ms", success, processingTimeMs);
    }

    /**
     * CRITICAL: Record API availability metric
     */
    public void recordApiCall(String endpoint, boolean success, long responseTimeMs) {
        SLAMetrics metrics = slaMetrics.get("api_availability");
        metrics.totalRequests.incrementAndGet();
        
        if (success) {
            metrics.successfulRequests.incrementAndGet();
        } else {
            metrics.failedRequests.incrementAndGet();
        }
        
        metrics.totalResponseTime.add(responseTimeMs);
        
        // Track per-endpoint metrics
        String endpointKey = "endpoint_" + endpoint.replaceAll("/", "_");
        SLAMetrics endpointMetrics = slaMetrics.computeIfAbsent(endpointKey, k -> new SLAMetrics());
        endpointMetrics.totalRequests.incrementAndGet();
        if (success) {
            endpointMetrics.successfulRequests.incrementAndGet();
        }
        endpointMetrics.totalResponseTime.add(responseTimeMs);
        
        // Check SLA violations
        checkApiAvailabilitySLA();
        if (responseTimeMs > responseTimeTarget) {
            checkResponseTimeSLA(endpoint, responseTimeMs);
        }
        
        log.debug("SLA_MONITORING: API call recorded - Endpoint: {}, Success: {}, Time: {}ms", endpoint, success, responseTimeMs);
    }

    /**
     * CRITICAL: Record transaction outcome
     */
    public void recordTransactionOutcome(String transactionType, boolean success, BigDecimal amount) {
        SLAMetrics metrics = slaMetrics.get("transaction_success");
        metrics.totalRequests.incrementAndGet();
        
        if (success) {
            metrics.successfulRequests.incrementAndGet();
        } else {
            metrics.failedRequests.incrementAndGet();
        }
        
        // Track transaction value
        if (amount != null) {
            metrics.totalTransactionValue.add(amount.doubleValue());
        }
        
        checkTransactionSuccessSLA();
        
        log.debug("SLA_MONITORING: Transaction recorded - Type: {}, Success: {}, Amount: {}", transactionType, success, amount);
    }

    /**
     * CRITICAL: Record fraud detection accuracy
     */
    public void recordFraudDetection(boolean actualFraud, boolean detectedFraud, double confidenceScore) {
        SLAMetrics metrics = slaMetrics.get("fraud_detection");
        metrics.totalRequests.incrementAndGet();
        
        // True positive or true negative
        if ((actualFraud && detectedFraud) || (!actualFraud && !detectedFraud)) {
            metrics.successfulRequests.incrementAndGet();
        } else {
            metrics.failedRequests.incrementAndGet();
        }
        
        metrics.totalResponseTime.add((long)(confidenceScore * 1000)); // Store confidence as pseudo-time
        
        checkFraudDetectionSLA();
        
        log.debug("SLA_MONITORING: Fraud detection recorded - Actual: {}, Detected: {}, Confidence: {}", 
                actualFraud, detectedFraud, confidenceScore);
    }

    /**
     * CRITICAL: Scheduled SLA monitoring and reporting
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorSLAs() {
        try {
            log.debug("SLA_MONITORING: Running SLA checks...");
            
            for (Map.Entry<String, SLAMetrics> entry : slaMetrics.entrySet()) {
                String slaType = entry.getKey();
                SLAMetrics metrics = entry.getValue();
                
                if (shouldCheckSLA(slaType)) {
                    SLAReport report = generateSLAReport(slaType, metrics);
                    evaluateSLACompliance(slaType, report);
                    
                    // Store metrics in cache for dashboard
                    cacheService.set("sla_metrics:" + slaType, report, 300); // 5 minutes
                }
            }
            
        } catch (Exception e) {
            log.error("SLA_MONITORING: Error during SLA monitoring", e);
        }
    }

    /**
     * CRITICAL: Generate comprehensive SLA report
     */
    @Scheduled(cron = "0 */5 * * * ?") // Every 5 minutes
    public void generateSLAReports() {
        try {
            Map<String, Object> comprehensiveReport = new HashMap<>();
            comprehensiveReport.put("timestamp", Instant.now().toString());
            comprehensiveReport.put("reporting_period_minutes", 5);
            
            for (Map.Entry<String, SLAMetrics> entry : slaMetrics.entrySet()) {
                String slaType = entry.getKey();
                SLAMetrics metrics = entry.getValue();
                
                SLAReport report = generateSLAReport(slaType, metrics);
                comprehensiveReport.put(slaType, report);
                
                // Reset metrics for next period (optional - depends on requirements)
                if (shouldResetMetrics(slaType)) {
                    resetMetrics(metrics);
                }
            }
            
            // Publish report to Kafka for downstream processing
            publishSLAReport(comprehensiveReport);
            
            log.info("SLA_MONITORING: Comprehensive SLA report generated and published");
            
        } catch (Exception e) {
            log.error("SLA_MONITORING: Error generating SLA reports", e);
        }
    }

    /**
     * Check payment processing SLA
     */
    private void checkPaymentProcessingSLA() {
        SLAMetrics metrics = slaMetrics.get("payment_processing");
        double currentSLA = calculateSuccessRate(metrics);
        
        if (currentSLA < paymentProcessingSLA) {
            AlertThresholds thresholds = alertThresholds.get("payment_processing");
            
            if (shouldTriggerAlert("payment_processing", currentSLA)) {
                SLAViolationAlert alert = SLAViolationAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .slaType("PAYMENT_PROCESSING")
                        .targetSLA(paymentProcessingSLA)
                        .currentSLA(currentSLA)
                        .severity(determineSeverity(currentSLA, paymentProcessingSLA))
                        .timestamp(LocalDateTime.now())
                        .message(String.format("Payment processing SLA violation: %.2f%% (target: %.2f%%)", 
                                currentSLA, paymentProcessingSLA))
                        .build();
                
                alertingService.sendAlert(alert);
                updateAlertTimestamp("payment_processing");
                
                log.error("SLA_MONITORING: Payment processing SLA violation - Current: {:.2f}%, Target: {:.2f}%", 
                        currentSLA, paymentProcessingSLA);
            }
        }
    }

    /**
     * Check API availability SLA
     */
    private void checkApiAvailabilitySLA() {
        SLAMetrics metrics = slaMetrics.get("api_availability");
        double currentSLA = calculateSuccessRate(metrics);
        
        if (currentSLA < apiAvailabilitySLA) {
            if (shouldTriggerAlert("api_availability", currentSLA)) {
                SLAViolationAlert alert = SLAViolationAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .slaType("API_AVAILABILITY")
                        .targetSLA(apiAvailabilitySLA)
                        .currentSLA(currentSLA)
                        .severity(determineSeverity(currentSLA, apiAvailabilitySLA))
                        .timestamp(LocalDateTime.now())
                        .message(String.format("API availability SLA violation: %.2f%% (target: %.2f%%)", 
                                currentSLA, apiAvailabilitySLA))
                        .build();
                
                alertingService.sendAlert(alert);
                updateAlertTimestamp("api_availability");
                
                log.error("SLA_MONITORING: API availability SLA violation - Current: {:.2f}%, Target: {:.2f}%", 
                        currentSLA, apiAvailabilitySLA);
            }
        }
    }

    /**
     * Check response time SLA
     */
    private void checkResponseTimeSLA(String endpoint, long responseTime) {
        if (shouldTriggerAlert("response_time_" + endpoint, responseTime)) {
            SLAViolationAlert alert = SLAViolationAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .slaType("RESPONSE_TIME")
                    .targetSLA(responseTimeTarget)
                    .currentSLA(responseTime)
                    .severity(determineResponseTimeSeverity(responseTime))
                    .timestamp(LocalDateTime.now())
                    .message(String.format("Response time SLA violation on %s: %dms (target: %dms)", 
                            endpoint, responseTime, responseTimeTarget))
                    .metadata(Map.of("endpoint", endpoint, "response_time", responseTime))
                    .build();
            
            alertingService.sendAlert(alert);
            updateAlertTimestamp("response_time_" + endpoint);
            
            log.warn("SLA_MONITORING: Response time SLA violation - Endpoint: {}, Time: {}ms, Target: {}ms", 
                    endpoint, responseTime, responseTimeTarget);
        }
    }

    /**
     * Check transaction success SLA
     */
    private void checkTransactionSuccessSLA() {
        SLAMetrics metrics = slaMetrics.get("transaction_success");
        double currentSLA = calculateSuccessRate(metrics);
        
        if (currentSLA < transactionSuccessRateSLA) {
            if (shouldTriggerAlert("transaction_success", currentSLA)) {
                SLAViolationAlert alert = SLAViolationAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .slaType("TRANSACTION_SUCCESS")
                        .targetSLA(transactionSuccessRateSLA)
                        .currentSLA(currentSLA)
                        .severity(determineSeverity(currentSLA, transactionSuccessRateSLA))
                        .timestamp(LocalDateTime.now())
                        .message(String.format("Transaction success rate SLA violation: %.2f%% (target: %.2f%%)", 
                                currentSLA, transactionSuccessRateSLA))
                        .build();
                
                alertingService.sendAlert(alert);
                updateAlertTimestamp("transaction_success");
                
                log.error("SLA_MONITORING: Transaction success SLA violation - Current: {:.2f}%, Target: {:.2f}%", 
                        currentSLA, transactionSuccessRateSLA);
            }
        }
    }

    /**
     * Check fraud detection SLA
     */
    private void checkFraudDetectionSLA() {
        SLAMetrics metrics = slaMetrics.get("fraud_detection");
        double currentSLA = calculateSuccessRate(metrics);
        
        if (currentSLA < fraudDetectionAccuracySLA) {
            if (shouldTriggerAlert("fraud_detection", currentSLA)) {
                SLAViolationAlert alert = SLAViolationAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .slaType("FRAUD_DETECTION")
                        .targetSLA(fraudDetectionAccuracySLA)
                        .currentSLA(currentSLA)
                        .severity(determineSeverity(currentSLA, fraudDetectionAccuracySLA))
                        .timestamp(LocalDateTime.now())
                        .message(String.format("Fraud detection accuracy SLA violation: %.2f%% (target: %.2f%%)", 
                                currentSLA, fraudDetectionAccuracySLA))
                        .build();
                
                alertingService.sendAlert(alert);
                updateAlertTimestamp("fraud_detection");
                
                log.error("SLA_MONITORING: Fraud detection SLA violation - Current: {:.2f}%, Target: {:.2f}%", 
                        currentSLA, fraudDetectionAccuracySLA);
            }
        }
    }

    /**
     * Generate SLA report for a specific metric
     */
    private SLAReport generateSLAReport(String slaType, SLAMetrics metrics) {
        double successRate = calculateSuccessRate(metrics);
        double avgResponseTime = calculateAverageResponseTime(metrics);
        
        return SLAReport.builder()
                .slaType(slaType)
                .timestamp(LocalDateTime.now())
                .totalRequests(metrics.totalRequests.get())
                .successfulRequests(metrics.successfulRequests.get())
                .failedRequests(metrics.failedRequests.get())
                .successRate(successRate)
                .averageResponseTime(avgResponseTime)
                .totalTransactionValue(metrics.totalTransactionValue.sum())
                .slaCompliant(isSLACompliant(slaType, successRate, avgResponseTime))
                .build();
    }

    /**
     * Calculate success rate percentage
     */
    private double calculateSuccessRate(SLAMetrics metrics) {
        long total = metrics.totalRequests.get();
        if (total == 0) return 100.0;
        
        long successful = metrics.successfulRequests.get();
        return BigDecimal.valueOf(successful)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * Calculate average response time
     */
    private double calculateAverageResponseTime(SLAMetrics metrics) {
        long total = metrics.totalRequests.get();
        if (total == 0) return 0.0;
        
        return metrics.totalResponseTime.sum() / total;
    }

    /**
     * Check if SLA is compliant
     */
    private boolean isSLACompliant(String slaType, double successRate, double avgResponseTime) {
        switch (slaType) {
            case "payment_processing":
                return successRate >= paymentProcessingSLA;
            case "api_availability":
                return successRate >= apiAvailabilitySLA;
            case "response_time":
                return avgResponseTime <= responseTimeTarget;
            case "transaction_success":
                return successRate >= transactionSuccessRateSLA;
            case "fraud_detection":
                return successRate >= fraudDetectionAccuracySLA;
            default:
                return true;
        }
    }

    /**
     * Initialize alert thresholds
     */
    private void initializeAlertThresholds() {
        alertThresholds.put("payment_processing", new AlertThresholds(5 * 60 * 1000)); // 5 minutes
        alertThresholds.put("api_availability", new AlertThresholds(2 * 60 * 1000)); // 2 minutes
        alertThresholds.put("transaction_success", new AlertThresholds(10 * 60 * 1000)); // 10 minutes
        alertThresholds.put("fraud_detection", new AlertThresholds(15 * 60 * 1000)); // 15 minutes
    }

    /**
     * Check if alert should be triggered
     */
    private boolean shouldTriggerAlert(String alertKey, double currentValue) {
        AlertThresholds thresholds = alertThresholds.computeIfAbsent(alertKey, k -> new AlertThresholds(5 * 60 * 1000));
        
        long now = System.currentTimeMillis();
        long lastAlert = thresholds.lastAlertTime;
        
        // Don't trigger alert if within cooldown period
        return (now - lastAlert) >= thresholds.alertCooldownMs;
    }

    /**
     * Update alert timestamp
     */
    private void updateAlertTimestamp(String alertKey) {
        AlertThresholds thresholds = alertThresholds.get(alertKey);
        if (thresholds != null) {
            thresholds.lastAlertTime = System.currentTimeMillis();
        }
    }

    /**
     * Determine alert severity
     */
    private String determineSeverity(double current, double target) {
        double deviation = target - current;
        
        if (deviation > 5.0) {
            return "CRITICAL";
        } else if (deviation > 2.0) {
            return "HIGH";
        } else if (deviation > 1.0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Determine response time severity
     */
    private String determineResponseTimeSeverity(long responseTime) {
        if (responseTime > responseTimeTarget * 5) {
            return "CRITICAL";
        } else if (responseTime > responseTimeTarget * 3) {
            return "HIGH";
        } else if (responseTime > responseTimeTarget * 2) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Check if SLA should be evaluated
     */
    private boolean shouldCheckSLA(String slaType) {
        return !slaType.startsWith("endpoint_");
    }

    /**
     * Check if metrics should be reset
     */
    private boolean shouldResetMetrics(String slaType) {
        // Keep cumulative metrics for main SLA types
        return slaType.startsWith("endpoint_");
    }

    /**
     * Reset metrics
     */
    private void resetMetrics(SLAMetrics metrics) {
        metrics.totalRequests.set(0);
        metrics.successfulRequests.set(0);
        metrics.failedRequests.set(0);
        metrics.totalResponseTime.reset();
        metrics.totalTransactionValue.reset();
    }

    /**
     * Evaluate SLA compliance
     */
    private void evaluateSLACompliance(String slaType, SLAReport report) {
        if (!report.isSlaCompliant()) {
            log.warn("SLA_MONITORING: SLA violation detected for {} - Success rate: {:.2f}%, Avg response time: {:.2f}ms",
                    slaType, report.getSuccessRate(), report.getAverageResponseTime());
        } else {
            log.debug("SLA_MONITORING: SLA compliant for {} - Success rate: {:.2f}%, Avg response time: {:.2f}ms",
                    slaType, report.getSuccessRate(), report.getAverageResponseTime());
        }
    }

    /**
     * Publish SLA report to Kafka
     */
    private void publishSLAReport(Map<String, Object> report) {
        try {
            String reportJson = cacheService.getObjectMapper().writeValueAsString(report);
            kafkaTemplate.send("monitoring.sla.reports", reportJson);
            
        } catch (Exception e) {
            log.error("SLA_MONITORING: Failed to publish SLA report", e);
        }
    }

    /**
     * SLA Metrics storage class
     */
    private static class SLAMetrics {
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong successfulRequests = new AtomicLong(0);
        final AtomicLong failedRequests = new AtomicLong(0);
        final DoubleAdder totalResponseTime = new DoubleAdder();
        final DoubleAdder totalTransactionValue = new DoubleAdder();
    }

    /**
     * Alert thresholds storage class
     */
    private static class AlertThresholds {
        final long alertCooldownMs;
        volatile long lastAlertTime = 0;
        
        AlertThresholds(long alertCooldownMs) {
            this.alertCooldownMs = alertCooldownMs;
        }
    }
}