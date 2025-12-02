package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SLA priority levels for service level agreement classification
 */
@Getter
@AllArgsConstructor
public enum SLAPriority {
    CRITICAL("Critical", 1, "#dc3545"),
    HIGH("High", 2, "#fd7e14"),
    MEDIUM("Medium", 3, "#ffc107"),
    LOW("Low", 4, "#28a745");
    
    private final String displayName;
    private final int priority;
    private final String colorCode;
}