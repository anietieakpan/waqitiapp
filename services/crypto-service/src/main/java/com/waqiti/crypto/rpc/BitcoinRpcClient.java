package com.waqiti.crypto.rpc;

import com.waqiti.crypto.dto.BitcoinTransaction;
import java.math.BigDecimal;

/**
 * Bitcoin RPC Client Interface
 * Provides methods for interacting with Bitcoin Core RPC
 */
public interface BitcoinRpcClient {

    /**
     * Get the amount received by address with minimum confirmations
     */
    BigDecimal getReceivedByAddress(String address, int minConfirmations);

    /**
     * Get unconfirmed balance for address
     */
    BigDecimal getUnconfirmedBalance(String address);

    /**
     * Get raw transaction hex
     */
    String getRawTransaction(String txHash);

    /**
     * Decode raw transaction into structured format
     */
    BitcoinTransaction decodeRawTransaction(String rawTransaction);

    /**
     * Get transaction details by hash
     */
    BitcoinTransaction getTransaction(String txHash);

    /**
     * Get current block count (height)
     */
    long getBlockCount();

    /**
     * Get block hash by height
     */
    String getBlockHash(long blockHeight);

    /**
     * Get block information by hash
     */
    BitcoinBlock getBlock(String blockHash);

    /**
     * Estimate transaction fee for given target blocks
     */
    BigDecimal estimateSmartFee(int targetBlocks);

    /**
     * Get mempool information
     */
    MempoolInfo getMempoolInfo();

    /**
     * List unspent transaction outputs
     */
    java.util.List<UTXO> listUnspent(String address, int minConfirmations, int maxConfirmations);

    /**
     * Broadcast transaction to network
     */
    String sendRawTransaction(String signedTransactionHex);

    /**
     * Get blockchain info
     */
    BlockchainInfo getBlockchainInfo();

    /**
     * Test connection to Bitcoin node
     */
    boolean ping();
}