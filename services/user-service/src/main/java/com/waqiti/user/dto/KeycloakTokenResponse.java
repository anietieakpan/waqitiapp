package com.waqiti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Keycloak token response.
 * Represents the OAuth2/OIDC token response from Keycloak.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakTokenResponse {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("expires_in")
    private Long expiresIn;
    
    @JsonProperty("refresh_expires_in")
    private Long refreshExpiresIn;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    @JsonProperty("id_token")
    private String idToken;
    
    @JsonProperty("not-before-policy")
    private Long notBeforePolicy;
    
    @JsonProperty("session_state")
    private String sessionState;
    
    @JsonProperty("scope")
    private String scope;
    
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("error_description")
    private String errorDescription;
}