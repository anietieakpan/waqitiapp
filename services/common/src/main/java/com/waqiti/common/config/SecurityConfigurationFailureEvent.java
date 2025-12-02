package com.waqiti.common.config;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when security configuration validation fails.
 * This event indicates critical security issues that prevent safe application startup.
 */
public class SecurityConfigurationFailureEvent extends ApplicationEvent {
    
    private final String message;
    private final SecureConfigurationService.ConfigurationHealthStatus status;
    
    public SecurityConfigurationFailureEvent(Object source, String message, 
                                            SecureConfigurationService.ConfigurationHealthStatus status) {
        super(source);
        this.message = message;
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public SecureConfigurationService.ConfigurationHealthStatus getStatus() {
        return status;
    }
}