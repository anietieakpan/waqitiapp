package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "features")
public class FeaturesProperties {
    
    private AuthFeatures auth = new AuthFeatures();
    
    @Data
    public static class AuthFeatures {
        private String mode = "KEYCLOAK";
        private KeycloakFeature keycloak = new KeycloakFeature();
        private LegacyFeature legacy = new LegacyFeature();
    }
    
    @Data
    public static class KeycloakFeature {
        private Boolean enabled = true;
    }
    
    @Data
    public static class LegacyFeature {
        private Boolean enabled = false;
    }
}