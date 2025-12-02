package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Account entities
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Find account by account code
     */
    Optional<Account> findByAccountCode(String accountCode);

    /**
     * Check if account code exists
     */
    boolean existsByAccountCode(String accountCode);

    /**
     * Find all accounts ordered by account code
     */
    List<Account> findAllByOrderByAccountCodeAsc();

    /**
     * Find accounts by type and active status
     */
    List<Account> findByAccountTypeAndIsActiveTrue(Account.AccountType accountType);

    /**
     * Find all active accounts that allow transactions
     */
    List<Account> findByIsActiveTrueAndAllowsTransactionsTrue();

    /**
     * Find child accounts of a parent account
     */
    List<Account> findByParentAccountIdOrderByAccountCodeAsc(UUID parentAccountId);

    /**
     * Find root accounts (no parent)
     */
    List<Account> findByParentAccountIdIsNullOrderByAccountCodeAsc();

    /**
     * Find accounts by type
     */
    List<Account> findByAccountTypeOrderByAccountCodeAsc(Account.AccountType accountType);

    /**
     * Find active accounts by type
     */
    List<Account> findByAccountTypeAndIsActiveTrueOrderByAccountCodeAsc(Account.AccountType accountType);

    /**
     * Search accounts by name or code
     */
    @Query("SELECT a FROM Account a WHERE " +
           "(LOWER(a.accountName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(a.accountCode) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "a.isActive = true")
    List<Account> searchActiveAccounts(@Param("searchTerm") String searchTerm);

    /**
     * Get account hierarchy depth
     */
    @Query("SELECT MAX(a.level) FROM Account a WHERE a.isActive = true")
    Integer getMaxAccountLevel();

    /**
     * Get accounts by normal balance type
     */
    List<Account> findByNormalBalanceAndIsActiveTrueOrderByAccountCodeAsc(Account.NormalBalance normalBalance);

    /**
     * Get accounts that allow transactions within an account type
     */
    List<Account> findByAccountTypeAndAllowsTransactionsTrueAndIsActiveTrueOrderByAccountCodeAsc(
        Account.AccountType accountType);

    /**
     * Count active accounts
     */
    long countByIsActiveTrue();

    /**
     * Count accounts by type
     */
    long countByAccountTypeAndIsActiveTrue(Account.AccountType accountType);

    /**
     * Find accounts created within date range
     */
    @Query("SELECT a FROM Account a WHERE a.createdAt >= :startDate AND a.createdAt <= :endDate ORDER BY a.createdAt DESC")
    List<Account> findAccountsCreatedBetween(@Param("startDate") java.time.LocalDateTime startDate, 
                                           @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Update account status
     */
    @Modifying
    @Query("UPDATE Account a SET a.isActive = :isActive, a.lastUpdated = CURRENT_TIMESTAMP WHERE a.accountId = :accountId")
    int updateAccountStatus(@Param("accountId") UUID accountId, @Param("isActive") boolean isActive);

    /**
     * Get all parent account IDs in hierarchy
     */
    @Query(value = "WITH RECURSIVE account_hierarchy AS (" +
                   "  SELECT account_id, parent_account_id, account_code, 0 as level " +
                   "  FROM accounts WHERE account_id = :accountId " +
                   "  UNION ALL " +
                   "  SELECT a.account_id, a.parent_account_id, a.account_code, ah.level + 1 " +
                   "  FROM accounts a " +
                   "  INNER JOIN account_hierarchy ah ON a.account_id = ah.parent_account_id " +
                   ") " +
                   "SELECT account_id FROM account_hierarchy WHERE level > 0", 
           nativeQuery = true)
    List<UUID> getParentAccountIds(@Param("accountId") UUID accountId);

    /**
     * Get all child account IDs in hierarchy
     */
    @Query(value = "WITH RECURSIVE account_hierarchy AS (" +
                   "  SELECT account_id, parent_account_id, account_code, 0 as level " +
                   "  FROM accounts WHERE account_id = :accountId " +
                   "  UNION ALL " +
                   "  SELECT a.account_id, a.parent_account_id, a.account_code, ah.level + 1 " +
                   "  FROM accounts a " +
                   "  INNER JOIN account_hierarchy ah ON a.parent_account_id = ah.account_id " +
                   ") " +
                   "SELECT account_id FROM account_hierarchy WHERE level > 0",
           nativeQuery = true)
    List<UUID> getChildAccountIds(@Param("accountId") UUID accountId);

    /**
     * Find accounts by name or code containing (case insensitive) with pagination
     */
    org.springframework.data.domain.Page<Account> findByAccountNameContainingIgnoreCaseOrAccountCodeContainingIgnoreCase(
        String nameTerm, String codeTerm, org.springframework.data.domain.Pageable pageable);

    /**
     * Find by parent account ID
     */
    List<Account> findByParentAccountId(UUID parentAccountId);

    // =========================================================================
    // PAGINATED QUERY METHODS (P1-4: Production-ready pagination support)
    // =========================================================================

    /**
     * Find accounts by type with pagination (P1-4)
     */
    org.springframework.data.domain.Page<Account> findByAccountType(
        Account.AccountType accountType,
        org.springframework.data.domain.Pageable pageable);

    /**
     * Find active accounts by type with pagination (P1-4)
     */
    org.springframework.data.domain.Page<Account> findByAccountTypeAndIsActiveTrue(
        Account.AccountType accountType,
        org.springframework.data.domain.Pageable pageable);

    /**
     * Find all active accounts with pagination (P1-4)
     */
    org.springframework.data.domain.Page<Account> findByIsActiveTrue(
        org.springframework.data.domain.Pageable pageable);
}