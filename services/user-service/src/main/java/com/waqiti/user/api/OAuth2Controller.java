package com.waqiti.user.api;

import com.waqiti.user.service.KeycloakAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class OAuth2Controller {

    private final KeycloakAuthService keycloakAuthService;
    
    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    /**
     * OAuth2 callback endpoint - now handled by Keycloak
     * This endpoint provides information about Keycloak OAuth2 flow
     */
    @GetMapping("/callback")
    public ResponseEntity<?> oauthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        log.info("OAuth2 callback received - redirecting to Keycloak flow");

        if (error != null) {
            log.warn("OAuth2 error received: {}", error);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", error);
            errorResponse.put("message", "OAuth2 authentication failed");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "OAuth2 authentication is now handled by Keycloak");
        response.put("authUrl", keycloakAuthService.getAuthenticationUrl("/api/v1/oauth2/success", state));
        response.put("tokenUrl", keycloakAuthService.getTokenUrl());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * OAuth2 authentication URLs for clients
     */
    @GetMapping("/auth-urls")
    public ResponseEntity<Map<String, String>> getAuthUrls(
            @RequestParam(value = "redirect_uri", defaultValue = "/") String redirectUri,
            @RequestParam(value = "state", required = false) String state) {
        
        Map<String, String> urls = new HashMap<>();
        urls.put("authUrl", keycloakAuthService.getAuthenticationUrl(redirectUri, state));
        urls.put("tokenUrl", keycloakAuthService.getTokenUrl());
        urls.put("logoutUrl", keycloakAuthService.getLogoutUrl(redirectUri));
        
        return ResponseEntity.ok(urls);
    }
    
    /**
     * Success callback for OAuth2 flow
     */
    @GetMapping("/success")
    public ResponseEntity<String> oauthSuccess() {
        return ResponseEntity.ok("OAuth2 authentication successful. You can now use the access token.");
    }
}