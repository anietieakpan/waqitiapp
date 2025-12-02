package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of policy operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResult {
    private boolean success;
    private String policyName;
    private PolicyType type;
    private LocalDateTime appliedAt;
    private String errorMessage;
}