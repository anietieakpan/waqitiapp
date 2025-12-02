package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk import operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResponseDto {

    private Integer totalRows;
    private Integer successCount;
    private Integer failureCount;
    private Integer skippedCount;

    private List<ImportedExpense> successfulImports;
    private List<ImportError> errors;
    private List<String> warnings;

    private Boolean importCompleted;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportedExpense {
        private Integer rowNumber;
        private String expenseId;
        private String description;
        private String amount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        private Integer rowNumber;
        private String field;
        private String errorMessage;
        private String rowData;
    }
}
