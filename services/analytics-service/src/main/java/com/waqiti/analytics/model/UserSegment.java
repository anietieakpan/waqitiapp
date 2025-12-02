package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * User segment model for analytics
 */
@Entity
@Table(name = "user_segments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSegment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "segment_name", nullable = false, length = 100)
    private String segmentName;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "confidence", nullable = false)
    private double confidence;
    
    @Column(name = "segment_type", length = 50)
    private String segmentType;
    
    @Column(name = "priority", nullable = false)
    private int priority = 0;
    
    @Column(name = "active", nullable = false)
    private boolean active = true;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}