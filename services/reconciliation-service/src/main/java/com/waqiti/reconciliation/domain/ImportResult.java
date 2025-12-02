package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ImportResult {
    private final String filename;
    private final String fileType;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer totalRecords;
    private final Integer successfulRecords;
    private final Integer failedRecords;
    private final boolean success;
    private final String errorMessage;
    private final List<Map<String, Object>> data;
    private final List<ImportError> errors;
    private final List<String> warnings;
    private final String importId;
    private final Long fileSizeBytes;
    private final Map<String, Object> metadata;
    
    public Long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return 0L;
    }
    
    public Double getSuccessRate() {
        if (totalRecords == null || totalRecords == 0) {
            return 0.0;
        }
        return (double) successfulRecords / totalRecords * 100.0;
    }
    
    public Double getFailureRate() {
        if (totalRecords == null || totalRecords == 0) {
            return 0.0;
        }
        return (double) failedRecords / totalRecords * 100.0;
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    public boolean isPartialSuccess() {
        return success && failedRecords != null && failedRecords > 0;
    }
    
    public String getFormattedFileSize() {
        if (fileSizeBytes == null) {
            return "Unknown";
        }
        
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return String.format("%.2f KB", fileSizeBytes / 1024.0);
        } else if (fileSizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public String getStatusDescription() {
        if (!success) {
            return "Failed";
        } else if (isPartialSuccess()) {
            return "Partial Success";
        } else {
            return "Success";
        }
    }
}