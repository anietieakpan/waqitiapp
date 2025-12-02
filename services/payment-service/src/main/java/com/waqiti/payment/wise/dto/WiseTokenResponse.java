package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Wise Token Response DTO
 * 
 * Response object for OAuth2 token operations.
 */
@Data
public class WiseTokenResponse {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    @JsonProperty("expires_in")
    private Integer expiresIn;
    
    private String scope;
}