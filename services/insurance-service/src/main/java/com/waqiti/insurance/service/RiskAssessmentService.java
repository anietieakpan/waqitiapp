package com.waqiti.insurance.service;

import com.waqiti.insurance.entity.InsurancePolicy;
import com.waqiti.insurance.entity.RiskProfile;
import com.waqiti.insurance.repository.InsuranceClaimRepository;
import com.waqiti.insurance.repository.RiskProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Risk Assessment Service
 * Handles risk scoring and customer risk profiling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskProfileRepository riskProfileRepository;
    private final InsuranceClaimRepository claimRepository;

    @Transactional
    public RiskProfile createRiskProfile(UUID customerId) {
        log.info("Creating risk profile for customer: {}", customerId);

        BigDecimal riskScore = calculateRiskScore(customerId, null);
        InsurancePolicy.RiskCategory category = determineRiskCategory(riskScore);

        RiskProfile profile = RiskProfile.builder()
                .customerId(customerId)
                .overallRiskScore(riskScore)
                .riskCategory(category)
                .claimsHistoryCount(0)
                .totalClaimsValue(BigDecimal.ZERO)
                .assessmentDate(LocalDateTime.now())
                .build();

        return riskProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public RiskProfile getRiskProfile(UUID customerId) {
        return riskProfileRepository.findByCustomerId(customerId)
                .orElseGet(() -> createRiskProfile(customerId));
    }

    @Transactional
    public BigDecimal calculateRiskScore(UUID customerId, String policyType) {
        log.debug("Calculating risk score for customer: {}, policyType: {}", customerId, policyType);

        RiskProfile profile = riskProfileRepository.findByCustomerId(customerId).orElse(null);

        BigDecimal baseScore = new BigDecimal("50.0");
        BigDecimal claimsAdjustment = BigDecimal.ZERO;
        BigDecimal creditAdjustment = BigDecimal.ZERO;

        if (profile != null) {
            // Adjust for claims history
            if (profile.getClaimsHistoryCount() > 0) {
                claimsAdjustment = new BigDecimal(profile.getClaimsHistoryCount() * 5);
            }

            // Adjust for credit score
            if (profile.getCreditScore() != null) {
                if (profile.getCreditScore() < 600) {
                    creditAdjustment = new BigDecimal("20");
                } else if (profile.getCreditScore() < 700) {
                    creditAdjustment = new BigDecimal("10");
                } else if (profile.getCreditScore() >= 750) {
                    creditAdjustment = new BigDecimal("-10");
                }
            }
        }

        BigDecimal totalScore = baseScore.add(claimsAdjustment).add(creditAdjustment);

        // Cap between 0 and 100
        if (totalScore.compareTo(BigDecimal.ZERO) < 0) totalScore = BigDecimal.ZERO;
        if (totalScore.compareTo(new BigDecimal("100")) > 0) totalScore = new BigDecimal("100");

        return totalScore;
    }

    @Transactional
    public void updateRiskProfile(UUID customerId, Integer creditScore, String occupationRiskLevel) {
        RiskProfile profile = getRiskProfile(customerId);

        profile.setCreditScore(creditScore);
        profile.setOccupationRiskLevel(occupationRiskLevel);
        profile.setOverallRiskScore(calculateRiskScore(customerId, null));
        profile.setRiskCategory(determineRiskCategory(profile.getOverallRiskScore()));
        profile.setAssessmentDate(LocalDateTime.now());

        riskProfileRepository.save(profile);
    }

    private InsurancePolicy.RiskCategory determineRiskCategory(BigDecimal riskScore) {
        if (riskScore.compareTo(new BigDecimal("75")) >= 0) {
            return InsurancePolicy.RiskCategory.VERY_HIGH;
        } else if (riskScore.compareTo(new BigDecimal("60")) >= 0) {
            return InsurancePolicy.RiskCategory.HIGH;
        } else if (riskScore.compareTo(new BigDecimal("40")) >= 0) {
            return InsurancePolicy.RiskCategory.MEDIUM;
        } else {
            return InsurancePolicy.RiskCategory.LOW;
        }
    }
}
