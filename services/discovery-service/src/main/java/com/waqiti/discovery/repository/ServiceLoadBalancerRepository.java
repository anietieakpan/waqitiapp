package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.LoadBalancerStrategy;
import com.waqiti.discovery.domain.ServiceLoadBalancer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceLoadBalancer entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceLoadBalancerRepository extends JpaRepository<ServiceLoadBalancer, UUID> {

    Optional<ServiceLoadBalancer> findByLoadBalancerId(String loadBalancerId);

    List<ServiceLoadBalancer> findByServiceId(String serviceId);

    Optional<ServiceLoadBalancer> findByServiceIdAndIsActive(String serviceId, Boolean isActive);

    List<ServiceLoadBalancer> findByAlgorithm(LoadBalancerStrategy algorithm);

    void deleteByServiceId(String serviceId);

    @Query("SELECT lb FROM ServiceLoadBalancer lb WHERE lb.serviceId = :serviceId " +
        "AND lb.isActive = true")
    Optional<ServiceLoadBalancer> findActiveLoadBalancer(@Param("serviceId") String serviceId);

    long countByServiceId(String serviceId);
}
