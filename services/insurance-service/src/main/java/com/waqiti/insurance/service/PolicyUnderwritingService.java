package com.waqiti.insurance.service;

import com.waqiti.insurance.entity.InsurancePolicy;
import com.waqiti.insurance.entity.PolicyUnderwriting;
import com.waqiti.insurance.repository.PolicyUnderwritingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Policy Underwriting Service
 * Handles underwriting decisions and risk evaluation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyUnderwritingService {

    private final PolicyUnderwritingRepository underwritingRepository;
    private final RiskAssessmentService riskAssessmentService;

    @Transactional
    public PolicyUnderwriting createUnderwriting(InsurancePolicy policy, UUID underwriterId) {
        log.info("Creating underwriting for policy: {}", policy.getId());

        BigDecimal riskScore = riskAssessmentService.calculateRiskScore(
                policy.getPolicyholderId(),
                policy.getPolicyType().toString()
        );

        PolicyUnderwriting.UnderwritingDecision decision = determineDecision(riskScore);
        BigDecimal recommendedPremium = calculateRecommendedPremium(policy, riskScore);

        PolicyUnderwriting underwriting = PolicyUnderwriting.builder()
                .policy(policy)
                .underwriterId(underwriterId)
                .riskScore(riskScore)
                .decision(decision)
                .recommendedPremium(recommendedPremium)
                .decisionReason(getDecisionReason(decision, riskScore))
                .build();

        return underwritingRepository.save(underwriting);
    }

    @Transactional(readOnly = true)
    public PolicyUnderwriting getUnderwritingByPolicy(UUID policyId) {
        return underwritingRepository.findByPolicyId(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Underwriting not found for policy: " + policyId));
    }

    private PolicyUnderwriting.UnderwritingDecision determineDecision(BigDecimal riskScore) {
        if (riskScore.compareTo(new BigDecimal("80")) >= 0) {
            return PolicyUnderwriting.UnderwritingDecision.DECLINED;
        } else if (riskScore.compareTo(new BigDecimal("60")) >= 0) {
            return PolicyUnderwriting.UnderwritingDecision.APPROVED_WITH_CONDITIONS;
        } else if (riskScore.compareTo(new BigDecimal("40")) >= 0) {
            return PolicyUnderwriting.UnderwritingDecision.PENDING_REVIEW;
        } else {
            return PolicyUnderwriting.UnderwritingDecision.APPROVED;
        }
    }

    private BigDecimal calculateRecommendedPremium(InsurancePolicy policy, BigDecimal riskScore) {
        BigDecimal basePremium = policy.getPremiumAmount();
        BigDecimal riskMultiplier = riskScore.divide(new BigDecimal("50"), 4, java.math.RoundingMode.HALF_UP);
        return basePremium.multiply(riskMultiplier);
    }

    private String getDecisionReason(PolicyUnderwriting.UnderwritingDecision decision, BigDecimal riskScore) {
        return switch (decision) {
            case APPROVED -> "Low risk profile, standard approval";
            case APPROVED_WITH_CONDITIONS -> "Moderate risk, approved with premium adjustment";
            case DECLINED -> "High risk profile exceeds acceptable threshold";
            case PENDING_REVIEW -> "Risk score requires manual underwriter review";
            case REQUIRES_MEDICAL_EXAM -> "Medical examination required for assessment";
        };
    }
}
