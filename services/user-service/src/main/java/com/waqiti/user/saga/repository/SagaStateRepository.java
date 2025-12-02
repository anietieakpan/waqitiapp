package com.waqiti.user.saga.repository;

import com.waqiti.user.saga.SagaStatus;
import com.waqiti.user.saga.SagaType;
import com.waqiti.user.saga.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Saga State
 *
 * Provides queries for saga monitoring and recovery
 */
@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {

    /**
     * Find sagas by status
     */
    List<SagaState> findByStatus(SagaStatus status);

    /**
     * Find sagas by type
     */
    List<SagaState> findBySagaType(SagaType sagaType);

    /**
     * Find active sagas (not in terminal state)
     */
    @Query("SELECT s FROM SagaState s WHERE s.status IN ('STARTED', 'IN_PROGRESS', 'COMPENSATING') " +
           "ORDER BY s.createdAt DESC")
    List<SagaState> findActiveSagas();

    /**
     * Find failed sagas requiring manual intervention
     */
    @Query("SELECT s FROM SagaState s WHERE s.status = 'COMPENSATION_FAILED' " +
           "ORDER BY s.createdAt DESC")
    List<SagaState> findFailedSagasRequiringIntervention();

    /**
     * Find old active sagas (stuck/orphaned)
     */
    @Query("SELECT s FROM SagaState s WHERE s.status IN ('STARTED', 'IN_PROGRESS', 'COMPENSATING') " +
           "AND s.createdAt < :threshold ORDER BY s.createdAt ASC")
    List<SagaState> findStuckSagas(@Param("threshold") LocalDateTime threshold);

    /**
     * Count sagas by status
     */
    long countByStatus(SagaStatus status);

    /**
     * Get saga statistics
     */
    @Query("SELECT s.status, COUNT(s) FROM SagaState s GROUP BY s.status")
    List<Object[]> getSagaStatistics();

    /**
     * Find sagas created within time range
     */
    @Query("SELECT s FROM SagaState s WHERE s.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY s.createdAt DESC")
    List<SagaState> findByCreatedAtBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Delete old completed sagas (cleanup)
     */
    @Query("DELETE FROM SagaState s WHERE s.status IN ('COMPLETED', 'COMPENSATED') " +
           "AND s.completedAt < :retentionDate")
    void deleteCompletedSagasOlderThan(@Param("retentionDate") LocalDateTime retentionDate);
}
