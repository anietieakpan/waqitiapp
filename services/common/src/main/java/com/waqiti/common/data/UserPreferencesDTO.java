package com.waqiti.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * User Preferences DTO for user settings and preferences
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDTO {
    private String userId;
    private String language;
    private String timezone;
    private String currency;
    private boolean emailNotifications;
    private boolean smsNotifications;
    private boolean pushNotifications;
    private List<String> notificationCategories;
    private Map<String, Object> privacySettings;
    private Map<String, Object> securitySettings;
    private String theme;
}