package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID userId;
    private String username;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private LocalDateTime accountCreatedDate;
    private LocalDateTime lastLoginDate;
    private String accountStatus;
    private String kycStatus;
    private String preferredLanguage;
    private String timezone;
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean twoFactorEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private List<String> notificationPreferences;
    private Map<String, Object> customAttributes;
    private LocalDateTime lastSecurityReviewDate;
    private Integer accountAgeInYears;
    private Integer accountAgeInDays;
    private boolean isPremiumUser;
    private String customerSegment;
}