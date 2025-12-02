package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DocumentAuthenticityRequest {
    private String userId;
    private String documentType;
    private String documentImageUrl;
    private String verificationLevel;
    private List<String> checkFeatures;
    private java.time.Instant requestTimestamp;
}
