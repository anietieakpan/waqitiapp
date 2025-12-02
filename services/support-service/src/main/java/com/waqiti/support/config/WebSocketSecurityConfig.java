package com.waqiti.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
                // Allow connection without authentication (auth handled in handshake)
                .simpTypeMatchers(org.springframework.messaging.simp.SimpMessageType.CONNECT,
                        org.springframework.messaging.simp.SimpMessageType.HEARTBEAT,
                        org.springframework.messaging.simp.SimpMessageType.UNSUBSCRIBE,
                        org.springframework.messaging.simp.SimpMessageType.DISCONNECT).permitAll()
                
                // User-specific destinations
                .simpDestMatchers("/user/queue/errors").permitAll()
                .simpDestMatchers("/user/**").authenticated()
                
                // Support agent destinations
                .simpDestMatchers("/topic/support/agents/**").hasRole("SUPPORT_AGENT")
                .simpDestMatchers("/queue/support/tickets/**").hasRole("SUPPORT_AGENT")
                
                // Customer destinations
                .simpDestMatchers("/topic/support/chat/**").authenticated()
                .simpDestMatchers("/queue/support/chat/**").authenticated()
                
                // Application destinations
                .simpDestMatchers("/app/support/chat/**").authenticated()
                .simpDestMatchers("/app/support/typing/**").authenticated()
                .simpDestMatchers("/app/support/ticket/**").authenticated()
                
                // Admin destinations
                .simpDestMatchers("/topic/support/admin/**").hasRole("ADMIN")
                
                // Deny everything else
                .anyMessage().denyAll();
    }

    @Override
    protected boolean sameOriginDisabled() {
        // Disable CSRF for WebSocket endpoints
        return true;
    }
}