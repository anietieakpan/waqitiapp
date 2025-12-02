package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk Assessment for data breaches and privacy impacts
 *
 * Evaluates the risk level and factors for GDPR compliance
 * under Articles 33-34 (breach notification) and Article 35 (DPIA)
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore; // 0-100 scale

    @Column(name = "likelihood_score")
    private Integer likelihoodScore; // 0-10 scale

    @Column(name = "impact_score")
    private Integer impactScore; // 0-10 scale

    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors; // JSON array of risk factors

    @Column(name = "mitigating_factors", columnDefinition = "TEXT")
    private String mitigatingFactors; // JSON array of mitigating factors

    @Column(name = "residual_risk_level", length = 20)
    private String residualRiskLevel;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;

    @Column(name = "assessed_by", length = 255)
    private String assessedBy;

    @Column(name = "assessment_methodology", length = 100)
    private String assessmentMethodology;

    @Column(name = "requires_dpia")
    private Boolean requiresDpia;

    @Column(name = "dpia_reference", length = 255)
    private String dpiaReference;

    @Column(name = "review_date")
    private LocalDateTime reviewDate;

    /**
     * Calculate overall risk score based on likelihood and impact
     */
    public void calculateRiskScore() {
        if (likelihoodScore != null && impactScore != null) {
            // Risk Score = (Likelihood × Impact) × 10
            this.riskScore = (likelihoodScore * impactScore) * 10 / 10;

            // Determine risk level based on score
            if (riskScore >= 75) {
                this.riskLevel = RiskLevel.CRITICAL;
            } else if (riskScore >= 50) {
                this.riskLevel = RiskLevel.HIGH;
            } else if (riskScore >= 25) {
                this.riskLevel = RiskLevel.MEDIUM;
            } else {
                this.riskLevel = RiskLevel.LOW;
            }
        }
    }

    /**
     * Determine if DPIA is required based on risk level
     */
    public boolean shouldRequireDpia() {
        return riskLevel != null &&
               (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL);
    }

    /**
     * Parse risk factors from JSON string
     */
    public List<String> getRiskFactorsList() {
        if (riskFactors == null || riskFactors.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(riskFactors,
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Set risk factors as JSON string
     */
    public void setRiskFactorsList(List<String> factors) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            this.riskFactors = mapper.writeValueAsString(factors);
        } catch (Exception e) {
            this.riskFactors = "[]";
        }
    }
}
