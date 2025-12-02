package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "service.auth")
public class ServiceAuthProperties {

    private boolean enabled = true;
    private String clientId = "user-service";
    private String clientSecret;
}