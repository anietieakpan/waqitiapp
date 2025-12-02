package com.waqiti.transaction.saga.repository;

import com.waqiti.transaction.saga.domain.SagaExecution;
import com.waqiti.transaction.saga.domain.SagaExecution.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL: Repository for saga persistence and crash recovery
 */
@Repository
public interface SagaExecutionRepository extends JpaRepository<SagaExecution, UUID> {

    /**
     * Find saga by transaction ID
     */
    Optional<SagaExecution> findByTransactionId(UUID transactionId);

    /**
     * Find all sagas with specific status
     */
    List<SagaExecution> findByStatus(SagaStatus status);

    /**
     * Find sagas that need recovery (RUNNING or COMPENSATING that haven't been updated recently)
     */
    @Query("SELECT s FROM SagaExecution s WHERE " +
           "(s.status = 'RUNNING' OR s.status = 'COMPENSATING') AND " +
           "s.lastUpdatedAt < :staleSince")
    List<SagaExecution> findStaleSagas(@Param("staleSince") LocalDateTime staleSince);

    /**
     * Find sagas that have timed out
     */
    @Query("SELECT s FROM SagaExecution s WHERE " +
           "s.status IN ('RUNNING', 'COMPENSATING') AND " +
           "s.timeoutAt IS NOT NULL AND s.timeoutAt < :now")
    List<SagaExecution> findTimedOutSagas(@Param("now") LocalDateTime now);

    /**
     * Find sagas by type and status
     */
    List<SagaExecution> findBySagaTypeAndStatus(String sagaType, SagaStatus status);

    /**
     * Find sagas created within time range
     */
    @Query("SELECT s FROM SagaExecution s WHERE " +
           "s.createdAt BETWEEN :start AND :end")
    List<SagaExecution> findByCreatedAtBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Count sagas by status
     */
    long countByStatus(SagaStatus status);

    /**
     * Delete completed sagas older than retention period
     */
    @Query("DELETE FROM SagaExecution s WHERE " +
           "s.status IN ('COMPLETED', 'COMPENSATED') AND " +
           "s.completedAt < :cutoffDate")
    int deleteCompletedSagasOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
