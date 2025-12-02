package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACH Transaction entity - NACHA-compliant ACH transaction record
 */
@Entity
@Table(name = "ach_transactions", indexes = {
    @Index(name = "idx_ach_txn_batch_id", columnList = "batch_id"),
    @Index(name = "idx_ach_txn_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_ach_txn_status", columnList = "status"),
    @Index(name = "idx_ach_txn_trace_number", columnList = "traceNumber")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking - prevents concurrent update conflicts
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ACHBatch batch;

    // New field for batch ID reference (used by ACHBatchProcessorService)
    @Column(name = "batch_id", insertable = false, updatable = false)
    private String batchId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false, length = 2)
    private String transactionCode; // NACHA transaction code (22, 27, etc.)

    @Column(nullable = false)
    private String transactionType; // DEBIT or CREDIT

    @Column(nullable = false, length = 500)
    private String accountNumber; // Encrypted

    @Column(nullable = false, length = 9)
    private String routingNumber;

    // New field for DFI account number (NACHA field name)
    @Column(length = 500)
    private String dfiAccountNumber;

    // New field for receiving DFI identification
    @Column(length = 8)
    private String receivingDFIIdentification;

    // New field for check digit
    @Column(length = 1)
    private String checkDigit;

    private String accountName;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    private String description;

    private String individualIdNumber;

    private String individualName;

    private String addendaRecord;

    // New field for discretionary data
    @Column(length = 2)
    private String discretionaryData;

    // New field for sequence number within batch
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    // New field for transaction hash (duplicate detection)
    @Column(length = 100)
    private String transactionHash;

    // New field for failure reason
    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @Column(unique = true, length = 15)
    private String traceNumber;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }

    /**
     * Nested TransactionStatus enum for ACH-specific statuses
     */
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        PROCESSED,
        FAILED,
        RETURNED,
        SETTLED
    }
}
