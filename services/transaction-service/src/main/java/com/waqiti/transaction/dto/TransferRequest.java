package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer request DTO for P2P transfers.
 * Comprehensive request object for all transfer types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "Sender ID is required")
    private UUID senderId;

    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    private String description;
    private String reference;
    private String externalReference;
    
    // Account information
    private String sourceAccountId;
    private String targetAccountId;
    private String sourceWalletId;
    private String targetWalletId;
    
    // Transfer type and category
    private String transferType; // P2P, BANK_TRANSFER, INTERNATIONAL, etc.
    private String category; // PERSONAL, BUSINESS, INVESTMENT, etc.
    private String subcategory;
    
    // Scheduling
    private LocalDateTime scheduledDate;
    private Boolean isImmediate;
    private String frequency; // For recurring transfers
    
    // Fees and charges
    private BigDecimal fee;
    private BigDecimal tax;
    private String feeStructure;
    
    // Geographic and compliance
    private String sourceCountry;
    private String targetCountry;
    private Boolean isInternational;
    private String complianceNotes;
    
    // Device and session information
    private String deviceId;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    
    // Risk and fraud indicators
    private Boolean isFirstTime;
    private Integer dailyTransactionCount;
    private BigDecimal dailyTransactionVolume;
    private String riskLevel;
    
    // Notification preferences
    private Boolean notifySender;
    private Boolean notifyRecipient;
    private String[] notificationChannels;
    
    // Additional metadata
    private Map<String, String> metadata;
    private Map<String, Object> customFields;
    
    // Audit fields
    private String initiatedBy;
    private String channel; // WEB, MOBILE, API, etc.
    private LocalDateTime requestTimestamp;
    
    // Validation flags
    private Boolean requiresApproval;
    private Boolean bypassFraudCheck;
    private String approvalWorkflow;

    /**
     * Factory method for simple P2P transfer
     */
    public static TransferRequest createP2PTransfer(UUID senderId, UUID recipientId, 
                                                   BigDecimal amount, String currency, String description) {
        return TransferRequest.builder()
                .transactionId(UUID.randomUUID())
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(amount)
                .currency(currency)
                .description(description)
                .transferType("P2P")
                .category("PERSONAL")
                .isImmediate(true)
                .requestTimestamp(LocalDateTime.now())
                .notifySender(true)
                .notifyRecipient(true)
                .requiresApproval(false)
                .bypassFraudCheck(false)
                .build();
    }

    /**
     * Factory method for international transfer
     */
    public static TransferRequest createInternationalTransfer(UUID senderId, UUID recipientId,
                                                            BigDecimal amount, String currency,
                                                            String sourceCountry, String targetCountry,
                                                            String description) {
        return TransferRequest.builder()
                .transactionId(UUID.randomUUID())
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(amount)
                .currency(currency)
                .description(description)
                .transferType("INTERNATIONAL")
                .category("PERSONAL")
                .sourceCountry(sourceCountry)
                .targetCountry(targetCountry)
                .isInternational(true)
                .isImmediate(false) // International transfers typically not immediate
                .requestTimestamp(LocalDateTime.now())
                .notifySender(true)
                .notifyRecipient(true)
                .requiresApproval(true) // International transfers often require approval
                .bypassFraudCheck(false)
                .build();
    }

    /**
     * Validates if the transfer request has all required fields
     */
    public boolean isValid() {
        return transactionId != null &&
               senderId != null &&
               recipientId != null &&
               amount != null &&
               amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.trim().isEmpty();
    }

    /**
     * Determines if this is a high-value transaction
     */
    public boolean isHighValue() {
        if (amount == null) return false;
        
        // High value thresholds by currency (simplified)
        double threshold = switch (currency.toUpperCase()) {
            case "USD", "EUR", "GBP" -> 10000.0;
            case "JPY" -> 1000000.0;
            default -> 10000.0;
        };
        
        return amount.doubleValue() >= threshold;
    }

    /**
     * Calculates total amount including fees and taxes
     */
    public BigDecimal getTotalAmount() {
        BigDecimal total = amount;
        if (fee != null) {
            total = total.add(fee);
        }
        if (tax != null) {
            total = total.add(tax);
        }
        return total;
    }

    /**
     * Determines if this transfer requires enhanced due diligence
     */
    public boolean requiresEnhancedDueDiligence() {
        return isHighValue() ||
               Boolean.TRUE.equals(isInternational) ||
               Boolean.TRUE.equals(requiresApproval) ||
               "HIGH".equals(riskLevel);
    }
}