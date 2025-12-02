package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.HealthStatus;
import com.waqiti.discovery.domain.ServiceHealth;
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
 * Repository for ServiceHealth entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceHealthRepository extends JpaRepository<ServiceHealth, UUID> {

    /**
     * Find health check by check ID
     *
     * @param checkId check ID
     * @return optional service health
     */
    Optional<ServiceHealth> findByCheckId(String checkId);

    /**
     * Find all health checks by service ID
     *
     * @param serviceId service ID
     * @return list of service healths
     */
    List<ServiceHealth> findByServiceId(String serviceId);

    /**
     * Find all health checks by instance ID
     *
     * @param instanceId instance ID
     * @return list of service healths
     */
    List<ServiceHealth> findByInstanceId(String instanceId);

    /**
     * Find latest health check by service ID
     *
     * @param serviceId service ID
     * @return optional service health
     */
    Optional<ServiceHealth> findFirstByServiceIdOrderByCheckTimeDesc(String serviceId);

    /**
     * Find health checks by service ID and status
     *
     * @param serviceId service ID
     * @param status health status
     * @return list of service healths
     */
    List<ServiceHealth> findByServiceIdAndStatus(String serviceId, HealthStatus status);

    /**
     * Find health checks after a certain time
     *
     * @param serviceId service ID
     * @param startTime start time
     * @return list of service healths
     */
    List<ServiceHealth> findByServiceIdAndCheckTimeAfter(String serviceId, Instant startTime);

    /**
     * Find health checks in time range
     *
     * @param serviceId service ID
     * @param startTime start time
     * @return list of service healths ordered by check time
     */
    @Query("SELECT h FROM ServiceHealth h WHERE h.serviceId = :serviceId " +
        "AND h.checkTime >= :startTime ORDER BY h.checkTime ASC")
    List<ServiceHealth> findByServiceIdAndCheckTimeAfterOrderByCheckTimeAsc(
        @Param("serviceId") String serviceId,
        @Param("startTime") Instant startTime
    );

    /**
     * Count health checks by status
     *
     * @param status health status
     * @return count
     */
    long countByStatus(HealthStatus status);

    /**
     * Delete old health check records
     *
     * @param cutoffTime cutoff time
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM ServiceHealth h WHERE h.checkTime < :cutoffTime")
    int deleteByCheckTimeBefore(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find failing health checks
     *
     * @param threshold failure threshold
     * @return list of failing health checks
     */
    @Query("SELECT h FROM ServiceHealth h WHERE h.status IN ('UNHEALTHY', 'DEGRADED') " +
        "ORDER BY h.checkTime DESC")
    List<ServiceHealth> findFailingHealthChecks();
}
