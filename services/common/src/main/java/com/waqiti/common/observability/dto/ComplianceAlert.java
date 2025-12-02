package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ComplianceAlert {
    private String id;
    private String message;
    private AlertSeverity severity;
    private LocalDateTime timestamp;
    private String slaName;
    private boolean requiresAction;
}