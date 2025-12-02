package com.waqiti.account.domain;

/**
 * Transaction status enumeration for account transactions
 *
 * <p>Represents the lifecycle states of a transaction from initiation to completion.
 * This enum provides status tracking for financial transactions with clear state
 * transitions and validation helpers.</p>
 *
 * <h3>Status Flow:</h3>
 * <pre>
 * INITIATED → PENDING → PROCESSING → COMPLETED
 *                    ↓
 *                  FAILED
 *                    ↓
 *             CANCELLED / REVERSED
 *                    ↓
 *                 ON_HOLD
 * </pre>
 *
 * <h3>Terminal States:</h3>
 * <ul>
 *   <li>COMPLETED - Transaction successfully completed</li>
 *   <li>FAILED - Transaction failed and cannot be retried</li>
 *   <li>CANCELLED - Transaction cancelled by user or system</li>
 *   <li>REVERSED - Transaction reversed (refund/chargeback)</li>
 *   <li>TIMEOUT - Transaction timed out</li>
 * </ul>
 *
 * <h3>Active States:</h3>
 * <ul>
 *   <li>INITIATED - Transaction created but not validated</li>
 *   <li>PENDING - Transaction pending processing</li>
 *   <li>PROCESSING - Transaction currently being processed</li>
 *   <li>ON_HOLD - Transaction on hold pending review</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 * @see com.waqiti.account.entity.Transaction
 */
public enum TransactionStatus {

    /**
     * Transaction has been initiated but not yet validated.
     * <p>This is the initial state when a transaction is created.
     * The transaction is awaiting validation of inputs, balances, and business rules.</p>
     *
     * <p><b>Next States:</b> PENDING, FAILED, CANCELLED</p>
     */
    INITIATED("Transaction initiated", true, false),

    /**
     * Transaction is pending processing (awaiting settlement).
     * <p>The transaction has been validated and is waiting to be processed.
     * Funds may be on hold during this state.</p>
     *
     * <p><b>Next States:</b> PROCESSING, FAILED, CANCELLED, ON_HOLD</p>
     */
    PENDING("Pending processing", true, false),

    /**
     * Transaction is currently being processed.
     * <p>The transaction is actively being executed. This is typically a short-lived
     * state between PENDING and COMPLETED/FAILED.</p>
     *
     * <p><b>Next States:</b> COMPLETED, FAILED, ON_HOLD</p>
     */
    PROCESSING("Processing", false, false),

    /**
     * Transaction completed successfully.
     * <p>This is a terminal state. The transaction has been successfully executed
     * and all funds have been transferred. No further state transitions are possible.</p>
     *
     * <p><b>Terminal State:</b> No further transitions</p>
     */
    COMPLETED("Completed", false, true),

    /**
     * Transaction failed due to validation or processing error.
     * <p>This is a terminal state. The transaction could not be completed due to
     * an error (e.g., insufficient funds, validation failure, system error).</p>
     *
     * <p><b>Terminal State:</b> No further transitions (may create new transaction)</p>
     */
    FAILED("Failed", false, true),

    /**
     * Transaction was cancelled by user or system.
     * <p>This is a terminal state. The transaction was explicitly cancelled
     * before completion. Any holds are released.</p>
     *
     * <p><b>Terminal State:</b> No further transitions</p>
     */
    CANCELLED("Cancelled", false, true),

    /**
     * Transaction was reversed (refund/chargeback).
     * <p>This is a terminal state. A previously completed transaction has been
     * reversed, typically due to a refund request or chargeback.</p>
     *
     * <p><b>Terminal State:</b> No further transitions</p>
     */
    REVERSED("Reversed", false, true),

    /**
     * Transaction is on hold pending review (compliance/fraud).
     * <p>The transaction has been flagged for manual review due to compliance,
     * fraud detection, or risk assessment concerns.</p>
     *
     * <p><b>Next States:</b> PROCESSING, FAILED, CANCELLED</p>
     */
    ON_HOLD("On hold", false, false),

    /**
     * Transaction timed out.
     * <p>This is a terminal state. The transaction exceeded the allowed processing
     * time and was automatically cancelled.</p>
     *
     * <p><b>Terminal State:</b> No further transitions (may create new transaction)</p>
     */
    TIMEOUT("Timeout", false, true);

    private final String description;
    private final boolean cancellable;
    private final boolean terminal;

    /**
     * Constructs a TransactionStatus with specified properties.
     *
     * @param description Human-readable description of the status
     * @param cancellable Whether the transaction can be cancelled in this state
     * @param terminal Whether this is a terminal (final) state
     */
    TransactionStatus(String description, boolean cancellable, boolean terminal) {
        this.description = description;
        this.cancellable = cancellable;
        this.terminal = terminal;
    }

    /**
     * Gets the human-readable description of this status.
     *
     * @return Description of the transaction status
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this status is terminal (no further state transitions possible).
     * <p>Terminal states indicate the transaction has reached a final state
     * and cannot transition to any other status.</p>
     *
     * <p><b>Terminal States:</b></p>
     * <ul>
     *   <li>COMPLETED - Successfully finished</li>
     *   <li>FAILED - Failed to complete</li>
     *   <li>CANCELLED - Explicitly cancelled</li>
     *   <li>REVERSED - Reversed/refunded</li>
     *   <li>TIMEOUT - Timed out</li>
     * </ul>
     *
     * @return true if this is a terminal state, false otherwise
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Checks if a transaction in this status can be cancelled.
     * <p>Only transactions in early stages (INITIATED, PENDING) can be cancelled.
     * Once processing begins or the transaction reaches a terminal state,
     * cancellation is no longer possible.</p>
     *
     * <p><b>Cancellable States:</b></p>
     * <ul>
     *   <li>INITIATED - Can be cancelled before validation</li>
     *   <li>PENDING - Can be cancelled before processing starts</li>
     * </ul>
     *
     * @return true if the transaction can be cancelled, false otherwise
     */
    public boolean isCancellable() {
        return cancellable;
    }

    /**
     * Checks if the transaction is active (not in a terminal state).
     * <p>Active transactions are still being processed or awaiting processing.
     * They may transition to other states, including terminal states.</p>
     *
     * <p>This is the inverse of {@link #isTerminal()}.</p>
     *
     * @return true if the transaction is active (not terminal), false if terminal
     */
    public boolean isActive() {
        return !terminal;
    }

    /**
     * Checks if the transaction is in a pending state (waiting for action).
     * <p>Pending states indicate the transaction is waiting for processing,
     * review, or other action.</p>
     *
     * @return true if status is PENDING or ON_HOLD, false otherwise
     */
    public boolean isPending() {
        return this == PENDING || this == ON_HOLD;
    }

    /**
     * Checks if the transaction is in a processing state.
     * <p>Processing states indicate active work is being performed on the transaction.</p>
     *
     * @return true if status is PROCESSING, false otherwise
     */
    public boolean isProcessing() {
        return this == PROCESSING;
    }

    /**
     * Checks if the transaction completed successfully.
     *
     * @return true if status is COMPLETED, false otherwise
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * Checks if the transaction failed.
     * <p>This includes explicit failures, cancellations, reversals, and timeouts.</p>
     *
     * @return true if status is FAILED, CANCELLED, REVERSED, or TIMEOUT
     */
    public boolean hasFailed() {
        return this == FAILED || this == CANCELLED || this == REVERSED || this == TIMEOUT;
    }

    /**
     * Validates if a transition from this status to the target status is allowed.
     * <p>Enforces valid state machine transitions to maintain data integrity.</p>
     *
     * <p><b>Valid Transitions:</b></p>
     * <ul>
     *   <li>INITIATED → PENDING, FAILED, CANCELLED</li>
     *   <li>PENDING → PROCESSING, FAILED, CANCELLED, ON_HOLD</li>
     *   <li>PROCESSING → COMPLETED, FAILED, ON_HOLD</li>
     *   <li>ON_HOLD → PROCESSING, FAILED, CANCELLED</li>
     *   <li>COMPLETED → REVERSED (only via explicit reversal operation)</li>
     * </ul>
     *
     * @param targetStatus The status to transition to
     * @return true if the transition is valid, false otherwise
     * @throws IllegalArgumentException if targetStatus is null
     */
    public boolean canTransitionTo(TransactionStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }

        // Cannot transition from terminal states (except COMPLETED can be REVERSED)
        if (this.isTerminal() && !(this == COMPLETED && targetStatus == REVERSED)) {
            return false;
        }

        // Cannot transition to the same status
        if (this == targetStatus) {
            return false;
        }

        // Define valid transitions
        switch (this) {
            case INITIATED:
                return targetStatus == PENDING ||
                       targetStatus == FAILED ||
                       targetStatus == CANCELLED;

            case PENDING:
                return targetStatus == PROCESSING ||
                       targetStatus == FAILED ||
                       targetStatus == CANCELLED ||
                       targetStatus == ON_HOLD ||
                       targetStatus == TIMEOUT;

            case PROCESSING:
                return targetStatus == COMPLETED ||
                       targetStatus == FAILED ||
                       targetStatus == ON_HOLD ||
                       targetStatus == TIMEOUT;

            case ON_HOLD:
                return targetStatus == PROCESSING ||
                       targetStatus == FAILED ||
                       targetStatus == CANCELLED ||
                       targetStatus == TIMEOUT;

            case COMPLETED:
                // Only reversal allowed from completed state
                return targetStatus == REVERSED;

            default:
                // Terminal states (except COMPLETED) cannot transition
                return false;
        }
    }

    /**
     * Returns a string representation of this status.
     * <p>Format: "STATUS_NAME (Description)"</p>
     *
     * @return String representation of the status
     */
    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}
