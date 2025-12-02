package com.waqiti.crypto.dto;

import com.waqiti.crypto.domain.BlockchainTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Transaction Sign Request DTO
 *
 * @author Waqiti Blockchain Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSignRequest {

    /**
     * User ID requesting signature
     */
    @NotNull(message = "User ID is required")
    private UUID userId;

    /**
     * Transaction to sign
     */
    @NotNull(message = "Transaction is required")
    private BlockchainTransaction transaction;

    /**
     * Transaction hash (for idempotency and audit)
     */
    @NotBlank(message = "Transaction hash is required")
    private String transactionHash;

    /**
     * Blockchain type (ETHEREUM, BITCOIN, BSC, POLYGON, etc.)
     */
    @NotBlank(message = "Blockchain type is required")
    private String blockchain;

    /**
     * Optional: Multi-sig configuration
     */
    private MultiSigConfig multiSigConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiSigConfig {
        private int requiredSignatures;
        private int totalSigners;
    }
}
