package com.waqiti.ledger.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when ledger enters emergency freeze state
 * All services consuming ledger events must halt ledger-related operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEmergencyFreezeEvent {

    private UUID eventId = UUID.randomUUID();
    private String reason;
    private LocalDateTime timestamp;
    private String frozenBy;
    private String severity = "CRITICAL";
    private String actionRequired = "IMMEDIATE_CFO_CTO_INTERVENTION";

    /**
     * Additional context about the freeze
     */
    private Object freezeContext;
}
