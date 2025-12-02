package com.waqiti.common.transaction;

/**
 * Status of a participant in a distributed transaction
 */
public enum ParticipantStatus {
    PENDING,        // Initial state
    ENLISTED,       // Participant has been enlisted in the transaction
    PREPARING,      // In prepare phase
    PREPARED,       // Ready to commit
    PREPARE_FAILED, // Failed to prepare
    COMMITTING,     // Committing changes
    COMMITTED,      // Successfully committed
    COMMIT_FAILED,  // Failed to commit
    ABORTING,       // Rolling back
    ABORTED,        // Successfully rolled back
    ROLLING_BACK,   // Rolling back (alias for ABORTING)
    ROLLED_BACK,    // Successfully rolled back (alias for ABORTED)  
    ROLLBACK_FAILED,// Failed to roll back
    FAILED,         // Failed during operation
    COMPENSATING,   // Executing compensation (saga)
    COMPENSATED,    // Compensation completed (saga)
    TIMEOUT         // Operation timed out
}