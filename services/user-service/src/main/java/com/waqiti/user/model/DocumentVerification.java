package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "document_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    private String documentId;
    
    private String documentType;
    
    private boolean verified;
    
    private BigDecimal verificationScore;
    
    private String failureReason;
    
    private Instant verifiedAt;
    
    private Instant createdAt;
    
    public boolean isVerified() {
        return verified;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (verifiedAt == null) {
            verifiedAt = Instant.now();
        }
    }
}