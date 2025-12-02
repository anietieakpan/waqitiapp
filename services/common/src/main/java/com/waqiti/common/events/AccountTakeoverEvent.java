package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when account takeover is detected or suspected.
 *
 * This is a critical security event requiring immediate action.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountTakeoverEvent extends FinancialEvent {

    private UUID accountId;
    private String detectionMethod;  // ML_MODEL, RULE_BASED, MANUAL_REPORT
    private String severityLevel;  // LOW, MEDIUM, HIGH, CRITICAL
    private String suspiciousActivity;
    private String ipAddress;
    private String userAgent;
    private String location;
    private Instant detectedAt;
    private boolean accountLocked;
    private boolean notificationSent;
    private Map<String, Object> evidenceData;
    private String investigationStatus;

    public static AccountTakeoverEvent create(UUID accountId, UUID userId,
                                               String detectionMethod, String severityLevel) {
        return AccountTakeoverEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_TAKEOVER_DETECTED")
            .eventCategory("SECURITY")
            .accountId(accountId)
            .userId(userId)
            .detectionMethod(detectionMethod)
            .severityLevel(severityLevel)
            .detectedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateType("Account")
            .aggregateId(accountId)
            .build();
    }

    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(severityLevel);
    }
}
