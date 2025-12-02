package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.Coverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoverageRepository extends JpaRepository<Coverage, UUID> {

    List<Coverage> findByPolicyIdAndActiveTrue(UUID policyId);
}
