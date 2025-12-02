package com.waqiti.common.eventsourcing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for event snapshots
 */
@Repository
public interface EventSnapshotRepository extends JpaRepository<EventSnapshotEntity, Long> {
    
    /**
     * Find latest snapshot for aggregate
     */
    Optional<EventSnapshotEntity> findTopByAggregateIdOrderByVersionDesc(String aggregateId);
    
    /**
     * Find snapshots by aggregate type
     */
    List<EventSnapshotEntity> findByAggregateTypeOrderByTimestampDesc(String aggregateType);
    
    /**
     * Find snapshots before timestamp (for cleanup)
     */
    List<EventSnapshotEntity> findByTimestampBefore(Instant cutoffTime);
    
    /**
     * Delete old snapshots
     */
    void deleteByTimestampBefore(Instant cutoffTime);
    
    /**
     * Get snapshot count by aggregate type
     */
    @Query("SELECT s.aggregateType, COUNT(s) FROM EventSnapshotEntity s GROUP BY s.aggregateType")
    List<Object[]> getSnapshotCountsByAggregateType();
    
    /**
     * Find snapshots with data size greater than threshold
     */
    List<EventSnapshotEntity> findByDataSizeGreaterThanOrderByDataSizeDesc(Long threshold);
}