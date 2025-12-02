package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "audit.security")
public class SecurityProperties {
    
    private JwtProperties jwt = new JwtProperties();
    
    @Data
    public static class JwtProperties {
        private String secret;
        private Long expiration = 86400000L;
    }
}