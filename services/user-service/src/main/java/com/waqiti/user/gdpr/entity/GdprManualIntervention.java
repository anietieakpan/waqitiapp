package com.waqiti.user.gdpr.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Manual Intervention Entity
 *
 * Tracks failed GDPR operations requiring manual processing
 * to ensure 30-day SLA compliance with GDPR Article 12(3)
 */
@Entity
@Table(name = "gdpr_manual_interventions", indexes = {
        @Index(name = "idx_gdpr_ticket", columnList = "ticket_number", unique = true),
        @Index(name = "idx_gdpr_user_id", columnList = "user_id"),
        @Index(name = "idx_gdpr_status", columnList = "status"),
        @Index(name = "idx_gdpr_sla", columnList = "sla_deadline"),
        @Index(name = "idx_gdpr_created_at", columnList = "created_at"),
        @Index(name = "idx_gdpr_operation_type", columnList = "operation_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprManualIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 100)
    private String ticketNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, IN_PROGRESS, RESOLVED, ESCALATED

    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if SLA is breached
     */
    public boolean isSlaBreached() {
        return LocalDateTime.now().isAfter(slaDeadline) &&
               !"RESOLVED".equals(status);
    }

    /**
     * Get days until SLA deadline
     */
    public long getDaysUntilSlaDeadline() {
        return java.time.Duration.between(
                LocalDateTime.now(),
                slaDeadline
        ).toDays();
    }

    /**
     * Get days since creation
     */
    public long getDaysSinceCreation() {
        return java.time.Duration.between(
                createdAt,
                LocalDateTime.now()
        ).toDays();
    }
}
