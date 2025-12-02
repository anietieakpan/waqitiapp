package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FedNowResponse {
    private String transactionId;
    private String status;
    private String errorMessage;
    private String errorCode;
    private LocalDateTime processedAt;
    private Map<String, Object> networkMetadata;
    
    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);
    }
    
    public String toJson() {
        // Simple JSON representation for storage
        return String.format("{\"transactionId\":\"%s\",\"status\":\"%s\",\"processedAt\":\"%s\"}", 
            transactionId, status, processedAt);
    }
}