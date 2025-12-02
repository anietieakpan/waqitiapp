package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.ReconciliationItem;
import com.waqiti.reconciliation.domain.ReconciliationItem.ReconciliationItemType;
import com.waqiti.reconciliation.domain.ReconciliationItem.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ReconciliationItemJpaRepository - JPA repository for ReconciliationItem entities
 *
 * Provides data access operations with optimized queries for reconciliation matching.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Repository
public interface ReconciliationItemJpaRepository extends JpaRepository<ReconciliationItem, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReconciliationItem r WHERE r.id = :id AND r.deleted = false")
    Optional<ReconciliationItem> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT r FROM ReconciliationItem r WHERE r.reconciliationBatchId = :batchId AND r.deleted = false")
    List<ReconciliationItem> findByReconciliationBatchId(@Param("batchId") String batchId);

    @Query("SELECT r FROM ReconciliationItem r WHERE r.status = :status AND r.deleted = false")
    Page<ReconciliationItem> findByStatus(@Param("status") ReconciliationStatus status, Pageable pageable);

    @Query("SELECT r FROM ReconciliationItem r WHERE r.reconciliationBatchId = :batchId " +
           "AND r.status = :status AND r.deleted = false")
    List<ReconciliationItem> findByBatchIdAndStatus(
        @Param("batchId") String batchId,
        @Param("status") ReconciliationStatus status
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE r.itemType = :itemType " +
           "AND r.status = :status AND r.deleted = false")
    List<ReconciliationItem> findByItemTypeAndStatus(
        @Param("itemType") ReconciliationItemType itemType,
        @Param("status") ReconciliationStatus status
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE r.sourceSystem = :sourceSystem " +
           "AND r.externalReferenceId = :externalId AND r.deleted = false")
    Optional<ReconciliationItem> findBySourceSystemAndExternalId(
        @Param("sourceSystem") String sourceSystem,
        @Param("externalId") String externalReferenceId
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE r.internalReferenceId = :internalId AND r.deleted = false")
    Optional<ReconciliationItem> findByInternalReferenceId(@Param("internalId") String internalReferenceId);

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.amount BETWEEN :minAmount AND :maxAmount " +
           "AND r.currency = :currency " +
           "AND r.transactionDate BETWEEN :startDate AND :endDate " +
           "AND r.status = :status " +
           "AND r.deleted = false")
    List<ReconciliationItem> findPotentialMatches(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("currency") String currency,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("status") ReconciliationStatus status
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.reconciliationBatchId = :batchId " +
           "AND r.status = 'PENDING' " +
           "AND r.deleted = false " +
           "ORDER BY r.transactionDate ASC")
    List<ReconciliationItem> findUnmatchedItemsInBatch(@Param("batchId") String batchId);

    @Query("SELECT COUNT(r) FROM ReconciliationItem r WHERE " +
           "r.reconciliationBatchId = :batchId AND r.status = :status AND r.deleted = false")
    Long countByBatchIdAndStatus(
        @Param("batchId") String batchId,
        @Param("status") ReconciliationStatus status
    );

    @Query("SELECT SUM(r.amount) FROM ReconciliationItem r WHERE " +
           "r.reconciliationBatchId = :batchId AND r.status = :status AND r.deleted = false")
    BigDecimal sumAmountByBatchIdAndStatus(
        @Param("batchId") String batchId,
        @Param("status") ReconciliationStatus status
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.reconciledAt BETWEEN :startDate AND :endDate AND r.deleted = false")
    List<ReconciliationItem> findReconciledItemsByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.discrepancyId = :discrepancyId AND r.deleted = false")
    List<ReconciliationItem> findByDiscrepancyId(@Param("discrepancyId") String discrepancyId);

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.matchedItemId = :matchedItemId AND r.deleted = false")
    Optional<ReconciliationItem> findByMatchedItemId(@Param("matchedItemId") String matchedItemId);

    boolean existsByInternalReferenceIdAndDeleted(String internalReferenceId, boolean deleted);

    @Query("SELECT r FROM ReconciliationItem r WHERE " +
           "r.createdAt < :cutoffDate " +
           "AND r.status IN ('MATCHED', 'RESOLVED', 'EXCLUDED') " +
           "AND r.deleted = false")
    List<ReconciliationItem> findItemsEligibleForArchiving(@Param("cutoffDate") LocalDateTime cutoffDate);
}
