package com.waqiti.user.dto.notification;

import com.waqiti.user.entity.NotificationPreference.NotificationType;
import com.waqiti.user.entity.NotificationPreference.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesResponse {
    private String userId;
    private List<PreferenceDTO> preferences;
    private boolean hasPreferences;
    private int totalPreferences;
    private int enabledPreferences;
    private Map<NotificationType, Long> enabledByType;
    private Map<NotificationChannel, Long> enabledByChannel;
}