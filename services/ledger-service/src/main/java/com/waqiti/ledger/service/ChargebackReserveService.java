package com.waqiti.ledger.service;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Chargeback Reserve Service - Manages merchant chargeback reserves
 * 
 * Handles financial reserves to mitigate chargeback risks:
 * - Dynamic reserve calculations based on merchant risk
 * - Reserve adjustments for chargeback events
 * - Risk-based reserve requirements
 * - Merchant liquidity management
 * - Reserve release mechanisms
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackReserveService {

    private final LedgerServiceImpl ledgerService;
    private final MerchantAccountService merchantAccountService;

    // Reserve configuration constants
    private static final BigDecimal BASE_RESERVE_RATE = new BigDecimal("0.05"); // 5%
    private static final BigDecimal HIGH_RISK_RESERVE_RATE = new BigDecimal("0.15"); // 15%
    private static final BigDecimal HIGH_VALUE_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal MAX_RESERVE_RATE = new BigDecimal("0.30"); // 30%
    private static final BigDecimal MIN_RESERVE_AMOUNT = new BigDecimal("100.00");

    /**
     * Adjusts chargeback reserve based on merchant risk profile
     * 
     * @param merchantId Merchant identifier
     * @param chargebackAmount Chargeback amount
     * @param currency Currency code
     * @param cardNetwork Card network
     * @param chargebackRatio Current merchant chargeback ratio
     * @param isHighValue Whether this is a high-value chargeback
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustChargebackReserve(
            String merchantId,
            BigDecimal chargebackAmount,
            String currency,
            PaymentChargebackEvent.CardNetwork cardNetwork,
            BigDecimal chargebackRatio,
            boolean isHighValue) {

        try {
            log.info("Adjusting chargeback reserve for merchant: {} due to chargeback amount: {} {}", 
                merchantId, chargebackAmount, currency);

            // Calculate required reserve adjustment
            BigDecimal reserveAdjustment = calculateReserveAdjustment(
                merchantId, chargebackAmount, chargebackRatio, isHighValue
            );

            if (reserveAdjustment.compareTo(BigDecimal.ZERO) > 0) {
                // Increase reserve
                increaseChargebackReserve(merchantId, reserveAdjustment, currency, 
                    "Chargeback reserve increase due to " + cardNetwork.getDisplayName() + " chargeback");
                
                log.info("Increased chargeback reserve by {} {} for merchant: {}", 
                    reserveAdjustment, currency, merchantId);
            }

            // Check if merchant needs additional monitoring
            if (requiresAdditionalMonitoring(chargebackRatio, isHighValue)) {
                flagMerchantForReview(merchantId, chargebackRatio, chargebackAmount);
            }

        } catch (Exception e) {
            log.error("Failed to adjust chargeback reserve for merchant: {}", merchantId, e);
            throw new ChargebackReserveException("Failed to adjust chargeback reserve", e);
        }
    }

    /**
     * Calculates the required reserve adjustment amount
     */
    private BigDecimal calculateReserveAdjustment(
            String merchantId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackRatio,
            boolean isHighValue) {

        // Base reserve calculation
        BigDecimal reserveRate = BASE_RESERVE_RATE;

        // Adjust for merchant risk profile
        if (chargebackRatio != null) {
            if (chargebackRatio.compareTo(new BigDecimal("0.01")) > 0) { // > 1%
                reserveRate = HIGH_RISK_RESERVE_RATE;
            } else if (chargebackRatio.compareTo(new BigDecimal("0.005")) > 0) { // > 0.5%
                reserveRate = BASE_RESERVE_RATE.multiply(new BigDecimal("2"));
            }
        }

        // Adjust for high-value chargebacks
        if (isHighValue) {
            reserveRate = reserveRate.multiply(HIGH_VALUE_MULTIPLIER);
        }

        // Cap at maximum reserve rate
        reserveRate = reserveRate.min(MAX_RESERVE_RATE);

        // Calculate reserve amount
        BigDecimal reserveAmount = chargebackAmount.multiply(reserveRate)
            .setScale(2, RoundingMode.HALF_UP);

        // Ensure minimum reserve amount
        return reserveAmount.max(MIN_RESERVE_AMOUNT);
    }

    /**
     * Increases chargeback reserve for a merchant
     */
    private void increaseChargebackReserve(
            String merchantId,
            BigDecimal reserveAmount,
            String currency,
            String description) {

        // Create ledger entries for reserve increase
        ledgerService.createDoubleEntry(
            merchantId + "_RESERVE_" + System.currentTimeMillis(),
            "CHARGEBACK_RESERVE_INCREASE",
            description,
            merchantId,
            reserveAmount,
            currency,
            "CHARGEBACK_RESERVES", // Debit reserve account
            "MERCHANT_CASH", // Credit merchant cash account
            java.time.LocalDateTime.now(),
            java.util.Map.of(
                "merchant_id", merchantId,
                "reserve_type", "CHARGEBACK",
                "adjustment_type", "INCREASE"
            )
        );
    }

    /**
     * Checks if merchant requires additional monitoring
     */
    private boolean requiresAdditionalMonitoring(BigDecimal chargebackRatio, boolean isHighValue) {
        return (chargebackRatio != null && chargebackRatio.compareTo(new BigDecimal("0.02")) > 0) ||
               isHighValue;
    }

    /**
     * Flags merchant for review due to chargeback activity
     */
    private void flagMerchantForReview(String merchantId, BigDecimal chargebackRatio, BigDecimal chargebackAmount) {
        try {
            merchantAccountService.flagMerchantForReview(
                merchantId,
                "HIGH_CHARGEBACK_ACTIVITY",
                String.format("Chargeback ratio: %s, Recent chargeback: %s", 
                    chargebackRatio, chargebackAmount),
                java.time.LocalDateTime.now().plusDays(7) // Review deadline
            );

            log.warn("Flagged merchant for review due to chargeback activity - Merchant: {}, Ratio: {}", 
                merchantId, chargebackRatio);

        } catch (Exception e) {
            log.error("Failed to flag merchant for review: {}", merchantId, e);
        }
    }

    /**
     * Custom exception for chargeback reserve operations
     */
    public static class ChargebackReserveException extends RuntimeException {
        public ChargebackReserveException(String message) {
            super(message);
        }
        
        public ChargebackReserveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}