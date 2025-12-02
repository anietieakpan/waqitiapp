package com.waqiti.customer.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Base event model for customer domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String customerId;
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String ssn;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String onboardingStatus;
    private String kycStatus;
    private String riskLevel;
    private String beneficiaryId;
    private String beneficiaryName;
    private String beneficiaryRelationship;
    private String consentType;
    private String consentStatus;
    private String communicationChannel;
    private String communicationFrequency;
    private String serviceRequestId;
    private String serviceRequestType;
    private String serviceRequestStatus;
    private String feedbackType;
    private String feedbackRating;
    private String feedbackComment;
    private String adminId;
    private String reason;
    private String previousValue;
    private String newValue;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String status;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "CUSTOMER_DEATH_NOTIFICATION".equals(eventType) || 
               "CUSTOMER_FRAUD_ALERT".equals(eventType);
    }
    
    /**
     * Check if this is a compliance-related event
     */
    public boolean isComplianceEvent() {
        return "CUSTOMER_KYC_VERIFICATION".equals(eventType) || 
               "CUSTOMER_RISK_ASSESSMENT".equals(eventType);
    }
    
    /**
     * Check if this requires customer notification
     */
    public boolean requiresCustomerNotification() {
        return "CUSTOMER_PROFILE_UPDATED".equals(eventType) || 
               "CUSTOMER_CONSENT_UPDATED".equals(eventType);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}