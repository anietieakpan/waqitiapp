package com.waqiti.user.dto.notification;

import com.waqiti.user.entity.NotificationPreference.NotificationType;
import com.waqiti.user.entity.NotificationPreference.NotificationChannel;
import com.waqiti.user.entity.NotificationPreference.NotificationFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceUpdateRequest {
    private NotificationType notificationType;
    private NotificationChannel channel;
    private boolean enabled;
    private NotificationFrequency frequency;
}