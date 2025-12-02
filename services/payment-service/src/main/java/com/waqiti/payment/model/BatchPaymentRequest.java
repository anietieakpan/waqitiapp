package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Validated batch payment request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPaymentRequest {

    @NotBlank(message = "Batch ID is required")
    @Size(max = 100, message = "Batch ID must not exceed 100 characters")
    private String batchId;

    @NotNull(message = "Batch type is required")
    private BatchType batchType;

    @Size(max = 50, message = "Merchant ID must not exceed 50 characters")
    private String merchantId;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal totalAmount;

    @NotNull(message = "Payment count is required")
    @Min(value = 1, message = "Must have at least 1 payment")
    @Max(value = 10000, message = "Cannot exceed 10000 payments per batch")
    private Integer paymentCount;

    @NotNull(message = "Payments list is required")
    @NotEmpty(message = "Payments list cannot be empty")
    @Size(max = 10000, message = "Cannot exceed 10000 payments per batch")
    @Valid
    private List<PaymentInstruction> payments;

    @NotNull(message = "Processing mode is required")
    private ProcessingMode processingMode;

    @Pattern(regexp = "^(LOW|NORMAL|HIGH|URGENT)$", message = "Priority must be LOW, NORMAL, HIGH, or URGENT")
    private String priority;

    private boolean autoSettle;

    @Future(message = "Settlement date must be in the future")
    private Instant settlementDate;

    @Pattern(regexp = "^https?://.*", message = "Notification URL must be a valid HTTP/HTTPS URL")
    private String notificationUrl;

    private Map<String, Object> metadata;

    @NotBlank(message = "Requested by is required")
    @Size(max = 100, message = "Requested by must not exceed 100 characters")
    private String requestedBy;

    @PastOrPresent(message = "Created at cannot be in the future")
    private Instant createdAt;
}
