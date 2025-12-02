package com.waqiti.common.fraud.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class AlertResolutionRequest {
    @NotBlank(message = "Resolved by is required")
    private String resolvedBy;

    @NotBlank(message = "Resolution is required")
    private String resolution;
    private String notes;
}