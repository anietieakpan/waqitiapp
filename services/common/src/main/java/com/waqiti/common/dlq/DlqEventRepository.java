package com.waqiti.common.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DLQ Event persistence and querying
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Repository
public interface DlqEventRepository extends JpaRepository<DlqEvent, Long> {

    Optional<DlqEvent> findByEventId(String eventId);

    List<DlqEvent> findByStatus(DlqEvent.DlqEventStatus status);

    List<DlqEvent> findByServiceName(String serviceName);

    List<DlqEvent> findByServiceNameAndStatus(String serviceName, DlqEvent.DlqEventStatus status);

    @Query("SELECT d FROM DlqEvent d WHERE d.status = :status AND d.nextRetryAt <= :now")
    List<DlqEvent> findEventsReadyForRetry(
        @Param("status") DlqEvent.DlqEventStatus status,
        @Param("now") Instant now
    );

    @Query("SELECT d FROM DlqEvent d WHERE d.status = com.waqiti.common.dlq.DlqEvent.DlqEventStatus.MANUAL_REVIEW " +
           "AND d.severity >= :minSeverity ORDER BY d.severity DESC, d.createdAt ASC")
    List<DlqEvent> findHighSeverityManualReviewEvents(@Param("minSeverity") Double minSeverity);

    @Query("SELECT d FROM DlqEvent d WHERE d.recoveryStrategy = :strategy AND d.status = :status")
    List<DlqEvent> findByRecoveryStrategyAndStatus(
        @Param("strategy") DlqEvent.RecoveryStrategy strategy,
        @Param("status") DlqEvent.DlqEventStatus status
    );

    @Query("SELECT COUNT(d) FROM DlqEvent d WHERE d.serviceName = :serviceName AND d.status = :status")
    Long countByServiceNameAndStatus(
        @Param("serviceName") String serviceName,
        @Param("status") DlqEvent.DlqEventStatus status
    );

    @Query("SELECT d FROM DlqEvent d WHERE d.correlationId = :correlationId")
    List<DlqEvent> findByCorrelationId(@Param("correlationId") String correlationId);

    @Query("SELECT d FROM DlqEvent d WHERE d.assignedTo = :userId AND d.status = com.waqiti.common.dlq.DlqEvent.DlqEventStatus.MANUAL_REVIEW")
    List<DlqEvent> findAssignedToUser(@Param("userId") String userId);

    @Query("SELECT d.serviceName, COUNT(d) FROM DlqEvent d WHERE d.status = :status GROUP BY d.serviceName")
    List<Object[]> countByStatusGroupByService(@Param("status") DlqEvent.DlqEventStatus status);

    List<DlqEvent> findByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT d FROM DlqEvent d WHERE d.status = com.waqiti.common.dlq.DlqEvent.DlqEventStatus.RETRY_SCHEDULED " +
           "AND d.retryCount < d.maxRetries AND d.nextRetryAt <= :now")
    List<DlqEvent> findEligibleForRetry(@Param("now") Instant now);
}
