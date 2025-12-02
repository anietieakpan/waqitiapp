package com.waqiti.dispute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Evidence submitted for dispute resolution
 */
@Entity
@Table(name = "dispute_evidence", indexes = {
    @Index(name = "idx_evidence_dispute", columnList = "dispute_id"),
    @Index(name = "idx_evidence_type", columnList = "evidence_type"),
    @Index(name = "idx_evidence_submitted", columnList = "submitted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidence {
    
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    
    @Column(name = "dispute_id", nullable = false, length = 36)
    private String disputeId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;
    
    @Column(name = "submitted_by", nullable = false, length = 100)
    private String submittedBy;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    @Column(name = "document_url", length = 500)
    private String documentUrl;
    
    @Column(name = "document_hash", length = 64)
    private String documentHash; // SHA-256 hash for integrity
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "verified_by", length = 100)
    private String verifiedBy;
    
    @Column(name = "verification_notes", length = 500)
    private String verificationNotes;
    
    @Column(name = "weight_score")
    private Double weightScore; // Evidence weight in decision making (0.0 to 1.0)
    
    @Column(name = "is_system_generated", nullable = false)
    private boolean systemGenerated = false;
    
    @Column(name = "source_system", length = 100)
    private String sourceSystem;
    
    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "evidence_metadata", joinColumns = @JoinColumn(name = "evidence_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if evidence is verified
     */
    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }
    
    /**
     * Check if evidence is valid for use
     */
    public boolean isValidForUse() {
        return verificationStatus == VerificationStatus.VERIFIED ||
               verificationStatus == VerificationStatus.PENDING;
    }
    
    /**
     * Calculate evidence age in days
     */
    public long getAgeInDays() {
        return java.time.Duration.between(submittedAt, LocalDateTime.now()).toDays();
    }
}