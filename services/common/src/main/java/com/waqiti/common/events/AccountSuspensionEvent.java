package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when an account is suspended.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountSuspensionEvent extends FinancialEvent {

    private UUID accountId;
    private String accountNumber;
    private String suspensionReason;
    private String suspensionType;  // TEMPORARY, PERMANENT, PENDING_INVESTIGATION
    private Instant suspendedAt;
    private UUID suspendedBy;
    private Instant suspensionExpiresAt;
    private String previousStatus;
    private boolean allowWithdrawals;
    private boolean allowDeposits;

    public static AccountSuspensionEvent create(UUID accountId, UUID userId, String reason) {
        return AccountSuspensionEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_SUSPENDED")
            .eventCategory("ACCOUNT")
            .accountId(accountId)
            .userId(userId)
            .suspensionReason(reason)
            .suspendedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateType("Account")
            .aggregateId(accountId)
            .build();
    }
}
