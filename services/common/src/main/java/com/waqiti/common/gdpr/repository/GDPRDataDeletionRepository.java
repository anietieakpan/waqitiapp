// ========================================
// FILE 2: GDPRDataDeletionRepository.java
// Location: services/common/src/main/java/com/waqiti/common/gdpr/repository/
// ========================================
package com.waqiti.common.gdpr.repository;

import com.waqiti.common.gdpr.model.GDPRDataDeletionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GDPRDataDeletionRepository extends JpaRepository<GDPRDataDeletionResult, UUID> {
    List<GDPRDataDeletionResult> findByUserIdOrderByRequestedAtDesc(UUID userId);
    Optional<GDPRDataDeletionResult> findByDeletionRequestId(String deletionRequestId);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'PENDING_APPROVAL' ORDER BY d.requestedAt ASC")
    List<GDPRDataDeletionResult> findPendingApprovalDeletions();

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'APPROVED' ORDER BY d.approvedAt ASC")
    List<GDPRDataDeletionResult> findApprovedDeletions();

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'PROCESSING' ORDER BY d.startedAt ASC")
    List<GDPRDataDeletionResult> findProcessingDeletions();

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.userId = :userId AND d.status IN ('COMPLETED', 'PARTIAL') ORDER BY d.completedAt DESC")
    List<GDPRDataDeletionResult> findCompletedDeletions(@Param("userId") UUID userId);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'FAILED' AND d.retryCount < :maxRetries ORDER BY d.requestedAt ASC")
    List<GDPRDataDeletionResult> findFailedDeletionsForRetry(@Param("maxRetries") int maxRetries);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'ON_LEGAL_HOLD' ORDER BY d.requestedAt DESC")
    List<GDPRDataDeletionResult> findDeletionsOnLegalHold();

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM GDPRDataDeletionResult d WHERE d.userId = :userId AND d.status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING')")
    boolean hasPendingDeletionRequest(@Param("userId") UUID userId);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.userId = :userId ORDER BY d.requestedAt DESC")
    Optional<GDPRDataDeletionResult> findMostRecentDeletion(@Param("userId") UUID userId);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.requestedAt BETWEEN :startDate AND :endDate")
    List<GDPRDataDeletionResult> findDeletionsRequestedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT d.status, COUNT(d) FROM GDPRDataDeletionResult d GROUP BY d.status")
    List<Object[]> countDeletionsByStatus();

    @Modifying
    @Query("UPDATE GDPRDataDeletionResult d SET d.status = :status, d.updatedAt = :now WHERE d.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") GDPRDataDeletionResult.DeletionStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'PROCESSING' AND d.startedAt < :threshold")
    List<GDPRDataDeletionResult> findStaleDeletions(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.status = 'ANONYMIZED' AND d.anonymizedAt < :threshold")
    List<GDPRDataDeletionResult> findAnonymizedRecordsForHardDeletion(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(d) as total, SUM(d.totalRecordsDeleted) as totalDeleted, SUM(d.totalRecordsAnonymized) as totalAnonymized, SUM(d.totalRecordsRetained) as totalRetained FROM GDPRDataDeletionResult d WHERE d.status IN ('COMPLETED', 'PARTIAL')")
    Object[] calculateDeletionMetrics();

    @Query("SELECT d FROM GDPRDataDeletionResult d WHERE d.requestedBy = :requesterType ORDER BY d.requestedAt DESC")
    List<GDPRDataDeletionResult> findByRequesterType(@Param("requesterType") String requesterType);

    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, d.startedAt, d.completedAt)) FROM GDPRDataDeletionResult d WHERE d.status IN ('COMPLETED', 'PARTIAL') AND d.startedAt IS NOT NULL AND d.completedAt IS NOT NULL")
    Double calculateAverageProcessingTimeSeconds();
}
