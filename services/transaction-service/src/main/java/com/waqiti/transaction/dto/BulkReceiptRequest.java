package com.waqiti.transaction.dto;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk receipt downloads
 */
@Data
public class BulkReceiptRequest {

    @NotEmpty(message = "Transaction IDs are required")
    @Size(max = 50, message = "Cannot request more than 50 receipts at once")
    private List<UUID> transactionIds;

    @Valid
    private ReceiptGenerationOptions options;

    private boolean includeMetadata = true;
    private String archivePassword;
}