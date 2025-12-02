package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer fraud evaluation request DTO
 * Specialized request for money transfer fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"senderVerificationData", "recipientVerificationData"})
public class TransferFraudRequest {
    
    @NotNull
    private UUID transferId;
    
    @NotNull
    private UUID senderId;
    
    @NotNull
    private UUID recipientId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
    
    @NotNull
    private TransferType transferType;
    
    private TransferMethod transferMethod;
    
    private LocalDateTime initiatedAt;
    
    private String purpose;
    
    // Transfer-specific context
    private boolean isInternational;
    private String corridorRisk; // HIGH, MEDIUM, LOW for specific country pairs
    private BigDecimal exchangeRate;
    private String sourceOfFunds;
    private String relationshipToRecipient;
    
    // Sender details
    private SenderProfile senderProfile;
    
    // Recipient details  
    private RecipientProfile recipientProfile;
    
    // Transfer pattern analysis
    private TransferPattern transferPattern;
    
    // Regulatory context
    private RegulatoryContext regulatoryContext;
    
    // Verification data
    private Map<String, Object> senderVerificationData;
    private Map<String, Object> recipientVerificationData;
    
    // Risk indicators
    private Map<String, Object> riskIndicators;
    
    public enum TransferType {
        P2P,           // Person to person
        REMITTANCE,    // Cross-border remittance
        BUSINESS,      // Business transfer
        SALARY,        // Salary payment
        INVOICE,       // Invoice payment
        DONATION,      // Charitable donation
        INVESTMENT,    // Investment transfer
        OTHER
    }
    
    public enum TransferMethod {
        BANK_TRANSFER,
        DIGITAL_WALLET,
        CASH_PICKUP,
        MOBILE_MONEY,
        CARD_TRANSFER,
        CRYPTO,
        CHECK,
        WIRE,
        ACH,
        INSTANT_TRANSFER
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderProfile {
        private String fullName;
        private LocalDateTime dateOfBirth;
        private String nationality;
        private String residenceCountry;
        private String occupation;
        private String employerName;
        private BigDecimal monthlyIncome;
        private String incomeSource;
        private Integer creditScore;
        private boolean isPoliticallyExposed;
        private LocalDateTime customerSince;
        private Integer totalTransfers;
        private BigDecimal totalVolume;
        private String riskRating;
        private boolean isVerified;
        private LocalDateTime lastVerificationDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientProfile {
        private String fullName;
        private String country;
        private String bankName;
        private String accountType;
        private boolean isFirstTime;
        private Integer receivedTransfers;
        private BigDecimal receivedVolume;
        private LocalDateTime lastReceivedDate;
        private String relationshipToSender;
        private boolean isVerified;
        private boolean isBlacklisted;
        private String riskRating;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferPattern {
        private boolean isUnusualAmount;
        private boolean isUnusualTiming;
        private boolean isUnusualFrequency;
        private boolean isUnusualDestination;
        private boolean isRoundNumber;
        private boolean isJustUnderLimit;
        private boolean isStructured; // Potential structuring to avoid reporting
        private Integer similarTransfersLast30Days;
        private BigDecimal averageTransferAmount;
        private String typicalTransferTiming;
        private String frequencyPattern;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegulatoryContext {
        private boolean requiresReporting; // CTR, SAR etc
        private BigDecimal reportingThreshold;
        private String regulatoryJurisdiction;
        private boolean isHighRiskCountry;
        private boolean sanctionsCheckRequired;
        private boolean amlVerificationRequired;
        private String complianceOfficerReview;
        private Map<String, Object> regulatoryFlags;
    }
    
    // Business logic methods
    public boolean isHighValueTransfer() {
        return amount.compareTo(new BigDecimal("10000")) > 0;
    }
    
    public boolean isPotentiallyStructured() {
        return transferPattern != null && transferPattern.isStructured();
    }
    
    public boolean requiresEnhancedDueDiligence() {
        return isHighValueTransfer() || 
               isInternational || 
               (senderProfile != null && senderProfile.isPoliticallyExposed()) ||
               (regulatoryContext != null && regulatoryContext.isHighRiskCountry());
    }
    
    public boolean isFirstTimeRecipient() {
        return recipientProfile != null && recipientProfile.isFirstTime();
    }
    
    public boolean hasComplianceRisks() {
        return (recipientProfile != null && recipientProfile.isBlacklisted()) ||
               (regulatoryContext != null && regulatoryContext.isHighRiskCountry()) ||
               (senderProfile != null && senderProfile.isPoliticallyExposed());
    }
}