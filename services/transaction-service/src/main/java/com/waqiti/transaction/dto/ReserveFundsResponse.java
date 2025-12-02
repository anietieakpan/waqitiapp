package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for fund reservation operations.
 * Contains details about the successful reservation of funds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsResponse {
    
    // Reservation identifiers
    private String reservationId;
    private UUID transactionId;
    private String accountId;
    
    // Reservation details
    private BigDecimal reservedAmount;
    private String currency;
    private String status;
    private ReservationStatus reservationStatus;
    
    // Balance information after reservation
    private BigDecimal availableBalanceAfter;
    private BigDecimal blockedAmountAfter;
    private BigDecimal totalReservedAmount;
    
    // Timing information
    private LocalDateTime reservationTime;
    private LocalDateTime expiryTime;
    private Integer durationMinutes;
    private Boolean autoRelease;
    
    // Reference information
    private String reference;
    private String confirmationCode;
    private String authorizationCode;
    
    // Processing details
    private String processedBy;
    private String processingNode;
    private Long processingTimeMs;
    
    // Validation results
    private Boolean balanceCheckPassed;
    private Boolean limitCheckPassed;
    private Boolean complianceCheckPassed;
    
    // Additional metadata
    private Map<String, String> metadata;
    private Map<String, Object> processingDetails;
    
    // Warning or informational messages
    private String message;
    private String warningMessage;
    
    public enum ReservationStatus {
        ACTIVE,
        EXPIRED,
        RELEASED,
        CONSUMED,
        CANCELLED,
        FAILED
    }
    
    /**
     * Factory method for successful reservation
     */
    public static ReserveFundsResponse successfulReservation(
            String reservationId,
            UUID transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            LocalDateTime expiryTime) {
        return ReserveFundsResponse.builder()
                .reservationId(reservationId)
                .transactionId(transactionId)
                .accountId(accountId)
                .reservedAmount(amount)
                .currency(currency)
                .status("SUCCESS")
                .reservationStatus(ReservationStatus.ACTIVE)
                .reservationTime(LocalDateTime.now())
                .expiryTime(expiryTime)
                .balanceCheckPassed(true)
                .limitCheckPassed(true)
                .complianceCheckPassed(true)
                .confirmationCode(generateConfirmationCode())
                .build();
    }
    
    /**
     * Factory method for failed reservation
     */
    public static ReserveFundsResponse failedReservation(
            UUID transactionId,
            String accountId,
            String failureReason) {
        return ReserveFundsResponse.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .status("FAILED")
                .reservationStatus(ReservationStatus.FAILED)
                .reservationTime(LocalDateTime.now())
                .message(failureReason)
                .balanceCheckPassed(false)
                .build();
    }
    
    /**
     * Check if reservation is still active
     */
    public boolean isActive() {
        if (reservationStatus != ReservationStatus.ACTIVE) {
            return false;
        }
        
        if (expiryTime != null && LocalDateTime.now().isAfter(expiryTime)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if reservation was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status) && 
               reservationId != null && 
               reservationStatus == ReservationStatus.ACTIVE;
    }
    
    /**
     * Calculate remaining time before expiry
     */
    public Long getRemainingMinutes() {
        if (expiryTime == null || !isActive()) {
            return 0L;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiryTime)) {
            return 0L;
        }
        
        return java.time.Duration.between(now, expiryTime).toMinutes();
    }
    
    /**
     * Generate a unique confirmation code
     */
    private static String generateConfirmationCode() {
        return "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}