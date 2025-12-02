package com.waqiti.dispute.entity;

/**
 * Dispute status enumeration
 */
public enum DisputeStatus {
    INITIATED("Dispute initiated by customer"),
    PENDING_EVIDENCE("Awaiting evidence submission"),
    UNDER_REVIEW("Under review by dispute team"),
    ESCALATED_TO_SUPERVISOR("Escalated to supervisor level"),
    ESCALATED_TO_MANAGER("Escalated to management"),
    ESCALATED_TO_LEGAL("Escalated to legal team"),
    REQUIRES_MANUAL_REVIEW("Requires manual intervention"),
    CHARGEBACK_INITIATED("Chargeback process started"),
    CHARGEBACK_DISPUTED("Chargeback being disputed"),
    RESOLVED("Dispute resolved"),
    CLOSED("Dispute closed"),
    REJECTED("Dispute rejected"),
    WITHDRAWN("Dispute withdrawn by customer"),
    OPEN();

    private final String description;

    DisputeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED || this == REJECTED || this == WITHDRAWN;
    }
}
