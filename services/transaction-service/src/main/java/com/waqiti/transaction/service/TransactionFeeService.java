package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.client.PricingServiceClient;
import com.waqiti.transaction.client.AccountServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-grade Transaction Fee Calculation Service
 * 
 * Handles comprehensive fee calculation for all transaction types:
 * - Dynamic fee structures based on user tiers and volumes
 * - Multi-currency fee calculations with forex considerations
 * - Time-based fee adjustments (peak/off-peak pricing)
 * - Partner and merchant-specific fee arrangements
 * - Regulatory fee compliance (interchange, network fees)
 * - Fee optimization and transparency features
 * - Real-time fee previews and explanations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionFeeService {

    private final PricingServiceClient pricingClient;
    private final AccountServiceClient accountClient;
    private final CurrencyConversionService currencyService;

    @Value("${transaction.fees.enabled:true}")
    private boolean feesEnabled;

    @Value("${transaction.fees.minimum.processing:0.50}")
    private BigDecimal minimumProcessingFee;

    @Value("${transaction.fees.maximum.percentage:0.05}")
    private BigDecimal maximumFeePercentage;

    @Value("${transaction.fees.free.tier.enabled:true}")
    private boolean freeTierEnabled;

    /**
     * Calculate comprehensive transaction fees with detailed breakdown
     */
    public TransactionFeeCalculation calculateTransactionFees(TransactionFeeRequest request) {
        log.info("Calculating fees for transaction type: {} amount: {} {}", 
                request.getTransactionType(), request.getAmount(), request.getCurrency());

        try {
            // Initialize fee calculation context
            FeeCalculationContext context = buildFeeCalculationContext(request);
            
            // Get user fee tier and account information
            UserFeeProfile userProfile = getUserFeeProfile(request.getUserId());
            
            // Calculate base fees
            FeeBreakdown feeBreakdown = calculateBaseFees(request, context, userProfile);
            
            // Apply fee adjustments and optimizations
            applyFeeAdjustments(feeBreakdown, context, userProfile);
            
            // Apply regulatory and network fees
            applyRegulatoryFees(feeBreakdown, request, context);
            
            // Calculate final fee amounts
            BigDecimal totalFee = calculateTotalFee(feeBreakdown);
            
            // Apply fee caps and minimums
            totalFee = applyFeeLimits(totalFee, request, userProfile);
            
            // Create comprehensive result
            TransactionFeeCalculation result = TransactionFeeCalculation.builder()
                    .transactionAmount(request.getAmount())
                    .currency(request.getCurrency())
                    .totalFee(totalFee)
                    .netAmount(request.getAmount().subtract(totalFee))
                    .feeBreakdown(feeBreakdown)
                    .userTier(userProfile.getTier())
                    .feeExplanation(generateFeeExplanation(feeBreakdown, userProfile))
                    .appliedDiscounts(feeBreakdown.getDiscounts())
                    .timestamp(LocalDateTime.now())
                    .metadata(buildFeeMetadata(request, context, userProfile))
                    .build();

            // Audit fee calculation
            auditFeeCalculation(request, result);

            log.info("Fee calculation completed: Total fee {} {} for {} {} transaction", 
                    totalFee, request.getCurrency(), request.getAmount(), request.getCurrency());

            return result;

        } catch (Exception e) {
            log.error("Fee calculation failed for transaction: {}", request, e);
            throw new FeeCalculationException("Fee calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get fee preview without storing calculation
     */
    public FeePreview getFeePreview(TransactionFeeRequest request) {
        TransactionFeeCalculation calculation = calculateTransactionFees(request);
        
        return FeePreview.builder()
                .estimatedFee(calculation.getTotalFee())
                .netAmount(calculation.getNetAmount())
                .feeComponents(extractFeeComponents(calculation.getFeeBreakdown()))
                .userTier(calculation.getUserTier())
                .savingsMessage(generateSavingsMessage(calculation))
                .validUntil(LocalDateTime.now().plusMinutes(15))
                .build();
    }

    /**
     * Calculate fees for multiple transaction scenarios (for comparison)
     */
    public List<FeeComparison> compareFeeScenarios(List<TransactionFeeRequest> scenarios) {
        return scenarios.stream()
                .map(this::calculateTransactionFees)
                .map(calc -> FeeComparison.builder()
                        .scenario(calc.getMetadata().get("scenario").toString())
                        .totalFee(calc.getTotalFee())
                        .savings(calculatePotentialSavings(calc))
                        .recommendation(generateRecommendation(calc))
                        .build())
                .toList();
    }

    /**
     * Get fee structure for user transparency
     */
    @Cacheable(value = "user-fee-structure", key = "#userId")
    public UserFeeStructure getUserFeeStructure(String userId) {
        UserFeeProfile profile = getUserFeeProfile(userId);
        
        return UserFeeStructure.builder()
                .userId(userId)
                .tier(profile.getTier())
                .tierBenefits(profile.getBenefits())
                .feeRates(buildFeeRatesForUser(profile))
                .monthlyFreeTransactions(profile.getMonthlyFreeTransactions())
                .volumeDiscounts(profile.getVolumeDiscounts())
                .nextTierRequirements(calculateNextTierRequirements(profile))
                .build();
    }

    /**
     * Calculate fee refund for failed/cancelled transactions
     */
    public FeeRefundCalculation calculateFeeRefund(String transactionId, String reason) {
        try {
            // Get original transaction and fee calculation
            TransactionFeeCalculation originalCalculation = getOriginalFeeCalculation(transactionId);
            
            if (originalCalculation == null) {
                return FeeRefundCalculation.noRefund("Original fee calculation not found");
            }

            // Determine refund eligibility based on reason and timing
            RefundEligibility eligibility = determineRefundEligibility(originalCalculation, reason);
            
            if (!eligibility.isEligible()) {
                return FeeRefundCalculation.noRefund(eligibility.getReason());
            }

            // Calculate refund amounts
            BigDecimal processingFeeRefund = calculateProcessingFeeRefund(originalCalculation, eligibility);
            BigDecimal networkFeeRefund = calculateNetworkFeeRefund(originalCalculation, eligibility);
            BigDecimal totalRefund = processingFeeRefund.add(networkFeeRefund);

            return FeeRefundCalculation.builder()
                    .originalFee(originalCalculation.getTotalFee())
                    .refundAmount(totalRefund)
                    .processingFeeRefund(processingFeeRefund)
                    .networkFeeRefund(networkFeeRefund)
                    .reason(reason)
                    .refundMethod("ORIGINAL_PAYMENT_METHOD")
                    .estimatedRefundTime("1-3 business days")
                    .build();

        } catch (Exception e) {
            log.error("Fee refund calculation failed for transaction: {}", transactionId, e);
            return FeeRefundCalculation.error("Refund calculation failed: " + e.getMessage());
        }
    }

    /**
     * Build fee calculation context with all relevant data
     */
    private FeeCalculationContext buildFeeCalculationContext(TransactionFeeRequest request) {
        FeeCalculationContext.FeeCalculationContextBuilder contextBuilder = FeeCalculationContext.builder()
                .transactionType(request.getTransactionType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .sourceCountry(request.getSourceCountry())
                .destinationCountry(request.getDestinationCountry())
                .timestamp(LocalDateTime.now())
                .isWeekend(isWeekend())
                .isPeakHour(isPeakHour())
                .isInternational(isInternationalTransaction(request));

        // Add payment method context if available
        if (request.getPaymentMethodId() != null) {
            PaymentMethodInfo paymentMethod = getPaymentMethodInfo(request.getPaymentMethodId());
            contextBuilder.paymentMethodType(paymentMethod.getType())
                         .paymentNetworkCost(paymentMethod.getNetworkCost());
        }

        return contextBuilder.build();
    }

    /**
     * Get user fee profile with tier information and benefits
     */
    private UserFeeProfile getUserFeeProfile(String userId) {
        try {
            return accountClient.getUserFeeProfile(userId);
        } catch (Exception e) {
            log.warn("Failed to get user fee profile for {}, using default", userId, e);
            return createDefaultFeeProfile(userId);
        }
    }

    /**
     * Calculate base fees before adjustments
     */
    private FeeBreakdown calculateBaseFees(TransactionFeeRequest request, FeeCalculationContext context, UserFeeProfile userProfile) {
        FeeBreakdown breakdown = new FeeBreakdown();

        // Processing fee (base fee for handling the transaction)
        BigDecimal processingFee = calculateProcessingFee(request, context, userProfile);
        breakdown.setProcessingFee(processingFee);

        // Network fees (for card transactions, bank transfers, etc.)
        BigDecimal networkFee = calculateNetworkFee(request, context);
        breakdown.setNetworkFee(networkFee);

        // Currency conversion fees (if applicable)
        if (requiresCurrencyConversion(request)) {
            BigDecimal conversionFee = calculateCurrencyConversionFee(request, context);
            breakdown.setConversionFee(conversionFee);
        }

        // International transfer fees
        if (context.isInternational()) {
            BigDecimal internationalFee = calculateInternationalFee(request, context);
            breakdown.setInternationalFee(internationalFee);
        }

        // Express/priority processing fees
        if (request.isPriorityProcessing()) {
            BigDecimal priorityFee = calculatePriorityFee(request, context);
            breakdown.setPriorityFee(priorityFee);
        }

        return breakdown;
    }

    /**
     * Apply various fee adjustments and optimizations
     */
    private void applyFeeAdjustments(FeeBreakdown breakdown, FeeCalculationContext context, UserFeeProfile userProfile) {
        // Volume-based discounts
        BigDecimal volumeDiscount = calculateVolumeDiscount(breakdown.getTotalBeforeAdjustments(), userProfile);
        breakdown.addDiscount("VOLUME_DISCOUNT", volumeDiscount);

        // Tier-based discounts
        BigDecimal tierDiscount = calculateTierDiscount(breakdown.getTotalBeforeAdjustments(), userProfile);
        breakdown.addDiscount("TIER_DISCOUNT", tierDiscount);

        // Peak hour adjustments
        if (context.isPeakHour()) {
            BigDecimal peakSurcharge = breakdown.getTotalBeforeAdjustments().multiply(new BigDecimal("0.001")); // 0.1%
            breakdown.addSurcharge("PEAK_HOUR", peakSurcharge);
        }

        // Weekend discounts (to encourage off-peak usage)
        if (context.isWeekend()) {
            BigDecimal weekendDiscount = breakdown.getTotalBeforeAdjustments().multiply(new BigDecimal("0.0025")); // 0.25%
            breakdown.addDiscount("WEEKEND_DISCOUNT", weekendDiscount);
        }

        // Loyalty program benefits
        if (userProfile.hasLoyaltyBenefits()) {
            BigDecimal loyaltyDiscount = calculateLoyaltyDiscount(breakdown.getTotalBeforeAdjustments(), userProfile);
            breakdown.addDiscount("LOYALTY_DISCOUNT", loyaltyDiscount);
        }
    }

    /**
     * Apply regulatory and compliance-related fees
     */
    private void applyRegulatoryFees(FeeBreakdown breakdown, TransactionFeeRequest request, FeeCalculationContext context) {
        // Interchange fees for card transactions
        if (isCardTransaction(request)) {
            BigDecimal interchangeFee = calculateInterchangeFee(request, context);
            breakdown.setInterchangeFee(interchangeFee);
        }

        // Regulatory fees (varies by jurisdiction)
        BigDecimal regulatoryFee = calculateRegulatoryFee(request, context);
        if (regulatoryFee.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.setRegulatoryFee(regulatoryFee);
        }

        // Anti-money laundering (AML) fees for high-value transactions
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            BigDecimal amlFee = new BigDecimal("2.50"); // Fixed AML compliance fee
            breakdown.setComplianceFee(amlFee);
        }
    }

    /**
     * Calculate processing fee based on transaction type and amount
     */
    private BigDecimal calculateProcessingFee(TransactionFeeRequest request, FeeCalculationContext context, UserFeeProfile userProfile) {
        Map<String, BigDecimal> processingRates = Map.of(
            "P2P_TRANSFER", userProfile.getP2pProcessingRate(),
            "MERCHANT_PAYMENT", userProfile.getMerchantPaymentRate(),
            "INTERNATIONAL_TRANSFER", userProfile.getInternationalTransferRate(),
            "CRYPTO_TRANSFER", userProfile.getCryptoTransferRate(),
            "DEPOSIT", userProfile.getDepositRate(),
            "WITHDRAWAL", userProfile.getWithdrawalRate()
        );

        BigDecimal rate = processingRates.getOrDefault(request.getTransactionType(), new BigDecimal("0.01"));
        BigDecimal percentageFee = request.getAmount().multiply(rate);
        
        // Apply minimum fee
        return percentageFee.max(minimumProcessingFee);
    }

    /**
     * Calculate network-specific fees
     */
    private BigDecimal calculateNetworkFee(TransactionFeeRequest request, FeeCalculationContext context) {
        if (context.getPaymentNetworkCost() != null) {
            return context.getPaymentNetworkCost();
        }

        // Default network fees by payment method type
        Map<String, BigDecimal> networkFees = Map.of(
            "CREDIT_CARD", new BigDecimal("0.30"),
            "DEBIT_CARD", new BigDecimal("0.25"),
            "ACH", new BigDecimal("0.50"),
            "WIRE", new BigDecimal("15.00"),
            "CRYPTO", new BigDecimal("2.00")
        );

        return networkFees.getOrDefault(context.getPaymentMethodType(), BigDecimal.ZERO);
    }

    /**
     * Calculate currency conversion fees
     */
    private BigDecimal calculateCurrencyConversionFee(TransactionFeeRequest request, FeeCalculationContext context) {
        // Use the currency conversion service to get accurate fees
        CurrencyConversionRequest conversionRequest = CurrencyConversionRequest.builder()
                .amount(request.getAmount())
                .fromCurrency(request.getCurrency())
                .toCurrency(request.getTargetCurrency())
                .transactionType(request.getTransactionType())
                .userId(request.getUserId())
                .build();

        try {
            CurrencyConversionResult conversion = currencyService.convertAmount(conversionRequest);
            return conversion.getConversionFee();
        } catch (Exception e) {
            log.warn("Failed to get accurate conversion fee, using estimate", e);
            return request.getAmount().multiply(new BigDecimal("0.002")); // 0.2% estimate
        }
    }

    /**
     * Calculate fees for international transfers
     */
    private BigDecimal calculateInternationalFee(TransactionFeeRequest request, FeeCalculationContext context) {
        // International fees vary by corridor and amount
        BigDecimal baseFee = new BigDecimal("5.00");
        BigDecimal percentageFee = request.getAmount().multiply(new BigDecimal("0.005")); // 0.5%
        
        return baseFee.add(percentageFee);
    }

    /**
     * Calculate priority processing fees
     */
    private BigDecimal calculatePriorityFee(TransactionFeeRequest request, FeeCalculationContext context) {
        return request.getAmount().multiply(new BigDecimal("0.002")); // 0.2% for priority processing
    }

    // Helper methods for fee calculations
    private BigDecimal calculateTotalFee(FeeBreakdown breakdown) {
        return breakdown.getProcessingFee()
                .add(breakdown.getNetworkFee())
                .add(breakdown.getConversionFee())
                .add(breakdown.getInternationalFee())
                .add(breakdown.getPriorityFee())
                .add(breakdown.getInterchangeFee())
                .add(breakdown.getRegulatoryFee())
                .add(breakdown.getComplianceFee())
                .add(breakdown.getTotalSurcharges())
                .subtract(breakdown.getTotalDiscounts());
    }

    private BigDecimal applyFeeLimits(BigDecimal totalFee, TransactionFeeRequest request, UserFeeProfile userProfile) {
        // Apply maximum fee percentage cap
        BigDecimal maxAllowedFee = request.getAmount().multiply(maximumFeePercentage);
        totalFee = totalFee.min(maxAllowedFee);

        // Apply user-specific fee caps
        BigDecimal userMaxFee = userProfile.getMaxFeePerTransaction();
        if (userMaxFee != null) {
            totalFee = totalFee.min(userMaxFee);
        }

        // Apply minimum fee
        if (totalFee.compareTo(minimumProcessingFee) < 0) {
            totalFee = minimumProcessingFee;
        }

        return totalFee;
    }

    // Additional helper methods
    private boolean requiresCurrencyConversion(TransactionFeeRequest request) {
        return request.getTargetCurrency() != null && !request.getCurrency().equals(request.getTargetCurrency());
    }

    private boolean isCardTransaction(TransactionFeeRequest request) {
        return request.getPaymentMethodType() != null && 
               (request.getPaymentMethodType().contains("CARD") || 
                request.getPaymentMethodType().contains("CREDIT") || 
                request.getPaymentMethodType().contains("DEBIT"));
    }

    private boolean isInternationalTransaction(TransactionFeeRequest request) {
        return request.getDestinationCountry() != null && 
               !request.getSourceCountry().equals(request.getDestinationCountry());
    }

    private boolean isWeekend() {
        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue();
        return dayOfWeek == 6 || dayOfWeek == 7; // Saturday or Sunday
    }

    private boolean isPeakHour() {
        int hour = LocalDateTime.now().getHour();
        return hour >= 9 && hour <= 17; // 9 AM to 5 PM
    }

    private UserFeeProfile createDefaultFeeProfile(String userId) {
        return UserFeeProfile.builder()
                .userId(userId)
                .tier("BASIC")
                .p2pProcessingRate(new BigDecimal("0.01"))
                .merchantPaymentRate(new BigDecimal("0.025"))
                .internationalTransferRate(new BigDecimal("0.005"))
                .cryptoTransferRate(new BigDecimal("0.0075"))
                .depositRate(new BigDecimal("0.005"))
                .withdrawalRate(new BigDecimal("0.01"))
                .monthlyFreeTransactions(5)
                .maxFeePerTransaction(new BigDecimal("50.00"))
                .build();
    }

    // Audit and logging methods
    private void auditFeeCalculation(TransactionFeeRequest request, TransactionFeeCalculation result) {
        log.info("AUDIT: Fee calculation - User: {}, Type: {}, Amount: {} {}, Fee: {} {}, Tier: {}", 
                request.getUserId(), 
                request.getTransactionType(),
                request.getAmount(), request.getCurrency(),
                result.getTotalFee(), result.getCurrency(),
                result.getUserTier());
    }

    // Utility methods for building responses
    private String generateFeeExplanation(FeeBreakdown breakdown, UserFeeProfile profile) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("Fee calculation for ").append(profile.getTier()).append(" tier user: ");
        
        if (breakdown.getProcessingFee().compareTo(BigDecimal.ZERO) > 0) {
            explanation.append("Processing fee: ").append(breakdown.getProcessingFee()).append(", ");
        }
        
        if (breakdown.getTotalDiscounts().compareTo(BigDecimal.ZERO) > 0) {
            explanation.append("Discounts applied: ").append(breakdown.getTotalDiscounts());
        }
        
        return explanation.toString();
    }

    private Map<String, Object> buildFeeMetadata(TransactionFeeRequest request, FeeCalculationContext context, UserFeeProfile profile) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("calculationVersion", "2.1.0");
        metadata.put("userTier", profile.getTier());
        metadata.put("isPeakHour", context.isPeakHour());
        metadata.put("isWeekend", context.isWeekend());
        metadata.put("isInternational", context.isInternational());
        
        return metadata;
    }

    // Exception class
    public static class FeeCalculationException extends RuntimeException {
        public FeeCalculationException(String message) {
            super(message);
        }
        
        public FeeCalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}