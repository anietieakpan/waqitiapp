package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByPolicyIdAndActiveTrue(UUID policyId);
}
