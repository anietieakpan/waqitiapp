package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for wallet information from wallet-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    /**
     * Unique wallet identifier
     */
    @NotBlank(message = "Wallet ID is required")
    private String walletId;

    /**
     * Customer ID who owns the wallet
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Wallet type (DIGITAL, CRYPTO, FIAT, etc.)
     */
    @NotBlank(message = "Wallet type is required")
    private String walletType;

    /**
     * Current wallet status (ACTIVE, FROZEN, CLOSED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Current wallet balance
     */
    @NotNull(message = "Balance is required")
    private BigDecimal balance;

    /**
     * Currency code
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Whether wallet is frozen
     */
    private Boolean frozen;

    /**
     * Wallet creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Wallet provider (if applicable)
     */
    private String provider;

    /**
     * Whether wallet is verified
     */
    private Boolean verified;
}
