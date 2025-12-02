package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessSubAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessSubAccountRepository extends JpaRepository<BusinessSubAccount, UUID> {
    
    List<BusinessSubAccount> findByMainAccountId(UUID mainAccountId);
    
    List<BusinessSubAccount> findByMainAccountIdAndIsActiveTrue(UUID mainAccountId);
    
    Optional<BusinessSubAccount> findByAccountNumber(String accountNumber);
    
    boolean existsByAccountNumber(String accountNumber);
    
    long countByMainAccountId(UUID mainAccountId);
    
    long countByMainAccountIdAndIsActiveTrue(UUID mainAccountId);
    
    Page<BusinessSubAccount> findByMainAccountId(UUID mainAccountId, Pageable pageable);
    
    @Query("SELECT sa FROM BusinessSubAccount sa WHERE sa.mainAccountId = :mainAccountId AND " +
           "(:accountType IS NULL OR sa.accountType = :accountType) AND " +
           "(:isActive IS NULL OR sa.isActive = :isActive)")
    Page<BusinessSubAccount> findByFilters(@Param("mainAccountId") UUID mainAccountId,
                                          @Param("accountType") String accountType,
                                          @Param("isActive") Boolean isActive,
                                          Pageable pageable);
    
    @Query("SELECT SUM(sa.currentBalance) FROM BusinessSubAccount sa " +
           "WHERE sa.mainAccountId = :mainAccountId AND sa.isActive = true")
    BigDecimal getTotalBalanceByMainAccount(@Param("mainAccountId") UUID mainAccountId);
    
    @Query("SELECT sa.accountType, COUNT(sa) FROM BusinessSubAccount sa " +
           "WHERE sa.mainAccountId = :mainAccountId GROUP BY sa.accountType")
    List<Object[]> getSubAccountCountByType(@Param("mainAccountId") UUID mainAccountId);
    
    @Query("SELECT sa FROM BusinessSubAccount sa WHERE sa.spendingLimit < :amount")
    List<BusinessSubAccount> findAccountsWithLimitBelow(@Param("amount") BigDecimal amount);
}