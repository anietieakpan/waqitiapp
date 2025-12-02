package com.waqiti.websocket.config;

import com.waqiti.websocket.handler.AuthChannelInterceptor;
import com.waqiti.websocket.handler.WaqitiWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

/**
 * WebSocket configuration for real-time communication
 * Supports STOMP over WebSocket with Redis pub/sub for scaling
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Value("${websocket.allowed-origins}")
    private String[] allowedOrigins;
    
    @Value("${websocket.relay.host:localhost}")
    private String relayHost;
    
    @Value("${websocket.relay.port:61613}")
    private int relayPort;
    
    private final AuthChannelInterceptor authChannelInterceptor;
    private final WaqitiWebSocketHandler webSocketHandler;
    
    public WebSocketConfig(AuthChannelInterceptor authChannelInterceptor,
                          WaqitiWebSocketHandler webSocketHandler) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.webSocketHandler = webSocketHandler;
    }
    
    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        messages
            .nullDestMatcher().permitAll()
            .simpDestMatchers("/app/public/**").permitAll()
            .simpSubscribeDestMatchers("/user/queue/errors").permitAll()
            .simpSubscribeDestMatchers("/topic/public/**").permitAll()
            .simpSubscribeDestMatchers("/user/**", "/queue/**", "/topic/**").authenticated()
            .simpTypeMatchers(SimpMessageType.CONNECT).authenticated()
            .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
            .anyMessage().denyAll();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for topics and queues
        config.enableStompBrokerRelay("/topic", "/queue", "/exchange")
            .setRelayHost(relayHost)
            .setRelayPort(relayPort)
            .setClientLogin("guest")
            .setClientPasscode("guest")
            .setSystemLogin("guest")
            .setSystemPasscode("guest")
            .setSystemHeartbeatSendInterval(20000)
            .setSystemHeartbeatReceiveInterval(20000);
        
        // Configure application destination prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // Configure user destination prefix
        config.setUserDestinationPrefix("/user");
        
        // Enable heartbeats
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.setThreadNamePrefix("waqiti-websocket-heartbeat-");
        taskScheduler.initialize();
        config.setTaskScheduler(taskScheduler);
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(allowedOrigins)
            .setHandshakeHandler(new DefaultHandshakeHandler())
            .addInterceptors(webSocketHandler)
            .withSockJS()
            .setSessionCookieNeeded(false)
            .setStreamBytesLimit(512 * 1024)
            .setHttpMessageCacheSize(1000)
            .setDisconnectDelay(30 * 1000);
        
        // Add raw WebSocket endpoint for native mobile apps
        registry.addEndpoint("/ws-native")
            .setAllowedOrigins(allowedOrigins)
            .setHandshakeHandler(new DefaultHandshakeHandler())
            .addInterceptors(webSocketHandler);
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration
            .interceptors(authChannelInterceptor)
            .taskExecutor()
            .corePoolSize(8)
            .maxPoolSize(16)
            .queueCapacity(1000)
            .keepAliveSeconds(60);
    }
    
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration
            .taskExecutor()
            .corePoolSize(8)
            .maxPoolSize(16)
            .queueCapacity(1000);
    }
    
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new MappingJackson2MessageConverter());
        return true;
    }
    
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(8192);
        container.setMaxSessionIdleTimeout(60000L);
        return container;
    }
    
    @Override
    protected boolean sameOriginDisabled() {
        return true;
    }
}