package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Suspicious Activity Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivityResponse {
    private UUID activityId;
    private UUID userId;
    private String activityType;
    private String description;
    private String severity;
    private String status;
    private Map<String, Object> details;
    private LocalDateTime detectedAt;
    private String detectedBy;
}
