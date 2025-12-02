package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for BNPL fraud analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplFraudAnalysisRequest {
    
    // Application identifiers
    private UUID applicationId;
    private UUID userId;
    private String merchantId;
    private String merchantName;
    private String orderId;
    
    // Financial details
    private BigDecimal purchaseAmount;
    private BigDecimal downPayment;
    private BigDecimal financedAmount;
    private String currency;
    private Integer requestedInstallments;
    
    // Credit information
    private Integer creditScore;
    private Boolean thinCreditFile;
    private Double creditUtilization;
    private String creditTier;
    
    // Device and session information
    private String ipAddress;
    private String deviceFingerprint;
    private String userAgent;
    private String sessionId;
    private String geolocation;
    
    // Application context
    private String applicationSource; // MOBILE_APP, WEB, PARTNER_API
    private String referrer;
    private Long sessionDuration;
    
    // Risk context
    private Boolean isFirstTimeUser;
    private Boolean hasActiveLoans;
    private Integer previousBnplApplications;
    private BigDecimal totalBnplExposure;
    
    // Merchant context
    private String merchantCategory;
    private Boolean isNewMerchant;
    private Double merchantRiskScore;
}