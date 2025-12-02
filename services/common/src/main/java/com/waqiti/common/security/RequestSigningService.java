package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Request Signing Service for API Security
 * 
 * Implements HMAC-SHA256 request signing following AWS Signature Version 4 patterns:
 * - Request integrity verification
 * - Replay attack prevention
 * - Timestamp-based expiration
 * - Nonce-based duplicate detection
 * - Multiple signing algorithms support
 * - Key rotation support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestSigningService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final String SHA256 = "SHA-256";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String NONCE_HEADER = "X-Nonce";
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${request.signing.enabled:true}")
    private boolean signingEnabled;
    
    @Value("${request.signing.algorithm:HmacSHA256}")
    private String defaultAlgorithm;
    
    @Value("${request.signing.timestamp-tolerance-seconds:300}")
    private int timestampToleranceSeconds;
    
    @Value("${request.signing.nonce-ttl-seconds:3600}")
    private int nonceTtlSeconds;
    
    @Value("${request.signing.include-headers:true}")
    private boolean includeHeaders;
    
    @Value("${request.signing.include-query-params:true}")
    private boolean includeQueryParams;
    
    // Client key registry (in production, load from secure store)
    private final Map<String, ClientCredentials> clientRegistry = new ConcurrentHashMap<>();
    
    // Signing configuration per client
    private final Map<String, SigningConfiguration> signingConfigs = new ConcurrentHashMap<>();

    public void init() {
        // Initialize with default signing configurations
        registerDefaultClients();
        log.info("Request signing service initialized with signing {}", 
            signingEnabled ? "enabled" : "disabled");
    }

    /**
     * Generate request signature
     */
    public RequestSignature generateSignature(SigningRequest request) {
        if (!signingEnabled) {
            return RequestSignature.disabled();
        }

        try {
            ClientCredentials credentials = getClientCredentials(request.getClientId());
            SigningConfiguration config = getSigningConfiguration(request.getClientId());
            
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String nonce = generateNonce();
            
            // Build canonical request
            String canonicalRequest = buildCanonicalRequest(request, timestamp, nonce);
            
            // Calculate signature
            String signature = calculateSignature(canonicalRequest, credentials, config);
            
            log.debug("Generated signature for client: {} with algorithm: {}", 
                request.getClientId(), config.getAlgorithm());
            
            return RequestSignature.builder()
                .signature(signature)
                .algorithm(config.getAlgorithm())
                .timestamp(timestamp)
                .nonce(nonce)
                .clientId(request.getClientId())
                .canonicalRequest(canonicalRequest)
                .enabled(true)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate signature for client: {}", request.getClientId(), e);
            throw new RequestSigningException("Signature generation failed", e);
        }
    }

    /**
     * Validate request signature
     */
    public SignatureValidationResult validateSignature(SigningRequest request, 
                                                      String providedSignature, 
                                                      String timestamp, 
                                                      String nonce) {
        if (!signingEnabled) {
            return SignatureValidationResult.disabled();
        }

        try {
            // Basic validation
            if (providedSignature == null || timestamp == null || nonce == null) {
                return SignatureValidationResult.failed("Missing signature components");
            }
            
            ClientCredentials credentials = getClientCredentials(request.getClientId());
            if (credentials == null) {
                return SignatureValidationResult.failed("Unknown client");
            }
            
            SigningConfiguration config = getSigningConfiguration(request.getClientId());
            
            // Validate timestamp
            if (!isTimestampValid(timestamp)) {
                return SignatureValidationResult.failed("Timestamp out of range");
            }
            
            // Check for replay attack
            if (isNonceUsed(nonce, request.getClientId())) {
                return SignatureValidationResult.failed("Nonce already used (replay attack)");
            }
            
            // Build canonical request
            String canonicalRequest = buildCanonicalRequest(request, timestamp, nonce);
            
            // Calculate expected signature
            String expectedSignature = calculateSignature(canonicalRequest, credentials, config);
            
            // Constant-time comparison to prevent timing attacks
            boolean signatureValid = constantTimeEquals(providedSignature, expectedSignature);
            
            if (signatureValid) {
                // Mark nonce as used
                markNonceAsUsed(nonce, request.getClientId());
                
                log.debug("Signature validation successful for client: {}", request.getClientId());
                return SignatureValidationResult.success();
            } else {
                log.warn("Signature validation failed for client: {}", request.getClientId());
                return SignatureValidationResult.failed("Invalid signature");
            }
            
        } catch (Exception e) {
            log.error("Signature validation error for client: {}", request.getClientId(), e);
            return SignatureValidationResult.failed("Validation error");
        }
    }

    /**
     * Register client credentials
     */
    public void registerClient(String clientId, String apiKey, String secretKey) {
        ClientCredentials credentials = ClientCredentials.builder()
            .clientId(clientId)
            .apiKey(apiKey)
            .secretKey(secretKey)
            .createdAt(Instant.now())
            .enabled(true)
            .keyRotationDate(null)
            .build();
            
        SigningConfiguration config = SigningConfiguration.builder()
            .clientId(clientId)
            .algorithm(defaultAlgorithm)
            .includeHeaders(includeHeaders)
            .includeQueryParams(includeQueryParams)
            .timestampTolerance(timestampToleranceSeconds)
            .requireNonce(true)
            .customHeaders(Set.of("Content-Type", "User-Agent"))
            .excludedHeaders(Set.of("Authorization", "Cookie"))
            .build();
            
        clientRegistry.put(clientId, credentials);
        signingConfigs.put(clientId, config);
        
        log.info("Registered client: {} for request signing", clientId);
    }

    /**
     * Rotate client credentials
     */
    public void rotateClientKey(String clientId, String newSecretKey) {
        ClientCredentials existing = clientRegistry.get(clientId);
        if (existing == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        
        ClientCredentials rotated = existing.toBuilder()
            .secretKey(newSecretKey)
            .keyRotationDate(Instant.now())
            .build();
            
        clientRegistry.put(clientId, rotated);
        
        log.info("Rotated secret key for client: {}", clientId);
    }

    // Private helper methods

    private String buildCanonicalRequest(SigningRequest request, String timestamp, String nonce) {
        StringBuilder canonical = new StringBuilder();
        
        // HTTP Method
        canonical.append(request.getMethod().toUpperCase()).append("\n");
        
        // URI (without query parameters)
        String uri = request.getUri();
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        canonical.append(uri).append("\n");
        
        // Query parameters (sorted)
        if (includeQueryParams && request.getQueryParameters() != null) {
            String queryString = request.getQueryParameters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
            canonical.append(queryString);
        }
        canonical.append("\n");
        
        // Headers (sorted, canonical format)
        if (includeHeaders) {
            SigningConfiguration config = getSigningConfiguration(request.getClientId());
            Map<String, String> headersToSign = new TreeMap<>();
            
            if (request.getHeaders() != null) {
                request.getHeaders().forEach((key, value) -> {
                    String lowerKey = key.toLowerCase();
                    if (!config.getExcludedHeaders().contains(key) && 
                        (config.getCustomHeaders().isEmpty() || config.getCustomHeaders().contains(key))) {
                        headersToSign.put(lowerKey, value.trim());
                    }
                });
            }
            
            // Add signing-specific headers
            headersToSign.put("x-timestamp", timestamp);
            headersToSign.put("x-nonce", nonce);
            headersToSign.put("x-client-id", request.getClientId());
            
            for (Map.Entry<String, String> header : headersToSign.entrySet()) {
                canonical.append(header.getKey()).append(":").append(header.getValue()).append("\n");
            }
        }
        canonical.append("\n");
        
        // Body hash
        String bodyHash = hashRequestBody(request.getBody());
        canonical.append(bodyHash);
        
        return canonical.toString();
    }

    private String calculateSignature(String canonicalRequest, ClientCredentials credentials, 
                                    SigningConfiguration config) throws NoSuchAlgorithmException, InvalidKeyException {
        
        // Create signing key (can be enhanced with key derivation)
        byte[] signingKey = createSigningKey(credentials.getSecretKey(), config);
        
        // Calculate signature
        Mac mac = Mac.getInstance(config.getAlgorithm());
        SecretKeySpec keySpec = new SecretKeySpec(signingKey, config.getAlgorithm());
        mac.init(keySpec);
        
        byte[] signature = mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(signature);
    }

    private byte[] createSigningKey(String secretKey, SigningConfiguration config) {
        // Basic key derivation - in production, use proper key derivation
        // Could implement AWS Signature V4 key derivation here
        return secretKey.getBytes(StandardCharsets.UTF_8);
    }

    private String hashRequestBody(String body) {
        try {
            if (body == null || body.isEmpty()) {
                body = "";
            }
            
            MessageDigest digest = MessageDigest.getInstance(SHA256);
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String generateNonce() {
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }

    private boolean isTimestampValid(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            long timeDifference = Math.abs(currentTime - requestTime);
            
            return timeDifference <= timestampToleranceSeconds;
            
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNonceUsed(String nonce, String clientId) {
        String key = "nonce:" + clientId + ":" + nonce;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markNonceAsUsed(String nonce, String clientId) {
        String key = "nonce:" + clientId + ":" + nonce;
        redisTemplate.opsForValue().set(key, "used", Duration.ofSeconds(nonceTtlSeconds));
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }

    private ClientCredentials getClientCredentials(String clientId) {
        return clientRegistry.get(clientId);
    }

    private SigningConfiguration getSigningConfiguration(String clientId) {
        return signingConfigs.getOrDefault(clientId, getDefaultSigningConfiguration());
    }

    private SigningConfiguration getDefaultSigningConfiguration() {
        return SigningConfiguration.builder()
            .algorithm(defaultAlgorithm)
            .includeHeaders(includeHeaders)
            .includeQueryParams(includeQueryParams)
            .timestampTolerance(timestampToleranceSeconds)
            .requireNonce(true)
            .customHeaders(new HashSet<>())
            .excludedHeaders(Set.of("Authorization", "Cookie", "X-Forwarded-For"))
            .build();
    }

    private void registerDefaultClients() {
        // Register default clients for development/testing
        registerClient("waqiti-web-app", "web_app_key_123", "web_app_secret_456");
        registerClient("waqiti-mobile-app", "mobile_app_key_789", "mobile_app_secret_012");
        registerClient("waqiti-admin-panel", "admin_key_345", "admin_secret_678");
    }

    // Data classes and DTOs

    @lombok.Data
    @lombok.Builder
    public static class SigningRequest {
        private String clientId;
        private String method;
        private String uri;
        private Map<String, String> queryParameters;
        private Map<String, String> headers;
        private String body;
    }

    @lombok.Data
    @lombok.Builder
    public static class RequestSignature {
        private String signature;
        private String algorithm;
        private String timestamp;
        private String nonce;
        private String clientId;
        private String canonicalRequest;
        private boolean enabled;

        public static RequestSignature disabled() {
            return RequestSignature.builder()
                .enabled(false)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class SignatureValidationResult {
        private boolean valid;
        private String errorMessage;
        private String clientId;

        public static SignatureValidationResult success() {
            return SignatureValidationResult.builder()
                .valid(true)
                .build();
        }

        public static SignatureValidationResult failed(String message) {
            return SignatureValidationResult.builder()
                .valid(false)
                .errorMessage(message)
                .build();
        }

        public static SignatureValidationResult disabled() {
            return SignatureValidationResult.builder()
                .valid(true)
                .errorMessage("Signing disabled")
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder(toBuilder = true)
    public static class ClientCredentials {
        private String clientId;
        private String apiKey;
        private String secretKey;
        private Instant createdAt;
        private Instant keyRotationDate;
        private boolean enabled;
    }

    @lombok.Data
    @lombok.Builder
    public static class SigningConfiguration {
        private String clientId;
        private String algorithm;
        private boolean includeHeaders;
        private boolean includeQueryParams;
        private int timestampTolerance;
        private boolean requireNonce;
        private Set<String> customHeaders;
        private Set<String> excludedHeaders;
    }

    public static class RequestSigningException extends RuntimeException {
        public RequestSigningException(String message) {
            super(message);
        }

        public RequestSigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}