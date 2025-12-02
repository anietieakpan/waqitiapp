package com.waqiti.common.exception;

/**
 * Exception thrown when blockchain transaction broadcast fails.
 *
 * Common causes:
 * - Insufficient gas/fees
 * - Invalid transaction format
 * - Nonce conflicts
 * - Network congestion
 * - Transaction rejected by network
 *
 * Business logic should:
 * - Check if transaction should be retried
 * - Adjust gas price if needed
 * - Handle nonce management
 * - Notify user of failure
 * - Log for investigation
 *
 * @author Waqiti Platform
 */
public class TransactionBroadcastException extends RuntimeException {

    private final String transactionHash;
    private final String blockchain;
    private final String reason;
    private final boolean retryable;

    /**
     * Creates exception with message
     */
    public TransactionBroadcastException(String message) {
        super(message);
        this.transactionHash = null;
        this.blockchain = null;
        this.reason = null;
        this.retryable = false;
    }

    /**
     * Creates exception with message and cause
     */
    public TransactionBroadcastException(String message, Throwable cause) {
        super(message, cause);
        this.transactionHash = null;
        this.blockchain = null;
        this.reason = null;
        this.retryable = false;
    }

    /**
     * Creates exception with detailed transaction information
     */
    public TransactionBroadcastException(String message, String transactionHash, String blockchain,
                                        String reason, boolean retryable) {
        super(String.format("%s (txHash=%s, blockchain=%s, reason=%s, retryable=%s)",
            message, transactionHash, blockchain, reason, retryable));
        this.transactionHash = transactionHash;
        this.blockchain = blockchain;
        this.reason = reason;
        this.retryable = retryable;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getBlockchain() {
        return blockchain;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
