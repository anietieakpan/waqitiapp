package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Unspent Transaction Output (UTXO)
 * Represents an unspent output that can be used as input for new transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UTXO {
    
    /**
     * Transaction hash containing this output
     */
    private String txHash;
    
    /**
     * Output index within the transaction
     */
    private Integer outputIndex;
    
    /**
     * Amount of cryptocurrency in this output
     */
    private BigDecimal amount;
    
    /**
     * Number of confirmations for this UTXO
     */
    private Integer confirmations;
    
    /**
     * Script public key (locking script)
     */
    private String scriptPubKey;
    
    /**
     * Address that controls this UTXO
     */
    private String address;
    
    /**
     * Whether this UTXO is spendable (not locked by time/height)
     */
    private Boolean spendable;
    
    /**
     * Block height where this UTXO was created
     */
    private Long blockHeight;
    
    /**
     * Timestamp when this UTXO was created
     */
    private Long timestamp;
    
    /**
     * Size of this UTXO in bytes (for fee calculation)
     */
    private Integer size;
    
    /**
     * Whether this is a coinbase output
     */
    private Boolean coinbase;
    
    /**
     * Redeem script for P2SH outputs
     */
    private String redeemScript;
    
    /**
     * Witness script for SegWit outputs
     */
    private String witnessScript;
    
    /**
     * Calculate the effective value of this UTXO after deducting input fee
     */
    public BigDecimal getEffectiveValue(BigDecimal feeRate) {
        if (amount == null || feeRate == null) {
            return BigDecimal.ZERO;
        }
        
        // Estimate input size based on script type
        int inputSize = estimateInputSize();
        BigDecimal inputFee = feeRate.multiply(new BigDecimal(inputSize));
        
        return amount.subtract(inputFee).max(BigDecimal.ZERO);
    }
    
    /**
     * Estimate the size of input that would spend this UTXO
     */
    private int estimateInputSize() {
        if (scriptPubKey == null) {
            return 148; // Default P2PKH input size
        }
        
        // Simplified size estimation based on script type
        if (scriptPubKey.startsWith("76a914") && scriptPubKey.endsWith("88ac")) {
            return 148; // P2PKH
        } else if (scriptPubKey.startsWith("a914") && scriptPubKey.endsWith("87")) {
            return 296; // P2SH (assuming 2-of-3 multisig)
        } else if (scriptPubKey.startsWith("0014")) {
            return 68; // P2WPKH
        } else if (scriptPubKey.startsWith("0020")) {
            return 104; // P2WSH
        }
        
        return 148; // Default
    }
    
    /**
     * Check if this UTXO is mature (coinbase outputs need 100 confirmations)
     */
    public boolean isMature() {
        if (!Boolean.TRUE.equals(coinbase)) {
            return true; // Non-coinbase outputs are always mature
        }
        
        return confirmations != null && confirmations >= 100;
    }
    
    /**
     * Check if this UTXO is confirmed
     */
    public boolean isConfirmed() {
        return confirmations != null && confirmations > 0;
    }
    
    /**
     * Get unique identifier for this UTXO
     */
    public String getOutpoint() {
        return txHash + ":" + outputIndex;
    }
}