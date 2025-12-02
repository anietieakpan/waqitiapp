package com.waqiti.compliance.entity;

import com.waqiti.compliance.enums.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Incident Escalation Entity
 *
 * Tracks all escalations of compliance incidents including escalation chain,
 * reasons, and audit trail.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Entity
@Table(name = "incident_escalations", indexes = {
    @Index(name = "idx_escalations_incident", columnList = "incident_id"),
    @Index(name = "idx_escalations_to", columnList = "escalated_to")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEscalation {

    @Id
    @Column(name = "escalation_id", nullable = false, length = 255)
    private String escalationId;

    @Column(name = "incident_id", nullable = false, length = 255)
    private String incidentId;

    @Column(name = "escalated_from", nullable = false, length = 255)
    private String escalatedFrom;

    @Column(name = "escalated_to", nullable = false, length = 255)
    private String escalatedTo;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "escalated_at", nullable = false)
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_by", nullable = false, length = 255)
    private String escalatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 50)
    private IncidentStatus previousStatus;

    /**
     * Check if escalation is to C-suite
     */
    public boolean isExecutiveEscalation() {
        return escalatedTo != null && (
            escalatedTo.contains("CHIEF_") ||
            escalatedTo.contains("CEO") ||
            escalatedTo.contains("CFO") ||
            escalatedTo.contains("CTO")
        );
    }

    /**
     * Check if this is an auto-escalation
     */
    public boolean isAutoEscalation() {
        return reason != null && reason.startsWith("AUTO_ESCALATION:");
    }
}
