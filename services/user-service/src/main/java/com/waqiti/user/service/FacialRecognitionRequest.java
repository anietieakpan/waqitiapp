package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class FacialRecognitionRequest {
    private String userId;
    private String imageUrl;
    private String referenceImageUrl;
    private String verificationLevel;
    private boolean livenessCheck;
    private java.time.Instant requestTimestamp;
}
