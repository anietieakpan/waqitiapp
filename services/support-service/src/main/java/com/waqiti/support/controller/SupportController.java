package com.waqiti.support.controller;

import com.waqiti.support.dto.*;
import com.waqiti.support.service.*;
import com.waqiti.support.service.impl.AIAssistantServiceImpl;
import com.waqiti.support.domain.Ticket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Customer support operations")
@Slf4j
public class SupportController {
    private final TicketService ticketService;
    private final AIAssistantServiceImpl aiAssistantService;
    private final LiveChatService liveChatService;
    private final SimpleKnowledgeBaseService knowledgeBaseService;
    private final ChatSessionService chatSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/tickets")
    @Operation(summary = "Create a new support ticket")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TicketDTO> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @RequestHeader("X-User-Id") String userId) {
        request.setUserId(userId);
        TicketDTO ticket = ticketService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping("/tickets")
    @Operation(summary = "Get user's tickets")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TicketDTO>> getUserTickets(
            @RequestHeader("X-User-Id") String userId,
            @ModelAttribute TicketFilterRequest filter,
            Pageable pageable) {
        Page<TicketDTO> tickets = ticketService.getUserTickets(userId, filter, pageable);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Get ticket details")
    @PreAuthorize("hasRole('USER') or hasRole('SUPPORT_AGENT')")
    public ResponseEntity<TicketDetailsDTO> getTicketDetails(
            @PathVariable String ticketId,
            @RequestHeader("X-User-Id") String userId) {
        TicketDetailsDTO details = ticketService.getTicketDetails(ticketId, userId);
        return ResponseEntity.ok(details);
    }

    @PutMapping("/tickets/{ticketId}")
    @Operation(summary = "Update ticket")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<TicketDTO> updateTicket(
            @PathVariable String ticketId,
            @Valid @RequestBody UpdateTicketRequest request) {
        TicketDTO ticket = ticketService.updateTicket(ticketId, request);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @Operation(summary = "Add message to ticket")
    @PreAuthorize("hasRole('USER') or hasRole('SUPPORT_AGENT')")
    public ResponseEntity<TicketMessageDTO> addMessage(
            @PathVariable String ticketId,
            @Valid @RequestBody AddMessageRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        request.setSenderId(userId);
        request.setSenderType(userRole.contains("AGENT") ? 
                TicketMessage.SenderType.AGENT : 
                TicketMessage.SenderType.CUSTOMER);
        TicketMessageDTO message = ticketService.addMessage(ticketId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PostMapping("/tickets/{ticketId}/escalate")
    @Operation(summary = "Escalate ticket")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Void> escalateTicket(
            @PathVariable String ticketId,
            @Valid @RequestBody EscalateTicketRequest request) {
        ticketService.escalateTicket(ticketId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tickets/{ticketId}/satisfaction")
    @Operation(summary = "Submit satisfaction rating")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> submitSatisfactionRating(
            @PathVariable String ticketId,
            @Valid @RequestBody SatisfactionRatingRequest request) {
        ticketService.submitSatisfactionRating(ticketId, request);
        return ResponseEntity.noContent().build();
    }

    // Chatbot endpoints
    @PostMapping("/chat/message")
    @Operation(summary = "Send message to chatbot")
    public ResponseEntity<ChatbotResponse> sendChatMessage(
            @Valid @RequestBody ChatMessageRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        
        String actualUserId = userId != null ? userId : "anonymous";
        String actualSessionId = sessionId != null ? sessionId : generateSessionId();
        
        log.info("Processing chat message from user: {} in session: {}", actualUserId, actualSessionId);
        
        ChatbotResponse response = aiAssistantService.handleChatMessage(
                actualSessionId, actualUserId, request.getMessage()
        );
        
        // Send response via WebSocket if connected
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + actualSessionId, response);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message for session: {}", actualSessionId, e);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/handoff")
    @Operation(summary = "Request handoff to human agent")
    public ResponseEntity<HandoffAnalysis> requestHandoff(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        log.info("Processing handoff request for session: {}", sessionId);
        
        HandoffAnalysis analysis = aiAssistantService.analyzeHandoffNeed(sessionId, userId);
        
        if (analysis.isShouldHandoff()) {
            // Transfer to human agent
            chatSessionService.transferToAgent(sessionId, analysis.getSuggestedAgent());
            
            // Notify via WebSocket
            ChatbotResponse handoffResponse = ChatbotResponse.builder()
                    .sessionId(sessionId)
                    .message("I'm connecting you with a human agent who can better assist you.")
                    .needsHumanHandoff(true)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            try {
                messagingTemplate.convertAndSend("/topic/chat/" + sessionId, handoffResponse);
            } catch (Exception e) {
                log.warn("Failed to send handoff notification via WebSocket", e);
            }
        }
        
        return ResponseEntity.ok(analysis);
    }

    // WebSocket endpoints for real-time chat
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/chat")
    public ChatbotResponse sendMessage(@Payload ChatMessageRequest message) {
        log.info("Received WebSocket message: {}", message.getMessage());
        
        return aiAssistantService.handleChatMessage(
                message.getSessionId(),
                message.getUserId(),
                message.getMessage()
        );
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/chat")
    public ChatbotResponse addUser(@Payload ChatMessageRequest message) {
        log.info("User joined chat session: {}", message.getSessionId());
        
        return ChatbotResponse.builder()
                .sessionId(message.getSessionId())
                .message("Welcome! How can I help you today?")
                .needsHumanHandoff(false)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Live chat endpoints
    @PostMapping("/live-chat/connect")
    @Operation(summary = "Connect to live chat agent")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LiveChatSession> connectToAgent(
            @Valid @RequestBody ConnectAgentRequest request,
            @RequestHeader("X-User-Id") String userId) {
        LiveChatSession session = liveChatService.connectToAgent(userId, request);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/live-chat/{sessionId}/message")
    @Operation(summary = "Send message in live chat")
    @PreAuthorize("hasRole('USER') or hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Void> sendLiveChatMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody LiveChatMessageRequest request,
            @RequestHeader("X-User-Id") String userId) {
        liveChatService.sendMessage(sessionId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/live-chat/{sessionId}/end")
    @Operation(summary = "End live chat session")
    @PreAuthorize("hasRole('USER') or hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Void> endLiveChat(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId) {
        liveChatService.endSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    // Knowledge base endpoints
    @GetMapping("/knowledge/search")
    @Operation(summary = "Search knowledge base")
    public ResponseEntity<List<KnowledgeArticle>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Searching knowledge base for: {}", query);
        List<KnowledgeArticle> articles = knowledgeBaseService.searchArticles(query, limit);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/knowledge/popular")
    @Operation(summary = "Get popular knowledge articles")
    public ResponseEntity<List<KnowledgeArticle>> getPopularArticles(
            @RequestParam(defaultValue = "10") int limit) {
        List<KnowledgeArticle> articles = knowledgeBaseService.findPopularArticles(limit);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/knowledge/recent")
    @Operation(summary = "Get recent knowledge articles")
    public ResponseEntity<List<KnowledgeArticle>> getRecentArticles(
            @RequestParam(defaultValue = "10") int limit) {
        List<KnowledgeArticle> articles = knowledgeBaseService.findRecentArticles(limit);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/knowledge/featured")
    @Operation(summary = "Get featured knowledge articles")
    public ResponseEntity<List<KnowledgeArticle>> getFeaturedArticles(
            @RequestParam(defaultValue = "10") int limit) {
        List<KnowledgeArticle> articles = knowledgeBaseService.findFeaturedArticles(limit);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/knowledge/category/{category}")
    @Operation(summary = "Get articles by category")
    public ResponseEntity<List<KnowledgeArticle>> getArticlesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "10") int limit) {
        List<KnowledgeArticle> articles = knowledgeBaseService.findByCategory(category, limit);
        return ResponseEntity.ok(articles);
    }

    // Agent endpoints
    @GetMapping("/agent/tickets")
    @Operation(summary = "Get agent's assigned tickets")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Page<TicketDTO>> getAgentTickets(
            @RequestHeader("X-User-Id") String agentId,
            @ModelAttribute TicketFilterRequest filter,
            Pageable pageable) {
        Page<TicketDTO> tickets = ticketService.getAgentTickets(agentId, filter, pageable);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/agent/tickets/{ticketId}/suggestions")
    @Operation(summary = "Get AI suggestions for ticket")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<TicketSuggestions> getTicketSuggestions(@PathVariable String ticketId) {
        log.info("Generating AI suggestions for ticket: {}", ticketId);
        
        // Get ticket from service
        Ticket ticket = ticketService.findById(ticketId);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        
        TicketSuggestions suggestions = aiAssistantService.generateTicketSuggestions(ticket);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/agent/chat/query")
    @Operation(summary = "Get AI assistance for agent")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<AIResponse> getAgentAI(
            @Valid @RequestBody ChatMessageRequest request,
            @RequestHeader("X-User-Id") String agentId) {
        
        log.info("Processing AI query from agent: {}", agentId);
        
        AIResponse response = aiAssistantService.processQuery(
                agentId,
                request.getMessage(),
                request.getContext()
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    @Operation(summary = "Get support analytics")
    @PreAuthorize("hasRole('SUPPORT_MANAGER')")
    public ResponseEntity<TicketAnalyticsDTO> getAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        TicketAnalyticsDTO analytics = ticketService.getTicketAnalytics(startDate, endDate);
        return ResponseEntity.ok(analytics);
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }
}