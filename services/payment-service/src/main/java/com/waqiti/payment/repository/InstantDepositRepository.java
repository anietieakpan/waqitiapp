package com.waqiti.payment.repository;

import com.waqiti.payment.entity.InstantDeposit;
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

/**
 * Repository for instant deposits
 */
@Repository
public interface InstantDepositRepository extends JpaRepository<InstantDeposit, UUID> {

    /**
     * Check if an instant deposit exists for the given ACH transfer ID
     */
    boolean existsByAchTransferId(UUID achTransferId);

    /**
     * Sum daily instant deposits for a user starting from a specific time
     */
    @Query("SELECT COALESCE(SUM(d.originalAmount), 0) FROM InstantDeposit d " +
           "WHERE d.userId = :userId " +
           "AND d.createdAt >= :startOfDay " +
           "AND d.status NOT IN ('FAILED', 'CANCELLED')")
    Optional<BigDecimal> sumDailyInstantDeposits(@Param("userId") UUID userId, 
                                                @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Find instant deposits by user ID ordered by creation date descending
     */
    List<InstantDeposit> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Sum fees collected within a date range
     */
    @Query("SELECT COALESCE(SUM(d.feeAmount), 0) FROM InstantDeposit d " +
           "WHERE d.createdAt >= :startDate " +
           "AND d.createdAt <= :endDate " +
           "AND d.status = 'COMPLETED'")
    Optional<BigDecimal> sumFeesCollected(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
}