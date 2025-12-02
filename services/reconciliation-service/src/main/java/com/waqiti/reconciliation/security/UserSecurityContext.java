package com.waqiti.reconciliation.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User security context for audit events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityContext {
    private String userId;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private Instant timestamp;
}