package com.waqiti.common.error;

/**
 * Enterprise-grade error severity classification for comprehensive error management
 * Provides detailed severity levels with business impact assessment and automated escalation
 */
public enum ErrorSeverity {
    
    /**
     * Low severity - Informational issues that don't affect functionality
     * No immediate action required, logged for analysis
     */
    LOW(1, "Low", "Informational issue with no functional impact", 
        "LOG_FOR_REVIEW", false, 24, "info"),
    
    /**
     * Medium severity - Minor issues with limited impact
     * Should be addressed but not urgent
     */
    MEDIUM(2, "Medium", "Minor issue with limited functional impact", 
        "CREATE_TICKET", false, 8, "warn"),
    
    /**
     * High severity - Significant issues affecting functionality
     * Requires prompt attention from development team
     */
    HIGH(3, "High", "Significant issue affecting core functionality", 
        "NOTIFY_TEAM_LEAD", true, 4, "error"),
    
    /**
     * Critical severity - Major issues with severe impact
     * Requires immediate attention from senior engineers
     */
    CRITICAL(4, "Critical", "Major issue with severe business impact", 
        "NOTIFY_SENIOR_ENGINEERS", true, 1, "fatal"),
    
    /**
     * Emergency severity - System-wide failures
     * Requires immediate escalation to on-call team
     */
    EMERGENCY(5, "Emergency", "System-wide failure requiring immediate action", 
        "PAGE_ONCALL_IMMEDIATELY", true, 0, "fatal");
    
    private final int level;
    private final String displayName;
    private final String description;
    private final String escalationPath;
    private final boolean requiresImmediateAction;
    private final int resolutionTimeHours;
    private final String logLevel;
    
    ErrorSeverity(int level, String displayName, String description, 
                  String escalationPath, boolean requiresImmediateAction,
                  int resolutionTimeHours, String logLevel) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
        this.escalationPath = escalationPath;
        this.requiresImmediateAction = requiresImmediateAction;
        this.resolutionTimeHours = resolutionTimeHours;
        this.logLevel = logLevel;
    }
    
    /**
     * Get numeric severity level for comparison
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Get human-readable severity name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get detailed description of severity level
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get recommended escalation path for this severity
     */
    public String getEscalationPath() {
        return escalationPath;
    }
    
    /**
     * Check if this severity requires immediate action
     */
    public boolean requiresImmediateAction() {
        return requiresImmediateAction;
    }
    
    /**
     * Get expected resolution time in hours
     */
    public int getResolutionTimeHours() {
        return resolutionTimeHours;
    }
    
    /**
     * Get corresponding log level for this severity
     */
    public String getLogLevel() {
        return logLevel;
    }
    
    /**
     * Check if this severity is higher than another
     */
    public boolean isHigherThan(ErrorSeverity other) {
        return this.level > other.level;
    }
    
    /**
     * Check if this severity is lower than another
     */
    public boolean isLowerThan(ErrorSeverity other) {
        return this.level < other.level;
    }
    
    /**
     * Get severity from numeric level
     */
    public static ErrorSeverity fromLevel(int level) {
        for (ErrorSeverity severity : values()) {
            if (severity.level == level) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Invalid severity level: " + level);
    }
    
    /**
     * Get severity from string name (case-insensitive)
     */
    public static ErrorSeverity fromString(String name) {
        if (name == null) {
            return MEDIUM; // Default severity
        }
        
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match by display name
            for (ErrorSeverity severity : values()) {
                if (severity.displayName.equalsIgnoreCase(name)) {
                    return severity;
                }
            }
            return MEDIUM; // Default if not found
        }
    }
    
    /**
     * Determine severity based on exception type
     */
    public static ErrorSeverity fromException(Exception exception) {
        if (exception == null) {
            return LOW;
        }
        
        String className = exception.getClass().getSimpleName();
        
        // Critical exceptions
        if (className.contains("OutOfMemory") || 
            className.contains("StackOverflow") ||
            className.contains("SecurityException")) {
            return CRITICAL;
        }
        
        // High severity exceptions
        if (className.contains("SQLException") ||
            className.contains("IOException") ||
            className.contains("ConnectException") ||
            className.contains("TimeoutException")) {
            return HIGH;
        }
        
        // Medium severity exceptions
        if (className.contains("IllegalArgument") ||
            className.contains("IllegalState") ||
            className.contains("NullPointer")) {
            return MEDIUM;
        }
        
        // Default to LOW for unknown exceptions
        return LOW;
    }
    
    /**
     * Calculate severity based on business impact metrics
     */
    public static ErrorSeverity fromImpactMetrics(int affectedUsers, 
                                                  double financialImpact,
                                                  boolean customerFacing) {
        if (affectedUsers > 10000 || financialImpact > 100000 || 
            (customerFacing && affectedUsers > 1000)) {
            return EMERGENCY;
        }
        
        if (affectedUsers > 1000 || financialImpact > 10000 || 
            (customerFacing && affectedUsers > 100)) {
            return CRITICAL;
        }
        
        if (affectedUsers > 100 || financialImpact > 1000 || customerFacing) {
            return HIGH;
        }
        
        if (affectedUsers > 10 || financialImpact > 100) {
            return MEDIUM;
        }
        
        return LOW;
    }
    
    /**
     * Get notification channels for this severity
     */
    public String[] getNotificationChannels() {
        switch (this) {
            case EMERGENCY:
                return new String[]{"SMS", "PHONE", "SLACK", "EMAIL", "PAGERDUTY"};
            case CRITICAL:
                return new String[]{"SLACK", "EMAIL", "PAGERDUTY"};
            case HIGH:
                return new String[]{"SLACK", "EMAIL"};
            case MEDIUM:
                return new String[]{"EMAIL"};
            case LOW:
                return new String[]{"LOG"};
            default:
                return new String[]{"LOG"};
        }
    }
    
    /**
     * Get color code for UI display
     */
    public String getColorCode() {
        switch (this) {
            case EMERGENCY:
                return "#FF0000"; // Red
            case CRITICAL:
                return "#FF4500"; // Orange Red
            case HIGH:
                return "#FFA500"; // Orange
            case MEDIUM:
                return "#FFD700"; // Gold
            case LOW:
                return "#90EE90"; // Light Green
            default:
                return "#808080"; // Gray
        }
    }
    
    /**
     * Get priority score for queue processing
     */
    public int getPriorityScore() {
        return 100 * level; // Higher severity = higher priority
    }
    
    /**
     * Check if this severity should trigger automatic remediation
     */
    public boolean shouldAutoRemediate() {
        return level <= 2; // Only LOW and MEDIUM can be auto-remediated
    }
    
    /**
     * Get maximum retry attempts for this severity
     */
    public int getMaxRetryAttempts() {
        switch (this) {
            case LOW:
                return 5;
            case MEDIUM:
                return 3;
            case HIGH:
                return 2;
            case CRITICAL:
                return 1;
            case EMERGENCY:
                return 0; // No retries for emergency
            default:
                return 3;
        }
    }
}