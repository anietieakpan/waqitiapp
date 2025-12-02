package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, UUID> {

    Optional<InsurancePolicy> findByPolicyNumber(String policyNumber);

    List<InsurancePolicy> findByPolicyholderId(UUID policyholderId);

    List<InsurancePolicy> findByStatus(InsurancePolicy.PolicyStatus status);

    @Query("SELECT p FROM InsurancePolicy p WHERE p.expiryDate BETWEEN :startDate AND :endDate")
    List<InsurancePolicy> findPoliciesExpiringBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT p FROM InsurancePolicy p WHERE p.status = 'ACTIVE' AND p.nextRenewalDate <= :date")
    List<InsurancePolicy> findPoliciesDueForRenewal(@Param("date") LocalDate date);
}
