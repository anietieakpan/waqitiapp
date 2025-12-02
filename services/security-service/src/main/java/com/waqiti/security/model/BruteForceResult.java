package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Brute Force Detection Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BruteForceResult {

    private boolean detected;
    private Double confidence;
    private Integer attemptCount;
    private Integer timeWindowMinutes;
    private String targetUserId;
    private String sourceIpAddress;
    private String attackType;
    private Integer failureRate;
}
