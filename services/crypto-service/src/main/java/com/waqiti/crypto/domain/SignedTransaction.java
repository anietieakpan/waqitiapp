package com.waqiti.crypto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed Blockchain Transaction
 *
 * Contains transaction and signature components
 *
 * @author Waqiti Blockchain Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedTransaction {

    /**
     * Original transaction
     */
    private BlockchainTransaction transaction;

    /**
     * Signature (r component)
     */
    private String r;

    /**
     * Signature (s component)
     */
    private String s;

    /**
     * Recovery ID (v component for Ethereum)
     */
    private Integer v;

    /**
     * Raw signed transaction (hex encoded, ready for broadcast)
     */
    private String rawTransaction;

    /**
     * Transaction hash
     */
    private String transactionHash;
}
