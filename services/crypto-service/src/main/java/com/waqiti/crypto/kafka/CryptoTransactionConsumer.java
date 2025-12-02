package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.service.BlockchainService;
import com.waqiti.crypto.service.CryptoComplianceService;
import com.waqiti.crypto.service.CryptoNotificationService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoTransactionConsumer {
    
    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletService cryptoWalletService;
    private final BlockchainService blockchainService;
    private final CryptoComplianceService cryptoComplianceService;
    private final CryptoNotificationService cryptoNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = {"crypto-transactions", "crypto-transaction-events"},
        groupId = "crypto-service-transaction-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleCryptoTransaction(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("CRYPTO TRANSACTION: Processing crypto transaction - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transactionId = null;
        UUID userId = null;
        String transactionType = null;
        String idempotencyKey = null;
        UUID operationId = UUID.randomUUID();

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            transactionId = UUID.fromString((String) event.get("transactionId"));
            userId = UUID.fromString((String) event.get("userId"));
            transactionType = (String) event.get("transactionType");

            // CRITICAL SECURITY: Idempotency check
            idempotencyKey = String.format("crypto-transaction:%s:%s", transactionId, userId);
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto transaction ignored: transactionId={}", transactionId);
                acknowledgment.acknowledge();
                return;
            }
            String transactionStatus = (String) event.get("transactionStatus");
            String cryptocurrency = (String) event.get("cryptocurrency");
            String cryptoSymbol = (String) event.get("cryptoSymbol");
            BigDecimal cryptoAmount = new BigDecimal(event.get("cryptoAmount").toString());
            BigDecimal fiatAmount = event.containsKey("fiatAmount") ? 
                    new BigDecimal(event.get("fiatAmount").toString()) : null;
            String fiatCurrency = (String) event.getOrDefault("fiatCurrency", "USD");
            BigDecimal exchangeRate = event.containsKey("exchangeRate") ? 
                    new BigDecimal(event.get("exchangeRate").toString()) : null;
            String sourceAddress = (String) event.get("sourceAddress");
            String destinationAddress = (String) event.get("destinationAddress");
            String blockchainNetwork = (String) event.get("blockchainNetwork");
            String transactionHash = (String) event.get("transactionHash");
            Integer confirmations = event.containsKey("confirmations") ? 
                    (Integer) event.get("confirmations") : 0;
            BigDecimal networkFee = event.containsKey("networkFee") ? 
                    new BigDecimal(event.get("networkFee").toString()) : BigDecimal.ZERO;
            BigDecimal platformFee = event.containsKey("platformFee") ? 
                    new BigDecimal(event.get("platformFee").toString()) : BigDecimal.ZERO;
            LocalDateTime transactionTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            Boolean requiresCompliance = (Boolean) event.getOrDefault("requiresCompliance", false);
            
            log.info("Crypto transaction - TxnId: {}, UserId: {}, Type: {}, Status: {}, Crypto: {} {}, Fiat: {} {}, Hash: {}, Confirmations: {}", 
                    transactionId, userId, transactionType, transactionStatus, cryptoAmount, 
                    cryptoSymbol, fiatAmount, fiatCurrency, transactionHash, confirmations);
            
            validateCryptoTransaction(transactionId, userId, transactionType, transactionStatus, 
                    cryptocurrency, cryptoAmount);
            
            processTransactionByType(transactionId, userId, transactionType, transactionStatus, 
                    cryptocurrency, cryptoSymbol, cryptoAmount, fiatAmount, fiatCurrency, 
                    exchangeRate, sourceAddress, destinationAddress, blockchainNetwork, 
                    transactionHash, confirmations, networkFee, platformFee, transactionTimestamp);
            
            if ("CONFIRMED".equals(transactionStatus)) {
                handleConfirmedTransaction(transactionId, userId, transactionType, cryptocurrency, 
                        cryptoSymbol, cryptoAmount, fiatAmount, fiatCurrency, sourceAddress, 
                        destinationAddress, transactionHash, confirmations);
            } else if ("PENDING".equals(transactionStatus)) {
                handlePendingTransaction(transactionId, userId, transactionType, cryptocurrency, 
                        cryptoAmount, transactionHash, confirmations);
            } else if ("FAILED".equals(transactionStatus)) {
                handleFailedTransaction(transactionId, userId, transactionType, cryptocurrency, 
                        cryptoAmount, transactionHash);
            }
            
            if (requiresCompliance || fiatAmount != null && fiatAmount.compareTo(new BigDecimal("10000")) > 0) {
                performComplianceCheck(transactionId, userId, transactionType, cryptocurrency, 
                        cryptoAmount, fiatAmount, fiatCurrency, sourceAddress, destinationAddress);
            }
            
            updateWalletBalance(userId, transactionType, cryptocurrency, cryptoAmount, 
                    transactionStatus);
            
            trackBlockchainConfirmation(transactionId, transactionHash, blockchainNetwork, 
                    confirmations);
            
            notifyUser(userId, transactionId, transactionType, transactionStatus, cryptocurrency, 
                    cryptoAmount, fiatAmount, fiatCurrency, transactionHash);
            
            updateCryptoMetrics(transactionType, transactionStatus, cryptocurrency, cryptoAmount, 
                    fiatAmount);
            
            auditCryptoTransaction(transactionId, userId, transactionType, transactionStatus, 
                    cryptocurrency, cryptoAmount, fiatAmount, fiatCurrency, transactionHash, 
                    processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Crypto transaction processed - TxnId: {}, Type: {}, Status: {}, ProcessingTime: {}ms", 
                    transactionId, transactionType, transactionStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Crypto transaction processing failed - TxnId: {}, UserId: {}, Type: {}, Error: {}", 
                    transactionId, userId, transactionType, e.getMessage(), e);
            
            if (transactionId != null && userId != null) {
                handleTransactionFailure(transactionId, userId, transactionType, e);
            }
            
            throw new RuntimeException("Crypto transaction processing failed", e);
        }
    }
    
    private void validateCryptoTransaction(UUID transactionId, UUID userId, String transactionType,
                                          String transactionStatus, String cryptocurrency,
                                          BigDecimal cryptoAmount) {
        if (transactionId == null || userId == null) {
            throw new IllegalArgumentException("Transaction ID and User ID are required");
        }
        
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        
        if (transactionStatus == null || transactionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction status is required");
        }
        
        if (cryptocurrency == null || cryptocurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency is required");
        }
        
        if (cryptoAmount == null || cryptoAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid crypto amount");
        }
        
        log.debug("Crypto transaction validation passed - TxnId: {}", transactionId);
    }
    
    private void processTransactionByType(UUID transactionId, UUID userId, String transactionType,
                                         String transactionStatus, String cryptocurrency,
                                         String cryptoSymbol, BigDecimal cryptoAmount,
                                         BigDecimal fiatAmount, String fiatCurrency,
                                         BigDecimal exchangeRate, String sourceAddress,
                                         String destinationAddress, String blockchainNetwork,
                                         String transactionHash, Integer confirmations,
                                         BigDecimal networkFee, BigDecimal platformFee,
                                         LocalDateTime transactionTimestamp) {
        try {
            switch (transactionType) {
                case "BUY" -> processBuyTransaction(transactionId, userId, cryptocurrency, cryptoSymbol, 
                        cryptoAmount, fiatAmount, fiatCurrency, exchangeRate, platformFee, transactionTimestamp);
                
                case "SELL" -> processSellTransaction(transactionId, userId, cryptocurrency, cryptoSymbol, 
                        cryptoAmount, fiatAmount, fiatCurrency, exchangeRate, platformFee, transactionTimestamp);
                
                case "SEND" -> processSendTransaction(transactionId, userId, cryptocurrency, cryptoSymbol, 
                        cryptoAmount, sourceAddress, destinationAddress, blockchainNetwork, 
                        transactionHash, networkFee);
                
                case "RECEIVE" -> processReceiveTransaction(transactionId, userId, cryptocurrency, 
                        cryptoSymbol, cryptoAmount, sourceAddress, destinationAddress, blockchainNetwork, 
                        transactionHash);
                
                case "SWAP" -> processSwapTransaction(transactionId, userId, cryptocurrency, cryptoAmount, 
                        exchangeRate, platformFee);
                
                case "STAKE" -> processStakeTransaction(transactionId, userId, cryptocurrency, cryptoAmount);
                
                case "UNSTAKE" -> processUnstakeTransaction(transactionId, userId, cryptocurrency, cryptoAmount);
                
                default -> {
                    log.warn("Unknown crypto transaction type: {}", transactionType);
                    processGenericTransaction(transactionId, userId, transactionType);
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
    
    private void processBuyTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                      String cryptoSymbol, BigDecimal cryptoAmount, BigDecimal fiatAmount,
                                      String fiatCurrency, BigDecimal exchangeRate, BigDecimal platformFee,
                                      LocalDateTime transactionTimestamp) {
        log.info("Processing BUY transaction - TxnId: {}, Crypto: {} {}, Fiat: {} {}, Rate: {}", 
                transactionId, cryptoAmount, cryptoSymbol, fiatAmount, fiatCurrency, exchangeRate);
        
        cryptoTransactionService.processBuy(transactionId, userId, cryptocurrency, cryptoSymbol, 
                cryptoAmount, fiatAmount, fiatCurrency, exchangeRate, platformFee, transactionTimestamp);
    }
    
    private void processSellTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                       String cryptoSymbol, BigDecimal cryptoAmount, BigDecimal fiatAmount,
                                       String fiatCurrency, BigDecimal exchangeRate, BigDecimal platformFee,
                                       LocalDateTime transactionTimestamp) {
        log.info("Processing SELL transaction - TxnId: {}, Crypto: {} {}, Fiat: {} {}, Rate: {}", 
                transactionId, cryptoAmount, cryptoSymbol, fiatAmount, fiatCurrency, exchangeRate);
        
        cryptoTransactionService.processSell(transactionId, userId, cryptocurrency, cryptoSymbol, 
                cryptoAmount, fiatAmount, fiatCurrency, exchangeRate, platformFee, transactionTimestamp);
    }
    
    private void processSendTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                       String cryptoSymbol, BigDecimal cryptoAmount, String sourceAddress,
                                       String destinationAddress, String blockchainNetwork,
                                       String transactionHash, BigDecimal networkFee) {
        log.info("Processing SEND transaction - TxnId: {}, Crypto: {} {}, From: {}, To: {}, Network: {}", 
                transactionId, cryptoAmount, cryptoSymbol, sourceAddress, destinationAddress, 
                blockchainNetwork);
        
        cryptoTransactionService.processSend(transactionId, userId, cryptocurrency, cryptoSymbol, 
                cryptoAmount, sourceAddress, destinationAddress, blockchainNetwork, transactionHash, 
                networkFee);
    }
    
    private void processReceiveTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                          String cryptoSymbol, BigDecimal cryptoAmount, String sourceAddress,
                                          String destinationAddress, String blockchainNetwork,
                                          String transactionHash) {
        log.info("Processing RECEIVE transaction - TxnId: {}, Crypto: {} {}, From: {}, To: {}, Network: {}", 
                transactionId, cryptoAmount, cryptoSymbol, sourceAddress, destinationAddress, 
                blockchainNetwork);
        
        cryptoTransactionService.processReceive(transactionId, userId, cryptocurrency, cryptoSymbol, 
                cryptoAmount, sourceAddress, destinationAddress, blockchainNetwork, transactionHash);
    }
    
    private void processSwapTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                       BigDecimal cryptoAmount, BigDecimal exchangeRate,
                                       BigDecimal platformFee) {
        log.info("Processing SWAP transaction - TxnId: {}, Crypto: {} {}, Rate: {}", 
                transactionId, cryptoAmount, cryptocurrency, exchangeRate);
        
        cryptoTransactionService.processSwap(transactionId, userId, cryptocurrency, cryptoAmount, 
                exchangeRate, platformFee);
    }
    
    private void processStakeTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                        BigDecimal cryptoAmount) {
        log.info("Processing STAKE transaction - TxnId: {}, Crypto: {} {}", 
                transactionId, cryptoAmount, cryptocurrency);
        
        cryptoTransactionService.processStake(transactionId, userId, cryptocurrency, cryptoAmount);
    }
    
    private void processUnstakeTransaction(UUID transactionId, UUID userId, String cryptocurrency,
                                          BigDecimal cryptoAmount) {
        log.info("Processing UNSTAKE transaction - TxnId: {}, Crypto: {} {}", 
                transactionId, cryptoAmount, cryptocurrency);
        
        cryptoTransactionService.processUnstake(transactionId, userId, cryptocurrency, cryptoAmount);
    }
    
    private void processGenericTransaction(UUID transactionId, UUID userId, String transactionType) {
        log.info("Processing generic crypto transaction - TxnId: {}, Type: {}", transactionId, transactionType);
        
        cryptoTransactionService.processGeneric(transactionId, userId, transactionType);
    }
    
    private void handleConfirmedTransaction(UUID transactionId, UUID userId, String transactionType,
                                           String cryptocurrency, String cryptoSymbol,
                                           BigDecimal cryptoAmount, BigDecimal fiatAmount,
                                           String fiatCurrency, String sourceAddress,
                                           String destinationAddress, String transactionHash,
                                           Integer confirmations) {
        try {
            log.info("Processing confirmed crypto transaction - TxnId: {}, Hash: {}, Confirmations: {}", 
                    transactionId, transactionHash, confirmations);
            
            cryptoTransactionService.recordConfirmedTransaction(transactionId, userId, transactionType, 
                    cryptocurrency, cryptoSymbol, cryptoAmount, fiatAmount, fiatCurrency, 
                    sourceAddress, destinationAddress, transactionHash, confirmations);
            
        } catch (Exception e) {
            log.error("Failed to handle confirmed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handlePendingTransaction(UUID transactionId, UUID userId, String transactionType,
                                         String cryptocurrency, BigDecimal cryptoAmount,
                                         String transactionHash, Integer confirmations) {
        try {
            log.info("Processing pending crypto transaction - TxnId: {}, Hash: {}, Confirmations: {}/{}", 
                    transactionId, transactionHash, confirmations, 6);
            
            cryptoTransactionService.trackPendingTransaction(transactionId, userId, transactionType, 
                    cryptocurrency, cryptoAmount, transactionHash, confirmations);
            
        } catch (Exception e) {
            log.error("Failed to handle pending transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleFailedTransaction(UUID transactionId, UUID userId, String transactionType,
                                        String cryptocurrency, BigDecimal cryptoAmount,
                                        String transactionHash) {
        try {
            log.error("Processing failed crypto transaction - TxnId: {}, Hash: {}", 
                    transactionId, transactionHash);
            
            cryptoTransactionService.recordFailedTransaction(transactionId, userId, transactionType, 
                    cryptocurrency, cryptoAmount, transactionHash);
            
        } catch (Exception e) {
            log.error("Failed to handle failed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void performComplianceCheck(UUID transactionId, UUID userId, String transactionType,
                                       String cryptocurrency, BigDecimal cryptoAmount,
                                       BigDecimal fiatAmount, String fiatCurrency,
                                       String sourceAddress, String destinationAddress) {
        try {
            log.info("Performing compliance check - TxnId: {}, Type: {}, FiatAmount: {} {}", 
                    transactionId, transactionType, fiatAmount, fiatCurrency);
            
            cryptoComplianceService.performComplianceCheck(transactionId, userId, transactionType, 
                    cryptocurrency, cryptoAmount, fiatAmount, fiatCurrency, sourceAddress, 
                    destinationAddress);
            
        } catch (Exception e) {
            log.error("Failed to perform compliance check - TxnId: {}", transactionId, e);
        }
    }
    
    private void updateWalletBalance(UUID userId, String transactionType, String cryptocurrency,
                                    BigDecimal cryptoAmount, String transactionStatus) {
        try {
            if ("CONFIRMED".equals(transactionStatus)) {
                cryptoWalletService.updateBalance(userId, transactionType, cryptocurrency, cryptoAmount);
                
                log.debug("Wallet balance updated - UserId: {}, Crypto: {}, Amount: {}", 
                        userId, cryptocurrency, cryptoAmount);
            }
            
        } catch (Exception e) {
            log.error("Failed to update wallet balance - UserId: {}", userId, e);
        }
    }
    
    private void trackBlockchainConfirmation(UUID transactionId, String transactionHash,
                                            String blockchainNetwork, Integer confirmations) {
        try {
            blockchainService.trackConfirmation(transactionId, transactionHash, blockchainNetwork, 
                    confirmations);
            
            log.debug("Blockchain confirmation tracked - TxnId: {}, Hash: {}, Confirmations: {}", 
                    transactionId, transactionHash, confirmations);
            
        } catch (Exception e) {
            log.error("Failed to track blockchain confirmation - TxnId: {}", transactionId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID transactionId, String transactionType,
                           String transactionStatus, String cryptocurrency, BigDecimal cryptoAmount,
                           BigDecimal fiatAmount, String fiatCurrency, String transactionHash) {
        try {
            cryptoNotificationService.sendTransactionNotification(userId, transactionId, transactionType, 
                    transactionStatus, cryptocurrency, cryptoAmount, fiatAmount, fiatCurrency, 
                    transactionHash);
            
            log.info("User notified - UserId: {}, TxnId: {}, Type: {}, Status: {}", 
                    userId, transactionId, transactionType, transactionStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, TxnId: {}", userId, transactionId, e);
        }
    }
    
    private void updateCryptoMetrics(String transactionType, String transactionStatus,
                                    String cryptocurrency, BigDecimal cryptoAmount, BigDecimal fiatAmount) {
        try {
            cryptoTransactionService.updateTransactionMetrics(transactionType, transactionStatus, 
                    cryptocurrency, cryptoAmount, fiatAmount);
        } catch (Exception e) {
            log.error("Failed to update crypto metrics - Type: {}, Status: {}", 
                    transactionType, transactionStatus, e);
        }
    }
    
    private void auditCryptoTransaction(UUID transactionId, UUID userId, String transactionType,
                                       String transactionStatus, String cryptocurrency,
                                       BigDecimal cryptoAmount, BigDecimal fiatAmount, String fiatCurrency,
                                       String transactionHash, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "CRYPTO_TRANSACTION_PROCESSED",
                    userId.toString(),
                    String.format("Crypto transaction %s - Type: %s, Crypto: %s %s, Fiat: %s %s, Hash: %s", 
                            transactionStatus, transactionType, cryptoAmount, cryptocurrency, 
                            fiatAmount, fiatCurrency, transactionHash),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "userId", userId.toString(),
                            "transactionType", transactionType,
                            "transactionStatus", transactionStatus,
                            "cryptocurrency", cryptocurrency,
                            "cryptoAmount", cryptoAmount.toString(),
                            "fiatAmount", fiatAmount != null ? fiatAmount.toString() : "N/A",
                            "fiatCurrency", fiatCurrency,
                            "transactionHash", transactionHash != null ? transactionHash : "N/A",
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit crypto transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleTransactionFailure(UUID transactionId, UUID userId, String transactionType,
                                         Exception error) {
        try {
            cryptoTransactionService.handleTransactionFailure(transactionId, userId, transactionType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "CRYPTO_TRANSACTION_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process crypto transaction: " + error.getMessage(),
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
        topics = {"crypto-transactions.DLQ", "crypto-transaction-events.DLQ"},
        groupId = "crypto-service-transaction-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Crypto transaction event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String transactionType = (String) event.get("transactionType");
            
            log.error("DLQ: Crypto transaction failed permanently - TxnId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    transactionId, userId, transactionType);
            
            if (transactionId != null && userId != null) {
                cryptoTransactionService.markForManualReview(transactionId, userId, transactionType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse crypto transaction DLQ event: {}", eventJson, e);
        }
    }
}