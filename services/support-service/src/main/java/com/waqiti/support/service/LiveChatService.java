package com.waqiti.support.service;

import com.waqiti.support.domain.*;
import com.waqiti.support.dto.*;
import com.waqiti.support.repository.*;
import com.waqiti.support.websocket.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LiveChatService {
    
    private final LiveChatSessionRepository sessionRepository;
    private final LiveChatMessageRepository messageRepository;
    private final AgentAvailabilityService agentAvailabilityService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final AIAssistantService aiAssistantService;
    private final UserServiceClient userServiceClient;
    
    // In-memory tracking of active sessions
    private final Map<String, LiveChatSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, String> userToSessionMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> agentActiveSessions = new ConcurrentHashMap<>();
    
    public LiveChatSessionDTO startChatSession(StartChatRequest request) {
        log.info("Starting live chat session for user: {}", request.getUserId());
        
        // Check if user already has an active session
        if (userToSessionMap.containsKey(request.getUserId())) {
            String existingSessionId = userToSessionMap.get(request.getUserId());
            LiveChatSession existingSession = activeSessions.get(existingSessionId);
            if (existingSession != null && existingSession.getStatus() != ChatSessionStatus.ENDED) {
                return mapToSessionDTO(existingSession);
            }
        }
        
        // Get user details
        UserDTO user = userServiceClient.getUser(request.getUserId())
            .orElseThrow(() -> new BusinessException("User not found"));
        
        // Create new session
        LiveChatSession session = LiveChatSession.builder()
            .userId(request.getUserId())
            .userName(user.getName())
            .userEmail(user.getEmail())
            .topic(request.getTopic())
            .category(request.getCategory())
            .status(ChatSessionStatus.WAITING_FOR_AGENT)
            .channel(ChatChannel.WEB)
            .metadata(request.getMetadata())
            .isVip(user.isVip())
            .languageCode(request.getLanguageCode())
            .build();
        
        // Try to find available agent
        Optional<AgentDTO> availableAgent = agentAvailabilityService.findAvailableAgent(
            request.getCategory(), 
            request.getLanguageCode(),
            user.isVip()
        );
        
        if (availableAgent.isPresent()) {
            assignAgentToSession(session, availableAgent.get());
        } else {
            // Add to queue
            session.setQueuePosition(agentAvailabilityService.addToQueue(session));
            session.setEstimatedWaitTime(agentAvailabilityService.estimateWaitTime(request.getCategory()));
            
            // Start bot conversation if enabled
            if (shouldStartBotConversation(request)) {
                startBotConversation(session);
            }
        }
        
        session = sessionRepository.save(session);
        
        // Track in memory
        activeSessions.put(session.getId(), session);
        userToSessionMap.put(request.getUserId(), session.getId());
        
        // Send initial system message
        addSystemMessage(session, getWelcomeMessage(session));
        
        // Notify user about session status
        notifySessionUpdate(session);
        
        return mapToSessionDTO(session);
    }
    
    public ChatMessageDTO sendMessage(String sessionId, SendChatMessageRequest request) {
        LiveChatSession session = getActiveSession(sessionId);
        
        // Validate sender
        if (!canSendMessage(session, request.getSenderId(), request.getSenderType())) {
            throw new BusinessException("Sender not authorized for this session");
        }
        
        // Create message
        LiveChatMessage message = LiveChatMessage.builder()
            .session(session)
            .senderId(request.getSenderId())
            .senderName(request.getSenderName())
            .senderType(request.getSenderType())
            .content(request.getContent())
            .metadata(request.getMetadata())
            .build();
        
        // Handle special message types
        if (request.getMessageType() != null) {
            message.setMessageType(request.getMessageType());
            handleSpecialMessageType(session, message);
        }
        
        // Analyze sentiment
        if (message.getSenderType() == ChatSenderType.USER) {
            message.setSentimentScore(aiAssistantService.analyzeSentiment(request.getContent()));
        }
        
        message = messageRepository.save(message);
        session.getMessages().add(message);
        session.setLastMessageAt(LocalDateTime.now());
        
        // Update typing status
        updateTypingStatus(session, request.getSenderId(), false);
        
        // Handle bot responses
        if (session.getStatus() == ChatSessionStatus.BOT_CONVERSATION && 
            message.getSenderType() == ChatSenderType.USER) {
            handleBotResponse(session, message);
        }
        
        // Broadcast message
        broadcastMessage(session, message);
        
        // Update session activity
        updateSessionActivity(session);
        
        return mapToMessageDTO(message);
    }
    
    public void updateTypingStatus(String sessionId, String userId, boolean isTyping) {
        LiveChatSession session = getActiveSession(sessionId);
        
        TypingStatusUpdate update = TypingStatusUpdate.builder()
            .sessionId(sessionId)
            .userId(userId)
            .userName(getUserName(userId))
            .isTyping(isTyping)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Broadcast typing status
        messagingTemplate.convertAndSend(
            "/topic/chat/" + sessionId + "/typing",
            update
        );
    }
    
    public LiveChatSessionDTO transferSession(String sessionId, TransferSessionRequest request) {
        LiveChatSession session = getActiveSession(sessionId);
        
        if (session.getStatus() != ChatSessionStatus.ACTIVE) {
            throw new BusinessException("Can only transfer active sessions");
        }
        
        // Get new agent
        AgentDTO newAgent = userServiceClient.getAgent(request.getNewAgentId())
            .orElseThrow(() -> new BusinessException("Agent not found"));
        
        // Check agent availability
        if (!agentAvailabilityService.isAgentAvailable(request.getNewAgentId())) {
            throw new BusinessException("Target agent is not available");
        }
        
        // Remove from current agent
        if (session.getAgentId() != null) {
            agentActiveSessions.computeIfPresent(session.getAgentId(), 
                (k, sessions) -> {
                    sessions.remove(sessionId);
                    return sessions.isEmpty() ? null : sessions;
                });
        }
        
        // Assign to new agent
        String oldAgentId = session.getAgentId();
        assignAgentToSession(session, newAgent);
        
        // Add transfer message
        String transferMessage = String.format(
            "Chat transferred from %s to %s. Reason: %s",
            session.getAgentName(),
            newAgent.getName(),
            request.getReason()
        );
        addSystemMessage(session, transferMessage);
        
        // Update session
        session.setTransferCount(session.getTransferCount() + 1);
        session.setLastTransferAt(LocalDateTime.now());
        session.setLastTransferReason(request.getReason());
        sessionRepository.save(session);
        
        // Notify all parties
        notificationService.notifyChatTransferred(session, oldAgentId, newAgent.getId());
        broadcastSessionUpdate(session);
        
        return mapToSessionDTO(session);
    }
    
    public LiveChatSessionDTO endSession(String sessionId, EndSessionRequest request) {
        LiveChatSession session = getActiveSession(sessionId);
        
        // Add closing message
        if (request.getClosingMessage() != null) {
            addSystemMessage(session, request.getClosingMessage());
        }
        
        // Update session status
        session.setStatus(ChatSessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        session.setEndedBy(request.getEndedBy());
        session.setEndReason(request.getReason());
        
        // Calculate session metrics
        if (session.getStartedAt() != null) {
            long durationMinutes = java.time.Duration.between(
                session.getStartedAt(), session.getEndedAt()
            ).toMinutes();
            session.setDurationMinutes((int) durationMinutes);
        }
        
        session = sessionRepository.save(session);
        
        // Clean up in-memory tracking
        activeSessions.remove(sessionId);
        userToSessionMap.remove(session.getUserId());
        if (session.getAgentId() != null) {
            agentActiveSessions.computeIfPresent(session.getAgentId(), 
                (k, sessions) -> {
                    sessions.remove(sessionId);
                    return sessions.isEmpty() ? null : sessions;
                });
        }
        
        // Update agent availability
        if (session.getAgentId() != null) {
            agentAvailabilityService.updateAgentLoad(session.getAgentId(), -1);
        }
        
        // Request feedback
        requestChatFeedback(session);
        
        // Broadcast session end
        broadcastSessionEnd(session);
        
        // Convert to ticket if needed
        if (request.isConvertToTicket()) {
            convertChatToTicket(session);
        }
        
        return mapToSessionDTO(session);
    }
    
    public Page<LiveChatSessionDTO> getAgentSessions(String agentId, Pageable pageable) {
        return sessionRepository.findByAgentIdAndStatus(
            agentId, 
            ChatSessionStatus.ACTIVE, 
            pageable
        ).map(this::mapToSessionDTO);
    }
    
    public Page<LiveChatSessionDTO> getUserChatHistory(String userId, Pageable pageable) {
        return sessionRepository.findByUserId(userId, pageable)
            .map(this::mapToSessionDTO);
    }
    
    public List<ChatMessageDTO> getSessionMessages(String sessionId, int limit) {
        LiveChatSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        
        List<LiveChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtDesc(
            sessionId, 
            Pageable.ofSize(limit)
        );
        
        Collections.reverse(messages); // Return in chronological order
        
        return messages.stream()
            .map(this::mapToMessageDTO)
            .collect(Collectors.toList());
    }
    
    public ChatStatisticsDTO getChatStatistics(LocalDateTime since) {
        List<LiveChatSession> sessions = sessionRepository.findByCreatedAtAfter(since);
        
        return ChatStatisticsDTO.builder()
            .totalSessions(sessions.size())
            .activeSessions(activeSessions.size())
            .averageWaitTime(calculateAverageWaitTime(sessions))
            .averageSessionDuration(calculateAverageSessionDuration(sessions))
            .averageSatisfactionScore(calculateAverageSatisfaction(sessions))
            .sessionsByCategory(groupSessionsByCategory(sessions))
            .peakHours(calculatePeakHours(sessions))
            .agentUtilization(calculateAgentUtilization())
            .botDeflectionRate(calculateBotDeflectionRate(sessions))
            .build();
    }
    
    public void submitChatFeedback(String sessionId, ChatFeedbackRequest request) {
        LiveChatSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        
        session.setSatisfactionRating(request.getRating());
        session.setFeedbackComment(request.getComment());
        session.setWouldRecommend(request.getWouldRecommend());
        
        sessionRepository.save(session);
        
        // Handle negative feedback
        if (request.getRating() < 3) {
            handleNegativeFeedback(session, request);
        }
    }
    
    // Helper methods
    
    private LiveChatSession getActiveSession(String sessionId) {
        LiveChatSession session = activeSessions.get(sessionId);
        if (session == null) {
            session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
            
            if (session.getStatus() == ChatSessionStatus.ENDED) {
                throw new BusinessException("Session has ended");
            }
        }
        return session;
    }
    
    private void assignAgentToSession(LiveChatSession session, AgentDTO agent) {
        session.setAgentId(agent.getId());
        session.setAgentName(agent.getName());
        session.setStatus(ChatSessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        session.setQueuePosition(null);
        
        // Track agent sessions
        agentActiveSessions.computeIfAbsent(agent.getId(), k -> new HashSet<>())
            .add(session.getId());
        
        // Update agent availability
        agentAvailabilityService.updateAgentLoad(agent.getId(), 1);
    }
    
    private void startBotConversation(LiveChatSession session) {
        session.setStatus(ChatSessionStatus.BOT_CONVERSATION);
        session.setBotSessionId(UUID.randomUUID().toString());
        
        // Get bot greeting based on category
        String botGreeting = aiAssistantService.getBotGreeting(
            session.getCategory(),
            session.getLanguageCode()
        );
        
        addBotMessage(session, botGreeting);
    }
    
    private void handleBotResponse(LiveChatSession session, LiveChatMessage userMessage) {
        // Get bot response
        BotResponse botResponse = aiAssistantService.generateBotResponse(
            session,
            userMessage.getContent()
        );
        
        // Add bot message
        addBotMessage(session, botResponse.getMessage());
        
        // Check if human agent needed
        if (botResponse.isEscalateToHuman()) {
            escalateToHumanAgent(session, botResponse.getEscalationReason());
        }
        
        // Handle bot actions
        if (botResponse.getActions() != null) {
            processBotActions(session, botResponse.getActions());
        }
    }
    
    private void escalateToHumanAgent(LiveChatSession session, String reason) {
        session.setStatus(ChatSessionStatus.WAITING_FOR_AGENT);
        session.setBotEscalationReason(reason);
        
        addSystemMessage(session, "Connecting you with a human agent...");
        
        // Try to find available agent
        Optional<AgentDTO> agent = agentAvailabilityService.findAvailableAgent(
            session.getCategory(),
            session.getLanguageCode(),
            session.isVip()
        );
        
        if (agent.isPresent()) {
            assignAgentToSession(session, agent.get());
            addSystemMessage(session, "You are now connected with " + agent.get().getName());
        } else {
            session.setQueuePosition(agentAvailabilityService.addToQueue(session));
            addSystemMessage(session, "All agents are busy. You are #" + 
                session.getQueuePosition() + " in queue.");
        }
        
        sessionRepository.save(session);
        broadcastSessionUpdate(session);
    }
    
    private void addSystemMessage(LiveChatSession session, String content) {
        LiveChatMessage message = LiveChatMessage.builder()
            .session(session)
            .senderId("SYSTEM")
            .senderName("System")
            .senderType(ChatSenderType.SYSTEM)
            .content(content)
            .messageType(ChatMessageType.SYSTEM)
            .build();
        
        messageRepository.save(message);
        session.getMessages().add(message);
        
        broadcastMessage(session, message);
    }
    
    private void addBotMessage(LiveChatSession session, String content) {
        LiveChatMessage message = LiveChatMessage.builder()
            .session(session)
            .senderId("BOT")
            .senderName("Waqiti Assistant")
            .senderType(ChatSenderType.BOT)
            .content(content)
            .messageType(ChatMessageType.BOT_RESPONSE)
            .build();
        
        messageRepository.save(message);
        session.getMessages().add(message);
        
        broadcastMessage(session, message);
    }
    
    private void broadcastMessage(LiveChatSession session, LiveChatMessage message) {
        ChatMessageDTO messageDTO = mapToMessageDTO(message);
        
        // Send to user
        messagingTemplate.convertAndSendToUser(
            session.getUserId(),
            "/queue/chat/messages",
            messageDTO
        );
        
        // Send to agent if assigned
        if (session.getAgentId() != null) {
            messagingTemplate.convertAndSendToUser(
                session.getAgentId(),
                "/queue/chat/messages",
                messageDTO
            );
        }
        
        // Send to topic for monitoring
        messagingTemplate.convertAndSend(
            "/topic/chat/" + session.getId() + "/messages",
            messageDTO
        );
    }
    
    private void broadcastSessionUpdate(LiveChatSession session) {
        LiveChatSessionDTO sessionDTO = mapToSessionDTO(session);
        
        messagingTemplate.convertAndSend(
            "/topic/chat/" + session.getId() + "/status",
            sessionDTO
        );
    }
    
    private void broadcastSessionEnd(LiveChatSession session) {
        SessionEndNotification notification = SessionEndNotification.builder()
            .sessionId(session.getId())
            .endedAt(session.getEndedAt())
            .reason(session.getEndReason())
            .build();
        
        messagingTemplate.convertAndSend(
            "/topic/chat/" + session.getId() + "/end",
            notification
        );
    }
    
    private boolean canSendMessage(LiveChatSession session, String senderId, ChatSenderType senderType) {
        return switch (senderType) {
            case USER -> session.getUserId().equals(senderId);
            case AGENT -> session.getAgentId() != null && session.getAgentId().equals(senderId);
            case BOT, SYSTEM -> true;
        };
    }
    
    private boolean shouldStartBotConversation(StartChatRequest request) {
        // Start bot for non-urgent categories when no agents available
        return request.getCategory() != ChatCategory.URGENT &&
               request.getCategory() != ChatCategory.SECURITY &&
               !request.isPreferHuman();
    }
    
    private String getWelcomeMessage(LiveChatSession session) {
        if (session.getStatus() == ChatSessionStatus.ACTIVE) {
            return String.format("Welcome! You are now connected with %s. How can I help you today?", 
                session.getAgentName());
        } else if (session.getStatus() == ChatSessionStatus.WAITING_FOR_AGENT) {
            return String.format("Welcome! All our agents are currently busy. You are #%d in queue. " +
                "Estimated wait time: %d minutes.", 
                session.getQueuePosition(), 
                session.getEstimatedWaitTime());
        } else if (session.getStatus() == ChatSessionStatus.BOT_CONVERSATION) {
            return "Welcome! I'm Waqiti Assistant. I'm here to help you with your questions. " +
                   "If you need a human agent, just let me know!";
        }
        return "Welcome to Waqiti Support!";
    }
    
    private void requestChatFeedback(LiveChatSession session) {
        // Send feedback request notification
        notificationService.requestChatFeedback(session);
    }
    
    private void convertChatToTicket(LiveChatSession session) {
        CreateTicketRequest ticketRequest = CreateTicketRequest.builder()
            .userId(session.getUserId())
            .subject("Chat Session: " + session.getTopic())
            .description(generateChatTranscript(session))
            .category(mapChatCategoryToTicketCategory(session.getCategory()))
            .channel(SupportChannel.CHAT)
            .relatedChatSessionId(session.getId())
            .build();
        
        // Create ticket via ticket service
        // This would be injected and called
        log.info("Converting chat session {} to ticket", session.getId());
    }
    
    private String generateChatTranscript(LiveChatSession session) {
        StringBuilder transcript = new StringBuilder();
        transcript.append("Chat Session Transcript\n");
        transcript.append("========================\n");
        transcript.append("Session ID: ").append(session.getId()).append("\n");
        transcript.append("User: ").append(session.getUserName()).append("\n");
        transcript.append("Agent: ").append(session.getAgentName()).append("\n");
        transcript.append("Duration: ").append(session.getDurationMinutes()).append(" minutes\n");
        transcript.append("\nMessages:\n");
        transcript.append("---------\n");
        
        for (LiveChatMessage message : session.getMessages()) {
            transcript.append(String.format("[%s] %s: %s\n",
                message.getCreatedAt(),
                message.getSenderName(),
                message.getContent()
            ));
        }
        
        return transcript.toString();
    }
    
    private void updateSessionActivity(LiveChatSession session) {
        session.setLastActivityAt(LocalDateTime.now());
        // Don't save immediately to avoid too many DB writes
        // This will be saved on next significant update
    }
    
    private void notifySessionUpdate(LiveChatSession session) {
        // Implementation would send real-time updates
        broadcastSessionUpdate(session);
    }
    
    private void handleSpecialMessageType(LiveChatSession session, LiveChatMessage message) {
        switch (message.getMessageType()) {
            case FILE_SHARE:
                // Handle file sharing
                break;
            case EMOJI_REACTION:
                // Handle emoji reactions
                break;
            case QUICK_REPLY:
                // Handle quick replies
                break;
        }
    }
    
    private void processBotActions(LiveChatSession session, List<BotAction> actions) {
        for (BotAction action : actions) {
            switch (action.getType()) {
                case "SHOW_ARTICLE":
                    // Show knowledge base article
                    break;
                case "COLLECT_INFO":
                    // Collect additional information
                    break;
                case "CREATE_TICKET":
                    // Create support ticket
                    break;
            }
        }
    }
    
    private void handleNegativeFeedback(LiveChatSession session, ChatFeedbackRequest feedback) {
        // Escalate to supervisor
        log.warn("Negative feedback received for session {}: {}", 
            session.getId(), feedback.getComment());
        
        // Could create a follow-up ticket or alert
    }
    
    private String getUserName(String userId) {
        return userServiceClient.getUser(userId)
            .map(UserDTO::getName)
            .orElse("Unknown");
    }
    
    private TicketCategory mapChatCategoryToTicketCategory(ChatCategory chatCategory) {
        return switch (chatCategory) {
            case GENERAL -> TicketCategory.OTHER;
            case PAYMENT -> TicketCategory.PAYMENT;
            case ACCOUNT -> TicketCategory.ACCOUNT;
            case TECHNICAL -> TicketCategory.TECHNICAL;
            case SECURITY -> TicketCategory.SECURITY;
            default -> TicketCategory.OTHER;
        };
    }
    
    // Calculation methods for statistics
    
    private double calculateAverageWaitTime(List<LiveChatSession> sessions) {
        return sessions.stream()
            .filter(s -> s.getWaitTimeMinutes() != null)
            .mapToInt(LiveChatSession::getWaitTimeMinutes)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageSessionDuration(List<LiveChatSession> sessions) {
        return sessions.stream()
            .filter(s -> s.getDurationMinutes() != null)
            .mapToInt(LiveChatSession::getDurationMinutes)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageSatisfaction(List<LiveChatSession> sessions) {
        return sessions.stream()
            .filter(s -> s.getSatisfactionRating() != null)
            .mapToInt(LiveChatSession::getSatisfactionRating)
            .average()
            .orElse(0.0);
    }
    
    private Map<ChatCategory, Long> groupSessionsByCategory(List<LiveChatSession> sessions) {
        return sessions.stream()
            .collect(Collectors.groupingBy(
                LiveChatSession::getCategory,
                Collectors.counting()
            ));
    }
    
    private Map<Integer, Long> calculatePeakHours(List<LiveChatSession> sessions) {
        return sessions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getCreatedAt().getHour(),
                Collectors.counting()
            ));
    }
    
    private Map<String, Double> calculateAgentUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : agentActiveSessions.entrySet()) {
            String agentId = entry.getKey();
            int activeSessions = entry.getValue().size();
            int maxCapacity = agentAvailabilityService.getAgentMaxCapacity(agentId);
            
            double utilizationRate = maxCapacity > 0 ? 
                (double) activeSessions / maxCapacity * 100 : 0;
            
            utilization.put(agentId, utilizationRate);
        }
        
        return utilization;
    }
    
    private double calculateBotDeflectionRate(List<LiveChatSession> sessions) {
        long botOnlySessions = sessions.stream()
            .filter(s -> s.getStatus() == ChatSessionStatus.BOT_CONVERSATION &&
                        s.getAgentId() == null)
            .count();
        
        long totalSessions = sessions.size();
        
        return totalSessions > 0 ? (double) botOnlySessions / totalSessions * 100 : 0;
    }
    
    // DTO mapping methods
    
    private LiveChatSessionDTO mapToSessionDTO(LiveChatSession session) {
        return LiveChatSessionDTO.builder()
            .id(session.getId())
            .userId(session.getUserId())
            .userName(session.getUserName())
            .userEmail(session.getUserEmail())
            .agentId(session.getAgentId())
            .agentName(session.getAgentName())
            .topic(session.getTopic())
            .category(session.getCategory())
            .status(session.getStatus())
            .queuePosition(session.getQueuePosition())
            .estimatedWaitTime(session.getEstimatedWaitTime())
            .messageCount(session.getMessages().size())
            .lastMessageAt(session.getLastMessageAt())
            .startedAt(session.getStartedAt())
            .endedAt(session.getEndedAt())
            .durationMinutes(session.getDurationMinutes())
            .satisfactionRating(session.getSatisfactionRating())
            .isVip(session.isVip())
            .createdAt(session.getCreatedAt())
            .build();
    }
    
    private ChatMessageDTO mapToMessageDTO(LiveChatMessage message) {
        return ChatMessageDTO.builder()
            .id(message.getId())
            .sessionId(message.getSession().getId())
            .senderId(message.getSenderId())
            .senderName(message.getSenderName())
            .senderType(message.getSenderType())
            .content(message.getContent())
            .messageType(message.getMessageType())
            .sentimentScore(message.getSentimentScore())
            .createdAt(message.getCreatedAt())
            .deliveredAt(message.getDeliveredAt())
            .readAt(message.getReadAt())
            .metadata(message.getMetadata())
            .build();
    }
}