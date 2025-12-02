package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.TransactionSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockedTransactionsConsumer {

    private final TransactionSecurityService transactionSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("blocked_transactions_processed_total")
            .description("Total number of successfully processed blocked transaction events")
            .register(meterRegistry);
        errorCounter = Counter.builder("blocked_transactions_errors_total")
            .description("Total number of blocked transaction processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("blocked_transactions_processing_duration")
            .description("Time taken to process blocked transaction events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"blocked-transactions", "transaction-blocks", "fraud-blocked-transactions"},
        groupId = "security-service-blocked-transactions-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "blocked-transactions", fallbackMethod = "handleBlockedTransactionFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBlockedTransaction(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("blocked-transaction-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String transactionId = (String) event.get("transactionId");
            String userId = (String) event.get("userId");
            String blockReason = (String) event.get("blockReason");
            String eventKey = String.format("%s-%s-%s", transactionId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing blocked transaction: transactionId={}, userId={}, reason={}",
                transactionId, userId, blockReason);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Double amount = ((Number) event.get("amount")).doubleValue();
            String currency = (String) event.get("currency");
            String merchantId = (String) event.get("merchantId");
            String merchantName = (String) event.get("merchantName");
            String cardId = (String) event.get("cardId");
            LocalDateTime blockedAt = LocalDateTime.parse((String) event.get("blockedAt"));
            String blockType = (String) event.get("blockType");
            String riskScore = String.valueOf(event.getOrDefault("riskScore", "0"));
            @SuppressWarnings("unchecked")
            List<String> riskFactors = (List<String>) event.getOrDefault("riskFactors", List.of());
            String deviceId = (String) event.get("deviceId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            @SuppressWarnings("unchecked")
            Map<String, Object> transactionData = (Map<String, Object>) event.getOrDefault("transactionData", Map.of());
            String blockedBy = (String) event.get("blockedBy");
            Boolean isAppealed = (Boolean) event.getOrDefault("isAppealed", false);
            String status = (String) event.getOrDefault("status", "BLOCKED");

            // Process blocked transaction based on block type
            switch (blockType) {
                case "FRAUD_SUSPECTED":
                    processFraudSuspectedBlock(transactionId, userId, amount, currency, merchantId,
                        cardId, blockedAt, riskScore, riskFactors, deviceId, ipAddress, correlationId);
                    break;

                case "VELOCITY_LIMIT_EXCEEDED":
                    processVelocityLimitBlock(transactionId, userId, amount, currency, merchantId,
                        cardId, blockedAt, transactionData, correlationId);
                    break;

                case "GEOGRAPHIC_ANOMALY":
                    processGeographicAnomalyBlock(transactionId, userId, amount, currency,
                        merchantId, cardId, location, ipAddress, blockedAt, correlationId);
                    break;

                case "MERCHANT_RISK":
                    processMerchantRiskBlock(transactionId, userId, amount, currency, merchantId,
                        merchantName, cardId, blockedAt, riskFactors, correlationId);
                    break;

                case "BEHAVIORAL_ANOMALY":
                    processBehavioralAnomalyBlock(transactionId, userId, amount, currency,
                        merchantId, cardId, blockedAt, riskScore, riskFactors, correlationId);
                    break;

                case "DEVICE_RISK":
                    processDeviceRiskBlock(transactionId, userId, amount, currency, merchantId,
                        cardId, deviceId, blockedAt, riskFactors, correlationId);
                    break;

                case "MANUAL_BLOCK":
                    processManualBlock(transactionId, userId, amount, currency, merchantId,
                        cardId, blockedAt, blockedBy, blockReason, correlationId);
                    break;

                default:
                    processGenericBlock(transactionId, userId, amount, currency, merchantId,
                        cardId, blockType, blockReason, blockedAt, correlationId);
                    break;
            }

            // Handle appeal if present
            if (isAppealed) {
                handleTransactionAppeal(transactionId, userId, blockType, blockReason,
                    transactionData, correlationId);
            }

            // Update security metrics
            updateSecurityMetrics(userId, blockType, blockReason, amount, riskScore, correlationId);

            // Notify relevant parties
            notifyStakeholders(transactionId, userId, blockType, blockReason, amount,
                merchantName, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BLOCKED_TRANSACTION_PROCESSED", userId,
                Map.of("transactionId", transactionId, "blockType", blockType, "blockReason", blockReason,
                    "amount", amount, "currency", currency, "merchantId", merchantId,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process blocked transaction event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("blocked-transactions-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBlockedTransactionFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("blocked-transaction-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for blocked transaction: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("blocked-transactions-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Blocked Transaction Circuit Breaker Triggered",
                String.format("Blocked transaction processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBlockedTransaction(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-blocked-transaction-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Blocked transaction permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String transactionId = (String) event.get("transactionId");
            String userId = (String) event.get("userId");
            String blockType = (String) event.get("blockType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BLOCKED_TRANSACTION_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "transactionId", transactionId, "blockType", blockType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Blocked Transaction Dead Letter Event",
                String.format("Blocked transaction %s for user %s sent to DLT: %s", transactionId, userId, exceptionMessage),
                Map.of("transactionId", transactionId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse blocked transaction DLT event: {}", eventJson, ex);
        }
    }

    private void processFraudSuspectedBlock(String transactionId, String userId, Double amount,
                                          String currency, String merchantId, String cardId,
                                          LocalDateTime blockedAt, String riskScore,
                                          List<String> riskFactors, String deviceId,
                                          String ipAddress, String correlationId) {
        try {
            transactionSecurityService.processFraudSuspectedBlock(transactionId, userId, amount,
                currency, merchantId, cardId, blockedAt, riskScore, riskFactors, deviceId, ipAddress);

            // Escalate to fraud team for high-risk transactions
            if (Double.parseDouble(riskScore) > 0.8) {
                threatResponseService.escalateToFraudTeam(transactionId, userId, amount,
                    riskScore, riskFactors, correlationId);
            }

            log.info("Fraud suspected block processed: transactionId={}, userId={}, riskScore={}",
                transactionId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process fraud suspected block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Fraud suspected block processing failed", e);
        }
    }

    private void processVelocityLimitBlock(String transactionId, String userId, Double amount,
                                         String currency, String merchantId, String cardId,
                                         LocalDateTime blockedAt, Map<String, Object> transactionData,
                                         String correlationId) {
        try {
            transactionSecurityService.processVelocityLimitBlock(transactionId, userId, amount,
                currency, merchantId, cardId, blockedAt, transactionData);

            log.info("Velocity limit block processed: transactionId={}, userId={}, amount={}",
                transactionId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process velocity limit block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Velocity limit block processing failed", e);
        }
    }

    private void processGeographicAnomalyBlock(String transactionId, String userId, Double amount,
                                             String currency, String merchantId, String cardId,
                                             String location, String ipAddress, LocalDateTime blockedAt,
                                             String correlationId) {
        try {
            transactionSecurityService.processGeographicAnomalyBlock(transactionId, userId, amount,
                currency, merchantId, cardId, location, ipAddress, blockedAt);

            log.info("Geographic anomaly block processed: transactionId={}, userId={}, location={}",
                transactionId, userId, location);

        } catch (Exception e) {
            log.error("Failed to process geographic anomaly block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Geographic anomaly block processing failed", e);
        }
    }

    private void processMerchantRiskBlock(String transactionId, String userId, Double amount,
                                        String currency, String merchantId, String merchantName,
                                        String cardId, LocalDateTime blockedAt, List<String> riskFactors,
                                        String correlationId) {
        try {
            transactionSecurityService.processMerchantRiskBlock(transactionId, userId, amount,
                currency, merchantId, merchantName, cardId, blockedAt, riskFactors);

            log.info("Merchant risk block processed: transactionId={}, userId={}, merchantId={}",
                transactionId, userId, merchantId);

        } catch (Exception e) {
            log.error("Failed to process merchant risk block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Merchant risk block processing failed", e);
        }
    }

    private void processBehavioralAnomalyBlock(String transactionId, String userId, Double amount,
                                             String currency, String merchantId, String cardId,
                                             LocalDateTime blockedAt, String riskScore,
                                             List<String> riskFactors, String correlationId) {
        try {
            transactionSecurityService.processBehavioralAnomalyBlock(transactionId, userId, amount,
                currency, merchantId, cardId, blockedAt, riskScore, riskFactors);

            log.info("Behavioral anomaly block processed: transactionId={}, userId={}, score={}",
                transactionId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process behavioral anomaly block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Behavioral anomaly block processing failed", e);
        }
    }

    private void processDeviceRiskBlock(String transactionId, String userId, Double amount,
                                      String currency, String merchantId, String cardId,
                                      String deviceId, LocalDateTime blockedAt, List<String> riskFactors,
                                      String correlationId) {
        try {
            transactionSecurityService.processDeviceRiskBlock(transactionId, userId, amount,
                currency, merchantId, cardId, deviceId, blockedAt, riskFactors);

            log.info("Device risk block processed: transactionId={}, userId={}, deviceId={}",
                transactionId, userId, deviceId);

        } catch (Exception e) {
            log.error("Failed to process device risk block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Device risk block processing failed", e);
        }
    }

    private void processManualBlock(String transactionId, String userId, Double amount,
                                  String currency, String merchantId, String cardId,
                                  LocalDateTime blockedAt, String blockedBy, String blockReason,
                                  String correlationId) {
        try {
            transactionSecurityService.processManualBlock(transactionId, userId, amount,
                currency, merchantId, cardId, blockedAt, blockedBy, blockReason);

            log.info("Manual block processed: transactionId={}, userId={}, blockedBy={}",
                transactionId, userId, blockedBy);

        } catch (Exception e) {
            log.error("Failed to process manual block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Manual block processing failed", e);
        }
    }

    private void processGenericBlock(String transactionId, String userId, Double amount,
                                   String currency, String merchantId, String cardId,
                                   String blockType, String blockReason, LocalDateTime blockedAt,
                                   String correlationId) {
        try {
            transactionSecurityService.processGenericBlock(transactionId, userId, amount,
                currency, merchantId, cardId, blockType, blockReason, blockedAt);

            log.info("Generic block processed: transactionId={}, userId={}, type={}",
                transactionId, userId, blockType);

        } catch (Exception e) {
            log.error("Failed to process generic block: transactionId={}, userId={}",
                transactionId, userId, e);
            throw new RuntimeException("Generic block processing failed", e);
        }
    }

    private void handleTransactionAppeal(String transactionId, String userId, String blockType,
                                       String blockReason, Map<String, Object> transactionData,
                                       String correlationId) {
        try {
            transactionSecurityService.handleTransactionAppeal(transactionId, userId, blockType,
                blockReason, transactionData);

            log.info("Transaction appeal handled: transactionId={}, userId={}, blockType={}",
                transactionId, userId, blockType);

        } catch (Exception e) {
            log.error("Failed to handle transaction appeal: transactionId={}, userId={}",
                transactionId, userId, e);
            // Don't throw exception as appeal handling failure shouldn't block processing
        }
    }

    private void updateSecurityMetrics(String userId, String blockType, String blockReason,
                                     Double amount, String riskScore, String correlationId) {
        try {
            transactionSecurityService.updateSecurityMetrics(userId, blockType, blockReason,
                amount, riskScore);

            log.debug("Security metrics updated: userId={}, blockType={}, amount={}",
                userId, blockType, amount);

        } catch (Exception e) {
            log.error("Failed to update security metrics: userId={}, blockType={}",
                userId, blockType, e);
            // Don't throw exception as metrics update failure shouldn't block processing
        }
    }

    private void notifyStakeholders(String transactionId, String userId, String blockType,
                                  String blockReason, Double amount, String merchantName,
                                  String correlationId) {
        try {
            // Notify user about blocked transaction
            securityNotificationService.notifyUserTransactionBlocked(userId, transactionId,
                amount, merchantName, blockReason);

            // Notify merchant if necessary
            if ("MERCHANT_RISK".equals(blockType)) {
                securityNotificationService.notifyMerchantTransactionBlocked(transactionId,
                    amount, blockReason);
            }

            log.debug("Stakeholders notified: transactionId={}, userId={}, blockType={}",
                transactionId, userId, blockType);

        } catch (Exception e) {
            log.error("Failed to notify stakeholders: transactionId={}, userId={}",
                transactionId, userId, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}