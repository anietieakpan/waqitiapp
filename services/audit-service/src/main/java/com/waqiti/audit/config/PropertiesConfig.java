package com.waqiti.audit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AuditProperties.class,
    ServicesProperties.class,
    AwsProperties.class,
    AuthenticationProperties.class,
    FeaturesProperties.class,
    KeycloakProperties.class,
    SecurityProperties.class
})
public class PropertiesConfig {
    // This class enables all configuration properties
}