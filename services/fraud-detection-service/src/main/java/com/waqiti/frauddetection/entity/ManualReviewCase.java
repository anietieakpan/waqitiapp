package com.waqiti.frauddetection.entity;

import com.waqiti.frauddetection.service.ManualReviewQueueService.ReviewPriority;
import com.waqiti.frauddetection.service.ManualReviewQueueService.ReviewStatus;
import com.waqiti.frauddetection.service.ManualReviewQueueService.ReviewDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Manual Review Case Entity
 *
 * Represents a fraud case that requires manual review by an analyst.
 * Tracks SLA compliance, assignment, and resolution.
 */
@Entity
@Table(name = "manual_review_cases", indexes = {
    @Index(name = "idx_case_id", columnList = "caseId"),
    @Index(name = "idx_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_assigned_to", columnList = "assignedTo"),
    @Index(name = "idx_sla_deadline", columnList = "slaDeadline"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String caseId;

    @Column(nullable = false, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(nullable = false)
    private Double riskScore;

    private Double mlScore;

    private Double ruleScore;

    @ElementCollection
    @CollectionTable(name = "review_case_triggered_rules",
        joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "rule")
    private List<String> triggeredRules;

    @Column(length = 1000)
    private String reviewReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime slaDeadline;

    @Column
    private String assignedTo;

    @Column
    private LocalDateTime assignedAt;

    @Column
    private String reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReviewDecision decision;

    @Column(length = 2000)
    private String reviewNotes;

    @Column
    private Long reviewTimeMinutes;

    @Column(nullable = false)
    private boolean slaViolated = false;

    @Column(nullable = false)
    private boolean isBlocked = false;

    @Column(nullable = false)
    private boolean escalated = false;

    @Column(length = 500)
    private String escalationReason;

    @Column
    private LocalDateTime escalatedAt;

    @ElementCollection
    @CollectionTable(name = "review_case_metadata",
        joinColumns = @JoinColumn(name = "case_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, Object> metadata;

    @Column
    private LocalDateTime lastUpdatedAt;

    @PrePersist
    protected void onCreate() {
        lastUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
}
