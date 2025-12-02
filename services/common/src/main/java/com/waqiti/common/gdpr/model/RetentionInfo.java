package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Retention Information
 *
 * GDPR Article 5(1)(e): Storage limitation principle
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionInfo {

    private String dataCategory;
    private String retentionPeriod; // e.g., "7 years", "Until consent withdrawal"
    private String legalBasis;
    private LocalDateTime createdAt;
    private LocalDateTime deleteAfter;
    private Boolean isActive;
    private String retentionReason;
}
