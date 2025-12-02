package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for NFC session initialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCSessionResponse {

    private String sessionId;
    private String sessionToken;
    private String sessionType; // MERCHANT, P2P, CONTACT_EXCHANGE
    private String status; // ACTIVE, EXPIRED, CANCELLED
    private Instant createdAt;
    private Instant expiresAt;
    private String qrCode; // Optional QR code for backup payment method
    private String deepLinkUrl; // Deep link for mobile app integration
    
    // Session configuration
    private String nfcProtocolVersion;
    private String encryptionKey;
    private String publicKey;
    
    // Security information
    private String securityLevel;
    private boolean biometricRequired;
    private boolean pinRequired;
    
    // Session limits
    private String maxTransactionAmount;
    private Integer maxTransactionCount;
    private Integer remainingTransactions;
    
    // WebSocket information for real-time updates
    private String websocketUrl;
    private String websocketToken;
    
    // Additional metadata
    private String metadata;

    /**
     * Creates a successful session response
     */
    public static NFCSessionResponse success(String sessionId, String sessionToken, 
                                           String sessionType, Instant expiresAt) {
        return NFCSessionResponse.builder()
                .sessionId(sessionId)
                .sessionToken(sessionToken)
                .sessionType(sessionType)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Checks if the session is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(status) && 
               expiresAt != null && 
               Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the session has expired
     */
    public boolean isExpired() {
        return "EXPIRED".equals(status) || 
               (expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    /**
     * Gets remaining session time in seconds
     */
    public long getRemainingTimeSeconds() {
        if (expiresAt == null) {
            return 0L;
        }
        
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0L, remaining);
    }
}