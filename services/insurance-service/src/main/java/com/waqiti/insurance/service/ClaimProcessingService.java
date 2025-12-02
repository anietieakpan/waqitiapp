package com.waqiti.insurance.service;

import com.waqiti.insurance.entity.InsuranceClaim;
import com.waqiti.insurance.entity.InsurancePolicy;
import com.waqiti.insurance.model.ClaimStatus;
import com.waqiti.insurance.model.ClaimComplexity;
import com.waqiti.insurance.repository.InsuranceClaimRepository;
import com.waqiti.insurance.repository.InsurancePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Claim Processing Service
 * Handles complete claim lifecycle and processing logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimProcessingService {

    private final InsuranceClaimRepository claimRepository;
    private final InsurancePolicyRepository policyRepository;
    private final InsurancePolicyService policyService;

    private static final int DEFAULT_SLA_DAYS = 30;
    private static final BigDecimal AUTO_APPROVAL_THRESHOLD = new BigDecimal("5000.00");

    /**
     * Create new claim
     */
    @Transactional
    public InsuranceClaim createClaim(UUID policyId, UUID policyholderId,
                                     InsuranceClaim.ClaimType claimType,
                                     BigDecimal claimAmount, LocalDate incidentDate,
                                     String incidentDescription, String incidentLocation) {
        log.info("Creating claim: policy={}, type={}, amount={}", policyId, claimType, claimAmount);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        if (!policy.isActive()) {
            throw new IllegalStateException("Cannot create claim for inactive policy");
        }

        String claimNumber = generateClaimNumber(claimType);
        LocalDateTime slaDeadline = LocalDateTime.now().plusDays(DEFAULT_SLA_DAYS);

        InsuranceClaim claim = InsuranceClaim.builder()
                .claimNumber(claimNumber)
                .policy(policy)
                .policyholderId(policyholderId)
                .claimType(claimType)
                .status(ClaimStatus.PENDING)
                .complexity(determineComplexity(claimAmount, claimType))
                .claimAmount(claimAmount)
                .incidentDate(incidentDate)
                .incidentDescription(incidentDescription)
                .incidentLocation(incidentLocation)
                .submissionDate(LocalDateTime.now())
                .slaDeadline(slaDeadline)
                .priority(calculatePriority(claimAmount))
                .fraudScore(0.0)
                .requiresInvestigation(false)
                .build();

        claim = claimRepository.save(claim);
        log.info("Claim created: claimNumber={}, id={}", claimNumber, claim.getId());

        return claim;
    }

    /**
     * Assign claim number
     */
    @Transactional
    public void assignClaimNumber(InsuranceClaim claim) {
        if (claim.getClaimNumber() == null) {
            claim.setClaimNumber(generateClaimNumber(claim.getClaimType()));
            claimRepository.save(claim);
        }
    }

    /**
     * Set claim priority
     */
    @Transactional
    public void setClaimPriority(InsuranceClaim claim, Integer priority) {
        log.info("Setting claim priority: claimId={}, priority={}", claim.getId(), priority);
        claim.setPriority(priority);
        claimRepository.save(claim);
    }

    /**
     * Update claim status
     */
    @Transactional
    public void updateClaimStatus(InsuranceClaim claim, String status) {
        log.info("Updating claim status: claimNumber={}, status={}", claim.getClaimNumber(), status);
        claim.setStatus(ClaimStatus.valueOf(status));
        claimRepository.save(claim);
    }

    /**
     * Approve claim
     */
    @Transactional
    public void approveClaim(UUID claimId, BigDecimal approvedAmount, UUID approvedBy) {
        log.info("Approving claim: id={}, amount={}, approvedBy={}", claimId, approvedAmount, approvedBy);

        InsuranceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(approvedAmount);
        claim.setApprovalDate(LocalDateTime.now());

        claimRepository.save(claim);

        // Update policy
        policyService.updatePolicyAfterClaim(claim.getPolicy().getId(), approvedAmount);

        log.info("Claim approved: claimNumber={}", claim.getClaimNumber());
    }

    /**
     * Reject claim
     */
    @Transactional
    public void rejectClaim(UUID claimId, String reason) {
        log.info("Rejecting claim: id={}, reason={}", claimId, reason);

        InsuranceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setStatus(ClaimStatus.REJECTED);
        claim.setDenialReason(reason);

        claimRepository.save(claim);
    }

    /**
     * Settle claim
     */
    @Transactional
    public void settleClaim(UUID claimId, BigDecimal settledAmount) {
        log.info("Settling claim: id={}, amount={}", claimId, settledAmount);

        InsuranceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new IllegalStateException("Can only settle approved claims");
        }

        claim.setStatus(ClaimStatus.SETTLED);
        claim.setSettledAmount(settledAmount);
        claim.setSettlementDate(LocalDateTime.now());
        claim.setPaymentDate(LocalDateTime.now());

        claimRepository.save(claim);
    }

    /**
     * Assign adjuster to claim
     */
    @Transactional
    public void assignAdjuster(UUID claimId, UUID adjusterId) {
        log.info("Assigning adjuster: claimId={}, adjusterId={}", claimId, adjusterId);

        InsuranceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setAdjusterId(adjusterId);
        claim.setAdjusterAssignedAt(LocalDateTime.now());
        claim.setStatus(ClaimStatus.UNDER_REVIEW);

        claimRepository.save(claim);
    }

    /**
     * Flag claim for investigation
     */
    @Transactional
    public void flagForInvestigation(UUID claimId, String reason, Double fraudScore) {
        log.warn("Flagging claim for investigation: id={}, reason={}, fraudScore={}",
                claimId, reason, fraudScore);

        InsuranceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setRequiresInvestigation(true);
        claim.setFraudScore(fraudScore);
        claim.setInvestigationNotes(reason);
        claim.setStatus(ClaimStatus.UNDER_INVESTIGATION);

        claimRepository.save(claim);
    }

    /**
     * Get claims by status
     */
    @Transactional(readOnly = true)
    public List<InsuranceClaim> getClaimsByStatus(ClaimStatus status) {
        // Placeholder - would need a repository method
        return List.of();
    }

    /**
     * Get claims by adjuster
     */
    @Transactional(readOnly = true)
    public List<InsuranceClaim> getClaimsByAdjuster(UUID adjusterId, ClaimStatus status) {
        return claimRepository.findByAdjusterIdAndStatus(adjusterId, status);
    }

    /**
     * Get claims breaching SLA
     */
    @Transactional(readOnly = true)
    public List<InsuranceClaim> getClaimsBreachingSLA() {
        return claimRepository.findClaimsBreachingSLA(ClaimStatus.PENDING, LocalDateTime.now());
    }

    /**
     * Get high fraud risk claims
     */
    @Transactional(readOnly = true)
    public List<InsuranceClaim> getHighFraudRiskClaims(Double threshold) {
        return claimRepository.findHighFraudRiskClaims(threshold);
    }

    /**
     * Validate claim amount
     */
    public boolean isValidClaimAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if claim can be auto-approved
     */
    public boolean canAutoApprove(BigDecimal claimAmount, Double fraudScore) {
        return claimAmount.compareTo(AUTO_APPROVAL_THRESHOLD) <= 0 &&
               fraudScore < 0.3;
    }

    // Private helper methods

    private String generateClaimNumber(InsuranceClaim.ClaimType claimType) {
        String prefix = switch (claimType) {
            case MEDICAL -> "MED";
            case ACCIDENT -> "ACC";
            case PROPERTY_DAMAGE -> "PRO";
            case THEFT -> "THF";
            case FIRE -> "FIR";
            case FLOOD -> "FLD";
            case DEATH_BENEFIT -> "DTH";
            case DISABILITY -> "DIS";
            case TRAVEL_INTERRUPTION -> "TRV";
            case LIABILITY -> "LBL";
        };

        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        return String.format("CLM-%s-%s-%s", prefix, timestamp.substring(timestamp.length() - 6), random);
    }

    private ClaimComplexity determineComplexity(BigDecimal amount, InsuranceClaim.ClaimType claimType) {
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            return ClaimComplexity.CATASTROPHIC;
        } else if (amount.compareTo(new BigDecimal("25000")) > 0) {
            return ClaimComplexity.COMPLEX;
        } else if (claimType == InsuranceClaim.ClaimType.DEATH_BENEFIT ||
                   claimType == InsuranceClaim.ClaimType.DISABILITY) {
            return ClaimComplexity.COMPLEX;
        } else {
            return ClaimComplexity.STANDARD;
        }
    }

    private Integer calculatePriority(BigDecimal claimAmount) {
        // Priority: 1 (highest) to 10 (lowest)
        if (claimAmount.compareTo(new BigDecimal("50000")) > 0) {
            return 1;
        } else if (claimAmount.compareTo(new BigDecimal("25000")) > 0) {
            return 3;
        } else if (claimAmount.compareTo(new BigDecimal("10000")) > 0) {
            return 5;
        } else {
            return 7;
        }
    }
}
