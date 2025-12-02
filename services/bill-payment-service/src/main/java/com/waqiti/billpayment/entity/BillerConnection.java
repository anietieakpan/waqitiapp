package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a user's connection to a specific biller account
 * Links a Waqiti user to their external biller account for bill import and auto-pay
 */
@Entity
@Table(name = "biller_connections", indexes = {
        @Index(name = "idx_connection_user_id", columnList = "user_id"),
        @Index(name = "idx_connection_biller_id", columnList = "biller_id"),
        @Index(name = "idx_connection_status", columnList = "status"),
        @Index(name = "idx_connection_user_biller", columnList = "user_id, biller_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_biller_account", columnNames = {"user_id", "biller_id", "account_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "biller_id", nullable = false)
    private UUID billerId;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 200)
    private String accountHolderName;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ConnectionStatus status;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "auto_import_enabled", nullable = false)
    private Boolean autoImportEnabled = false;

    @Column(name = "last_import_at")
    private LocalDateTime lastImportAt;

    @Column(name = "last_import_status", length = 50)
    private String lastImportStatus;

    @Column(name = "next_import_at")
    private LocalDateTime nextImportAt;

    @Column(name = "import_frequency_days")
    private Integer importFrequencyDays = 7;

    @Column(name = "connection_failed_count")
    private Integer connectionFailedCount = 0;

    @Column(name = "last_connection_error", length = 500)
    private String lastConnectionError;

    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(name = "external_connection_id", length = 100)
    private String externalConnectionId;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    // Business logic methods

    public boolean isActive() {
        return status == ConnectionStatus.ACTIVE && isVerified;
    }

    public boolean needsReauthorization() {
        return status == ConnectionStatus.REAUTH_REQUIRED || connectionFailedCount >= 3;
    }

    public void markImportSuccess() {
        this.lastImportAt = LocalDateTime.now();
        this.lastImportStatus = "SUCCESS";
        this.connectionFailedCount = 0;
        this.lastConnectionError = null;
        this.status = ConnectionStatus.ACTIVE;

        if (importFrequencyDays != null) {
            this.nextImportAt = LocalDateTime.now().plusDays(importFrequencyDays);
        }
    }

    public void markImportFailed(String error) {
        this.lastImportAt = LocalDateTime.now();
        this.lastImportStatus = "FAILED";
        this.lastConnectionError = error;
        this.connectionFailedCount++;

        if (connectionFailedCount >= 3) {
            this.status = ConnectionStatus.REAUTH_REQUIRED;
        }
    }

    public void verify() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.status = ConnectionStatus.ACTIVE;
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    @PrePersist
    protected void onCreate() {
        if (isVerified == null) {
            isVerified = false;
        }
        if (autoImportEnabled == null) {
            autoImportEnabled = false;
        }
        if (importFrequencyDays == null) {
            importFrequencyDays = 7;
        }
        if (connectionFailedCount == null) {
            connectionFailedCount = 0;
        }
    }
}
