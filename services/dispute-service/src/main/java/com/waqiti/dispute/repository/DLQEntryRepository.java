package com.waqiti.dispute.repository;

import com.waqiti.dispute.entity.DLQEntry;
import com.waqiti.dispute.entity.DLQStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DLQ Entry management
 */
@Repository
public interface DLQEntryRepository extends JpaRepository<DLQEntry, String> {

    Optional<DLQEntry> findByEventId(String eventId);

    List<DLQEntry> findByStatus(DLQStatus status);

    List<DLQEntry> findBySourceTopic(String sourceTopic);

    @Query("SELECT d FROM DLQEntry d WHERE d.status = :status AND d.retryCount < d.maxRetries")
    List<DLQEntry> findRetriableEntries(@Param("status") DLQStatus status);

    @Query("SELECT d FROM DLQEntry d WHERE d.status = 'RETRY_SCHEDULED' AND d.lastRetryAt < :beforeTime")
    List<DLQEntry> findEntriesReadyForRetry(@Param("beforeTime") LocalDateTime beforeTime);

    @Query("SELECT d FROM DLQEntry d WHERE d.createdAt < :beforeTime AND d.status IN ('RESOLVED', 'DISCARDED')")
    List<DLQEntry> findOldResolvedEntries(@Param("beforeTime") LocalDateTime beforeTime);

    long countByStatus(DLQStatus status);
}
