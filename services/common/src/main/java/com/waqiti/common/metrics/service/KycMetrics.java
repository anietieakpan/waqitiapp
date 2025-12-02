package com.waqiti.common.metrics.service;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class KycMetrics {
    private String provider;
    private String status;
    private String verificationLevel;
    private Duration processingTime;
    private double confidenceScore;
    private String documentType;
    private String countryCode;
    private boolean manualReviewRequired;
    private String rejectionReason;
}