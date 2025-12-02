package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for processing a subpoena
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessSubpoenaRequest {

    @NotBlank(message = "Subpoena ID is required")
    private String subpoenaId;

    @NotNull(message = "Record types are required")
    private List<String> recordTypes;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private String batesPrefix;
    private boolean customerNotificationRequired;
    private String processedBy;
}
