package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Wise Transfer Request DTO
 * 
 * Request object for creating transfers.
 */
@Data
@Builder
public class WiseTransferRequest {
    
    @JsonProperty("targetAccount")
    private Long targetAccount;
    
    @JsonProperty("quoteUuid")
    private String quoteUuid;
    
    @JsonProperty("customerTransactionId")
    private String customerTransactionId;
    
    @JsonProperty("details")
    private Map<String, Object> details;
}