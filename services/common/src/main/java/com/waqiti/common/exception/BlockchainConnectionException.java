package com.waqiti.common.exception;

/**
 * Exception thrown when connection to blockchain node fails.
 *
 * Common causes:
 * - Blockchain node is down
 * - Network connectivity issues
 * - Node synchronization problems
 * - RPC endpoint unavailable
 *
 * This should trigger:
 * - Circuit breaker activation
 * - Failover to backup node
 * - Retry with exponential backoff
 * - Alert blockchain infrastructure team
 *
 * @author Waqiti Platform
 */
public class BlockchainConnectionException extends RuntimeException {

    private final String blockchain;
    private final String nodeUrl;
    private final String operation;

    /**
     * Creates exception with message
     */
    public BlockchainConnectionException(String message) {
        super(message);
        this.blockchain = null;
        this.nodeUrl = null;
        this.operation = null;
    }

    /**
     * Creates exception with message and cause
     */
    public BlockchainConnectionException(String message, Throwable cause) {
        super(message, cause);
        this.blockchain = null;
        this.nodeUrl = null;
        this.operation = null;
    }

    /**
     * Creates exception with detailed blockchain information
     */
    public BlockchainConnectionException(String message, String blockchain, String nodeUrl, String operation) {
        super(String.format("%s (blockchain=%s, node=%s, operation=%s)",
            message, blockchain, nodeUrl, operation));
        this.blockchain = blockchain;
        this.nodeUrl = nodeUrl;
        this.operation = operation;
    }

    /**
     * Creates exception with detailed blockchain information and cause
     */
    public BlockchainConnectionException(String message, String blockchain, String nodeUrl,
                                        String operation, Throwable cause) {
        super(String.format("%s (blockchain=%s, node=%s, operation=%s)",
            message, blockchain, nodeUrl, operation), cause);
        this.blockchain = blockchain;
        this.nodeUrl = nodeUrl;
        this.operation = operation;
    }

    public String getBlockchain() {
        return blockchain;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public String getOperation() {
        return operation;
    }
}
