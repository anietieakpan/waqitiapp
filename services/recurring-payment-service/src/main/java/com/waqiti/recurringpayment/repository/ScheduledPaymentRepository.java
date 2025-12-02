package com.waqiti.recurringpayment.repository;

import com.waqiti.recurringpayment.domain.ScheduledPayment;
import com.waqiti.recurringpayment.domain.ScheduledPaymentStatus;
import com.waqiti.recurringpayment.domain.PaymentExecutionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Repository for advanced scheduled payment operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Repository
public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, String> {
    
    /**
     * Find all payments due for execution.
     *
     * @param currentTime current time for comparison
     * @return list of due payments
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.status = 'ACTIVE' " +
           "AND sp.nextExecutionDate <= :currentTime " +
           "AND (sp.endDate IS NULL OR sp.endDate > :currentTime) " +
           "AND (sp.pausedUntil IS NULL OR sp.pausedUntil < :currentTime)")
    List<ScheduledPayment> findDuePayments(@Param("currentTime") Instant currentTime);
    
    /**
     * Find upcoming payments within a time window.
     *
     * @param startTime start of time window
     * @param endTime end of time window
     * @return list of upcoming payments
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.status = 'ACTIVE' " +
           "AND sp.nextExecutionDate BETWEEN :startTime AND :endTime " +
           "AND sp.reminderSettings.enabled = true " +
           "AND sp.reminderSent = false")
    List<ScheduledPayment> findUpcomingPayments(@Param("startTime") Instant startTime, 
                                               @Param("endTime") Instant endTime);
    
    /**
     * Find payments by user ID.
     *
     * @param userId user ID
     * @return list of user's payments
     */
    List<ScheduledPayment> findByUserId(String userId);
    
    /**
     * Find active payments by user ID.
     *
     * @param userId user ID
     * @param status payment status
     * @param pageable pagination
     * @return page of payments
     */
    Page<ScheduledPayment> findByUserIdAndStatus(String userId, ScheduledPaymentStatus status, Pageable pageable);
    
    /**
     * Find payments by recipient.
     *
     * @param recipientId recipient ID
     * @return list of payments to recipient
     */
    List<ScheduledPayment> findByRecipientId(String recipientId);
    
    /**
     * Find failed payments for retry.
     *
     * @param maxFailures maximum consecutive failures
     * @return list of failed payments eligible for retry
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.status = 'ACTIVE' " +
           "AND sp.consecutiveFailures > 0 " +
           "AND sp.consecutiveFailures < :maxFailures " +
           "AND sp.retrySettings.enabled = true")
    List<ScheduledPayment> findFailedPaymentsForRetry(@Param("maxFailures") int maxFailures);
    
    /**
     * Find payments needing suspension.
     *
     * @param maxFailures failure threshold for suspension
     * @return list of payments to suspend
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.status = 'ACTIVE' " +
           "AND sp.consecutiveFailures >= :maxFailures")
    List<ScheduledPayment> findPaymentsForSuspension(@Param("maxFailures") int maxFailures);
    
    /**
     * Calculate total scheduled amount for user.
     *
     * @param userId user ID
     * @param currency currency code
     * @return total scheduled amount
     */
    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM ScheduledPayment sp " +
           "WHERE sp.userId = :userId " +
           "AND sp.currency = :currency " +
           "AND sp.status = 'ACTIVE'")
    BigDecimal calculateTotalScheduledAmount(@Param("userId") String userId, 
                                            @Param("currency") String currency);
    
    /**
     * Get payment statistics for user.
     *
     * @param userId user ID
     * @return payment statistics
     */
    @Query("SELECT NEW com.waqiti.recurring.dto.PaymentStatistics(" +
           "COUNT(sp), " +
           "SUM(CASE WHEN sp.status = 'ACTIVE' THEN 1 ELSE 0 END), " +
           "SUM(sp.successfulExecutions), " +
           "SUM(sp.failedExecutions), " +
           "SUM(sp.totalAmountPaid)) " +
           "FROM ScheduledPayment sp WHERE sp.userId = :userId")
    PaymentStatistics getPaymentStatistics(@Param("userId") String userId);
    
    /**
     * Find payments by status.
     *
     * @param statuses set of statuses
     * @param pageable pagination
     * @return page of payments
     */
    Page<ScheduledPayment> findByStatusIn(Set<ScheduledPaymentStatus> statuses, Pageable pageable);
    
    /**
     * Update next execution date for payment.
     *
     * @param paymentId payment ID
     * @param nextExecutionDate next execution date
     */
    @Modifying
    @Query("UPDATE ScheduledPayment sp SET sp.nextExecutionDate = :nextDate " +
           "WHERE sp.id = :paymentId")
    void updateNextExecutionDate(@Param("paymentId") String paymentId, 
                                @Param("nextDate") Instant nextExecutionDate);
    
    /**
     * Reset reminder sent flag for payments.
     */
    @Modifying
    @Query("UPDATE ScheduledPayment sp SET sp.reminderSent = false " +
           "WHERE sp.status = 'ACTIVE' AND sp.reminderSettings.enabled = true")
    void resetReminderFlags();
    
    /**
     * Find payments with upcoming execution in time range.
     *
     * @param userId user ID
     * @param startTime start time
     * @param endTime end time
     * @return list of upcoming payments
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.userId = :userId " +
           "AND sp.status = 'ACTIVE' " +
           "AND sp.nextExecutionDate BETWEEN :startTime AND :endTime " +
           "ORDER BY sp.nextExecutionDate ASC")
    List<ScheduledPayment> findUpcomingPaymentsForUser(@Param("userId") String userId,
                                                       @Param("startTime") Instant startTime,
                                                       @Param("endTime") Instant endTime);
    
    /**
     * Find payments by multiple IDs.
     *
     * @param paymentIds set of payment IDs
     * @return list of payments
     */
    List<ScheduledPayment> findAllById(Set<String> paymentIds);
    
    /**
     * Get execution history for a payment.
     *
     * @param paymentId payment ID
     * @return list of execution records
     */
    @Query(value = "SELECT * FROM payment_execution_records WHERE scheduled_payment_id = :paymentId " +
                   "ORDER BY execution_time DESC LIMIT 100", nativeQuery = true)
    List<PaymentExecutionRecord> findExecutionHistory(@Param("paymentId") String paymentId);
    
    /**
     * Save execution record.
     *
     * @param record execution record
     */
    @Modifying
    @Query(value = "INSERT INTO payment_execution_records " +
                   "(id, scheduled_payment_id, execution_time, status, amount, currency, " +
                   "transaction_id, error_message) " +
                   "VALUES (:#{#record.id}, :#{#record.scheduledPaymentId}, " +
                   ":#{#record.executionTime}, :#{#record.status}, :#{#record.amount}, " +
                   ":#{#record.currency}, :#{#record.transactionId}, :#{#record.errorMessage})",
           nativeQuery = true)
    void saveExecutionRecord(@Param("record") PaymentExecutionRecord record);
    
    /**
     * Find paused payments ready to resume.
     *
     * @param currentTime current time
     * @return list of payments to resume
     */
    @Query("SELECT sp FROM ScheduledPayment sp WHERE sp.status = 'PAUSED' " +
           "AND sp.pausedUntil IS NOT NULL " +
           "AND sp.pausedUntil <= :currentTime")
    List<ScheduledPayment> findPaymentsToResume(@Param("currentTime") Instant currentTime);
    
    /**
     * Count active payments for user.
     *
     * @param userId user ID
     * @return count of active payments
     */
    long countByUserIdAndStatus(String userId, ScheduledPaymentStatus status);
    
    /**
     * Check if user has payment to recipient.
     *
     * @param userId user ID
     * @param recipientId recipient ID
     * @param status payment status
     * @return true if payment exists
     */
    boolean existsByUserIdAndRecipientIdAndStatus(String userId, String recipientId, 
                                                 ScheduledPaymentStatus status);
}