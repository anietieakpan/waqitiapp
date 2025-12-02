package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.AccountCredit;
import com.waqiti.billingorchestrator.entity.AccountCredit.CreditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AccountCredit entities
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Repository
public interface AccountCreditRepository extends JpaRepository<AccountCredit, UUID> {

    /**
     * Find all credits for account
     */
    List<AccountCredit> findByAccountIdOrderByIssuedAtAsc(UUID accountId);

    /**
     * Find available credits (issued, not expired)
     */
    @Query("SELECT c FROM AccountCredit c WHERE c.accountId = :accountId " +
           "AND c.status IN ('ISSUED', 'PARTIALLY_APPLIED') " +
           "AND c.remainingBalance > 0 " +
           "AND (c.expiryDate IS NULL OR c.expiryDate > :now) " +
           "ORDER BY c.issuedAt ASC")
    List<AccountCredit> findAvailableCredits(@Param("accountId") UUID accountId, @Param("now") LocalDateTime now);

    /**
     * Get total available credit balance
     */
    @Query("SELECT COALESCE(SUM(c.remainingBalance), 0) FROM AccountCredit c " +
           "WHERE c.accountId = :accountId " +
           "AND c.status IN ('ISSUED', 'PARTIALLY_APPLIED') " +
           "AND c.remainingBalance > 0 " +
           "AND (c.expiryDate IS NULL OR c.expiryDate > :now)")
    BigDecimal getTotalAvailableBalance(@Param("accountId") UUID accountId, @Param("now") LocalDateTime now);

    /**
     * Find expired credits
     */
    @Query("SELECT c FROM AccountCredit c WHERE c.status IN ('ISSUED', 'PARTIALLY_APPLIED') " +
           "AND c.expiryDate < :now AND c.expiryDate IS NOT NULL")
    List<AccountCredit> findExpiredCredits(@Param("now") LocalDateTime now);

    /**
     * Find credits by status
     */
    List<AccountCredit> findByAccountIdAndStatus(UUID accountId, CreditStatus status);
}
