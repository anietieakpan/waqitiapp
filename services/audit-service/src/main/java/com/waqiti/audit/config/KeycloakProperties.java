package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    
    private Boolean enabled = false;
    private String authServerUrl;
    private String realm = "waqiti-fintech";
    private String resource = "audit-service";
    private Boolean bearerOnly = true;
    private String sslRequired = "external";
    private Boolean verifyTokenAudience = true;
    private Boolean useResourceRoleMappings = true;
    private String principalAttribute = "preferred_username";
    private Credentials credentials = new Credentials();
    private Boolean publicClient = false;
    
    @Data
    public static class Credentials {
        private String secret;
    }
}