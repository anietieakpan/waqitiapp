package com.waqiti.kyc.dto.response;

import com.waqiti.kyc.domain.BatchKYCJob.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for batch KYC operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchKYCResponse {
    
    private String batchId;
    
    private JobStatus status;
    
    private Integer totalUsers;
    
    private Integer processedUsers;
    
    private Integer successfulUsers;
    
    private Integer failedUsers;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private Map<String, String> results;
    
    private Map<String, String> errors;
    
    private String downloadUrl;
    
    private LocalDateTime estimatedCompletionTime;
    
    public double getProgressPercentage() {
        if (totalUsers == null || totalUsers == 0) return 0.0;
        if (processedUsers == null) return 0.0;
        return (double) processedUsers / totalUsers * 100;
    }
    
    public double getSuccessRate() {
        if (processedUsers == null || processedUsers == 0) return 0.0;
        if (successfulUsers == null) return 0.0;
        return (double) successfulUsers / processedUsers * 100;
    }
}