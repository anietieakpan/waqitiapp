package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for card network registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardNetworkRegistration {
    
    private String token;
    
    private String status;
    
    private String registrationId;
    
    private String networkId;
    
    private String issuerBin;
    
    private String processorId;
    
    private boolean successful;
    
    private String errorCode;
    
    private String errorMessage;
    
    private LocalDateTime registeredAt;
    
    private LocalDateTime expiresAt;
    
    private String networkBrand;
    
    private String cardProductId;
    
    private String tokenRequestorId;
    
    private Map<String, String> networkMetadata;
    
    /**
     * Creates a successful registration response
     */
    public static CardNetworkRegistration success(String token, String status) {
        return CardNetworkRegistration.builder()
            .token(token)
            .status(status)
            .successful(true)
            .registeredAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a failed registration response
     */
    public static CardNetworkRegistration failure(String errorCode, String errorMessage) {
        return CardNetworkRegistration.builder()
            .successful(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .registeredAt(LocalDateTime.now())
            .build();
    }
}