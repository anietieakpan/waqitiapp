package com.waqiti.account.repository;

import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountNumberPattern;
import com.waqiti.account.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountNumberPatternRepository extends JpaRepository<AccountNumberPattern, String> {
    
    /**
     * Find active patterns by account type
     */
    List<AccountNumberPattern> findByAccountTypeAndActiveTrue(Account.AccountType accountType);
    
    /**
     * Find active patterns by account type and region
     */
    Optional<AccountNumberPattern> findByAccountTypeAndRegionAndActiveTrue(
            Account.AccountType accountType, Region region);
    
    /**
     * Find all active patterns
     */
    List<AccountNumberPattern> findByActiveTrue();
    
    /**
     * Find patterns by account type, ordered by priority
     */
    @Query("SELECT p FROM AccountNumberPattern p WHERE p.accountType = :accountType AND p.active = true ORDER BY p.priority DESC, p.createdAt ASC")
    List<AccountNumberPattern> findByAccountTypeOrderedByPriority(@Param("accountType") Account.AccountType accountType);
    
    /**
     * Find the highest priority pattern for account type and region
     */
    @Query("SELECT p FROM AccountNumberPattern p WHERE p.accountType = :accountType AND " +
           "(:region IS NULL OR p.region = :region) AND p.active = true " +
           "ORDER BY p.priority DESC, p.createdAt ASC")
    Optional<AccountNumberPattern> findHighestPriorityPattern(
            @Param("accountType") Account.AccountType accountType, 
            @Param("region") Region region);
    
    /**
     * Find patterns by region
     */
    List<AccountNumberPattern> findByRegionAndActiveTrue(Region region);
    
    /**
     * Find patterns by prefix
     */
    List<AccountNumberPattern> findByPrefixAndActiveTrue(String prefix);
    
    /**
     * Check if pattern exists for account type and region
     */
    boolean existsByAccountTypeAndRegionAndActiveTrue(Account.AccountType accountType, Region region);
    
    /**
     * Find patterns created by specific user
     */
    List<AccountNumberPattern> findByCreatedByAndActiveTrue(String createdBy);
    
    /**
     * Find patterns that support specific currency
     */
    List<AccountNumberPattern> findByCurrencyCodeAndActiveTrue(String currencyCode);
    
    /**
     * Count active patterns by account type
     */
    long countByAccountTypeAndActiveTrue(Account.AccountType accountType);
    
    /**
     * Find all patterns for admin management (including inactive)
     */
    @Query("SELECT p FROM AccountNumberPattern p ORDER BY p.accountType, p.priority DESC, p.createdAt DESC")
    List<AccountNumberPattern> findAllForAdmin();
    
    /**
     * Find patterns that need validation (e.g., patterns without validation regex)
     */
    @Query("SELECT p FROM AccountNumberPattern p WHERE p.active = true AND " +
           "(p.validationRegex IS NULL OR p.validationRegex = '')")
    List<AccountNumberPattern> findPatternsNeedingValidation();
    
    /**
     * Find duplicate patterns (same pattern string)
     */
    @Query("SELECT p FROM AccountNumberPattern p WHERE p.pattern = :pattern AND p.active = true")
    List<AccountNumberPattern> findDuplicatePatterns(@Param("pattern") String pattern);
    
    /**
     * Search patterns by description
     */
    @Query("SELECT p FROM AccountNumberPattern p WHERE p.active = true AND " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<AccountNumberPattern> searchByDescription(@Param("searchTerm") String searchTerm);
}