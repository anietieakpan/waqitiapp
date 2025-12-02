package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for exporting dispute data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    @NotBlank(message = "Export format is required")
    private String format; // CSV, EXCEL, PDF, JSON

    private DisputeStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
