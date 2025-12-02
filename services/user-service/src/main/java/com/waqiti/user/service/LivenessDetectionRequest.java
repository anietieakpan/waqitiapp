package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class LivenessDetectionRequest {
    private String userId;
    private String videoUrl;
    private String challengeType;
    private String verificationLevel;
    private java.time.Instant requestTimestamp;
}
