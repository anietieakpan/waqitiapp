package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationProcessingResult {
    private String eventId;
    private String verificationType;
    private String userId;
    private Instant processingStartTime;
    private Instant processingEndTime;
    private boolean success;
    private BigDecimal verificationScore;
    private Map<String, Object> processingDetails;
    private String errorMessage;
    private boolean fallbackUsed;
}
