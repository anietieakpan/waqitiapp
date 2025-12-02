package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "compliance_reports", indexes = {
        @Index(name = "idx_compliance_report_tx_id", columnList = "transactionId"),
        @Index(name = "idx_compliance_report_type", columnList = "complianceType"),
        @Index(name = "idx_compliance_report_status", columnList = "status"),
        @Index(name = "idx_compliance_report_filing_date", columnList = "filingDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ComplianceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String complianceType;

    @Column(columnDefinition = "TEXT")
    private String reportData;

    @Column
    private String correlationId;

    @Column(nullable = false)
    private Instant filingDate;

    @Column(nullable = false)
    private String status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
