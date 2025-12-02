package com.waqiti.kyc.service.impl;

import com.waqiti.kyc.config.KYCProperties;
import com.waqiti.kyc.exception.KYCProviderException;
import com.waqiti.kyc.provider.KYCProvider;
import com.waqiti.kyc.provider.KYCProviderFactory;
import com.waqiti.kyc.service.KYCProviderService;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class KYCProviderServiceImpl implements KYCProviderService {

    private final KYCProviderFactory providerFactory;
    private final KYCProperties kycProperties;
    private final CircuitBreaker kycProviderCircuitBreaker;
    private final Retry kycProviderRetry;

    @Override
    public String createVerificationSession(String userId, KYCVerificationRequest request) {
        log.info("Creating verification session for user: {} with provider: {}", 
                userId, request.getPreferredProvider());
        
        String providerName = request.getPreferredProvider() != null ? 
                request.getPreferredProvider() : kycProperties.getProviders().getDefaultProvider();
        
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        Supplier<String> decoratedSupplier = CircuitBreaker
                .decorateSupplier(kycProviderCircuitBreaker, 
                    () -> provider.createVerificationSession(userId, request));
        
        decoratedSupplier = Retry.decorateSupplier(kycProviderRetry, decoratedSupplier);
        
        try {
            String sessionId = decoratedSupplier.get();
            log.info("Successfully created verification session: {} for user: {}", sessionId, userId);
            return sessionId;
        } catch (Exception e) {
            log.error("Failed to create verification session for user: {}", userId, e);
            throw new KYCProviderException("Failed to create verification session", providerName, e);
        }
    }

    @Override
    @Cacheable(value = "kyc-provider-status", key = "#sessionId", unless = "#result == null")
    public Map<String, Object> getVerificationStatus(String sessionId) {
        log.debug("Getting verification status for session: {}", sessionId);
        
        // Try each provider to find the session
        for (String providerName : providerFactory.getAvailableProviders()) {
            try {
                KYCProvider provider = providerFactory.getProvider(providerName);
                Map<String, Object> status = provider.getVerificationStatus(sessionId);
                
                if (status != null && !status.isEmpty()) {
                    status.put("provider", providerName);
                    return status;
                }
            } catch (Exception e) {
                log.debug("Provider {} doesn't have session {}: {}", 
                        providerName, sessionId, e.getMessage());
            }
        }
        
        log.warn("Session {} not found in any provider", sessionId);
        return Map.of("status", "NOT_FOUND", "sessionId", sessionId);
    }

    @Override
    public Map<String, Object> getVerificationResults(String sessionId) {
        log.info("Getting verification results for session: {}", sessionId);
        
        Map<String, Object> status = getVerificationStatus(sessionId);
        String providerName = (String) status.get("provider");
        
        if (providerName == null) {
            throw new KYCProviderException("Session not found: " + sessionId, "UNKNOWN");
        }
        
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        try {
            Map<String, Object> results = provider.getVerificationResults(sessionId);
            results.put("provider", providerName);
            return results;
        } catch (Exception e) {
            log.error("Failed to get verification results for session: {}", sessionId, e);
            throw new KYCProviderException("Failed to get verification results", providerName, e);
        }
    }

    @Override
    public void cancelVerificationSession(String sessionId) {
        log.info("Cancelling verification session: {}", sessionId);
        
        Map<String, Object> status = getVerificationStatus(sessionId);
        String providerName = (String) status.get("provider");
        
        if (providerName == null) {
            log.warn("Cannot cancel session {} - not found in any provider", sessionId);
            return;
        }
        
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        try {
            provider.cancelVerificationSession(sessionId);
            log.info("Successfully cancelled verification session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to cancel verification session: {}", sessionId, e);
            throw new KYCProviderException("Failed to cancel verification session", providerName, e);
        }
    }

    @Override
    public String uploadDocumentToProvider(String sessionId, byte[] documentData, String documentType) {
        log.info("Uploading document to provider for session: {}, type: {}", sessionId, documentType);
        
        Map<String, Object> status = getVerificationStatus(sessionId);
        String providerName = (String) status.get("provider");
        
        if (providerName == null) {
            throw new KYCProviderException("Session not found: " + sessionId, "UNKNOWN");
        }
        
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        try {
            String documentId = provider.uploadDocument(sessionId, documentData, documentType);
            log.info("Successfully uploaded document: {} for session: {}", documentId, sessionId);
            return documentId;
        } catch (Exception e) {
            log.error("Failed to upload document for session: {}", sessionId, e);
            throw new KYCProviderException("Failed to upload document", providerName, e);
        }
    }

    @Override
    public Map<String, String> extractDocumentDataFromProvider(String documentId) {
        log.info("Extracting document data for document: {}", documentId);
        
        // Try each provider to find the document
        for (String providerName : providerFactory.getAvailableProviders()) {
            try {
                KYCProvider provider = providerFactory.getProvider(providerName);
                Map<String, String> data = provider.extractDocumentData(documentId);
                
                if (data != null && !data.isEmpty()) {
                    data.put("provider", providerName);
                    return data;
                }
            } catch (Exception e) {
                log.debug("Provider {} doesn't have document {}: {}", 
                        providerName, documentId, e.getMessage());
            }
        }
        
        log.warn("Document {} not found in any provider", documentId);
        return Map.of();
    }

    @Override
    public void processWebhook(Map<String, Object> webhookData) {
        String providerName = (String) webhookData.get("provider");
        
        if (providerName == null) {
            log.error("No provider specified in webhook data");
            throw new IllegalArgumentException("Provider not specified in webhook data");
        }
        
        log.info("Processing webhook for provider: {}", providerName);
        
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        try {
            provider.processWebhook(webhookData);
            log.info("Successfully processed webhook for provider: {}", providerName);
        } catch (Exception e) {
            log.error("Failed to process webhook for provider: {}", providerName, e);
            throw new KYCProviderException("Failed to process webhook", providerName, e);
        }
    }

    @Override
    public boolean validateWebhookSignature(String signature, String payload) {
        // Each provider has different signature validation
        // Try to determine provider from payload
        String providerName = detectProviderFromPayload(payload);
        
        if (providerName == null) {
            log.error("Cannot determine provider from webhook payload");
            return false;
        }
        
        return validateProviderWebhookSignature(providerName, signature, payload);
    }

    @Override
    public boolean isProviderAvailable() {
        String defaultProvider = kycProperties.getProviders().getDefaultProvider();
        return isProviderAvailable(defaultProvider);
    }
    
    public boolean isProviderAvailable(String providerName) {
        try {
            KYCProvider provider = providerFactory.getProvider(providerName);
            return provider.isAvailable();
        } catch (Exception e) {
            log.error("Provider {} is not available: {}", providerName, e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return kycProperties.getProviders().getDefaultProvider();
    }

    @Override
    public Map<String, Object> getProviderCapabilities() {
        String providerName = getProviderName();
        KYCProvider provider = providerFactory.getProvider(providerName);
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("provider", providerName);
        capabilities.put("supportedDocuments", provider.getSupportedDocumentTypes());
        capabilities.put("supportedCountries", provider.getSupportedCountries());
        capabilities.put("features", provider.getFeatures());
        capabilities.put("isAvailable", provider.isAvailable());
        
        return capabilities;
    }

    @Override
    public void updateConfiguration(Map<String, String> config) {
        log.info("Updating provider configuration");
        
        // Update provider-specific configurations
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (key.startsWith("provider.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 3) {
                    String providerName = parts[1];
                    String configKey = parts[2];
                    updateProviderConfig(providerName, configKey, value);
                }
            }
        }
    }

    @Override
    public Map<String, String> getConfiguration() {
        Map<String, String> config = new HashMap<>();
        
        // Get configuration for all providers
        for (String providerName : providerFactory.getAvailableProviders()) {
            KYCProvider provider = providerFactory.getProvider(providerName);
            Map<String, String> providerConfig = provider.getConfiguration();
            
            for (Map.Entry<String, String> entry : providerConfig.entrySet()) {
                config.put("provider." + providerName + "." + entry.getKey(), entry.getValue());
            }
        }
        
        config.put("defaultProvider", kycProperties.getProviders().getDefaultProvider());
        
        return config;
    }
    
    private String detectProviderFromPayload(String payload) {
        // Simple detection based on payload content
        if (payload.contains("onfido")) {
            return "ONFIDO";
        } else if (payload.contains("jumio")) {
            return "JUMIO";
        } else if (payload.contains("complyadvantage")) {
            return "COMPLY_ADVANTAGE";
        }
        
        // Security risk: Unknown provider attempting webhook delivery
        log.error("SECURITY_ALERT: Unknown KYC provider attempting webhook delivery. Payload preview: {}", 
                  payload.length() > 100 ? payload.substring(0, 100) + "..." : payload);
        
        // Throw security exception instead of returning null to prevent unauthorized access
        throw new SecurityException("Unknown KYC provider detected. Webhook rejected for security reasons.");
    }
    
    private boolean validateProviderWebhookSignature(String providerName, String signature, String payload) {
        try {
            switch (providerName) {
                case "ONFIDO":
                    return validateOnfidoSignature(signature, payload);
                case "JUMIO":
                    return validateJumioSignature(signature, payload);
                case "COMPLY_ADVANTAGE":
                    return validateComplyAdvantageSignature(signature, payload);
                default:
                    log.warn("Unknown provider for signature validation: {}", providerName);
                    return false;
            }
        } catch (Exception e) {
            log.error("Error validating webhook signature for provider {}: {}", providerName, e.getMessage());
            return false;
        }
    }
    
    private boolean validateOnfidoSignature(String signature, String payload) {
        String webhookSecret = kycProperties.getProviders().getOnfido().getWebhookSecret();
        return validateHmacSignature(signature, payload, webhookSecret, "SHA256");
    }
    
    private boolean validateJumioSignature(String signature, String payload) {
        String webhookSecret = kycProperties.getProviders().getJumio().getWebhookSecret();
        return validateHmacSignature(signature, payload, webhookSecret, "SHA256");
    }
    
    private boolean validateComplyAdvantageSignature(String signature, String payload) {
        // ComplyAdvantage might use different validation
        return true; // Simplified for now
    }
    
    private boolean validateHmacSignature(String signature, String payload, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance("Hmac" + algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "Hmac" + algorithm);
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            
            return computedSignature.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error computing HMAC signature: {}", e.getMessage());
            return false;
        }
    }
    
    private void updateProviderConfig(String providerName, String key, String value) {
        try {
            KYCProvider provider = providerFactory.getProvider(providerName);
            provider.updateConfiguration(key, value);
            log.info("Updated configuration for provider {}: {} = {}", providerName, key, value);
        } catch (Exception e) {
            log.error("Failed to update configuration for provider {}: {}", providerName, e.getMessage());
        }
    }
}