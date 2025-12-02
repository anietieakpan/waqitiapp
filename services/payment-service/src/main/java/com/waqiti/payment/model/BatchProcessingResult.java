package com.waqiti.payment.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProcessingResult {
    private String batchId;
    private Instant processingStartTime;
    private Instant processingEndTime;
    private Long totalProcessingTime;
    private Integer processedCount;
    private Integer failedCount;
    private Double successRate;
    private List<PaymentProcessingError> processingErrors;

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
