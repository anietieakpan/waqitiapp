package com.waqiti.common.eventsourcing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for financial events
 */
@Repository
public interface FinancialEventRepository extends JpaRepository<FinancialEventEntity, Long> {
    
    /**
     * Find events by aggregate ID and version greater than or equal to specified version
     */
    List<FinancialEventEntity> findByAggregateIdAndEventVersionGreaterThanEqualOrderBySequenceNumber(
        String aggregateId, Long fromVersion);
    
    /**
     * Find events by aggregate ID ordered by sequence number
     */
    List<FinancialEventEntity> findByAggregateIdOrderBySequenceNumber(String aggregateId);
    
    /**
     * Find events by time range
     */
    List<FinancialEventEntity> findByTimestampBetweenOrderByTimestampAsc(
        Instant fromTime, Instant toTime, Pageable pageable);
    
    /**
     * Find events by event type
     */
    List<FinancialEventEntity> findByEventTypeOrderByTimestampDesc(
        String eventType, Pageable pageable);
    
    /**
     * Find events by correlation ID
     */
    List<FinancialEventEntity> findByCorrelationIdOrderByTimestamp(String correlationId);
    
    /**
     * Find events by user ID within time range
     */
    List<FinancialEventEntity> findByUserIdAndTimestampBetweenOrderByTimestamp(
        String userId, Instant fromTime, Instant toTime);
    
    /**
     * Find events by aggregate type
     */
    List<FinancialEventEntity> findByAggregateTypeOrderByTimestamp(
        String aggregateType, Pageable pageable);
    
    /**
     * Get maximum version for aggregate
     */
    @Query("SELECT MAX(e.eventVersion) FROM FinancialEventEntity e WHERE e.aggregateId = :aggregateId")
    Long findMaxVersionByAggregateId(@Param("aggregateId") String aggregateId);
    
    /**
     * Get maximum sequence number for aggregate
     */
    @Query("SELECT MAX(e.sequenceNumber) FROM FinancialEventEntity e WHERE e.aggregateId = :aggregateId")
    Long findMaxSequenceNumberByAggregateId(@Param("aggregateId") String aggregateId);
    
    /**
     * Count events by aggregate
     */
    long countByAggregateId(String aggregateId);
    
    /**
     * Count distinct aggregate IDs
     */
    @Query("SELECT COUNT(DISTINCT e.aggregateId) FROM FinancialEventEntity e")
    long countDistinctAggregateIds();
    
    /**
     * Find events after specific sequence number for aggregate
     */
    List<FinancialEventEntity> findByAggregateIdAndSequenceNumberGreaterThanOrderBySequenceNumber(
        String aggregateId, Long sequenceNumber);
    
    /**
     * Get event counts by event type
     */
    @Query("SELECT e.eventType, COUNT(e) FROM FinancialEventEntity e GROUP BY e.eventType")
    List<Object[]> getEventCountsByType();
    
    /**
     * Get event counts by aggregate type
     */
    @Query("SELECT e.aggregateType, COUNT(e) FROM FinancialEventEntity e GROUP BY e.aggregateType")
    List<Object[]> getEventCountsByAggregateType();
    
    /**
     * Find events by multiple correlation IDs
     */
    List<FinancialEventEntity> findByCorrelationIdInOrderByTimestamp(List<String> correlationIds);
    
    /**
     * Get recent events for monitoring
     */
    List<FinancialEventEntity> findTop100ByOrderByTimestampDesc();
    
    /**
     * Delete old events (for data retention)
     */
    void deleteByTimestampBefore(Instant cutoffTime);
    
    /**
     * Find events by causation ID
     */
    List<FinancialEventEntity> findByCausationIdOrderByTimestamp(String causationId);
}