package com.waqiti.websocket.handler;

import com.waqiti.websocket.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaqitiWebSocketHandler implements HandshakeInterceptor {

    private final PresenceService presenceService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        
        log.info("WebSocket handshake from IP: {} with User-Agent: {}", clientIp, userAgent);
        
        // Store connection metadata
        attributes.put("clientIp", clientIp);
        attributes.put("userAgent", userAgent);
        attributes.put("connectTime", System.currentTimeMillis());
        
        // Extract device info from headers
        String deviceId = request.getHeaders().getFirst("X-Device-Id");
        String platform = request.getHeaders().getFirst("X-Platform");
        
        if (deviceId != null) {
            attributes.put("deviceId", deviceId);
        }
        if (platform != null) {
            attributes.put("platform", platform);
        }
        
        return true; // Allow handshake
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        
        if (exception != null) {
            log.error("WebSocket handshake failed", exception);
        } else {
            log.debug("WebSocket handshake completed successfully");
        }
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}