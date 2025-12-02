package com.waqiti.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request to update notification preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {
    private Boolean appNotificationsEnabled;
    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private Boolean pushNotificationsEnabled;
    private Map<String, Boolean> categoryPreferences;
    private Integer quietHoursStart;
    private Integer quietHoursEnd;
    private String email;
    private String phoneNumber;
    private String deviceToken;
}

