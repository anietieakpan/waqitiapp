package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class BiometricVerificationRequest {
    private String userId;
    private String biometricType;
    private String biometricData;
    private String templateUrl;
    private String verificationLevel;
    private boolean livenessCheck;
    private java.time.Instant requestTimestamp;
}
