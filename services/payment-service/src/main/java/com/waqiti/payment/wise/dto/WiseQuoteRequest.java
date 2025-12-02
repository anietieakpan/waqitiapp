package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Wise Quote Request DTO
 * 
 * Request object for creating payment quotes.
 */
@Data
@Builder
public class WiseQuoteRequest {
    
    @JsonProperty("sourceCurrency")
    private String sourceCurrency;
    
    @JsonProperty("targetCurrency") 
    private String targetCurrency;
    
    @JsonProperty("sourceAmount")
    private BigDecimal sourceAmount;
    
    @JsonProperty("targetAmount")
    private BigDecimal targetAmount;
    
    @JsonProperty("payOut")
    private String payOut;
    
    @JsonProperty("preferredPayIn")
    private String preferredPayIn;
}