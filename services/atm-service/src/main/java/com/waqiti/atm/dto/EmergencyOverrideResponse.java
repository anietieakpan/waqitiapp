package com.waqiti.atm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for emergency biometric override
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyOverrideResponse {
    private boolean overrideAccepted;
    private boolean silentAlarmTriggered;
    private String message;
    private String incidentId;
}