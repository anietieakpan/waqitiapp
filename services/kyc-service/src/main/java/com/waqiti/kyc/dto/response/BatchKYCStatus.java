package com.waqiti.kyc.dto.response;

import com.waqiti.kyc.domain.BatchKYCJob.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Status DTO for batch KYC jobs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchKYCStatus {
    
    private String batchId;
    
    private JobStatus status;
    
    private Integer totalUsers;
    
    private Integer processedUsers;
    
    private Integer successfulUsers;
    
    private Integer failedUsers;
    
    private Double progressPercentage;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private LocalDateTime estimatedCompletionTime;
    
    private String message;
    
    public boolean isComplete() {
        return status == JobStatus.COMPLETED || 
               status == JobStatus.FAILED || 
               status == JobStatus.CANCELLED;
    }
    
    public boolean isInProgress() {
        return status == JobStatus.IN_PROGRESS;
    }
}