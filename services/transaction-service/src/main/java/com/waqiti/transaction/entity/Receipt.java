package com.waqiti.transaction.entity;

import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing stored receipt metadata and information
 */
@Entity
@Table(name = "receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {

    @Id
    private UUID id;

    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private Long optLockVersion;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "security_hash", nullable = false)
    private String securityHash;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private ReceiptGenerationOptions.ReceiptFormat format;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private String version = "1.0";

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "cached", nullable = false)
    @Builder.Default
    private Boolean cached = false;

    @Column(name = "emailed", nullable = false)
    @Builder.Default
    private Boolean emailed = false;

    @Column(name = "email_count", nullable = false)
    @Builder.Default
    private Integer emailCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Indexes for performance
    @Table.Index(name = "idx_receipt_transaction_id", columnList = "transaction_id")
    @Table.Index(name = "idx_receipt_generated_at", columnList = "generated_at")
    @Table.Index(name = "idx_receipt_expires_at", columnList = "expires_at")
    public static class Indexes {}
}