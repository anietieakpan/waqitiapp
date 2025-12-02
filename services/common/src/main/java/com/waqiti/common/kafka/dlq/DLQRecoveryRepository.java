package com.waqiti.common.kafka.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DLQ recovery records.
 */
@Repository
public interface DLQRecoveryRepository extends JpaRepository<DLQRecoveryRecord, String> {

    Optional<DLQRecoveryRecord> findByMessageKey(String messageKey);

    List<DLQRecoveryRecord> findByStatus(RecoveryStatus status);

    List<DLQRecoveryRecord> findByOriginalTopic(String topic);

    @Query("SELECT r FROM DLQRecoveryRecord r WHERE r.status = 'PENDING_RETRY' AND r.nextRetryAt <= :now")
    List<DLQRecoveryRecord> findPendingRetries(LocalDateTime now);

    @Query("SELECT r FROM DLQRecoveryRecord r WHERE r.status = 'MANUAL_INTERVENTION_REQUIRED'")
    List<DLQRecoveryRecord> findRequiringManualIntervention();

    @Query("SELECT COUNT(r) FROM DLQRecoveryRecord r WHERE r.originalTopic = :topic AND r.status = 'RECOVERED'")
    long countRecoveredByTopic(String topic);

    @Query("SELECT COUNT(r) FROM DLQRecoveryRecord r WHERE r.originalTopic = :topic AND r.status = 'MANUAL_INTERVENTION_REQUIRED'")
    long countManualInterventionByTopic(String topic);
}
