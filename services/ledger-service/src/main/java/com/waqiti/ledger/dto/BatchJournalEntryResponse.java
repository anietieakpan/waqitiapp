package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJournalEntryResponse {
    private UUID batchId;
    private int totalRequested;
    private int successfulCount;
    private int errorCount;
    private List<JournalEntryResponse> successfulEntries;
    private List<BatchEntryError> errors;
    private LocalDateTime processedAt;
    private long processingTimeMs;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BatchEntryError {
    private int entryIndex;
    private String referenceNumber;
    private String errorMessage;
    private String errorCode;
}