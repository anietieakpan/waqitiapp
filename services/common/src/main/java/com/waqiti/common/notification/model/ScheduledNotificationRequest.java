package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Request to schedule a notification for future delivery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledNotificationRequest {
    
    /**
     * The notification to schedule
     */
    private NotificationRequest notification;
    
    /**
     * When to send the notification
     */
    private Instant scheduledTime;
    
    /**
     * Timezone for the scheduled time
     */
    @Builder.Default
    private ZoneId timezone = ZoneId.of("UTC");
    
    /**
     * Recurrence pattern (if recurring)
     */
    private RecurrencePattern recurrence;
    
    /**
     * End date for recurring notifications
     */
    private Instant recurrenceEndDate;
    
    /**
     * Maximum number of occurrences
     */
    private Integer maxOccurrences;
    
    /**
     * Whether to skip if past scheduled time
     */
    @Builder.Default
    private boolean skipIfPast = true;
    
    /**
     * Grace period in minutes
     */
    @Builder.Default
    private int gracePeriodMinutes = 5;
    
    /**
     * Schedule metadata
     */
    private ScheduleMetadata metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurrencePattern {
        private RecurrenceType type;
        private int interval;
        private String cronExpression;
        private DaysOfWeek daysOfWeek;
        private Integer dayOfMonth;
        private Integer monthOfYear;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleMetadata {
        private String scheduleName;
        private String description;
        private String category;
        private boolean active;
        private String createdBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaysOfWeek {
        private boolean monday;
        private boolean tuesday;
        private boolean wednesday;
        private boolean thursday;
        private boolean friday;
        private boolean saturday;
        private boolean sunday;
    }
    
    public enum RecurrenceType {
        ONCE,
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY,
        CUSTOM
    }
}