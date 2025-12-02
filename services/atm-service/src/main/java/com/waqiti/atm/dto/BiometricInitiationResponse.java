package com.waqiti.atm.dto;

import com.waqiti.atm.security.AtmBiometricAuthService.BiometricMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for biometric authentication initiation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricInitiationResponse {
    private String sessionId;
    private boolean biometricRequired;
    private List<BiometricMethod> requiredMethods;
    private boolean livenessCheckRequired;
    private int sessionDurationMinutes;
    private String message;
}