package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Wise Transfer DTO
 * 
 * Represents a transfer from Wise API.
 */
@Data
public class WiseTransfer {
    private Long id;
    
    @JsonProperty("user")
    private Long userId;
    
    @JsonProperty("targetAccount")
    private Long targetAccount;
    
    @JsonProperty("sourceAccount")
    private Long sourceAccount;
    
    @JsonProperty("quote")
    private Long quoteId;
    
    private String status;
    
    @JsonProperty("reference")
    private String reference;
    
    private BigDecimal rate;
    
    @JsonProperty("created")
    private LocalDateTime created;
    
    @JsonProperty("business")
    private Long businessId;
    
    @JsonProperty("transferRequest")
    private Long transferRequestId;
    
    @JsonProperty("details")
    private Map<String, Object> details;
    
    @JsonProperty("hasActiveIssues")
    private Boolean hasActiveIssues;
    
    @JsonProperty("sourceCurrency")
    private String sourceCurrency;
    
    @JsonProperty("sourceValue")
    private BigDecimal sourceValue;
    
    @JsonProperty("targetCurrency")
    private String targetCurrency;
    
    @JsonProperty("targetValue")
    private BigDecimal targetValue;
    
    @JsonProperty("customerTransactionId")
    private String customerTransactionId;
}