package com.waqiti.common.kafka.dlq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkResolveRequest {
    @NotEmpty(message = "caseIds cannot be empty")
    private List<String> caseIds;

    @NotBlank(message = "resolutionNotes is required")
    private String resolutionNotes;

    @NotBlank(message = "resolutionAction is required")
    private String resolutionAction;

    @NotBlank(message = "resolvedBy is required")
    private String resolvedBy;
}
