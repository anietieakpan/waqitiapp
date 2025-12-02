package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.ChargebackService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargebackEventsConsumer {

    private final ChargebackService chargebackService;
    private final PaymentNotificationService paymentNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "chargeback-events:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    
    @KafkaListener(
        topics = {"chargeback-events", "chargeback-initiated", "chargeback-representment"},
        groupId = "payment-service-chargeback-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleChargebackEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Build idempotency key for duplicate prevention
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + topic + ":" + partition + ":" + offset;

        // Check if already processed (PCI DSS: Prevent duplicate chargeback processing)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
            log.info("CHARGEBACK: Duplicate event detected, skipping - Idempotency key: {}", idempotencyKey);
            acknowledgment.acknowledge();
            return;
        }

        log.info("CHARGEBACK: Processing chargeback event - Topic: {}, Partition: {}, Offset: {}",
                topic, partition, offset);

        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID chargebackId = null;
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            chargebackId = UUID.fromString((String) event.get("chargebackId"));
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String chargebackStatus = (String) event.get("chargebackStatus");
            String reasonCode = (String) event.get("reasonCode");
            String reasonDescription = (String) event.get("reasonDescription");
            BigDecimal chargebackAmount = new BigDecimal(event.get("chargebackAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime chargebackDate = LocalDateTime.parse((String) event.get("chargebackDate"));
            String merchantId = (String) event.get("merchantId");
            String cardNetwork = (String) event.get("cardNetwork");
            String acquirerReferenceNumber = (String) event.get("acquirerReferenceNumber");
            String issuerReferenceNumber = (String) event.getOrDefault("issuerReferenceNumber", "");
            LocalDateTime responseDeadline = LocalDateTime.parse((String) event.get("responseDeadline"));
            Boolean isPreArbitration = (Boolean) event.getOrDefault("isPreArbitration", false);
            Boolean isFriendlyFraud = (Boolean) event.getOrDefault("isFriendlyFraud", false);
            String liabilityShift = (String) event.getOrDefault("liabilityShift", "MERCHANT");
            
            log.warn("Chargeback event - ChargebackId: {}, TransactionId: {}, CustomerId: {}, EventType: {}, Status: {}, Amount: {} {}, ReasonCode: {}", 
                    chargebackId, transactionId, customerId, eventType, chargebackStatus, 
                    chargebackAmount, currency, reasonCode);
            
            validateChargebackEvent(chargebackId, transactionId, customerId, eventType, 
                    chargebackAmount, reasonCode);
            
            switch (eventType) {
                case "CHARGEBACK_INITIATED" -> processChargebackInitiated(chargebackId, transactionId, 
                        customerId, reasonCode, reasonDescription, chargebackAmount, currency, 
                        chargebackDate, merchantId, cardNetwork, acquirerReferenceNumber, 
                        responseDeadline, isPreArbitration, isFriendlyFraud, liabilityShift);
                
                case "CHARGEBACK_ACCEPTED" -> processChargebackAccepted(chargebackId, transactionId, 
                        customerId, chargebackAmount, currency, merchantId, liabilityShift);
                
                case "CHARGEBACK_DISPUTED" -> processChargebackDisputed(chargebackId, transactionId, 
                        customerId, chargebackAmount, currency, responseDeadline, merchantId);
                
                case "REPRESENTMENT_SUBMITTED" -> processRepresentmentSubmitted(chargebackId, 
                        transactionId, customerId, chargebackAmount, currency, merchantId, 
                        cardNetwork, responseDeadline);
                
                case "REPRESENTMENT_ACCEPTED" -> processRepresentmentAccepted(chargebackId, 
                        transactionId, customerId, chargebackAmount, currency, merchantId);
                
                case "REPRESENTMENT_REJECTED" -> processRepresentmentRejected(chargebackId, 
                        transactionId, customerId, chargebackAmount, currency, reasonCode, merchantId);
                
                case "ARBITRATION_FILED" -> processArbitrationFiled(chargebackId, transactionId, 
                        customerId, chargebackAmount, currency, cardNetwork, merchantId);
                
                default -> {
                    log.warn("Unknown chargeback event type: {}", eventType);
                    processGenericEvent(chargebackId, transactionId, customerId, eventType);
                }
            }
            
            notifyStakeholders(customerId, chargebackId, transactionId, eventType, chargebackStatus, 
                    chargebackAmount, currency, merchantId, responseDeadline);
            
            updateChargebackMetrics(eventType, chargebackStatus, reasonCode, chargebackAmount, 
                    cardNetwork, isPreArbitration, isFriendlyFraud, liabilityShift);
            
            auditChargebackEvent(chargebackId, transactionId, customerId, eventType, chargebackStatus, 
                    reasonCode, chargebackAmount, currency, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();

            log.warn("Chargeback event processed - ChargebackId: {}, EventType: {}, Status: {}, ProcessingTime: {}ms",
                    chargebackId, eventType, chargebackStatus, processingTimeMs);

            // Mark as processed in Redis (prevent duplicates)
            redisTemplate.opsForValue().set(
                idempotencyKey,
                "processed",
                IDEMPOTENCY_TTL_HOURS,
                TimeUnit.HOURS
            );

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Chargeback event processing failed - ChargebackId: {}, TransactionId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    chargebackId, transactionId, customerId, eventType, e.getMessage(), e);
            
            if (chargebackId != null && customerId != null) {
                handleEventFailure(chargebackId, transactionId, customerId, eventType, e);
            }
            
            throw new RuntimeException("Chargeback event processing failed", e);
        }
    }
    
    private void validateChargebackEvent(UUID chargebackId, UUID transactionId, UUID customerId,
                                       String eventType, BigDecimal chargebackAmount, String reasonCode) {
        if (chargebackId == null || transactionId == null || customerId == null) {
            throw new IllegalArgumentException("Chargeback ID, Transaction ID, and Customer ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (chargebackAmount == null || chargebackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid chargeback amount");
        }
        
        if (reasonCode == null || reasonCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason code is required");
        }
        
        log.debug("Chargeback event validation passed - ChargebackId: {}", chargebackId);
    }
    
    private void processChargebackInitiated(UUID chargebackId, UUID transactionId, UUID customerId,
                                          String reasonCode, String reasonDescription, BigDecimal chargebackAmount,
                                          String currency, LocalDateTime chargebackDate, String merchantId,
                                          String cardNetwork, String acquirerReferenceNumber,
                                          LocalDateTime responseDeadline, Boolean isPreArbitration,
                                          Boolean isFriendlyFraud, String liabilityShift) {
        log.error("Processing CHARGEBACK INITIATED - ChargebackId: {}, TransactionId: {}, Amount: {} {}, ReasonCode: {}", 
                chargebackId, transactionId, chargebackAmount, currency, reasonCode);
        
        chargebackService.processChargebackInitiated(chargebackId, transactionId, customerId, 
                reasonCode, reasonDescription, chargebackAmount, currency, chargebackDate, 
                merchantId, cardNetwork, acquirerReferenceNumber, responseDeadline, 
                isPreArbitration, isFriendlyFraud, liabilityShift);
    }
    
    private void processChargebackAccepted(UUID chargebackId, UUID transactionId, UUID customerId,
                                         BigDecimal chargebackAmount, String currency, String merchantId,
                                         String liabilityShift) {
        log.warn("Processing CHARGEBACK ACCEPTED - ChargebackId: {}, Amount: {} {}, Liability: {}", 
                chargebackId, chargebackAmount, currency, liabilityShift);
        
        chargebackService.processChargebackAccepted(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, merchantId, liabilityShift);
    }
    
    private void processChargebackDisputed(UUID chargebackId, UUID transactionId, UUID customerId,
                                         BigDecimal chargebackAmount, String currency, 
                                         LocalDateTime responseDeadline, String merchantId) {
        log.info("Processing CHARGEBACK DISPUTED - ChargebackId: {}, Amount: {} {}, Deadline: {}", 
                chargebackId, chargebackAmount, currency, responseDeadline);
        
        chargebackService.processChargebackDisputed(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, responseDeadline, merchantId);
    }
    
    private void processRepresentmentSubmitted(UUID chargebackId, UUID transactionId, UUID customerId,
                                             BigDecimal chargebackAmount, String currency, String merchantId,
                                             String cardNetwork, LocalDateTime responseDeadline) {
        log.info("Processing REPRESENTMENT SUBMITTED - ChargebackId: {}, Amount: {} {}, Network: {}", 
                chargebackId, chargebackAmount, currency, cardNetwork);
        
        chargebackService.processRepresentmentSubmitted(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, merchantId, cardNetwork, responseDeadline);
    }
    
    private void processRepresentmentAccepted(UUID chargebackId, UUID transactionId, UUID customerId,
                                            BigDecimal chargebackAmount, String currency, String merchantId) {
        log.info("Processing REPRESENTMENT ACCEPTED - ChargebackId: {}, Amount: {} {}", 
                chargebackId, chargebackAmount, currency);
        
        chargebackService.processRepresentmentAccepted(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, merchantId);
    }
    
    private void processRepresentmentRejected(UUID chargebackId, UUID transactionId, UUID customerId,
                                            BigDecimal chargebackAmount, String currency, String reasonCode,
                                            String merchantId) {
        log.warn("Processing REPRESENTMENT REJECTED - ChargebackId: {}, Amount: {} {}, ReasonCode: {}", 
                chargebackId, chargebackAmount, currency, reasonCode);
        
        chargebackService.processRepresentmentRejected(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, reasonCode, merchantId);
    }
    
    private void processArbitrationFiled(UUID chargebackId, UUID transactionId, UUID customerId,
                                       BigDecimal chargebackAmount, String currency, String cardNetwork,
                                       String merchantId) {
        log.error("Processing ARBITRATION FILED - ChargebackId: {}, Amount: {} {}, Network: {}", 
                chargebackId, chargebackAmount, currency, cardNetwork);
        
        chargebackService.processArbitrationFiled(chargebackId, transactionId, customerId, 
                chargebackAmount, currency, cardNetwork, merchantId);
    }
    
    private void processGenericEvent(UUID chargebackId, UUID transactionId, UUID customerId, String eventType) {
        log.info("Processing generic chargeback event - ChargebackId: {}, EventType: {}", 
                chargebackId, eventType);
        
        chargebackService.processGenericEvent(chargebackId, transactionId, customerId, eventType);
    }
    
    private void notifyStakeholders(UUID customerId, UUID chargebackId, UUID transactionId,
                                  String eventType, String chargebackStatus, BigDecimal chargebackAmount,
                                  String currency, String merchantId, LocalDateTime responseDeadline) {
        try {
            paymentNotificationService.sendChargebackNotification(customerId, chargebackId, transactionId, 
                    eventType, chargebackStatus, chargebackAmount, currency, merchantId, responseDeadline);
            
            log.warn("Chargeback stakeholders notified - ChargebackId: {}, EventType: {}", 
                    chargebackId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to notify stakeholders - ChargebackId: {}", chargebackId, e);
        }
    }
    
    private void updateChargebackMetrics(String eventType, String chargebackStatus, String reasonCode,
                                       BigDecimal chargebackAmount, String cardNetwork, Boolean isPreArbitration,
                                       Boolean isFriendlyFraud, String liabilityShift) {
        try {
            chargebackService.updateChargebackMetrics(eventType, chargebackStatus, reasonCode, 
                    chargebackAmount, cardNetwork, isPreArbitration, isFriendlyFraud, liabilityShift);
        } catch (Exception e) {
            log.error("Failed to update chargeback metrics - EventType: {}, Status: {}", 
                    eventType, chargebackStatus, e);
        }
    }
    
    private void auditChargebackEvent(UUID chargebackId, UUID transactionId, UUID customerId, 
                                    String eventType, String chargebackStatus, String reasonCode,
                                    BigDecimal chargebackAmount, String currency, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "CHARGEBACK_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Chargeback event %s - Status: %s, ReasonCode: %s, Amount: %s %s", 
                            eventType, chargebackStatus, reasonCode, chargebackAmount, currency),
                    Map.of(
                            "chargebackId", chargebackId.toString(),
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "chargebackStatus", chargebackStatus,
                            "reasonCode", reasonCode,
                            "chargebackAmount", chargebackAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit chargeback event - ChargebackId: {}", chargebackId, e);
        }
    }
    
    private void handleEventFailure(UUID chargebackId, UUID transactionId, UUID customerId,
                                   String eventType, Exception error) {
        try {
            chargebackService.handleEventFailure(chargebackId, transactionId, customerId, eventType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "CHARGEBACK_EVENT_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process chargeback event: " + error.getMessage(),
                    Map.of(
                            "chargebackId", chargebackId.toString(),
                            "transactionId", transactionId != null ? transactionId.toString() : "UNKNOWN",
                            "customerId", customerId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - ChargebackId: {}", chargebackId, e);
        }
    }
    
    @KafkaListener(
        topics = {"chargeback-events.DLQ", "chargeback-initiated.DLQ", "chargeback-representment.DLQ"},
        groupId = "payment-service-chargeback-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Chargeback event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID chargebackId = event.containsKey("chargebackId") ? 
                    UUID.fromString((String) event.get("chargebackId")) : null;
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Chargeback event failed permanently - ChargebackId: {}, TransactionId: {}, CustomerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    chargebackId, transactionId, customerId, eventType);
            
            if (chargebackId != null && customerId != null) {
                chargebackService.markForManualReview(chargebackId, transactionId, customerId, 
                        eventType, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse chargeback DLQ event: {}", eventJson, e);
        }
    }
}