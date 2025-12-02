package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Validation Response DTO
 * 
 * Response structure for wallet validation operations.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {
    
    @JsonProperty("valid")
    private Boolean valid;
    
    @JsonProperty("errors")
    private List<ValidationError> errors;
    
    @JsonProperty("warnings")
    private List<String> warnings;
    
    @JsonProperty("available_balance")
    private BigDecimal availableBalance;
    
    @JsonProperty("required_amount")
    private BigDecimal requiredAmount;
    
    @JsonProperty("validation_details")
    private Map<String, Object> validationDetails;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("field")
        private String field;
        
        @JsonProperty("message")
        private String message;
    }
}