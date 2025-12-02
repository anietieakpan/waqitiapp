package com.waqiti.payment.repository;

import com.waqiti.payment.entity.SanctionScreeningRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SanctionScreeningRecordRepository extends JpaRepository<SanctionScreeningRecord, UUID> {
    
    Optional<SanctionScreeningRecord> findByScreeningId(String screeningId);
    
    List<SanctionScreeningRecord> findByUserId(String userId);
    
    List<SanctionScreeningRecord> findByEntityId(String entityId);
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.userId = :userId ORDER BY s.screenedAt DESC")
    List<SanctionScreeningRecord> findRecentScreeningsByUserId(@Param("userId") String userId);
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.isSanctioned = true AND s.status = 'ACTIVE'")
    List<SanctionScreeningRecord> findAllActiveSanctionedRecords();
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.manualReviewRequired = true AND s.reviewedAt IS NULL")
    List<SanctionScreeningRecord> findPendingManualReviews();
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.status = :status")
    List<SanctionScreeningRecord> findByStatus(@Param("status") String status);
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.listType = :listType")
    List<SanctionScreeningRecord> findByListType(@Param("listType") String listType);
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.screenedAt >= :since")
    List<SanctionScreeningRecord> findScreeningsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SanctionScreeningRecord s WHERE s.isSanctioned = true AND s.screenedAt >= :since")
    long countSanctionedScreeningsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SanctionScreeningRecord s WHERE s.screenedAt >= :since")
    long countScreeningsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT s FROM SanctionScreeningRecord s WHERE s.expiresAt IS NOT NULL AND s.expiresAt <= :now AND s.status = 'ACTIVE'")
    List<SanctionScreeningRecord> findExpiredScreenings(@Param("now") LocalDateTime now);
}