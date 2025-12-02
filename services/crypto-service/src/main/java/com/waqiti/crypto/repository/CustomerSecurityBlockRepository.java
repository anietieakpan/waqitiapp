package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CustomerSecurityBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerSecurityBlockRepository extends JpaRepository<CustomerSecurityBlock, UUID> {
    List<CustomerSecurityBlock> findByCustomerId(UUID customerId);
    List<CustomerSecurityBlock> findByCustomerIdAndActiveTrue(UUID customerId);
    List<CustomerSecurityBlock> findByActiveTrue();
    List<CustomerSecurityBlock> findByCorrelationId(String correlationId);
    List<CustomerSecurityBlock> findByViolationType(String violationType);
}
