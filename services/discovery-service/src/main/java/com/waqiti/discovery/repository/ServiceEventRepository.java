package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.EventLevel;
import com.waqiti.discovery.domain.EventType;
import com.waqiti.discovery.domain.ServiceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceEvent entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceEventRepository extends JpaRepository<ServiceEvent, UUID> {

    Optional<ServiceEvent> findByEventId(String eventId);

    List<ServiceEvent> findByServiceId(String serviceId);

    List<ServiceEvent> findByInstanceId(String instanceId);

    List<ServiceEvent> findByEventType(EventType eventType);

    List<ServiceEvent> findByEventLevel(EventLevel eventLevel);

    List<ServiceEvent> findByProcessed(Boolean processed);

    List<ServiceEvent> findByServiceIdAndEventType(String serviceId, EventType eventType);

    List<ServiceEvent> findByEventTimestampAfter(Instant startTime);

    @Query("SELECT e FROM ServiceEvent e WHERE e.serviceId = :serviceId " +
        "AND e.eventTimestamp BETWEEN :startTime AND :endTime " +
        "ORDER BY e.eventTimestamp DESC")
    List<ServiceEvent> findEventsByServiceInTimeRange(
        @Param("serviceId") String serviceId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @Query("SELECT e FROM ServiceEvent e WHERE e.eventLevel IN ('CRITICAL', 'ERROR') " +
        "AND e.processed = false ORDER BY e.eventTimestamp DESC")
    List<ServiceEvent> findUnprocessedCriticalEvents();

    @Query("SELECT e FROM ServiceEvent e WHERE e.correlationId = :correlationId " +
        "ORDER BY e.eventTimestamp ASC")
    List<ServiceEvent> findByCorrelationIdOrderByTimestamp(
        @Param("correlationId") String correlationId
    );

    @Modifying
    @Query("DELETE FROM ServiceEvent e WHERE e.eventTimestamp < :cutoffTime")
    int deleteOldEvents(@Param("cutoffTime") Instant cutoffTime);

    long countByEventType(EventType eventType);

    long countByEventLevel(EventLevel eventLevel);

    long countByServiceIdAndEventTimestampAfter(String serviceId, Instant startTime);
}
