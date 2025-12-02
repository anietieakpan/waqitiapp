package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Service authentication configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "service.auth")
public class ServiceAuthProperties {
    
    private boolean enabled = true;
    private String tokenValidationUrl;
    private String apiKey;
    private String clientId;
    private String clientSecret;
    private JwtConfig jwt = new JwtConfig();
    
    @Data
    public static class JwtConfig {
        private String secret;
        private long expiration = 3600000; // 1 hour in milliseconds
    }
}