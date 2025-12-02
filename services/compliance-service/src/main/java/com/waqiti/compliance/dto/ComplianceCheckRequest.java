package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckRequest {

    @NotNull
    private String transactionId;

    @NotNull
    private String customerId;

    private String customerName;

    @NotNull
    private String transactionType;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String currency;

    private String fromAccount;

    private String toAccount;

    private String counterpartyId;

    private String counterpartyName;

    private String counterpartyCountry;

    private String sourceCountry;

    private String destinationCountry;

    private String paymentMethod;

    private String purpose;

    private String description;

    @NotNull
    private LocalDateTime transactionDate;

    private String channel;

    private String ipAddress;

    private String deviceFingerprint;

    private String userAgent;

    private String sessionId;

    private Boolean isRecurring;

    private Boolean isHighValue;

    private Boolean isInternational;

    private Boolean isCrossBorder;

    private Map<String, Object> additionalData;

    // Customer context
    private String customerType;

    private String riskLevel;

    private String occupation;

    private String industry;

    private String businessNature;

    private String citizenshipCountry;

    private String residenceCountry;

    private Boolean isPEP;

    private Boolean isSanctioned;

    private Boolean hasAdverseMedia;

    // Transaction context
    private BigDecimal dailyVolume;

    private Integer dailyTransactionCount;

    private BigDecimal weeklyVolume;

    private Integer weeklyTransactionCount;

    private BigDecimal monthlyVolume;

    private Integer monthlyTransactionCount;

    private BigDecimal yearlyVolume;

    private Integer yearlyTransactionCount;

    private BigDecimal averageTransactionAmount;

    private BigDecimal largestTransactionAmount;

    private Integer unusualActivityCount;

    private LocalDateTime lastTransactionDate;

    private String lastTransactionType;

    private BigDecimal lastTransactionAmount;

    // Compliance flags
    private Boolean bypassScreening;

    private Boolean expeditedProcessing;

    private Boolean manualReview;

    private String complianceNotes;

    private String requestedBy;

    private String requestSource;

    private LocalDateTime requestTimestamp;

    public boolean isHighValueTransaction() {
        return amount.compareTo(new BigDecimal("10000.00")) >= 0;
    }

    public boolean isSuspiciousAmount() {
        return amount.compareTo(new BigDecimal("5000.00")) >= 0;
    }

    public boolean isRoundAmount() {
        return amount.remainder(new BigDecimal("1000.00")).compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isInternationalTransfer() {
        return isInternational != null && isInternational;
    }

    public boolean isCrossBorderTransfer() {
        return isCrossBorder != null && isCrossBorder;
    }

    public boolean isHighRiskCustomer() {
        return "HIGH".equals(riskLevel) || "VERY_HIGH".equals(riskLevel);
    }

    public boolean isPEPCustomer() {
        return isPEP != null && isPEP;
    }

    public boolean isSanctionedCustomer() {
        return isSanctioned != null && isSanctioned;
    }

    public boolean hasAdverseMediaCustomer() {
        return hasAdverseMedia != null && hasAdverseMedia;
    }
}