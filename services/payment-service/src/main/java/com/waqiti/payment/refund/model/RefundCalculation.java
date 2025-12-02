package com.waqiti.payment.refund.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Enterprise Refund Calculation Model
 * 
 * Comprehensive financial calculation for refund processing including:
 * - Fee calculations with multiple fee types
 * - Currency conversion calculations
 * - Tax calculations where applicable
 * - Provider-specific fee structures
 * - Compliance and regulatory fee adjustments
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCalculation {
    
    // Base amounts
    private BigDecimal requestedAmount;
    private BigDecimal eligibleAmount;
    private BigDecimal refundableAmount;
    private String currency;
    private String baseCurrency;
    
    // Fee breakdown
    private BigDecimal refundFee;
    private BigDecimal processingFee;
    private BigDecimal providerFee;
    private BigDecimal networkFee;
    private BigDecimal complianceFee;
    private BigDecimal totalFees;
    
    // Net calculations
    private BigDecimal netRefundAmount;
    private BigDecimal merchantDeduction;
    private BigDecimal customerRefund;
    
    // Currency conversion
    private BigDecimal exchangeRate;
    private BigDecimal convertedAmount;
    private String conversionCurrency;
    private Instant exchangeRateTimestamp;
    private String exchangeRateProvider;
    
    // Tax calculations
    private BigDecimal taxAmount;
    private BigDecimal taxRefundAmount;
    private String taxJurisdiction;
    private String taxCalculationMethod;
    
    // Fee calculation details
    private String feeCalculationMethod;
    private String feeStructureType;
    private BigDecimal feePercentage;
    private BigDecimal flatFeeAmount;
    private BigDecimal minimumFee;
    private BigDecimal maximumFee;
    
    // Provider-specific calculations
    private BigDecimal providerRefundPercentage;
    private BigDecimal providerMinimumFee;
    private String providerFeeStructure;
    private Map<String, BigDecimal> providerSpecificFees;
    
    // Time-based calculations
    private Instant calculatedAt;
    private String calculatedBy;
    private Long calculationDurationMillis;
    private String calculationVersion;
    
    // Validation and compliance
    private boolean calculationValid;
    private String validationMessage;
    private boolean complianceApproved;
    private String complianceNotes;
    
    // Metadata
    private Map<String, Object> calculationMetadata;
    private String calculationId;
    private String originalPaymentId;
    private String refundRequestId;
    
    // Enums
    public enum FeeStructureType {
        FLAT_FEE,
        PERCENTAGE,
        TIERED,
        HYBRID,
        CUSTOM,
        REGULATORY_MANDATED
    }
    
    public enum CalculationMethod {
        STANDARD,
        PREMIUM,
        EXPEDITED,
        INTERNATIONAL,
        HIGH_VALUE,
        REGULATORY_COMPLIANT
    }
    
    // Helper methods
    public BigDecimal getTotalFees() {
        if (totalFees != null) {
            return totalFees;
        }
        
        BigDecimal total = BigDecimal.ZERO;
        if (refundFee != null) total = total.add(refundFee);
        if (processingFee != null) total = total.add(processingFee);
        if (providerFee != null) total = total.add(providerFee);
        if (networkFee != null) total = total.add(networkFee);
        if (complianceFee != null) total = total.add(complianceFee);
        
        return total;
    }
    
    public BigDecimal calculateNetRefund() {
        if (requestedAmount == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal net = requestedAmount.subtract(getTotalFees());
        
        // Apply tax adjustments
        if (taxRefundAmount != null) {
            net = net.add(taxRefundAmount);
        }
        
        // Ensure non-negative
        return net.max(BigDecimal.ZERO);
    }
    
    public BigDecimal getEffectiveFeePercentage() {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return getTotalFees()
            .divide(requestedAmount, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    public boolean isHighValueRefund() {
        BigDecimal highValueThreshold = new BigDecimal("5000.00");
        return requestedAmount != null && 
               requestedAmount.compareTo(highValueThreshold) > 0;
    }
    
    public boolean requiresCurrencyConversion() {
        return currency != null && baseCurrency != null && 
               !currency.equals(baseCurrency);
    }
    
    public boolean hasRegulatoryFees() {
        return complianceFee != null && 
               complianceFee.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isFeesWithinLimits() {
        BigDecimal maxFeePercentage = new BigDecimal("10.00"); // 10% max
        return getEffectiveFeePercentage().compareTo(maxFeePercentage) <= 0;
    }
    
    public String getCalculationSummary() {
        return String.format(
            "Refund: %s %s, Fees: %s %s (%.2f%%), Net: %s %s",
            requestedAmount, currency,
            getTotalFees(), currency,
            getEffectiveFeePercentage(),
            calculateNetRefund(), currency
        );
    }
    
    // Static factory methods
    public static RefundCalculation standard(BigDecimal requestedAmount, 
                                           String currency, 
                                           BigDecimal standardFeePercentage) {
        BigDecimal refundFee = requestedAmount
            .multiply(standardFeePercentage)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        
        return RefundCalculation.builder()
            .requestedAmount(requestedAmount)
            .eligibleAmount(requestedAmount)
            .refundableAmount(requestedAmount)
            .currency(currency)
            .refundFee(refundFee)
            .totalFees(refundFee)
            .netRefundAmount(requestedAmount.subtract(refundFee))
            .feeCalculationMethod("STANDARD_PERCENTAGE")
            .feeStructureType("PERCENTAGE")
            .feePercentage(standardFeePercentage)
            .calculatedAt(Instant.now())
            .calculationValid(true)
            .build();
    }
    
    public static RefundCalculation noFee(BigDecimal requestedAmount, String currency) {
        return RefundCalculation.builder()
            .requestedAmount(requestedAmount)
            .eligibleAmount(requestedAmount)
            .refundableAmount(requestedAmount)
            .currency(currency)
            .refundFee(BigDecimal.ZERO)
            .totalFees(BigDecimal.ZERO)
            .netRefundAmount(requestedAmount)
            .feeCalculationMethod("NO_FEE")
            .feeStructureType("FLAT_FEE")
            .calculatedAt(Instant.now())
            .calculationValid(true)
            .build();
    }
    
    public static RefundCalculation flatFee(BigDecimal requestedAmount, 
                                          String currency, 
                                          BigDecimal flatFee) {
        BigDecimal netAmount = requestedAmount.subtract(flatFee).max(BigDecimal.ZERO);
        
        return RefundCalculation.builder()
            .requestedAmount(requestedAmount)
            .eligibleAmount(requestedAmount)
            .refundableAmount(requestedAmount)
            .currency(currency)
            .refundFee(flatFee)
            .flatFeeAmount(flatFee)
            .totalFees(flatFee)
            .netRefundAmount(netAmount)
            .feeCalculationMethod("FLAT_FEE")
            .feeStructureType("FLAT_FEE")
            .calculatedAt(Instant.now())
            .calculationValid(true)
            .build();
    }
    
    public static RefundCalculation failed(String reason) {
        return RefundCalculation.builder()
            .calculationValid(false)
            .validationMessage(reason)
            .calculatedAt(Instant.now())
            .build();
    }
    
    // Builder customization
    public static class RefundCalculationBuilder {
        
        public RefundCalculationBuilder withFeeBreakdown(BigDecimal refundFee, 
                                                       BigDecimal processingFee, 
                                                       BigDecimal providerFee) {
            this.refundFee = refundFee != null ? refundFee : BigDecimal.ZERO;
            this.processingFee = processingFee != null ? processingFee : BigDecimal.ZERO;
            this.providerFee = providerFee != null ? providerFee : BigDecimal.ZERO;
            
            this.totalFees = this.refundFee.add(this.processingFee).add(this.providerFee);
            
            if (this.requestedAmount != null) {
                this.netRefundAmount = this.requestedAmount.subtract(this.totalFees);
            }
            
            return this;
        }
        
        public RefundCalculationBuilder withCurrencyConversion(BigDecimal exchangeRate, 
                                                             String targetCurrency) {
            this.exchangeRate = exchangeRate;
            this.conversionCurrency = targetCurrency;
            this.exchangeRateTimestamp = Instant.now();
            
            if (this.requestedAmount != null && exchangeRate != null) {
                this.convertedAmount = this.requestedAmount.multiply(exchangeRate);
            }
            
            return this;
        }
        
        public RefundCalculationBuilder withTax(BigDecimal taxAmount, 
                                              BigDecimal taxRefundAmount, 
                                              String jurisdiction) {
            this.taxAmount = taxAmount;
            this.taxRefundAmount = taxRefundAmount;
            this.taxJurisdiction = jurisdiction;
            return this;
        }
        
        public RefundCalculationBuilder withProviderSpecifics(String providerFeeStructure, 
                                                            BigDecimal providerPercentage, 
                                                            Map<String, BigDecimal> specificFees) {
            this.providerFeeStructure = providerFeeStructure;
            this.providerRefundPercentage = providerPercentage;
            this.providerSpecificFees = specificFees;
            return this;
        }
        
        public RefundCalculationBuilder withValidation(boolean valid, String message) {
            this.calculationValid = valid;
            this.validationMessage = message;
            return this;
        }
    }
}