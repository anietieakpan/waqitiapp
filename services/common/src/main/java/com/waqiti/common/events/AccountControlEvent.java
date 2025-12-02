package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when account control actions are performed.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountControlEvent extends FinancialEvent {

    private UUID accountId;
    private String controlAction;  // FREEZE, UNFREEZE, LOCK, UNLOCK, RESTRICT, UNRESTRICT
    private String controlReason;
    private UUID performedBy;
    private Instant performedAt;
    private boolean requiresApproval;
    private String approvalStatus;

    public static AccountControlEvent create(UUID accountId, UUID userId,
                                              String controlAction, String reason) {
        return AccountControlEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACCOUNT_CONTROL")
            .eventCategory("ACCOUNT")
            .accountId(accountId)
            .userId(userId)
            .controlAction(controlAction)
            .controlReason(reason)
            .performedAt(Instant.now())
            .timestamp(Instant.now())
            .aggregateId(accountId)
            .build();
    }
}
