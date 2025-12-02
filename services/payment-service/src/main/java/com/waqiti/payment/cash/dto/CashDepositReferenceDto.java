package com.waqiti.payment.cash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cash deposit reference response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashDepositReferenceDto {

    private String depositId;
    private String referenceNumber;
    private String displayReferenceNumber;
    private BigDecimal amount;
    private String currency;
    private CashDepositNetwork network;
    private CashDepositStatus status;
    
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    // Instructions and codes
    private String qrCodeData;
    private String qrCodeImageUrl;
    private String barcodeData;
    private String barcodeImageUrl;
    
    // Network-specific data
    private String networkConfirmationCode;
    private String networkTransactionId;
    
    // Fee information
    private BigDecimal fee;
    private BigDecimal netAmount;
    private String feeDescription;
    
    // Instructions
    private List<String> instructions;
    private String customerServicePhone;
    private String supportEmail;
    
    // Nearby locations
    private List<CashDepositLocationDto> nearbyLocations;
    
    // Tracking
    private String trackingUrl;
    
    // Notifications sent
    private boolean smsSent;
    private boolean emailSent;
    private String notificationStatus;
}