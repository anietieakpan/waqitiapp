package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "secret_records",
    indexes = {
        @Index(name = "idx_secret_secret_name", columnList = "secret_name", unique = true),
        @Index(name = "idx_secret_status", columnList = "status"),
        @Index(name = "idx_secret_created_at", columnList = "created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretRecord {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "secret_name", nullable = false, unique = true)
    private String secretName;
    
    @Column(name = "secret_arn", columnDefinition = "TEXT")
    private String secretArn;
    
    @Column(name = "version_id")
    private String versionId;
    
    @Column(name = "secret_type", length = 50)
    private String secretType;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "kms_key_id")
    private String kmsKeyId;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "rotation_enabled", nullable = false)
    private Boolean rotationEnabled;
    
    @Column(name = "rotation_frequency_days")
    private Integer rotationFrequencyDays;
    
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
    
    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;
    
    @Column(name = "next_rotation_at")
    private LocalDateTime nextRotationAt;
    
    @Column(name = "access_count", nullable = false)
    private Long accessCount;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (rotationEnabled == null) {
            rotationEnabled = false;
        }
        if (accessCount == null) {
            accessCount = 0L;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}