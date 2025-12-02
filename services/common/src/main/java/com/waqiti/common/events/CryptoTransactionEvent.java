package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published for cryptocurrency transactions
 * 
 * Triggers:
 * - OFAC sanctions screening
 * - FinCEN CTR filing (>$10K)
 * - Suspicious Activity Report (SAR) filing
 * - Travel Rule compliance (>$3K)
 * - Blockchain analytics
 * 
 * @author Waqiti Crypto Platform
 * @version 1.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransactionEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Blockchain transaction hash
     */
    @JsonProperty("transaction_hash")
    private String transactionHash;
    
    /**
     * User ID initiating transaction
     */
    @JsonProperty("user_id")
    private String userId;
    
    /**
     * Cryptocurrency type: BTC, ETH, USDT, etc.
     */
    @JsonProperty("cryptocurrency")
    private String cryptocurrency;
    
    /**
     * Transaction amount in crypto
     */
    @JsonProperty("amount")
    private BigDecimal amount;
    
    /**
     * USD equivalent at transaction time
     */
    @JsonProperty("usd_equivalent")
    private BigDecimal usdEquivalent;
    
    /**
     * From wallet address
     */
    @JsonProperty("from_address")
    private String fromAddress;
    
    /**
     * To wallet address
     */
    @JsonProperty("to_address")
    private String toAddress;
    
    /**
     * Transaction date/time
     */
    @JsonProperty("transaction_date")
    private LocalDateTime transactionDate;
    
    /**
     * Transaction type: DEPOSIT, WITHDRAWAL, TRANSFER, TRADE
     */
    @JsonProperty("transaction_type")
    private String transactionType;
    
    /**
     * Network/chain: MAINNET, TESTNET, etc.
     */
    @JsonProperty("network")
    private String network;
    
    /**
     * Gas/fee paid
     */
    @JsonProperty("fee")
    private BigDecimal fee;
    
    /**
     * Number of confirmations
     */
    @JsonProperty("confirmations")
    private Integer confirmations;
    
    /**
     * Block number
     */
    @JsonProperty("block_number")
    private Long blockNumber;
}