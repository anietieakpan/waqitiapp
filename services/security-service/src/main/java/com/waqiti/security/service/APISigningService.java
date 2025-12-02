package com.waqiti.security.service;

import com.waqiti.security.dto.SignedRequest;
import com.waqiti.security.dto.SignatureVerificationResult;
import com.waqiti.security.exception.SignatureVerificationException;
import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class APISigningService {

    private final CacheService cacheService;
    
    @Value("${security.api-signing.algorithm:RS256}")
    private String defaultAlgorithm;
    
    @Value("${security.api-signing.key-rotation-hours:24}")
    private int keyRotationHours;
    
    @Value("${security.api-signing.max-timestamp-skew-seconds:300}")
    private int maxTimestampSkewSeconds;
    
    @Value("${security.api-signing.nonce-cache-minutes:15}")
    private int nonceCacheMinutes;
    
    private final Map<String, KeyPair> signingKeys = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> verificationKeys = new ConcurrentHashMap<>();
    
    /**
     * Signs an API request with the specified algorithm
     */
    public SignedRequest signRequest(String clientId, String method, String uri, 
                                   String body, Map<String, String> headers) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String nonce = generateNonce();
            
            // Create canonical string
            String canonicalString = createCanonicalString(method, uri, body, headers, timestamp, nonce);
            
            // Get or create signing key
            KeyPair keyPair = getOrCreateSigningKey(clientId);
            
            // Sign the canonical string
            String signature = signString(canonicalString, keyPair.getPrivate(), defaultAlgorithm);
            
            return SignedRequest.builder()
                    .clientId(clientId)
                    .method(method)
                    .uri(uri)
                    .body(body)
                    .headers(headers)
                    .timestamp(timestamp)
                    .nonce(nonce)
                    .algorithm(defaultAlgorithm)
                    .signature(signature)
                    .keyId(getKeyId(clientId))
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to sign API request", e);
            throw new SignatureVerificationException("Failed to sign request", e);
        }
    }
    
    /**
     * Verifies an API request signature
     */
    public SignatureVerificationResult verifySignature(SignedRequest signedRequest) {
        try {
            // Validate timestamp
            if (!isValidTimestamp(signedRequest.getTimestamp())) {
                return SignatureVerificationResult.invalid("Request timestamp is too old or too far in the future");
            }
            
            // Check nonce for replay protection
            if (isNonceUsed(signedRequest.getNonce())) {
                return SignatureVerificationResult.invalid("Nonce has already been used (replay attack detected)");
            }
            
            // Create canonical string
            String canonicalString = createCanonicalString(
                    signedRequest.getMethod(),
                    signedRequest.getUri(),
                    signedRequest.getBody(),
                    signedRequest.getHeaders(),
                    signedRequest.getTimestamp(),
                    signedRequest.getNonce()
            );
            
            // Get verification key
            PublicKey publicKey = getVerificationKey(signedRequest.getClientId(), signedRequest.getKeyId());
            if (publicKey == null) {
                return SignatureVerificationResult.invalid("Invalid client ID or key ID");
            }
            
            // Verify signature
            boolean isValid = verifySignature(canonicalString, signedRequest.getSignature(), 
                    publicKey, signedRequest.getAlgorithm());
            
            if (isValid) {
                // Mark nonce as used
                markNonceAsUsed(signedRequest.getNonce());
                return SignatureVerificationResult.valid(signedRequest.getClientId());
            } else {
                return SignatureVerificationResult.invalid("Invalid signature");
            }
            
        } catch (Exception e) {
            log.error("Failed to verify API signature", e);
            return SignatureVerificationResult.invalid("Signature verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Signs a string with HMAC
     */
    public String signWithHMAC(String data, String secret, String algorithm) {
        try {
            String hmacAlgorithm = "Hmac" + algorithm.replace("HS", "SHA");
            
            Mac mac = Mac.getInstance(hmacAlgorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), 
                    hmacAlgorithm
            );
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            throw new SignatureVerificationException("Failed to create HMAC signature", e);
        }
    }
    
    /**
     * Verifies HMAC signature
     */
    public boolean verifyHMAC(String data, String signature, String secret, String algorithm) {
        try {
            String computedSignature = signWithHMAC(data, secret, algorithm);
            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    computedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify HMAC signature", e);
            return false;
        }
    }
    
    /**
     * Generates a new key pair for client
     */
    public KeyPair generateKeyPair(String clientId, String algorithm) {
        try {
            KeyPairGenerator keyGen;
            
            if (algorithm.startsWith("RS") || algorithm.startsWith("PS")) {
                keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
            } else if (algorithm.startsWith("ES")) {
                keyGen = KeyPairGenerator.getInstance("EC");
                keyGen.initialize(256);
            } else {
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
            }
            
            KeyPair keyPair = keyGen.generateKeyPair();
            
            // Store the key pair
            signingKeys.put(clientId, keyPair);
            verificationKeys.put(clientId, keyPair.getPublic());
            
            // Cache the public key for verification
            String keyId = getKeyId(clientId);
            cacheService.set("public-key:" + keyId, keyPair.getPublic(), 
                    java.time.Duration.ofHours(keyRotationHours));
            
            log.info("Generated new key pair for client: {} with algorithm: {}", clientId, algorithm);
            return keyPair;
            
        } catch (Exception e) {
            throw new SignatureVerificationException("Failed to generate key pair", e);
        }
    }
    
    /**
     * Rotates keys for a client
     */
    public void rotateKeys(String clientId) {
        log.info("Rotating keys for client: {}", clientId);
        
        // Generate new key pair
        generateKeyPair(clientId, defaultAlgorithm);
        
        // The old key should be kept for some time to verify existing signatures
        // Implementation would include key versioning
    }
    
    /**
     * Exports public key for client integration
     */
    public String exportPublicKey(String clientId, String format) {
        PublicKey publicKey = verificationKeys.get(clientId);
        if (publicKey == null) {
            throw new IllegalArgumentException("No public key found for client: " + clientId);
        }
        
        if ("PEM".equalsIgnoreCase(format)) {
            return "-----BEGIN PUBLIC KEY-----\n" +
                   Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded()) +
                   "\n-----END PUBLIC KEY-----";
        } else if ("JWK".equalsIgnoreCase(format)) {
            return exportAsJWK(publicKey);
        } else {
            return Base64.getEncoder().encodeToString(publicKey.getEncoded());
        }
    }
    
    // Helper methods
    
    private String createCanonicalString(String method, String uri, String body, 
                                       Map<String, String> headers, String timestamp, String nonce) {
        StringBuilder canonical = new StringBuilder();
        
        // HTTP method
        canonical.append(method.toUpperCase()).append("\n");
        
        // URI (normalized)
        canonical.append(normalizeUri(uri)).append("\n");
        
        // Timestamp
        canonical.append(timestamp).append("\n");
        
        // Nonce
        canonical.append(nonce).append("\n");
        
        // Content hash
        canonical.append(hashContent(body)).append("\n");
        
        // Signed headers (sorted)
        List<String> signedHeaders = getSignedHeaders(headers);
        Collections.sort(signedHeaders);
        
        for (String header : signedHeaders) {
            canonical.append(header.toLowerCase()).append(":")
                    .append(normalizeHeaderValue(headers.get(header))).append("\n");
        }
        
        canonical.append("\n");
        canonical.append(String.join(";", signedHeaders));
        
        return canonical.toString();
    }
    
    private String signString(String data, PrivateKey privateKey, String algorithm) throws Exception {
        Signature signature = Signature.getInstance(getJavaAlgorithm(algorithm));
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
    
    private boolean verifySignature(String data, String signatureString, 
                                  PublicKey publicKey, String algorithm) throws Exception {
        Signature signature = Signature.getInstance(getJavaAlgorithm(algorithm));
        signature.initVerify(publicKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        
        byte[] signatureBytes = Base64.getDecoder().decode(signatureString);
        return signature.verify(signatureBytes);
    }
    
    private String getJavaAlgorithm(String algorithm) {
        switch (algorithm) {
            case "RS256":
                return "SHA256withRSA";
            case "RS384":
                return "SHA384withRSA";
            case "RS512":
                return "SHA512withRSA";
            case "ES256":
                return "SHA256withECDSA";
            case "ES384":
                return "SHA384withECDSA";
            case "ES512":
                return "SHA512withECDSA";
            case "PS256":
                return "SHA256withRSAandMGF1";
            case "PS384":
                return "SHA384withRSAandMGF1";
            case "PS512":
                return "SHA512withRSAandMGF1";
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }
    
    private KeyPair getOrCreateSigningKey(String clientId) {
        KeyPair keyPair = signingKeys.get(clientId);
        if (keyPair == null) {
            keyPair = generateKeyPair(clientId, defaultAlgorithm);
        }
        return keyPair;
    }
    
    private PublicKey getVerificationKey(String clientId, String keyId) {
        // First try local cache
        PublicKey publicKey = verificationKeys.get(clientId);
        if (publicKey != null) {
            return publicKey;
        }
        
        // Try cache service
        return cacheService.get("public-key:" + keyId, PublicKey.class);
    }
    
    private String getKeyId(String clientId) {
        return "key_" + clientId + "_" + System.currentTimeMillis();
    }
    
    private String generateNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }
    
    private boolean isValidTimestamp(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            long diff = Math.abs(currentTime - requestTime);
            
            return diff <= maxTimestampSkewSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isNonceUsed(String nonce) {
        String cacheKey = "nonce:" + nonce;
        return cacheService.get(cacheKey, Boolean.class) != null;
    }
    
    private void markNonceAsUsed(String nonce) {
        String cacheKey = "nonce:" + nonce;
        cacheService.set(cacheKey, true, java.time.Duration.ofMinutes(nonceCacheMinutes));
    }
    
    private String normalizeUri(String uri) {
        // Remove query parameters and normalize path
        int queryIndex = uri.indexOf('?');
        if (queryIndex != -1) {
            uri = uri.substring(0, queryIndex);
        }
        
        // Ensure it starts with /
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        
        // Remove trailing slash unless it's root
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        
        return uri;
    }
    
    private String hashContent(String content) {
        if (content == null || content.isEmpty()) {
            content = "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private List<String> getSignedHeaders(Map<String, String> headers) {
        List<String> signedHeaders = new ArrayList<>();
        
        // Always include critical headers
        String[] criticalHeaders = {"authorization", "content-type", "date", "host", "x-api-key"};
        
        for (String header : criticalHeaders) {
            if (headers.containsKey(header)) {
                signedHeaders.add(header);
            }
        }
        
        // Add any x-waqiti- headers
        headers.keySet().stream()
                .filter(h -> h.toLowerCase().startsWith("x-waqiti-"))
                .forEach(signedHeaders::add);
        
        return signedHeaders;
    }
    
    private String normalizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Trim and collapse multiple spaces
        return value.trim().replaceAll("\\s+", " ");
    }
    
    private String exportAsJWK(PublicKey publicKey) {
        // Simplified JWK export - would need proper implementation for production
        return "{\n" +
               "  \"kty\": \"RSA\",\n" +
               "  \"use\": \"sig\",\n" +
               "  \"alg\": \"" + defaultAlgorithm + "\",\n" +
               "  \"n\": \"" + Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getEncoded()) + "\"\n" +
               "}";
    }
}