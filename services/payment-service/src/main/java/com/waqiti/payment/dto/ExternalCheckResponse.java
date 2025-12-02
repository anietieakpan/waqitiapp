package com.waqiti.payment.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from external check processor
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCheckResponse {
    private String processorId;
    private String referenceId;
    private boolean accepted;
    private String status;
    private String rejectionReason;
    private String processingTime;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}