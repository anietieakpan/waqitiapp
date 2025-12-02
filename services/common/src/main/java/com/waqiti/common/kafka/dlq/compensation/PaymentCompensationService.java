package com.waqiti.common.kafka.dlq.compensation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment compensation service interface.
 *
 * Handles compensation for failed payment operations including:
 * - Payment reversals
 * - Refund processing
 * - Authorization releases
 * - Settlement corrections
 *
 * IMPLEMENTATION REQUIREMENTS:
 * - Must be implemented by payment-service
 * - All operations must use BigDecimal for amounts
 * - Must validate payment state before compensation
 * - Must check idempotency keys
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface PaymentCompensationService extends CompensationService {

    /**
     * Reverses a payment transaction.
     *
     * @param paymentId Payment ID to reverse
     * @param amount Amount to reverse (must match original)
     * @param reason Reason for reversal
     * @return Compensation result
     */
    CompensationResult reversePayment(UUID paymentId, BigDecimal amount, String reason);

    /**
     * Releases a payment authorization.
     *
     * @param authorizationId Authorization ID to release
     * @param reason Reason for release
     * @return Compensation result
     */
    CompensationResult releaseAuthorization(UUID authorizationId, String reason);

    /**
     * Processes a refund for a failed payment.
     *
     * @param paymentId Payment ID to refund
     * @param amount Refund amount
     * @param reason Refund reason
     * @return Compensation result
     */
    CompensationResult refundPayment(UUID paymentId, BigDecimal amount, String reason);
}
