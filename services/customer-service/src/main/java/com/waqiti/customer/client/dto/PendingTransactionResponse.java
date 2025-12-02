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
 * Response DTO for pending transaction from ledger-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransactionResponse {

    /**
     * Transaction identifier
     */
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    /**
     * Account identifier
     */
    @NotBlank(message = "Account ID is required")
    private String accountId;

    /**
     * Transaction type (DEBIT, CREDIT)
     */
    @NotBlank(message = "Transaction type is required")
    private String transactionType;

    /**
     * Transaction amount
     */
    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /**
     * Currency code
     */
    @NotBlank(message = "Currency is required")
    private String currency;

    /**
     * Transaction status
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Transaction description
     */
    private String description;

    /**
     * Reference number
     */
    private String referenceNumber;

    /**
     * Transaction category
     */
    private String category;

    /**
     * Merchant name (if applicable)
     */
    private String merchantName;

    /**
     * Transaction creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Expected settlement date
     */
    private LocalDateTime expectedSettlementDate;
}
