package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.domain.BnplInstallment;
import com.waqiti.bnpl.domain.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for BNPL installments
 */
@Repository
public interface BnplInstallmentRepository extends JpaRepository<BnplInstallment, Long> {

    /**
     * Find installments by plan ID
     */
    List<BnplInstallment> findByBnplPlanIdOrderByInstallmentNumber(Long planId);

    /**
     * Find installments due on a specific date
     */
    List<BnplInstallment> findByDueDate(LocalDate dueDate);

    /**
     * Find installments due within a date range
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "WHERE i.dueDate BETWEEN :startDate AND :endDate " +
           "AND i.status IN ('SCHEDULED', 'DUE')")
    List<BnplInstallment> findInstallmentsDueInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find overdue installments
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "WHERE i.dueDate < :currentDate " +
           "AND i.status IN ('DUE', 'OVERDUE') " +
           "AND i.amountDue > 0")
    List<BnplInstallment> findOverdueInstallments(@Param("currentDate") LocalDate currentDate);

    /**
     * Find installments for reminder notification
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "WHERE i.dueDate = :reminderDate " +
           "AND i.reminderSent = false " +
           "AND i.status IN ('SCHEDULED', 'DUE')")
    List<BnplInstallment> findByDueDateAndReminderSentFalse(
            @Param("reminderDate") LocalDate reminderDate);

    /**
     * Find overdue installments without notification
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "WHERE i.dueDate < :currentDate " +
           "AND i.status = 'OVERDUE' " +
           "AND i.overdueNotificationSent = false")
    List<BnplInstallment> findOverdueWithoutNotification(
            @Param("currentDate") LocalDate currentDate);

    /**
     * Find installments by status
     */
    List<BnplInstallment> findByStatus(InstallmentStatus status);

    /**
     * Count paid installments for a plan
     */
    @Query("SELECT COUNT(i) FROM BnplInstallment i " +
           "WHERE i.bnplPlan.id = :planId " +
           "AND i.status = 'PAID'")
    long countPaidInstallmentsByPlanId(@Param("planId") Long planId);

    /**
     * Find next due installment for a plan
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "WHERE i.bnplPlan.id = :planId " +
           "AND i.status IN ('SCHEDULED', 'DUE', 'OVERDUE') " +
           "ORDER BY i.installmentNumber ASC " +
           "LIMIT 1")
    BnplInstallment findNextDueInstallment(@Param("planId") Long planId);

    /**
     * Find installments by user ID and due date after specified date
     * Used for payment history analysis in credit scoring
     */
    @Query("SELECT i FROM BnplInstallment i " +
           "JOIN i.bnplPlan p " +
           "WHERE p.userId = :userId " +
           "AND i.dueDate >= :afterDate " +
           "ORDER BY i.dueDate DESC")
    List<InstallmentPayment> findByUserIdAndDueDateAfter(
            @Param("userId") java.util.UUID userId,
            @Param("afterDate") java.time.LocalDateTime afterDate);

    /**
     * Inner class for installment payment data transfer
     */
    interface InstallmentPayment {
        java.time.LocalDateTime getDueDate();
        java.time.LocalDateTime getPaidAt();
        String getStatus();
    }
}