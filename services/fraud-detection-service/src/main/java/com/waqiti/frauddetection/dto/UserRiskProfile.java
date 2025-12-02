package com.waqiti.frauddetection.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User risk profile for fraud detection
 */
@Data
@Builder
public class UserRiskProfile {
    private String userId;
    private int totalTransactions;
    private long highRiskTransactions;
    private long mediumRiskTransactions;
    private double averageRiskScore;
    private LocalDateTime lastUpdated;
}