package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardDesign;
import com.waqiti.virtualcard.domain.CardPersonalization;
import com.waqiti.virtualcard.domain.enums.CardBrand;
import com.waqiti.virtualcard.domain.enums.CardStatus;
import com.waqiti.virtualcard.domain.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for physical card information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalCardDto {
    
    private String id;
    private String orderId;
    private CardType type;
    private CardBrand brand;
    private CardStatus status;
    
    private CardDesign design;
    private CardPersonalization personalization;
    
    private String currency;
    private BigDecimal balance;
    
    // Masked card details (for security)
    private String lastFourDigits;
    private String maskedCardNumber; // e.g., "**** **** **** 1234"
    private Integer expiryMonth;
    private Integer expiryYear;
    
    // Status timestamps
    private Instant orderedAt;
    private Instant deliveredAt;
    private Instant activatedAt;
    private Instant estimatedDelivery;
    
    // Flags
    private boolean isReplacement;
    private boolean pinSet;
    
    // Computed fields
    private String statusDescription;
    private String formattedBalance;
    private String expiryDate;
    private boolean canActivate;
    private boolean isExpired;
    private boolean needsActivation;
    private int daysUntilExpiry;
    
    public String getStatusDescription() {
        return status != null ? status.getDescription() : null;
    }
    
    public String getFormattedBalance() {
        if (balance == null) {
            return currency + " 0.00";
        }
        return currency + " " + balance.toString();
    }
    
    public String getExpiryDate() {
        if (expiryMonth == null || expiryYear == null) {
            return null;
        }
        return String.format("%02d/%02d", expiryMonth, expiryYear % 100);
    }
    
    public String getMaskedCardNumber() {
        if (lastFourDigits == null) {
            return "**** **** **** ****";
        }
        return "**** **** **** " + lastFourDigits;
    }
    
    public boolean canActivate() {
        return status == CardStatus.DELIVERED && activatedAt == null;
    }
    
    public boolean isExpired() {
        if (expiryMonth == null || expiryYear == null) {
            return false;
        }
        
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate expiryDate = java.time.LocalDate.of(expiryYear, expiryMonth, 1)
            .plusMonths(1).minusDays(1);
        
        return now.isAfter(expiryDate);
    }
    
    public boolean needsActivation() {
        return status == CardStatus.DELIVERED && 
               activatedAt == null && 
               deliveredAt != null &&
               java.time.Duration.between(deliveredAt, Instant.now()).toDays() >= 1;
    }
    
    public int getDaysUntilExpiry() {
        if (expiryMonth == null || expiryYear == null) {
            return -1;
        }
        
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate expiryDate = java.time.LocalDate.of(expiryYear, expiryMonth, 1)
            .plusMonths(1).minusDays(1);
        
        return (int) java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);
    }
    
    public boolean isActive() {
        return status == CardStatus.ACTIVE && !isExpired();
    }
    
    public boolean canReplace() {
        return status == CardStatus.ACTIVE || 
               status == CardStatus.BLOCKED || 
               status == CardStatus.DAMAGED;
    }
}