package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ACH Batch entity for NACHA file processing
 */
@Entity
@Table(name = "ach_batches", indexes = {
    @Index(name = "idx_ach_batch_id", columnList = "batchId"),
    @Index(name = "idx_ach_batch_number", columnList = "batchNumber"),
    @Index(name = "idx_ach_batch_status", columnList = "status"),
    @Index(name = "idx_ach_batch_company_id", columnList = "companyId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHBatch {

    @Id
    @Column(nullable = false, unique = true, length = 50)
    private String id; // Changed from Long to String for UUID-based IDs

    /**
     * Optimistic locking - prevents concurrent batch update conflicts
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // batchId is now an alias for id (backward compatibility)
    @Transient
    public String getBatchId() {
        return id;
    }

    public void setBatchId(String batchId) {
        this.id = batchId;
    }

    @Column(nullable = false)
    private String batchNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ACHBatchType batchType; // Use enum instead of string

    @Column(nullable = false)
    private String serviceClassCode; // SEC code (PPD, CCD, WEB, etc.)

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String companyId;

    @Column(length = 10)
    private String companyIdentification;

    @Column(length = 10)
    private String companyEntryDescription;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    // New field - renamed from effectiveDate for NACHA compliance
    @Column(name = "effective_entry_date")
    private LocalDate effectiveEntryDate;

    @Column(length = 8)
    private String originatingDFIIdentification;

    @Column(length = 500)
    private String batchDescription;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime updatedAt;

    // New fields for comprehensive batch tracking
    private LocalDateTime processingStartedAt;
    private LocalDateTime processingCompletedAt;
    private LocalDateTime processingFailedAt;
    private LocalDateTime completedAt;
    private LocalDateTime scheduledProcessingDate;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalDebitAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalCreditAmount = BigDecimal.ZERO;

    @Builder.Default
    private Integer transactionCount = 0;

    @Builder.Default
    private Integer entryCount = 0;

    // New fields for entry counts
    private Integer totalEntryCount;
    private Integer debitEntryCount;
    private Integer creditEntryCount;
    private Long entryHash;

    // New fields for processing results
    private Integer successfulTransactions;
    private Integer failedTransactions;

    // New fields for validation and failure tracking
    @Column(columnDefinition = "TEXT")
    private String validationErrors;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 1000)
    private String cancellationReason;

    private LocalDateTime cancelledAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String nachaFileContent;

    private String fileHash;

    // New field for metadata
    @ElementCollection
    @CollectionTable(name = "ach_batch_metadata", joinColumns = @JoinColumn(name = "batch_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private java.util.Map<String, String> metadata;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ACHTransaction> transactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        if (totalDebitAmount == null) {
            totalDebitAmount = BigDecimal.ZERO;
        }
        if (totalCreditAmount == null) {
            totalCreditAmount = BigDecimal.ZERO;
        }
        if (transactionCount == null) {
            transactionCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
