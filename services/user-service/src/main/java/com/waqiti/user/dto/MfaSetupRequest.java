// File: services/user-service/src/main/java/com/waqiti/user/dto/MfaSetupRequest.java
package com.waqiti.user.dto;

import com.waqiti.user.domain.MfaMethod;
import com.waqiti.user.validation.SafeString;
import com.waqiti.user.validation.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA Setup Request
 *
 * SECURITY:
 * - Phone numbers validated in E.164 format
 * - Email addresses validated and checked for injection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupRequest {
    @NotNull(message = "MFA method is required")
    private MfaMethod method;

    // For SMS method
    @ValidPhoneNumber
    private String phoneNumber;

    // For Email method
    @Email
    @SafeString(maxLength = 255)
    private String email;
}