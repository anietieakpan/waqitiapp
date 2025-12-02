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
 * Event emitted when account restrictions are applied or modified.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountRestrictionsEvent extends FinancialEvent {

    private UUID accountId;
    private List<String> restrictions;  // WITHDRAWAL_DISABLED, DEPOSIT_DISABLED, TRANSFER_LIMITED, etc.
    private String restrictionReason;
    private Instant appliedAt;
    private Instant expiresAt;
    private UUID appliedBy;

    public static AccountRestrictionsEvent create(UUID accountId, UUID userId,
                                                   List<String> restrictions, String reason) {
        return AccountRestrictionsEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_RESTRICTIONS")
            .eventCategory("ACCOUNT")
            .accountId(accountId)
            .userId(userId)
            .restrictions(restrictions)
            .restrictionReason(reason)
            .appliedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(accountId)
            .build();
    }
}
