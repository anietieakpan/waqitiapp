package com.waqiti.support.service.impl;

import com.waqiti.support.domain.*;
import com.waqiti.support.dto.*;
import com.waqiti.support.repository.*;
import com.waqiti.support.service.AIAssistantService;
import com.waqiti.support.service.KnowledgeBaseService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIAssistantServiceImpl implements AIAssistantService {
    
    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    private final TicketRepository ticketRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    
    // Chat session storage (in production, use Redis)
    private final Map<String, ChatSession> chatSessions = new ConcurrentHashMap<>();
    
    private static final String SYSTEM_PROMPT = """
        You are Waqiti AI Assistant, a helpful and professional customer support agent for Waqiti P2P payment platform.
        Your role is to:
        1. Help users with their questions about payments, transfers, and account issues
        2. Provide clear, concise, and accurate information
        3. Be empathetic and understanding
        4. Escalate to human agents when necessary
        5. Never provide actual account balances or sensitive information
        6. Always prioritize security and privacy
        
        Key features of Waqiti:
        - Instant P2P money transfers
        - QR code payments
        - Bill splitting
        - Virtual cards
        - Investment features
        - International transfers
        - Biometric authentication
        - End-to-end encrypted messaging
        
        If you're unsure about something, acknowledge it and offer to connect the user with a human agent.
        """;

    @Override
    public AIResponse processQuery(String userId, String query, Map<String, Object> context) {
        log.debug("Processing AI query for user: {} - Query: {}", userId, query);
        
        try {
            // Generate embedding for the query
            float[] queryEmbedding = generateEmbedding(query);
            
            // Search knowledge base
            List<KnowledgeArticle> relevantArticles = knowledgeBaseService.searchArticles(
                query, 5
            );
            
            // Search similar resolved tickets
            List<Ticket> similarTickets = findSimilarResolvedTickets(queryEmbedding, 3);
            
            // Build context for AI
            String enrichedContext = buildEnrichedContext(query, relevantArticles, similarTickets, context);
            
            // Generate AI response
            String aiResponse = generateAIResponse(query, enrichedContext);
            
            // Extract intent and confidence
            IntentAnalysis intentAnalysis = analyzeIntent(query, aiResponse);
            
            // Determine if human handoff is needed
            boolean needsHumanHandoff = shouldHandoffToHuman(intentAnalysis, aiResponse);
            
            return AIResponse.builder()
                    .response(aiResponse)
                    .confidence(intentAnalysis.getConfidence())
                    .intent(intentAnalysis.getIntent())
                    .suggestedArticles(relevantArticles.stream().limit(3).collect(Collectors.toList()))
                    .needsHumanHandoff(needsHumanHandoff)
                    .metadata(Map.of(
                        "processingTime", System.currentTimeMillis(),
                        "articlesFound", relevantArticles.size(),
                        "similarTicketsFound", similarTickets.size()
                    ))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error processing AI query", e);
            return AIResponse.builder()
                    .response("I apologize, but I'm having trouble processing your request. Would you like me to connect you with a human agent?")
                    .confidence(0.0)
                    .needsHumanHandoff(true)
                    .error(true)
                    .build();
        }
    }

    @Override
    public ChatbotResponse handleChatMessage(String sessionId, String userId, String message) {
        log.debug("Handling chat message - Session: {}, User: {}, Message: {}", sessionId, userId, message);
        
        // Get or create chat session
        ChatSession session = chatSessions.computeIfAbsent(sessionId, 
            k -> createNewChatSession(sessionId, userId));
        
        // Add user message to history
        session.addMessage(ChatMessage.user(message));
        
        try {
            // Process the message
            AIResponse aiResponse = processQuery(userId, message, 
                Map.of("sessionId", sessionId, "messageCount", session.getMessageCount()));
            
            // Add AI response to history
            session.addMessage(ChatMessage.assistant(aiResponse.getResponse()));
            
            // Generate quick replies based on context
            List<String> quickReplies = generateQuickReplies(aiResponse.getIntent(), message);
            
            return ChatbotResponse.builder()
                    .sessionId(sessionId)
                    .message(aiResponse.getResponse())
                    .quickReplies(quickReplies)
                    .confidence(aiResponse.getConfidence())
                    .needsHumanHandoff(aiResponse.isNeedsHumanHandoff())
                    .suggestedArticles(aiResponse.getSuggestedArticles())
                    .timestamp(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error handling chat message", e);
            return ChatbotResponse.builder()
                    .sessionId(sessionId)
                    .message("I apologize for the inconvenience. Let me connect you with a human agent who can better assist you.")
                    .needsHumanHandoff(true)
                    .error(true)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public TicketSuggestions generateTicketSuggestions(Ticket ticket) {
        log.debug("Generating suggestions for ticket: {}", ticket.getTicketNumber());
        
        try {
            // Combine ticket subject and description
            String ticketContent = ticket.getSubject() + " " + ticket.getDescription();
            
            // Generate embedding
            float[] embedding = generateEmbedding(ticketContent);
            
            // Find similar resolved tickets
            List<Ticket> similarTickets = findSimilarResolvedTickets(embedding, 5);
            
            // Extract resolution patterns
            List<String> resolutionTemplates = extractResolutionTemplates(similarTickets);
            
            // Generate AI suggestions
            String aiSuggestion = generateAgentSuggestion(ticket, similarTickets);
            
            // Identify potential solutions
            List<String> potentialSolutions = identifyPotentialSolutions(ticket, similarTickets);
            
            // Estimate resolution time
            long estimatedMinutes = estimateResolutionTime(ticket.getCategory(), 
                ticket.getPriority(), similarTickets);
            
            return TicketSuggestions.builder()
                    .ticketId(ticket.getId())
                    .aiSuggestion(aiSuggestion)
                    .resolutionTemplates(resolutionTemplates)
                    .potentialSolutions(potentialSolutions)
                    .similarTickets(similarTickets.stream()
                        .map(this::mapToSimilarTicketSummary)
                        .collect(Collectors.toList()))
                    .estimatedResolutionMinutes(estimatedMinutes)
                    .suggestedTags(extractSuggestedTags(ticketContent))
                    .suggestedPriority(suggestPriority(ticketContent))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating ticket suggestions", e);
            return TicketSuggestions.builder()
                    .ticketId(ticket.getId())
                    .error(true)
                    .errorMessage("Unable to generate suggestions")
                    .build();
        }
    }

    @Override
    public HandoffAnalysis analyzeHandoffNeed(String sessionId, String userId) {
        ChatSession session = chatSessions.get(sessionId);
        if (session == null) {
            return HandoffAnalysis.builder()
                    .shouldHandoff(false)
                    .reason("No active session")
                    .build();
        }
        
        // Analyze conversation for handoff signals
        List<ChatMessage> recentMessages = session.getRecentMessages(10);
        
        // Check for explicit handoff requests
        boolean explicitRequest = recentMessages.stream()
            .anyMatch(msg -> containsHandoffKeywords(msg.getContent()));
        
        // Check for frustration indicators
        double frustrationScore = calculateFrustrationScore(recentMessages);
        
        // Check for complex issues
        boolean complexIssue = isComplexIssue(recentMessages);
        
        // Check conversation length
        boolean longConversation = session.getMessageCount() > 10;
        
        // Make handoff decision
        boolean shouldHandoff = explicitRequest || frustrationScore > 0.7 || 
            (complexIssue && longConversation);
        
        String reason = determineHandoffReason(explicitRequest, frustrationScore, 
            complexIssue, longConversation);
        
        return HandoffAnalysis.builder()
                .shouldHandoff(shouldHandoff)
                .reason(reason)
                .frustrationScore(frustrationScore)
                .conversationSummary(summarizeConversation(recentMessages))
                .suggestedAgent(suggestBestAgent(session))
                .priority(calculateHandoffPriority(frustrationScore, complexIssue))
                .build();
    }

    /**
     * Generates AI response with circuit breaker protection.
     * Falls back to generic message if OpenAI is unavailable.
     */
    @CircuitBreaker(name = "openai-service", fallbackMethod = "generateAIResponseFallback")
    @Retry(name = "openai-service")
    @TimeLimiter(name = "openai-service")
    @Bulkhead(name = "openai-service")
    @RateLimiter(name = "openai-service")
    private String generateAIResponse(String query, String context) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new SystemMessage("Context:\n" + context));
        messages.add(new UserMessage(query));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatClient.call(prompt);

        return response.getResult().getOutput().getContent();
    }

    /**
     * Fallback method when OpenAI service is unavailable.
     * Provides graceful degradation with knowledge base search results.
     */
    private String generateAIResponseFallback(String query, String context, Exception e) {
        log.warn("OpenAI service unavailable, using fallback response. Error: {}", e.getMessage());

        // Try to extract relevant info from context if available
        if (context != null && !context.isEmpty()) {
            return "I apologize, but I'm experiencing technical difficulties with my AI service. " +
                   "However, based on our knowledge base, I found some information that might help:\n\n" +
                   context.substring(0, Math.min(500, context.length())) +
                   "\n\nWould you like me to connect you with a human agent for immediate assistance?";
        }

        return "I apologize, but I'm currently experiencing technical difficulties. " +
               "Would you like me to connect you with a human support agent who can assist you right away?";
    }

    /**
     * Generates text embedding with circuit breaker protection.
     * Falls back to null embedding if service is unavailable.
     */
    @CircuitBreaker(name = "openai-service", fallbackMethod = "generateEmbeddingFallback")
    @Retry(name = "openai-service")
    @TimeLimiter(name = "openai-service")
    private float[] generateEmbedding(String text) {
        EmbeddingResponse response = embeddingClient.embed(text);
        return response.getResult().getOutput();
    }

    /**
     * Fallback method when embedding service is unavailable.
     * Returns null to trigger keyword-based search instead.
     */
    private float[] generateEmbeddingFallback(String text, Exception e) {
        log.warn("Embedding service unavailable, falling back to keyword search. Error: {}", e.getMessage());
        return null;  // Will trigger keyword-based search in calling code
    }

    private String buildEnrichedContext(String query, List<KnowledgeArticle> articles, 
                                       List<Ticket> similarTickets, Map<String, Object> context) {
        StringBuilder contextBuilder = new StringBuilder();
        
        // Add relevant knowledge articles
        if (!articles.isEmpty()) {
            contextBuilder.append("Relevant Knowledge Articles:\n");
            articles.stream().limit(3).forEach(article -> {
                contextBuilder.append("- ").append(article.getTitle())
                    .append(": ").append(article.getSummary()).append("\n");
            });
        }
        
        // Add similar resolved tickets
        if (!similarTickets.isEmpty()) {
            contextBuilder.append("\nSimilar Resolved Issues:\n");
            similarTickets.stream().limit(2).forEach(ticket -> {
                contextBuilder.append("- Issue: ").append(ticket.getSubject())
                    .append(" | Resolution: ").append(ticket.getResolution()).append("\n");
            });
        }
        
        // Add user context if available
        if (context != null && !context.isEmpty()) {
            contextBuilder.append("\nAdditional Context:\n");
            context.forEach((key, value) -> 
                contextBuilder.append("- ").append(key).append(": ").append(value).append("\n"));
        }
        
        return contextBuilder.toString();
    }

    private IntentAnalysis analyzeIntent(String query, String response) {
        // Use AI to analyze intent
        String intentPrompt = String.format("""
            Analyze the following customer query and determine:
            1. The primary intent (one of: BALANCE_INQUIRY, PAYMENT_ISSUE, TRANSFER_HELP, 
               SECURITY_CONCERN, TECHNICAL_ISSUE, ACCOUNT_HELP, GENERAL_QUESTION)
            2. Confidence level (0.0 to 1.0)
            3. Any entities mentioned (e.g., transaction IDs, amounts, dates)
            
            Query: %s
            
            Respond in JSON format:
            {
                "intent": "INTENT_NAME",
                "confidence": 0.8,
                "entities": {
                    "key": "value"
                }
            }
            """, query);
        
        List<Message> messages = List.of(
            new SystemMessage("You are an intent classification system. Respond only with valid JSON."),
            new UserMessage(intentPrompt)
        );
        
        ChatResponse intentResponse = chatClient.call(new Prompt(messages));
        String jsonResponse = intentResponse.getResult().getOutput().getContent();
        
        // Parse JSON response
        return parseIntentAnalysis(jsonResponse);
    }

    private boolean shouldHandoffToHuman(IntentAnalysis intent, String response) {
        // Check confidence threshold
        if (intent.getConfidence() < 0.6) {
            return true;
        }
        
        // Check for specific intents that need human help
        Set<String> humanRequiredIntents = Set.of(
            "COMPLAINT", "LEGAL_ISSUE", "COMPLEX_TECHNICAL", "ACCOUNT_LOCKED"
        );
        
        if (humanRequiredIntents.contains(intent.getIntent())) {
            return true;
        }
        
        // Check if response contains uncertainty phrases
        String[] uncertaintyPhrases = {
            "I'm not sure", "I don't have enough information", 
            "would need to check", "might need human assistance"
        };
        
        String lowerResponse = response.toLowerCase();
        return Arrays.stream(uncertaintyPhrases)
            .anyMatch(lowerResponse::contains);
    }

    @Cacheable(value = "similarTickets", key = "#embedding.hashCode()")
    private List<Ticket> findSimilarResolvedTickets(float[] embedding, int limit) {
        // In production, this would use a vector database
        // For now, we'll use a simple text search
        return ticketRepository.findResolvedTicketsWithSimilarity(
            PageRequest.of(0, limit)
        );
    }

    private List<String> generateQuickReplies(String intent, String message) {
        return switch (intent) {
            case "PAYMENT_ISSUE" -> List.of(
                "Check transaction status",
                "Report failed payment",
                "Request refund",
                "Talk to agent"
            );
            case "BALANCE_INQUIRY" -> List.of(
                "View transaction history",
                "Add money to wallet",
                "Withdraw funds",
                "Check pending transactions"
            );
            case "SECURITY_CONCERN" -> List.of(
                "Reset password",
                "Enable 2FA",
                "Report unauthorized access",
                "Freeze account"
            );
            default -> List.of(
                "Browse help articles",
                "Contact support",
                "Check FAQ",
                "Return to main menu"
            );
        };
    }

    private String generateAgentSuggestion(Ticket ticket, List<Ticket> similarTickets) {
        if (similarTickets.isEmpty()) {
            return "No similar tickets found. Consider gathering more information about the issue.";
        }
        
        // Build suggestion based on similar tickets
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("Based on similar tickets, consider the following approach:\n\n");
        
        // Analyze common resolution patterns
        Map<String, Long> resolutionPatterns = similarTickets.stream()
            .map(Ticket::getResolution)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                resolution -> extractResolutionPattern(resolution),
                Collectors.counting()
            ));
        
        resolutionPatterns.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                suggestion.append("• ").append(entry.getKey())
                    .append(" (used in ").append(entry.getValue()).append(" similar cases)\n");
            });
        
        return suggestion.toString();
    }

    private IntentAnalysis parseIntentAnalysis(String jsonResponse) {
        try {
            // Simple JSON parsing - in production use proper JSON library
            String intent = extractJsonValue(jsonResponse, "intent");
            double confidence = Double.parseDouble(extractJsonValue(jsonResponse, "confidence"));
            
            Map<String, String> entities = new HashMap<>();
            // Extract entities if present in JSON
            
            return IntentAnalysis.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .entities(entities)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing intent analysis JSON: {}", jsonResponse, e);
            return IntentAnalysis.builder()
                    .intent("GENERAL_QUESTION")
                    .confidence(0.5)
                    .entities(new HashMap<>())
                    .build();
        }
    }

    private List<String> extractResolutionTemplates(List<Ticket> similarTickets) {
        List<String> templates = new ArrayList<>();
        
        Map<String, Integer> patternCounts = new HashMap<>();
        
        for (Ticket ticket : similarTickets) {
            if (ticket.getResolution() != null) {
                String pattern = extractResolutionPattern(ticket.getResolution());
                patternCounts.put(pattern, patternCounts.getOrDefault(pattern, 0) + 1);
            }
        }
        
        // Sort by frequency and create templates
        patternCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> templates.add(entry.getKey()));
        
        return templates;
    }

    private List<String> identifyPotentialSolutions(Ticket ticket, List<Ticket> similarTickets) {
        List<String> solutions = new ArrayList<>();
        
        // Extract common solutions from similar tickets - optimized with streams
        Map<String, Integer> solutionCount = similarTickets.parallelStream()
            .filter(similarTicket -> similarTicket.getResolution() != null)
            .map(similarTicket -> extractSolutionsFromResolution(similarTicket.getResolution()))
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(
                solution -> solution,
                Collectors.summingInt(solution -> 1)
            ));
        
        // Sort by frequency and return top solutions
        solutionCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> solutions.add(entry.getKey()));
        
        return solutions;
    }

    private long estimateResolutionTime(String category, String priority, List<Ticket> similarTickets) {
        if (similarTickets.isEmpty()) {
            return getDefaultResolutionTime(category, priority);
        }
        
        // Calculate average resolution time from similar tickets
        double averageMinutes = similarTickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && ticket.getResolvedAt() != null)
                .mapToLong(ticket -> java.time.Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes())
                .average()
                .orElse(getDefaultResolutionTime(category, priority));
        
        return Math.round(averageMinutes);
    }

    private SimilarTicketSummary mapToSimilarTicketSummary(Ticket ticket) {
        long resolutionTime = 0;
        if (ticket.getCreatedAt() != null && ticket.getResolvedAt() != null) {
            resolutionTime = java.time.Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toMinutes();
        }
        
        return SimilarTicketSummary.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .subject(ticket.getSubject())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .resolution(ticket.getResolution())
                .similarityScore(0.85) // Would be calculated based on embedding similarity
                .resolutionTimeMinutes(resolutionTime)
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .agentId(ticket.getAssignedAgentId())
                .resolutionPattern(extractResolutionPattern(ticket.getResolution()))
                .build();
    }

    private List<String> extractSuggestedTags(String ticketContent) {
        List<String> tags = new ArrayList<>();
        String lowerContent = ticketContent.toLowerCase();
        
        // Define tag patterns
        Map<String, Pattern> tagPatterns = Map.of(
            "payment", Pattern.compile("\\b(payment|pay|transaction|money|transfer)\\b"),
            "security", Pattern.compile("\\b(security|hack|fraud|unauthorized|breach)\\b"),
            "technical", Pattern.compile("\\b(bug|error|crash|not working|technical)\\b"),
            "account", Pattern.compile("\\b(account|profile|login|registration)\\b"),
            "card", Pattern.compile("\\b(card|virtual|debit|credit)\\b"),
            "mobile", Pattern.compile("\\b(app|mobile|android|ios|phone)\\b")
        );
        
        for (Map.Entry<String, Pattern> entry : tagPatterns.entrySet()) {
            if (entry.getValue().matcher(lowerContent).find()) {
                tags.add(entry.getKey());
            }
        }
        
        return tags;
    }

    private String suggestPriority(String ticketContent) {
        String lowerContent = ticketContent.toLowerCase();
        
        // High priority indicators
        if (lowerContent.contains("urgent") || lowerContent.contains("critical") || 
            lowerContent.contains("can't access") || lowerContent.contains("fraud") ||
            lowerContent.contains("money missing") || lowerContent.contains("hacked")) {
            return "HIGH";
        }
        
        // Medium priority indicators
        if (lowerContent.contains("issue") || lowerContent.contains("problem") ||
            lowerContent.contains("not working") || lowerContent.contains("error")) {
            return "MEDIUM";
        }
        
        // Default to low priority
        return "LOW";
    }

    private boolean isComplexIssue(List<ChatMessage> messages) {
        // Check for complexity indicators in conversation
        int technicalTerms = 0;
        int questions = 0;
        
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.USER) {
                String content = message.getContent().toLowerCase();
                
                // Count technical terms
                String[] techTerms = {"api", "database", "server", "integration", "authentication", "authorization"};
                for (String term : techTerms) {
                    if (content.contains(term)) {
                        technicalTerms++;
                    }
                }
                
                // Count questions
                if (content.contains("?")) {
                    questions++;
                }
            }
        }
        
        return technicalTerms > 2 || questions > 5 || messages.size() > 8;
    }

    private String determineHandoffReason(boolean explicitRequest, double frustrationScore, 
                                        boolean complexIssue, boolean longConversation) {
        if (explicitRequest) {
            return "Customer explicitly requested human agent";
        }
        if (frustrationScore > 0.7) {
            return "High frustration level detected";
        }
        if (complexIssue) {
            return "Complex technical issue requiring specialized expertise";
        }
        if (longConversation) {
            return "Extended conversation without resolution";
        }
        return "General escalation recommended";
    }

    private String summarizeConversation(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "No conversation history";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Customer interaction summary:\n");
        
        // Extract key user messages
        List<ChatMessage> userMessages = messages.stream()
                .filter(msg -> msg.getRole() == ChatMessage.Role.USER)
                .limit(3)
                .collect(Collectors.toList());
        
        summary.append("Key concerns: ");
        for (int i = 0; i < userMessages.size(); i++) {
            if (i > 0) summary.append("; ");
            summary.append(userMessages.get(i).getContent().substring(0, 
                Math.min(50, userMessages.get(i).getContent().length())));
        }
        
        return summary.toString();
    }

    private String suggestBestAgent(ChatSession session) {
        // In production, this would use agent expertise matching
        // For now, return a generic suggestion
        return "agent_general_support";
    }

    private String calculateHandoffPriority(double frustrationScore, boolean complexIssue) {
        if (frustrationScore > 0.8 || complexIssue) {
            return "HIGH";
        } else if (frustrationScore > 0.5) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private String extractResolutionPattern(String resolution) {
        if (resolution == null || resolution.trim().isEmpty()) {
            return "No resolution pattern";
        }
        
        String lowerResolution = resolution.toLowerCase();
        
        // Common resolution patterns
        if (lowerResolution.contains("reset") || lowerResolution.contains("restart")) {
            return "System reset/restart";
        } else if (lowerResolution.contains("update") || lowerResolution.contains("upgrade")) {
            return "Software update";
        } else if (lowerResolution.contains("refund") || lowerResolution.contains("reversed")) {
            return "Transaction reversal";
        } else if (lowerResolution.contains("verification") || lowerResolution.contains("verified")) {
            return "Account verification";
        } else if (lowerResolution.contains("config") || lowerResolution.contains("setting")) {
            return "Configuration change";
        } else {
            return "Manual investigation";
        }
    }

    private boolean containsHandoffKeywords(String message) {
        String[] keywords = {
            "speak to agent", "human agent", "real person", 
            "talk to someone", "operator", "representative",
            "not helping", "doesn't understand", "frustrated"
        };
        
        String lowerMessage = message.toLowerCase();
        return Arrays.stream(keywords).anyMatch(lowerMessage::contains);
    }

    private double calculateFrustrationScore(List<ChatMessage> messages) {
        double score = 0.0;
        
        // Frustration indicators
        String[] frustrationWords = {
            "frustrated", "annoying", "stupid", "doesn't work",
            "broken", "terrible", "worst", "hate", "angry"
        };
        
        String[] exclamations = {"!", "!!", "!!!", "??", "?!"};
        
        for (ChatMessage msg : messages) {
            if (msg.getRole() == ChatMessage.Role.USER) {
                String content = msg.getContent().toLowerCase();
                
                // Check frustration words
                for (String word : frustrationWords) {
                    if (content.contains(word)) {
                        score += 0.2;
                    }
                }
                
                // Check exclamations
                for (String exclamation : exclamations) {
                    if (content.contains(exclamation)) {
                        score += 0.1;
                    }
                }
                
                // Check for caps (shouting)
                if (content.equals(content.toUpperCase()) && content.length() > 5) {
                    score += 0.3;
                }
            }
        }
        
        return Math.min(score, 1.0);
    }

    private ChatSession createNewChatSession(String sessionId, String userId) {
        return ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .startTime(LocalDateTime.now())
                .messages(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }

    private String extractJsonValue(String json, String key) {
        // Simple JSON value extraction - in production use proper JSON library
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\"\\n\\r,}]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private List<String> extractSolutionsFromResolution(String resolution) {
        List<String> solutions = new ArrayList<>();
        
        if (resolution == null || resolution.trim().isEmpty()) {
            return solutions;
        }
        
        String lowerResolution = resolution.toLowerCase();
        
        // Extract common solution patterns
        if (lowerResolution.contains("reset password")) {
            solutions.add("Reset user password");
        }
        if (lowerResolution.contains("verify account")) {
            solutions.add("Account verification required");
        }
        if (lowerResolution.contains("contact bank")) {
            solutions.add("Bank integration issue");
        }
        if (lowerResolution.contains("update app")) {
            solutions.add("App update required");
        }
        if (lowerResolution.contains("clear cache")) {
            solutions.add("Clear application cache");
        }
        if (lowerResolution.contains("refund")) {
            solutions.add("Process transaction refund");
        }
        
        return solutions;
    }

    private long getDefaultResolutionTime(String category, String priority) {
        // Default resolution times in minutes based on category and priority
        Map<String, Map<String, Long>> defaultTimes = Map.of(
            "PAYMENT", Map.of("HIGH", 60L, "MEDIUM", 120L, "LOW", 240L),
            "SECURITY", Map.of("HIGH", 30L, "MEDIUM", 90L, "LOW", 180L),
            "TECHNICAL", Map.of("HIGH", 120L, "MEDIUM", 240L, "LOW", 480L),
            "ACCOUNT", Map.of("HIGH", 45L, "MEDIUM", 120L, "LOW", 240L)
        );
        
        return defaultTimes.getOrDefault(category, Map.of("HIGH", 60L, "MEDIUM", 120L, "LOW", 240L))
                .getOrDefault(priority, 120L);
    }

    // Additional helper classes
    @lombok.Data
    @lombok.Builder
    private static class IntentAnalysis {
        private String intent;
        private double confidence;
        private Map<String, String> entities;
    }

    @lombok.Data
    @lombok.Builder
    private static class ChatSession {
        private String sessionId;
        private String userId;
        private LocalDateTime startTime;
        private List<ChatMessage> messages;
        private Map<String, Object> metadata;
        
        public void addMessage(ChatMessage message) {
            messages.add(message);
        }
        
        public int getMessageCount() {
            return messages.size();
        }
        
        public List<ChatMessage> getRecentMessages(int count) {
            int start = Math.max(0, messages.size() - count);
            return messages.subList(start, messages.size());
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class ChatMessage {
        public enum Role { USER, ASSISTANT, SYSTEM }
        
        private Role role;
        private String content;
        private LocalDateTime timestamp;
        
        public static ChatMessage user(String content) {
            return ChatMessage.builder()
                    .role(Role.USER)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        public static ChatMessage assistant(String content) {
            return ChatMessage.builder()
                    .role(Role.ASSISTANT)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
}