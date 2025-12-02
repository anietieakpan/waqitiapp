package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "account_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String verificationType;
    
    private String verificationLevel;
    
    @Enumerated(EnumType.STRING)
    private VerificationStatus status;
    
    private BigDecimal verificationScore;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = com.waqiti.user.domain.JsonMapConverter.class)
    private Map<String, Object> verificationData;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String sessionId;
    
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