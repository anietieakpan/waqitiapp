package com.waqiti.payment.dwolla.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Dwolla Token Response DTO
 * 
 * Response object for OAuth2 token operations.
 */
@Data
public class DwollaTokenResponse {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    @JsonProperty("expires_in")
    private Integer expiresIn;
}