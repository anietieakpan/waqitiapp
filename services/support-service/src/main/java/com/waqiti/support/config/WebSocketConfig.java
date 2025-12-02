package com.waqiti.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.websocket.message-broker.relay-host:localhost}")
    private String relayHost;

    @Value("${spring.websocket.message-broker.relay-port:61613}")
    private int relayPort;

    @Value("${spring.websocket.message-broker.client-login:guest}")
    private String clientLogin;

    @Value("${spring.websocket.message-broker.client-passcode:guest}")
    private String clientPasscode;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry messages back to clients
        config.enableSimpleBroker("/topic", "/queue", "/user");
        
        // Set prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        
        // Set prefix for user destinations
        config.setUserDestinationPrefix("/user");
        
        // For production, use external message broker (RabbitMQ/ActiveMQ)
        /*
        config.enableStompBrokerRelay("/topic", "/queue", "/user")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(clientLogin)
                .setClientPasscode(clientPasscode)
                .setSystemLogin(clientLogin)
                .setSystemPasscode(clientPasscode);
        */
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoints
        registry.addEndpoint("/ws/support")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new CustomHandshakeHandler())
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
        
        // Register endpoint without SockJS for native WebSocket clients
        registry.addEndpoint("/ws/support-native")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new CustomHandshakeHandler())
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(65536) // 64KB
                .setSendTimeLimit(20 * 1000) // 20 seconds
                .setSendBufferSizeLimit(512 * 1024) // 512KB
                .setTimeToFirstMessage(30 * 1000); // 30 seconds
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extract authentication from headers
                    String authToken = accessor.getFirstNativeHeader("Authorization");
                    if (authToken != null && authToken.startsWith("Bearer ")) {
                        String jwt = authToken.substring(7);
                        // Validate JWT and set authentication
                        // This should use your JWT validation service
                        log.debug("WebSocket connection with JWT: {}", jwt.substring(0, 10) + "...");
                        
                        // Set user principal for the session
                        Principal principal = new Principal() {
                            @Override
                            public String getName() {
                                // Extract username from JWT
                                return "user-" + jwt.hashCode(); // Replace with actual JWT parsing
                            }
                        };
                        accessor.setUser(principal);
                    }
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    log.debug("WebSocket disconnection for user: {}", accessor.getUser());
                }
                
                return message;
            }
        });
        
        registration.taskExecutor().corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(100);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(8).maxPoolSize(16);
    }
    
    /**
     * Custom handshake handler to generate unique session IDs
     */
    private static class CustomHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Map<String, Object> attributes) {
            // Generate unique principal for each connection
            return new Principal() {
                private final String name = "SESS-" + System.currentTimeMillis() + "-" + 
                        secureRandom.nextLong();
                
                @Override
                public String getName() {
                    return name;
                }
            };
        }
    }
    
    /**
     * Interceptor to copy HTTP session attributes to WebSocket session
     */
    private static class HttpSessionHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Map<String, Object> attributes) throws Exception {
            
            // Add custom attributes
            attributes.put("clientIp", request.getRemoteAddress());
            attributes.put("connectionTime", System.currentTimeMillis());
            
            log.debug("WebSocket handshake initiated from: {}", request.getRemoteAddress());
            return true;
        }

        @Override
        public void afterHandshake(
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response,
                org.springframework.web.socket.WebSocketHandler wsHandler,
                Exception exception) {
            
            if (exception != null) {
                log.error("WebSocket handshake failed", exception);
            } else {
                log.debug("WebSocket handshake completed successfully");
            }
        }
    }
}