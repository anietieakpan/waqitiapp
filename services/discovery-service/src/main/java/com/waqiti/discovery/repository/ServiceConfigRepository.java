package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.ServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceConfig entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, UUID> {

    Optional<ServiceConfig> findByConfigId(String configId);

    List<ServiceConfig> findByServiceId(String serviceId);

    Optional<ServiceConfig> findByServiceIdAndConfigKey(String serviceId, String configKey);

    Optional<ServiceConfig> findByServiceIdAndConfigKeyAndEnvironment(
        String serviceId, String configKey, String environment);

    List<ServiceConfig> findByServiceIdAndEnvironment(String serviceId, String environment);

    List<ServiceConfig> findByServiceIdAndIsActive(String serviceId, Boolean isActive);

    List<ServiceConfig> findByIsSensitive(Boolean isSensitive);

    void deleteByServiceId(String serviceId);

    @Query("SELECT c FROM ServiceConfig c WHERE c.serviceId = :serviceId " +
        "AND c.isActive = true AND c.effectiveDate <= :now " +
        "AND (c.expiryDate IS NULL OR c.expiryDate > :now)")
    List<ServiceConfig> findEffectiveConfigs(
        @Param("serviceId") String serviceId,
        @Param("now") Instant now
    );

    long countByServiceId(String serviceId);
}
