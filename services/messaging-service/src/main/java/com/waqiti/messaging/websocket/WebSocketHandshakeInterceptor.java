package com.waqiti.messaging.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        // Extract authentication token from query parameters or headers
        String token = extractToken(request);
        
        if (token != null) {
            attributes.put("token", token);
            return true;
        }
        
        log.warn("WebSocket connection attempt without authentication token");
        return false;
    }
    
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Log successful handshake
        if (exception == null) {
            log.info("WebSocket handshake completed successfully");
        } else {
            log.error("WebSocket handshake failed", exception);
        }
    }
    
    private String extractToken(ServerHttpRequest request) {
        // First try to get from query parameters
        String query = request.getURI().getQuery();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }
        
        // Then try to get from headers
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        return null;
    }
}