package com.waqiti.kyc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an Enhanced Due Diligence (EDD) Report
 * 
 * EDD reports contain comprehensive customer risk assessments including:
 * - Source of wealth verification
 * - PEP screening results
 * - Sanctions list screening
 * - Adverse media screening  
 * - Transaction pattern analysis
 * - Risk scoring and recommendations
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Entity
@Table(name = "edd_reports", indexes = {
    @Index(name = "idx_edd_user_id", columnList = "user_id"),
    @Index(name = "idx_edd_status", columnList = "status"),
    @Index(name = "idx_edd_risk_level", columnList = "risk_level"),
    @Index(name = "idx_edd_generated_at", columnList = "generated_at"),
    @Index(name = "idx_edd_expiry", columnList = "expiry_date"),
    @Index(name = "idx_edd_risk_score", columnList = "risk_score")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EddReportEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "report_id", nullable = false, unique = true, length = 100)
    private String reportId;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status; // PENDING_REVIEW, APPROVED, REJECTED, EXPIRED
    
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL, PROHIBITED
    
    @Column(name = "risk_score", nullable = false)
    private Double riskScore; // 0-100
    
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
    
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate; // Typically 12 months from generation
    
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "approved_by", length = 100)
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "source_of_wealth_verified", nullable = false)
    @Builder.Default
    private Boolean sourceOfWealthVerified = false;
    
    @Column(name = "source_of_wealth_details", columnDefinition = "TEXT")
    private String sourceOfWealthDetails;
    
    @Column(name = "pep_screening_passed", nullable = false)
    @Builder.Default
    private Boolean pepScreeningPassed = true;
    
    @Column(name = "pep_findings", columnDefinition = "TEXT")
    private String pepFindings;
    
    @Column(name = "sanctions_screening_passed", nullable = false)
    @Builder.Default
    private Boolean sanctionsScreeningPassed = true;
    
    @Column(name = "sanctions_findings", columnDefinition = "TEXT")
    private String sanctionsFindings;
    
    @Column(name = "adverse_media_found", nullable = false)
    @Builder.Default
    private Boolean adverseMediaFound = false;
    
    @Column(name = "adverse_media_details", columnDefinition = "TEXT")
    private String adverseMediaDetails;
    
    @Column(name = "transaction_pattern_analysis", columnDefinition = "TEXT")
    private String transactionPatternAnalysis;
    
    @Column(name = "geographic_risk_assessment", columnDefinition = "TEXT")
    private String geographicRiskAssessment;
    
    @Column(name = "occupation_risk_assessment", columnDefinition = "TEXT")
    private String occupationRiskAssessment;
    
    @Column(name = "expected_transaction_volume", columnDefinition = "TEXT")
    private String expectedTransactionVolume;
    
    @Column(name = "actual_transaction_volume", columnDefinition = "TEXT")
    private String actualTransactionVolume;
    
    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;
    
    @Column(name = "monitoring_requirements", columnDefinition = "TEXT")
    private String monitoringRequirements;
    
    @Column(name = "approval_conditions", columnDefinition = "TEXT")
    private String approvalConditions;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
    
    @Column(name = "additional_documentation_required", columnDefinition = "TEXT")
    private String additionalDocumentationRequired;
    
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> riskFactors;
    
    @Column(name = "screening_results", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> screeningResults;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "analyst_notes", columnDefinition = "TEXT")
    private String analystNotes;
    
    @Column(name = "compliance_officer_notes", columnDefinition = "TEXT")
    private String complianceOfficerNotes;
}