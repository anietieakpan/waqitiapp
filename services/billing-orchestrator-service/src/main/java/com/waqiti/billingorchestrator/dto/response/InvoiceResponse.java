package com.waqiti.billingorchestrator.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Invoice
 *
 * Complete invoice details
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Invoice response with complete details")
public class InvoiceResponse {

    @Schema(description = "Invoice UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Invoice number", example = "INV-2025-0001")
    private String invoiceNumber;

    @Schema(description = "Billing cycle UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID billingCycleId;

    @Schema(description = "Customer UUID", example = "456e7890-e89b-12d3-a456-426614174222")
    private UUID customerId;

    @Schema(description = "Invoice status", example = "SENT")
    private String status;

    @Schema(description = "Invoice date", example = "2025-02-01")
    private LocalDate invoiceDate;

    @Schema(description = "Due date", example = "2025-02-15")
    private LocalDate dueDate;

    @Schema(description = "Currency", example = "USD")
    private String currency;

    @Schema(description = "Subtotal amount", example = "142.49")
    private BigDecimal subtotal;

    @Schema(description = "Tax amount", example = "14.25")
    private BigDecimal taxAmount;

    @Schema(description = "Total amount", example = "156.74")
    private BigDecimal totalAmount;

    @Schema(description = "Amount paid", example = "100.00")
    private BigDecimal amountPaid;

    @Schema(description = "Amount due", example = "56.74")
    private BigDecimal amountDue;

    @Schema(description = "Line items")
    private List<InvoiceLineItem> lineItems;

    @Schema(description = "Payment history")
    private List<PaymentRecord> payments;

    @Schema(description = "PDF download URL", example = "https://invoices.example.com/inv_123.pdf")
    private String pdfUrl;

    @Schema(description = "Payment URL", example = "https://billing.example.com/pay/inv_123")
    private String paymentUrl;

    @Schema(description = "Created timestamp", example = "2025-02-01T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Sent timestamp", example = "2025-02-01T10:30:00")
    private LocalDateTime sentAt;

    /**
     * Nested DTO for invoice line items
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceLineItem {
        private UUID id;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String category;
    }

    /**
     * Nested DTO for payment records
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRecord {
        private UUID id;
        private BigDecimal amount;
        private LocalDateTime paymentDate;
        private String paymentMethod;
        private String status;
        private String transactionId;
    }
}
