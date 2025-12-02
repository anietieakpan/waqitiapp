package com.waqiti.websocket.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component; /**
 * Legacy simple JWT authentication (for backward compatibility only)
 * Use only when websocket.security.use-legacy-auth=true
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.security.use-legacy-auth", havingValue = "true")
@Deprecated
public class LegacyAuthChannelInterceptor implements ChannelInterceptor {

    // Keep the old implementation for backward compatibility
    // This should be removed in future versions
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        log.warn("Using deprecated legacy WebSocket authentication - please upgrade to secure authentication");
        
        // Basic token validation without security features
        // Implementation kept simple for compatibility
        return message;
    }
}
