package com.waqiti.payment.domain;

/**
 * Enumeration of payment dispute statuses.
 * Represents the complete lifecycle of a dispute from initiation to resolution.
 */
public enum DisputeStatus {
    
    INITIATED("Dispute initiated by customer or detected by system"),
    UNDER_REVIEW("Dispute is being reviewed by compliance team"),
    AWAITING_EVIDENCE("Waiting for additional evidence from customer or merchant"),
    INVESTIGATION("Active investigation in progress"),
    ESCALATED_TO_CHARGEBACK("Dispute escalated to payment processor chargeback"),
    RESOLVED("Dispute resolved in favor of customer or merchant"),
    REJECTED("Dispute rejected due to insufficient evidence or validity"),
    WITHDRAWN("Dispute withdrawn by initiating party"),
    EXPIRED("Dispute expired without resolution"),
    CANCELLED("Dispute cancelled due to external factors");

    private final String description;

    DisputeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == REJECTED || this == WITHDRAWN || 
               this == EXPIRED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public boolean canTransitionTo(DisputeStatus newStatus) {
        return switch (this) {
            case INITIATED -> newStatus == UNDER_REVIEW || newStatus == REJECTED || newStatus == WITHDRAWN;
            case UNDER_REVIEW -> newStatus == AWAITING_EVIDENCE || newStatus == INVESTIGATION || 
                                newStatus == RESOLVED || newStatus == REJECTED || newStatus == WITHDRAWN;
            case AWAITING_EVIDENCE -> newStatus == INVESTIGATION || newStatus == UNDER_REVIEW || 
                                     newStatus == REJECTED || newStatus == EXPIRED;
            case INVESTIGATION -> newStatus == ESCALATED_TO_CHARGEBACK || newStatus == RESOLVED || 
                                 newStatus == REJECTED;
            case ESCALATED_TO_CHARGEBACK -> newStatus == RESOLVED || newStatus == REJECTED;
            default -> false; // Terminal states cannot transition
        };
    }
}