package com.waqiti.common.kafka.dlq.compensation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wallet compensation service interface.
 *
 * Handles compensation for failed wallet operations including:
 * - Balance adjustments
 * - Transaction reversals
 * - Hold releases
 * - Credit/debit corrections
 *
 * FINANCIAL PRECISION:
 * - All amounts must use BigDecimal with scale=4
 * - Currency must be specified for multi-currency wallets
 * - Balance adjustments must be audited
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface WalletCompensationService extends CompensationService {

    /**
     * Adjusts wallet balance to compensate for failed operation.
     *
     * @param walletId Wallet ID
     * @param amount Adjustment amount (positive for credit, negative for debit)
     * @param currency Currency code (USD, EUR, etc.)
     * @param reason Adjustment reason
     * @return Compensation result
     */
    CompensationResult adjustBalance(UUID walletId, BigDecimal amount, String currency, String reason);

    /**
     * Releases a wallet hold.
     *
     * @param holdId Hold ID to release
     * @param reason Release reason
     * @return Compensation result
     */
    CompensationResult releaseHold(UUID holdId, String reason);

    /**
     * Reverses a wallet transaction.
     *
     * @param transactionId Transaction ID to reverse
     * @param reason Reversal reason
     * @return Compensation result
     */
    CompensationResult reverseTransaction(UUID transactionId, String reason);
}
