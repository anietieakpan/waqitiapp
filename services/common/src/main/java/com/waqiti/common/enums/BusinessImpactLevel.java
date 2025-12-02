package com.waqiti.common.enums;

import lombok.Getter;

/**
 * Enterprise business impact level classification
 */
@Getter
public enum BusinessImpactLevel {
    CRITICAL("Critical", 5, "#dc3545", "Severe business disruption requiring immediate executive attention"),
    HIGH("High", 4, "#fd7e14", "Significant business impact requiring senior management attention"), 
    MEDIUM("Medium", 3, "#ffc107", "Moderate business impact requiring management attention"),
    LOW("Low", 2, "#17a2b8", "Minor business impact with standard response procedures"),
    MINIMAL("Minimal", 1, "#28a745", "Negligible business impact requiring documentation only");
    
    private final String displayName;
    private final int severity;
    private final String colorCode;
    private final String description;
    
    BusinessImpactLevel(String displayName, int severity, String colorCode, String description) {
        this.displayName = displayName;
        this.severity = severity;
        this.colorCode = colorCode;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getSeverity() {
        return severity;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    public String getDescription() {
        return description;
    }
}