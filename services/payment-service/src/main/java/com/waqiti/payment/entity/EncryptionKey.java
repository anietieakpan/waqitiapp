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
@Table(name = "encryption_keys",
    indexes = {
        @Index(name = "idx_encryption_key_key_id", columnList = "key_id", unique = true),
        @Index(name = "idx_encryption_key_status", columnList = "status"),
        @Index(name = "idx_encryption_key_created_at", columnList = "created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKey {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;
    
    @Column(name = "alias", nullable = false)
    private String alias;
    
    @Column(name = "key_type", nullable = false, length = 50)
    private String keyType;
    
    @Column(name = "algorithm", length = 50)
    private String algorithm;
    
    @Column(name = "key_size")
    private Integer keySize;
    
    @Column(name = "encrypted_data_key", columnDefinition = "TEXT")
    private String encryptedDataKey;
    
    @Column(name = "kms_key_arn", columnDefinition = "TEXT")
    private String kmsKeyArn;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "purpose", length = 100)
    private String purpose;
    
    @Column(name = "rotation_enabled", nullable = false)
    private Boolean rotationEnabled;
    
    @Column(name = "rotation_frequency_days")
    private Integer rotationFrequencyDays;
    
    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;
    
    @Column(name = "next_rotation_at")
    private LocalDateTime nextRotationAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
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
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}