package com.waqiti.user.dto.notification;

import com.waqiti.user.entity.NotificationPreference.NotificationType;
import com.waqiti.user.entity.NotificationPreference.NotificationChannel;
import com.waqiti.user.entity.NotificationPreference.NotificationFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceDTO {
    private String id;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private boolean enabled;
    private NotificationFrequency frequency;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private String timezone;
    private String language;
    private LocalDateTime optInDate;
    private LocalDateTime optOutDate;
}