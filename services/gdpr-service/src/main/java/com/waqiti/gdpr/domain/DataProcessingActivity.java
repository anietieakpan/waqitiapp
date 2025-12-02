package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "data_processing_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataProcessingActivity {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "activity_name", nullable = false)
    private String activityName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "processing_purpose", nullable = false)
    private String processingPurpose;
    
    @Column(name = "lawful_basis")
    @Enumerated(EnumType.STRING)
    private LawfulBasis lawfulBasis;
    
    @Column(name = "data_controller")
    private String dataController;
    
    @Column(name = "data_processor")
    private String dataProcessor;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "activity_data_categories",
        joinColumns = @JoinColumn(name = "activity_id")
    )
    @Column(name = "category")
    private List<String> dataCategories = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "activity_data_subjects",
        joinColumns = @JoinColumn(name = "activity_id")
    )
    @Column(name = "subject_type")
    private List<String> dataSubjects = new ArrayList<>();
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "activity_recipients",
        joinColumns = @JoinColumn(name = "activity_id")
    )
    @Column(name = "recipient")
    private List<String> recipients = new ArrayList<>();
    
    @Column(name = "retention_period")
    private String retentionPeriod;
    
    @Column(name = "security_measures", columnDefinition = "TEXT")
    private String securityMeasures;
    
    @Column(name = "third_country_transfers")
    private Boolean thirdCountryTransfers;
    
    @Column(name = "transfer_safeguards", columnDefinition = "TEXT")
    private String transferSafeguards;
    
    @Column(name = "is_high_risk")
    private Boolean isHighRisk;
    
    @Column(name = "dpia_required")
    private Boolean dpiaRequired;
    
    @Column(name = "dpia_completed")
    private Boolean dpiaCompleted;
    
    @Column(name = "dpia_reference")
    private String dpiaReference;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ActivityStatus status;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = ActivityStatus.ACTIVE;
        
        // Set next review date (typically annually)
        nextReviewDate = createdAt.plusYears(1);
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

enum ActivityStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    DISCONTINUED
}