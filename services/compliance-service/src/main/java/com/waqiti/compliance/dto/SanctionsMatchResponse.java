package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsMatchResponse {
    private UUID matchId;
    private String entityId;
    private String entityName;
    private String entityType;
    private String sanctionsList;
    private Double matchScore;
    private String status; // PENDING, CONFIRMED, FALSE_POSITIVE, RESOLVED
    private String resolution;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime detectedAt;
    private String notes;
}