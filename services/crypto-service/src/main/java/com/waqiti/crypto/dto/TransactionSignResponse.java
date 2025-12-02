package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction Sign Response DTO
 *
 * @author Waqiti Blockchain Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSignResponse {

    /**
     * Transaction hash
     */
    private String transactionHash;

    /**
     * Signature in hexadecimal format
     */
    private String signatureHex;

    /**
     * Signature in Base64 format
     */
    private String signatureBase64;

    /**
     * Public key used for signing (hex format)
     */
    private String publicKeyHex;

    /**
     * Blockchain type
     */
    private String blockchain;

    /**
     * Whether signature was verified
     */
    private boolean verified;

    /**
     * KMS Key ID used for signing
     */
    private String kmsKeyId;

    /**
     * Success indicator
     */
    private boolean success;

    /**
     * Error message (if any)
     */
    private String errorMessage;

    /**
     * Recovery ID (for Ethereum signatures)
     */
    private Integer recoveryId;
}
