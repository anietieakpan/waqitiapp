package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.PolicyUnderwriting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyUnderwritingRepository extends JpaRepository<PolicyUnderwriting, UUID> {

    Optional<PolicyUnderwriting> findByPolicyId(UUID policyId);
}
