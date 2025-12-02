package com.waqiti.common.gdpr.model;

import com.waqiti.common.gdpr.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Data Protection Impact Assessment (DPIA)
 *
 * GDPR Article 35: Required when processing likely results in high risk to rights and freedoms
 *
 * Key Requirements:
 * - Must be conducted before high-risk processing begins
 * - Should describe the processing, purposes, and legitimate interests
 * - Must assess necessity and proportionality
 * - Must assess risks to data subjects
 * - Must identify measures to address risks
 *
 * High-risk processing includes:
 * - Systematic and extensive automated processing with legal effects
 * - Large-scale processing of special categories of data
 * - Systematic monitoring of publicly accessible areas
 *
 * @author Waqiti Platform Team
 * @version 1.0 - Fixed to use RiskLevel from enums package
 * @since 2025-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRDataProtectionImpactAssessment {

    private UUID dpiaId;

    /**
     * Name of the processing activity being assessed
     */
    private String processingActivity;

    /**
     * Purpose of the data processing
     */
    private String purpose;

    /**
     * Categories of personal data being processed
     * Examples: "contact details", "financial data", "biometric data"
     */
    private List<String> dataCategories;

    /**
     * Recipients or categories of recipients of the data
     */
    private List<String> recipients;

    /**
     * Overall risk level assessment
     * FIXED: Now uses RiskLevel from enums package
     */
    private RiskLevel riskLevel;

    /**
     * Detailed description of identified risks
     */
    private String riskDescription;

    /**
     * Measures implemented to mitigate identified risks
     */
    private List<String> mitigationMeasures;

    /**
     * Legal basis for processing
     * Examples: "Consent", "Contract", "Legal obligation", "Legitimate interest"
     */
    private String legalBasis;

    /**
     * Whether consultation with supervisory authority is required
     * Required if high residual risk remains after mitigation
     */
    private Boolean consultationRequired;

    /**
     * Date when the DPIA was conducted
     */
    private LocalDateTime assessmentDate;

    /**
     * Next scheduled review date
     * DPIAs should be reviewed periodically or when processing changes
     */
    private LocalDateTime reviewDate;

    /**
     * Person or team who conducted the assessment
     */
    private String assessedBy;

    /**
     * Person who approved the assessment (typically DPO or senior management)
     */
    private String approvedBy;

    /**
     * Whether a Data Protection Officer (DPO) is required for this processing
     * Article 37: DPO required for public authorities or large-scale special category processing
     */
    private Boolean requiresDPO;

    /**
     * Current status of the DPIA
     * Values: "DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED", "REQUIRES_CONSULTATION"
     */
    private String status;

    /**
     * Check if DPIA indicates high risk requiring supervisory authority consultation
     * Article 36: Consultation required if high risk remains after mitigation
     */
    public boolean requiresSupervisoryConsultation() {
        return Boolean.TRUE.equals(consultationRequired) ||
                (riskLevel != null && riskLevel.getSeverity() >= 3);
    }

    /**
     * Check if DPIA is approved and up-to-date
     */
    public boolean isValid() {
        if (!"APPROVED".equals(status)) {
            return false;
        }

        if (reviewDate != null && LocalDateTime.now().isAfter(reviewDate)) {
            return false; // Past review date, needs re-assessment
        }

        return true;
    }

    /**
     * Check if DPIA requires immediate review
     */
    public boolean requiresImmediateReview() {
        if (reviewDate != null && LocalDateTime.now().isAfter(reviewDate)) {
            return true;
        }

        if (riskLevel == RiskLevel.CRITICAL && !isValid()) {
            return true;
        }

        return false;
    }
}