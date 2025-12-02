package com.waqiti.payment.dwolla.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dwolla Funding Source DTO
 * 
 * Represents a funding source from Dwolla API.
 */
@Data
public class DwollaFundingSource {
    private String id;
    private String status;
    private String type;
    private String bankAccountType;
    private String name;
    private LocalDateTime created;
    private Boolean removed;
    private String channels;
    private String bankName;
    private String fingerprint;
    
    // HAL Links
    private Map<String, Object> _links;
}