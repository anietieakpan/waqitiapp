/**
 * BNPL Application Repository
 * Repository interface for BNPL application data access
 */
package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.entity.BnplApplication;
import com.waqiti.bnpl.entity.BnplApplication.ApplicationStatus;
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
public interface BnplApplicationRepository extends JpaRepository<BnplApplication, UUID> {
    
    Optional<BnplApplication> findByApplicationNumber(String applicationNumber);
    
    Page<BnplApplication> findByUserId(UUID userId, Pageable pageable);
    
    Page<BnplApplication> findByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);
    
    List<BnplApplication> findByUserIdAndStatusIn(UUID userId, List<ApplicationStatus> statuses);
    
    @Query("SELECT a FROM BnplApplication a WHERE a.userId = :userId AND a.status IN ('ACTIVE', 'APPROVED')")
    List<BnplApplication> findActiveApplicationsByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(a.financedAmount) FROM BnplApplication a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    BigDecimal getTotalActiveFinancedAmount(@Param("userId") UUID userId);

    @Query(value = "SELECT COALESCE(SUM(financed_amount), 0) FROM bnpl_applications WHERE user_id = :userId AND status = 'ACTIVE' FOR UPDATE",
           nativeQuery = true)
    BigDecimal getTotalActiveFinancedAmountWithLock(@Param("userId") UUID userId);

    @Query("SELECT COUNT(a) FROM BnplApplication a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    Integer getActiveApplicationCount(@Param("userId") UUID userId);
    
    @Query("SELECT a FROM BnplApplication a WHERE a.status = 'PENDING' AND a.applicationDate < :cutoffDate")
    List<BnplApplication> findStaleApplications(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT a FROM BnplApplication a WHERE a.merchantId = :merchantId AND a.applicationDate BETWEEN :startDate AND :endDate")
    Page<BnplApplication> findByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    @Query("SELECT a FROM BnplApplication a WHERE a.status = 'APPROVED' AND a.firstPaymentDate = :date")
    List<BnplApplication> findApplicationsWithFirstPaymentDue(@Param("date") LocalDateTime date);
    
    @Query("SELECT AVG(a.creditScore) FROM BnplApplication a WHERE a.userId = :userId AND a.creditScore IS NOT NULL")
    Double getAverageCreditScore(@Param("userId") UUID userId);
    
    @Query("SELECT COUNT(a) FROM BnplApplication a WHERE a.userId = :userId AND a.status = 'REJECTED' AND a.decisionDate > :sinceDate")
    Integer getRecentRejectionCount(@Param("userId") UUID userId, @Param("sinceDate") LocalDateTime sinceDate);
    
    @Query("SELECT COUNT(a) FROM BnplApplication a WHERE a.userId = :userId AND a.status = 'DEFAULTED'")
    Integer getDefaultCount(@Param("userId") UUID userId);
    
    @Modifying
    @Query("UPDATE BnplApplication a SET a.status = :newStatus WHERE a.id = :applicationId AND a.status = :currentStatus")
    int updateStatus(@Param("applicationId") UUID applicationId, 
                     @Param("currentStatus") ApplicationStatus currentStatus,
                     @Param("newStatus") ApplicationStatus newStatus);
    
    @Query(value = "SELECT * FROM bnpl_applications WHERE user_id = :userId AND status = 'ACTIVE' FOR UPDATE", 
           nativeQuery = true)
    List<BnplApplication> findActiveApplicationsForUpdate(@Param("userId") UUID userId);
    
    boolean existsByUserIdAndOrderId(UUID userId, String orderId);
    
    @Query("SELECT new map(a.status as status, COUNT(a) as count) FROM BnplApplication a WHERE a.userId = :userId GROUP BY a.status")
    List<Object[]> getApplicationStatusCounts(@Param("userId") UUID userId);
}