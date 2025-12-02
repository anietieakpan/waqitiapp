package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationNotificationRequest {

    private UUID jobId;
    
    private LocalDate reconciliationDate;
    
    private com.waqiti.reconciliation.model.ReconciliationStatus status;
    
    private Map<String, Object> stepResults;
    
    private NotificationType notificationType;
    
    private NotificationPriority priority;
    
    private List<String> recipients;
    
    private String subject;
    
    private String message;
    
    private Map<String, Object> templateData;
    
    private List<NotificationChannel> channels;

    public enum NotificationType {
        RECONCILIATION_STARTED,
        RECONCILIATION_COMPLETED,
        RECONCILIATION_FAILED,
        BREAKS_DETECTED,
        CRITICAL_BREAK,
        ESCALATION,
        RESOLUTION_COMPLETED
    }

    public enum NotificationPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum NotificationChannel {
        EMAIL,
        SMS,
        SLACK,
        TEAMS,
        WEBHOOK,
        IN_APP
    }

    public boolean isCritical() {
        return NotificationPriority.CRITICAL.equals(priority);
    }

    public boolean hasRecipients() {
        return recipients != null && !recipients.isEmpty();
    }

    public boolean isMultiChannel() {
        return channels != null && channels.size() > 1;
    }
}