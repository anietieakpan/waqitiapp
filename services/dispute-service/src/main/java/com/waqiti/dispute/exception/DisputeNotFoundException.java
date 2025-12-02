package com.waqiti.dispute.exception;

import java.util.UUID;

/**
 * Exception thrown when a dispute is not found
 *
 * HTTP Status: 404 Not Found
 *
 * @author Waqiti Dispute Team
 */
public class DisputeNotFoundException extends DisputeServiceException {

    private final String disputeId;

    public DisputeNotFoundException(String disputeId) {
        super(String.format("Dispute not found: %s", disputeId), "DISPUTE_NOT_FOUND", 404);
        this.disputeId = disputeId;
    }

    public DisputeNotFoundException(UUID disputeId) {
        this(disputeId.toString());
    }

    public String getDisputeId() {
        return disputeId;
    }
}
