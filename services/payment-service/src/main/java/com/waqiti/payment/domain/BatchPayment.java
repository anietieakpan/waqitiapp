package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "batch_payments", indexes = {
    @Index(name = "idx_batch_id", columnList = "batch_id", unique = true),
    @Index(name = "idx_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "batch_id", nullable = false, unique = true)
    private String batchId;

    @Column(name = "batch_type", nullable = false)
    private String batchType;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "payment_count")
    private Integer paymentCount;

    @Column(name = "processed_count")
    private Integer processedCount;

    @Column(name = "failed_count")
    private Integer failedCount;

    @Column(name = "success_rate", precision = 5, scale = 2)
    private Double successRate;

    @Column(name = "processing_mode", length = 50)
    private String processingMode;

    @Column(name = "priority", length = 50)
    private String priority;

    @Column(name = "auto_settle")
    private Boolean autoSettle;

    @Column(name = "settlement_date")
    private Instant settlementDate;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "validation_result", columnDefinition = "TEXT")
    private String validationResult;

    @Column(name = "fraud_screening_result", columnDefinition = "TEXT")
    private String fraudScreeningResult;

    @Column(name = "processing_result", columnDefinition = "TEXT")
    private String processingResult;

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Convert(converter = MapToJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void setBatchType(com.waqiti.payment.model.BatchType batchType) {
        this.batchType = batchType != null ? batchType.name() : null;
    }

    public void setProcessingMode(com.waqiti.payment.model.ProcessingMode processingMode) {
        this.processingMode = processingMode != null ? processingMode.name() : null;
    }

    public void setStatus(BatchStatus status) {
        this.status = status != null ? status.name() : null;
    }
}
