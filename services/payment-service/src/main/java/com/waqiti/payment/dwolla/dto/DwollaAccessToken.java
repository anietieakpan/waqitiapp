package com.waqiti.payment.dwolla.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dwolla Access Token DTO
 * 
 * Represents OAuth2 access token for Dwolla API authentication.
 */
@Data
@Builder(toBuilder = true)
public class DwollaAccessToken {
    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    private LocalDateTime issuedAt;
}