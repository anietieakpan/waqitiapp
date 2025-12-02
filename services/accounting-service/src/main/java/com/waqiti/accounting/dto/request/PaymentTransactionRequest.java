package com.waqiti.accounting.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment transaction request for accounting")
public class PaymentTransactionRequest {

    @NotNull(message = "Transaction ID is required")
    @Schema(description = "Unique transaction identifier", required = true)
    @JsonProperty("transaction_id")
    private UUID transactionId;

    @NotNull(message = "User ID is required")
    @Schema(description = "User identifier", required = true)
    @JsonProperty("user_id")
    private UUID userId;

    @NotNull(message = "Merchant ID is required")
    @Schema(description = "Merchant identifier", required = true)
    @JsonProperty("merchant_id")
    private UUID merchantId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be greater than zero", inclusive = false)
    @DecimalMax(value = "999999999.9999", message = "Amount exceeds maximum allowed value")
    @Digits(integer = 10, fraction = 4, message = "Amount must have max 4 decimal places")
    @Schema(description = "Transaction amount (up to 4 decimal precision)", example = "100.0000", required = true)
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase 3-letter ISO 4217 code")
    @Schema(description = "Currency code (ISO 4217)", example = "USD", required = true)
    @JsonProperty("currency")
    private String currency;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Transaction description")
    @JsonProperty("description")
    private String description;

    @NotBlank(message = "Initiated by is required")
    @Schema(description = "User who initiated the transaction", required = true)
    @JsonProperty("initiated_by")
    private String initiatedBy;
}
