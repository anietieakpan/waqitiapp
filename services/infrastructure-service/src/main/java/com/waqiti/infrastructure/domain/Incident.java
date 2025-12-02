package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "incidents")
public class Incident {
    @Id
    private String id;
    private IncidentType incidentType;
    private IncidentSeverity severity;
    private String description;
    private String reportedBy;
    private IncidentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private List<String> affectedServices;
    private List<String> responseActions;
    private String resolutionNotes;
    private String rootCause;
    private String preventativeActions;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class IncidentRequest {
    private IncidentType incidentType;
    private IncidentSeverity severity;
    private String description;
    private String reportedBy;
    private List<String> affectedServices;
    private String urgency;
    private String impact;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class IncidentResult {
    private String incidentId;
    private IncidentStatus status;
    private java.time.Duration responseTime;
    private List<String> actionsExecuted;
    private boolean resolved;
    private String escalationLevel;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class IncidentResponse {
    private List<String> actions;
    private String notes;
    private boolean resolved;
    private String escalatedTo;
    private LocalDateTime responseTime;
}

enum IncidentType {
    SERVICE_UNAVAILABLE,
    HIGH_ERROR_RATE,
    CAPACITY_EXCEEDED,
    SECURITY_BREACH,
    DATA_CORRUPTION,
    NETWORK_ISSUE,
    HARDWARE_FAILURE,
    SOFTWARE_BUG,
    CONFIGURATION_ERROR,
    PERFORMANCE_DEGRADATION
}

enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED
}