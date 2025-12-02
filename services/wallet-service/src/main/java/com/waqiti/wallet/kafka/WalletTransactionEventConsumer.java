package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletTransactionService;
import com.waqiti.wallet.service.WalletBalanceService;
import com.waqiti.wallet.service.WalletNotificationService;
import com.waqiti.wallet.service.WalletMetricsService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
public class WalletTransactionEventConsumer {

    private final WalletTransactionService walletTransactionService;
    private final WalletBalanceService walletBalanceService;
    private final WalletNotificationService walletNotificationService;
    private final WalletMetricsService walletMetricsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    private final com.waqiti.common.idempotency.EventProcessingService eventProcessingService;
    
    @KafkaListener(
        topics = {"wallet-transaction-events", "wallet-transactions", "wallet-activity"},
        groupId = "wallet-service-transaction-group",
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
    public void handleWalletTransactionEvent(
            ConsumerRecord<String, String> record,
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("WALLET TRANSACTION: Processing transaction - Topic: {}, Partition: {}, Offset: {}",
                topic, partition, offset);

        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transactionId = null;
        UUID userId = null;
        String transactionType = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            // ✅ CRITICAL PRODUCTION FIX: Idempotency check to prevent duplicate wallet transactions
            // Prevents double-crediting/debiting from event replays (double-spending vulnerability)
            transactionId = UUID.fromString((String) event.get("transactionId"));
            String idempotencyKey = "wallet-transaction:" + transactionId + ":" + topic;

            if (eventProcessingService.isProcessed(idempotencyKey)) {
                log.info("IDEMPOTENCY: Wallet transaction already processed - transactionId: {} - Skipping duplicate",
                    transactionId);
                acknowledgment.acknowledge();
                return; // Skip duplicate processing
            }

            // Continue parsing event
            userId = UUID.fromString((String) event.get("userId"));
            UUID walletId = UUID.fromString((String) event.get("walletId"));
            transactionType = (String) event.get("transactionType");
            String transactionStatus = (String) event.get("transactionStatus");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            BigDecimal balanceBeforeTransaction = new BigDecimal(event.get("balanceBeforeTransaction").toString());
            BigDecimal balanceAfterTransaction = new BigDecimal(event.get("balanceAfterTransaction").toString());
            String description = (String) event.get("description");
            String category = (String) event.get("category");
            UUID referenceId = event.containsKey("referenceId") ? 
                    UUID.fromString((String) event.get("referenceId")) : null;
            String referenceType = (String) event.get("referenceType");
            LocalDateTime transactionTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String merchantName = (String) event.get("merchantName");
            UUID merchantId = event.containsKey("merchantId") ? 
                    UUID.fromString((String) event.get("merchantId")) : null;
            Boolean isReversible = (Boolean) event.getOrDefault("isReversible", true);
            Boolean requiresNotification = (Boolean) event.getOrDefault("requiresNotification", true);
            
            log.info("Wallet transaction - TxnId: {}, UserId: {}, WalletId: {}, Type: {}, Status: {}, Amount: {} {}, BalanceBefore: {} {}, BalanceAfter: {} {}", 
                    transactionId, userId, walletId, transactionType, transactionStatus, amount, currency, 
                    balanceBeforeTransaction, currency, balanceAfterTransaction, currency);
            
            validateWalletTransaction(transactionId, userId, walletId, transactionType, transactionStatus, 
                    amount, currency);
            
            processTransactionByType(transactionId, userId, walletId, transactionType, transactionStatus, 
                    amount, currency, balanceBeforeTransaction, balanceAfterTransaction, description, 
                    category, referenceId, referenceType, transactionTimestamp, merchantName, merchantId);
            
            if ("COMPLETED".equals(transactionStatus)) {
                handleCompletedTransaction(transactionId, userId, walletId, transactionType, amount, 
                        currency, balanceAfterTransaction, transactionTimestamp);
            } else if ("PENDING".equals(transactionStatus)) {
                handlePendingTransaction(transactionId, userId, walletId, amount, currency);
            } else if ("FAILED".equals(transactionStatus)) {
                handleFailedTransaction(transactionId, userId, walletId, transactionType, amount, 
                        currency);
            } else if ("REVERSED".equals(transactionStatus)) {
                handleReversedTransaction(transactionId, userId, walletId, amount, currency, 
                        balanceAfterTransaction);
            }
            
            updateWalletBalance(walletId, userId, balanceAfterTransaction, currency);
            
            if (requiresNotification) {
                notifyUser(userId, transactionId, transactionType, transactionStatus, amount, 
                        currency, balanceAfterTransaction, merchantName);
            }
            
            checkBalanceThresholds(userId, walletId, balanceAfterTransaction, currency);
            
            updateTransactionMetrics(transactionType, transactionStatus, amount, currency, category);
            
            auditWalletTransaction(transactionId, userId, walletId, transactionType, transactionStatus, 
                    amount, currency, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();

            log.info("Wallet transaction processed - TxnId: {}, Type: {}, Status: {}, ProcessingTime: {}ms",
                    transactionId, transactionType, transactionStatus, processingTimeMs);

            // ✅ CRITICAL PRODUCTION FIX: Mark transaction as processed for idempotency
            // Uses Redis with 7-day TTL to prevent duplicate wallet transactions
            eventProcessingService.markAsProcessed(idempotencyKey, Map.of(
                "transactionId", transactionId.toString(),
                "userId", userId.toString(),
                "transactionType", transactionType,
                "transactionStatus", transactionStatus,
                "amount", amount.toString(),
                "currency", currency,
                "processedAt", LocalDateTime.now().toString()
            ));

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing wallet event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            if (transactionId != null && userId != null) {
                handleTransactionFailure(transactionId, userId, transactionType, e);
            }

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet event - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet event processing failed", e);
        }
    }
    
    private void validateWalletTransaction(UUID transactionId, UUID userId, UUID walletId,
                                          String transactionType, String transactionStatus,
                                          BigDecimal amount, String currency) {
        if (transactionId == null || userId == null || walletId == null) {
            throw new IllegalArgumentException("Transaction ID, User ID, and Wallet ID are required");
        }
        
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        
        if (transactionStatus == null || transactionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction status is required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        log.debug("Wallet transaction validation passed - TxnId: {}", transactionId);
    }
    
    private void processTransactionByType(UUID transactionId, UUID userId, UUID walletId,
                                         String transactionType, String transactionStatus,
                                         BigDecimal amount, String currency,
                                         BigDecimal balanceBeforeTransaction,
                                         BigDecimal balanceAfterTransaction, String description,
                                         String category, UUID referenceId, String referenceType,
                                         LocalDateTime transactionTimestamp, String merchantName,
                                         UUID merchantId) {
        try {
            switch (transactionType) {
                case "CREDIT" -> processCreditTransaction(transactionId, userId, walletId, amount, 
                        currency, balanceAfterTransaction, description, referenceId, referenceType);
                
                case "DEBIT" -> processDebitTransaction(transactionId, userId, walletId, amount, 
                        currency, balanceAfterTransaction, description, merchantName, merchantId);
                
                case "TRANSFER_IN" -> processTransferIn(transactionId, userId, walletId, amount, 
                        currency, balanceAfterTransaction, referenceId, referenceType);
                
                case "TRANSFER_OUT" -> processTransferOut(transactionId, userId, walletId, amount, 
                        currency, balanceAfterTransaction, referenceId, referenceType);
                
                case "PAYMENT" -> processPayment(transactionId, userId, walletId, amount, currency, 
                        merchantName, merchantId, referenceId);
                
                case "REFUND" -> processRefund(transactionId, userId, walletId, amount, currency, 
                        balanceAfterTransaction, referenceId);
                
                case "FEE" -> processFee(transactionId, userId, walletId, amount, currency, description);
                
                case "CASHBACK" -> processCashback(transactionId, userId, walletId, amount, currency, 
                        balanceAfterTransaction, referenceId);
                
                case "INTEREST" -> processInterest(transactionId, userId, walletId, amount, currency, 
                        balanceAfterTransaction);
                
                default -> {
                    log.warn("Unknown transaction type: {}", transactionType);
                    processGenericTransaction(transactionId, userId, walletId, transactionType);
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
    
    private void processCreditTransaction(UUID transactionId, UUID userId, UUID walletId,
                                         BigDecimal amount, String currency,
                                         BigDecimal balanceAfterTransaction, String description,
                                         UUID referenceId, String referenceType) {
        log.info("Processing CREDIT transaction - TxnId: {}, Amount: {} {}, NewBalance: {} {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency);
        
        walletTransactionService.processCredit(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, description, referenceId, referenceType);
    }
    
    private void processDebitTransaction(UUID transactionId, UUID userId, UUID walletId,
                                        BigDecimal amount, String currency,
                                        BigDecimal balanceAfterTransaction, String description,
                                        String merchantName, UUID merchantId) {
        log.info("Processing DEBIT transaction - TxnId: {}, Amount: {} {}, NewBalance: {} {}, Merchant: {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency, merchantName);
        
        walletTransactionService.processDebit(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, description, merchantName, merchantId);
    }
    
    private void processTransferIn(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                                  String currency, BigDecimal balanceAfterTransaction, UUID referenceId,
                                  String referenceType) {
        log.info("Processing TRANSFER_IN - TxnId: {}, Amount: {} {}, NewBalance: {} {}, Ref: {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency, referenceId);
        
        walletTransactionService.processTransferIn(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, referenceId, referenceType);
    }
    
    private void processTransferOut(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                                   String currency, BigDecimal balanceAfterTransaction, UUID referenceId,
                                   String referenceType) {
        log.info("Processing TRANSFER_OUT - TxnId: {}, Amount: {} {}, NewBalance: {} {}, Ref: {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency, referenceId);
        
        walletTransactionService.processTransferOut(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, referenceId, referenceType);
    }
    
    private void processPayment(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                               String currency, String merchantName, UUID merchantId, UUID referenceId) {
        log.info("Processing PAYMENT - TxnId: {}, Amount: {} {}, Merchant: {} ({})", 
                transactionId, amount, currency, merchantName, merchantId);
        
        walletTransactionService.processPayment(transactionId, userId, walletId, amount, currency, 
                merchantName, merchantId, referenceId);
    }
    
    private void processRefund(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                              String currency, BigDecimal balanceAfterTransaction, UUID referenceId) {
        log.info("Processing REFUND - TxnId: {}, Amount: {} {}, NewBalance: {} {}, OriginalTxn: {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency, referenceId);
        
        walletTransactionService.processRefund(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, referenceId);
    }
    
    private void processFee(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                           String currency, String description) {
        log.info("Processing FEE - TxnId: {}, Amount: {} {}, Description: {}", 
                transactionId, amount, currency, description);
        
        walletTransactionService.processFee(transactionId, userId, walletId, amount, currency, 
                description);
    }
    
    private void processCashback(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                                String currency, BigDecimal balanceAfterTransaction, UUID referenceId) {
        log.info("Processing CASHBACK - TxnId: {}, Amount: {} {}, NewBalance: {} {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency);
        
        walletTransactionService.processCashback(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction, referenceId);
    }
    
    private void processInterest(UUID transactionId, UUID userId, UUID walletId, BigDecimal amount,
                                String currency, BigDecimal balanceAfterTransaction) {
        log.info("Processing INTEREST - TxnId: {}, Amount: {} {}, NewBalance: {} {}", 
                transactionId, amount, currency, balanceAfterTransaction, currency);
        
        walletTransactionService.processInterest(transactionId, userId, walletId, amount, currency, 
                balanceAfterTransaction);
    }
    
    private void processGenericTransaction(UUID transactionId, UUID userId, UUID walletId,
                                          String transactionType) {
        log.info("Processing generic wallet transaction - TxnId: {}, Type: {}", 
                transactionId, transactionType);
        
        walletTransactionService.processGeneric(transactionId, userId, walletId, transactionType);
    }
    
    private void handleCompletedTransaction(UUID transactionId, UUID userId, UUID walletId,
                                           String transactionType, BigDecimal amount, String currency,
                                           BigDecimal balanceAfterTransaction,
                                           LocalDateTime transactionTimestamp) {
        try {
            log.info("Processing completed wallet transaction - TxnId: {}, Type: {}, Amount: {} {}", 
                    transactionId, transactionType, amount, currency);
            
            walletTransactionService.recordCompletedTransaction(transactionId, userId, walletId, 
                    transactionType, amount, currency, balanceAfterTransaction, transactionTimestamp);
            
        } catch (Exception e) {
            log.error("Failed to handle completed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handlePendingTransaction(UUID transactionId, UUID userId, UUID walletId,
                                         BigDecimal amount, String currency) {
        try {
            log.info("Processing pending wallet transaction - TxnId: {}, Amount: {} {}", 
                    transactionId, amount, currency);
            
            walletTransactionService.recordPendingTransaction(transactionId, userId, walletId, amount, 
                    currency);
            
        } catch (Exception e) {
            log.error("Failed to handle pending transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleFailedTransaction(UUID transactionId, UUID userId, UUID walletId,
                                        String transactionType, BigDecimal amount, String currency) {
        try {
            log.error("Processing failed wallet transaction - TxnId: {}, Type: {}, Amount: {} {}", 
                    transactionId, transactionType, amount, currency);
            
            walletTransactionService.recordFailedTransaction(transactionId, userId, walletId, 
                    transactionType, amount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle failed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleReversedTransaction(UUID transactionId, UUID userId, UUID walletId,
                                          BigDecimal amount, String currency,
                                          BigDecimal balanceAfterTransaction) {
        try {
            log.info("Processing reversed wallet transaction - TxnId: {}, Amount: {} {}, NewBalance: {} {}", 
                    transactionId, amount, currency, balanceAfterTransaction, currency);
            
            walletTransactionService.recordReversedTransaction(transactionId, userId, walletId, amount, 
                    currency, balanceAfterTransaction);
            
        } catch (Exception e) {
            log.error("Failed to handle reversed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void updateWalletBalance(UUID walletId, UUID userId, BigDecimal balanceAfterTransaction,
                                    String currency) {
        try {
            walletBalanceService.updateBalance(walletId, userId, balanceAfterTransaction, currency);
            
            log.debug("Wallet balance updated - WalletId: {}, Balance: {} {}", 
                    walletId, balanceAfterTransaction, currency);
            
        } catch (Exception e) {
            log.error("Failed to update wallet balance - WalletId: {}", walletId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID transactionId, String transactionType,
                           String transactionStatus, BigDecimal amount, String currency,
                           BigDecimal balanceAfterTransaction, String merchantName) {
        try {
            walletNotificationService.sendTransactionNotification(userId, transactionId, 
                    transactionType, transactionStatus, amount, currency, balanceAfterTransaction, 
                    merchantName);
            
            log.info("User notified of wallet transaction - UserId: {}, TxnId: {}, Type: {}", 
                    userId, transactionId, transactionType);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, TxnId: {}", userId, transactionId, e);
        }
    }
    
    private void checkBalanceThresholds(UUID userId, UUID walletId, BigDecimal balanceAfterTransaction,
                                       String currency) {
        try {
            if (balanceAfterTransaction.compareTo(new BigDecimal("100")) < 0) {
                walletNotificationService.sendLowBalanceAlert(userId, walletId, 
                        balanceAfterTransaction, currency);
                log.warn("Low balance alert sent - WalletId: {}, Balance: {} {}", 
                        walletId, balanceAfterTransaction, currency);
            }
            
            if (balanceAfterTransaction.compareTo(new BigDecimal("10")) < 0) {
                walletNotificationService.sendCriticalBalanceAlert(userId, walletId, 
                        balanceAfterTransaction, currency);
                log.error("Critical low balance - WalletId: {}, Balance: {} {}", 
                        walletId, balanceAfterTransaction, currency);
            }
            
        } catch (Exception e) {
            log.error("Failed to check balance thresholds - WalletId: {}", walletId, e);
        }
    }
    
    private void updateTransactionMetrics(String transactionType, String transactionStatus,
                                         BigDecimal amount, String currency, String category) {
        try {
            walletMetricsService.updateTransactionMetrics(transactionType, transactionStatus, amount, 
                    currency, category);
        } catch (Exception e) {
            log.error("Failed to update transaction metrics - Type: {}, Status: {}", 
                    transactionType, transactionStatus, e);
        }
    }
    
    private void auditWalletTransaction(UUID transactionId, UUID userId, UUID walletId,
                                       String transactionType, String transactionStatus,
                                       BigDecimal amount, String currency, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "WALLET_TRANSACTION_PROCESSED",
                    userId.toString(),
                    String.format("Wallet transaction %s - Type: %s, Amount: %s %s", 
                            transactionStatus, transactionType, amount, currency),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId.toString(),
                            "walletId", walletId.toString(),
                            "transactionType", transactionType,
                            "transactionStatus", transactionStatus,
                            "amount", amount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit wallet transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleTransactionFailure(UUID transactionId, UUID userId, String transactionType,
                                         Exception error) {
        try {
            walletTransactionService.handleTransactionFailure(transactionId, userId, transactionType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "WALLET_TRANSACTION_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process wallet transaction: " + error.getMessage(),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId.toString(),
                            "transactionType", transactionType != null ? transactionType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle transaction failure - TxnId: {}", transactionId, e);
        }
    }
    
    @KafkaListener(
        topics = {"wallet-transaction-events.DLQ", "wallet-transactions.DLQ", "wallet-activity.DLQ"},
        groupId = "wallet-service-transaction-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Wallet transaction event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String transactionType = (String) event.get("transactionType");
            
            log.error("DLQ: Wallet transaction failed permanently - TxnId: {}, UserId: {}, Type: {} - MANUAL INTERVENTION REQUIRED", 
                    transactionId, userId, transactionType);
            
            if (transactionId != null && userId != null) {
                walletTransactionService.markForManualReview(transactionId, userId, transactionType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse wallet transaction DLQ event: {}", eventJson, e);
        }
    }
}