package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.AccountType;
import com.waqiti.accounting.domain.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Chart of Accounts operations
 */
@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, String> {

    /**
     * Find account by code
     */
    Optional<ChartOfAccounts> findByCode(String code);

    /**
     * Check if account code exists
     */
    boolean existsByCode(String code);

    /**
     * Find all active accounts
     */
    List<ChartOfAccounts> findByIsActiveTrue();

    /**
     * Find active accounts alias for service usage
     */
    default List<ChartOfAccounts> findAllActive() {
        return findByIsActiveTrue();
    }

    /**
     * Find accounts by type
     */
    List<ChartOfAccounts> findByType(AccountType type);

    /**
     * Find accounts by type and active status
     */
    List<ChartOfAccounts> findByTypeAndIsActiveTrue(AccountType type);

    /**
     * Find system accounts
     */
    List<ChartOfAccounts> findByIsSystemAccountTrue();

    /**
     * Find child accounts
     */
    List<ChartOfAccounts> findByParentCode(String parentCode);

    /**
     * Find accounts by category
     */
    List<ChartOfAccounts> findByCategory(String category);

    /**
     * Find accounts by currency
     */
    List<ChartOfAccounts> findByCurrency(String currency);

    /**
     * Find accounts in code range
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.code BETWEEN :startCode AND :endCode AND c.isActive = true ORDER BY c.code")
    List<ChartOfAccounts> findByCodeRange(@Param("startCode") String startCode,
                                         @Param("endCode") String endCode);

    /**
     * Find accounts by type and code range
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE c.type = :type AND c.code BETWEEN :startCode AND :endCode AND c.isActive = true ORDER BY c.code")
    List<ChartOfAccounts> findByTypeAndCodeRange(@Param("type") AccountType type,
                                                 @Param("startCode") String startCode,
                                                 @Param("endCode") String endCode);

    /**
     * Search accounts by name - FIXED SQL injection vulnerability
     * Uses proper escaping of wildcards to prevent SQL injection through search terms
     */
    @Query("SELECT c FROM ChartOfAccounts c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:searchTerm, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')) " +
           "AND c.isActive = true")
    List<ChartOfAccounts> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Count accounts by type
     */
    long countByTypeAndIsActiveTrue(AccountType type);

    /**
     * Find parent accounts (accounts that have children)
     */
    @Query("SELECT DISTINCT a FROM ChartOfAccounts a " +
           "WHERE EXISTS (SELECT 1 FROM ChartOfAccounts child WHERE child.parentCode = a.code)")
    List<ChartOfAccounts> findParentAccounts();
}