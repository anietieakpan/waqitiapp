package com.waqiti.merchant.repository;

import com.waqiti.merchant.domain.Merchant;
import com.waqiti.merchant.domain.MerchantStatus;
import com.waqiti.merchant.domain.MerchantCategory;
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

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    
    Optional<Merchant> findByMerchantCode(String merchantCode);
    
    Optional<Merchant> findByBusinessRegistrationNumber(String businessRegistrationNumber);
    
    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);
    
    Page<Merchant> findByCategory(MerchantCategory category, Pageable pageable);
    
    Page<Merchant> findByStatusAndCategory(MerchantStatus status, MerchantCategory category, Pageable pageable);
    
    @Query("SELECT m FROM Merchant m WHERE " +
           "(:status IS NULL OR m.status = :status) AND " +
           "(:category IS NULL OR m.category = :category) AND " +
           "(:searchTerm IS NULL OR LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(m.merchantCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Merchant> findByFilters(@Param("status") MerchantStatus status,
                                @Param("category") MerchantCategory category,
                                @Param("searchTerm") String searchTerm,
                                Pageable pageable);
    
    List<Merchant> findByOnboardedDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.status = :status")
    Long countByStatus(@Param("status") MerchantStatus status);
    
    @Query("SELECT m.category, COUNT(m) FROM Merchant m GROUP BY m.category")
    List<Object[]> countByCategory();
    
    @Query("SELECT m FROM Merchant m WHERE m.status = 'ACTIVE' " +
           "AND m.lastTransactionDate IS NOT NULL " +
           "AND m.lastTransactionDate < :date")
    List<Merchant> findInactiveMerchants(@Param("date") LocalDateTime date);
    
    @Query("SELECT m FROM Merchant m WHERE m.status = 'PENDING_APPROVAL' " +
           "AND m.onboardedDate < :date")
    List<Merchant> findPendingApprovalMerchants(@Param("date") LocalDateTime date);
    
    @Query("SELECT SUM(m.totalTransactionVolume) FROM Merchant m WHERE m.status = 'ACTIVE'")
    BigDecimal getTotalActiveMerchantVolume();
    
    @Query("SELECT m FROM Merchant m WHERE m.status = 'ACTIVE' " +
           "ORDER BY m.totalTransactionVolume DESC")
    Page<Merchant> findTopMerchantsByVolume(Pageable pageable);
    
    @Query("SELECT m FROM Merchant m WHERE m.status = 'ACTIVE' " +
           "ORDER BY m.totalTransactionCount DESC")
    Page<Merchant> findTopMerchantsByTransactionCount(Pageable pageable);
    
    @Modifying
    @Query("UPDATE Merchant m SET m.status = :status, m.lastModifiedDate = :date " +
           "WHERE m.id = :merchantId")
    void updateMerchantStatus(@Param("merchantId") UUID merchantId,
                             @Param("status") MerchantStatus status,
                             @Param("date") LocalDateTime date);
    
    @Modifying
    @Query("UPDATE Merchant m SET m.totalTransactionVolume = m.totalTransactionVolume + :amount, " +
           "m.totalTransactionCount = m.totalTransactionCount + 1, " +
           "m.lastTransactionDate = :date " +
           "WHERE m.id = :merchantId")
    void updateTransactionStats(@Param("merchantId") UUID merchantId,
                               @Param("amount") BigDecimal amount,
                               @Param("date") LocalDateTime date);
    
    @Query("SELECT m FROM Merchant m WHERE m.settlementSchedule = :schedule " +
           "AND m.status = 'ACTIVE'")
    List<Merchant> findMerchantsForSettlement(@Param("schedule") String schedule);
    
    @Query("SELECT m FROM Merchant m WHERE m.riskScore > :threshold " +
           "AND m.status = 'ACTIVE'")
    List<Merchant> findHighRiskMerchants(@Param("threshold") BigDecimal threshold);
    
    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
    
    boolean existsByMerchantCode(String merchantCode);
    
    @Query("SELECT m FROM Merchant m WHERE m.contactEmail = :email")
    Optional<Merchant> findByContactEmail(@Param("email") String email);
    
    @Query("SELECT m FROM Merchant m WHERE m.apiKey = :apiKey AND m.status = 'ACTIVE'")
    Optional<Merchant> findByApiKey(@Param("apiKey") String apiKey);
}