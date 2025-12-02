package com.waqiti.payment.dwolla.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB document for storing Dwolla customer information
 */
@Document(collection = "dwolla_customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DwollaCustomerRecord {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId;
    
    @Indexed(unique = true)
    private String dwollaCustomerId;
    
    private String firstName;
    private String lastName;
    private String email;
    private String type; // personal or business
    private String status; // unverified, suspended, retry, document, verified, suspended, deactivated
    
    private String dwollaResourceLocation;
    private String fundingSourcesUrl;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime verifiedAt;
    
    private Map<String, Object> metadata;
    
    // Business customer specific fields
    private String businessName;
    private String ein;
    private String businessType;
    private String businessClassification;
    
    // Verification details
    private String ssnLast4;
    private LocalDateTime dateOfBirth;
    
    // Address
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    // Phone
    private String phoneNumber;
    
    // Compliance
    private boolean kycVerified;
    private String kycStatus;
    private LocalDateTime kycVerifiedAt;
    
    // Status tracking
    private String lastError;
    private LocalDateTime lastErrorAt;
    private int retryCount;
}