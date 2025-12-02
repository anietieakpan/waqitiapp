package com.waqiti.discovery.repository;

import com.waqiti.discovery.domain.ServiceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ServiceEndpoint entity
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Repository
public interface ServiceEndpointRepository extends JpaRepository<ServiceEndpoint, UUID> {

    Optional<ServiceEndpoint> findByEndpointId(String endpointId);

    List<ServiceEndpoint> findByServiceId(String serviceId);

    List<ServiceEndpoint> findByServiceIdAndHttpMethod(String serviceId, String httpMethod);

    List<ServiceEndpoint> findByIsPublic(Boolean isPublic);

    List<ServiceEndpoint> findByDeprecated(Boolean deprecated);

    List<ServiceEndpoint> findByServiceIdAndDeprecated(String serviceId, Boolean deprecated);

    void deleteByServiceId(String serviceId);

    @Query("SELECT e FROM ServiceEndpoint e WHERE e.serviceId = :serviceId " +
        "AND e.endpointPath LIKE %:pathPattern%")
    List<ServiceEndpoint> findByServiceIdAndPathContaining(
        @Param("serviceId") String serviceId,
        @Param("pathPattern") String pathPattern
    );

    @Query("SELECT e FROM ServiceEndpoint e WHERE :tag = ANY(e.tags)")
    List<ServiceEndpoint> findByTag(@Param("tag") String tag);

    long countByServiceId(String serviceId);

    long countByServiceIdAndDeprecated(String serviceId, Boolean deprecated);
}
