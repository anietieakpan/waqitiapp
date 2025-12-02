package com.waqiti.compliance.fincen.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manual Filing Queue Entry Entity
 *
 * Represents a SAR that requires manual filing to FinCEN due to API unavailability.
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Entity
@Table(name = "manual_filing_queue", indexes = {
        @Index(name = "idx_manual_filing_status", columnList = "status"),
        @Index(name = "idx_manual_filing_priority", columnList = "priority"),
        @Index(name = "idx_manual_filing_sla_deadline", columnList = "sla_deadline"),
        @Index(name = "idx_manual_filing_sar_id", columnList = "sar_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ManualFilingQueueEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sar_id", nullable = false, length = 255)
    private String sarId;

    @Column(name = "sar_xml", nullable = false, columnDefinition = "TEXT")
    private String sarXml;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FilingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private FilingPriority priority;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "filing_number", length = 255)
    private String filingNumber;

    @Column(name = "filed_by", length = 255)
    private String filedBy;

    @Column(name = "jira_ticket_id", length = 100)
    private String jiraTicketId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
