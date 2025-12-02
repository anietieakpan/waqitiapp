// File: services/user-service/src/main/java/com/waqiti/user/dto/MfaVerifyRequest.java
package com.waqiti.user.dto;

import com.waqiti.user.domain.MfaMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerifyRequest {
    @NotNull(message = "MFA method is required")
    private MfaMethod method;

    @NotBlank(message = "Code is required")
    private String code;
}