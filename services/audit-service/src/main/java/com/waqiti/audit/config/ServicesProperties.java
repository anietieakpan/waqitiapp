package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "services")
public class ServicesProperties {
    
    private ServiceConfig securityService = new ServiceConfig();
    private ServiceConfig notificationService = new ServiceConfig();
    
    @Data
    public static class ServiceConfig {
        private String url;
        private String timeout = "30s";
    }
}