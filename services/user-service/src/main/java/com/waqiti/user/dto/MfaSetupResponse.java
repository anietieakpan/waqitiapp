// File: services/user-service/src/main/java/com/waqiti/user/dto/MfaSetupResponse.java
package com.waqiti.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {
    private String secret;
    private String qrCodeImage;
}