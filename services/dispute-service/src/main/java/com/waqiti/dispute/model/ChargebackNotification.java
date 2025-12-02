package com.waqiti.dispute.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Chargeback notification from payment provider
 */
@Data
@Builder
public class ChargebackNotification {
    
    private String transactionId;
    private String providerCaseId;
    private String provider; // Stripe, PayPal, etc.
    private BigDecimal amount;
    private String currency;
    private String chargebackCode;
    private String reason;
    private ChargebackStatus status;
    private LocalDateTime receivedAt;
    private LocalDateTime dueDate;
    
    // Card network specific
    private String cardNetwork; // Visa, Mastercard, etc.
    private String issuerBank;
    private String lastFourDigits;
    
    // Documentation
    private String documentationUrl;
    private Map<String, String> providerMetadata;
    
    // Liability
    private boolean liabilityShift;
    private String liabilityReason;
    
    public enum ChargebackStatus {
        NEEDS_RESPONSE,
        UNDER_REVIEW,
        CHARGE_REFUNDED,
        WON,
        LOST,
        WARNING_NEEDS_RESPONSE,
        WARNING_UNDER_REVIEW,
        WARNING_CLOSED
    }
    
    /**
     * Check if urgent response needed
     */
    public boolean isUrgent() {
        if (dueDate == null) {
            return true; // No deadline means urgent
        }
        
        long hoursUntilDue = java.time.Duration.between(LocalDateTime.now(), dueDate).toHours();
        return hoursUntilDue <= 24;
    }
    
    /**
     * Check if response required
     */
    public boolean requiresResponse() {
        return status == ChargebackStatus.NEEDS_RESPONSE || 
               status == ChargebackStatus.WARNING_NEEDS_RESPONSE;
    }
    
    /**
     * Get response deadline in hours
     */
    public long getResponseDeadlineHours() {
        if (dueDate == null) {
            return 0;
        }
        
        long hours = java.time.Duration.between(LocalDateTime.now(), dueDate).toHours();
        return Math.max(0, hours);
    }
}