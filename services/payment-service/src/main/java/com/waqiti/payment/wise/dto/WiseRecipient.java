package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Wise Recipient DTO
 * 
 * Represents a payment recipient from Wise API.
 */
@Data
public class WiseRecipient {
    private Long id;
    private String currency;
    private String type;
    private String profile;
    
    @JsonProperty("accountHolderName")
    private String accountHolderName;
    
    private Map<String, Object> details;
    
    @JsonProperty("user")
    private Long userId;
    
    @JsonProperty("active")
    private Boolean active;
    
    @JsonProperty("ownedByCustomer")
    private Boolean ownedByCustomer;
}