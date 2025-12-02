package com.waqiti.common.kafka.dlq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectCaseRequest {
    @NotBlank(message = "reason is required")
    private String reason;

    @NotBlank(message = "rejectionNotes is required")
    private String rejectionNotes;

    @NotBlank(message = "rejectedBy is required")
    private String rejectedBy;
}
