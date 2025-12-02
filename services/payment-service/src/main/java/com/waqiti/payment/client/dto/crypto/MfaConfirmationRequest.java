package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for MFA confirmation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaConfirmationRequest {
    private String mfaCode;
    private String mfaMethod; // TOTP, SMS, EMAIL
}
