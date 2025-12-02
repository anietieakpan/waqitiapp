package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "verification_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String sessionId;
    
    @Column(nullable = false)
    private String userId;
    
    private String verificationType;
    
    private boolean active;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String deviceId;
    
    private Instant startedAt;
    
    private Instant completedAt;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}