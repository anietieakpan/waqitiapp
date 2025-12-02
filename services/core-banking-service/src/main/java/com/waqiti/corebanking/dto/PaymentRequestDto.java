package com.waqiti.corebanking.dto;

import com.waqiti.corebanking.validation.CurrencyCode;
import com.waqiti.corebanking.validation.TransactionAmount;
import com.waqiti.corebanking.validation.ValidUUID;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to process a payment between users")
public class PaymentRequestDto {

    @NotBlank(message = "Transaction ID is required")
    @ValidUUID(allowPrefix = true)
    @Schema(description = "Unique transaction identifier", example = "txn-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String transactionId;

    @NotBlank(message = "Idempotency key is required for duplicate prevention")
    @Size(min = 1, max = 100, message = "Idempotency key must be between 1 and 100 characters")
    @ValidUUID(allowPrefix = true)
    @Schema(description = "Idempotency key to prevent duplicate processing. Must be unique per request. Use UUID recommended.",
            example = "idem-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String idempotencyKey;

    @NotBlank(message = "From user ID is required")
    @ValidUUID(allowPrefix = true)
    @Schema(description = "Sender user identifier", example = "user-550e8400-e29b-41d4-a716-446655440000", required = true)
    private String fromUserId;

    @NotBlank(message = "To user ID is required")
    @ValidUUID(allowPrefix = true)
    @Schema(description = "Recipient user identifier", example = "user-750e8400-e29b-41d4-a716-446655440000", required = true)
    private String toUserId;

    @NotNull(message = "Amount is required")
    @TransactionAmount(min = "0.01", max = "1000000.00")
    @Schema(description = "Payment amount (max 1,000,000.00 for automatic processing)", example = "100.50", required = true)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @CurrencyCode
    @Schema(description = "ISO 4217 currency code", example = "USD", required = true)
    private String currency;

    @Schema(description = "Payment description", example = "Payment for services")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Schema(description = "Payment category", 
            example = "P2P",
            allowableValues = {"P2P", "MERCHANT", "BILL", "REFUND"})
    private String category = "P2P";

    @Schema(description = "Additional payment metadata")
    private Map<String, Object> metadata;
}