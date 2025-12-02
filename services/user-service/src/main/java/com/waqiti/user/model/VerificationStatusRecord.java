package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "verification_status_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    private String verificationType;
    
    private String status;
    
    private String verificationLevel;
    
    private Instant lastVerificationDate;
    
    private Instant nextReviewDate;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}