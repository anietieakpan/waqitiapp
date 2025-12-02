package com.waqiti.common.retention.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Data Retention Enforcement Result
 *
 * PRODUCTION FIX: Created to support DataRetentionService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionResult {
    // Policy information
    private String policyId;
    private String policyName;
    private String dataType;

    // Execution details
    private Instant cutoffDate;
    private Instant startTime;
    private Instant endTime;
    private boolean dryRun;

    // Record counts
    private Integer eligibleRecordCount;
    private Integer legalHoldCount;
    private Integer deletableRecordCount;
    private Integer deletedRecordCount;
    private Integer recordsDeleted;  // Alias for compatibility
    private Integer recordsFailed;

    // Status
    private boolean success;
    private String status;
    private String errorMessage;

    // Timing
    private LocalDateTime executedAt;
    private Long durationMs;
}
