package com.waqiti.virtualcard.domain;

import com.waqiti.virtualcard.domain.enums.ReplacementReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

/**
 * Entity for tracking card incidents (lost, stolen, etc.)
 */
@Entity
@Table(name = "card_incidents", indexes = {
    @Index(name = "idx_card_incident_card_id", columnList = "card_id"),
    @Index(name = "idx_card_incident_user_id", columnList = "user_id"),
    @Index(name = "idx_card_incident_type", columnList = "type"),
    @Index(name = "idx_card_incident_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CardIncident {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReplacementReason type;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Column(name = "location", length = 500)
    private String location;
    
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;
    
    @Column(name = "police_report_number")
    private String policeReportNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;
    
    @Column(name = "investigation_id")
    private String investigationId;
    
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    public enum IncidentStatus {
        REPORTED,
        UNDER_INVESTIGATION,
        RESOLVED,
        CLOSED,
        ESCALATED
    }
}