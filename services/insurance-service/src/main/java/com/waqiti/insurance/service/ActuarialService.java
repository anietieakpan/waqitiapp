package com.waqiti.insurance.service;

import com.waqiti.insurance.entity.ActuarialData;
import com.waqiti.insurance.repository.ActuarialDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Actuarial Service
 * Handles premium calculations and actuarial data management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActuarialService {

    private final ActuarialDataRepository actuarialDataRepository;

    @Transactional(readOnly = true)
    public BigDecimal calculatePremium(String policyType, String ageGroup,
                                      String riskCategory, BigDecimal coverageAmount) {
        log.debug("Calculating premium: type={}, age={}, risk={}, coverage={}",
                policyType, ageGroup, riskCategory, coverageAmount);

        ActuarialData data = actuarialDataRepository.findCurrentRates(
                policyType, ageGroup, riskCategory, LocalDateTime.now()
        ).orElseGet(() -> getDefaultActuarialData(policyType));

        BigDecimal basePremiumRate = data.getBasePremiumRate();
        BigDecimal riskAdjustment = data.getRiskAdjustmentFactor();

        // Premium = Coverage * Base Rate * Risk Adjustment
        BigDecimal premium = coverageAmount
                .multiply(basePremiumRate)
                .multiply(riskAdjustment);

        log.debug("Calculated premium: {}", premium);
        return premium;
    }

    @Transactional(readOnly = true)
    public BigDecimal getMortalityRate(String policyType, String ageGroup) {
        return actuarialDataRepository.findCurrentRates(
                policyType, ageGroup, "MEDIUM", LocalDateTime.now()
        ).map(ActuarialData::getMortalityRate)
        .orElse(new BigDecimal("0.001"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getClaimFrequency(String policyType, String riskCategory) {
        return actuarialDataRepository.findCurrentRates(
                policyType, "30-40", riskCategory, LocalDateTime.now()
        ).map(ActuarialData::getClaimFrequency)
        .orElse(new BigDecimal("0.05"));
    }

    private ActuarialData getDefaultActuarialData(String policyType) {
        // Default fallback rates
        return ActuarialData.builder()
                .policyType(policyType)
                .ageGroup("DEFAULT")
                .riskCategory("MEDIUM")
                .basePremiumRate(new BigDecimal("0.01"))
                .riskAdjustmentFactor(BigDecimal.ONE)
                .claimFrequency(new BigDecimal("0.05"))
                .averageClaimAmount(new BigDecimal("5000"))
                .lossRatio(new BigDecimal("0.70"))
                .effectiveFrom(LocalDateTime.now())
                .build();
    }
}
