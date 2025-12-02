package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wise Balance DTO
 * 
 * Represents account balance from Wise API.
 */
@Data
public class WiseBalance {
    private Long id;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("icon")
    private String icon;
    
    @JsonProperty("amount")
    private WiseAmount amount;
    
    @JsonProperty("reservedAmount")
    private WiseAmount reservedAmount;
    
    @JsonProperty("cashAmount")
    private WiseAmount cashAmount;
    
    @JsonProperty("totalWorth")
    private WiseAmount totalWorth;
    
    @JsonProperty("modificationTime")
    private LocalDateTime modificationTime;
    
    @JsonProperty("visible")
    private Boolean visible;
    
    @Data
    public static class WiseAmount {
        private BigDecimal value;
        private String currency;
    }
}