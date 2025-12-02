package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction model for AML/CTF rule evaluation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLTransaction {
    
    // Transaction identifiers
    @NotBlank(message = "Transaction ID is required")
    @Size(max = 50, message = "Transaction ID must be 50 characters or less")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Transaction ID contains invalid characters")
    private String transactionId;
    
    @Size(max = 50, message = "Account ID must be 50 characters or less")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Account ID contains invalid characters")
    private String accountId;
    
    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID must be 50 characters or less")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Customer ID contains invalid characters")
    private String customerId;
    
    @Size(max = 50, message = "Counterparty ID must be 50 characters or less")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Counterparty ID contains invalid characters")
    private String counterpartyId;
    
    @Size(max = 50, message = "Counterparty Account ID must be 50 characters or less")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Counterparty Account ID contains invalid characters")
    private String counterpartyAccountId;
    
    // Transaction details
    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    @Digits(integer = 9, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
    private String currency;
    
    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "^(DEPOSIT|WITHDRAWAL|TRANSFER|PAYMENT|EXCHANGE|OTHER)$", 
             message = "Invalid transaction type")
    private String type; // DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT, etc.
    
    @Pattern(regexp = "^(ONLINE|ATM|BRANCH|MOBILE|API|OTHER)$", 
             message = "Invalid channel type")
    private String channel; // ONLINE, ATM, BRANCH, MOBILE, etc.
    
    @NotNull(message = "Transaction timestamp is required")
    @PastOrPresent(message = "Transaction timestamp cannot be in the future")
    private LocalDateTime timestamp;
    
    // Geographic information
    private String originCountry;
    private String destinationCountry;
    private String ipAddress;
    private String deviceId;
    private Double latitude;
    private Double longitude;
    
    // Transaction patterns
    private Integer dailyTransactionCount;
    private BigDecimal dailyTransactionVolume;
    private Integer monthlyTransactionCount;
    private BigDecimal monthlyTransactionVolume;
    private BigDecimal averageTransactionAmount;
    private BigDecimal largestPreviousTransaction;
    
    // Customer information
    private String customerType; // INDIVIDUAL, BUSINESS
    private String customerRiskRating; // LOW, MEDIUM, HIGH, VERY_HIGH
    private Integer accountAgeInDays;
    private Boolean isPEP; // Politically Exposed Person
    private Boolean isHighRiskJurisdiction;
    private String occupation;
    private BigDecimal declaredIncome;
    
    // Counterparty information  
    private String counterpartyType;
    private String counterpartyCountry;
    private Boolean counterpartyIsHighRisk;
    private Boolean counterpartyIsSanctioned;
    
    // Transaction metadata
    private String description;
    private String reference;
    private String purposeCode;
    private Boolean isInternational;
    private Boolean isCashTransaction;
    private Boolean isStructured; // Multiple transactions to avoid reporting
    
    // Previous screening results
    private Integer previousSARCount; // Suspicious Activity Reports
    private LocalDateTime lastSARDate;
    private Integer previousAlertCount;
    private LocalDateTime lastAlertDate;
    
    // Rule evaluation results (populated by rules)
    @Builder.Default
    private List<RiskIndicator> riskIndicators = new ArrayList<>();
    
    @Builder.Default
    private List<ComplianceAlert> alerts = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> ruleOutputs = new HashMap<>();
    
    private RiskScore riskScore;
    private Boolean requiresReview;
    private Boolean requiresSAR;
    private Boolean shouldBlock;
    
    // Convenience methods for rule evaluation
    public void addRiskIndicator(String code, String description, Integer score) {
        riskIndicators.add(new RiskIndicator(code, description, score));
    }
    
    public void addAlert(String type, String severity, String message) {
        alerts.add(new ComplianceAlert(type, severity, message, LocalDateTime.now()));
    }
    
    public Integer getTotalRiskScore() {
        return riskIndicators.stream()
            .mapToInt(RiskIndicator::getScore)
            .sum();
    }
    
    public boolean isHighValueTransaction(BigDecimal threshold) {
        return amount != null && amount.compareTo(threshold) >= 0;
    }
    
    public boolean isRapidMovement(Integer minutes) {
        // Check if this is a rapid movement of funds
        // (would need transaction history to implement fully)
        return false; // Placeholder
    }
    
    public boolean isRoundAmount() {
        if (amount == null) return false;
        BigDecimal remainder = amount.remainder(new BigDecimal("1000"));
        return remainder.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isNearThreshold(BigDecimal threshold, BigDecimal tolerance) {
        if (amount == null || threshold == null) return false;
        BigDecimal difference = threshold.subtract(amount).abs();
        return difference.compareTo(tolerance) <= 0;
    }
    
    public boolean isUnusualForCustomer() {
        if (amount == null || averageTransactionAmount == null) return false;
        BigDecimal ratio = amount.divide(averageTransactionAmount, 2, RoundingMode.HALF_UP);
        return ratio.compareTo(new BigDecimal("3")) > 0; // 3x average
    }
    
    /**
     * Risk Indicator for specific risk factors identified
     */
    @Data
    @AllArgsConstructor
    public static class RiskIndicator {
        private String code;
        private String description;
        private Integer score;
    }
    
    /**
     * Compliance alert generated by rules
     */
    @Data
    @AllArgsConstructor
    public static class ComplianceAlert {
        private String type;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private String message;
        private LocalDateTime timestamp;
    }
    
    /**
     * Overall risk score for the transaction
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScore {
        private Integer totalScore;
        private String riskLevel; // LOW, MEDIUM, HIGH, VERY_HIGH
        private String recommendation; // APPROVE, REVIEW, ESCALATE, BLOCK
        private List<String> reasons;
    }
}