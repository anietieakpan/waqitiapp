package com.waqiti.websocket.handler;

import com.waqiti.websocket.security.SecureWebSocketAuthenticationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Legacy WebSocket authentication interceptor - replaced by SecureWebSocketAuthenticationHandler
 * This class is kept for backward compatibility and delegates to the secure handler
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "websocket.security.use-legacy-auth", havingValue = "false", matchIfMissing = true)
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final SecureWebSocketAuthenticationHandler secureHandler;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Delegate to the secure authentication handler
        return secureHandler.preSend(message, channel);
    }
}

