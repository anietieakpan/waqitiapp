package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for data breach incidents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBreach {
    private String breachId;
    private String type;
    private String severity;
    private LocalDateTime detectedAt;
    private LocalDateTime occurredAt;
    private String status;
    private String description;
    private Long affectedRecords;
    private List<String> affectedDataTypes;
    private List<String> affectedSystems;
    private String attackVector;
    private String breachSource;
    private String containmentStatus;
    private List<String> notificationsSent;
    private LocalDateTime reportedToAuthorities;
    private String investigationStatus;
    private String rootCause;
    private List<String> remediationActions;
    private Double estimatedDamage;
}