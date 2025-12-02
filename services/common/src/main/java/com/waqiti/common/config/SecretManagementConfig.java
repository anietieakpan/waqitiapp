package com.waqiti.common.config;

import com.waqiti.common.security.secrets.SecretManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for secret management
 * Integrates SecretManager with Spring's property resolution
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "secrets.enabled", havingValue = "true", matchIfMissing = true)
public class SecretManagementConfig {

    private final ConfigurableEnvironment environment;
    private final SecretManager secretManager;
    
    public SecretManagementConfig(ConfigurableEnvironment environment,
                                 SecretManager secretManager) {
        this.environment = environment;
        this.secretManager = secretManager;
    }

    /**
     * Initialize secret property source
     */
    @PostConstruct
    public void initializeSecretPropertySource() {
        // Create a custom property source that delegates to SecretManager
        PropertySource<SecretManager> secretPropertySource = new PropertySource<SecretManager>("secrets", secretManager) {
            @Override
            public Object getProperty(String name) {
                if (name.startsWith("secret.")) {
                    try {
                        String secretKey = name.substring(7); // Remove "secret." prefix
                        return source.getSecret(secretKey);
                    } catch (Exception e) {
                        log.debug("Secret not found for property: {}", name);
                        return null;
                    }
                }
                return null;
            }
        };

        // Add to the beginning of property sources (highest priority)
        environment.getPropertySources().addFirst(secretPropertySource);
        
        log.info("Secret property source initialized");
    }

    /**
     * Database configuration using secrets
     */
    @Bean
    @ConditionalOnProperty(name = "secrets.database.enabled", havingValue = "true", matchIfMissing = true)
    public Map<String, String> databaseConfig() {
        Map<String, String> config = new HashMap<>();
        
        try {
            config.put("url", secretManager.getDatabaseUrl());
            config.put("username", secretManager.getDatabaseUsername());
            config.put("password", secretManager.getDatabasePassword());
            
            log.info("Database configuration loaded from secrets");
        } catch (Exception e) {
            log.error("Failed to load database configuration from secrets", e);
            // Fall back to properties
            config.put("url", environment.getProperty("spring.datasource.url"));
            config.put("username", environment.getProperty("spring.datasource.username"));
            config.put("password", environment.getProperty("spring.datasource.password"));
        }
        
        return config;
    }

    /**
     * Redis configuration using secrets
     */
    @Bean
    @ConditionalOnProperty(name = "secrets.redis.enabled", havingValue = "true", matchIfMissing = true)
    public Map<String, String> redisConfig() {
        Map<String, String> config = new HashMap<>();
        
        try {
            config.put("url", secretManager.getRedisUrl());
            config.put("password", secretManager.getRedisPassword());
            
            log.info("Redis configuration loaded from secrets");
        } catch (Exception e) {
            log.error("Failed to load Redis configuration from secrets", e);
            // Fall back to properties
            config.put("url", environment.getProperty("spring.data.redis.url"));
            config.put("password", environment.getProperty("spring.data.redis.password"));
        }
        
        return config;
    }

    /**
     * Payment provider configuration using secrets
     */
    @Bean
    public Map<String, Map<String, String>> paymentProviderConfig() {
        Map<String, Map<String, String>> providers = new HashMap<>();
        
        // Stripe configuration
        Map<String, String> stripe = new HashMap<>();
        try {
            stripe.put("apiKey", secretManager.getStripeApiKey());
            stripe.put("webhookSecret", secretManager.getSecret("stripe/webhook-secret"));
        } catch (Exception e) {
            log.error("Failed to load Stripe configuration from secrets", e);
        }
        providers.put("stripe", stripe);
        
        // PayPal configuration
        Map<String, String> paypal = new HashMap<>();
        try {
            paypal.put("clientId", secretManager.getPayPalClientId());
            paypal.put("clientSecret", secretManager.getPayPalClientSecret());
        } catch (Exception e) {
            log.error("Failed to load PayPal configuration from secrets", e);
        }
        providers.put("paypal", paypal);
        
        return providers;
    }

    /**
     * Security configuration using secrets
     */
    @Bean
    public Map<String, String> securityConfig() {
        Map<String, String> config = new HashMap<>();
        
        try {
            config.put("jwtSigningKey", secretManager.getJwtSigningKey());
            config.put("encryptionKey", secretManager.getEncryptionMasterKey());
            
            log.info("Security configuration loaded from secrets");
        } catch (Exception e) {
            log.error("Failed to load security configuration from secrets", e);
            throw new RuntimeException("Critical security configuration missing", e);
        }
        
        return config;
    }
}