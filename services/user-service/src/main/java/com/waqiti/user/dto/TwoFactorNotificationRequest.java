// File: services/user-service/src/main/java/com/waqiti/user/dto/TwoFactorNotificationRequest.java
package com.waqiti.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorNotificationRequest {
    private UUID userId;
    private String recipient;
    private String verificationCode;
    private String language;
}