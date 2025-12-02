package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLAlertResponse {
    private UUID alertId;
    private String alertType;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String status; // OPEN, IN_REVIEW, RESOLVED, ESCALATED
    private String title;
    private String description;
    private UUID userId;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID assignedTo;
    private List<String> riskIndicators;
    private String resolution;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
}