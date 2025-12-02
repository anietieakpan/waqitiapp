package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for generated expense report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseReportDto {

    private String reportId;
    private String title;
    private String format;
    private String fileUrl;
    private String fileName;
    private Long fileSizeBytes;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private String status; // GENERATING, COMPLETED, FAILED
    private String downloadUrl;
    private Integer expenseCount;
    private String message;
}
