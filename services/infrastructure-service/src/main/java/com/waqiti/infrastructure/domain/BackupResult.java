package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupResult {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Duration duration;
    private BackupStatus overallStatus;
    private List<BackupJob> backupJobs;
    private Long totalSizeBytes;
    private String summaryMessage;
    private Double successRate;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BackupJob {
    private String target;
    private BackupType type;
    private BackupJobStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long sizeBytes;
    private String location;
    private String error;
    private String checksum;
    private Duration duration;
}

enum BackupStatus {
    IN_PROGRESS,
    COMPLETED,
    PARTIAL_FAILURE,
    FAILED,
    CANCELLED
}

enum BackupType {
    DATABASE,
    CONFIGURATION,
    FILESYSTEM,
    CACHE,
    LOGS,
    SECURITY_KEYS
}

enum BackupJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETRYING
}