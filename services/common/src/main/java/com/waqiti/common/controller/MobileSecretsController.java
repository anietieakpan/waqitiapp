package com.waqiti.common.controller;

import com.waqiti.common.config.SecretsConfig;
import com.waqiti.common.dto.ApiResponse;
import com.waqiti.common.security.annotation.RequiresMobileAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for providing mobile app configuration secrets
 * Integrates with existing Vault infrastructure to securely deliver API keys
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mobile")
@RequiresMobileAuth
@RequiredArgsConstructor
public class MobileSecretsController {

    private final SecretsConfig.ApiSecrets apiSecrets;
    
    // Define which secrets mobile apps are allowed to access
    private static final Map<String, String> ALLOWED_MOBILE_SECRETS = Map.of(
        "google.maps.api.key", "googleMapsApiKey",
        "stripe.publishable.key", "stripePublishableKey", 
        "firebase.api.key", "firebaseApiKey",
        "mixpanel.token", "mixpanelToken",
        "sentry.dsn", "sentryDsn"
    );

    /**
     * Get mobile app secrets from Vault
     * POST /api/v1/mobile/secrets
     * 
     * SECURITY: This endpoint provides API keys to mobile apps and requires
     * both mobile authentication and specific mobile access permissions
     */
    @PostMapping("/secrets")
    @PreAuthorize("hasAuthority('SCOPE_mobile:secrets:read') and hasRole('MOBILE_APP')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMobileSecrets(
            @RequestBody MobileSecretsRequest request,
            Authentication authentication) {
        
        try {
            log.info("Mobile secrets request from user: {}", authentication.getName());
            
            // Validate request
            if (request.getRequiredSecrets() == null || request.getRequiredSecrets().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Required secrets list cannot be empty"));
            }

            Map<String, String> secrets = new HashMap<>();
            
            // Only provide secrets that are in the allowed list
            for (String secretKey : request.getRequiredSecrets()) {
                if (!ALLOWED_MOBILE_SECRETS.containsKey(secretKey)) {
                    log.warn("Unauthorized secret requested: {} by user: {}", secretKey, authentication.getName());
                    continue;
                }
                
                try {
                    String secretValue = getSecretValue(secretKey);
                    if (secretValue != null && !secretValue.isEmpty()) {
                        secrets.put(secretKey, secretValue);
                    } else {
                        log.warn("Secret {} is null or empty", secretKey);
                    }
                } catch (Exception e) {
                    log.error("Failed to retrieve secret: {}", secretKey, e);
                    // Don't fail the entire request for one missing secret
                }
            }

            Map<String, Object> response = Map.of(
                "secrets", secrets,
                "timestamp", System.currentTimeMillis(),
                "ttl", 300 // 5 minutes TTL
            );

            log.info("Successfully provided {} secrets to mobile app", secrets.size());
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Error providing mobile secrets", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve mobile secrets"));
        }
    }

    /**
     * Get specific secret by key
     * GET /api/v1/mobile/secrets/{secretKey}
     */
    @GetMapping("/secrets/{secretKey}")
    @PreAuthorize("hasAuthority('SCOPE_mobile:secrets:read') and hasRole('MOBILE_APP')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSecret(
            @PathVariable String secretKey,
            Authentication authentication) {
        
        try {
            if (!ALLOWED_MOBILE_SECRETS.containsKey(secretKey)) {
                log.warn("Unauthorized secret requested: {} by user: {}", secretKey, authentication.getName());
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Secret not available for mobile access"));
            }

            String secretValue = getSecretValue(secretKey);
            if (secretValue == null || secretValue.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = Map.of(
                "key", secretKey,
                "value", secretValue,
                "timestamp", System.currentTimeMillis()
            );

            log.info("Provided secret {} to mobile user: {}", secretKey, authentication.getName());
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Error retrieving secret: {}", secretKey, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve secret"));
        }
    }

    /**
     * Check which secrets are available
     * GET /api/v1/mobile/secrets/available
     */
    @GetMapping("/secrets/available")
    @PreAuthorize("hasAuthority('SCOPE_mobile:secrets:read') and hasRole('MOBILE_APP')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAvailableSecrets(
            Authentication authentication) {
        
        try {
            Map<String, Boolean> availability = new HashMap<>();
            
            for (String secretKey : ALLOWED_MOBILE_SECRETS.keySet()) {
                try {
                    String value = getSecretValue(secretKey);
                    availability.put(secretKey, value != null && !value.isEmpty());
                } catch (Exception e) {
                    availability.put(secretKey, false);
                }
            }

            Map<String, Object> response = Map.of(
                "available", availability,
                "allowedSecrets", ALLOWED_MOBILE_SECRETS.keySet(),
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Error checking secret availability", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to check secret availability"));
        }
    }

    private String getSecretValue(String secretKey) {
        return switch (secretKey) {
            case "google.maps.api.key" -> apiSecrets.getGoogleMapsApiKey();
            case "stripe.publishable.key" -> getStripePublishableKey();
            case "firebase.api.key" -> apiSecrets.getFirebaseServerKey();
            case "mixpanel.token" -> getMixpanelToken();
            case "sentry.dsn" -> getSentryDsn();
            default -> null;
        };
    }

    private String getStripePublishableKey() {
        // Stripe has separate publishable and secret keys
        // The publishable key is safe for mobile/frontend use
        try {
            return apiSecrets.getOptionalSecret("stripe.publishable.key").orElse("");
        } catch (Exception e) {
            log.warn("CRITICAL: Stripe publishable key not configured - Payment functionality disabled", e);
            return ""; // Return empty string instead of null for mobile compatibility
        }
    }

    private String getMixpanelToken() {
        try {
            return apiSecrets.getOptionalSecret("mixpanel.token").orElse(null);
        } catch (Exception e) {
            log.warn("Mixpanel token not configured", e);
            return null;
        }
    }

    private String getSentryDsn() {
        try {
            return apiSecrets.getOptionalSecret("sentry.dsn").orElse(null);
        } catch (Exception e) {
            log.warn("Sentry DSN not configured", e);
            return null;
        }
    }

    /**
     * Request DTO for mobile secrets
     */
    public static class MobileSecretsRequest {
        private List<String> requiredSecrets;

        public List<String> getRequiredSecrets() {
            return requiredSecrets;
        }

        public void setRequiredSecrets(List<String> requiredSecrets) {
            this.requiredSecrets = requiredSecrets;
        }
    }
}