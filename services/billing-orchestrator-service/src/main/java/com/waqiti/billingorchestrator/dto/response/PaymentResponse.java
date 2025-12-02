package com.waqiti.billingorchestrator.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Payment
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment response with transaction details")
public class PaymentResponse {

    @Schema(description = "Payment UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Invoice UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID invoiceId;

    @Schema(description = "Transaction ID from payment processor", example = "ch_1234567890abcdef")
    private String transactionId;

    @Schema(description = "Payment status", example = "COMPLETED")
    private String status;

    @Schema(description = "Payment amount", example = "150.50")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "USD")
    private String currency;

    @Schema(description = "Payment method type", example = "CREDIT_CARD")
    private String paymentMethod;

    @Schema(description = "Last 4 digits of payment method", example = "4242")
    private String paymentMethodLast4;

    @Schema(description = "Payment processor", example = "STRIPE")
    private String processor;

    @Schema(description = "Payment date", example = "2025-02-15T14:30:00")
    private LocalDateTime paymentDate;

    @Schema(description = "Processing fee", example = "4.35")
    private BigDecimal processingFee;

    @Schema(description = "Net amount (after fees)", example = "146.15")
    private BigDecimal netAmount;

    @Schema(description = "3D Secure used", example = "true")
    private Boolean threedsecureUsed;

    @Schema(description = "Receipt URL", example = "https://receipts.example.com/rcpt_123")
    private String receiptUrl;

    @Schema(description = "Failure reason (if failed)", example = "Insufficient funds")
    private String failureReason;

    @Schema(description = "Failure code", example = "INSUFFICIENT_FUNDS")
    private String failureCode;

    @Schema(description = "Can be refunded", example = "true")
    private Boolean refundable;

    @Schema(description = "Already refunded amount", example = "0.00")
    private BigDecimal refundedAmount;

    @Schema(description = "Created timestamp", example = "2025-02-15T14:30:00")
    private LocalDateTime createdAt;
}
