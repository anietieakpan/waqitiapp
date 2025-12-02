package com.waqiti.common.exception;

/**
 * Exception thrown when blockchain node is out of sync.
 *
 * Common causes:
 * - Node still syncing with network
 * - Network fork or chain reorganization
 * - Node fell behind network
 * - Database corruption
 *
 * This should trigger:
 * - Wait for sync to complete
 * - Failover to synced node
 * - Alert blockchain infrastructure team
 * - Monitor sync progress
 *
 * @author Waqiti Platform
 */
public class NodeSyncException extends RuntimeException {

    private final String blockchain;
    private final long currentBlock;
    private final long networkBlock;
    private final long blocksBehind;
    private final double syncPercentage;

    /**
     * Creates exception with message
     */
    public NodeSyncException(String message) {
        super(message);
        this.blockchain = null;
        this.currentBlock = -1;
        this.networkBlock = -1;
        this.blocksBehind = -1;
        this.syncPercentage = -1.0;
    }

    /**
     * Creates exception with message and cause
     */
    public NodeSyncException(String message, Throwable cause) {
        super(message, cause);
        this.blockchain = null;
        this.currentBlock = -1;
        this.networkBlock = -1;
        this.blocksBehind = -1;
        this.syncPercentage = -1.0;
    }

    /**
     * Creates exception with detailed sync information
     */
    public NodeSyncException(String message, String blockchain, long currentBlock, long networkBlock) {
        super(String.format("%s (blockchain=%s, currentBlock=%d, networkBlock=%d, behind=%d, sync=%.2f%%)",
            message, blockchain, currentBlock, networkBlock,
            networkBlock - currentBlock,
            (double) currentBlock / networkBlock * 100));
        this.blockchain = blockchain;
        this.currentBlock = currentBlock;
        this.networkBlock = networkBlock;
        this.blocksBehind = networkBlock - currentBlock;
        this.syncPercentage = (double) currentBlock / networkBlock * 100;
    }

    public String getBlockchain() {
        return blockchain;
    }

    public long getCurrentBlock() {
        return currentBlock;
    }

    public long getNetworkBlock() {
        return networkBlock;
    }

    public long getBlocksBehind() {
        return blocksBehind;
    }

    public double getSyncPercentage() {
        return syncPercentage;
    }
}
