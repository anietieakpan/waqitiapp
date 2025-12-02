package com.waqiti.common.messaging.recovery.repository;

import com.waqiti.common.messaging.recovery.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public interface DLQRecoveryRepository extends JpaRepository<DLQEvent, UUID> {

    List<DLQEvent> findByStatusAndNextRetryAtBefore(DLQStatus status, LocalDateTime dateTime);

    List<DLQEvent> findByServiceNameAndStatus(String serviceName, DLQStatus status);

    @Query("SELECT d FROM DLQEvent d WHERE d.priority = 'CRITICAL' AND d.status = 'PENDING' ORDER BY d.firstFailedAt ASC")
    List<DLQEvent> findCriticalPendingEvents();

    // Save recovery attempt (custom method implementation)
    default void saveRecoveryAttempt(DLQEvent event, RecoveryResult result) {
        event.setLastRetryAt(LocalDateTime.now());
        event.setStatus(DLQStatus.valueOf(result.getStatus()));
        save(event);
    }

    // Save manual review case
    default void saveManualReviewCase(ManualReviewCase reviewCase) {
        // Store in separate table or as JSONB metadata
    }

    // Save dead storage record
    default void saveDeadStorageRecord(DeadStorageRecord record) {
        // Archive to dead storage table
    }
}