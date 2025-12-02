package com.waqiti.analytics.service;

import com.waqiti.analytics.model.*;
import com.waqiti.analytics.processor.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise-grade stream processing service for real-time analytics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamProcessingService {

    private final TransactionAnalyticsProcessor transactionProcessor;
    private final UserAnalyticsProcessor userProcessor;
    private final MerchantAnalyticsProcessor merchantProcessor;
    private final FraudAnalyticsProcessor fraudProcessor;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, List<PaymentEvent>> realtimeBuffer = new ConcurrentHashMap<>();
    private final Map<String, TransactionAggregate> aggregateCache = new ConcurrentHashMap<>();
    
    private static final String PROCESSED_EVENTS_TOPIC = "processed-analytics-events";
    private static final int BUFFER_SIZE = 1000;
    private static final int AGGREGATION_WINDOW_MINUTES = 5;

    /**
     * Process real-time payment events stream
     */
    @KafkaListener(topics = "payment-events", groupId = "stream-processing")
    public void processPaymentEventStream(PaymentEvent event) {
        try {
            log.debug("Processing payment event stream: {}", event.getTransactionId());
            
            // Buffer events for aggregation
            bufferEvent(event);
            
            // Process individual event
            processIndividualEvent(event);
            
            // Check for aggregation triggers
            checkAggregationTriggers(event);
            
        } catch (Exception e) {
            log.error("Error processing payment event stream", e);
        }
    }

    /**
     * Process fraud events stream
     */
    @KafkaListener(topics = "fraud-events", groupId = "stream-processing")
    public void processFraudEventStream(FraudEvent event) {
        try {
            log.debug("Processing fraud event stream: {}", event.getEventId());
            
            // Analyze fraud patterns in real-time
            fraudProcessor.analyzeTransaction(createPaymentEventFromFraud(event));
            
            // Trigger immediate alerts for high-risk events
            if (event.getRiskScore() != null && 
                event.getRiskScore().compareTo(BigDecimal.valueOf(85)) > 0) {
                triggerHighRiskAlert(event);
            }
            
        } catch (Exception e) {
            log.error("Error processing fraud event stream", e);
        }
    }

    /**
     * Process user behavior events stream
     */
    @KafkaListener(topics = "user-behavior-events", groupId = "stream-processing")
    public void processUserBehaviorStream(UserBehaviorEvent event) {
        try {
            log.debug("Processing user behavior event: {}", event.getEventId());
            
            // Process behavior patterns
            userProcessor.processUserBehavior(event);
            
            // Detect anomalous behavior patterns
            detectBehavioralAnomalies(event);
            
        } catch (Exception e) {
            log.error("Error processing user behavior event stream", e);
        }
    }

    /**
     * Buffer events for aggregation
     */
    private void bufferEvent(PaymentEvent event) {
        String bufferKey = generateBufferKey(event);
        List<PaymentEvent> buffer = realtimeBuffer.computeIfAbsent(bufferKey, k -> new ArrayList<>());
        
        synchronized (buffer) {
            buffer.add(event);
            
            // Trigger aggregation if buffer is full or time window elapsed
            if (buffer.size() >= BUFFER_SIZE || shouldTriggerTimeBasedAggregation(bufferKey)) {
                triggerAggregation(bufferKey, new ArrayList<>(buffer));
                buffer.clear();
            }
        }
    }

    /**
     * Process individual event
     */
    private void processIndividualEvent(PaymentEvent event) {
        // Real-time processing for immediate analytics
        transactionProcessor.processTransactionEvent(event);
        userProcessor.processUserTransaction(event);
        merchantProcessor.processMerchantTransaction(event);
    }

    /**
     * Check aggregation triggers
     */
    private void checkAggregationTriggers(PaymentEvent event) {
        String userKey = "user-" + event.getCustomerId();
        String merchantKey = "merchant-" + event.getMerchantId();
        
        // Check if user transactions need aggregation
        if (shouldAggregateUserTransactions(event.getCustomerId())) {
            aggregateUserTransactions(event.getCustomerId());
        }
        
        // Check if merchant transactions need aggregation
        if (shouldAggregateMerchantTransactions(event.getMerchantId())) {
            aggregateMerchantTransactions(event.getMerchantId());
        }
    }

    /**
     * Trigger aggregation
     */
    private void triggerAggregation(String bufferKey, List<PaymentEvent> events) {
        try {
            TransactionAggregate aggregate = createTransactionAggregate(events);
            
            // Cache aggregate
            aggregateCache.put(bufferKey, aggregate);
            
            // Publish aggregated data
            publishAggregatedData(aggregate);
            
            log.debug("Aggregated {} events for key: {}", events.size(), bufferKey);
            
        } catch (Exception e) {
            log.error("Error triggering aggregation for key: {}", bufferKey, e);
        }
    }

    /**
     * Create transaction aggregate from events
     */
    private TransactionAggregate createTransactionAggregate(List<PaymentEvent> events) {
        if (events.isEmpty()) {
            return null;
        }
        
        Instant windowStart = events.stream()
                .map(PaymentEvent::getTimestamp)
                .min(Instant::compareTo)
                .orElse(Instant.now());
        
        Instant windowEnd = events.stream()
                .map(PaymentEvent::getTimestamp)
                .max(Instant::compareTo)
                .orElse(Instant.now());
        
        // Calculate metrics
        long count = events.size();
        BigDecimal totalAmount = events.stream()
                .map(PaymentEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgAmount = totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        
        BigDecimal maxAmount = events.stream()
                .map(PaymentEvent::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        BigDecimal minAmount = events.stream()
                .map(PaymentEvent::getAmount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        // Group by status
        Map<String, Long> statusCounts = events.stream()
                .collect(Collectors.groupingBy(PaymentEvent::getStatus, Collectors.counting()));
        
        // Group by payment method
        Map<String, Long> paymentMethodCounts = events.stream()
                .collect(Collectors.groupingBy(PaymentEvent::getPaymentMethod, Collectors.counting()));
        
        // Group by currency
        Map<String, BigDecimal> currencyTotals = events.stream()
                .collect(Collectors.groupingBy(
                        PaymentEvent::getCurrency,
                        Collectors.reducing(BigDecimal.ZERO, PaymentEvent::getAmount, BigDecimal::add)
                ));
        
        return TransactionAggregate.builder()
                .aggregateId(UUID.randomUUID().toString())
                .count(count)
                .totalAmount(totalAmount)
                .averageAmount(avgAmount)
                .maxAmount(maxAmount)
                .minAmount(minAmount)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .timeFrame("REALTIME")
                .statusCounts(statusCounts)
                .paymentMethodCounts(paymentMethodCounts)
                .currencyTotals(currencyTotals)
                .amounts(events.stream().map(PaymentEvent::getAmount).collect(Collectors.toList()))
                .build();
    }

    /**
     * Publish aggregated data
     */
    private void publishAggregatedData(TransactionAggregate aggregate) {
        try {
            kafkaTemplate.send(PROCESSED_EVENTS_TOPIC, aggregate.getAggregateId(), aggregate);
        } catch (Exception e) {
            log.error("Error publishing aggregated data", e);
        }
    }

    /**
     * Generate buffer key for events
     */
    private String generateBufferKey(PaymentEvent event) {
        // Create time-based partitioned key
        long windowStart = event.getTimestamp().truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
        return "buffer-" + windowStart / (AGGREGATION_WINDOW_MINUTES * 60 * 1000);
    }

    /**
     * Check if time-based aggregation should trigger
     */
    private boolean shouldTriggerTimeBasedAggregation(String bufferKey) {
        // Extract timestamp from buffer key and check if window has elapsed
        try {
            String[] parts = bufferKey.split("-");
            long windowStart = Long.parseLong(parts[1]) * (AGGREGATION_WINDOW_MINUTES * 60 * 1000);
            Instant windowTime = Instant.ofEpochMilli(windowStart);
            
            return Instant.now().isAfter(windowTime.plus(AGGREGATION_WINDOW_MINUTES, ChronoUnit.MINUTES));
        } catch (Exception e) {
            return true; // Trigger aggregation on error
        }
    }

    /**
     * Check if user transactions should be aggregated
     */
    private boolean shouldAggregateUserTransactions(String userId) {
        // Simple rule: aggregate every 100 transactions
        List<PaymentEvent> userBuffer = realtimeBuffer.get("user-" + userId);
        return userBuffer != null && userBuffer.size() >= 100;
    }

    /**
     * Check if merchant transactions should be aggregated
     */
    private boolean shouldAggregateMerchantTransactions(String merchantId) {
        // Simple rule: aggregate every 200 transactions
        List<PaymentEvent> merchantBuffer = realtimeBuffer.get("merchant-" + merchantId);
        return merchantBuffer != null && merchantBuffer.size() >= 200;
    }

    /**
     * Aggregate user transactions
     */
    private void aggregateUserTransactions(String userId) {
        List<PaymentEvent> userEvents = realtimeBuffer.get("user-" + userId);
        if (userEvents != null && !userEvents.isEmpty()) {
            TransactionAggregate aggregate = createTransactionAggregate(userEvents);
            if (aggregate != null) {
                aggregate.setUserId(userId);
                publishAggregatedData(aggregate);
                userEvents.clear();
            }
        }
    }

    /**
     * Aggregate merchant transactions
     */
    private void aggregateMerchantTransactions(String merchantId) {
        List<PaymentEvent> merchantEvents = realtimeBuffer.get("merchant-" + merchantId);
        if (merchantEvents != null && !merchantEvents.isEmpty()) {
            TransactionAggregate aggregate = createTransactionAggregate(merchantEvents);
            if (aggregate != null) {
                aggregate.setMerchantId(merchantId);
                publishAggregatedData(aggregate);
                merchantEvents.clear();
            }
        }
    }

    /**
     * Trigger high-risk alert
     */
    private void triggerHighRiskAlert(FraudEvent event) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "HIGH_RISK_TRANSACTION");
            alert.put("eventId", event.getEventId());
            alert.put("transactionId", event.getTransactionId());
            alert.put("customerId", event.getCustomerId());
            alert.put("riskScore", event.getRiskScore());
            alert.put("timestamp", Instant.now());
            
            kafkaTemplate.send("fraud-alerts", event.getEventId(), alert);
            
        } catch (Exception e) {
            log.error("Error triggering high-risk alert", e);
        }
    }

    /**
     * Detect behavioral anomalies
     */
    private void detectBehavioralAnomalies(UserBehaviorEvent event) {
        // Simple anomaly detection based on event patterns
        if ("LOGIN_FAILURE".equals(event.getEventType())) {
            // Check for multiple login failures
            checkMultipleLoginFailures(event.getUserId());
        }
        
        if ("UNUSUAL_DEVICE".equals(event.getEventType())) {
            // Flag unusual device usage
            flagUnusualDeviceUsage(event);
        }
    }

    /**
     * Check for multiple login failures
     */
    private void checkMultipleLoginFailures(String userId) {
        // Implementation would check recent login failures
        log.warn("Multiple login failures detected for user: {}", userId);
    }

    /**
     * Flag unusual device usage
     */
    private void flagUnusualDeviceUsage(UserBehaviorEvent event) {
        // Implementation would flag unusual device patterns
        log.warn("Unusual device usage detected: {} for user: {}", event.getDeviceId(), event.getUserId());
    }

    /**
     * Convert fraud event to payment event
     */
    private PaymentEvent createPaymentEventFromFraud(FraudEvent fraudEvent) {
        return PaymentEvent.builder()
                .transactionId(fraudEvent.getTransactionId())
                .customerId(fraudEvent.getCustomerId())
                .merchantId(fraudEvent.getMerchantId())
                .amount(fraudEvent.getAmount())
                .currency(fraudEvent.getCurrency())
                .paymentMethod(fraudEvent.getPaymentMethod())
                .status(fraudEvent.getStatus())
                .timestamp(fraudEvent.getTimestamp())
                .location(fraudEvent.getLocation())
                .deviceId(fraudEvent.getDeviceId())
                .ipAddress(fraudEvent.getIpAddress())
                .metadata(fraudEvent.getMetadata())
                .build();
    }

    /**
     * Get stream processing statistics
     */
    public StreamProcessingStats getProcessingStats() {
        int totalBufferedEvents = realtimeBuffer.values().stream()
                .mapToInt(List::size)
                .sum();
        
        return StreamProcessingStats.builder()
                .totalBufferedEvents(totalBufferedEvents)
                .totalAggregates(aggregateCache.size())
                .bufferKeys(realtimeBuffer.keySet().size())
                .lastProcessedAt(Instant.now())
                .build();
    }

    /**
     * Clear old data
     */
    public void clearOldData() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        
        // Clear old buffer data
        realtimeBuffer.entrySet().removeIf(entry -> {
            List<PaymentEvent> events = entry.getValue();
            if (events.isEmpty()) return true;
            
            Instant lastEventTime = events.stream()
                    .map(PaymentEvent::getTimestamp)
                    .max(Instant::compareTo)
                    .orElse(Instant.EPOCH);
            
            return lastEventTime.isBefore(cutoff);
        });
        
        // Clear old aggregates
        aggregateCache.entrySet().removeIf(entry -> {
            TransactionAggregate aggregate = entry.getValue();
            return aggregate.getWindowEnd().isBefore(cutoff);
        });
    }
}