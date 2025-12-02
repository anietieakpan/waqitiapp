package com.waqiti.payment.client.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batch fraud evaluation request DTO
 * For processing multiple transactions simultaneously
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BatchFraudRequest {
    
    @NotNull
    private UUID batchId;
    
    @NotNull
    @Size(min = 1, max = 1000)
    private List<PaymentFraudRequest> payments;
    
    @NotNull
    private BatchProcessingMode processingMode;
    
    private BatchPriority priority;
    
    private LocalDateTime requestedCompletionTime;
    
    private Map<String, Object> batchMetadata;
    
    private String batchSource; // API, SCHEDULER, MANUAL
    
    private UUID requesterId;
    
    @Builder.Default
    private boolean enableParallelProcessing = true;
    
    @Builder.Default
    private boolean failFastOnError = false;
    
    public enum BatchProcessingMode {
        SEQUENTIAL,    // Process one by one
        PARALLEL,      // Process in parallel
        STREAMING,     // Process as stream
        PRIORITY_BASED // Process based on priority
    }
    
    public enum BatchPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}