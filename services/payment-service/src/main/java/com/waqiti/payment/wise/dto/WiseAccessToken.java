package com.waqiti.payment.wise.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wise Access Token DTO
 * 
 * Represents OAuth2 access token for Wise API authentication.
 */
@Data
@Builder(toBuilder = true)
public class WiseAccessToken {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Integer expiresIn;
    private String scope;
    private LocalDateTime issuedAt;
}