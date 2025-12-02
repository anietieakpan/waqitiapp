package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ACH Batch creation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHBatchResult {

    private String batchId;

    private boolean success;

    private String message;

    private List<String> batchIds;

    private Integer totalBatches;

    private Integer totalTransactions;

    private BigDecimal totalAmount;

    private List<NACHAFileResult> nachaFiles;

    private LocalDateTime createdAt;

    private long processingTimeMs;

    private Integer errorCount;

    private List<String> errors;

    public boolean isSuccess() {
        return success;
    }

    public int getTransactionCount() {
        return totalTransactions != null ? totalTransactions : 0;
    }

    public String getErrorMessage() {
        return message;
    }
}
