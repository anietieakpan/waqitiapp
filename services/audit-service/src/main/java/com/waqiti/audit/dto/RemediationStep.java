package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationStep {
    private String stepId;
    private String description;
    private String assignedTo;
    private LocalDateTime dueDate;
    private String status;
    private String completionNotes;
}
