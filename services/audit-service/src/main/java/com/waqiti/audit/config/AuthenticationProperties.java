package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "authentication")
public class AuthenticationProperties {
    
    private String mode = "KEYCLOAK";
    private Boolean legacyJwtEnabled = false;
    private Boolean keycloakEnabled = true;
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    
    @Data
    public static class CircuitBreakerProperties {
        private Integer failureThreshold = 5;
        private Long timeoutDuration = 10000L;
        private Integer halfOpenRequests = 3;
    }
}