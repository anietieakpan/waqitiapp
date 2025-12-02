package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "backup_records")
public class BackupRecord {
    @Id
    private String id;
    private LocalDateTime timestamp;
    private BackupStatus status;
    private Integer jobCount;
    private Integer successfulJobs;
    private Integer failedJobs;
    private Long totalSize;
    private Duration duration;
    private String failureReason;
    private String location;
    private Double successRate;
}