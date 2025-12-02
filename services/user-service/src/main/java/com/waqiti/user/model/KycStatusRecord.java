package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "kyc_status_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String userId;
    
    private String kycStatus;
    
    private String kycLevel;
    
    private Instant lastUpdated;
    
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }
}