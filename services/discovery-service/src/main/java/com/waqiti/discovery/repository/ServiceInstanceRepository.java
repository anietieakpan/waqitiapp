package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.HealthStatus;
import com.waqiti.discovery.domain.ServiceInstance;
import com.waqiti.discovery.domain.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceInstance entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceInstanceRepository extends JpaRepository<ServiceInstance, UUID> {

    /**
     * Find instance by instance ID
     *
     * @param instanceId instance ID
     * @return optional service instance
     */
    Optional<ServiceInstance> findByInstanceId(String instanceId);

    /**
     * Find all instances by service ID
     *
     * @param serviceId service ID
     * @return list of service instances
     */
    List<ServiceInstance> findByServiceId(String serviceId);

    /**
     * Find all instances by instance status
     *
     * @param instanceStatus instance status
     * @return list of service instances
     */
    List<ServiceInstance> findByInstanceStatus(ServiceStatus instanceStatus);

    /**
     * Find all instances by health status
     *
     * @param healthStatus health status
     * @return list of service instances
     */
    List<ServiceInstance> findByHealthStatus(HealthStatus healthStatus);

    /**
     * Find all healthy instances for a service
     *
     * @param serviceId service ID
     * @param isHealthy health flag
     * @return list of healthy service instances
     */
    List<ServiceInstance> findByServiceIdAndIsHealthy(String serviceId, Boolean isHealthy);

    /**
     * Find all instances by service ID and instance status
     *
     * @param serviceId service ID
     * @param instanceStatus instance status
     * @return list of service instances
     */
    List<ServiceInstance> findByServiceIdAndInstanceStatus(String serviceId, ServiceStatus instanceStatus);

    /**
     * Find instances not seen since cutoff time
     *
     * @param cutoffTime cutoff time
     * @return list of service instances
     */
    List<ServiceInstance> findByLastSeenBefore(Instant cutoffTime);

    /**
     * Count instances by service ID
     *
     * @param serviceId service ID
     * @return count
     */
    long countByServiceId(String serviceId);

    /**
     * Count healthy instances by service ID
     *
     * @param serviceId service ID
     * @param isHealthy health flag
     * @return count
     */
    long countByServiceIdAndIsHealthy(String serviceId, Boolean isHealthy);

    /**
     * Check if instance exists by instance ID
     *
     * @param instanceId instance ID
     * @return true if exists
     */
    boolean existsByInstanceId(String instanceId);

    /**
     * Delete instance by instance ID
     *
     * @param instanceId instance ID
     */
    void deleteByInstanceId(String instanceId);

    /**
     * Delete all instances by service ID
     *
     * @param serviceId service ID
     */
    void deleteByServiceId(String serviceId);

    /**
     * Find available instances for load balancing
     *
     * @param serviceId service ID
     * @return list of available instances
     */
    @Query("SELECT i FROM ServiceInstance i WHERE i.serviceId = :serviceId " +
        "AND i.isHealthy = true AND i.instanceStatus = 'UP' AND i.healthStatus = 'HEALTHY'")
    List<ServiceInstance> findAvailableInstancesForLoadBalancing(@Param("serviceId") String serviceId);

    /**
     * Find instances with high error rate
     *
     * @param threshold error count threshold
     * @return list of instances
     */
    @Query("SELECT i FROM ServiceInstance i WHERE i.errorCount > :threshold " +
        "ORDER BY i.errorCount DESC")
    List<ServiceInstance> findInstancesWithHighErrorRate(@Param("threshold") Integer threshold);
}
