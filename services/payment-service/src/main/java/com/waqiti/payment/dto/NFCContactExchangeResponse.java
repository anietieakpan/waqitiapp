package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for NFC contact exchange
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCContactExchangeResponse {

    private String connectionId;
    private String status; // SUCCESS, FAILED, PENDING
    private String userId;
    private String contactUserId;
    private String displayName;
    private String contactDisplayName;
    private Instant exchangedAt;
    private String signature;
    
    // Connection details
    private String connectionType; // MUTUAL, ONE_WAY
    private boolean isExistingContact;
    private boolean contactAccepted;
    
    // Shared information
    private String sharedPhoneNumber;
    private String sharedEmail;
    private String sharedAddress;
    private String sharedProfilePicture;
    
    // Social platform information
    private String socialPlatformUsername;
    private String socialPlatformType;
    
    // Professional information
    private String companyName;
    private String jobTitle;
    private String bio;
    
    // Payment capabilities
    private boolean paymentRequestsAllowed;
    private boolean directPaymentsAllowed;
    
    // Security information
    private String securityLevel;
    private boolean mutualVerification;
    
    // NFC specific fields
    private String nfcSessionId;
    private String nfcProtocolVersion;
    private boolean bothDevicesSecure;
    
    // Additional features
    private String contactScore; // Based on mutual connections, transaction history, etc.
    private boolean suggestedConnection;
    private String[] mutualConnections;
    
    // Error information (if any)
    private String errorCode;
    private String errorMessage;
    
    // Additional metadata
    private String metadata;

    /**
     * Creates a successful contact exchange response
     */
    public static NFCContactExchangeResponse success(String connectionId, String userId, 
                                                   String contactUserId, String displayName, 
                                                   String contactDisplayName) {
        return NFCContactExchangeResponse.builder()
                .connectionId(connectionId)
                .status("SUCCESS")
                .userId(userId)
                .contactUserId(contactUserId)
                .displayName(displayName)
                .contactDisplayName(contactDisplayName)
                .exchangedAt(Instant.now())
                .connectionType("MUTUAL")
                .contactAccepted(true)
                .mutualVerification(true)
                .build();
    }

    /**
     * Creates a failed contact exchange response
     */
    public static NFCContactExchangeResponse failure(String errorCode, String errorMessage) {
        return NFCContactExchangeResponse.builder()
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .exchangedAt(Instant.now())
                .build();
    }

    /**
     * Creates a pending contact exchange response
     */
    public static NFCContactExchangeResponse pending(String connectionId, String userId, 
                                                   String contactUserId) {
        return NFCContactExchangeResponse.builder()
                .connectionId(connectionId)
                .status("PENDING")
                .userId(userId)
                .contactUserId(contactUserId)
                .exchangedAt(Instant.now())
                .contactAccepted(false)
                .build();
    }

    /**
     * Checks if the contact exchange was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    /**
     * Checks if the contact exchange is pending
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * Checks if the contact exchange failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * Checks if this created a new contact connection
     */
    public boolean isNewConnection() {
        return !isExistingContact && isSuccessful();
    }

    /**
     * Checks if payment features are available
     */
    public boolean hasPaymentCapabilities() {
        return paymentRequestsAllowed || directPaymentsAllowed;
    }
}