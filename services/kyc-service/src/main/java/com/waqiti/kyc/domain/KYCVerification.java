package com.waqiti.kyc.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "kyc_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KYCVerification extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KYCStatus status = KYCStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationLevel verificationLevel = VerificationLevel.BASIC;

    @Column(nullable = false)
    private String provider = "ONFIDO";

    private String providerId;

    private String providerStatus;

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VerificationDocument> documents = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "kyc_verification_checks", joinColumns = @JoinColumn(name = "verification_id"))
    @MapKeyColumn(name = "check_type")
    @Column(name = "check_result")
    private Map<String, String> checks = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private LocalDateTime verifiedAt;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Integer attemptCount = 0;

    private LocalDateTime lastAttemptAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(columnDefinition = "json")
    private String metadata;

    @Column(columnDefinition = "json")
    private String riskScore;

    @Column(columnDefinition = "json")
    private String additionalInfo;
    
    @Column(nullable = false)
    private Boolean reminderSent = false;
    
    private LocalDateTime completedAt;
    
    private String providerReference;
    
    private String notes;

    public enum Status {
        PENDING,
        IN_PROGRESS,
        VERIFIED,
        REJECTED,
        MANUAL_REVIEW,
        FAILED,
        EXPIRED
    }
    
    public enum KYCStatus {
        PENDING,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        EXPIRED,
        REQUIRES_ADDITIONAL_INFO
    }

    public enum VerificationLevel {
        BASIC,      // Email + Phone verification
        STANDARD,   // ID Document verification
        ENHANCED    // ID + Proof of Address + Enhanced Due Diligence
    }

    @PrePersist
    @PreUpdate
    public void updateTimestamps() {
        if (status == KYCStatus.APPROVED && verifiedAt == null) {
            verifiedAt = LocalDateTime.now();
            expiresAt = LocalDateTime.now().plusYears(1); // KYC valid for 1 year
        }
    }

    public void addDocument(VerificationDocument document) {
        documents.add(document);
        document.setVerification(this);
    }

    public void removeDocument(VerificationDocument document) {
        documents.remove(document);
        document.setVerification(null);
    }

    public void incrementAttempt() {
        this.attemptCount++;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean canRetry() {
        return attemptCount < 3 && status != KYCStatus.APPROVED;
    }
}