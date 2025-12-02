package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.ACHPaymentService;
import com.waqiti.payment.service.PaymentValidationService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.dto.ACHTransferResult;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Consumer for ACH transfer events
 * 
 * CRITICAL FINANCIAL CONSUMER
 * Processes ACH (Automated Clearing House) transfer requests with comprehensive
 * validation, fraud detection, and compliance checks.
 * 
 * ACH transfers are critical for:
 * - Bank-to-bank transfers
 * - Direct deposits
 * - Bill payments
 * - Payroll processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ACHTransfersConsumer {

    private final ACHPaymentService achPaymentService;
    private final PaymentValidationService validationService;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "ach-transfers:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    
    @KafkaListener(
        topics = "ach-transfers",
        groupId = "payment-service-ach-transfers-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class
        }
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleACHTransfer(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Build idempotency key for duplicate prevention
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + topic + ":" + partition + ":" + offset;

        // Check if already processed (PCI DSS: Prevent duplicate ACH transfers)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
            log.info("ACH TRANSFER: Duplicate event detected, skipping - Idempotency key: {}", idempotencyKey);
            acknowledgment.acknowledge();
            return;
        }

        log.info("CRITICAL ACH TRANSFER: Processing ACH transfer - Topic: {}, Partition: {}, Offset: {}",
                topic, partition, offset);

        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transferId = null;
        UUID userId = null;

        try {
            // Parse ACH transfer event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            transferId = UUID.fromString((String) event.get("transferId"));
            userId = UUID.fromString((String) event.get("userId"));
            String sourceAccountId = (String) event.get("sourceAccountId");
            String targetAccountId = (String) event.get("targetAccountId");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            String transferType = (String) event.get("transferType"); // DEBIT or CREDIT
            String purpose = (String) event.get("purpose");
            String routingNumber = (String) event.get("routingNumber");
            String accountNumber = (String) event.get("accountNumber");
            boolean sameDayACH = Boolean.parseBoolean(event.getOrDefault("sameDayACH", "false").toString());
            
            log.info("Processing ACH transfer - TransferId: {}, UserId: {}, Amount: {} {}, Type: {}, SameDay: {}", 
                    transferId, userId, amount, currency, transferType, sameDayACH);
            
            // 1. Validate ACH transfer request
            validateACHTransfer(transferId, userId, sourceAccountId, targetAccountId, amount, 
                    currency, routingNumber, accountNumber);
            
            // 2. Fraud detection screening
            performFraudScreening(transferId, userId, amount, sourceAccountId, targetAccountId, transferType);
            
            // 3. Check account balance and limits
            validateAccountBalanceAndLimits(userId, sourceAccountId, amount, transferType);
            
            // 4. Validate routing number
            validateRoutingNumber(routingNumber);
            
            // 5. Process ACH transfer
            ACHTransferResult result = processACHTransfer(
                    transferId, userId, sourceAccountId, targetAccountId, amount, currency,
                    transferType, purpose, routingNumber, accountNumber, sameDayACH);
            
            // 6. Update transfer status
            updateTransferStatus(transferId, result);
            
            // 7. Send notifications
            sendACHTransferNotifications(userId, transferId, amount, currency, result);
            
            // 8. Audit the ACH transfer
            auditACHTransfer(transferId, userId, amount, currency, transferType, result, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();

            log.info("ACH transfer processed successfully - TransferId: {}, Status: {}, ProcessingTime: {}ms",
                    transferId, result.getStatus(), processingTimeMs);

            // Mark as processed in Redis (prevent duplicates)
            redisTemplate.opsForValue().set(
                idempotencyKey,
                "processed",
                IDEMPOTENCY_TTL_HOURS,
                TimeUnit.HOURS
            );

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: ACH transfer processing failed - TransferId: {}, UserId: {}, Error: {}", 
                    transferId, userId, e.getMessage(), e);
            
            if (transferId != null) {
                handleACHTransferFailure(transferId, userId, e);
            }
            
            throw new RuntimeException("ACH transfer processing failed", e);
        }
    }
    
    private void validateACHTransfer(UUID transferId, UUID userId, String sourceAccountId, 
                                    String targetAccountId, BigDecimal amount, String currency,
                                    String routingNumber, String accountNumber) {
        
        if (transferId == null || userId == null) {
            throw new IllegalArgumentException("TransferId and UserId are required");
        }
        
        if (sourceAccountId == null || targetAccountId == null) {
            throw new IllegalArgumentException("Source and target account IDs are required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transfer amount");
        }
        
        if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            log.warn("Large ACH transfer detected - TransferId: {}, Amount: {}", transferId, amount);
        }
        
        if (routingNumber == null || routingNumber.length() != 9) {
            throw new IllegalArgumentException("Invalid routing number format");
        }
        
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is required");
        }
        
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("ACH transfers only support USD currency");
        }
        
        boolean isValid = validationService.validateACHTransferRequest(transferId, userId, 
                sourceAccountId, targetAccountId, amount);
        
        if (!isValid) {
            throw new IllegalArgumentException("ACH transfer validation failed");
        }
        
        log.debug("ACH transfer validation passed - TransferId: {}", transferId);
    }
    
    private void performFraudScreening(UUID transferId, UUID userId, BigDecimal amount, 
                                      String sourceAccountId, String targetAccountId, String transferType) {
        try {
            boolean isSuspicious = fraudDetectionService.screenACHTransfer(
                    transferId, userId, sourceAccountId, targetAccountId, amount, transferType);
            
            if (isSuspicious) {
                log.error("FRAUD ALERT: Suspicious ACH transfer detected - TransferId: {}, UserId: {}, Amount: {}", 
                        transferId, userId, amount);
                throw new SecurityException("ACH transfer flagged as suspicious - blocked for review");
            }
            
            log.debug("Fraud screening passed for ACH transfer - TransferId: {}", transferId);
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fraud screening failed for ACH transfer - TransferId: {}", transferId, e);
            throw new RuntimeException("Fraud screening error", e);
        }
    }
    
    private void validateAccountBalanceAndLimits(UUID userId, String sourceAccountId, 
                                                 BigDecimal amount, String transferType) {
        try {
            if ("DEBIT".equals(transferType)) {
                boolean hasSufficientBalance = achPaymentService.checkAccountBalance(sourceAccountId, amount);
                
                if (!hasSufficientBalance) {
                    throw new IllegalArgumentException("Insufficient funds for ACH transfer");
                }
            }
            
            boolean withinLimits = achPaymentService.checkACHLimits(userId, amount);
            
            if (!withinLimits) {
                log.warn("ACH transfer exceeds user limits - UserId: {}, Amount: {}", userId, amount);
                throw new IllegalArgumentException("ACH transfer exceeds daily/monthly limits");
            }
            
            log.debug("Balance and limits validation passed - UserId: {}", userId);
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Balance/limits validation failed - UserId: {}", userId, e);
            throw new RuntimeException("Balance validation error", e);
        }
    }
    
    private void validateRoutingNumber(String routingNumber) {
        try {
            boolean isValid = achPaymentService.validateRoutingNumber(routingNumber);
            
            if (!isValid) {
                throw new IllegalArgumentException("Invalid routing number: " + routingNumber);
            }
            
            log.debug("Routing number validated: {}", routingNumber);
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Routing number validation failed: {}", routingNumber, e);
            throw new RuntimeException("Routing number validation error", e);
        }
    }
    
    private ACHTransferResult processACHTransfer(UUID transferId, UUID userId, String sourceAccountId,
                                                String targetAccountId, BigDecimal amount, String currency,
                                                String transferType, String purpose, String routingNumber,
                                                String accountNumber, boolean sameDayACH) {
        try {
            log.info("Processing ACH transfer - TransferId: {}, Type: {}, SameDay: {}", 
                    transferId, transferType, sameDayACH);
            
            ACHTransferResult result = achPaymentService.processACHTransfer(
                    transferId, userId, sourceAccountId, targetAccountId, amount, currency,
                    transferType, purpose, routingNumber, accountNumber, sameDayACH);
            
            if ("FAILED".equals(result.getStatus())) {
                log.error("ACH transfer processing failed - TransferId: {}, Reason: {}", 
                        transferId, result.getFailureReason());
                throw new RuntimeException("ACH transfer failed: " + result.getFailureReason());
            }
            
            return result;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ACH transfer processing error - TransferId: {}", transferId, e);
            throw new RuntimeException("ACH processing error", e);
        }
    }
    
    private void updateTransferStatus(UUID transferId, ACHTransferResult result) {
        try {
            achPaymentService.updateTransferStatus(transferId, result.getStatus(), 
                    result.getTrackingNumber(), result.getEstimatedSettlementDate());
            log.debug("Updated transfer status - TransferId: {}, Status: {}", transferId, result.getStatus());
        } catch (Exception e) {
            log.error("Failed to update transfer status - TransferId: {}", transferId, e);
        }
    }
    
    private void sendACHTransferNotifications(UUID userId, UUID transferId, BigDecimal amount, 
                                            String currency, ACHTransferResult result) {
        try {
            achPaymentService.sendTransferNotification(userId, transferId, amount, currency, 
                    result.getStatus(), result.getEstimatedSettlementDate());
            log.debug("Sent ACH transfer notification - UserId: {}, TransferId: {}", userId, transferId);
        } catch (Exception e) {
            log.error("Failed to send ACH transfer notification - UserId: {}, TransferId: {}", 
                    userId, transferId, e);
        }
    }
    
    private void auditACHTransfer(UUID transferId, UUID userId, BigDecimal amount, String currency,
                                 String transferType, ACHTransferResult result, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditPaymentEvent(
                    "ACH_TRANSFER_PROCESSED",
                    userId.toString(),
                    String.format("ACH transfer processed - TransferId: %s, Amount: %s %s, Type: %s, Status: %s", 
                            transferId, amount, currency, transferType, result.getStatus()),
                    Map.of(
                            "transferId", transferId.toString(),
                            "userId", userId.toString(),
                            "amount", amount.toString(),
                            "currency", currency,
                            "transferType", transferType,
                            "status", result.getStatus(),
                            "trackingNumber", result.getTrackingNumber() != null ? result.getTrackingNumber() : "N/A",
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit ACH transfer - TransferId: {}", transferId, e);
        }
    }
    
    private void handleACHTransferFailure(UUID transferId, UUID userId, Exception error) {
        try {
            achPaymentService.handleTransferFailure(transferId, userId, error.getMessage());
            
            auditService.auditPaymentEvent(
                    "ACH_TRANSFER_FAILED",
                    userId != null ? userId.toString() : "UNKNOWN",
                    "ACH transfer processing failed: " + error.getMessage(),
                    Map.of(
                            "transferId", transferId.toString(),
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle ACH transfer failure - TransferId: {}", transferId, e);
        }
    }
    
    @KafkaListener(
        topics = "ach-transfers.DLQ",
        groupId = "payment-service-ach-transfers-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: ACH transfer sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transferId = event.containsKey("transferId") ? 
                    UUID.fromString((String) event.get("transferId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            
            log.error("DLQ: ACH transfer failed permanently - TransferId: {}, UserId: {} - MANUAL INTERVENTION REQUIRED", 
                    transferId, userId);
            
            if (transferId != null && userId != null) {
                achPaymentService.markTransferForManualReview(transferId, userId, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse ACH transfer DLQ event: {}", eventJson, e);
        }
    }
    
}