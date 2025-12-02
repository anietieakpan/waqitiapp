package com.waqiti.kyc.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResult {
    
    private String userId;
    private MigrationStatus status;
    private String message;
    private String error;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int migratedVerifications;
    private int migratedDocuments;
    private int migratedChecks;
    
    public long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    public boolean isSuccessful() {
        return status == MigrationStatus.SUCCESS;
    }
    
    public boolean isFailed() {
        return status == MigrationStatus.FAILED;
    }
    
    public boolean isSkipped() {
        return status == MigrationStatus.SKIPPED;
    }
}