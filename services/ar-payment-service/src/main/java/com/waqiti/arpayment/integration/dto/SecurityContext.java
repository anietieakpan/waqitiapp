package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * Security context for user from security service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityContext {

    private UUID userId;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Set<String> permissions;
    private Set<String> roles;
    private boolean restrictedMode;
    private String sessionId;
    private boolean requiresMfa;
    private Integer transactionLimit;
}
