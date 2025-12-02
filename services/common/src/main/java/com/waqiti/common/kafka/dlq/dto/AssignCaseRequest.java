package com.waqiti.common.kafka.dlq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignCaseRequest {
    @NotBlank(message = "assignedTo is required")
    private String assignedTo;
}
