package com.waqiti.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID; /**
 * Response for notification preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationPreferencesResponse {
    private UUID userId;
    private boolean appNotificationsEnabled;
    private boolean emailNotificationsEnabled;
    private boolean smsNotificationsEnabled;
    private boolean pushNotificationsEnabled;
    private Map<String, Boolean> categoryPreferences;
    private Integer quietHoursStart;
    private Integer quietHoursEnd;
    private String email;
    private String phoneNumber;
    private boolean deviceTokenRegistered;
    private LocalDateTime updatedAt;
}
