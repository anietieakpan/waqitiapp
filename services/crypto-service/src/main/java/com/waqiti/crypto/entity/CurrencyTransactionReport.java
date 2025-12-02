package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Currency Transaction Report (CTR) entity for FinCEN compliance.
 * Required for crypto transactions exceeding $10,000.
 */
@Entity
@Table(name = "currency_transaction_reports", indexes = {
        @Index(name = "idx_ctr_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_ctr_customer_id", columnList = "customerId"),
        @Index(name = "idx_ctr_transaction_date", columnList = "transactionDate"),
        @Index(name = "idx_ctr_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CurrencyTransactionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal transactionAmount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant transactionDate;

    @Column
    private String correlationId;

    @Column(nullable = false)
    private String status;

    @Column
    private String reportingInstitution;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private Long version;
}
