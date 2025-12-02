package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CustomerFreezeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerFreezeAuditRepository extends JpaRepository<CustomerFreezeAudit, UUID> {
    List<CustomerFreezeAudit> findByCustomerId(UUID customerId);
    List<CustomerFreezeAudit> findByCorrelationId(String correlationId);
    List<CustomerFreezeAudit> findByCustomerIdAndActiveTrue(UUID customerId);
}
