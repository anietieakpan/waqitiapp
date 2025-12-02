package com.waqiti.common.messaging.deadletter;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Processing History Entry
 * 
 * Tracks each processing attempt for a message, including
 * timing, errors, and environmental context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingHistory {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    private int attemptNumber;
    private String errorMessage;
    private String errorType;
    private String errorCode;
    private String stackTrace;
    
    // Processing environment
    private String processingNode;
    private String consumerGroup;
    private String consumerInstance;
    private String applicationVersion;
    
    // Performance metrics
    private long processingDurationMs;
    private long queueWaitTimeMs;
    private long memoryUsageMb;
    private double cpuUsagePercent;
    
    // Context information
    private String threadName;
    private String operation;
    private String methodName;
    
    // Recovery information
    private String recoveryAction;
    private boolean recovered;
    private String recoveryNotes;
}