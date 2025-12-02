package com.waqiti.user.api;

import com.waqiti.common.ratelimit.RateLimited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    /**
     * Redirect to Keycloak login - Legacy JWT authentication has been migrated to Keycloak
     */
    @PostMapping("/login")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    public ResponseEntity<?> login() {
        log.info("Legacy login endpoint called - redirecting to Keycloak");
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Please use Keycloak OAuth2 flow for authentication");
        response.put("authUrl", String.format("%s/realms/%s/protocol/openid-connect/auth", authServerUrl, realm));
        response.put("clientId", clientId);
        
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).body(response);
    }


    /**
     * MFA verification is now handled by Keycloak
     */
    @PostMapping("/mfa/verify")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    public ResponseEntity<?> verifyMfa() {
        log.info("Legacy MFA verification endpoint called - now handled by Keycloak");
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "MFA verification is now handled by Keycloak during the OAuth2 flow");
        response.put("authUrl", String.format("%s/realms/%s/protocol/openid-connect/auth", authServerUrl, realm));
        
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).body(response);
    }

    /**
     * Token refresh is now handled by Keycloak
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken() {
        log.info("Legacy token refresh endpoint called - now handled by Keycloak");
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Token refresh is now handled by Keycloak OAuth2 flow");
        response.put("tokenUrl", String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm));
        
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).body(response);
    }

    /**
     * Logout is now handled by Keycloak
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        log.info("Legacy logout endpoint called - now handled by Keycloak");
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logout is now handled by Keycloak OAuth2 flow");
        response.put("logoutUrl", String.format("%s/realms/%s/protocol/openid-connect/logout", authServerUrl, realm));
        
        return ResponseEntity.ok(response);
    }

}