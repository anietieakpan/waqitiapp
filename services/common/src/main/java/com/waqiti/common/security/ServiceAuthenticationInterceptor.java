package com.waqiti.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Service-to-service authentication interceptor for Feign clients
 * Implements HMAC-SHA256 based authentication for secure inter-service communication
 */
@Component
public class ServiceAuthenticationInterceptor implements RequestInterceptor {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SERVICE_ID_HEADER = "X-Service-ID";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Signature";

    @Value("${service.auth.secret:#{null}}")
    private String serviceSecret;

    @Value("${spring.application.name}")
    private String serviceName;

    @Override
    public void apply(RequestTemplate template) {
        if (serviceSecret == null || serviceSecret.isEmpty()) {
            throw new IllegalStateException("Service authentication secret not configured");
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = generateSignature(serviceName, timestamp, template.path());

        template.header(SERVICE_ID_HEADER, serviceName);
        template.header(TIMESTAMP_HEADER, timestamp);
        template.header(SIGNATURE_HEADER, signature);
    }

    private String generateSignature(String serviceId, String timestamp, String path) {
        try {
            String payload = serviceId + ":" + timestamp + ":" + path;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                serviceSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate service signature", e);
        }
    }
}