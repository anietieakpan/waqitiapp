package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Security Awareness Audit Log
 *
 * Tracks all security awareness activities for compliance and auditing.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "security_awareness_audit_logs", indexes = {
        @Index(name = "idx_audit_employee", columnList = "employee_id"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@Builder(builderMethodName = "internalBuilder")
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAwarenessAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "audit_id")
    private UUID id;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private EventType eventType;

    @Column(name = "event_type_string", length = 100)
    private String eventTypeString;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "pci_requirement", length = 50)
    private String pciRequirement;

    @Column(name = "compliance_status", length = 50)
    private String complianceStatus;

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public enum EventType {
        TRAINING_STARTED,
        TRAINING_COMPLETED,
        TRAINING_FAILED,
        PHISHING_EMAIL_SENT,
        PHISHING_EMAIL_OPENED,
        PHISHING_LINK_CLICKED,
        PHISHING_DATA_SUBMITTED,
        PHISHING_REPORTED,
        ASSESSMENT_STARTED,
        ASSESSMENT_COMPLETED,
        ASSESSMENT_FAILED,
        CERTIFICATE_ISSUED,
        PROFILE_UPDATED,
        RISK_SCORE_CALCULATED,
        NEW_EMPLOYEE_ONBOARDING,
        TRAINING_ACKNOWLEDGED
    }

    /**
     * Custom builder that accepts String eventType
     */
    public static SecurityAwarenessAuditLogBuilder builder() {
        return internalBuilder();
    }

    public static class SecurityAwarenessAuditLogBuilder {
        public SecurityAwarenessAuditLogBuilder eventType(String eventTypeStr) {
            this.eventTypeString = eventTypeStr;
            try {
                this.eventType = EventType.valueOf(eventTypeStr);
            } catch (IllegalArgumentException e) {
                // If not a valid enum, just store the string
                this.eventType = EventType.PROFILE_UPDATED; // Default fallback
            }
            return this;
        }

        public SecurityAwarenessAuditLogBuilder eventData(java.util.Map<String, Object> dataMap) {
            if (dataMap != null && !dataMap.isEmpty()) {
                try {
                    this.eventData = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(dataMap);
                } catch (Exception e) {
                    this.eventData = dataMap.toString();
                }
            }
            return this;
        }
    }
}