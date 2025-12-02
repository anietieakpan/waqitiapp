package com.waqiti.billingorchestrator.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Invoice Generation operation
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for invoice generation operation")
public class InvoiceGenerationResponse {

    @Schema(description = "Operation success", example = "true")
    private Boolean success;

    @Schema(description = "Billing cycle UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID billingCycleId;

    @Schema(description = "Generated invoice UUID", example = "987fcdeb-51a2-43d7-9c8b-123456789abc")
    private UUID invoiceId;

    @Schema(description = "Invoice number", example = "INV-2025-0001")
    private String invoiceNumber;

    @Schema(description = "Number of line items", example = "5")
    private Integer lineItemCount;

    @Schema(description = "Invoice sent immediately", example = "true")
    private Boolean invoiceSent;

    @Schema(description = "Notification channels used", example = "[\"EMAIL\", \"SMS\"]")
    private List<String> notificationChannels;

    @Schema(description = "PDF generated", example = "true")
    private Boolean pdfGenerated;

    @Schema(description = "PDF download URL", example = "https://invoices.example.com/inv_123.pdf")
    private String pdfUrl;

    @Schema(description = "Generation timestamp", example = "2025-02-01T00:00:00")
    private LocalDateTime generatedAt;

    @Schema(description = "Processing time in milliseconds", example = "543")
    private Long processingTimeMs;

    @Schema(description = "Warnings or messages")
    private List<String> messages;
}
