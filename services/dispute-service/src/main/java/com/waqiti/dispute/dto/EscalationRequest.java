package com.waqiti.dispute.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationRequest {

    private UUID disputeId;

    @NotBlank(message = "Escalation reason is required")
    @Size(min = 10, max = 1000, message = "Escalation reason must be between 10 and 1000 characters")
    private String escalationReason;

    private String escalateTo;
    private String priority;
    private String additionalNotes;
}
