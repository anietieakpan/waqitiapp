package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExportResult {
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String format;
    private final String fileName;
    private final byte[] data;
    private final Long fileSize;
    private final Integer totalRecords;
    private final Integer exportedRecords;
    private final boolean success;
    private final String errorMessage;
    private final List<String> warnings;
    private final String exportId;
    private final String checksumMd5;
    private final String downloadUrl;
    
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
        return (double) exportedRecords / totalRecords * 100.0;
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    public boolean isPartialSuccess() {
        return success && exportedRecords != null && totalRecords != null 
            && exportedRecords < totalRecords;
    }
    
    public String getFormattedFileSize() {
        if (fileSize == null) {
            return "Unknown";
        }
        
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
}