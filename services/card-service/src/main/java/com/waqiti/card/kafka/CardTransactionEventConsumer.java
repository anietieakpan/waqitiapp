package com.waqiti.card.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.card.service.CardTransactionService;
import com.waqiti.card.service.CardAuthorizationService;
import com.waqiti.card.service.CardNotificationService;
import com.waqiti.card.service.CardFraudDetectionService;
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
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardTransactionEventConsumer {
    
    private final CardTransactionService cardTransactionService;
    private final CardAuthorizationService cardAuthorizationService;
    private final CardNotificationService cardNotificationService;
    private final CardFraudDetectionService cardFraudDetectionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"card-transaction-events", "card-transactions", "card-activity", "card-purchase-events"},
        groupId = "card-service-transaction-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleCardTransactionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("CARD TRANSACTION: Processing transaction - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transactionId = null;
        UUID userId = null;
        String transactionStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            transactionId = UUID.fromString((String) event.get("transactionId"));
            userId = UUID.fromString((String) event.get("userId"));
            UUID cardId = UUID.fromString((String) event.get("cardId"));
            String cardNumber = (String) event.get("cardNumber");
            transactionStatus = (String) event.get("transactionStatus");
            String transactionType = (String) event.get("transactionType");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            String merchantName = (String) event.get("merchantName");
            String merchantCategory = (String) event.get("merchantCategory");
            String merchantCountry = (String) event.get("merchantCountry");
            String merchantCity = (String) event.get("merchantCity");
            LocalDateTime transactionTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String authorizationCode = (String) event.get("authorizationCode");
            String declineReason = (String) event.get("declineReason");
            Boolean isInternational = (Boolean) event.getOrDefault("isInternational", false);
            Boolean isContactless = (Boolean) event.getOrDefault("isContactless", false);
            Boolean isOnline = (Boolean) event.getOrDefault("isOnline", false);
            Integer riskScore = event.containsKey("riskScore") ? (Integer) event.get("riskScore") : 0;
            String posEntryMode = (String) event.get("posEntryMode");
            
            log.info("Card transaction - TxnId: {}, UserId: {}, CardId: {}, Status: {}, Type: {}, Amount: {} {}, Merchant: {} ({}, {}), International: {}, Contactless: {}, Online: {}", 
                    transactionId, userId, cardId, transactionStatus, transactionType, amount, currency, 
                    merchantName, merchantCity, merchantCountry, isInternational, isContactless, isOnline);
            
            validateCardTransaction(transactionId, userId, cardId, transactionStatus, transactionType, 
                    amount, currency);
            
            processTransactionByType(transactionId, userId, cardId, transactionStatus, transactionType, 
                    amount, currency, merchantName, merchantCategory, merchantCountry, merchantCity, 
                    transactionTimestamp, authorizationCode, isInternational, isContactless, isOnline, 
                    posEntryMode);
            
            if ("AUTHORIZED".equals(transactionStatus)) {
                handleAuthorizedTransaction(transactionId, userId, cardId, amount, currency, 
                        merchantName, authorizationCode, transactionTimestamp);
            } else if ("DECLINED".equals(transactionStatus)) {
                handleDeclinedTransaction(transactionId, userId, cardId, amount, currency, 
                        merchantName, declineReason);
            } else if ("COMPLETED".equals(transactionStatus)) {
                handleCompletedTransaction(transactionId, userId, cardId, amount, currency, 
                        merchantName, transactionTimestamp);
            } else if ("REVERSED".equals(transactionStatus)) {
                handleReversedTransaction(transactionId, userId, cardId, amount, currency);
            }
            
            if (isInternational) {
                handleInternationalTransaction(transactionId, userId, cardId, amount, currency, 
                        merchantCountry, riskScore);
            }
            
            if (riskScore >= 70) {
                performFraudCheck(transactionId, userId, cardId, amount, currency, merchantName, 
                        riskScore, isInternational, isOnline);
            }
            
            notifyUser(userId, transactionId, transactionType, transactionStatus, amount, currency, 
                    merchantName, isInternational);
            
            updateCardMetrics(transactionStatus, transactionType, amount, currency, merchantCategory, 
                    isInternational);
            
            auditCardTransaction(transactionId, userId, cardId, transactionStatus, transactionType, 
                    amount, currency, merchantName, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Card transaction processed - TxnId: {}, Status: {}, ProcessingTime: {}ms", 
                    transactionId, transactionStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Card transaction processing failed - TxnId: {}, UserId: {}, Status: {}, Error: {}", 
                    transactionId, userId, transactionStatus, e.getMessage(), e);
            
            if (transactionId != null && userId != null) {
                handleTransactionFailure(transactionId, userId, transactionStatus, e);
            }
            
            throw new RuntimeException("Card transaction processing failed", e);
        }
    }
    
    private void validateCardTransaction(UUID transactionId, UUID userId, UUID cardId,
                                        String transactionStatus, String transactionType,
                                        BigDecimal amount, String currency) {
        if (transactionId == null || userId == null || cardId == null) {
            throw new IllegalArgumentException("Transaction ID, User ID, and Card ID are required");
        }
        
        if (transactionStatus == null || transactionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction status is required");
        }
        
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        log.debug("Card transaction validation passed - TxnId: {}", transactionId);
    }
    
    private void processTransactionByType(UUID transactionId, UUID userId, UUID cardId,
                                         String transactionStatus, String transactionType,
                                         BigDecimal amount, String currency, String merchantName,
                                         String merchantCategory, String merchantCountry,
                                         String merchantCity, LocalDateTime transactionTimestamp,
                                         String authorizationCode, Boolean isInternational,
                                         Boolean isContactless, Boolean isOnline, String posEntryMode) {
        try {
            switch (transactionType) {
                case "PURCHASE" -> processPurchase(transactionId, userId, cardId, amount, currency, 
                        merchantName, merchantCategory, authorizationCode, isContactless, isOnline);
                
                case "WITHDRAWAL" -> processWithdrawal(transactionId, userId, cardId, amount, currency, 
                        merchantName, merchantCountry);
                
                case "REFUND" -> processRefund(transactionId, userId, cardId, amount, currency, 
                        merchantName);
                
                case "CASH_ADVANCE" -> processCashAdvance(transactionId, userId, cardId, amount, 
                        currency, merchantName);
                
                case "BALANCE_INQUIRY" -> processBalanceInquiry(transactionId, userId, cardId, 
                        merchantName);
                
                case "PAYMENT" -> processPayment(transactionId, userId, cardId, amount, currency);
                
                default -> {
                    log.warn("Unknown transaction type: {}", transactionType);
                    processGenericTransaction(transactionId, userId, cardId, transactionType);
                }
            }
            
            log.debug("Transaction type processing completed - TxnId: {}, Type: {}", 
                    transactionId, transactionType);
            
        } catch (Exception e) {
            log.error("Failed to process transaction by type - TxnId: {}, Type: {}", 
                    transactionId, transactionType, e);
            throw new RuntimeException("Transaction type processing failed", e);
        }
    }
    
    private void processPurchase(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                                String currency, String merchantName, String merchantCategory,
                                String authorizationCode, Boolean isContactless, Boolean isOnline) {
        log.info("Processing PURCHASE - TxnId: {}, Amount: {} {}, Merchant: {} ({}), AuthCode: {}, Contactless: {}, Online: {}", 
                transactionId, amount, currency, merchantName, merchantCategory, authorizationCode, 
                isContactless, isOnline);
        
        cardTransactionService.processPurchase(transactionId, userId, cardId, amount, currency, 
                merchantName, merchantCategory, authorizationCode, isContactless, isOnline);
    }
    
    private void processWithdrawal(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                                  String currency, String merchantName, String merchantCountry) {
        log.info("Processing WITHDRAWAL - TxnId: {}, Amount: {} {}, Location: {}, Country: {}", 
                transactionId, amount, currency, merchantName, merchantCountry);
        
        cardTransactionService.processWithdrawal(transactionId, userId, cardId, amount, currency, 
                merchantName, merchantCountry);
    }
    
    private void processRefund(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                              String currency, String merchantName) {
        log.info("Processing REFUND - TxnId: {}, Amount: {} {}, Merchant: {}", 
                transactionId, amount, currency, merchantName);
        
        cardTransactionService.processRefund(transactionId, userId, cardId, amount, currency, 
                merchantName);
    }
    
    private void processCashAdvance(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                                   String currency, String merchantName) {
        log.info("Processing CASH ADVANCE - TxnId: {}, Amount: {} {}, Location: {}", 
                transactionId, amount, currency, merchantName);
        
        cardTransactionService.processCashAdvance(transactionId, userId, cardId, amount, currency, 
                merchantName);
    }
    
    private void processBalanceInquiry(UUID transactionId, UUID userId, UUID cardId,
                                      String merchantName) {
        log.info("Processing BALANCE INQUIRY - TxnId: {}, Location: {}", transactionId, merchantName);
        
        cardTransactionService.processBalanceInquiry(transactionId, userId, cardId, merchantName);
    }
    
    private void processPayment(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                               String currency) {
        log.info("Processing PAYMENT - TxnId: {}, Amount: {} {}", transactionId, amount, currency);
        
        cardTransactionService.processPayment(transactionId, userId, cardId, amount, currency);
    }
    
    private void processGenericTransaction(UUID transactionId, UUID userId, UUID cardId,
                                          String transactionType) {
        log.info("Processing generic card transaction - TxnId: {}, Type: {}", 
                transactionId, transactionType);
        
        cardTransactionService.processGeneric(transactionId, userId, cardId, transactionType);
    }
    
    private void handleAuthorizedTransaction(UUID transactionId, UUID userId, UUID cardId,
                                            BigDecimal amount, String currency, String merchantName,
                                            String authorizationCode, LocalDateTime transactionTimestamp) {
        try {
            log.info("Processing authorized card transaction - TxnId: {}, Amount: {} {}, AuthCode: {}", 
                    transactionId, amount, currency, authorizationCode);
            
            cardAuthorizationService.recordAuthorizedTransaction(transactionId, userId, cardId, 
                    amount, currency, merchantName, authorizationCode, transactionTimestamp);
            
            cardTransactionService.deductFromAvailableBalance(userId, cardId, amount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle authorized transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleDeclinedTransaction(UUID transactionId, UUID userId, UUID cardId,
                                          BigDecimal amount, String currency, String merchantName,
                                          String declineReason) {
        try {
            log.warn("Processing declined card transaction - TxnId: {}, Amount: {} {}, Reason: {}", 
                    transactionId, amount, currency, declineReason);
            
            cardTransactionService.recordDeclinedTransaction(transactionId, userId, cardId, amount, 
                    currency, merchantName, declineReason);
            
        } catch (Exception e) {
            log.error("Failed to handle declined transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleCompletedTransaction(UUID transactionId, UUID userId, UUID cardId,
                                           BigDecimal amount, String currency, String merchantName,
                                           LocalDateTime transactionTimestamp) {
        try {
            log.info("Processing completed card transaction - TxnId: {}, Amount: {} {}, Merchant: {}", 
                    transactionId, amount, currency, merchantName);
            
            cardTransactionService.recordCompletedTransaction(transactionId, userId, cardId, amount, 
                    currency, merchantName, transactionTimestamp);
            
            cardTransactionService.settleTransaction(transactionId, userId, cardId, amount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle completed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleReversedTransaction(UUID transactionId, UUID userId, UUID cardId,
                                          BigDecimal amount, String currency) {
        try {
            log.info("Processing reversed card transaction - TxnId: {}, Amount: {} {}", 
                    transactionId, amount, currency);
            
            cardTransactionService.recordReversedTransaction(transactionId, userId, cardId, amount, 
                    currency);
            
            cardTransactionService.restoreAvailableBalance(userId, cardId, amount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle reversed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleInternationalTransaction(UUID transactionId, UUID userId, UUID cardId,
                                               BigDecimal amount, String currency,
                                               String merchantCountry, Integer riskScore) {
        try {
            log.warn("Processing INTERNATIONAL card transaction - TxnId: {}, Amount: {} {}, Country: {}, RiskScore: {}", 
                    transactionId, amount, currency, merchantCountry, riskScore);
            
            cardTransactionService.recordInternationalTransaction(transactionId, userId, cardId, 
                    amount, currency, merchantCountry, riskScore);
            
            if (riskScore >= 50) {
                cardFraudDetectionService.flagInternationalTransaction(transactionId, userId, cardId, 
                        merchantCountry, riskScore);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle international transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void performFraudCheck(UUID transactionId, UUID userId, UUID cardId, BigDecimal amount,
                                  String currency, String merchantName, Integer riskScore,
                                  Boolean isInternational, Boolean isOnline) {
        try {
            log.warn("Performing fraud check - TxnId: {}, RiskScore: {}, International: {}, Online: {}", 
                    transactionId, riskScore, isInternational, isOnline);
            
            cardFraudDetectionService.checkTransaction(transactionId, userId, cardId, amount, 
                    currency, merchantName, riskScore, isInternational, isOnline);
            
            if (riskScore >= 90) {
                cardFraudDetectionService.blockTransaction(transactionId, userId, cardId, 
                        "High fraud risk score");
                
                cardNotificationService.sendFraudAlert(userId, transactionId, amount, currency, 
                        merchantName);
            }
            
        } catch (Exception e) {
            log.error("Failed to perform fraud check - TxnId: {}", transactionId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID transactionId, String transactionType,
                           String transactionStatus, BigDecimal amount, String currency,
                           String merchantName, Boolean isInternational) {
        try {
            cardNotificationService.sendTransactionNotification(userId, transactionId, 
                    transactionType, transactionStatus, amount, currency, merchantName, 
                    isInternational);
            
            log.info("User notified of card transaction - UserId: {}, TxnId: {}, Status: {}", 
                    userId, transactionId, transactionStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, TxnId: {}", userId, transactionId, e);
        }
    }
    
    private void updateCardMetrics(String transactionStatus, String transactionType, BigDecimal amount,
                                  String currency, String merchantCategory, Boolean isInternational) {
        try {
            cardTransactionService.updateTransactionMetrics(transactionStatus, transactionType, 
                    amount, currency, merchantCategory, isInternational);
        } catch (Exception e) {
            log.error("Failed to update card metrics - Status: {}, Type: {}", 
                    transactionStatus, transactionType, e);
        }
    }
    
    private void auditCardTransaction(UUID transactionId, UUID userId, UUID cardId,
                                     String transactionStatus, String transactionType,
                                     BigDecimal amount, String currency, String merchantName,
                                     LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "CARD_TRANSACTION_PROCESSED",
                    userId.toString(),
                    String.format("Card transaction %s - Type: %s, Amount: %s %s, Merchant: %s", 
                            transactionStatus, transactionType, amount, currency, merchantName),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId.toString(),
                            "cardId", cardId.toString(),
                            "transactionStatus", transactionStatus,
                            "transactionType", transactionType,
                            "amount", amount.toString(),
                            "currency", currency,
                            "merchantName", merchantName != null ? merchantName : "N/A",
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit card transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleTransactionFailure(UUID transactionId, UUID userId, String transactionStatus,
                                         Exception error) {
        try {
            cardTransactionService.handleTransactionFailure(transactionId, userId, transactionStatus, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "CARD_TRANSACTION_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process card transaction: " + error.getMessage(),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId.toString(),
                            "transactionStatus", transactionStatus != null ? transactionStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle transaction failure - TxnId: {}", transactionId, e);
        }
    }
    
    @KafkaListener(
        topics = {"card-transaction-events.DLQ", "card-transactions.DLQ", "card-activity.DLQ", "card-purchase-events.DLQ"},
        groupId = "card-service-transaction-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Card transaction event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String transactionType = (String) event.get("transactionType");
            
            log.error("DLQ: Card transaction failed permanently - TxnId: {}, UserId: {}, Type: {} - MANUAL INTERVENTION REQUIRED", 
                    transactionId, userId, transactionType);
            
            if (transactionId != null && userId != null) {
                cardTransactionService.markForManualReview(transactionId, userId, transactionType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse card transaction DLQ event: {}", eventJson, e);
        }
    }
}