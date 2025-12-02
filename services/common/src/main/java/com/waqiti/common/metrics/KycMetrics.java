package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * KYC verification metrics data model
 */
@Data
@Builder
public class KycMetrics {
    private String userId;
    private String verificationId;
    private String provider; // JUMIO, ONFIDO, VERIFF
    private String status; // VERIFIED, REJECTED, PENDING, MANUAL_REVIEW
    private String verificationLevel; // BASIC, ENHANCED, PREMIUM
    private double confidenceScore;
    private Duration processingTime;
    private String documentType; // PASSPORT, DRIVERS_LICENSE, etc.
    private String rejectionReason;
    private boolean manualReviewRequired;
}