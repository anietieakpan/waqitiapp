package com.waqiti.payment.wise.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Wise Recipient Request DTO
 * 
 * Request object for creating payment recipients.
 */
@Data
@Builder
public class WiseRecipientRequest {
    private String currency;
    private String type;
    private String profile;
    private String accountHolderName;
    private Map<String, Object> details;
}