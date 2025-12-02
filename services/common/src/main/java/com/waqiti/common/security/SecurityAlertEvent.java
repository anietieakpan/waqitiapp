package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Security alert event for immediate security notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlertEvent {
    private String accountId;
    private String userId;
    private String message;
    private SecurityAlertLevel alertLevel;
    private Instant timestamp;
}