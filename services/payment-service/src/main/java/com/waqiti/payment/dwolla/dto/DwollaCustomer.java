package com.waqiti.payment.dwolla.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dwolla Customer DTO
 * 
 * Represents a customer from Dwolla API.
 */
@Data
public class DwollaCustomer {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String type;
    private String status;
    private LocalDateTime created;
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String postalCode;
    private String phone;
    
    // Business-specific fields
    private String businessName;
    private String businessType;
    private String businessClassification;
    private String ein;
    private String website;
    
    // HAL Links
    private Map<String, Object> _links;
}