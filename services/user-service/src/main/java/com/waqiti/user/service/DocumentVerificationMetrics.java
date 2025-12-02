package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DocumentVerificationMetrics {
    private String userId;
    private String documentType;
    private boolean verified;
    private BigDecimal verificationScore;
    private long processingTime;
    private boolean ocrUsed;
    private java.time.Instant timestamp;
}
