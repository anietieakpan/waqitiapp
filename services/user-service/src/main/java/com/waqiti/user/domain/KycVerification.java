package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * KYC Verification Entity
 * 
 * Tracks the complete KYC verification process lifecycle
 */
@Entity
@Table(name = "kyc_verifications", indexes = {
    @Index(name = "idx_kyc_verification_id", columnList = "verificationId"),
    @Index(name = "idx_kyc_user_id", columnList = "userId"),
    @Index(name = "idx_kyc_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "verification_id", unique = true, nullable = false, length = 100)
    private String verificationId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private KycStatus status;
    
    @Column(name = "kyc_level", length = 50)
    private String kycLevel;
    
    @Column(name = "verification_method", length = 100)
    private String verificationMethod;
    
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    
    @Column(name = "rejection_details", columnDefinition = "TEXT")
    private String rejectionDetails;
    
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    @Column(name = "error_code", length = 100)
    private String errorCode;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        lastModifiedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastModifiedAt = LocalDateTime.now();
        
        if (status == KycStatus.VERIFIED && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}