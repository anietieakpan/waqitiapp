package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Behavioral biometric analysis request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralBiometricRequest {
    private String userId;
    
    @NotNull(message = "Typing data is required")
    private Map<String, Object> typingData;
    
    @NotNull(message = "Interaction data is required")
    private Map<String, Object> interactionData;
    
    @NotNull(message = "Navigation data is required")
    private Map<String, Object> navigationData;
}