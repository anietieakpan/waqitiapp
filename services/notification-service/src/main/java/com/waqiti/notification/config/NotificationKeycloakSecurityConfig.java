package com.waqiti.notification.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Notification Service
 * Manages authentication and authorization for notification operations
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class NotificationKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain notificationKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "notification-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/notifications/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Notification Sending (Service-to-Service only)
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send-batch").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send-async").hasRole("SERVICE")
                
                // User Notification Management
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications").hasAuthority("SCOPE_notification:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/*").hasAuthority("SCOPE_notification:read")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/unread").hasAuthority("SCOPE_notification:read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/*/read").hasAuthority("SCOPE_notification:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/*").hasAuthority("SCOPE_notification:delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/mark-all-read").hasAuthority("SCOPE_notification:update")
                
                // Notification Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/preferences").hasAuthority("SCOPE_notification:preferences-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/preferences").hasAuthority("SCOPE_notification:preferences-write")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/preferences/channels").hasAuthority("SCOPE_notification:preferences-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/preferences/channels/*").hasAuthority("SCOPE_notification:preferences-write")
                
                // Push Notification Token Management
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/devices/register").hasAuthority("SCOPE_notification:device-register")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/devices/*").hasAuthority("SCOPE_notification:device-unregister")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/devices").hasAuthority("SCOPE_notification:device-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/devices/*/update").hasAuthority("SCOPE_notification:device-update")
                
                // Email Templates (Admin only)
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/templates").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/templates/*").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/templates").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/notifications/templates/*").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/templates/*").hasRole("NOTIFICATION_ADMIN")
                
                // SMS Configuration
                .requestMatchers("/api/v1/notifications/sms/config/**").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers("/api/v1/notifications/sms/balance").hasRole("NOTIFICATION_ADMIN")
                
                // Push Notification Topics
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/topics/subscribe").hasAuthority("SCOPE_notification:topic-subscribe")
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications/topics/unsubscribe").hasAuthority("SCOPE_notification:topic-unsubscribe")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/topics/subscriptions").hasAuthority("SCOPE_notification:topic-read")
                .requestMatchers("/api/v1/notifications/topics/broadcast").hasRole("NOTIFICATION_ADMIN")
                
                // Two-Factor Authentication Notifications
                .requestMatchers("/api/v1/notifications/2fa/**").hasRole("SERVICE")
                
                // Notification History and Analytics
                .requestMatchers("/api/v1/notifications/history/**").hasAuthority("SCOPE_notification:history")
                .requestMatchers("/api/v1/notifications/analytics/**").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers("/api/v1/notifications/metrics/**").hasRole("NOTIFICATION_ADMIN")
                
                // Webhook Management
                .requestMatchers("/api/v1/notifications/webhooks/**").hasRole("NOTIFICATION_ADMIN")
                
                // Notification Campaigns (Marketing)
                .requestMatchers("/api/v1/notifications/campaigns/**").hasAnyRole("MARKETING", "NOTIFICATION_ADMIN")
                
                // Admin Operations
                .requestMatchers("/api/v1/notifications/admin/**").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers("/api/v1/notifications/config/**").hasRole("NOTIFICATION_ADMIN")
                .requestMatchers("/api/v1/notifications/providers/**").hasRole("NOTIFICATION_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/notifications/**").hasRole("SERVICE")
                .requestMatchers("/internal/send/**").hasRole("SERVICE")
                .requestMatchers("/internal/batch/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}