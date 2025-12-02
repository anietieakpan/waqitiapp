package com.waqiti.investment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time investment updates
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for broadcasting messages
        config.enableSimpleBroker("/topic", "/queue");
        // Set application destination prefix for sending messages to server
        config.setApplicationDestinationPrefixes("/app");
        // Set user destination prefix for sending messages to specific users
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint with SockJS fallback
        registry.addEndpoint("/ws-investment")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Register STOMP endpoint without SockJS for native WebSocket clients
        registry.addEndpoint("/ws-investment")
                .setAllowedOriginPatterns("*");
        
        log.info("WebSocket endpoints registered for investment service");
    }
}