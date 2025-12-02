package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.CircuitBreakerState;
import com.waqiti.discovery.domain.DependencyType;
import com.waqiti.discovery.domain.ServiceDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceDependency entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, UUID> {

    Optional<ServiceDependency> findByDependencyId(String dependencyId);

    List<ServiceDependency> findByConsumerServiceId(String consumerServiceId);

    List<ServiceDependency> findByProviderServiceId(String providerServiceId);

    List<ServiceDependency> findByDependencyType(DependencyType dependencyType);

    List<ServiceDependency> findByIsCritical(Boolean isCritical);

    List<ServiceDependency> findByCircuitBreakerState(CircuitBreakerState state);

    List<ServiceDependency> findByConsumerServiceIdAndProviderServiceId(
        String consumerServiceId, String providerServiceId);

    void deleteByConsumerServiceId(String consumerServiceId);

    void deleteByProviderServiceId(String providerServiceId);

    boolean existsByConsumerServiceIdAndProviderServiceId(
        String consumerServiceId, String providerServiceId);

    @Query("SELECT d FROM ServiceDependency d WHERE d.consumerServiceId = :serviceId " +
        "OR d.providerServiceId = :serviceId")
    List<ServiceDependency> findAllDependenciesForService(@Param("serviceId") String serviceId);

    @Query("SELECT d FROM ServiceDependency d WHERE d.circuitBreakerState = 'OPEN' " +
        "AND d.isCritical = true")
    List<ServiceDependency> findCriticalDependenciesWithOpenCircuit();

    long countByConsumerServiceId(String consumerServiceId);
}
