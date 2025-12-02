package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.AccountReconciliation;
import com.waqiti.ledger.domain.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Account Reconciliation Repository
 *
 * Provides data access for account reconciliation records.
 */
@Repository
public interface AccountReconciliationRepository extends JpaRepository<AccountReconciliation, String> {

    /**
     * Find reconciliations by account ID
     */
    List<AccountReconciliation> findByAccountIdOrderByReconciliationDateDesc(String accountId);

    /**
     * Find reconciliations by status
     */
    List<AccountReconciliation> findByStatus(ReconciliationStatus status);

    /**
     * Find reconciliation by account ID and date
     */
    Optional<AccountReconciliation> findByAccountIdAndReconciliationDate(String accountId, LocalDate date);

    /**
     * Find pending reconciliations
     */
    List<AccountReconciliation> findByStatusIn(List<ReconciliationStatus> statuses);

    /**
     * Check if reconciliation exists for account and date
     */
    boolean existsByAccountIdAndReconciliationDate(String accountId, LocalDate date);

    /**
     * Check if reconciliation exists by reconciliation ID and account ID (for idempotency)
     */
    boolean existsByReconciliationIdAndAccountId(String reconciliationId, String accountId);
}
