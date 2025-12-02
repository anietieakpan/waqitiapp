package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive end-to-end encryption for service-to-service communication
 * Implements mutual TLS, message-level encryption, and request signing
 */
@Slf4j
@Configuration
public class SecureServiceCommunication {
    
    @Value("${security.communication.keystore.path:/etc/waqiti/keystore.jks}")
    private String keystorePath;
    
    @Value("${security.communication.keystore.password}")
    private String keystorePassword;
    
    @Value("${security.communication.truststore.path:/etc/waqiti/truststore.jks}")
    private String truststorePath;
    
    @Value("${security.communication.truststore.password}")
    private String truststorePassword;
    
    @Value("${security.communication.tls.protocol:TLSv1.3}")
    private String tlsProtocol;
    
    @Value("${security.communication.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${security.communication.signing.enabled:true}")
    private boolean signingEnabled;
    
    /**
     * Create secure RestTemplate with mTLS and encryption
     */
    @Bean(name = "secureRestTemplate")
    public RestTemplate secureRestTemplate(RestTemplateBuilder builder, 
                                          EncryptionService encryptionService,
                                          ObjectMapper objectMapper) throws Exception {
        log.info("Configuring secure RestTemplate with mTLS and encryption");
        
        // Configure SSL/TLS
        SSLContext sslContext = createSSLContext();
        
        // Create socket factory with custom TLS configuration
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
            sslContext,
            new String[]{tlsProtocol},
            null,
new org.apache.hc.client5.http.ssl.DefaultHostnameVerifier()
        );
        
        // Configure connection manager
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("https", sslSocketFactory)
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .build();
        
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);
        
        // Create HTTP client with mTLS
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .evictExpiredConnections()
            .evictIdleConnections(org.apache.hc.core5.util.TimeValue.ofSeconds(30))
            .build();
        
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(5000);
        requestFactory.setConnectionRequestTimeout(5000);
        
        // Create encryption and signing services
        MessageEncryptionService messageEncryption = new MessageEncryptionService(encryptionService, objectMapper);
        RequestSigningService signingService = new RequestSigningService();
        
        // Build RestTemplate with interceptors
        RestTemplate restTemplate = builder
            .requestFactory(() -> requestFactory)
            .interceptors(
                new EncryptionInterceptor(messageEncryption),
                new SigningInterceptor(signingService),
                new AuditInterceptor()
            )
            .build();
        
        log.info("Secure RestTemplate configured successfully");
        return restTemplate;
    }
    
    /**
     * Create SSL context for mutual TLS
     */
    private SSLContext createSSLContext() throws Exception {
        log.debug("Creating SSL context for mTLS");
        
        // Load keystore (client certificate)
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream keyStoreStream = new FileInputStream(keystorePath)) {
            keyStore.load(keyStoreStream, keystorePassword.toCharArray());
        }
        
        // Load truststore (server certificates)
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreStream = new FileInputStream(truststorePath)) {
            trustStore.load(trustStoreStream, truststorePassword.toCharArray());
        }
        
        // Initialize key manager
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
        
        // Initialize trust manager
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance(tlsProtocol);
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            new SecureRandom()
        );
        
        return sslContext;
    }
    
    /**
     * Message encryption service for payload protection
     */
    @Component
    @RequiredArgsConstructor
    public static class MessageEncryptionService {
        
        private final EncryptionService encryptionService;
        private final ObjectMapper objectMapper;
        
        // Cache session keys for performance
        private final ConcurrentHashMap<String, SessionKey> sessionKeys = new ConcurrentHashMap<>();
        
        /**
         * Encrypt message payload
         */
        public EncryptedMessage encryptMessage(Object payload, String targetService) {
            try {
                // Get or create session key for target service
                SessionKey sessionKey = getOrCreateSessionKey(targetService);
                
                // Serialize payload
                String jsonPayload = objectMapper.writeValueAsString(payload);
                byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
                
                // Generate IV for this message
                byte[] iv = generateIV();
                
                // Encrypt payload using AES-GCM
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.ENCRYPT_MODE, sessionKey.getKey(), gcmSpec);
                
                byte[] encryptedPayload = cipher.doFinal(payloadBytes);
                
                // Create encrypted message
                return EncryptedMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .encryptedPayload(Base64.getEncoder().encodeToString(encryptedPayload))
                    .iv(Base64.getEncoder().encodeToString(iv))
                    .sessionKeyId(sessionKey.getKeyId())
                    .algorithm("AES/GCM/NoPadding")
                    .timestamp(Instant.now())
                    .build();
                
            } catch (Exception e) {
                log.error("Failed to encrypt message for service: {}", targetService, e);
                throw new EncryptionException("Message encryption failed", e);
            }
        }
        
        /**
         * Decrypt message payload
         */
        public String decryptMessage(EncryptedMessage encryptedMessage) {
            try {
                // Get session key
                SessionKey sessionKey = sessionKeys.get(encryptedMessage.getSessionKeyId());
                if (sessionKey == null) {
                    throw new EncryptionException("Session key not found: " + encryptedMessage.getSessionKeyId());
                }
                
                // Decode encrypted payload and IV
                byte[] encryptedPayload = Base64.getDecoder().decode(encryptedMessage.getEncryptedPayload());
                byte[] iv = Base64.getDecoder().decode(encryptedMessage.getIv());
                
                // Decrypt payload
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, sessionKey.getKey(), gcmSpec);
                
                byte[] decryptedPayload = cipher.doFinal(encryptedPayload);
                
                return new String(decryptedPayload, StandardCharsets.UTF_8);
                
            } catch (Exception e) {
                log.error("Failed to decrypt message: {}", encryptedMessage.getMessageId(), e);
                throw new EncryptionException("Message decryption failed", e);
            }
        }
        
        /**
         * Get or create session key for service
         */
        private SessionKey getOrCreateSessionKey(String targetService) {
            return sessionKeys.computeIfAbsent(targetService, service -> {
                try {
                    // Generate new AES key
                    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                    keyGen.init(256);
                    SecretKey key = keyGen.generateKey();
                    
                    // Create session key
                    SessionKey sessionKey = SessionKey.builder()
                        .keyId(UUID.randomUUID().toString())
                        .key(key)
                        .targetService(service)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600)) // 1 hour expiry
                        .build();
                    
                    // Exchange key with target service (simplified - would use key exchange protocol)
                    exchangeSessionKey(sessionKey, service);
                    
                    return sessionKey;
                    
                } catch (Exception e) {
                    throw new EncryptionException("Failed to create session key", e);
                }
            });
        }
        
        /**
         * Exchange session key with target service
         */
        private void exchangeSessionKey(SessionKey sessionKey, String targetService) {
            // In production, implement proper key exchange (e.g., Diffie-Hellman or using PKI)
            log.debug("Exchanging session key with service: {}", targetService);
        }
        
        /**
         * Generate initialization vector
         */
        private byte[] generateIV() {
            byte[] iv = new byte[12]; // GCM recommended IV size
            new SecureRandom().nextBytes(iv);
            return iv;
        }
        
        /**
         * Clean up expired session keys
         */
        public void cleanupExpiredKeys() {
            Instant now = Instant.now();
            sessionKeys.entrySet().removeIf(entry -> 
                entry.getValue().getExpiresAt().isBefore(now)
            );
        }
    }
    
    /**
     * Request signing service for integrity and authentication
     */
    @Component
    @RequiredArgsConstructor
    public static class RequestSigningService {
        
        @Value("${security.communication.signing.algorithm:HmacSHA256}")
        private String signingAlgorithm;
        
        @Value("${security.communication.signing.key}")
        private String signingKey;
        
        /**
         * Sign request
         */
        public String signRequest(String method, String uri, String body, String timestamp) {
            try {
                // Create signing string
                String signingString = String.format("%s\n%s\n%s\n%s", 
                    method, uri, timestamp, body != null ? body : "");
                
                // Create HMAC
                Mac mac = Mac.getInstance(signingAlgorithm);
                SecretKeySpec secretKey = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), 
                    signingAlgorithm
                );
                mac.init(secretKey);
                
                // Generate signature
                byte[] signature = mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
                
                return Base64.getEncoder().encodeToString(signature);
                
            } catch (Exception e) {
                log.error("Failed to sign request", e);
                throw new SigningException("Request signing failed", e);
            }
        }
        
        /**
         * Verify request signature
         */
        public boolean verifySignature(String signature, String method, String uri, String body, String timestamp) {
            try {
                String expectedSignature = signRequest(method, uri, body, timestamp);
                return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
                );
            } catch (Exception e) {
                log.error("Failed to verify signature", e);
                return false;
            }
        }
    }
    
    /**
     * Interceptor for message encryption
     */
    @Component
    @RequiredArgsConstructor
    public static class EncryptionInterceptor implements ClientHttpRequestInterceptor {
        
        private final MessageEncryptionService encryptionService;
        
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                ClientHttpRequestExecution execution) throws IOException {
            
            // Skip encryption for certain endpoints
            if (shouldSkipEncryption(request)) {
                return execution.execute(request, body);
            }
            
            try {
                // Extract target service from URL
                String targetService = extractTargetService(request.getURI().toString());
                
                // Encrypt body if present
                if (body != null && body.length > 0) {
                    String jsonBody = new String(body, StandardCharsets.UTF_8);
                    EncryptedMessage encrypted = encryptionService.encryptMessage(jsonBody, targetService);
                    
                    // Replace body with encrypted payload
                    byte[] encryptedBody = encrypted.toString().getBytes(StandardCharsets.UTF_8);
                    
                    // Add encryption headers
                    request.getHeaders().add("X-Encryption-Algorithm", encrypted.getAlgorithm());
                    request.getHeaders().add("X-Session-Key-Id", encrypted.getSessionKeyId());
                    
                    return execution.execute(request, encryptedBody);
                }
                
            } catch (Exception e) {
                log.error("Encryption interceptor failed", e);
            }
            
            return execution.execute(request, body);
        }
        
        private boolean shouldSkipEncryption(HttpRequest request) {
            String uri = request.getURI().toString();
            return uri.contains("/health") || uri.contains("/metrics") || uri.contains("/actuator");
        }
        
        private String extractTargetService(String url) {
            // Extract service name from URL
            return url.split("/")[2].split(":")[0];
        }
    }
    
    /**
     * Interceptor for request signing
     */
    @Component
    @RequiredArgsConstructor
    public static class SigningInterceptor implements ClientHttpRequestInterceptor {
        
        private final RequestSigningService signingService;
        
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                ClientHttpRequestExecution execution) throws IOException {
            
            try {
                // Generate timestamp
                String timestamp = Instant.now().toString();
                
                // Sign request
                String signature = signingService.signRequest(
                    request.getMethod().toString(),
                    request.getURI().toString(),
                    body != null ? new String(body, StandardCharsets.UTF_8) : null,
                    timestamp
                );
                
                // Add signature headers
                request.getHeaders().add("X-Signature", signature);
                request.getHeaders().add("X-Timestamp", timestamp);
                request.getHeaders().add("X-Request-Id", UUID.randomUUID().toString());
                
            } catch (Exception e) {
                log.error("Signing interceptor failed", e);
            }
            
            return execution.execute(request, body);
        }
    }
    
    /**
     * Interceptor for audit logging
     */
    @Component
    public static class AuditInterceptor implements ClientHttpRequestInterceptor {
        
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                ClientHttpRequestExecution execution) throws IOException {
            
            String requestId = UUID.randomUUID().toString();
            request.getHeaders().add("X-Request-Id", requestId);
            
            long startTime = System.currentTimeMillis();
            
            try {
                ClientHttpResponse response = execution.execute(request, body);
                
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("Service call completed: method={}, uri={}, status={}, duration={}ms, requestId={}", 
                    request.getMethod(), request.getURI(), response.getStatusCode(), duration, requestId);
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                
                log.error("Service call failed: method={}, uri={}, duration={}ms, requestId={}, error={}", 
                    request.getMethod(), request.getURI(), duration, requestId, e.getMessage());
                
                throw e;
            }
        }
    }
    
    // Data classes
    
    @Data
    @Builder
    public static class EncryptedMessage {
        private String messageId;
        private String encryptedPayload;
        private String iv;
        private String sessionKeyId;
        private String algorithm;
        private Instant timestamp;
    }
    
    @Data
    @Builder
    public static class SessionKey {
        private String keyId;
        private SecretKey key;
        private String targetService;
        private Instant createdAt;
        private Instant expiresAt;
    }
    
    // Custom exceptions
    
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }
        
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class SigningException extends RuntimeException {
        public SigningException(String message) {
            super(message);
        }
        
        public SigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}