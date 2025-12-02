package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Suspicious Activity Report (SAR) entity for FinCEN compliance.
 * Tracks suspicious cryptocurrency transactions that must be reported to regulators.
 */
@Entity
@Table(name = "suspicious_activity_reports", indexes = {
        @Index(name = "idx_sar_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_sar_customer_id", columnList = "customerId"),
        @Index(name = "idx_sar_status", columnList = "status"),
        @Index(name = "idx_sar_filing_date", columnList = "filingDate"),
        @Index(name = "idx_sar_correlation_id", columnList = "correlationId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SuspiciousActivityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String violationType;

    @Column
    private String correlationId;

    @Column(nullable = false)
    private Instant filingDate;

    @Column(nullable = false)
    private String status; // PENDING, FILED, FAILED

    @Column
    private String reportingInstitution;

    @Column
    private String institutionTin;

    @Column
    private String fincenConfirmationNumber;

    @Column(length = 4000)
    private String description;

    @Column(length = 2000)
    private String investigationNotes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant lastModifiedAt;

    @Version
    private Long version;
}
