package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesDto {
    private String userId;
    private boolean pushEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean paymentNotifications;
    private boolean securityAlerts;
    private boolean promotionalNotifications;
    private boolean socialNotifications;
    private boolean transactionReceipts;
    private boolean dailySummary;
    private boolean weeklyReport;
    private boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
    private boolean sound;
    private boolean vibration;
    private String notificationSound;
    private String language;
    private LocalDateTime lastUpdated;
}