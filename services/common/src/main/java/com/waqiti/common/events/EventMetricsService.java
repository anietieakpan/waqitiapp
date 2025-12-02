package com.waqiti.common.events;

import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting metrics on financial events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventMetricsService {
    
    private final MetricsCollector metricsCollector;
    
    // Event counters
    private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventProcessingTimes = new ConcurrentHashMap<>();
    
    /**
     * Record payment event metrics
     */
    public void recordPaymentEventMetrics(PaymentEvent event, Duration processingTime) {
        try {
            // Record general metrics
            metricsCollector.recordFinancialTransaction(
                event.getEventType(),
                event.getStatus(),
                event.getAmount().doubleValue()
            );
            
            // Record event count
            String eventKey = "payment." + event.getEventType().toLowerCase();
            eventCounts.computeIfAbsent(eventKey, k -> new AtomicLong(0)).incrementAndGet();
            
            // Record processing time
            if (processingTime != null) {
                eventProcessingTimes.computeIfAbsent(eventKey, k -> new AtomicLong(0))
                    .addAndGet(processingTime.toMillis());
            }
            
            // Record payment method metrics
            recordPaymentMethodMetrics(event.getPaymentMethod(), event.getStatus());
            
            // Record currency metrics
            recordCurrencyMetrics(event.getCurrency(), event.getAmount().doubleValue());
            
        } catch (Exception e) {
            log.error("Failed to record payment event metrics for: {}", event.getPaymentId(), e);
        }
    }
    
    /**
     * Record transaction event metrics
     */
    public void recordTransactionEventMetrics(TransactionEvent event, Duration processingTime) {
        try {
            // Record general metrics
            metricsCollector.recordFinancialTransaction(
                event.getEventType(),
                event.getStatus(),
                event.getAmount().doubleValue()
            );
            
            // Record event count
            String eventKey = "transaction." + event.getEventType().toLowerCase();
            eventCounts.computeIfAbsent(eventKey, k -> new AtomicLong(0)).incrementAndGet();
            
            // Record processing time
            if (processingTime != null) {
                eventProcessingTimes.computeIfAbsent(eventKey, k -> new AtomicLong(0))
                    .addAndGet(processingTime.toMillis());
            }
            
            // Record transaction type metrics
            recordTransactionTypeMetrics(event.getTransactionType(), event.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to record transaction event metrics for: {}", event.getTransactionId(), e);
        }
    }
    
    /**
     * Record event processing metrics
     */
    public void recordEventProcessingMetrics(String eventType, Duration processingTime, boolean successful) {
        try {
            String status = successful ? "success" : "failure";
            String metricKey = "event.processing." + eventType.toLowerCase();
            
            // Record count
            eventCounts.computeIfAbsent(metricKey + "." + status, k -> new AtomicLong(0))
                .incrementAndGet();
            
            // Record processing time
            if (processingTime != null) {
                eventProcessingTimes.computeIfAbsent(metricKey, k -> new AtomicLong(0))
                    .addAndGet(processingTime.toMillis());
            }
            
        } catch (Exception e) {
            log.error("Failed to record event processing metrics for: {}", eventType, e);
        }
    }
    
    /**
     * Record fraud detection metrics
     */
    public void recordFraudDetectionMetrics(String riskLevel, boolean flagged) {
        try {
            String status = flagged ? "flagged" : "cleared";
            eventCounts.computeIfAbsent("fraud.detection." + riskLevel + "." + status, 
                k -> new AtomicLong(0)).incrementAndGet();
                
            if (flagged) {
                metricsCollector.recordSecurityEvent("fraud_detection", riskLevel);
            }
            
        } catch (Exception e) {
            log.error("Failed to record fraud detection metrics", e);
        }
    }
    
    /**
     * Record compliance check metrics
     */
    public void recordComplianceCheckMetrics(String checkType, String result, Duration checkTime) {
        try {
            String metricKey = "compliance." + checkType.toLowerCase() + "." + result.toLowerCase();
            eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
            
            if (checkTime != null) {
                eventProcessingTimes.computeIfAbsent("compliance." + checkType.toLowerCase(), 
                    k -> new AtomicLong(0)).addAndGet(checkTime.toMillis());
            }
            
        } catch (Exception e) {
            log.error("Failed to record compliance check metrics for: {}", checkType, e);
        }
    }
    
    /**
     * Record API event metrics
     */
    public void recordApiEventMetrics(String endpoint, String method, int statusCode, Duration responseTime) {
        try {
            String status = getStatusCategory(statusCode);
            metricsCollector.recordApiCall(endpoint, method, status, responseTime);
            
        } catch (Exception e) {
            log.error("Failed to record API event metrics for: {} {}", method, endpoint, e);
        }
    }
    
    /**
     * Get current event statistics
     */
    public Map<String, Long> getEventStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        eventCounts.forEach((key, value) -> stats.put(key, value.get()));
        return stats;
    }
    
    /**
     * Get average processing time for event type
     */
    public long getAverageProcessingTime(String eventType) {
        AtomicLong totalTime = eventProcessingTimes.get(eventType);
        AtomicLong count = eventCounts.get(eventType);
        
        if (totalTime == null || count == null || count.get() == 0) {
            return 0L;
        }
        
        return totalTime.get() / count.get();
    }
    
    /**
     * Reset all metrics (for testing or periodic cleanup)
     */
    public void resetMetrics() {
        eventCounts.clear();
        eventProcessingTimes.clear();
    }
    
    // Private helper methods
    
    private void recordPaymentMethodMetrics(String paymentMethod, String status) {
        String metricKey = "payment.method." + paymentMethod.toLowerCase() + "." + status.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private void recordCurrencyMetrics(String currency, double amount) {
        String metricKey = "payment.currency." + currency.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private void recordTransactionTypeMetrics(String transactionType, String status) {
        String metricKey = "transaction.type." + transactionType.toLowerCase() + "." + status.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private String getStatusCategory(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "success";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "client_error";
        } else if (statusCode >= 500) {
            return "server_error";
        } else {
            return "other";
        }
    }
    
    // Missing methods called by FinancialEventPublisher
    
    public void incrementPaymentEvent(String eventType) {
        String metricKey = "payment.event." + eventType.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementTransactionEvent(String eventType) {
        String metricKey = "transaction.event." + eventType.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementAccountEvent(String eventType) {
        String metricKey = "account.event." + eventType.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementUserEvent(String eventType) {
        String metricKey = "user.event." + eventType.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementFraudEvent(String riskLevel) {
        String metricKey = "fraud.event." + riskLevel.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementComplianceEvent(String complianceType) {
        String metricKey = "compliance.event." + complianceType.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementNotificationEvent(String channel) {
        String metricKey = "notification.event." + channel.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementAuditEvent(String action) {
        String metricKey = "audit.event." + action.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementBatchEvents(int count) {
        eventCounts.computeIfAbsent("batch.events.count", k -> new AtomicLong(0)).addAndGet(count);
    }
    
    public void recordPublishLatency(String topic, long latencyMs) {
        String metricKey = "publish.latency." + topic.toLowerCase();
        eventProcessingTimes.computeIfAbsent(metricKey, k -> new AtomicLong(0)).addAndGet(latencyMs);
    }
    
    public void incrementPublishFailure(String topic) {
        String metricKey = "publish.failure." + topic.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementHighPriorityFailure(String topic) {
        String metricKey = "publish.high_priority_failure." + topic.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementDeadLetterEvent(String originalTopic) {
        String metricKey = "dlq.event." + originalTopic.toLowerCase();
        eventCounts.computeIfAbsent(metricKey, k -> new AtomicLong(0)).incrementAndGet();
    }
}