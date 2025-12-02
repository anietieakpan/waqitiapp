package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessAccount;
import com.waqiti.business.domain.BusinessAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessAccountRepository extends JpaRepository<BusinessAccount, UUID> {
    
    Optional<BusinessAccount> findByOwnerId(UUID ownerId);
    
    boolean existsByOwnerId(UUID ownerId);
    
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
    
    Page<BusinessAccount> findByOwnerId(UUID ownerId, Pageable pageable);
    
    Page<BusinessAccount> findByStatus(BusinessAccountStatus status, Pageable pageable);
    
    List<BusinessAccount> findByStatusAndCreatedAtBefore(BusinessAccountStatus status, LocalDateTime dateTime);
    
    @Query("SELECT ba FROM BusinessAccount ba WHERE LOWER(ba.businessName) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(REPLACE(:name, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\_'), '%'))")
    Page<BusinessAccount> findByBusinessNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
    
    @Query("SELECT ba FROM BusinessAccount ba WHERE ba.industry = :industry")
    Page<BusinessAccount> findByIndustry(@Param("industry") String industry, Pageable pageable);
    
    @Query("SELECT ba FROM BusinessAccount ba WHERE " +
           "(:status IS NULL OR ba.status = :status) AND " +
           "(:industry IS NULL OR ba.industry = :industry) AND " +
           "(:businessName IS NULL OR LOWER(ba.businessName) LIKE LOWER(CONCAT('%', :businessName, '%')))")
    Page<BusinessAccount> findByFilters(@Param("status") BusinessAccountStatus status,
                                       @Param("industry") String industry,
                                       @Param("businessName") String businessName,
                                       Pageable pageable);
    
    @Query("SELECT COUNT(ba) FROM BusinessAccount ba WHERE ba.status = :status")
    Long countByStatus(@Param("status") BusinessAccountStatus status);
    
    @Query("SELECT COUNT(ba) FROM BusinessAccount ba WHERE ba.createdAt >= :startDate")
    Long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT SUM(ba.currentBalance) FROM BusinessAccount ba WHERE ba.status = 'ACTIVE'")
    BigDecimal getTotalActiveBalance();
    
    @Query("SELECT ba.industry, COUNT(ba) FROM BusinessAccount ba GROUP BY ba.industry")
    List<Object[]> getAccountCountByIndustry();
    
    @Query("SELECT ba FROM BusinessAccount ba WHERE ba.monthlyTransactionLimit < :amount")
    List<BusinessAccount> findAccountsWithLimitBelow(@Param("amount") BigDecimal amount);
    
    @Query("SELECT ba FROM BusinessAccount ba WHERE ba.riskLevel = 'HIGH' OR ba.riskLevel = 'CRITICAL'")
    List<BusinessAccount> findHighRiskAccounts();
    
    @Query("SELECT DISTINCT ba FROM BusinessAccount ba " +
           "LEFT JOIN BusinessTeamMember btm ON ba.id = btm.businessAccountId " +
           "WHERE ba.ownerId = :userId OR btm.userId = :userId")
    Page<BusinessAccount> findByUserAccess(@Param("userId") UUID userId, Pageable pageable);
}