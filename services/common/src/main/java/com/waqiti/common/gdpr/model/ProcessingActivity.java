package com.waqiti.common.gdpr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Processing Activity Record
 *
 * Records of Processing Activities (ROPA) per GDPR Article 30
 * Required for all data controllers and processors
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Entity
@Table(name = "gdpr_processing_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_id", unique = true, length = 100)
    private String activityId;

    @Column(name = "activity_name", nullable = false)
    private String activityName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis; // Consent, Contract, Legal Obligation, etc.

    @Column(name = "data_categories", columnDefinition = "TEXT")
    private String dataCategories;

    @Column(name = "data_subject_categories", columnDefinition = "TEXT")
    private String dataSubjectCategories;

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;

    @Column(name = "recipients", columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "third_country_transfers")
    private Boolean thirdCountryTransfers = false;

    @Column(name = "security_measures", columnDefinition = "TEXT")
    private String securityMeasures;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;
}
