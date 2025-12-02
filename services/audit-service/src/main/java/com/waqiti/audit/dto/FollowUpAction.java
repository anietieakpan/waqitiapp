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
public class FollowUpAction {
    private String actionId;
    private String actionType;
    private String description;
    private LocalDateTime scheduledDate;
    private String assignedTo;
    private String status;
    private String outcome;
}
