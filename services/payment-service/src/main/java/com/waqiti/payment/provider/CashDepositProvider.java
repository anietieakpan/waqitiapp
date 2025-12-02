package com.waqiti.payment.provider;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for cash deposit providers
 */
public interface CashDepositProvider {
    
    String getProviderName();
    
    boolean isAvailable();
    
    BigDecimal getMinimumAmount();
    
    BigDecimal getMaximumAmount();
    
    BigDecimal calculateFee(BigDecimal amount);
    
    List<CashDepositLocation> getNearbyLocations(double latitude, double longitude, int radiusMiles);
    
    CashDepositReference generateReference(String userId, BigDecimal amount);
    
    boolean validateReference(String reference);
    
    // DTOs
    
    record CashDepositLocation(
        String locationId,
        String name,
        String address,
        String city,
        String state,
        String zipCode,
        double latitude,
        double longitude,
        String type,
        List<String> services,
        String hours
    ) {}
    
    record CashDepositReference(
        String reference,
        String barcode,
        String qrCode,
        BigDecimal amount,
        BigDecimal fee,
        java.time.LocalDateTime expiresAt
    ) {}
}