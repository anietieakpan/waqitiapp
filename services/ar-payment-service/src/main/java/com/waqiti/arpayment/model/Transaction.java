package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a payment transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private UUID id;
    private String transactionId;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private UUID senderId;
    private String senderName;
    private UUID recipientId;
    private String recipientName;
    private String description;
    private Instant timestamp;
    private String paymentMethod;
    private Map<String, Object> metadata;
    private String referenceNumber;
    private BigDecimal fee;
    private BigDecimal exchangeRate;
    private String errorMessage;
    
    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        DEPOSIT,
        WITHDRAWAL,
        REFUND,
        FEE,
        REWARD,
        ADJUSTMENT
    }
    
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED,
        EXPIRED
    }
}