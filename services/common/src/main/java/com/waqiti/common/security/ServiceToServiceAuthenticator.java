package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service-to-Service Authentication Handler
 * Manages OAuth2 client credentials flow for inter-service communication
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceToServiceAuthenticator {
    
    // Thread-safe SecureRandom for secure authentication token generation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakServerUrl;
    
    @Value("${keycloak.realm:waqiti}")
    private String realm;
    
    @Value("${service.auth.cache-ttl-seconds:1800}")
    private int cacheTtlSeconds;
    
    @Value("${service.auth.refresh-threshold-seconds:300}")
    private int refreshThresholdSeconds;
    
    private static final String TOKEN_CACHE_PREFIX = "service:token:";
    private static final String SERVICE_REGISTRY_KEY = "service:registry";
    
    private final ConcurrentHashMap<String, ServiceToken> tokenCache = new ConcurrentHashMap<>();
    
    /**
     * Get authentication token for service-to-service communication
     */
    public String getServiceToken(String targetService) {
        String cacheKey = TOKEN_CACHE_PREFIX + targetService;
        
        // Check in-memory cache first
        ServiceToken cachedToken = tokenCache.get(targetService);
        if (cachedToken != null && !isTokenExpiringSoon(cachedToken)) {
            log.debug("Using cached token for service: {}", targetService);
            return cachedToken.getAccessToken();
        }
        
        // Check Redis cache
        cachedToken = getTokenFromRedis(cacheKey);
        if (cachedToken != null && !isTokenExpiringSoon(cachedToken)) {
            log.debug("Using Redis cached token for service: {}", targetService);
            tokenCache.put(targetService, cachedToken);
            return cachedToken.getAccessToken();
        }
        
        // Generate new token
        log.info("Generating new service token for: {}", targetService);
        ServiceToken newToken = generateServiceToken(targetService);
        
        // Cache the token
        cacheToken(targetService, newToken);
        
        return newToken.getAccessToken();
    }
    
    /**
     * Generate service token using client credentials flow
     */
    private ServiceToken generateServiceToken(String serviceName) {
        ServiceCredentials credentials = getServiceCredentials(serviceName);
        
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
            keycloakServerUrl, realm);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        params.add("client_id", credentials.getClientId());
        params.add("client_secret", credentials.getClientSecret());
        params.add("scope", credentials.getRequiredScopes());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                ServiceToken token = new ServiceToken();
                token.setAccessToken((String) body.get("access_token"));
                token.setTokenType((String) body.get("token_type"));
                token.setExpiresIn((Integer) body.get("expires_in"));
                token.setScope((String) body.get("scope"));
                token.setIssuedAt(Instant.now());
                token.setServiceName(serviceName);
                
                log.info("Successfully generated service token for: {}", serviceName);
                return token;
            }
            
            throw new RuntimeException("Failed to generate service token: " + response.getStatusCode());
            
        } catch (Exception e) {
            log.error("Error generating service token for {}: {}", serviceName, e.getMessage());
            throw new ServiceAuthenticationException("Failed to authenticate service: " + serviceName, e);
        }
    }
    
    /**
     * Get service credentials from configuration
     */
    private ServiceCredentials getServiceCredentials(String serviceName) {
        // In production, these would come from secure configuration or Vault
        ServiceCredentials credentials = new ServiceCredentials();
        credentials.setClientId(serviceName);
        credentials.setClientSecret(System.getenv(serviceName.toUpperCase() + "_CLIENT_SECRET"));
        credentials.setRequiredScopes(getRequiredScopes(serviceName));
        
        if (credentials.getClientSecret() == null || credentials.getClientSecret().isEmpty()) {
            log.warn("Client secret not found for service: {}, using fallback", serviceName);
            credentials.setClientSecret(serviceName + "-secret"); // Fallback for development
        }
        
        return credentials;
    }
    
    /**
     * Get required scopes for a service
     */
    private String getRequiredScopes(String serviceName) {
        // Define service-specific scopes
        return switch (serviceName) {
            case "payment-service" -> "payment:read payment:write payment:authorize";
            case "wallet-service" -> "wallet:read wallet:write wallet:manage";
            case "transaction-service" -> "transaction:read transaction:write transaction:verify";
            case "user-service" -> "user:read user:write user:manage profile email";
            case "notification-service" -> "notification:send notification:manage";
            case "fraud-detection-service" -> "fraud:detect fraud:analyze transaction:read";
            case "compliance-service" -> "compliance:check compliance:report user:read transaction:read";
            case "analytics-service" -> "analytics:read analytics:write metrics:collect";
            case "audit-service" -> "audit:write audit:read system:monitor";
            default -> "service:default";
        };
    }
    
    /**
     * Create authenticated HTTP headers for service calls
     */
    public HttpHeaders createAuthenticatedHeaders(String targetService) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getServiceToken(targetService));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Service-Name", getCurrentServiceName());
        headers.add("X-Request-Id", generateRequestId());
        headers.add("X-Trace-Id", generateTraceId());
        return headers;
    }
    
    /**
     * Validate incoming service token
     */
    public boolean validateServiceToken(String token, String expectedService) {
        try {
            String introspectUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", 
                keycloakServerUrl, realm);
            
            ServiceCredentials credentials = getServiceCredentials(getCurrentServiceName());
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", token);
            params.add("client_id", credentials.getClientId());
            params.add("client_secret", credentials.getClientSecret());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(introspectUrl, request, Map.class);
            
            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean active = (boolean) body.getOrDefault("active", false);
                String clientId = (String) body.get("client_id");
                
                if (active && expectedService.equals(clientId)) {
                    log.debug("Service token validated successfully for: {}", expectedService);
                    return true;
                }
            }
            
            log.warn("Invalid service token from: {}", expectedService);
            return false;
            
        } catch (Exception e) {
            log.error("Error validating service token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Register service with central registry
     */
    public void registerService(ServiceRegistration registration) {
        log.info("Registering service: {}", registration.getServiceName());
        
        String registryKey = SERVICE_REGISTRY_KEY + ":" + registration.getServiceName();
        registration.setRegisteredAt(Instant.now());
        registration.setStatus("ACTIVE");
        
        redisTemplate.opsForHash().put(SERVICE_REGISTRY_KEY, registration.getServiceName(), registration);
        redisTemplate.opsForValue().set(registryKey, registration, Duration.ofHours(24));
        
        log.info("Service registered successfully: {}", registration.getServiceName());
    }
    
    /**
     * Get registered service information
     */
    @Cacheable(value = "serviceRegistry", key = "#serviceName")
    public ServiceRegistration getServiceRegistration(String serviceName) {
        String registryKey = SERVICE_REGISTRY_KEY + ":" + serviceName;
        Object registration = redisTemplate.opsForValue().get(registryKey);
        
        if (registration instanceof ServiceRegistration) {
            return (ServiceRegistration) registration;
        }
        
        return null;
    }
    
    /**
     * Renew service token before expiration
     */
    public void renewServiceToken(String serviceName) {
        log.info("Renewing service token for: {}", serviceName);
        
        ServiceToken newToken = generateServiceToken(serviceName);
        cacheToken(serviceName, newToken);
        
        log.info("Service token renewed successfully for: {}", serviceName);
    }
    
    /**
     * Revoke service token
     */
    public void revokeServiceToken(String serviceName) {
        log.info("Revoking service token for: {}", serviceName);
        
        // Remove from caches
        tokenCache.remove(serviceName);
        redisTemplate.delete(TOKEN_CACHE_PREFIX + serviceName);
        
        // Call Keycloak revocation endpoint
        try {
            ServiceCredentials credentials = getServiceCredentials(serviceName);
            String revokeUrl = String.format("%s/realms/%s/protocol/openid-connect/revoke", 
                keycloakServerUrl, realm);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", credentials.getClientId());
            params.add("client_secret", credentials.getClientSecret());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            restTemplate.postForEntity(revokeUrl, request, String.class);
            
            log.info("Service token revoked successfully for: {}", serviceName);
            
        } catch (Exception e) {
            log.error("Error revoking service token for {}: {}", serviceName, e.getMessage());
        }
    }
    
    /**
     * Get current service name from environment
     */
    private String getCurrentServiceName() {
        return System.getenv("SERVICE_NAME") != null ? 
            System.getenv("SERVICE_NAME") : 
            System.getProperty("spring.application.name", "unknown-service");
    }
    
    /**
     * Check if token is expiring soon
     */
    private boolean isTokenExpiringSoon(ServiceToken token) {
        if (token == null || token.getExpiresIn() == null) {
            return true;
        }
        
        Instant expirationTime = token.getIssuedAt().plusSeconds(token.getExpiresIn());
        Instant refreshThreshold = Instant.now().plusSeconds(refreshThresholdSeconds);
        
        return expirationTime.isBefore(refreshThreshold);
    }
    
    /**
     * Cache token in memory and Redis
     */
    private void cacheToken(String serviceName, ServiceToken token) {
        tokenCache.put(serviceName, token);
        
        String cacheKey = TOKEN_CACHE_PREFIX + serviceName;
        redisTemplate.opsForValue().set(cacheKey, token, Duration.ofSeconds(cacheTtlSeconds));
    }
    
    /**
     * Get token from Redis cache
     */
    private ServiceToken getTokenFromRedis(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof ServiceToken) {
                return (ServiceToken) cached;
            }
            return objectMapper.convertValue(cached, ServiceToken.class);
        } catch (Exception e) {
            log.debug("Failed to get token from Redis: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate cryptographically secure unique request ID.
     * Format: req-{timestamp}-{secure-random-hex}
     */
    private String generateRequestId() {
        long timestamp = System.currentTimeMillis();
        
        // Generate 8 bytes of secure random data and convert to hex
        byte[] randomBytes = new byte[8];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : randomBytes) {
            hexString.append(String.format("%02x", b));
        }
        
        return "req-" + timestamp + "-" + hexString.toString();
    }
    
    /**
     * Generate trace ID for distributed tracing
     */
    private String generateTraceId() {
        return "trace-" + System.currentTimeMillis();
    }
    
    // Data classes
    
    @Data
    public static class ServiceToken {
        private String accessToken;
        private String tokenType;
        private Integer expiresIn;
        private String scope;
        private Instant issuedAt;
        private String serviceName;
    }
    
    @Data
    public static class ServiceCredentials {
        private String clientId;
        private String clientSecret;
        private String requiredScopes;
    }
    
    @Data
    public static class ServiceRegistration {
        private String serviceName;
        private String serviceUrl;
        private String healthCheckUrl;
        private String version;
        private String status;
        private Instant registeredAt;
        private Map<String, String> metadata;
    }
    
    public static class ServiceAuthenticationException extends RuntimeException {
        public ServiceAuthenticationException(String message) {
            super(message);
        }
        
        public ServiceAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}