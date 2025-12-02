package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.ServiceRegistry;
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
 * Repository for ServiceRegistry entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceRegistryRepository extends JpaRepository<ServiceRegistry, UUID> {

    /**
     * Find service by service ID
     *
     * @param serviceId service ID
     * @return optional service registry
     */
    Optional<ServiceRegistry> findByServiceId(String serviceId);

    /**
     * Find service by service name
     *
     * @param serviceName service name
     * @return list of service registries
     */
    List<ServiceRegistry> findByServiceName(String serviceName);

    /**
     * Find all services by status
     *
     * @param status service status
     * @return list of service registries
     */
    List<ServiceRegistry> findByServiceStatus(ServiceStatus status);

    /**
     * Find all services by environment
     *
     * @param environment environment
     * @return list of service registries
     */
    List<ServiceRegistry> findByEnvironment(String environment);

    /**
     * Find all services by type
     *
     * @param serviceType service type
     * @return list of service registries
     */
    List<ServiceRegistry> findByServiceType(String serviceType);

    /**
     * Find all services by zone
     *
     * @param zone zone
     * @return list of service registries
     */
    List<ServiceRegistry> findByZone(String zone);

    /**
     * Find all services with heartbeat before cutoff
     *
     * @param cutoffTime cutoff time
     * @return list of service registries
     */
    List<ServiceRegistry> findByLastHeartbeatBefore(Instant cutoffTime);

    /**
     * Find all services with status and environment
     *
     * @param status service status
     * @param environment environment
     * @return list of service registries
     */
    List<ServiceRegistry> findByServiceStatusAndEnvironment(ServiceStatus status, String environment);

    /**
     * Count services by status
     *
     * @param status service status
     * @return count
     */
    long countByServiceStatus(ServiceStatus status);

    /**
     * Count services by environment
     *
     * @param environment environment
     * @return count
     */
    long countByEnvironment(String environment);

    /**
     * Check if service exists by service ID
     *
     * @param serviceId service ID
     * @return true if exists
     */
    boolean existsByServiceId(String serviceId);

    /**
     * Delete services by service ID
     *
     * @param serviceId service ID
     */
    void deleteByServiceId(String serviceId);

    /**
     * Find all services ordered by registration time
     *
     * @return list of service registries
     */
    @Query("SELECT s FROM ServiceRegistry s ORDER BY s.registrationTime DESC")
    List<ServiceRegistry> findAllOrderedByRegistrationTime();

    /**
     * Find healthy services
     *
     * @param timeoutSeconds timeout in seconds
     * @return list of healthy service registries
     */
    @Query("SELECT s FROM ServiceRegistry s WHERE s.serviceStatus = 'UP' " +
        "AND s.lastHeartbeat > :cutoffTime")
    List<ServiceRegistry> findHealthyServices(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Find services by tag
     *
     * @param tag tag to search for
     * @return list of service registries
     */
    @Query("SELECT s FROM ServiceRegistry s WHERE :tag = ANY(s.tags)")
    List<ServiceRegistry> findByTag(@Param("tag") String tag);
}
