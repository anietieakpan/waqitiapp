package com.waqiti.ledger.repository;

import com.waqiti.ledger.entity.AccountEntity;
import com.waqiti.ledger.entity.AccountEntity.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Account Entity
 * 
 * Provides data access operations for ledger accounts with
 * optimized queries for financial reporting and analysis.
 */
@Repository
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    // Basic queries
    Optional<AccountEntity> findByAccountCode(String accountCode);
    
    Optional<AccountEntity> findByAccountCodeAndCompanyId(String accountCode, UUID companyId);
    
    List<AccountEntity> findByAccountType(AccountType accountType);
    
    List<AccountEntity> findByAccountTypeIn(List<AccountType> accountTypes);
    
    List<AccountEntity> findByIsActiveTrue();
    
    List<AccountEntity> findByIsActiveTrueAndAllowsTransactionsTrue();
    
    // Hierarchical queries
    List<AccountEntity> findByParentAccountIsNull();
    
    List<AccountEntity> findByParentAccountAccountId(UUID parentAccountId);
    
    @Query("SELECT a FROM AccountEntity a WHERE a.parentAccount IS NULL AND a.companyId = :companyId")
    List<AccountEntity> findRootAccountsByCompany(@Param("companyId") UUID companyId);
    
    // Search queries
    List<AccountEntity> findByAccountNameContainingIgnoreCase(String accountName);
    
    @Query("SELECT a FROM AccountEntity a WHERE " +
           "LOWER(a.accountCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(a.accountName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<AccountEntity> searchAccounts(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Type-based queries with active filter
    List<AccountEntity> findByAccountTypeAndIsActiveTrue(AccountType accountType);
    
    List<AccountEntity> findByAccountTypeInAndIsActiveTrue(List<AccountType> accountTypes);
    
    @Query("SELECT a FROM AccountEntity a WHERE a.accountType IN :types AND a.isActive = true " +
           "AND a.companyId = :companyId ORDER BY a.accountCode")
    List<AccountEntity> findActiveAccountsByTypesAndCompany(
        @Param("types") List<AccountType> types,
        @Param("companyId") UUID companyId
    );
    
    // Balance queries
    @Query("SELECT a FROM AccountEntity a WHERE a.currentBalance != 0 AND a.isActive = true")
    List<AccountEntity> findAccountsWithNonZeroBalance();
    
    @Query("SELECT a FROM AccountEntity a WHERE a.accountType = :type AND a.currentBalance < 0")
    List<AccountEntity> findAccountsWithNegativeBalance(@Param("type") AccountType type);
    
    // Aggregation queries
    @Query("SELECT SUM(a.currentBalance) FROM AccountEntity a WHERE a.accountType = :type " +
           "AND a.isActive = true AND a.companyId = :companyId")
    BigDecimal getTotalBalanceByTypeAndCompany(
        @Param("type") AccountType type,
        @Param("companyId") UUID companyId
    );
    
    @Query("SELECT a.accountType, SUM(a.currentBalance) FROM AccountEntity a " +
           "WHERE a.isActive = true AND a.companyId = :companyId " +
           "GROUP BY a.accountType")
    List<Object[]> getBalancesByAccountType(@Param("companyId") UUID companyId);
    
    // Company-specific queries
    List<AccountEntity> findByCompanyId(UUID companyId);
    
    List<AccountEntity> findByCompanyIdAndIsActiveTrue(UUID companyId);
    
    Page<AccountEntity> findByCompanyIdAndIsActiveTrue(UUID companyId, Pageable pageable);
    
    // Department and cost center queries
    List<AccountEntity> findByDepartmentId(UUID departmentId);
    
    List<AccountEntity> findByCostCenterId(UUID costCenterId);
    
    // Update operations
    @Modifying
    @Query("UPDATE AccountEntity a SET a.currentBalance = :balance, a.updatedAt = :updatedAt " +
           "WHERE a.accountId = :accountId")
    void updateAccountBalance(
        @Param("accountId") UUID accountId,
        @Param("balance") BigDecimal balance,
        @Param("updatedAt") LocalDateTime updatedAt
    );
    
    @Modifying
    @Query("UPDATE AccountEntity a SET a.isActive = false WHERE a.accountId IN :accountIds")
    void deactivateAccounts(@Param("accountIds") List<UUID> accountIds);
    
    // Chart of accounts queries
    @Query("SELECT a FROM AccountEntity a WHERE a.companyId = :companyId " +
           "ORDER BY a.accountType, a.accountCode")
    List<AccountEntity> getChartOfAccounts(@Param("companyId") UUID companyId);
    
    @Query("SELECT DISTINCT a.accountType FROM AccountEntity a WHERE a.companyId = :companyId")
    List<AccountType> getDistinctAccountTypes(@Param("companyId") UUID companyId);
    
    // Financial reporting queries
    @Query(value = "SELECT a.* FROM accounts a " +
           "WHERE a.account_type IN ('REVENUE', 'OPERATING_REVENUE') " +
           "AND a.company_id = :companyId " +
           "AND EXISTS (SELECT 1 FROM ledger_entries le " +
           "WHERE le.account_id = a.account_id " +
           "AND le.transaction_date BETWEEN :startDate AND :endDate)",
           nativeQuery = true)
    List<AccountEntity> findRevenueAccountsWithActivity(
        @Param("companyId") UUID companyId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query(value = "SELECT a.* FROM accounts a " +
           "WHERE a.account_type IN ('EXPENSE', 'OPERATING_EXPENSE', 'COST_OF_GOODS_SOLD') " +
           "AND a.company_id = :companyId " +
           "AND EXISTS (SELECT 1 FROM ledger_entries le " +
           "WHERE le.account_id = a.account_id " +
           "AND le.transaction_date BETWEEN :startDate AND :endDate)",
           nativeQuery = true)
    List<AccountEntity> findExpenseAccountsWithActivity(
        @Param("companyId") UUID companyId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Validation queries
    boolean existsByAccountCode(String accountCode);
    
    boolean existsByAccountCodeAndCompanyId(String accountCode, UUID companyId);
    
    @Query("SELECT COUNT(a) FROM AccountEntity a WHERE a.parentAccount.accountId = :parentId")
    long countChildAccounts(@Param("parentId") UUID parentId);
}