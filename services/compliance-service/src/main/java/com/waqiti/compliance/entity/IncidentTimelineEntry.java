package com.waqiti.compliance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Incident Timeline Entry Entity
 *
 * Provides complete chronological audit trail of all incident activities
 * for compliance and investigation purposes.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Entity
@Table(name = "incident_timeline", indexes = {
    @Index(name = "idx_timeline_incident", columnList = "incident_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentTimelineEntry {

    @Id
    @Column(name = "entry_id", nullable = false, length = 255)
    private String entryId;

    @Column(name = "incident_id", nullable = false, length = 255)
    private String incidentId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    /**
     * Check if this is a system-generated event
     */
    public boolean isSystemEvent() {
        return "SYSTEM".equals(createdBy) ||
               eventType.startsWith("AUTO_") ||
               eventType.contains("_AUTOMATED_");
    }

    /**
     * Check if this is a critical event
     */
    public boolean isCriticalEvent() {
        return eventType != null && (
            eventType.contains("ESCALAT") ||
            eventType.contains("BREACH") ||
            eventType.contains("EMERGENCY") ||
            eventType.contains("CRITICAL")
        );
    }
}
