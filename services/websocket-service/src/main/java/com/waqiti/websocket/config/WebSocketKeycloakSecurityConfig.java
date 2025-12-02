package com.waqiti.websocket.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for WebSocket Service
 * Manages authentication and authorization for real-time communication
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@EnableWebSocketSecurity
@Order(1)
public class WebSocketKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain webSocketKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "websocket-service", httpSecurity -> {
            httpSecurity
                // Disable CSRF for WebSocket endpoints
                .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/ws/**", "/wss/**", "/stomp/**")
                )
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/websocket/public/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    
                    // WebSocket handshake endpoints
                    .requestMatchers("/ws/**").authenticated()
                    .requestMatchers("/wss/**").authenticated()
                    .requestMatchers("/stomp/**").authenticated()
                    .requestMatchers("/websocket/**").authenticated()
                    
                    // Socket.IO endpoints (if used)
                    .requestMatchers("/socket.io/**").authenticated()
                    
                    // Connection Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/websocket/connections").hasAuthority("SCOPE_websocket:connections-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/websocket/connections/active").hasAuthority("SCOPE_websocket:connections-read")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/websocket/connections/*").hasAuthority("SCOPE_websocket:connections-manage")
                    
                    // Channel Subscription Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/subscribe").hasAuthority("SCOPE_websocket:subscribe")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/unsubscribe").hasAuthority("SCOPE_websocket:unsubscribe")
                    .requestMatchers(HttpMethod.GET, "/api/v1/websocket/subscriptions").hasAuthority("SCOPE_websocket:subscriptions-read")
                    
                    // Message Broadcasting (Service-to-Service only)
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/broadcast").hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/broadcast/user").hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/broadcast/topic").hasRole("SERVICE")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/broadcast/room").hasRole("SERVICE")
                    
                    // Presence Management
                    .requestMatchers("/api/v1/websocket/presence/**").hasAuthority("SCOPE_websocket:presence")
                    .requestMatchers("/api/v1/websocket/status/**").hasAuthority("SCOPE_websocket:status")
                    
                    // Room Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/rooms/create").hasAuthority("SCOPE_websocket:room-create")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/rooms/*/join").hasAuthority("SCOPE_websocket:room-join")
                    .requestMatchers(HttpMethod.POST, "/api/v1/websocket/rooms/*/leave").hasAuthority("SCOPE_websocket:room-leave")
                    .requestMatchers(HttpMethod.GET, "/api/v1/websocket/rooms").hasAuthority("SCOPE_websocket:room-read")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/websocket/rooms/*").hasAuthority("SCOPE_websocket:room-delete")
                    
                    // Event Streaming
                    .requestMatchers("/api/v1/websocket/events/stream").hasAuthority("SCOPE_websocket:events-stream")
                    .requestMatchers("/api/v1/websocket/events/history").hasAuthority("SCOPE_websocket:events-history")
                    
                    // Metrics and Monitoring
                    .requestMatchers("/api/v1/websocket/metrics/**").hasRole("WEBSOCKET_ADMIN")
                    .requestMatchers("/api/v1/websocket/stats/**").hasRole("WEBSOCKET_ADMIN")
                    
                    // Admin Operations
                    .requestMatchers("/api/v1/websocket/admin/**").hasRole("WEBSOCKET_ADMIN")
                    .requestMatchers("/api/v1/websocket/config/**").hasRole("WEBSOCKET_ADMIN")
                    .requestMatchers("/api/v1/websocket/debug/**").hasRole("WEBSOCKET_ADMIN")
                    
                    // Internal service-to-service endpoints
                    .requestMatchers("/internal/websocket/**").hasRole("SERVICE")
                    .requestMatchers("/internal/broadcast/**").hasRole("SERVICE")
                    .requestMatchers("/internal/events/**").hasRole("SERVICE")
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
        });
    }
    
    /**
     * WebSocket Message Broker Configuration
     */
    @Configuration
    @EnableWebSocketMessageBroker
    public static class WebSocketBrokerConfig implements WebSocketMessageBrokerConfigurer {
        
        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            // Enable simple broker for topics and queues
            config.enableSimpleBroker("/topic", "/queue", "/user");
            // Set application destination prefix
            config.setApplicationDestinationPrefixes("/app");
            // Set user destination prefix
            config.setUserDestinationPrefix("/user");
        }
        
        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            // Register STOMP endpoints
            registry.addEndpoint("/ws", "/wss", "/stomp")
                .setAllowedOriginPatterns(
                    "http://localhost:*",
                    "https://localhost:*",
                    "https://*.waqiti.com",
                    "capacitor://localhost",
                    "ionic://localhost"
                )
                .withSockJS()
                .setSessionCookieNeeded(false);
        }
    }
}