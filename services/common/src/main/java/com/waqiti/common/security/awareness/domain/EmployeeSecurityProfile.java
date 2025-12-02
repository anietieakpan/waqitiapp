package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Employee Security Profile
 *
 * Tracks an employee's security awareness metrics, training history,
 * and risk score.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "employee_security_profiles", indexes = {
        @Index(name = "idx_security_profile_employee", columnList = "employee_id"),
        @Index(name = "idx_security_profile_risk_score", columnList = "risk_score")
})
@Data
@Builder(builderMethodName = "internalBuilder")
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSecurityProfile {

    @Id
    @Column(name = "employee_id")
    private UUID id;

    @OneToOne
    @JoinColumn(name = "employee_id", nullable = false, unique = true, insertable = false, updatable = false)
    private Employee employee;

    @Column(name = "risk_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "training_modules_completed")
    @Builder.Default
    private Integer trainingModulesCompleted = 0;

    @Column(name = "training_modules_total")
    @Builder.Default
    private Integer trainingModulesTotal = 0;

    @Column(name = "phishing_tests_passed")
    @Builder.Default
    private Integer phishingTestsPassed = 0;

    @Column(name = "phishing_tests_failed")
    @Builder.Default
    private Integer phishingTestsFailed = 0;

    @Column(name = "assessments_completed")
    @Builder.Default
    private Integer assessmentsCompleted = 0;

    @Column(name = "average_assessment_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal averageAssessmentScore = BigDecimal.ZERO;

    @Column(name = "last_assessment_date")
    private LocalDateTime lastAssessmentDate;

    @Column(name = "next_assessment_due")
    private LocalDateTime nextAssessmentDue;

    @Column(name = "compliance_status", length = 50)
    @Builder.Default
    private String complianceStatus = "NOT_COMPLIANT";

    @Column(name = "total_modules_assigned")
    @Builder.Default
    private Integer totalModulesAssigned = 0;

    @Column(name = "total_modules_completed")
    @Builder.Default
    private Integer totalModulesCompleted = 0;

    @Column(name = "compliance_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal compliancePercentage = BigDecimal.ZERO;

    @Column(name = "last_training_completed_at")
    private LocalDateTime lastTrainingCompletedAt;

    @Column(name = "next_training_due_at")
    private LocalDateTime nextTrainingDueAt;

    @Column(name = "total_phishing_tests")
    @Builder.Default
    private Integer totalPhishingTests = 0;

    @Column(name = "phishing_success_rate_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal phishingSuccessRatePercentage = BigDecimal.valueOf(100);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Custom builder that accepts employeeId
     */
    public static EmployeeSecurityProfileBuilder builder() {
        return internalBuilder();
    }

    public static class EmployeeSecurityProfileBuilder {
        public EmployeeSecurityProfileBuilder employeeId(UUID employeeId) {
            this.id = employeeId;
            return this;
        }
    }

    /**
     * Get employee ID (alias for getId)
     */
    public UUID getEmployeeId() {
        return this.id;
    }

    /**
     * Get compliance percentage
     */
    public BigDecimal getCompliancePercentage() {
        return this.compliancePercentage != null ? this.compliancePercentage : BigDecimal.ZERO;
    }

    /**
     * Get next training due date
     */
    public LocalDateTime getNextTrainingDueAt() {
        return this.nextTrainingDueAt;
    }

    /**
     * Calculate and update risk score based on metrics
     */
    public void calculateRiskScore() {
        BigDecimal score = BigDecimal.ZERO;

        // Phishing failure rate (40% weight)
        int totalPhishingTests = phishingTestsPassed + phishingTestsFailed;
        if (totalPhishingTests > 0) {
            BigDecimal failureRate = BigDecimal.valueOf(phishingTestsFailed)
                    .divide(BigDecimal.valueOf(totalPhishingTests), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(40));
            score = score.add(failureRate);
        }

        // Training completion rate (30% weight) - inverse
        if (trainingModulesTotal > 0) {
            BigDecimal incompletionRate = BigDecimal.valueOf(trainingModulesTotal - trainingModulesCompleted)
                    .divide(BigDecimal.valueOf(trainingModulesTotal), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(30));
            score = score.add(incompletionRate);
        }

        // Assessment performance (30% weight) - inverse
        if (averageAssessmentScore.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal assessmentRisk = BigDecimal.valueOf(100)
                    .subtract(averageAssessmentScore)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(30));
            score = score.add(assessmentRisk);
        }

        this.riskScore = score;
        this.riskLevel = determineRiskLevel(score);
        this.complianceStatus = determineComplianceStatus();
    }

    private RiskLevel determineRiskLevel(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return RiskLevel.CRITICAL;
        } else if (score.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return RiskLevel.HIGH;
        } else if (score.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    private String determineComplianceStatus() {
        // Check if training is up to date
        boolean trainingComplete = trainingModulesCompleted.equals(trainingModulesTotal)
                && trainingModulesTotal > 0;

        // Check if assessment is current (within 90 days)
        boolean assessmentCurrent = nextAssessmentDue != null
                && LocalDateTime.now().isBefore(nextAssessmentDue);

        // Check risk level
        boolean lowRisk = riskLevel == RiskLevel.LOW || riskLevel == RiskLevel.MEDIUM;

        if (trainingComplete && assessmentCurrent && lowRisk) {
            return "COMPLIANT";
        } else if (trainingComplete || assessmentCurrent) {
            return "PARTIALLY_COMPLIANT";
        } else {
            return "NOT_COMPLIANT";
        }
    }

    /**
     * Update phishing test result
     */
    public void recordPhishingTestResult(boolean passed) {
        if (passed) {
            this.phishingTestsPassed++;
        } else {
            this.phishingTestsFailed++;
        }
        calculateRiskScore();
    }

    /**
     * Update training completion
     */
    public void recordTrainingCompletion() {
        this.trainingModulesCompleted++;
        calculateRiskScore();
    }

    /**
     * Update assessment result
     */
    public void recordAssessmentResult(BigDecimal score) {
        this.assessmentsCompleted++;

        // Calculate running average
        if (this.averageAssessmentScore.compareTo(BigDecimal.ZERO) == 0) {
            this.averageAssessmentScore = score;
        } else {
            BigDecimal total = this.averageAssessmentScore
                    .multiply(BigDecimal.valueOf(assessmentsCompleted - 1))
                    .add(score);
            this.averageAssessmentScore = total
                    .divide(BigDecimal.valueOf(assessmentsCompleted), 2, RoundingMode.HALF_UP);
        }

        this.lastAssessmentDate = LocalDateTime.now();
        this.nextAssessmentDue = LocalDateTime.now().plusDays(90); // Quarterly

        calculateRiskScore();
    }
}