package com.waqiti.recurringpayment.repository;

import com.waqiti.recurringpayment.domain.ExecutionStatus;
import com.waqiti.recurringpayment.domain.RecurringExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RecurringExecutionRepository extends JpaRepository<RecurringExecution, String> {
    
    Page<RecurringExecution> findByRecurringPaymentIdOrderByExecutedAtDesc(String recurringPaymentId, Pageable pageable);
    
    List<RecurringExecution> findByRecurringPaymentId(String recurringPaymentId);
    
    @Query("SELECT e FROM RecurringExecution e WHERE e.status = :status " +
           "AND e.retryAt IS NOT NULL AND e.retryAt <= :now")
    List<RecurringExecution> findDueForRetry(@Param("status") ExecutionStatus status, 
                                           @Param("now") Instant now);
    
    @Query("SELECT e FROM RecurringExecution e WHERE e.recurringPaymentId = :recurringPaymentId " +
           "AND e.status = :status ORDER BY e.createdAt DESC")
    List<RecurringExecution> findByRecurringPaymentIdAndStatus(@Param("recurringPaymentId") String recurringPaymentId,
                                                              @Param("status") ExecutionStatus status);
    
    @Query("SELECT COUNT(e) FROM RecurringExecution e WHERE e.recurringPaymentId = :recurringPaymentId " +
           "AND e.status = :status")
    long countByRecurringPaymentIdAndStatus(@Param("recurringPaymentId") String recurringPaymentId,
                                           @Param("status") ExecutionStatus status);
    
    @Query("SELECT e FROM RecurringExecution e WHERE e.recurringPaymentId = :recurringPaymentId " +
           "AND e.executedAt BETWEEN :start AND :end")
    List<RecurringExecution> findExecutionsBetweenDates(@Param("recurringPaymentId") String recurringPaymentId,
                                                       @Param("start") Instant start,
                                                       @Param("end") Instant end);
    
    @Query("SELECT e FROM RecurringExecution e WHERE e.status = :status " +
           "AND e.createdAt < :cutoff")
    List<RecurringExecution> findStaleExecutions(@Param("status") ExecutionStatus status,
                                                @Param("cutoff") Instant cutoff);
    
    @Query("SELECT AVG(e.processingTimeMs) FROM RecurringExecution e WHERE e.status = :status " +
           "AND e.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTime(@Param("status") ExecutionStatus status);
    
    @Query("SELECT e.status, COUNT(e) FROM RecurringExecution e " +
           "WHERE e.recurringPaymentId = :recurringPaymentId GROUP BY e.status")
    List<Object[]> getExecutionStatusDistribution(@Param("recurringPaymentId") String recurringPaymentId);
    
    @Query(value = "SELECT DATE_TRUNC('day', executed_at) as day, " +
           "COUNT(*) as count, " +
           "COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as successful " +
           "FROM recurring_executions " +
           "WHERE executed_at >= :since " +
           "GROUP BY DATE_TRUNC('day', executed_at) " +
           "ORDER BY day DESC", nativeQuery = true)
    List<Object[]> getDailyExecutionStats(@Param("since") Instant since);
    
    @Query("SELECT e FROM RecurringExecution e WHERE e.status = 'FAILED' " +
           "AND e.retryAt IS NOT NULL AND e.retryAt <= :now " +
           "AND e.attemptCount < 3")
    List<RecurringExecution> findFailedExecutionsDueForRetry(@Param("now") Instant now);
    
    @Modifying
    @Query("DELETE FROM RecurringExecution e WHERE e.executedAt < :cutoffDate")
    int deleteOldExecutions(@Param("cutoffDate") Instant cutoffDate);
}