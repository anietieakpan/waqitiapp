package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sanction_screening_records",
    indexes = {
        @Index(name = "idx_sanction_user_id", columnList = "user_id"),
        @Index(name = "idx_sanction_entity_id", columnList = "entity_id"),
        @Index(name = "idx_sanction_status", columnList = "status"),
        @Index(name = "idx_sanction_list_type", columnList = "list_type"),
        @Index(name = "idx_sanction_screened_at", columnList = "screened_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionScreeningRecord {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "screening_id", unique = true, nullable = false)
    private String screeningId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "entity_name", columnDefinition = "TEXT")
    private String entityName;
    
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    @Column(name = "country_code", length = 10)
    private String countryCode;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "is_sanctioned", nullable = false)
    private Boolean isSanctioned;
    
    @Column(name = "list_type", length = 50)
    private String listType;
    
    @Column(name = "match_type", length = 50)
    private String matchType;
    
    @Column(name = "match_score")
    private Double matchScore;
    
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    @Column(name = "matched_list_name", columnDefinition = "TEXT")
    private String matchedListName;
    
    @Column(name = "matched_entity_id")
    private String matchedEntityId;
    
    @Column(name = "matched_entity_name", columnDefinition = "TEXT")
    private String matchedEntityName;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "provider", length = 50)
    private String provider;
    
    @Column(name = "provider_reference_id")
    private String providerReferenceId;
    
    @Column(name = "manual_review_required")
    private Boolean manualReviewRequired;
    
    @Column(name = "reviewed_by")
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "review_decision", length = 50)
    private String reviewDecision;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        screenedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (isSanctioned == null) {
            isSanctioned = false;
        }
        if (manualReviewRequired == null) {
            manualReviewRequired = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}