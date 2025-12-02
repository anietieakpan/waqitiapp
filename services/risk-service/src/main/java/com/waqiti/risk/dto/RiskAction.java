package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Recommended or executed risk mitigation action
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAction {

    private String actionId;
    private String actionType; // ALLOW, BLOCK, REVIEW, CHALLENGE, STEP_UP_AUTH, LIMIT_AMOUNT

    private String status; // RECOMMENDED, PENDING, EXECUTED, FAILED
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL

    // Target
    private String transactionId;
    private String userId;
    private String merchantId;

    // Action details
    private String description;
    private String reason;
    private Map<String, Object> actionParameters;

    // Authorization
    private String authorizedBy; // USER_ID or SYSTEM
    private Instant authorizedAt;

    // Execution
    private String executedBy;
    private Instant executedAt;
    private String executionResult;
    private String executionError;

    // Timing
    private Instant createdAt;
    private Instant expiresAt;

    // Escalation
    private Boolean escalated;
    private String escalatedTo;
    private String escalationReason;

    private Map<String, Object> metadata;
}
