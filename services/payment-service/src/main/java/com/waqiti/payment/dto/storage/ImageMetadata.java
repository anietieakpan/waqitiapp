package com.waqiti.payment.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Image Metadata for Check Deposits
 *
 * SECURITY ENHANCEMENT (CRITICAL-005): Added depositId and checkAmount fields
 * for duplicate check detection and fraud prevention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadata {
    private String userId;
    private String imageType;
    private Instant uploadedAt;
    private String checkId;
    private String transactionId;

    // CRITICAL-005: Added for duplicate detection
    private String depositId;       // Check deposit ID for tracking duplicates
    private BigDecimal checkAmount;  // Check amount for fraud investigation
}