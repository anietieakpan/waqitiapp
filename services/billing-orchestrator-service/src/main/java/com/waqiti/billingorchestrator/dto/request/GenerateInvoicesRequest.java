package com.waqiti.billingorchestrator.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for generating invoices
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to generate invoices for billing cycles")
public class GenerateInvoicesRequest {

    @NotNull(message = "Cycle ID is required")
    @Schema(description = "Billing cycle ID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID cycleId;

    @Schema(description = "Invoice due date (overrides default)", example = "2025-02-15")
    private LocalDate dueDate;

    @Schema(description = "Send invoice immediately after generation", example = "true")
    private Boolean sendImmediately;

    @Schema(description = "Notification channels to use", example = "[\"EMAIL\", \"SMS\"]")
    private List<String> notificationChannels;

    @Schema(description = "Custom invoice notes", example = "Thank you for your business")
    private String invoiceNotes;

    @Schema(description = "Include detailed line items", example = "true")
    private Boolean includeDetailedLineItems;

    @Schema(description = "Generate PDF attachment", example = "true")
    private Boolean generatePdf;

    @Schema(description = "Language for invoice", example = "en")
    private String language;
}
