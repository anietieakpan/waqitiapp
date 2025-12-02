package com.waqiti.card.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.card.service.CardAuthorizationService;
import com.waqiti.card.service.CardFraudDetectionService;
import com.waqiti.card.service.CardNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardAuthorizationConsumer {
    
    private final CardAuthorizationService cardAuthorizationService;
    private final CardFraudDetectionService cardFraudDetectionService;
    private final CardNotificationService cardNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"card-authorization-events", "card-authorization"},
        groupId = "card-service-authorization-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleCardAuthorization(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("CARD AUTHORIZATION: Processing authorization event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID authorizationId = null;
        UUID cardId = null;
        String authorizationStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            authorizationId = UUID.fromString((String) event.get("authorizationId"));
            cardId = UUID.fromString((String) event.get("cardId"));
            UUID userId = UUID.fromString((String) event.get("userId"));
            String authorizationType = (String) event.get("authorizationType");
            authorizationStatus = (String) event.get("authorizationStatus");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            String merchantName = (String) event.get("merchantName");
            String merchantId = (String) event.get("merchantId");
            String merchantCategory = (String) event.get("merchantCategory");
            String merchantCountry = (String) event.get("merchantCountry");
            String transactionLocation = (String) event.get("transactionLocation");
            LocalDateTime authorizationTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            Integer riskScore = event.containsKey("riskScore") ? (Integer) event.get("riskScore") : 0;
            @SuppressWarnings("unchecked")
            List<String> riskFlags = (List<String>) event.getOrDefault("riskFlags", List.of());
            Boolean isInternational = (Boolean) event.getOrDefault("isInternational", false);
            Boolean isOnline = (Boolean) event.getOrDefault("isOnline", false);
            Boolean isContactless = (Boolean) event.getOrDefault("isContactless", false);
            String declineReason = (String) event.get("declineReason");
            
            log.info("Card authorization - AuthId: {}, CardId: {}, Status: {}, Amount: {} {}, Merchant: {}, RiskScore: {}", 
                    authorizationId, cardId, authorizationStatus, amount, currency, merchantName, riskScore);
            
            validateCardAuthorization(authorizationId, cardId, userId, authorizationStatus, amount);
            
            processAuthorizationByStatus(authorizationId, cardId, userId, authorizationType, 
                    authorizationStatus, amount, currency, merchantName, merchantId, merchantCategory, 
                    merchantCountry, transactionLocation, riskScore, riskFlags, isInternational, 
                    isOnline, isContactless, declineReason, authorizationTimestamp);
            
            if ("APPROVED".equals(authorizationStatus)) {
                handleApprovedAuthorization(authorizationId, cardId, userId, amount, currency, 
                        merchantName, merchantId, authorizationTimestamp);
            } else if ("DECLINED".equals(authorizationStatus)) {
                handleDeclinedAuthorization(authorizationId, cardId, userId, amount, currency, 
                        merchantName, declineReason, riskScore, riskFlags);
            } else if ("PENDING_REVIEW".equals(authorizationStatus)) {
                handlePendingReviewAuthorization(authorizationId, cardId, userId, amount, 
                        merchantName, riskScore, riskFlags);
            }
            
            if (riskScore >= 70 || !riskFlags.isEmpty()) {
                performFraudCheck(authorizationId, cardId, userId, amount, merchantName, 
                        merchantCountry, transactionLocation, riskScore, riskFlags, 
                        isInternational, isOnline);
            }
            
            notifyUser(userId, cardId, authorizationId, authorizationStatus, amount, currency, 
                    merchantName, isInternational);
            
            updateCardAuthorizationMetrics(authorizationType, authorizationStatus, amount, 
                    merchantCategory, riskScore, isInternational);
            
            auditCardAuthorization(authorizationId, cardId, userId, authorizationType, 
                    authorizationStatus, amount, currency, merchantName, riskScore, 
                    processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Card authorization processed - AuthId: {}, Status: {}, ProcessingTime: {}ms", 
                    authorizationId, authorizationStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Card authorization processing failed - AuthId: {}, CardId: {}, Status: {}, Error: {}", 
                    authorizationId, cardId, authorizationStatus, e.getMessage(), e);
            
            if (authorizationId != null && cardId != null) {
                handleAuthorizationFailure(authorizationId, cardId, authorizationStatus, e);
            }
            
            throw new RuntimeException("Card authorization processing failed", e);
        }
    }
    
    private void validateCardAuthorization(UUID authorizationId, UUID cardId, UUID userId, 
                                          String authorizationStatus, BigDecimal amount) {
        if (authorizationId == null || cardId == null || userId == null) {
            throw new IllegalArgumentException("Authorization ID, Card ID, and User ID are required");
        }
        
        if (authorizationStatus == null || authorizationStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization status is required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid authorization amount");
        }
        
        log.debug("Card authorization validation passed - AuthId: {}", authorizationId);
    }
    
    private void processAuthorizationByStatus(UUID authorizationId, UUID cardId, UUID userId,
                                             String authorizationType, String authorizationStatus,
                                             BigDecimal amount, String currency, String merchantName,
                                             String merchantId, String merchantCategory, String merchantCountry,
                                             String transactionLocation, Integer riskScore, List<String> riskFlags,
                                             Boolean isInternational, Boolean isOnline, Boolean isContactless,
                                             String declineReason, LocalDateTime authorizationTimestamp) {
        try {
            cardAuthorizationService.processAuthorization(
                    authorizationId, cardId, userId, authorizationType, authorizationStatus,
                    amount, currency, merchantName, merchantId, merchantCategory, merchantCountry,
                    transactionLocation, riskScore, riskFlags, isInternational, isOnline,
                    isContactless, declineReason, authorizationTimestamp);
            
            log.debug("Authorization status processing completed - AuthId: {}, Status: {}", 
                    authorizationId, authorizationStatus);
            
        } catch (Exception e) {
            log.error("Failed to process authorization by status - AuthId: {}, Status: {}", 
                    authorizationId, authorizationStatus, e);
            throw new RuntimeException("Authorization status processing failed", e);
        }
    }
    
    private void handleApprovedAuthorization(UUID authorizationId, UUID cardId, UUID userId,
                                            BigDecimal amount, String currency, String merchantName,
                                            String merchantId, LocalDateTime authorizationTimestamp) {
        try {
            log.info("Processing approved authorization - AuthId: {}, Amount: {} {}, Merchant: {}", 
                    authorizationId, amount, currency, merchantName);
            
            cardAuthorizationService.recordApprovedAuthorization(authorizationId, cardId, userId, 
                    amount, currency, merchantName, merchantId, authorizationTimestamp);
            
            cardAuthorizationService.updateCardLimits(cardId, amount);
            
        } catch (Exception e) {
            log.error("Failed to handle approved authorization - AuthId: {}", authorizationId, e);
        }
    }
    
    private void handleDeclinedAuthorization(UUID authorizationId, UUID cardId, UUID userId,
                                            BigDecimal amount, String currency, String merchantName,
                                            String declineReason, Integer riskScore, List<String> riskFlags) {
        try {
            log.warn("Processing declined authorization - AuthId: {}, Reason: {}, RiskScore: {}", 
                    authorizationId, declineReason, riskScore);
            
            cardAuthorizationService.recordDeclinedAuthorization(authorizationId, cardId, userId, 
                    amount, currency, merchantName, declineReason, riskScore, riskFlags);
            
            if (riskScore >= 80) {
                cardAuthorizationService.flagForFraudReview(cardId, userId, authorizationId, 
                        declineReason, riskScore);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle declined authorization - AuthId: {}", authorizationId, e);
        }
    }
    
    private void handlePendingReviewAuthorization(UUID authorizationId, UUID cardId, UUID userId,
                                                 BigDecimal amount, String merchantName,
                                                 Integer riskScore, List<String> riskFlags) {
        try {
            log.warn("Authorization pending review - AuthId: {}, RiskScore: {}, Flags: {}", 
                    authorizationId, riskScore, riskFlags.size());
            
            cardAuthorizationService.createReviewCase(authorizationId, cardId, userId, amount, 
                    merchantName, riskScore, riskFlags);
            
            cardAuthorizationService.notifyFraudTeam(authorizationId, cardId, userId, riskScore, 
                    riskFlags);
            
        } catch (Exception e) {
            log.error("Failed to handle pending review authorization - AuthId: {}", authorizationId, e);
        }
    }
    
    private void performFraudCheck(UUID authorizationId, UUID cardId, UUID userId, BigDecimal amount,
                                  String merchantName, String merchantCountry, String transactionLocation,
                                  Integer riskScore, List<String> riskFlags, Boolean isInternational,
                                  Boolean isOnline) {
        try {
            cardFraudDetectionService.analyzeFraudIndicators(authorizationId, cardId, userId, 
                    amount, merchantName, merchantCountry, transactionLocation, riskScore, 
                    riskFlags, isInternational, isOnline);
            
            log.debug("Fraud check completed - AuthId: {}, RiskScore: {}", authorizationId, riskScore);
            
        } catch (Exception e) {
            log.error("Failed to perform fraud check - AuthId: {}", authorizationId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID cardId, UUID authorizationId, String authorizationStatus,
                           BigDecimal amount, String currency, String merchantName, Boolean isInternational) {
        try {
            cardNotificationService.sendAuthorizationNotification(userId, cardId, authorizationId, 
                    authorizationStatus, amount, currency, merchantName, isInternational);
            
            log.info("User notified of authorization - UserId: {}, AuthId: {}, Status: {}", 
                    userId, authorizationId, authorizationStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, AuthId: {}", userId, authorizationId, e);
        }
    }
    
    private void updateCardAuthorizationMetrics(String authorizationType, String authorizationStatus,
                                               BigDecimal amount, String merchantCategory, Integer riskScore,
                                               Boolean isInternational) {
        try {
            cardAuthorizationService.updateAuthorizationMetrics(authorizationType, authorizationStatus, 
                    amount, merchantCategory, riskScore, isInternational);
        } catch (Exception e) {
            log.error("Failed to update authorization metrics - Type: {}, Status: {}", 
                    authorizationType, authorizationStatus, e);
        }
    }
    
    private void auditCardAuthorization(UUID authorizationId, UUID cardId, UUID userId,
                                       String authorizationType, String authorizationStatus,
                                       BigDecimal amount, String currency, String merchantName,
                                       Integer riskScore, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "CARD_AUTHORIZATION_PROCESSED",
                    userId.toString(),
                    String.format("Card authorization %s - Status: %s, Amount: %s %s, Merchant: %s", 
                            authorizationType, authorizationStatus, amount, currency, merchantName),
                    Map.of(
                            "authorizationId", authorizationId.toString(),
                            "cardId", cardId.toString(),
                            "userId", userId.toString(),
                            "authorizationType", authorizationType,
                            "authorizationStatus", authorizationStatus,
                            "amount", amount.toString(),
                            "currency", currency,
                            "merchantName", merchantName,
                            "riskScore", riskScore,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit card authorization - AuthId: {}", authorizationId, e);
        }
    }
    
    private void handleAuthorizationFailure(UUID authorizationId, UUID cardId, String authorizationStatus, 
                                           Exception error) {
        try {
            cardAuthorizationService.handleAuthorizationFailure(authorizationId, cardId, 
                    authorizationStatus, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "CARD_AUTHORIZATION_PROCESSING_FAILED",
                    "SYSTEM",
                    "Failed to process card authorization: " + error.getMessage(),
                    Map.of(
                            "authorizationId", authorizationId.toString(),
                            "cardId", cardId.toString(),
                            "authorizationStatus", authorizationStatus != null ? authorizationStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle authorization failure - AuthId: {}", authorizationId, e);
        }
    }
    
    @KafkaListener(
        topics = {"card-authorization-events.DLQ", "card-authorization.DLQ"},
        groupId = "card-service-authorization-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Card authorization event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID authorizationId = event.containsKey("authorizationId") ? 
                    UUID.fromString((String) event.get("authorizationId")) : null;
            UUID cardId = event.containsKey("cardId") ? 
                    UUID.fromString((String) event.get("cardId")) : null;
            String authorizationStatus = (String) event.get("authorizationStatus");
            
            log.error("DLQ: Card authorization failed permanently - AuthId: {}, CardId: {}, Status: {} - MANUAL REVIEW REQUIRED", 
                    authorizationId, cardId, authorizationStatus);
            
            if (authorizationId != null && cardId != null) {
                cardAuthorizationService.markForManualReview(authorizationId, cardId, 
                        authorizationStatus, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse card authorization DLQ event: {}", eventJson, e);
        }
    }
}