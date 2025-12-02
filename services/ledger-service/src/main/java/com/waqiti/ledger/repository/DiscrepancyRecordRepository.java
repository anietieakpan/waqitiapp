package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.DiscrepancyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Discrepancy Record Repository
 *
 * Provides data access for discrepancy records.
 */
@Repository
public interface DiscrepancyRecordRepository extends JpaRepository<DiscrepancyRecord, String> {

    /**
     * Find discrepancies by reconciliation ID
     */
    List<DiscrepancyRecord> findByReconciliationId(String reconciliationId);

    /**
     * Find unresolved discrepancies
     */
    List<DiscrepancyRecord> findByResolvedFalse();

    /**
     * Find discrepancies by account ID
     */
    List<DiscrepancyRecord> findByAccountId(String accountId);

    /**
     * Count unresolved discrepancies for a reconciliation
     */
    long countByReconciliationIdAndResolvedFalse(String reconciliationId);
}
