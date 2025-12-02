package com.waqiti.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Audit Alert entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "critical_audit_alerts")
public class CriticalAuditAlert {

    @Id
    private String id;

    private UUID criticalAlertId;

    private String originalAlertId;

    private String alertType;

    private AlertSeverity severity;

    private AlertStatus status;

    private String service;

    private String description;

    private String userId;

    private String resourceId;

    private String incidentCategory;

    private EscalationLevel escalationLevel;

    private LocalDateTime escalatedAt;

    private LocalDateTime detectedAt;

    private LocalDateTime resolvedAt;

    private String correlationId;

    private Map<String, Object> metadata;

    private String incidentId;

    private Boolean regulatoryReported;

    private Boolean requiresImmediateAction;

    private String assignedTo;

    private String resolution;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
