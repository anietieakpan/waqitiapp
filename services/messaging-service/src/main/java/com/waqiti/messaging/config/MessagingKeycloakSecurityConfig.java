package com.waqiti.messaging.config;

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
 * Keycloak security configuration for Messaging Service
 * Manages authentication and authorization for encrypted messaging features
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class MessagingKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain messagingKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "messaging-service", httpSecurity -> {
            httpSecurity
                // Disable CSRF for messaging endpoints
                .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/api/v1/messaging/ws/**", "/ws/**")
                )
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/messaging/public/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    
                    // WebSocket endpoints for real-time messaging
                    .requestMatchers("/ws/**", "/wss/**").authenticated()
                    .requestMatchers("/api/v1/messaging/ws/**").authenticated()
                    
                    // Conversation Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/conversations").hasAuthority("SCOPE_messaging:conversation-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/conversations").hasAuthority("SCOPE_messaging:conversation-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/conversations/*").hasAuthority("SCOPE_messaging:conversation-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/messaging/conversations/*").hasAuthority("SCOPE_messaging:conversation-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/messaging/conversations/*").hasAuthority("SCOPE_messaging:conversation-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/conversations/*/archive").hasAuthority("SCOPE_messaging:conversation-archive")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/conversations/*/mute").hasAuthority("SCOPE_messaging:conversation-mute")
                    
                    // Message Operations
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/messages/send").hasAuthority("SCOPE_messaging:message-send")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/messages/*").hasAuthority("SCOPE_messaging:message-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/messaging/messages/*/edit").hasAuthority("SCOPE_messaging:message-edit")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/messaging/messages/*").hasAuthority("SCOPE_messaging:message-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/messages/*/react").hasAuthority("SCOPE_messaging:message-react")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/messages/*/read").hasAuthority("SCOPE_messaging:message-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/messages/*/reply").hasAuthority("SCOPE_messaging:message-reply")
                    
                    // Group Chat Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/groups/create").hasAuthority("SCOPE_messaging:group-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/groups").hasAuthority("SCOPE_messaging:group-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/messaging/groups/*").hasAuthority("SCOPE_messaging:group-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/messaging/groups/*").hasAuthority("SCOPE_messaging:group-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/groups/*/members/add").hasAuthority("SCOPE_messaging:group-member-add")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/groups/*/members/remove").hasAuthority("SCOPE_messaging:group-member-remove")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/groups/*/leave").hasAuthority("SCOPE_messaging:group-leave")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/groups/*/admins/add").hasAuthority("SCOPE_messaging:group-admin-manage")
                    
                    // Media and Attachments
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/media/upload").hasAuthority("SCOPE_messaging:media-upload")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/media/*").hasAuthority("SCOPE_messaging:media-read")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/messaging/media/*").hasAuthority("SCOPE_messaging:media-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/attachments/upload").hasAuthority("SCOPE_messaging:attachment-upload")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/attachments/*").hasAuthority("SCOPE_messaging:attachment-read")
                    
                    // Voice and Video Calls
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/calls/initiate").hasAuthority("SCOPE_messaging:call-initiate")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/calls/*/answer").hasAuthority("SCOPE_messaging:call-answer")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/calls/*/end").hasAuthority("SCOPE_messaging:call-end")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/calls/history").hasAuthority("SCOPE_messaging:call-history")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/calls/*/record").hasAuthority("SCOPE_messaging:call-record")
                    
                    // Encryption Key Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/keys/exchange").hasAuthority("SCOPE_messaging:key-exchange")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/keys/public/*").hasAuthority("SCOPE_messaging:key-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/keys/rotate").hasAuthority("SCOPE_messaging:key-rotate")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/keys/backup").hasAuthority("SCOPE_messaging:key-backup")
                    
                    // Status and Presence
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/status/update").hasAuthority("SCOPE_messaging:status-update")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/status/*").hasAuthority("SCOPE_messaging:status-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/presence/update").hasAuthority("SCOPE_messaging:presence-update")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/presence/*").hasAuthority("SCOPE_messaging:presence-read")
                    
                    // Notifications
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/notifications").hasAuthority("SCOPE_messaging:notification-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/notifications/*/mark-read").hasAuthority("SCOPE_messaging:notification-update")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/messaging/notifications/settings").hasAuthority("SCOPE_messaging:notification-settings")
                    
                    // Search
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/search").hasAuthority("SCOPE_messaging:search")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/search/messages").hasAuthority("SCOPE_messaging:search-messages")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/search/conversations").hasAuthority("SCOPE_messaging:search-conversations")
                    
                    // Payment Integration
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/payments/send").hasAuthority("SCOPE_messaging:payment-send")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/payments/request").hasAuthority("SCOPE_messaging:payment-request")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/payments/history").hasAuthority("SCOPE_messaging:payment-history")
                    
                    // Moderation and Reporting
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/report").hasAuthority("SCOPE_messaging:report")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messaging/block/*").hasAuthority("SCOPE_messaging:block-user")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/messaging/block/*").hasAuthority("SCOPE_messaging:unblock-user")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messaging/blocked").hasAuthority("SCOPE_messaging:blocked-list")
                    
                    // Admin Operations
                    .requestMatchers("/api/v1/messaging/admin/**").hasRole("MESSAGING_ADMIN")
                    .requestMatchers("/api/v1/messaging/moderation/**").hasRole("MESSAGING_MODERATOR")
                    .requestMatchers("/api/v1/messaging/analytics/**").hasRole("MESSAGING_ADMIN")
                    .requestMatchers("/api/v1/messaging/export/**").hasRole("MESSAGING_ADMIN")
                    
                    // Internal service-to-service endpoints
                    .requestMatchers("/internal/messaging/**").hasRole("SERVICE")
                    .requestMatchers("/internal/notifications/**").hasRole("SERVICE")
                    .requestMatchers("/internal/encryption/**").hasRole("SERVICE")
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
        });
    }
}