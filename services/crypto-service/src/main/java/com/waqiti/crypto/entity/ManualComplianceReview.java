package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Manual Compliance Review Entity
 * Tracks transactions queued for manual compliance review
 * Supports SLA tracking, priority-based workflows, and audit trail
 */
@Entity
@Table(name = "manual_compliance_reviews", indexes = {
        @Index(name = "idx_review_transaction_id", columnList = "transactionId"),
        @Index(name = "idx_review_customer_id", columnList = "customerId"),
        @Index(name = "idx_review_status", columnList = "status"),
        @Index(name = "idx_review_priority", columnList = "priority"),
        @Index(name = "idx_review_sla_deadline", columnList = "slaDeadline"),
        @Index(name = "idx_review_correlation_id", columnList = "correlationId"),
        @Index(name = "idx_review_priority_status", columnList = "priority,status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ManualComplianceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, length = 2000)
    private String reviewReason;

    @Column(nullable = false, length = 50)
    private String riskScore; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, length = 50)
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, length = 50)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private Instant queuedAt;

    @Column(nullable = false)
    private Instant slaDeadline;

    @Column(length = 100)
    private String assignedTo;

    @Column
    private Instant assignedAt;

    @Column(length = 100)
    private String reviewedBy;

    @Column
    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String reviewComments;

    @Column(length = 50)
    private String decision; // APPROVED, REJECTED

    @Column
    private Boolean escalated = false;

    @Column
    private Instant escalatedAt;

    @Column(length = 1000)
    private String escalationReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;
}
