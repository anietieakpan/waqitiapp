package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/**
 * Result of batch audit event processing operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchProcessingResult {
    
    @JsonProperty("processed_count")
    private Integer processedCount;
    
    @JsonProperty("responses")
    private List<AuditEventResponse> responses;
    
    @JsonProperty("errors")
    private List<String> errors;
    
    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
    
    @JsonProperty("throughput_per_second")
    private Double throughputPerSecond;
    
    @JsonProperty("status")
    private BatchProcessingStatus status;
    
    @JsonProperty("batch_id")
    private String batchId;
    
    @JsonProperty("start_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant startTime;
    
    @JsonProperty("end_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private Instant endTime;
    
    @JsonProperty("failed_count")
    private Integer failedCount;
    
    @JsonProperty("success_count")
    private Integer successCount;
}