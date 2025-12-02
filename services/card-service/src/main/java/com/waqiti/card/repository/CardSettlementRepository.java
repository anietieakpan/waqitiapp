package com.waqiti.card.repository;

import com.waqiti.card.entity.CardSettlement;
import com.waqiti.card.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardSettlementRepository - Spring Data JPA repository for CardSettlement entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardSettlementRepository extends JpaRepository<CardSettlement, UUID>, JpaSpecificationExecutor<CardSettlement> {

    Optional<CardSettlement> findBySettlementId(String settlementId);

    Optional<CardSettlement> findByTransactionId(UUID transactionId);

    List<CardSettlement> findByCardId(UUID cardId);

    List<CardSettlement> findBySettlementStatus(SettlementStatus status);

    List<CardSettlement> findByBatchId(String batchId);

    @Query("SELECT s FROM CardSettlement s WHERE s.settlementStatus = 'PENDING' AND s.deletedAt IS NULL")
    List<CardSettlement> findPendingSettlements();

    @Query("SELECT s FROM CardSettlement s WHERE s.settlementStatus = 'FAILED' AND s.retryCount < s.maxRetryCount AND s.deletedAt IS NULL")
    List<CardSettlement> findFailedSettlementsForRetry();

    @Query("SELECT s FROM CardSettlement s WHERE s.settlementDate BETWEEN :startDate AND :endDate AND s.deletedAt IS NULL")
    List<CardSettlement> findBySettlementDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM CardSettlement s WHERE s.reconciliationStatus = 'DISCREPANCY' AND s.deletedAt IS NULL")
    List<CardSettlement> findSettlementsWithDiscrepancies();

    @Query("SELECT s FROM CardSettlement s WHERE s.isReconciled = false AND s.settlementStatus = 'SETTLED' AND s.deletedAt IS NULL")
    List<CardSettlement> findUnreconciledSettlements();
}
