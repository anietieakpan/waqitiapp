package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.CircuitBreakerState;
import com.waqiti.discovery.domain.ServiceCircuitBreaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceCircuitBreaker entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceCircuitBreakerRepository extends JpaRepository<ServiceCircuitBreaker, UUID> {

    Optional<ServiceCircuitBreaker> findByCircuitBreakerId(String circuitBreakerId);

    List<ServiceCircuitBreaker> findByServiceId(String serviceId);

    List<ServiceCircuitBreaker> findByTargetServiceId(String targetServiceId);

    Optional<ServiceCircuitBreaker> findByServiceIdAndTargetServiceId(
        String serviceId, String targetServiceId);

    List<ServiceCircuitBreaker> findByState(CircuitBreakerState state);

    @Query("SELECT cb FROM ServiceCircuitBreaker cb WHERE cb.serviceId = :serviceId " +
        "OR cb.targetServiceId = :serviceId")
    List<ServiceCircuitBreaker> findAllCircuitBreakersForService(@Param("serviceId") String serviceId);

    @Query("SELECT cb FROM ServiceCircuitBreaker cb WHERE cb.state = 'OPEN'")
    List<ServiceCircuitBreaker> findOpenCircuitBreakers();

    long countByState(CircuitBreakerState state);
}
