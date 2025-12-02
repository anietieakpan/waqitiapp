package com.waqiti.messaging.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.messaging.dto.MessageEvent;
import com.waqiti.messaging.dto.TypingIndicator;
import com.waqiti.messaging.dto.WebSocketMessage;
import com.waqiti.messaging.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {
    
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final AuthenticationService authenticationService;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }
        
        sessionToUser.put(session.getId(), userId);
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        
        log.info("WebSocket connection established for user: {}", userId);
        
        // Send connection acknowledgment
        sendToSession(session, WebSocketMessage.builder()
            .type("CONNECTION_ACK")
            .payload(Map.of("status", "connected", "userId", userId))
            .build());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = sessionToUser.get(session.getId());
        if (userId == null) {
            return;
        }
        
        try {
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
            
            switch (wsMessage.getType()) {
                case "TYPING":
                    handleTypingIndicator(userId, wsMessage);
                    break;
                    
                case "READ_RECEIPT":
                    handleReadReceipt(userId, wsMessage);
                    break;
                    
                case "PRESENCE_UPDATE":
                    handlePresenceUpdate(userId, wsMessage);
                    break;
                    
                case "PING":
                    sendToSession(session, WebSocketMessage.builder()
                        .type("PONG")
                        .payload(Map.of("timestamp", System.currentTimeMillis()))
                        .build());
                    break;
                    
                default:
                    log.warn("Unknown message type: {}", wsMessage.getType());
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendError(session, "Invalid message format");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = sessionToUser.remove(session.getId());
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            
            log.info("WebSocket connection closed for user: {}", userId);
            
            // Notify contacts about offline status
            broadcastPresenceUpdate(userId, false);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }
    
    public void sendToUser(String userId, Object message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            WebSocketMessage wsMessage = WebSocketMessage.builder()
                .type("MESSAGE")
                .payload(message)
                .timestamp(System.currentTimeMillis())
                .build();
            
            String payload = toJson(wsMessage);
            
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session", e);
                }
            });
        }
    }
    
    public void sendReadReceipt(String senderId, String messageId, String readByUserId) {
        sendToUser(senderId, Map.of(
            "type", "READ_RECEIPT",
            "messageId", messageId,
            "readBy", readByUserId,
            "readAt", System.currentTimeMillis()
        ));
    }
    
    public void broadcastTypingIndicator(String conversationId, String userId, boolean isTyping) {
        TypingIndicator indicator = TypingIndicator.builder()
            .conversationId(conversationId)
            .userId(userId)
            .isTyping(isTyping)
            .timestamp(System.currentTimeMillis())
            .build();
        
        // Send to all participants in the conversation
        // Implementation depends on your conversation service
    }
    
    private void handleTypingIndicator(String userId, WebSocketMessage message) {
        Map<String, Object> payload = (Map<String, Object>) message.getPayload();
        String conversationId = (String) payload.get("conversationId");
        boolean isTyping = (boolean) payload.get("isTyping");
        
        broadcastTypingIndicator(conversationId, userId, isTyping);
    }
    
    private void handleReadReceipt(String userId, WebSocketMessage message) {
        Map<String, Object> payload = (Map<String, Object>) message.getPayload();
        String messageId = (String) payload.get("messageId");
        
        // Process read receipt
        // Implementation depends on your message service
    }
    
    private void handlePresenceUpdate(String userId, WebSocketMessage message) {
        Map<String, Object> payload = (Map<String, Object>) message.getPayload();
        boolean isOnline = (boolean) payload.get("isOnline");
        
        broadcastPresenceUpdate(userId, isOnline);
    }
    
    private void broadcastPresenceUpdate(String userId, boolean isOnline) {
        // Broadcast to user's contacts
        // Implementation depends on your contact service
    }
    
    private String extractUserId(WebSocketSession session) {
        // Extract user ID from session attributes or query parameters
        // This should be set during the handshake interceptor
        Map<String, Object> attributes = session.getAttributes();
        String token = (String) attributes.get("token");
        
        if (token != null) {
            return authenticationService.validateTokenAndGetUserId(token);
        }
        
        return null;
    }
    
    private void sendToSession(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(toJson(message)));
            }
        } catch (IOException e) {
            log.error("Failed to send message to session", e);
        }
    }
    
    private void sendError(WebSocketSession session, String error) {
        sendToSession(session, WebSocketMessage.builder()
            .type("ERROR")
            .payload(Map.of("error", error))
            .timestamp(System.currentTimeMillis())
            .build());
    }
    
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
    
    public boolean isUserOnline(String userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty() && 
               sessions.stream().anyMatch(WebSocketSession::isOpen);
    }
    
    public int getActiveConnectionsCount() {
        return userSessions.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
}