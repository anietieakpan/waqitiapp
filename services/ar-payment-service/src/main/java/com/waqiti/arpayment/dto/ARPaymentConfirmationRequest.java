package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ARPaymentConfirmationRequest {
    
    @NotBlank(message = "Experience ID is required")
    private String experienceId;
    
    @NotBlank(message = "Confirmation method is required")
    private String confirmationMethod; // GESTURE, VOICE, BUTTON, GAZE, BIOMETRIC
    
    private Map<String, Object> confirmationData;
    private Map<String, Object> biometricData;
    private String voiceConfirmation;
    private Map<String, Double> gazeCoordinates;
    private boolean confirmed;
}