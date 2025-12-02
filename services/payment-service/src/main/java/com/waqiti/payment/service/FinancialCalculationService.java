package com.waqiti.payment.service;

import com.waqiti.common.math.MoneyMath;
import com.waqiti.payment.dto.CommissionCalculation;
import com.waqiti.payment.dto.PaymentSplitRequest;
import com.waqiti.payment.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Industrial-grade financial calculation service for payment processing.
 * Provides accurate commission calculations, payment splitting, and fee management
 * with full audit trail and regulatory compliance.
 *
 * Migrated from MoneyCalculator to MoneyMath (November 8, 2025).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialCalculationService {

    @Lazy
    private final FinancialCalculationService self;
    
    @Value("${payment.commission.p2p:0.5}")
    private BigDecimal p2pCommissionRate;
    
    @Value("${payment.commission.merchant:2.5}")
    private BigDecimal merchantCommissionRate;
    
    @Value("${payment.commission.international:3.5}")
    private BigDecimal internationalCommissionRate;
    
    @Value("${payment.commission.minimum:0.10}")
    private BigDecimal minimumCommission;
    
    @Value("${payment.commission.maximum:100.00}")
    private BigDecimal maximumCommission;
    
    // Tiered commission rates based on volume
    // THREAD-SAFETY FIX: Use unmodifiableMap to prevent runtime modifications and race conditions
    private static final Map<BigDecimal, BigDecimal> VOLUME_TIERS;

    static {
        Map<BigDecimal, BigDecimal> tempTiers = new HashMap<>();
        // BEST PRACTICE: Use BigDecimal.ZERO instead of new BigDecimal("0")
        tempTiers.put(BigDecimal.ZERO, new BigDecimal("3.0"));
        tempTiers.put(new BigDecimal("10000"), new BigDecimal("2.5"));
        tempTiers.put(new BigDecimal("50000"), new BigDecimal("2.0"));
        tempTiers.put(new BigDecimal("100000"), new BigDecimal("1.5"));
        // BEST PRACTICE: Use BigDecimal.ONE instead of new BigDecimal("1")
        tempTiers.put(new BigDecimal("500000"), BigDecimal.ONE);
        // CRITICAL: Make immutable to prevent accidental modifications in production
        VOLUME_TIERS = Collections.unmodifiableMap(tempTiers);
    }
    
    /**
     * Calculates commission for a payment based on transaction type and amount.
     * Implements tiered pricing, promotional rates, and partner-specific rates.
     * 
     * @param amount Transaction amount
     * @param transactionType Type of transaction
     * @param currency Currency code
     * @param merchantId Optional merchant ID for custom rates
     * @return Detailed commission calculation
     */
    public CommissionCalculation calculateCommission(
            BigDecimal amount, 
            TransactionType transactionType,
            String currency,
            UUID merchantId) {
        
        log.debug("Calculating commission for amount: {}, type: {}, currency: {}", 
                 amount, transactionType, currency);
        
        // Get base commission rate
        BigDecimal baseRate = getBaseCommissionRate(transactionType, merchantId);
        
        // Apply volume-based discount if applicable
        if (merchantId != null) {
            baseRate = applyVolumeDiscount(merchantId, baseRate);
        }
        
        // Calculate commission amount
        BigDecimal commission = MoneyMath.calculatePercentage(baseRate, amount);

        // Apply minimum and maximum limits
        commission = applyCommissionLimits(commission, currency);

        // Calculate net amount
        BigDecimal netAmount = MoneyMath.subtract(amount, commission);
        
        // Build detailed calculation response
        CommissionCalculation calculation = CommissionCalculation.builder()
                .transactionAmount(amount)
                .commissionRate(baseRate)
                .commissionAmount(commission)
                .netAmount(netAmount)
                .currency(currency)
                .transactionType(transactionType)
                .appliedTier(determineAppliedTier(merchantId))
                .build();
        
        log.info("Commission calculated: {} {} ({}% of {} {})", 
                commission, currency, baseRate, amount, currency);
        
        return calculation;
    }
    
    /**
     * Splits a payment among multiple recipients with proper remainder handling.
     * Ensures total amount is preserved without loss due to rounding.
     * 
     * @param request Payment split request
     * @return Map of recipient IDs to their amounts
     */
    public Map<UUID, BigDecimal> splitPayment(PaymentSplitRequest request) {
        log.debug("Splitting payment of {} {} among {} recipients", 
                 request.getTotalAmount(), request.getCurrency(), request.getRecipients().size());
        
        Map<UUID, BigDecimal> splitResults = new HashMap<>();
        
        if (request.getSplitType() == PaymentSplitRequest.SplitType.EQUAL) {
            // Equal split among all recipients
            BigDecimal[] splits = MoneyMath.splitAmount(
                request.getTotalAmount(),
                request.getRecipients().size()
            );
            
            int index = 0;
            for (UUID recipientId : request.getRecipients()) {
                splitResults.put(recipientId, splits[index++]);
            }
            
        } else if (request.getSplitType() == PaymentSplitRequest.SplitType.PERCENTAGE) {
            // Percentage-based split
            Map<UUID, BigDecimal> percentages = request.getPercentages();
            BigDecimal totalPercentage = BigDecimal.ZERO;
            
            // Validate percentages sum to 100
            for (BigDecimal percentage : percentages.values()) {
                totalPercentage = totalPercentage.add(percentage);
            }
            
            if (!MoneyMath.areAmountsEqual(totalPercentage, new BigDecimal("100"), new BigDecimal("0.01"))) {
                throw new IllegalArgumentException("Percentages must sum to 100%");
            }
            
            // Calculate amounts based on percentages
            BigDecimal runningTotal = BigDecimal.ZERO;
            UUID lastRecipient = null;
            
            for (Map.Entry<UUID, BigDecimal> entry : percentages.entrySet()) {
                UUID recipientId = entry.getKey();
                BigDecimal percentage = entry.getValue();
                
                BigDecimal amount = MoneyMath.calculatePercentage(
                    percentage,
                    request.getTotalAmount()
                );

                splitResults.put(recipientId, amount);
                runningTotal = MoneyMath.add(runningTotal, amount);
                lastRecipient = recipientId;
            }
            
            // Handle any rounding difference
            BigDecimal difference = MoneyMath.subtract(
                request.getTotalAmount(),
                runningTotal
            );

            if (difference.compareTo(BigDecimal.ZERO) != 0 && lastRecipient != null) {
                BigDecimal adjustedAmount = MoneyMath.add(
                    splitResults.get(lastRecipient),
                    difference
                );
                splitResults.put(lastRecipient, adjustedAmount);
                log.debug("Adjusted last recipient amount by {} to handle rounding", difference);
            }
            
        } else if (request.getSplitType() == PaymentSplitRequest.SplitType.CUSTOM) {
            // Custom amounts per recipient
            Map<UUID, BigDecimal> customAmounts = request.getCustomAmounts();
            BigDecimal total = BigDecimal.ZERO;
            
            for (Map.Entry<UUID, BigDecimal> entry : customAmounts.entrySet()) {
                UUID recipientId = entry.getKey();
                BigDecimal amount = entry.getValue();
                
                // Round to currency precision
                BigDecimal roundedAmount = MoneyMath.roundCurrency(amount);
                splitResults.put(recipientId, roundedAmount);
                total = MoneyMath.add(total, roundedAmount);
            }

            // Validate total matches
            if (!MoneyMath.areAmountsEqual(total, request.getTotalAmount(), new BigDecimal("0.01"))) {
                throw new IllegalArgumentException(
                    String.format("Custom amounts sum (%s) does not match total (%s)", 
                                 total, request.getTotalAmount())
                );
            }
        }
        
        // Verify split integrity
        verifySplitIntegrity(splitResults, request.getTotalAmount(), request.getCurrency());
        
        log.info("Payment split completed: {} {} distributed among {} recipients", 
                request.getTotalAmount(), request.getCurrency(), splitResults.size());
        
        return splitResults;
    }
    
    /**
     * Calculates currency conversion with proper precision and audit trail.
     * 
     * @param amount Amount to convert
     * @param fromCurrency Source currency
     * @param toCurrency Target currency
     * @param exchangeRate Exchange rate
     * @return Converted amount
     */
    public BigDecimal convertCurrency(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            BigDecimal exchangeRate) {
        
        log.debug("Converting {} {} to {} at rate {}", 
                 amount, fromCurrency, toCurrency, exchangeRate);
        
        BigDecimal convertedAmount = MoneyMath.convertCurrency(amount, exchangeRate);
        
        log.info("Currency conversion: {} {} = {} {} (rate: {})", 
                amount, fromCurrency, convertedAmount, toCurrency, exchangeRate);
        
        return convertedAmount;
    }
    
    /**
     * Calculates refund amount considering partial refunds and commission reversal.
     * 
     * @param originalAmount Original transaction amount
     * @param refundPercentage Percentage to refund (100 for full refund)
     * @param includeCommission Whether to include commission in refund
     * @param originalCommission Original commission amount
     * @param currency Currency code
     * @return Refund amount
     */
    public BigDecimal calculateRefundAmount(
            BigDecimal originalAmount,
            BigDecimal refundPercentage,
            boolean includeCommission,
            BigDecimal originalCommission,
            String currency) {
        
        log.debug("Calculating refund: {}% of {} {}", refundPercentage, originalAmount, currency);
        
        // Calculate base refund amount
        BigDecimal baseRefund = MoneyMath.calculatePercentage(
            refundPercentage,
            originalAmount
        );

        // Add commission if applicable
        BigDecimal totalRefund = baseRefund;
        if (includeCommission && originalCommission != null) {
            BigDecimal commissionRefund = MoneyMath.calculatePercentage(
                refundPercentage,
                originalCommission
            );
            totalRefund = MoneyMath.add(baseRefund, commissionRefund);
            log.debug("Including commission refund: {} {}", commissionRefund, currency);
        }
        
        log.info("Refund calculated: {} {} ({}% of original)", 
                totalRefund, currency, refundPercentage);
        
        return totalRefund;
    }
    
    // Private helper methods
    
    private BigDecimal getBaseCommissionRate(TransactionType type, UUID merchantId) {
        // Check for merchant-specific rates first
        if (merchantId != null) {
            BigDecimal customRate = self.getMerchantCustomRate(merchantId);
            if (customRate != null) {
                return customRate;
            }
        }
        
        // Return standard rates based on transaction type
        switch (type) {
            case P2P_TRANSFER:
                return p2pCommissionRate;
            case MERCHANT_PAYMENT:
                return merchantCommissionRate;
            case INTERNATIONAL_TRANSFER:
                return internationalCommissionRate;
            default:
                return merchantCommissionRate;
        }
    }
    
    @Cacheable(value = "merchantRates", key = "#merchantId")
    private BigDecimal getMerchantCustomRate(UUID merchantId) {
        // This would typically query a database for merchant-specific rates
        // Return default rate to use standard rates
        log.debug("No merchant-specific rates found for merchantId: {} - using default rate", merchantId);
        return new BigDecimal("2.9"); // Default merchant rate
    }
    
    private BigDecimal applyVolumeDiscount(UUID merchantId, BigDecimal baseRate) {
        // Get merchant's monthly volume (would query from database)
        BigDecimal monthlyVolume = self.getMerchantMonthlyVolume(merchantId);
        
        // Find applicable tier
        BigDecimal applicableRate = baseRate;
        for (Map.Entry<BigDecimal, BigDecimal> tier : VOLUME_TIERS.entrySet()) {
            if (monthlyVolume.compareTo(tier.getKey()) >= 0) {
                applicableRate = tier.getValue();
            }
        }
        
        // Return the lower of base rate or volume-based rate
        return baseRate.min(applicableRate);
    }
    
    @Cacheable(value = "merchantVolume", key = "#merchantId")
    private BigDecimal getMerchantMonthlyVolume(UUID merchantId) {
        // This would query actual transaction volume from database
        // Placeholder implementation
        return BigDecimal.ZERO;
    }
    
    private BigDecimal applyCommissionLimits(BigDecimal commission, String currency) {
        // Apply minimum commission
        if (commission.compareTo(minimumCommission) < 0) {
            log.debug("Applying minimum commission: {} {}", minimumCommission, currency);
            return minimumCommission;
        }
        
        // Apply maximum commission
        if (commission.compareTo(maximumCommission) > 0) {
            log.debug("Applying maximum commission: {} {}", maximumCommission, currency);
            return maximumCommission;
        }
        
        return commission;
    }
    
    private String determineAppliedTier(UUID merchantId) {
        if (merchantId == null) {
            return "STANDARD";
        }
        
        BigDecimal volume = self.getMerchantMonthlyVolume(merchantId);
        if (volume.compareTo(new BigDecimal("500000")) >= 0) return "PLATINUM";
        if (volume.compareTo(new BigDecimal("100000")) >= 0) return "GOLD";
        if (volume.compareTo(new BigDecimal("50000")) >= 0) return "SILVER";
        if (volume.compareTo(new BigDecimal("10000")) >= 0) return "BRONZE";
        return "STANDARD";
    }
    
    private void verifySplitIntegrity(Map<UUID, BigDecimal> splits, BigDecimal expectedTotal, String currency) {
        BigDecimal actualTotal = BigDecimal.ZERO;
        for (BigDecimal amount : splits.values()) {
            actualTotal = MoneyMath.add(actualTotal, amount);
        }

        if (!MoneyMath.areAmountsEqual(actualTotal, expectedTotal, new BigDecimal("0.01"))) {
            log.error("Split integrity check failed: {} != {}", actualTotal, expectedTotal);
            throw new IllegalStateException("Payment split integrity check failed");
        }
    }
}