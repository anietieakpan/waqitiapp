package com.waqiti.compliance.model.sanctions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sanctions Update History - Change Tracking
 *
 * Tracks all changes to sanctions lists for audit compliance and alerting.
 * Records additions, modifications, and removals from OFAC, EU, and UN lists.
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * All changes to sanctions lists must be tracked and logged for regulatory
 * audit purposes. Compliance team must be notified of new additions.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctions_update_history", indexes = {
        @Index(name = "idx_update_history_source", columnList = "list_source"),
        @Index(name = "idx_update_history_detected", columnList = "detected_at"),
        @Index(name = "idx_update_history_type", columnList = "change_type")
})
public class SanctionsUpdateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // Update identification
    @Column(name = "list_source", nullable = false, length = 20)
    private String listSource; // OFAC, EU, UN

    @Column(name = "old_version_id", length = 50)
    private String oldVersionId;

    @Column(name = "new_version_id", nullable = false, length = 50)
    private String newVersionId;

    // Change summary
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType; // ADDED, MODIFIED, REMOVED

    @Column(name = "entity_count", nullable = false)
    private Integer entityCount;

    // Detailed changes (stored as JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes_json", columnDefinition = "jsonb")
    private Map<String, Object> changesJson;

    // Processing
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Notifications
    @Column(name = "compliance_team_notified")
    private Boolean complianceTeamNotified = false;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
        if (complianceTeamNotified == null) {
            complianceTeamNotified = false;
        }
    }
}
