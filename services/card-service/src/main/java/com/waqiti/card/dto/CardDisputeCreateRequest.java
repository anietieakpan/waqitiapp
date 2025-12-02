package com.waqiti.card.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CardDisputeCreateRequest DTO - Request to create a dispute
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDisputeCreateRequest {

    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @NotBlank(message = "Dispute category is required")
    @Size(max = 50)
    private String disputeCategory;

    @NotBlank(message = "Dispute reason is required")
    private String disputeReason;

    private String cardholderExplanation;

    @NotNull(message = "Disputed amount is required")
    @DecimalMin(value = "0.01")
    private BigDecimal disputedAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currencyCode;

    private List<String> documentUrls;
}
