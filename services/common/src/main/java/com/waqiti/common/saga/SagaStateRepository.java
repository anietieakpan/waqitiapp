package com.waqiti.common.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing saga state persistence
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
    List<SagaState> findBySagaType(String sagaType);

    /**
     * Find sagas by type and status
     */
    List<SagaState> findBySagaTypeAndStatus(String sagaType, SagaStatus status);

    /**
     * Find running sagas that have timed out
     */
    @Query("SELECT s FROM SagaState s WHERE s.status = 'RUNNING' AND s.timeoutAt < :currentTime")
    List<SagaState> findTimedOutSagas(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find sagas created within time range
     */
    @Query("SELECT s FROM SagaState s WHERE s.createdAt BETWEEN :startTime AND :endTime")
    List<SagaState> findSagasCreatedBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find sagas by correlation ID
     */
    List<SagaState> findByCorrelationId(String correlationId);

    /**
     * Find sagas created by user
     */
    List<SagaState> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find sagas with specific tag
     */
    @Query("SELECT s FROM SagaState s JOIN s.tags t WHERE t = :tag")
    List<SagaState> findByTag(@Param("tag") String tag);

    /**
     * Find sagas that need recovery (running for too long)
     */
    @Query("SELECT s FROM SagaState s WHERE s.status = 'RUNNING' AND s.lastUpdated < :cutoffTime")
    List<SagaState> findSagasNeedingRecovery(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count sagas by status
     */
    long countByStatus(SagaStatus status);

    /**
     * Count sagas by type and status
     */
    long countBySagaTypeAndStatus(String sagaType, SagaStatus status);

    /**
     * Find oldest running saga
     */
    Optional<SagaState> findFirstByStatusOrderByCreatedAtAsc(SagaStatus status);

    /**
     * Find sagas with failed steps
     */
    @Query("SELECT s FROM SagaState s WHERE s.sagaId IN " +
           "(SELECT DISTINCT ss.sagaId FROM SagaState ss WHERE FUNCTION('JSON_EXTRACT', ss.stepStates, '$[*].status') LIKE '%FAILED%')")
    List<SagaState> findSagasWithFailedSteps();

    /**
     * Delete completed sagas older than specified date
     */
    void deleteByStatusInAndCompletedAtBefore(List<SagaStatus> statuses, LocalDateTime cutoffDate);
}