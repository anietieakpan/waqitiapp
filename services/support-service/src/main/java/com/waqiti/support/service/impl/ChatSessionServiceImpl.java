package com.waqiti.support.service.impl;

import com.waqiti.support.domain.ChatSession;
import com.waqiti.support.dto.ChatMessage;
import com.waqiti.support.repository.ChatSessionRepository;
import com.waqiti.support.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatSessionServiceImpl implements ChatSessionService {
    
    private final ChatSessionRepository chatSessionRepository;
    
    // In-memory cache for active sessions (in production, use Redis)
    private final ConcurrentHashMap<String, ChatSession> activeSessionCache = new ConcurrentHashMap<>();
    
    // Session timeout in minutes
    private static final int SESSION_TIMEOUT_MINUTES = 30;
    
    @Override
    public ChatSession getOrCreateSession(String sessionId, String userId) {
        if (sessionId == null) {
            sessionId = generateSessionId();
        }
        
        // Check cache first
        ChatSession session = activeSessionCache.get(sessionId);
        if (session != null && session.isActive()) {
            return session;
        }
        
        // Check database
        Optional<ChatSession> dbSession = chatSessionRepository.findBySessionIdAndStatus(sessionId, ChatSession.Status.ACTIVE);
        if (dbSession.isPresent()) {
            activeSessionCache.put(sessionId, dbSession.get());
            return dbSession.get();
        }
        
        // Create new session
        session = ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .startTime(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .status(ChatSession.Status.ACTIVE)
                .build();
        
        chatSessionRepository.save(session);
        activeSessionCache.put(sessionId, session);
        
        log.info("Created new chat session: {} for user: {}", sessionId, userId);
        return session;
    }
    
    @Override
    public Optional<ChatSession> getSession(String sessionId) {
        // Check cache first
        ChatSession session = activeSessionCache.get(sessionId);
        if (session != null) {
            return Optional.of(session);
        }
        
        // Check database
        Optional<ChatSession> dbSession = chatSessionRepository.findById(sessionId);
        if (dbSession.isPresent()) {
            activeSessionCache.put(sessionId, dbSession.get());
        }
        
        return dbSession;
    }
    
    @Override
    public void addMessage(String sessionId, ChatMessage message) {
        ChatSession session = activeSessionCache.get(sessionId);
        if (session == null) {
            session = getSession(sessionId).orElse(null);
        }
        
        if (session != null) {
            session.addMessage(message);
            session.setLastActivity(LocalDateTime.now());
            chatSessionRepository.save(session);
            activeSessionCache.put(sessionId, session);
        }
    }
    
    @Override
    public List<ChatMessage> getSessionMessages(String sessionId) {
        Optional<ChatSession> session = getSession(sessionId);
        if (session.isPresent()) {
            return session.get().getMessages();
        }
        return List.of();
    }
    
    @Override
    public List<ChatMessage> getRecentMessages(String sessionId, int count) {
        Optional<ChatSession> session = getSession(sessionId);
        if (session.isPresent()) {
            return session.get().getRecentMessages(count);
        }
        return List.of();
    }
    
    @Override
    public void endSession(String sessionId) {
        Optional<ChatSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            ChatSession session = sessionOpt.get();
            session.setStatus(ChatSession.Status.ENDED);
            session.setEndTime(LocalDateTime.now());
            
            chatSessionRepository.save(session);
            activeSessionCache.remove(sessionId);
            
            log.info("Ended chat session: {}", sessionId);
        }
    }
    
    @Override
    public void transferToAgent(String sessionId, String agentId) {
        Optional<ChatSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            ChatSession session = sessionOpt.get();
            session.setStatus(ChatSession.Status.TRANSFERRED);
            session.setAgentId(agentId);
            
            // Add system message about transfer
            ChatMessage transferMessage = ChatMessage.system(
                "Chat has been transferred to human agent: " + agentId
            );
            session.addMessage(transferMessage);
            
            chatSessionRepository.save(session);
            activeSessionCache.put(sessionId, session);
            
            log.info("Transferred chat session: {} to agent: {}", sessionId, agentId);
        }
    }
    
    @Override
    public void updateSessionMetadata(String sessionId, String key, Object value) {
        Optional<ChatSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isPresent()) {
            ChatSession session = sessionOpt.get();
            session.getMetadata().put(key, value.toString());
            chatSessionRepository.save(session);
            activeSessionCache.put(sessionId, session);
        }
    }
    
    @Override
    public boolean isSessionActive(String sessionId) {
        Optional<ChatSession> session = getSession(sessionId);
        return session.isPresent() && session.get().isActive();
    }
    
    @Override
    public List<ChatSession> getActiveSessionsForUser(String userId) {
        return chatSessionRepository.findByUserIdAndStatus(userId, ChatSession.Status.ACTIVE);
    }
    
    @Override
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupExpiredSessions() {
        log.debug("Cleaning up expired chat sessions");
        
        // Clean up from cache
        activeSessionCache.entrySet().removeIf(entry -> {
            ChatSession session = entry.getValue();
            return session.isExpired(SESSION_TIMEOUT_MINUTES);
        });
        
        // Clean up from database
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        List<ChatSession> expiredSessions = chatSessionRepository.findExpiredSessions(cutoffTime);
        
        for (ChatSession session : expiredSessions) {
            session.setStatus(ChatSession.Status.EXPIRED);
            session.setEndTime(LocalDateTime.now());
            chatSessionRepository.save(session);
        }
        
        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired chat sessions", expiredSessions.size());
        }
    }
    
    private String generateSessionId() {
        return "chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}