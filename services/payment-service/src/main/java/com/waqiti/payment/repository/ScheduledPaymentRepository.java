/**
 * Scheduled Payment Repository
 * Data access layer for scheduled payments
 */
package com.waqiti.payment.repository;

import com.waqiti.payment.entity.ScheduledPayment;
import com.waqiti.payment.entity.ScheduledPayment.ScheduledPaymentStatus;
import com.waqiti.payment.entity.ScheduledPayment.ScheduleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, UUID> {
    
    /**
     * Find scheduled payments by user ID
     */
    Page<ScheduledPayment> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find scheduled payments by user ID and status
     */
    Page<ScheduledPayment> findByUserIdAndStatus(
            UUID userId, 
            ScheduledPaymentStatus status, 
            Pageable pageable
    );
    
    /**
     * Find due payments that need to be processed
     */
    @Query("SELECT sp FROM ScheduledPayment sp " +
           "WHERE sp.status = 'ACTIVE' " +
           "AND sp.nextPaymentDate <= :today " +
           "AND (sp.preferredTime IS NULL OR sp.preferredTime <= :currentTime) " +
           "ORDER BY sp.preferredTime ASC")
    List<ScheduledPayment> findDuePayments(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime
    );
    
    /**
     * Find payments that need reminders
     */
    @Query("SELECT sp FROM ScheduledPayment sp " +
           "WHERE sp.status = 'ACTIVE' " +
           "AND sp.sendReminder = true " +
           "AND sp.nextPaymentDate = :reminderDate")
    List<ScheduledPayment> findPaymentsNeedingReminders(
            @Param("reminderDate") LocalDate reminderDate
    );
    
    /**
     * Check if similar scheduled payment exists
     */
    boolean existsByUserIdAndRecipientIdAndAmountAndStatusAndScheduleType(
            UUID userId,
            UUID recipientId,
            BigDecimal amount,
            ScheduledPaymentStatus status,
            ScheduleType scheduleType
    );
    
    /**
     * Find scheduled payments by recipient
     */
    List<ScheduledPayment> findByRecipientIdAndStatus(
            UUID recipientId,
            ScheduledPaymentStatus status
    );
    
    /**
     * Count active scheduled payments for user
     */
    long countByUserIdAndStatus(UUID userId, ScheduledPaymentStatus status);
    
    /**
     * Find scheduled payments expiring soon
     */
    @Query("SELECT sp FROM ScheduledPayment sp " +
           "WHERE sp.status = 'ACTIVE' " +
           "AND sp.endDate IS NOT NULL " +
           "AND sp.endDate BETWEEN :startDate AND :endDate")
    List<ScheduledPayment> findExpiringSoon(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    /**
     * Get total scheduled amount for user
     */
    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM ScheduledPayment sp " +
           "WHERE sp.userId = :userId " +
           "AND sp.status = 'ACTIVE' " +
           "AND sp.currency = :currency")
    BigDecimal getTotalScheduledAmount(
            @Param("userId") UUID userId,
            @Param("currency") String currency
    );
}