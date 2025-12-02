package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event emitted when account compromise is confirmed.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountCompromiseEvent extends FinancialEvent {

    private UUID accountId;
    private String compromiseType;  // CREDENTIAL_LEAK, PHISHING, MALWARE, SOCIAL_ENGINEERING
    private String severityLevel;
    private List<String> affectedServices;
    private Instant compromisedAt;
    private Instant detectedAt;
    private boolean credentialsReset;
    private boolean accountFrozen;
    private String remediationStatus;

    public static AccountCompromiseEvent create(UUID accountId, UUID userId,
                                                 String compromiseType, String severityLevel) {
        return AccountCompromiseEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_COMPROMISE")
            .eventCategory("SECURITY")
            .accountId(accountId)
            .userId(userId)
            .compromiseType(compromiseType)
            .severityLevel(severityLevel)
            .detectedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(accountId)
            .build();
    }
}
