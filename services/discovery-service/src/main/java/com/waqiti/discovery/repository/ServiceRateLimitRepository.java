package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.ServiceRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceRateLimit entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceRateLimitRepository extends JpaRepository<ServiceRateLimit, UUID> {

    Optional<ServiceRateLimit> findByRateLimitId(String rateLimitId);

    List<ServiceRateLimit> findByServiceId(String serviceId);

    List<ServiceRateLimit> findByEndpointId(String endpointId);

    List<ServiceRateLimit> findByServiceIdAndIsActive(String serviceId, Boolean isActive);

    Optional<ServiceRateLimit> findByServiceIdAndEndpointId(String serviceId, String endpointId);

    void deleteByServiceId(String serviceId);

    void deleteByEndpointId(String endpointId);

    @Query("SELECT rl FROM ServiceRateLimit rl WHERE rl.serviceId = :serviceId " +
        "AND rl.isActive = true")
    List<ServiceRateLimit> findActiveRateLimits(@Param("serviceId") String serviceId);

    long countByServiceId(String serviceId);
}
