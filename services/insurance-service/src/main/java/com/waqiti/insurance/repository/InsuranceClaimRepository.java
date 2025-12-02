package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.InsuranceClaim;
import com.waqiti.insurance.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, UUID> {

    Optional<InsuranceClaim> findByClaimNumber(String claimNumber);

    List<InsuranceClaim> findByPolicyId(UUID policyId);

    List<InsuranceClaim> findByPolicyholderIdAndStatus(UUID policyholderId, ClaimStatus status);

    @Query("SELECT c FROM InsuranceClaim c WHERE c.status = :status AND c.slaDeadline < :now AND c.slaBreached = false")
    List<InsuranceClaim> findClaimsBreachingSLA(@Param("status") ClaimStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT c FROM InsuranceClaim c WHERE c.fraudScore >= :threshold")
    List<InsuranceClaim> findHighFraudRiskClaims(@Param("threshold") Double threshold);

    List<InsuranceClaim> findByAdjusterIdAndStatus(UUID adjusterId, ClaimStatus status);
}
