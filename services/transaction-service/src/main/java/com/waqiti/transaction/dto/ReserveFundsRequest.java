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
 * Request DTO for reserving funds in an account.
 * Used to temporarily block funds for pending transactions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsRequest {
    
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    private String currency;
    
    @NotNull(message = "Account ID is required")
    private String accountId;
    
    // Reservation details
    private String purpose;
    private String description;
    private String reference;
    private String externalReference;
    
    // Reservation type
    private ReservationType reservationType;
    private String transactionType;
    
    // Duration and expiry
    private Integer durationMinutes;
    private LocalDateTime expiryTime;
    private Boolean autoRelease;
    
    // Priority and processing
    private Integer priority;
    private Boolean isUrgent;
    private Boolean requiresApproval;
    
    // Source information
    private String sourceSystem;
    private String sourceChannel;
    private String initiatedBy;
    
    // Recipient information (for transfers)
    private String recipientAccountId;
    private String recipientName;
    private String recipientBank;
    
    // Compliance and risk
    private String complianceCheckId;
    private String fraudCheckId;
    private Double riskScore;
    
    // Additional metadata
    private Map<String, String> metadata;
    private Map<String, Object> customFields;
    
    // Audit fields
    private LocalDateTime requestTimestamp;
    private String requestId;
    
    public enum ReservationType {
        PAYMENT,
        TRANSFER,
        WITHDRAWAL,
        PURCHASE,
        AUTHORIZATION,
        SETTLEMENT,
        ESCROW,
        HOLD
    }
    
    /**
     * Factory method for creating a simple fund reservation
     */
    public static ReserveFundsRequest createSimpleReservation(
            UUID transactionId, 
            String accountId, 
            BigDecimal amount, 
            String currency,
            String purpose) {
        return ReserveFundsRequest.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .purpose(purpose)
                .reservationType(ReservationType.PAYMENT)
                .durationMinutes(30) // Default 30 minutes
                .autoRelease(true)
                .requestTimestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }
    
    /**
     * Factory method for creating a transfer reservation
     */
    public static ReserveFundsRequest createTransferReservation(
            UUID transactionId,
            String sourceAccountId,
            String targetAccountId,
            BigDecimal amount,
            String currency,
            String description) {
        return ReserveFundsRequest.builder()
                .transactionId(transactionId)
                .accountId(sourceAccountId)
                .recipientAccountId(targetAccountId)
                .amount(amount)
                .currency(currency)
                .purpose("TRANSFER")
                .description(description)
                .reservationType(ReservationType.TRANSFER)
                .durationMinutes(15) // Shorter duration for transfers
                .autoRelease(true)
                .requestTimestamp(LocalDateTime.now())
                .requestId(UUID.randomUUID().toString())
                .build();
    }
    
    /**
     * Validates if the reservation request is valid
     */
    public boolean isValid() {
        return transactionId != null &&
               accountId != null && !accountId.trim().isEmpty() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.trim().isEmpty();
    }
    
    /**
     * Determines if this is a high-value reservation requiring special handling
     */
    public boolean isHighValue() {
        if (amount == null) return false;
        
        // High value thresholds by currency
        double threshold = switch (currency != null ? currency.toUpperCase() : "") {
            case "USD", "EUR", "GBP" -> 10000.0;
            case "JPY" -> 1000000.0;
            default -> 10000.0;
        };
        
        return amount.doubleValue() >= threshold;
    }
    
    /**
     * Gets the effective expiry time for the reservation
     */
    public LocalDateTime getEffectiveExpiryTime() {
        if (expiryTime != null) {
            return expiryTime;
        }
        
        int effectiveDuration = durationMinutes != null ? durationMinutes : 30;
        LocalDateTime baseTime = requestTimestamp != null ? requestTimestamp : LocalDateTime.now();
        return baseTime.plusMinutes(effectiveDuration);
    }
}