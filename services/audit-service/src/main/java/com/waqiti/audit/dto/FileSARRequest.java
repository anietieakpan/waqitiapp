package com.waqiti.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * File SAR Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSARRequest {
    @NotNull
    private UUID userId;

    private UUID transactionId;

    @NotBlank
    private String activityType;

    @NotBlank
    private String description;

    private String severity;
    private Map<String, Object> details;
}
