/**
 * File: ./wallet-service/src/main/java/com/waqiti/wallet/service/TransactionLogger.java
 */
package com.waqiti.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for logging transaction events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLogger {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TRANSACTION_TOPIC = "wallet-transactions";
    private static final String WALLET_EVENTS_TOPIC = "wallet-events";

    /**
     * Logs a transaction event
     */
    public void logTransaction(Transaction transaction) {
        try {
            Map<String, Object> transactionEvent = new HashMap<>();
            transactionEvent.put("eventType", "TRANSACTION");
            transactionEvent.put("transactionId", transaction.getId());
            transactionEvent.put("externalId", transaction.getExternalId());
            transactionEvent.put("sourceWalletId", transaction.getSourceWalletId());
            transactionEvent.put("targetWalletId", transaction.getTargetWalletId());
            transactionEvent.put("amount", transaction.getAmount());
            transactionEvent.put("currency", transaction.getCurrency());
            transactionEvent.put("type", transaction.getType().toString());
            transactionEvent.put("status", transaction.getStatus().toString());
            transactionEvent.put("description", transaction.getDescription());
            transactionEvent.put("createdAt", transaction.getCreatedAt().toString());
            transactionEvent.put("timestamp", LocalDateTime.now().toString());

            String eventJson = objectMapper.writeValueAsString(transactionEvent);
            kafkaTemplate.send(TRANSACTION_TOPIC, transaction.getId().toString(), eventJson);

            log.info("Transaction event logged: {}", transaction.getId());
        } catch (Exception e) {
            log.error("Failed to log transaction event", e);
        }
    }

    /**
     * Logs a wallet notification event
     */
    public void logWalletEvent(UUID userId, UUID walletId, String transactionType,
                               BigDecimal amount, String currency, UUID transactionId) {
        try {
            Map<String, Object> walletEvent = new HashMap<>();
            walletEvent.put("eventType", "WALLET_TRANSACTION");
            walletEvent.put("userId", userId);
            walletEvent.put("walletId", walletId);
            walletEvent.put("transactionType", transactionType);
            walletEvent.put("amount", amount);
            walletEvent.put("currency", currency);
            walletEvent.put("transactionId", transactionId);
            walletEvent.put("timestamp", LocalDateTime.now().toString());

            String eventJson = objectMapper.writeValueAsString(walletEvent);
            kafkaTemplate.send(WALLET_EVENTS_TOPIC, userId.toString(), eventJson);

            log.info("Wallet event logged for user: {}, transaction: {}", userId, transactionId);
        } catch (Exception e) {
            log.error("Failed to log wallet event", e);
        }
    }

    /**
     * Logs a transaction failure
     */
    public void logTransactionFailure(UUID transactionId, String errorMessage, String errorCode) {
        try {
            Map<String, Object> failureEvent = new HashMap<>();
            failureEvent.put("eventType", "TRANSACTION_FAILURE");
            failureEvent.put("transactionId", transactionId);
            failureEvent.put("errorMessage", errorMessage);
            failureEvent.put("errorCode", errorCode);
            failureEvent.put("timestamp", LocalDateTime.now().toString());

            String eventJson = objectMapper.writeValueAsString(failureEvent);
            kafkaTemplate.send(TRANSACTION_TOPIC, transactionId.toString(), eventJson);

            log.info("Transaction failure logged: {}", transactionId);
        } catch (Exception e) {
            log.error("Failed to log transaction failure", e);
        }
    }

    /**
     * Creates a transaction audit entry for a new transaction
     */
    public Transaction createTransactionAudit(UUID sourceWalletId, UUID targetWalletId,
                                              BigDecimal amount, String currency,
                                              TransactionType type, String description) {
        Transaction transaction = Transaction.create(
                sourceWalletId, targetWalletId, amount, currency, type, description);

        log.info("Created transaction audit: {}", transaction.getId());
        return transaction;
    }
}