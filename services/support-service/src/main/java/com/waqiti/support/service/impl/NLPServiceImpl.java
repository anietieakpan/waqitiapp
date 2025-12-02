package com.waqiti.support.service.impl;

import com.waqiti.support.dto.NLPResult;
import com.waqiti.support.service.NLPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NLPServiceImpl implements NLPService {
    
    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    
    // Common intent patterns
    private static final Map<String, Pattern> INTENT_PATTERNS = Map.of(
        "BALANCE_INQUIRY", Pattern.compile("\\b(balance|money|wallet|amount|funds)\\b", Pattern.CASE_INSENSITIVE),
        "PAYMENT_ISSUE", Pattern.compile("\\b(payment|pay|transaction|transfer|failed|error)\\b", Pattern.CASE_INSENSITIVE),
        "SECURITY_CONCERN", Pattern.compile("\\b(security|hack|unauthorized|fraud|suspicious|locked)\\b", Pattern.CASE_INSENSITIVE),
        "TECHNICAL_ISSUE", Pattern.compile("\\b(app|bug|crash|error|not working|broken)\\b", Pattern.CASE_INSENSITIVE),
        "ACCOUNT_HELP", Pattern.compile("\\b(account|profile|settings|verification)\\b", Pattern.CASE_INSENSITIVE),
        "CARD_ISSUE", Pattern.compile("\\b(card|debit|credit|virtual|blocked)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    // Entity patterns
    private static final Map<String, Pattern> ENTITY_PATTERNS = Map.of(
        "amount", Pattern.compile("\\$?([0-9]+(?:\\.[0-9]{2})?)"),
        "transaction_id", Pattern.compile("\\b([A-Z0-9]{8,20})\\b"),
        "email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
        "phone", Pattern.compile("\\b\\+?[1-9]\\d{1,14}\\b"),
        "date", Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})\\b")
    );
    
    // Sentiment keywords
    private static final Set<String> POSITIVE_WORDS = Set.of(
        "good", "great", "excellent", "amazing", "perfect", "love", "happy", "satisfied", "thank"
    );
    
    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "bad", "terrible", "awful", "hate", "frustrated", "angry", "disappointed", "broken", "useless"
    );
    
    @Override
    public NLPResult analyze(String text) {
        log.debug("Analyzing text: {}", text);
        
        String processedText = preprocessText(text);
        String intent = classifyIntent(processedText);
        double confidence = calculateConfidence(processedText, intent);
        Map<String, String> entities = extractEntities(processedText);
        float[] embedding = generateEmbedding(processedText);
        List<String> tags = extractTags(processedText);
        String sentiment = detectSentiment(processedText);
        double sentimentScore = calculateSentimentScore(processedText);
        List<String> keywords = extractKeywords(processedText);
        
        return NLPResult.builder()
                .intent(intent)
                .confidence(confidence)
                .entities(entities)
                .embedding(embedding)
                .extractedTags(tags)
                .sentiment(sentiment)
                .sentimentScore(sentimentScore)
                .keywords(keywords)
                .processedText(processedText)
                .build();
    }
    
    @Override
    public Map<String, String> extractEntities(String text) {
        Map<String, String> entities = new HashMap<>();
        
        for (Map.Entry<String, Pattern> entry : ENTITY_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(text);
            if (matcher.find()) {
                entities.put(entry.getKey(), matcher.group(1));
            }
        }
        
        return entities;
    }
    
    @Override
    public String classifyIntent(String text) {
        // First try pattern matching for common intents
        for (Map.Entry<String, Pattern> entry : INTENT_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        
        // Fallback to AI classification for complex cases
        return classifyIntentWithAI(text);
    }
    
    @Override
    public double calculateConfidence(String text, String intent) {
        // Calculate confidence based on pattern matching strength
        Pattern pattern = INTENT_PATTERNS.get(intent);
        if (pattern != null) {
            Matcher matcher = pattern.matcher(text);
            int matches = 0;
            while (matcher.find()) {
                matches++;
            }
            return Math.min(0.8 + (matches * 0.1), 1.0);
        }
        
        // Lower confidence for AI-classified intents
        return 0.6;
    }
    
    @Override
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    public float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingClient.embed(text);
            return response.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text, e);
            return new float[0];
        }
    }
    
    @Override
    public List<String> extractTags(String text) {
        List<String> tags = new ArrayList<>();
        
        // Extract tags based on detected entities and intent
        String lowerText = text.toLowerCase();
        
        if (lowerText.contains("payment") || lowerText.contains("transaction")) {
            tags.add("payment");
        }
        if (lowerText.contains("security") || lowerText.contains("fraud")) {
            tags.add("security");
        }
        if (lowerText.contains("app") || lowerText.contains("technical")) {
            tags.add("technical");
        }
        if (lowerText.contains("account") || lowerText.contains("profile")) {
            tags.add("account");
        }
        if (lowerText.contains("card") || lowerText.contains("virtual")) {
            tags.add("card");
        }
        
        return tags;
    }
    
    @Override
    public String detectSentiment(String text) {
        double score = calculateSentimentScore(text);
        
        if (score > 0.1) {
            return "POSITIVE";
        } else if (score < -0.1) {
            return "NEGATIVE";
        } else {
            return "NEUTRAL";
        }
    }
    
    @Override
    public double calculateSimilarity(String text1, String text2) {
        float[] embedding1 = generateEmbedding(text1);
        float[] embedding2 = generateEmbedding(text2);
        
        return cosineSimilarity(embedding1, embedding2);
    }
    
    @Override
    public String preprocessText(String text) {
        if (text == null) return "";
        
        return text.trim()
                .replaceAll("\\s+", " ")  // normalize whitespace
                .replaceAll("[^a-zA-Z0-9\\s.,!?@#$%]", "") // remove special chars
                .toLowerCase();
    }
    
    private String classifyIntentWithAI(String text) {
        try {
            String prompt = """
                Classify the following customer support message into one of these intents:
                - BALANCE_INQUIRY: Questions about account balance or wallet
                - PAYMENT_ISSUE: Problems with payments or transactions
                - SECURITY_CONCERN: Security-related issues or fraud reports
                - TECHNICAL_ISSUE: App bugs or technical problems
                - ACCOUNT_HELP: Account settings or profile issues
                - CARD_ISSUE: Virtual or physical card problems
                - GENERAL_QUESTION: General inquiries
                
                Message: "%s"
                
                Respond with only the intent name.
                """.formatted(text);
            
            ChatResponse response = chatClient.call(new Prompt(List.of(
                new SystemMessage("You are an intent classifier. Respond only with the intent name."),
                new UserMessage(prompt)
            )));
            
            return response.getResult().getOutput().getContent().trim().toUpperCase();
        } catch (Exception e) {
            log.error("Error classifying intent with AI", e);
            return "GENERAL_QUESTION";
        }
    }
    
    private double calculateSentimentScore(String text) {
        String[] words = text.toLowerCase().split("\\s+");
        double score = 0.0;
        
        for (String word : words) {
            if (POSITIVE_WORDS.contains(word)) {
                score += 0.1;
            }
            if (NEGATIVE_WORDS.contains(word)) {
                score -= 0.1;
            }
        }
        
        return Math.max(-1.0, Math.min(1.0, score));
    }
    
    private List<String> extractKeywords(String text) {
        // Simple keyword extraction based on word frequency and length
        String[] words = text.toLowerCase().split("\\s+");
        Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did");
        
        return Arrays.stream(words)
                .filter(word -> word.length() > 3)
                .filter(word -> !stopWords.contains(word))
                .filter(word -> word.matches("[a-zA-Z]+"))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }
    
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length || vectorA.length == 0) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}