package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Onfido applicant model for KYC verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnfidoApplicant {
    
    private String id;
    private String href;
    private String firstName;
    private String lastName;
    private String email;
    private String dob;
    private String idNumbers;
    private Address address;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean sandbox;
    
    // Application context
    private String kycApplicationId;
    private String userId;
    private String sessionId;
    
    // Verification status
    private VerificationStatus status;
    private List<OnfidoCheck> checks;
    private List<OnfidoDocument> documents;
    private List<OnfidoLivePhoto> livePhotos;
    
    // Webhook events
    private List<OnfidoWebhookEvent> webhookEvents;
    
    /**
     * Applicant address
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String flatNumber;
        private String buildingNumber;
        private String buildingName;
        private String street;
        private String subStreet;
        private String town;
        private String state;
        private String postcode;
        private String country;
        private String line1;
        private String line2;
        private String line3;
    }
    
    /**
     * Onfido check
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoCheck {
        private String id;
        private String href;
        private String status;
        private String result;
        private String type;
        private List<String> tags;
        private List<OnfidoReport> reports;
        private LocalDateTime createdAt;
        private String downloadUri;
        private String formUri;
        private String redirectUri;
        private Map<String, Object> options;
    }
    
    /**
     * Onfido document
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoDocument {
        private String id;
        private String href;
        private String type;
        private String side;
        private String issuingCountry;
        private String fileName;
        private String fileType;
        private int fileSize;
        private LocalDateTime createdAt;
        private String downloadHref;
    }
    
    /**
     * Onfido live photo
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoLivePhoto {
        private String id;
        private String href;
        private String fileName;
        private String fileType;
        private int fileSize;
        private LocalDateTime createdAt;
        private String downloadHref;
    }
    
    /**
     * Onfido report
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoReport {
        private String id;
        private String href;
        private String name;
        private String status;
        private String result;
        private String subResult;
        private String variant;
        private LocalDateTime createdAt;
        private Map<String, Object> breakdown;
        private List<String> properties;
        private Map<String, Object> options;
    }
    
    /**
     * Onfido webhook event
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoWebhookEvent {
        private String resourceType;
        private String action;
        private Object object;
        private LocalDateTime completedAt;
        private String eventId;
    }
    
    /**
     * Get the latest check
     */
    public OnfidoCheck getLatestCheck() {
        if (checks == null || checks.isEmpty()) {
            return null;
        }
        return checks.stream()
            .max((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt()))
            .orElse(null);
    }
    
    /**
     * Get check by ID
     */
    public OnfidoCheck getCheckById(String checkId) {
        if (checks == null) {
            return null;
        }
        return checks.stream()
            .filter(check -> checkId.equals(check.getId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if verification is complete
     */
    public boolean isVerificationComplete() {
        OnfidoCheck latestCheck = getLatestCheck();
        return latestCheck != null && "complete".equals(latestCheck.getStatus());
    }
    
    /**
     * Check if verification passed
     */
    public boolean isVerificationPassed() {
        OnfidoCheck latestCheck = getLatestCheck();
        return latestCheck != null && 
               "complete".equals(latestCheck.getStatus()) &&
               "clear".equals(latestCheck.getResult());
    }
}