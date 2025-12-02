package com.waqiti.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class PushNotificationConfig {
    // Firebase is initialized in PushNotificationServiceImpl @PostConstruct
    // This config enables async processing for push notifications
}