package com.waqiti.notification.domain;

/**
 * Represents the priority levels for notifications
 */
public enum NotificationPriority {
    LOW("low", "Low Priority", "Non-urgent notifications"),
    MEDIUM("medium", "Medium Priority", "Standard notifications"),
    HIGH("high", "High Priority", "Important notifications"),
    URGENT("urgent", "Urgent Priority", "Critical notifications requiring immediate attention");

    private final String code;
    private final String displayName;
    private final String description;

    NotificationPriority(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get priority by code
     */
    public static NotificationPriority fromCode(String code) {
        for (NotificationPriority priority : values()) {
            if (priority.getCode().equals(code)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown notification priority code: " + code);
    }

    /**
     * Get the Firebase/FCM priority for this priority level
     */
    public String getFcmPriority() {
        switch (this) {
            case URGENT:
            case HIGH:
                return "high";
            case MEDIUM:
            case LOW:
            default:
                return "normal";
        }
    }

    /**
     * Check if this priority should bypass Do Not Disturb settings
     */
    public boolean bypassDoNotDisturb() {
        return this == URGENT || this == HIGH;
    }

    /**
     * Get the sound level for this priority
     */
    public String getSoundLevel() {
        switch (this) {
            case URGENT:
                return "critical";
            case HIGH:
                return "loud";
            case MEDIUM:
                return "normal";
            case LOW:
            default:
                return "soft";
        }
    }
}