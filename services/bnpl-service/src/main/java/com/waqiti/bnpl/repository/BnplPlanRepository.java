package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.domain.BnplPlan;
import com.waqiti.bnpl.domain.enums.BnplPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for BNPL plans
 */
@Repository
public interface BnplPlanRepository extends JpaRepository<BnplPlan, Long> {

    /**
     * Find BNPL plan by plan number
     */
    Optional<BnplPlan> findByPlanNumber(String planNumber);

    /**
     * Find all plans for a user
     */
    Page<BnplPlan> findByUserId(String userId, Pageable pageable);

    /**
     * Find plans by user and status
     */
    List<BnplPlan> findByUserIdAndStatus(String userId, BnplPlanStatus status);

    /**
     * Find plans by merchant
     */
    Page<BnplPlan> findByMerchantId(String merchantId, Pageable pageable);

    /**
     * Find overdue plans
     */
    @Query("SELECT p FROM BnplPlan p " +
           "JOIN p.installments i " +
           "WHERE p.status = 'ACTIVE' " +
           "AND i.status IN ('DUE', 'OVERDUE') " +
           "AND i.dueDate < :currentDate " +
           "GROUP BY p")
    List<BnplPlan> findOverduePlans(@Param("currentDate") LocalDate currentDate);

    /**
     * Find plans expiring soon
     */
    @Query("SELECT p FROM BnplPlan p " +
           "WHERE p.status = 'ACTIVE' " +
           "AND p.lastPaymentDate BETWEEN :startDate AND :endDate")
    List<BnplPlan> findPlansExpiringSoon(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Count active plans for a user
     */
    @Query("SELECT COUNT(p) FROM BnplPlan p " +
           "WHERE p.userId = :userId " +
           "AND p.status = 'ACTIVE'")
    long countActiveUserPlans(@Param("userId") String userId);

    /**
     * Calculate total outstanding amount for a user
     */
    @Query("SELECT COALESCE(SUM(p.remainingBalance), 0) FROM BnplPlan p " +
           "WHERE p.userId = :userId " +
           "AND p.status = 'ACTIVE'")
    BigDecimal getTotalOutstandingAmount(@Param("userId") String userId);

    /**
     * Find plans by status
     */
    Page<BnplPlan> findByStatus(BnplPlanStatus status, Pageable pageable);

    /**
     * Find completed plans within date range
     */
    @Query("SELECT p FROM BnplPlan p " +
           "WHERE p.status = 'COMPLETED' " +
           "AND p.completedAt BETWEEN :startDate AND :endDate")
    List<BnplPlan> findCompletedPlansInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}