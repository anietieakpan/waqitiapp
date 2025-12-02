package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRODUCTION-READY Transaction Notification Request
 * Used for multi-channel transaction notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionNotificationRequest {
    // Transaction identification
    private String transactionId;
    private String userId;
    private String transactionReference;

    // Notification configuration
    private String notificationType; // TRANSACTION_SUCCESS, TRANSACTION_FAILED, etc.
    private List<String> channels; // EMAIL, SMS, PUSH
    private String priority; // HIGH, NORMAL, LOW

    // Transaction details
    private BigDecimal amount;
    private String currency;
    private String sourceWalletId;
    private String destinationWalletId;
    private String description;

    // Receipt data
    private Map<String, Object> receiptData;

    // Additional metadata
    private LocalDateTime timestamp;
    private String failureReason; // For failure notifications
}