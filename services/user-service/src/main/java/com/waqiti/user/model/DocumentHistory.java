package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "document_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    private String documentId;
    
    private String documentType;
    
    private String action;
    
    private boolean verified;
    
    private BigDecimal verificationScore;
    
    private String failureReason;
    
    private Instant timestamp;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
    
    public boolean isVerified() {
        return verified;
    }
}