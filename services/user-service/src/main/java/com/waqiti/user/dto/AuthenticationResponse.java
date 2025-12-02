package com.waqiti.user.dto;

import com.waqiti.user.domain.MfaMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
/**

 * Response for successful authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;

    // New fields for MFA
    private boolean requiresMfa;
    private List<MfaMethod> availableMfaMethods;
    private String mfaToken; // Temporary token for MFA verification

}
