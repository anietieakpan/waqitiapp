package com.waqiti.crypto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Blockchain Transaction Domain Object
 *
 * Represents a blockchain transaction for signing
 *
 * @author Waqiti Blockchain Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainTransaction {

    /**
     * Sender address
     */
    private String from;

    /**
     * Recipient address
     */
    private String to;

    /**
     * Transaction amount (in native currency)
     */
    private BigDecimal amount;

    /**
     * Transaction nonce (for Ethereum-based chains)
     */
    private Long nonce;

    /**
     * Gas price (in wei for Ethereum)
     */
    private BigDecimal gasPrice;

    /**
     * Gas limit
     */
    private Long gasLimit;

    /**
     * Transaction data (hex encoded)
     */
    private String data;

    /**
     * Chain ID (for Ethereum-based chains)
     */
    private Integer chainId;

    /**
     * Transaction value in wei (for Ethereum)
     */
    private BigDecimal value;

    /**
     * Transaction type (0 = legacy, 2 = EIP-1559)
     */
    private Integer type;

    /**
     * Max priority fee per gas (EIP-1559)
     */
    private BigDecimal maxPriorityFeePerGas;

    /**
     * Max fee per gas (EIP-1559)
     */
    private BigDecimal maxFeePerGas;
}
