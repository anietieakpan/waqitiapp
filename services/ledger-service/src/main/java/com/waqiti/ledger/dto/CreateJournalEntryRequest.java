package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJournalEntryRequest {

    @NotBlank(message = "Reference number is required")
    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    private String referenceNumber;

    @NotBlank(message = "Entry type is required")
    private String entryType;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Entry date is required")
    private LocalDateTime entryDate;

    @NotNull(message = "Effective date is required")
    private LocalDateTime effectiveDate;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    private UUID accountingPeriodId;

    private String sourceSystem;
    private String sourceDocumentId;
    private String sourceDocumentType;

    @Builder.Default
    private Boolean approvalRequired = false;

    private String metadata;

    @NotEmpty(message = "At least one ledger entry is required")
    @Valid
    private List<CreateLedgerEntryRequest> ledgerEntries;

    private String createdBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CreateLedgerEntryRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotBlank(message = "Entry type is required")
    private String entryType;

    @NotNull(message = "Amount is required")
    private java.math.BigDecimal amount;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String narrative;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    private UUID contraAccountId;
    private String metadata;
}