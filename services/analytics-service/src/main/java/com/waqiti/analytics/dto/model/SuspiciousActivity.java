package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Suspicious activity model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivity {
    private UUID activityId;
    private String activityType;
    private String description;
    private LocalDateTime detectedAt;
    private BigDecimal amount;
    private String merchantName;
    private String location;
    private BigDecimal riskScore;
    private String status; // INVESTIGATING, CLEARED, CONFIRMED, BLOCKED
}