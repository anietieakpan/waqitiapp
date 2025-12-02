package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "services")
public class ExternalServicesProperties {

    private IntegrationService integrationService = new IntegrationService();
    private NotificationService notificationService = new NotificationService();

    @Data
    public static class IntegrationService {
        private String url = "http://integration-service:8085";
    }

    @Data
    public static class NotificationService {
        private String url = "http://notification-service:8084";
    }
}