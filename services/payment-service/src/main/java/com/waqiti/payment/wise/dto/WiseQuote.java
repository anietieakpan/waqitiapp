package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wise Quote DTO
 * 
 * Represents a payment quote from Wise API.
 */
@Data
public class WiseQuote {
    private String id;
    
    @JsonProperty("sourceCurrency")
    private String sourceCurrency;
    
    @JsonProperty("targetCurrency")
    private String targetCurrency;
    
    @JsonProperty("sourceAmount")
    private BigDecimal sourceAmount;
    
    @JsonProperty("targetAmount")
    private BigDecimal targetAmount;
    
    private BigDecimal rate;
    
    @JsonProperty("createdTime")
    private LocalDateTime createdTime;
    
    @JsonProperty("expirationTime")
    private LocalDateTime expirationTime;
    
    @JsonProperty("paymentOptions")
    private List<WisePaymentOption> paymentOptions;
    
    @JsonProperty("notices")
    private List<WiseNotice> notices;
    
    @JsonProperty("fees")
    private List<WiseFee> fees;
    
    @JsonProperty("rateType")
    private String rateType;
    
    @JsonProperty("rateExpirationTime")
    private LocalDateTime rateExpirationTime;
    
    @Data
    public static class WisePaymentOption {
        @JsonProperty("payIn")
        private String payIn;
        
        @JsonProperty("payOut")
        private String payOut;
        
        private List<WiseFee> fees;
        
        @JsonProperty("sourceAmount")
        private BigDecimal sourceAmount;
        
        @JsonProperty("targetAmount")
        private BigDecimal targetAmount;
    }
    
    @Data
    public static class WiseNotice {
        private String text;
        private String link;
        private String type;
    }
    
    @Data
    public static class WiseFee {
        private String type;
        private BigDecimal total;
        private String currency;
        private String label;
        private String description;
    }
}