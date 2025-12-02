package com.waqiti.notification.websocket;

import com.waqiti.notification.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final JwtTokenProvider tokenProvider;
    private final WebSocketSessionManager sessionManager;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for client subscriptions
        config.enableSimpleBroker("/topic", "/queue");
        
        // Set application destination prefix for client messages
        config.setApplicationDestinationPrefixes("/app");
        
        // Enable user-specific messaging
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new CustomHandshakeInterceptor())
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
                
        // Also add raw WebSocket endpoint for native clients
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new CustomHandshakeInterceptor());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extract JWT token from headers
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        String token = authorization.get(0).replace("Bearer ", "");
                        
                        try {
                            if (tokenProvider.validateToken(token)) {
                                String userId = tokenProvider.getUserIdFromToken(token);
                                Principal principal = new WebSocketPrincipal(userId);
                                accessor.setUser(principal);
                                
                                // Store session info
                                sessionManager.addSession(userId, accessor.getSessionId());
                            }
                        } catch (Exception e) {
                            throw new WebSocketAuthenticationException("Invalid token");
                        }
                    }
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    // Handle disconnection
                    if (accessor.getUser() != null) {
                        sessionManager.removeSession(
                                accessor.getUser().getName(), 
                                accessor.getSessionId()
                        );
                    }
                }
                
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new OutboundChannelInterceptor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(128 * 1024) // 128KB
                .setSendBufferSizeLimit(512 * 1024) // 512KB
                .setSendTimeLimit(20 * 1000) // 20 seconds
                .setTimeToFirstMessage(30 * 1000); // 30 seconds
    }

    private static class CustomHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Map<String, Object> attributes) throws Exception {
            
            // Extract session attributes from HTTP request
            String sessionId = request.getHeaders().getFirst("X-Session-Id");
            if (sessionId != null) {
                attributes.put("sessionId", sessionId);
            }
            
            // Extract client info
            String userAgent = request.getHeaders().getFirst("User-Agent");
            String clientIp = request.getRemoteAddress() != null ? 
                    request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
            
            attributes.put("userAgent", userAgent);
            attributes.put("clientIp", clientIp);
            attributes.put("connectionTime", System.currentTimeMillis());
            
            return true;
        }

        @Override
        public void afterHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Exception exception) {
            // Log connection result
        }
    }

    private class OutboundChannelInterceptor implements ChannelInterceptor {
        @Override
        public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            
            if (accessor.getCommand() != null) {
                // Track message delivery statistics
                switch (accessor.getCommand()) {
                    case MESSAGE:
                        if (sent) {
                            // Log successful message delivery
                            String destination = accessor.getDestination();
                            String userId = accessor.getUser() != null ? 
                                    accessor.getUser().getName() : "anonymous";
                            
                            metricsService.recordMessageDelivery(userId, destination, sent);
                        }
                        break;
                    case ERROR:
                        // Log error
                        String errorMessage = accessor.getMessage();
                        log.error("WebSocket error: {}", errorMessage);
                        break;
                }
            }
        }
    }

    public static class WebSocketPrincipal implements Principal {
        private final String name;

        public WebSocketPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class WebSocketAuthenticationException extends RuntimeException {
        public WebSocketAuthenticationException(String message) {
            super(message);
        }
    }
}