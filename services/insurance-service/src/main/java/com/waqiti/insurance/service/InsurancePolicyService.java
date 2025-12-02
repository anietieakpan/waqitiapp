package com.waqiti.insurance.service;

import com.waqiti.insurance.entity.*;
import com.waqiti.insurance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Insurance Policy Service
 * Handles complete policy lifecycle: creation, modification, renewal, cancellation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsurancePolicyService {

    private final InsurancePolicyRepository policyRepository;
    private final PolicyUnderwritingRepository underwritingRepository;
    private final PremiumRepository premiumRepository;
    private final CoverageRepository coverageRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    /**
     * Create new insurance policy
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public InsurancePolicy createPolicy(UUID policyholderId, String policyholderName,
                                       InsurancePolicy.PolicyType policyType,
                                       BigDecimal coverageAmount, BigDecimal premiumAmount,
                                       InsurancePolicy.PaymentFrequency paymentFrequency,
                                       LocalDate effectiveDate, LocalDate expiryDate) {
        log.info("Creating policy: holder={}, type={}, coverage={}", policyholderId, policyType, coverageAmount);

        String policyNumber = generatePolicyNumber(policyType);

        InsurancePolicy policy = InsurancePolicy.builder()
                .policyNumber(policyNumber)
                .policyholderId(policyholderId)
                .policyholderName(policyholderName)
                .policyType(policyType)
                .status(InsurancePolicy.PolicyStatus.PENDING)
                .coverageAmount(coverageAmount)
                .premiumAmount(premiumAmount)
                .paymentFrequency(paymentFrequency)
                .effectiveDate(effectiveDate)
                .expiryDate(expiryDate)
                .nextRenewalDate(calculateNextRenewalDate(effectiveDate, paymentFrequency))
                .totalClaimsCount(0)
                .totalClaimsAmount(BigDecimal.ZERO)
                .build();

        policy = policyRepository.save(policy);
        log.info("Policy created: policyNumber={}, id={}", policyNumber, policy.getId());

        return policy;
    }

    /**
     * Activate policy after underwriting approval
     */
    @Transactional
    public void activatePolicy(UUID policyId, UUID approvedBy) {
        log.info("Activating policy: id={}, approvedBy={}", policyId, approvedBy);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        if (policy.getStatus() != InsurancePolicy.PolicyStatus.PENDING) {
            throw new IllegalStateException("Policy must be PENDING to activate: " + policy.getStatus());
        }

        policy.setStatus(InsurancePolicy.PolicyStatus.ACTIVE);
        policy.setApprovedBy(approvedBy);
        policy.setApprovalDate(LocalDateTime.now());

        policyRepository.save(policy);

        // Generate premium schedule
        generatePremiumSchedule(policy);

        log.info("Policy activated: policyNumber={}", policy.getPolicyNumber());
    }

    /**
     * Modify existing policy
     */
    @Transactional
    public InsurancePolicy modifyPolicy(UUID policyId, BigDecimal newCoverageAmount,
                                       BigDecimal newPremiumAmount) {
        log.info("Modifying policy: id={}, newCoverage={}, newPremium={}",
                policyId, newCoverageAmount, newPremiumAmount);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        if (!policy.isActive()) {
            throw new IllegalStateException("Can only modify active policies");
        }

        policy.setCoverageAmount(newCoverageAmount);
        policy.setPremiumAmount(newPremiumAmount);

        return policyRepository.save(policy);
    }

    /**
     * Renew policy
     */
    @Transactional
    public InsurancePolicy renewPolicy(UUID policyId, LocalDate newExpiryDate) {
        log.info("Renewing policy: id={}, newExpiry={}", policyId, newExpiryDate);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setExpiryDate(newExpiryDate);
        policy.setNextRenewalDate(calculateNextRenewalDate(LocalDate.now(), policy.getPaymentFrequency()));
        policy.setStatus(InsurancePolicy.PolicyStatus.ACTIVE);

        policy = policyRepository.save(policy);

        // Generate new premium schedule
        generatePremiumSchedule(policy);

        log.info("Policy renewed: policyNumber={}", policy.getPolicyNumber());
        return policy;
    }

    /**
     * Cancel policy
     */
    @Transactional
    public void cancelPolicy(UUID policyId, String reason) {
        log.info("Cancelling policy: id={}, reason={}", policyId, reason);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setStatus(InsurancePolicy.PolicyStatus.CANCELLED);
        policy.setCancelledAt(LocalDateTime.now());
        policy.setCancellationReason(reason);

        policyRepository.save(policy);

        log.info("Policy cancelled: policyNumber={}", policy.getPolicyNumber());
    }

    /**
     * Suspend policy
     */
    @Transactional
    public void suspendPolicy(UUID policyId, String reason) {
        log.info("Suspending policy: id={}, reason={}", policyId, reason);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setStatus(InsurancePolicy.PolicyStatus.SUSPENDED);
        policy.setNotes(reason);

        policyRepository.save(policy);
    }

    /**
     * Get policy by policy number
     */
    @Transactional(readOnly = true)
    public InsurancePolicy getPolicyByNumber(String policyNumber) {
        return policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyNumber));
    }

    /**
     * Get policies by policyholder
     */
    @Transactional(readOnly = true)
    public List<InsurancePolicy> getPoliciesByHolder(UUID policyholderId) {
        return policyRepository.findByPolicyholderId(policyholderId);
    }

    /**
     * Get active policies by policyholder
     */
    @Transactional(readOnly = true)
    public List<InsurancePolicy> getActivePolicies(UUID policyholderId) {
        return policyRepository.findByPolicyholderIdAndStatus(
                policyholderId, InsurancePolicy.PolicyStatus.ACTIVE);
    }

    /**
     * Get policies expiring soon
     */
    @Transactional(readOnly = true)
    public List<InsurancePolicy> getPoliciesExpiringSoon(int daysAhead) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);
        return policyRepository.findPoliciesExpiringBetween(startDate, endDate);
    }

    /**
     * Get policies due for renewal
     */
    @Transactional(readOnly = true)
    public List<InsurancePolicy> getPoliciesDueForRenewal() {
        return policyRepository.findPoliciesDueForRenewal(LocalDate.now());
    }

    /**
     * Update policy after claim
     */
    @Transactional
    public void updatePolicyAfterClaim(UUID policyId, BigDecimal claimAmount) {
        log.info("Updating policy after claim: id={}, claimAmount={}", policyId, claimAmount);

        InsurancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        policy.setTotalClaimsCount(policy.getTotalClaimsCount() + 1);
        policy.setTotalClaimsAmount(
                policy.getTotalClaimsAmount().add(claimAmount)
        );
        policy.setLastClaimDate(LocalDateTime.now());

        policyRepository.save(policy);
    }

    /**
     * Check if policy is valid and active
     */
    @Transactional(readOnly = true)
    public boolean isPolicyValid(UUID policyId) {
        return policyRepository.findById(policyId)
                .map(InsurancePolicy::isActive)
                .orElse(false);
    }

    /**
     * Get remaining coverage
     */
    @Transactional(readOnly = true)
    public BigDecimal getRemainingCoverage(UUID policyId) {
        return policyRepository.findById(policyId)
                .map(InsurancePolicy::getRemainingCoverage)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Validate policy eligibility
     */
    public boolean validatePolicyEligibility(UUID policyId, String claimType) {
        InsurancePolicy policy = policyRepository.findById(policyId).orElse(null);

        if (policy == null || !policy.isActive()) {
            return false;
        }

        // Check remaining coverage
        if (policy.getRemainingCoverage().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        return true;
    }

    // Private helper methods

    private String generatePolicyNumber(InsurancePolicy.PolicyType policyType) {
        String prefix = switch (policyType) {
            case LIFE -> "LF";
            case HEALTH -> "HL";
            case AUTO -> "AT";
            case HOME -> "HM";
            case BUSINESS -> "BS";
            case TRAVEL -> "TR";
            case DISABILITY -> "DS";
            case CRITICAL_ILLNESS -> "CI";
        };

        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return String.format("%s-%s-%s", prefix, timestamp.substring(timestamp.length() - 6), random);
    }

    private LocalDate calculateNextRenewalDate(LocalDate startDate, InsurancePolicy.PaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> startDate.plusMonths(1);
            case QUARTERLY -> startDate.plusMonths(3);
            case SEMI_ANNUALLY -> startDate.plusMonths(6);
            case ANNUALLY -> startDate.plusYears(1);
        };
    }

    private void generatePremiumSchedule(InsurancePolicy policy) {
        log.info("Generating premium schedule for policy: {}", policy.getPolicyNumber());

        LocalDate dueDate = policy.getEffectiveDate();
        LocalDate endDate = policy.getExpiryDate();

        while (dueDate.isBefore(endDate)) {
            Premium premium = Premium.builder()
                    .policy(policy)
                    .amount(policy.getPremiumAmount())
                    .dueDate(dueDate)
                    .status(Premium.PremiumStatus.PENDING)
                    .gracePeriodEnd(dueDate.plusDays(30))
                    .build();

            premiumRepository.save(premium);

            dueDate = calculateNextRenewalDate(dueDate, policy.getPaymentFrequency());
        }

        log.info("Premium schedule generated for policy: {}", policy.getPolicyNumber());
    }
}
