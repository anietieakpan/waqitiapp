package com.waqiti.common.kafka.dlq.compensation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ledger compensation service interface.
 *
 * Handles compensation for failed ledger operations including:
 * - Reversal journal entries
 * - Correcting entries
 * - Balance reconciliation
 *
 * ACCOUNTING COMPLIANCE:
 * - All ledger entries must balance (debits = credits)
 * - Reversals must reference original entry
 * - Audit trail must be immutable
 * - Double-entry bookkeeping enforced
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
public interface LedgerCompensationService extends CompensationService {

    /**
     * Creates a reversal journal entry.
     *
     * @param originalEntryId Original journal entry ID to reverse
     * @param reason Reversal reason
     * @return Compensation result
     */
    CompensationResult createReversalEntry(UUID originalEntryId, String reason);

    /**
     * Creates a correcting journal entry.
     *
     * @param originalEntryId Original entry ID
     * @param correctionAmount Correction amount
     * @param reason Correction reason
     * @return Compensation result
     */
    CompensationResult createCorrectingEntry(UUID originalEntryId, BigDecimal correctionAmount, String reason);
}
