package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single transaction's velocity data for tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionVelocity {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
    private String transactionType;
    private String channel;
    private String deviceId;
    private String ipAddress;
    private String location;

    public TransactionVelocity(TransactionData transaction) {
        this.transactionId = transaction.getTransactionId();
        this.userId = transaction.getUserId();
        this.amount = transaction.getAmount();
        this.currency = transaction.getCurrency();
        this.timestamp = transaction.getTimestamp();
        this.transactionType = transaction.getTransactionType();
        this.deviceId = transaction.getDeviceId();
        this.ipAddress = transaction.getIpAddress();
        this.location = transaction.getCountry();
    }
}
