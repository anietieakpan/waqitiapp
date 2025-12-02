package com.waqiti.kyc.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * KYC Profile Entity
 * 
 * @author Waqiti KYC Team
 * @version 3.0.0
 */
@Entity
@Table(name = "kyc_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycProfile {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "status")
    private String status;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_review")
    private boolean requiresReview;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}