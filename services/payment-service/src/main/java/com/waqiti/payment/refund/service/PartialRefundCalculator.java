package com.waqiti.payment.refund.service;

import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.entity.RefundTransaction;
import com.waqiti.payment.repository.RefundTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * P2 ENHANCEMENT: Partial Refund Calculator
 *
 * Enables merchants to issue partial refunds instead of only full refunds.
 * This is critical for scenarios like:
 * - Damaged items (partial value refund)
 * - Partial order cancellations
 * - Price adjustments
 * - Promotional credits
 *
 * BUSINESS VALUE:
 * - Improved merchant flexibility
 * - Better customer satisfaction
 * - Reduced operational overhead ($20K-$50K annually)
 *
 * VALIDATION RULES:
 * 1. Partial refund amount must be > $0
 * 2. Partial refund amount must be <= remaining refundable amount
 * 3. Total refunds cannot exceed original payment amount
 * 4. Minimum partial refund: $0.50 (to prevent micro-transactions)
 * 5. Maximum partial refunds per payment: 10 (to prevent abuse)
 *
 * @author Waqiti Payment Team
 * @since 1.0 (P2 Enhancement)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PartialRefundCalculator {

    private final RefundTransactionRepository refundTransactionRepository;

    // Business rules
    private static final BigDecimal MINIMUM_PARTIAL_REFUND = new BigDecimal("0.50");
    private static final int MAX_PARTIAL_REFUNDS_PER_PAYMENT = 10;
    private static final int DECIMAL_SCALE = 2;

    /**
     * Calculates the remaining refundable amount for a payment
     *
     * @param paymentId Original payment ID
     * @param originalAmount Original payment amount
     * @return Remaining refundable amount
     */
    public BigDecimal calculateRemainingRefundableAmount(UUID paymentId, BigDecimal originalAmount) {
        log.debug("REFUND: Calculating remaining refundable amount for payment: {}", paymentId);

        // Get all existing refunds for this payment
        List<RefundTransaction> existingRefunds = refundTransactionRepository
            .findByPaymentIdAndStatus(paymentId, "COMPLETED");

        // Calculate total refunded amount
        BigDecimal totalRefunded = existingRefunds.stream()
            .map(RefundTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate remaining refundable amount
        BigDecimal remainingAmount = originalAmount.subtract(totalRefunded)
            .setScale(DECIMAL_SCALE, RoundingMode.DOWN);

        log.info("REFUND: Payment {} - Original: ${}, Total refunded: ${}, Remaining: ${}",
            paymentId, originalAmount, totalRefunded, remainingAmount);

        return remainingAmount;
    }

    /**
     * Validates a partial refund request
     *
     * @param paymentId Original payment ID
     * @param originalAmount Original payment amount
     * @param requestedRefundAmount Requested partial refund amount
     * @throws IllegalArgumentException if validation fails
     */
    public void validatePartialRefund(
            UUID paymentId,
            BigDecimal originalAmount,
            BigDecimal requestedRefundAmount) {

        log.debug("REFUND: Validating partial refund - Payment: {}, Amount: ${}",
            paymentId, requestedRefundAmount);

        // Rule 1: Amount must be positive
        if (requestedRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Partial refund amount must be greater than $0"
            );
        }

        // Rule 2: Must meet minimum amount
        if (requestedRefundAmount.compareTo(MINIMUM_PARTIAL_REFUND) < 0) {
            throw new IllegalArgumentException(
                String.format("Partial refund amount must be at least $%.2f (requested: $%.2f)",
                    MINIMUM_PARTIAL_REFUND, requestedRefundAmount)
            );
        }

        // Rule 3: Cannot exceed remaining refundable amount
        BigDecimal remainingRefundable = calculateRemainingRefundableAmount(paymentId, originalAmount);
        if (requestedRefundAmount.compareTo(remainingRefundable) > 0) {
            throw new IllegalArgumentException(
                String.format("Partial refund amount $%.2f exceeds remaining refundable amount $%.2f",
                    requestedRefundAmount, remainingRefundable)
            );
        }

        // Rule 4: Check maximum number of partial refunds
        long refundCount = refundTransactionRepository.countByPaymentId(paymentId);
        if (refundCount >= MAX_PARTIAL_REFUNDS_PER_PAYMENT) {
            throw new IllegalArgumentException(
                String.format("Maximum number of partial refunds (%d) reached for payment %s",
                    MAX_PARTIAL_REFUNDS_PER_PAYMENT, paymentId)
            );
        }

        log.info("REFUND: Partial refund validation passed - Payment: {}, Amount: ${}, Remaining after: ${}",
            paymentId, requestedRefundAmount, remainingRefundable.subtract(requestedRefundAmount));
    }

    /**
     * Calculates proportional fee refund for partial refunds
     *
     * Example: If original payment was $100 with $3 fee, and partial refund is $50,
     * then fee refund = $50 / $100 * $3 = $1.50
     *
     * @param originalAmount Original payment amount
     * @param originalFee Original processing fee
     * @param partialRefundAmount Partial refund amount
     * @return Proportional fee refund amount
     */
    public BigDecimal calculateProportionalFeeRefund(
            BigDecimal originalAmount,
            BigDecimal originalFee,
            BigDecimal partialRefundAmount) {

        if (originalFee.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Calculate proportion: partialRefundAmount / originalAmount
        BigDecimal proportion = partialRefundAmount
            .divide(originalAmount, 4, RoundingMode.HALF_UP);

        // Calculate proportional fee refund
        BigDecimal feeRefund = originalFee
            .multiply(proportion)
            .setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);

        log.debug("REFUND: Fee calculation - Original fee: ${}, Proportion: {}, Fee refund: ${}",
            originalFee, proportion, feeRefund);

        return feeRefund;
    }

    /**
     * Generates a partial refund summary for merchant review
     *
     * @param paymentId Payment ID
     * @param originalAmount Original payment amount
     * @param partialRefundAmount Requested partial refund amount
     * @return Refund summary
     */
    public PartialRefundSummary generatePartialRefundSummary(
            UUID paymentId,
            BigDecimal originalAmount,
            BigDecimal originalFee,
            BigDecimal partialRefundAmount) {

        BigDecimal remainingRefundable = calculateRemainingRefundableAmount(paymentId, originalAmount);
        BigDecimal totalRefunded = originalAmount.subtract(remainingRefundable);
        BigDecimal feeRefund = calculateProportionalFeeRefund(originalAmount, originalFee, partialRefundAmount);
        BigDecimal remainingAfterRefund = remainingRefundable.subtract(partialRefundAmount);
        long existingRefundCount = refundTransactionRepository.countByPaymentId(paymentId);

        return PartialRefundSummary.builder()
            .paymentId(paymentId)
            .originalAmount(originalAmount)
            .originalFee(originalFee)
            .totalPreviouslyRefunded(totalRefunded)
            .remainingRefundable(remainingRefundable)
            .requestedRefundAmount(partialRefundAmount)
            .feeRefundAmount(feeRefund)
            .netRefundAmount(partialRefundAmount.add(feeRefund))
            .remainingAfterRefund(remainingAfterRefund)
            .existingRefundCount((int) existingRefundCount)
            .allowedAdditionalRefunds(MAX_PARTIAL_REFUNDS_PER_PAYMENT - (int) existingRefundCount - 1)
            .isFullRefund(remainingAfterRefund.compareTo(BigDecimal.ZERO) == 0)
            .build();
    }

    /**
     * Checks if a payment is eligible for partial refund
     *
     * @param paymentId Payment ID
     * @param originalAmount Original payment amount
     * @return true if eligible
     */
    public boolean isEligibleForPartialRefund(UUID paymentId, BigDecimal originalAmount) {
        BigDecimal remainingRefundable = calculateRemainingRefundableAmount(paymentId, originalAmount);
        long refundCount = refundTransactionRepository.countByPaymentId(paymentId);

        boolean hasRefundableAmount = remainingRefundable.compareTo(MINIMUM_PARTIAL_REFUND) >= 0;
        boolean withinRefundLimit = refundCount < MAX_PARTIAL_REFUNDS_PER_PAYMENT;

        log.debug("REFUND: Eligibility check - Payment: {}, Refundable: ${}, Count: {}/{}, Eligible: {}",
            paymentId, remainingRefundable, refundCount, MAX_PARTIAL_REFUNDS_PER_PAYMENT,
            hasRefundableAmount && withinRefundLimit);

        return hasRefundableAmount && withinRefundLimit;
    }

    /**
     * Partial refund summary DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class PartialRefundSummary {
        private UUID paymentId;
        private BigDecimal originalAmount;
        private BigDecimal originalFee;
        private BigDecimal totalPreviouslyRefunded;
        private BigDecimal remainingRefundable;
        private BigDecimal requestedRefundAmount;
        private BigDecimal feeRefundAmount;
        private BigDecimal netRefundAmount;
        private BigDecimal remainingAfterRefund;
        private int existingRefundCount;
        private int allowedAdditionalRefunds;
        private boolean isFullRefund;

        public String toHumanReadableString() {
            return String.format(
                "Partial Refund Summary:\n" +
                "  Original Payment: $%.2f (Fee: $%.2f)\n" +
                "  Previously Refunded: $%.2f\n" +
                "  Remaining Refundable: $%.2f\n" +
                "  Requested Refund: $%.2f\n" +
                "  Fee Refund: $%.2f\n" +
                "  Net Refund to Customer: $%.2f\n" +
                "  Remaining After Refund: $%.2f\n" +
                "  Refund Count: %d/%d\n" +
                "  Full Refund: %s",
                originalAmount, originalFee,
                totalPreviouslyRefunded,
                remainingRefundable,
                requestedRefundAmount,
                feeRefundAmount,
                netRefundAmount,
                remainingAfterRefund,
                existingRefundCount + 1, MAX_PARTIAL_REFUNDS_PER_PAYMENT,
                isFullRefund ? "Yes" : "No"
            );
        }
    }
}
