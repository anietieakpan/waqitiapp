package com.waqiti.kyc.provider;

import com.waqiti.kyc.exception.KYCProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for KYC providers with common functionality
 */
@Slf4j
public abstract class AbstractKYCProvider implements KYCProvider {
    
    protected WebClient webClient;
    protected Map<String, String> configuration = new ConcurrentHashMap<>();
    protected boolean initialized = false;
    protected String providerName;
    
    @Override
    public void initialize(Map<String, String> config) {
        this.configuration.putAll(config);
        this.providerName = getClass().getSimpleName().replace("KYCProvider", "").toUpperCase();
        
        // Build WebClient with provider-specific configuration
        String apiUrl = config.get("apiUrl");
        if (apiUrl == null) {
            throw new KYCProviderException("API URL not configured for provider: " + providerName, providerName);
        }
        
        int timeoutSeconds = Integer.parseInt(config.getOrDefault("timeoutSeconds", "30"));
        
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeaders(headers -> configureHeaders(headers, config))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        
        // Provider-specific initialization
        doInitialize(config);
        
        this.initialized = true;
        log.info("Initialized {} provider with URL: {}", providerName, apiUrl);
    }
    
    /**
     * Configure default headers for the provider
     * @param headers The header consumer
     * @param config The provider configuration
     */
    protected abstract void configureHeaders(org.springframework.http.HttpHeaders headers, Map<String, String> config);
    
    /**
     * Provider-specific initialization logic
     * @param config The provider configuration
     */
    protected abstract void doInitialize(Map<String, String> config);
    
    @Override
    public boolean isAvailable() {
        if (!initialized) {
            return false;
        }
        
        try {
            // Default implementation - providers can override
            return performHealthCheck();
        } catch (Exception e) {
            log.warn("Health check failed for provider {}: {}", providerName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Perform a health check on the provider
     * @return true if the provider is healthy
     */
    protected boolean performHealthCheck() {
        // Default implementation - just check if initialized
        // Providers should override with actual health check
        return initialized;
    }
    
    @Override
    public Map<String, String> getConfiguration() {
        Map<String, String> config = new HashMap<>(configuration);
        // Remove sensitive data
        config.remove("apiKey");
        config.remove("apiSecret");
        config.remove("webhookSecret");
        return config;
    }
    
    @Override
    public void updateConfiguration(String key, String value) {
        configuration.put(key, value);
        log.info("Updated configuration for provider {}: {} = {}", providerName, key, 
                key.contains("secret") || key.contains("key") ? "***" : value);
    }
    
    @Override
    public String getProviderName() {
        return providerName;
    }
    
    @Override
    public void shutdown() {
        log.info("Shutting down {} provider", providerName);
        initialized = false;
    }
    
    /**
     * Make a GET request to the provider API
     * @param path The API path
     * @param responseType The expected response type
     * @param <T> The response type
     * @return The response object
     */
    protected <T> T get(String path, Class<T> responseType) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(Duration.ofSeconds(getTimeoutSeconds()));
        } catch (Exception e) {
            throw new KYCProviderException("GET request failed: " + path, providerName, e);
        }
    }
    
    /**
     * Make a POST request to the provider API
     * @param path The API path
     * @param body The request body
     * @param responseType The expected response type
     * @param <T> The response type
     * @return The response object
     */
    protected <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return webClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(Duration.ofSeconds(getTimeoutSeconds()));
        } catch (Exception e) {
            throw new KYCProviderException("POST request failed: " + path, providerName, e);
        }
    }
    
    /**
     * Make a PUT request to the provider API
     * @param path The API path
     * @param body The request body
     * @param responseType The expected response type
     * @param <T> The response type
     * @return The response object
     */
    protected <T> T put(String path, Object body, Class<T> responseType) {
        try {
            return webClient.put()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(Duration.ofSeconds(getTimeoutSeconds()));
        } catch (Exception e) {
            throw new KYCProviderException("PUT request failed: " + path, providerName, e);
        }
    }
    
    /**
     * Make a DELETE request to the provider API
     * @param path The API path
     */
    protected void delete(String path) {
        try {
            webClient.delete()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(getTimeoutSeconds()));
        } catch (Exception e) {
            throw new KYCProviderException("DELETE request failed: " + path, providerName, e);
        }
    }
    
    /**
     * Get the configured timeout in seconds
     * @return The timeout value
     */
    protected int getTimeoutSeconds() {
        return Integer.parseInt(configuration.getOrDefault("timeoutSeconds", "30"));
    }
    
    /**
     * Validate required configuration parameters
     * @param requiredKeys The required configuration keys
     */
    protected void validateConfiguration(String... requiredKeys) {
        for (String key : requiredKeys) {
            if (!configuration.containsKey(key) || configuration.get(key).isEmpty()) {
                throw new KYCProviderException("Missing required configuration: " + key, providerName);
            }
        }
    }
}