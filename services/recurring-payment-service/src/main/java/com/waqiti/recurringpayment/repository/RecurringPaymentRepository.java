package com.waqiti.recurringpayment.repository;

import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.domain.RecurringStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, String>, JpaSpecificationExecutor<RecurringPayment> {
    
    Optional<RecurringPayment> findByIdAndUserId(String id, String userId);
    
    Page<RecurringPayment> findByUserId(String userId, Pageable pageable);
    
    Page<RecurringPayment> findByUserIdAndStatus(String userId, RecurringStatus status, Pageable pageable);
    
    List<RecurringPayment> findByUserIdAndStatusIn(String userId, List<RecurringStatus> statuses);
    
    long countByUserIdAndStatus(String userId, RecurringStatus status);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.status = :status " +
           "AND r.nextExecutionDate BETWEEN :start AND :end")
    List<RecurringPayment> findDueForExecution(@Param("start") Instant start, 
                                             @Param("end") Instant end, 
                                             @Param("status") RecurringStatus status);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.status = :status " +
           "AND r.reminderEnabled = true")
    List<RecurringPayment> findActiveWithRemindersEnabled(@Param("status") RecurringStatus status);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.status = :status " +
           "AND r.reminderEnabled = true")
    List<RecurringPayment> findActiveWithRemindersEnabled();
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.consecutiveFailures >= :threshold " +
           "AND r.status = :status")
    List<RecurringPayment> findWithExcessiveFailures(@Param("threshold") int threshold, 
                                                     @Param("status") RecurringStatus status);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.userId = :userId " +
           "AND r.status IN :statuses " +
           "AND r.createdAt BETWEEN :startDate AND :endDate")
    List<RecurringPayment> findUserRecurringBetweenDates(@Param("userId") String userId,
                                                        @Param("statuses") List<RecurringStatus> statuses,
                                                        @Param("startDate") Instant startDate,
                                                        @Param("endDate") Instant endDate);
    
    @Query("SELECT SUM(r.amount) FROM RecurringPayment r WHERE r.userId = :userId " +
           "AND r.status = :status AND r.currency = :currency")
    BigDecimal calculateTotalCommitmentByUserAndCurrency(@Param("userId") String userId,
                                                        @Param("status") RecurringStatus status,
                                                        @Param("currency") String currency);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.nextExecutionDate BETWEEN :start AND :end " +
           "AND r.userId = :userId AND r.status = :status")
    List<RecurringPayment> findUpcomingPayments(@Param("userId") String userId,
                                               @Param("status") RecurringStatus status,
                                               @Param("start") Instant start,
                                               @Param("end") Instant end);
    
    @Query("SELECT COUNT(r) FROM RecurringPayment r WHERE r.recipientId = :recipientId " +
           "AND r.status IN :statuses")
    long countByRecipientAndStatuses(@Param("recipientId") String recipientId,
                                    @Param("statuses") List<RecurringStatus> statuses);
    
    @Query("SELECT DISTINCT r.currency FROM RecurringPayment r WHERE r.userId = :userId")
    List<String> findDistinctCurrenciesByUser(@Param("userId") String userId);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.endDate IS NOT NULL " +
           "AND r.endDate < :now AND r.status = :status")
    List<RecurringPayment> findExpiredRecurring(@Param("now") Instant now, 
                                               @Param("status") RecurringStatus status);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.maxOccurrences IS NOT NULL " +
           "AND r.successfulExecutions >= r.maxOccurrences AND r.status = :status")
    List<RecurringPayment> findCompletedByMaxOccurrences(@Param("status") RecurringStatus status);
    
    @Modifying
    @Query("UPDATE RecurringPayment r SET r.status = :status, r.updatedAt = :now " +
           "WHERE r.id = :id")
    void updateStatus(@Param("id") String id, 
                     @Param("status") RecurringStatus status, 
                     @Param("now") Instant now);
    
    @Query("SELECT r.frequency, COUNT(r) FROM RecurringPayment r WHERE r.userId = :userId " +
           "AND r.status = :status GROUP BY r.frequency")
    List<Object[]> getFrequencyDistribution(@Param("userId") String userId, 
                                           @Param("status") RecurringStatus status);
    
    @Query(value = "SELECT DATE_TRUNC('month', created_at) as month, " +
           "COUNT(*) as count, SUM(total_amount_paid) as total " +
           "FROM recurring_payments " +
           "WHERE user_id = :userId AND status = 'COMPLETED' " +
           "AND created_at >= :since " +
           "GROUP BY DATE_TRUNC('month', created_at) " +
           "ORDER BY month DESC", nativeQuery = true)
    List<Object[]> getMonthlyStats(@Param("userId") String userId, 
                                  @Param("since") Instant since);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.status = :status " +
           "AND r.nextExecutionDate BETWEEN :start AND :end " +
           "ORDER BY r.nextExecutionDate ASC")
    List<RecurringPayment> findDueForExecutionWithPagination(@Param("start") Instant start,
                                                            @Param("end") Instant end,
                                                            @Param("status") RecurringStatus status,
                                                            @Param("limit") int limit,
                                                            @Param("offset") int offset);
    
    List<RecurringPayment> findByUserId(String userId);
    
    Page<RecurringPayment> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    @Query("SELECT r FROM RecurringPayment r WHERE " +
           "(r.endDate IS NOT NULL AND r.endDate < :now) OR " +
           "(r.maxOccurrences IS NOT NULL AND r.successfulExecutions >= r.maxOccurrences) " +
           "AND r.status = 'ACTIVE'")
    List<RecurringPayment> findPaymentsToComplete(@Param("now") Instant now);
    
    @Query("SELECT r FROM RecurringPayment r WHERE r.nextExecutionDate BETWEEN :start AND :end " +
           "AND r.status = 'ACTIVE'")
    List<RecurringPayment> findUpcomingPayments(@Param("start") Instant start,
                                               @Param("end") Instant end);
}