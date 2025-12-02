package com.waqiti.common.kafka.dlq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCaseRequest {
    @NotBlank(message = "resolutionNotes is required")
    private String resolutionNotes;

    @NotBlank(message = "resolutionAction is required")
    private String resolutionAction; // REPROCESS, FIX_DATA, SKIP, ESCALATE

    @NotBlank(message = "resolvedBy is required")
    private String resolvedBy;
}
