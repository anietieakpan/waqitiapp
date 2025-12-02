package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationContext {
    private String eventId;
    private String verificationType;
    private String userId;
    private Instant timestamp;
    private com.fasterxml.jackson.databind.JsonNode eventData;
    private String sourceSystem;
    private String userAgent;
    private String ipAddress;
    private String sessionId;
    private String deviceId;
    private String verificationLevel;
    private String priority;
    private com.waqiti.user.domain.User user;
    private String userTier;
    private String userStatus;
    private LocalDateTime registrationDate;
    private List<AccountVerification> existingVerifications;
    private Map<String, VerificationStatus> verificationStatusByType;
    private LocalDateTime lastVerificationDate;
    private VerificationStatus lastVerificationStatus;
    private UserSession sessionData;
    private LocalDateTime sessionStartTime;
    private LocalDateTime lastActivityTime;
    private com.waqiti.user.domain.UserDevice deviceInfo;
    private boolean deviceTrusted;
    private String deviceType;
    private UserRiskProfile riskProfile;
    private BigDecimal riskScore;
    private String riskLevel;
    private List<String> riskFactors;
    private GeolocationData geolocation;
    private String country;
    private String city;
    private UserCompliance complianceData;
    private BigDecimal complianceScore;
    private String complianceStatus;
    private LocalDateTime lastComplianceCheck;
    private DeviceFingerprint deviceFingerprint;
    private String browser;
    private String operatingSystem;
    private String devicePlatform;
    private BigDecimal verificationScore;
}
