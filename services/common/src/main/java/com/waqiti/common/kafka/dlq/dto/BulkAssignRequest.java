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
public class BulkAssignRequest {
    @NotEmpty(message = "caseIds cannot be empty")
    private List<String> caseIds;

    @NotBlank(message = "assignedTo is required")
    private String assignedTo;
}
