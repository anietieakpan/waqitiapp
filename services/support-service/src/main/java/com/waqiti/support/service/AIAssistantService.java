package com.waqiti.support.service;

import com.waqiti.support.dto.CreateTicketRequest;
import com.waqiti.support.model.FAQ;
import com.waqiti.support.model.KnowledgeArticle;
import com.waqiti.support.model.Ticket;
import com.waqiti.support.repository.FAQRepository;
import com.waqiti.support.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAssistantService {
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final FAQRepository faqRepository;
    private final KnowledgeArticleRepository articleRepository;
    private final NLPService nlpService;

    public AIResponse tryAutoResolve(CreateTicketRequest request) {
        log.info("Attempting to auto-resolve ticket: {}", request.getSubject());

        // Extract intent and entities from the request
        NLPResult nlpResult = nlpService.analyze(request.getSubject() + " " + request.getDescription());
        
        // Search for similar FAQs
        List<FAQ> similarFAQs = searchSimilarFAQs(nlpResult.getEmbedding(), 5);
        
        // Search for relevant knowledge articles
        List<KnowledgeArticle> relevantArticles = searchRelevantArticles(nlpResult.getEmbedding(), 3);

        // Check if we have a high confidence match
        if (!similarFAQs.isEmpty() && similarFAQs.get(0).getRelevanceScore() > 0.85) {
            FAQ bestMatch = similarFAQs.get(0);
            return AIResponse.builder()
                    .resolved(true)
                    .response(bestMatch.getAnswer())
                    .confidence(bestMatch.getRelevanceScore())
                    .suggestedArticles(relevantArticles)
                    .build();
        }

        // Try to generate a response based on intent
        if (nlpResult.getIntent() != null) {
            Optional<String> generatedResponse = generateResponseForIntent(nlpResult);
            if (generatedResponse.isPresent()) {
                return AIResponse.builder()
                        .resolved(true)
                        .response(generatedResponse.get())
                        .confidence(0.75)
                        .suggestedArticles(relevantArticles)
                        .build();
            }
        }

        // Provide suggestions even if we can't auto-resolve
        return AIResponse.builder()
                .resolved(false)
                .suggestedFAQs(similarFAQs)
                .suggestedArticles(relevantArticles)
                .suggestedTags(nlpResult.getExtractedTags())
                .suggestedCategory(determineCategoryFromIntent(nlpResult.getIntent()))
                .build();
    }

    public AgentSuggestions generateAgentSuggestions(Ticket ticket) {
        log.info("Generating agent suggestions for ticket: {}", ticket.getTicketNumber());

        // Analyze ticket content
        String content = ticket.getSubject() + " " + ticket.getDescription();
        NLPResult nlpResult = nlpService.analyze(content);

        // Find similar resolved tickets
        List<Ticket> similarTickets = findSimilarResolvedTickets(ticket, 5);

        // Extract successful resolution patterns
        List<ResolutionPattern> patterns = extractResolutionPatterns(similarTickets);

        // Generate response templates
        List<String> responseTemplates = generateResponseTemplates(ticket, patterns);

        // Identify potential issues and solutions
        List<IssueSolution> potentialSolutions = identifyPotentialSolutions(nlpResult, similarTickets);

        return AgentSuggestions.builder()
                .similarTickets(similarTickets.stream()
                        .map(this::mapToSimilarTicket)
                        .collect(Collectors.toList()))
                .responseTemplates(responseTemplates)
                .potentialSolutions(potentialSolutions)
                .recommendedActions(determineRecommendedActions(ticket, nlpResult))
                .estimatedResolutionTime(estimateResolutionTime(ticket, similarTickets))
                .build();
    }

    public ChatbotResponse handleChatMessage(String userId, String message, String sessionId) {
        log.info("Processing chatbot message from user: {}", userId);

        // Get or create chat session
        ChatSession session = chatSessionService.getOrCreateSession(sessionId, userId);

        // Analyze message
        NLPResult nlpResult = nlpService.analyze(message);

        // Check for greeting/farewell
        if (nlpResult.getIntent() == Intent.GREETING) {
            return ChatbotResponse.builder()
                    .message("Hello! I'm Waqiti Assistant. How can I help you today?")
                    .quickReplies(Arrays.asList(
                            "Check my balance",
                            "Report an issue",
                            "How to send money",
                            "Account security"
                    ))
                    .sessionId(sessionId)
                    .build();
        }

        // Try to handle common queries
        Optional<String> quickResponse = handleCommonQueries(nlpResult, userId);
        if (quickResponse.isPresent()) {
            return ChatbotResponse.builder()
                    .message(quickResponse.get())
                    .sessionId(sessionId)
                    .requiresHumanAgent(false)
                    .build();
        }

        // Search knowledge base
        List<FAQ> faqs = searchSimilarFAQs(nlpResult.getEmbedding(), 3);
        if (!faqs.isEmpty() && faqs.get(0).getRelevanceScore() > 0.8) {
            return ChatbotResponse.builder()
                    .message(faqs.get(0).getAnswer())
                    .relatedArticles(searchRelevantArticles(nlpResult.getEmbedding(), 2))
                    .sessionId(sessionId)
                    .requiresHumanAgent(false)
                    .build();
        }

        // If we can't handle it, offer to create a ticket or connect to agent
        return ChatbotResponse.builder()
                .message("I understand you need help with this. Would you like me to:")
                .quickReplies(Arrays.asList(
                        "Connect to a human agent",
                        "Create a support ticket",
                        "Search help articles"
                ))
                .sessionId(sessionId)
                .requiresHumanAgent(true)
                .build();
    }

    private List<FAQ> searchSimilarFAQs(float[] embedding, int limit) {
        // Search in vector store
        var results = vectorStore.similaritySearch(
                SearchRequest.query(embedding)
                        .withTopK(limit)
                        .withSimilarityThreshold(0.7)
        );

        return results.stream()
                .map(result -> {
                    FAQ faq = faqRepository.findById(result.getId()).orElse(null);
                    if (faq != null) {
                        faq.setRelevanceScore(result.getScore());
                    }
                    return faq;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<KnowledgeArticle> searchRelevantArticles(float[] embedding, int limit) {
        var results = vectorStore.similaritySearch(
                SearchRequest.query(embedding)
                        .withTopK(limit)
                        .withSimilarityThreshold(0.65)
        );

        return results.stream()
                .map(result -> articleRepository.findById(result.getId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Optional<String> generateResponseForIntent(NLPResult nlpResult) {
        switch (nlpResult.getIntent()) {
            case BALANCE_INQUIRY:
                return Optional.of("To check your balance, please open the Waqiti app and tap on your wallet. " +
                        "Your current balance will be displayed at the top of the screen. " +
                        "You can also view detailed transaction history below.");
                
            case TRANSFER_MONEY:
                return Optional.of("To send money:\n" +
                        "1. Open the Waqiti app\n" +
                        "2. Tap 'Send Money'\n" +
                        "3. Enter recipient's username or scan their QR code\n" +
                        "4. Enter amount and optional note\n" +
                        "5. Confirm with your PIN\n\n" +
                        "The money will be transferred instantly!");
                
            case CARD_ISSUE:
                String cardType = nlpResult.getEntities().getOrDefault("card_type", "card");
                return Optional.of("I understand you're having issues with your " + cardType + ". " +
                        "For security reasons, please don't share your card details here. " +
                        "You can temporarily freeze your card in the app under Settings > Cards. " +
                        "Would you like me to create a support ticket for further assistance?");
                
            case ACCOUNT_SECURITY:
                return Optional.of("Your account security is our top priority. Here are some tips:\n" +
                        "• Enable two-factor authentication\n" +
                        "• Use a strong, unique password\n" +
                        "• Never share your PIN or password\n" +
                        "• Review your transaction history regularly\n\n" +
                        "If you suspect unauthorized access, please freeze your account immediately in Settings.");
                
            default:
                return Optional.empty();
        }
    }

    private Optional<String> handleCommonQueries(NLPResult nlpResult, String userId) {
        // Handle balance inquiry
        if (nlpResult.getIntent() == Intent.BALANCE_INQUIRY) {
            // In real implementation, would fetch actual balance
            return Optional.of("To check your balance, please open the Waqiti app. " +
                    "Your balance is displayed on the home screen. " +
                    "For security reasons, I cannot display your balance in chat.");
        }

        // Handle transaction status
        if (nlpResult.getIntent() == Intent.TRANSACTION_STATUS && 
                nlpResult.getEntities().containsKey("transaction_id")) {
            String transactionId = nlpResult.getEntities().get("transaction_id");
            // In real implementation, would check actual transaction
            return Optional.of("I'm checking the status of transaction " + transactionId + "... " +
                    "For detailed transaction information, please check your transaction history in the app.");
        }

        return Optional.empty();
    }

    private Ticket.TicketCategory determineCategoryFromIntent(Intent intent) {
        if (intent == null) return Ticket.TicketCategory.OTHER;
        
        return switch (intent) {
            case PAYMENT_ISSUE, TRANSFER_MONEY -> Ticket.TicketCategory.PAYMENT;
            case ACCOUNT_SECURITY, LOGIN_ISSUE -> Ticket.TicketCategory.SECURITY;
            case CARD_ISSUE, BALANCE_INQUIRY -> Ticket.TicketCategory.ACCOUNT;
            case TECHNICAL_ISSUE -> Ticket.TicketCategory.TECHNICAL;
            case BILLING_INQUIRY -> Ticket.TicketCategory.BILLING;
            default -> Ticket.TicketCategory.OTHER;
        };
    }

    private List<String> generateResponseTemplates(Ticket ticket, List<ResolutionPattern> patterns) {
        List<String> templates = new ArrayList<>();

        // Generate templates based on ticket category
        switch (ticket.getCategory()) {
            case PAYMENT:
                templates.add("I understand you're experiencing issues with a payment. " +
                        "I've reviewed your account and [finding]. " +
                        "To resolve this, [action].");
                templates.add("Thank you for contacting us about your payment concern. " +
                        "I can see that [observation]. " +
                        "Here's what we can do: [solution].");
                break;
                
            case SECURITY:
                templates.add("Thank you for reporting this security concern. " +
                        "Your account safety is our top priority. " +
                        "I've [action taken] and [next steps].");
                break;
                
            case TECHNICAL:
                templates.add("I apologize for the technical difficulties you're experiencing. " +
                        "To help resolve this, could you please [troubleshooting step]? " +
                        "This will help us [benefit].");
                break;
        }

        // Add templates from successful patterns
        patterns.stream()
                .limit(2)
                .forEach(pattern -> templates.add(pattern.getResponseTemplate()));

        return templates;
    }

    @Data
    @Builder
    public static class AIResponse {
        private boolean resolved;
        private String response;
        private double confidence;
        private List<FAQ> suggestedFAQs;
        private List<KnowledgeArticle> suggestedArticles;
        private List<String> suggestedTags;
        private Ticket.TicketCategory suggestedCategory;
    }

    @Data
    @Builder
    public static class AgentSuggestions {
        private List<SimilarTicket> similarTickets;
        private List<String> responseTemplates;
        private List<IssueSolution> potentialSolutions;
        private List<String> recommendedActions;
        private Long estimatedResolutionTime; // in minutes
    }

    @Data
    @Builder
    public static class ChatbotResponse {
        private String message;
        private List<String> quickReplies;
        private List<KnowledgeArticle> relatedArticles;
        private String sessionId;
        private boolean requiresHumanAgent;
        private Map<String, Object> metadata;
    }

    public enum Intent {
        GREETING,
        BALANCE_INQUIRY,
        TRANSFER_MONEY,
        PAYMENT_ISSUE,
        CARD_ISSUE,
        ACCOUNT_SECURITY,
        LOGIN_ISSUE,
        TECHNICAL_ISSUE,
        BILLING_INQUIRY,
        FEATURE_REQUEST,
        COMPLAINT,
        GENERAL_INQUIRY,
        TRANSACTION_STATUS
    }
}