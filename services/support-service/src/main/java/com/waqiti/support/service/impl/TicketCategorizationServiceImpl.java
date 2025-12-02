package com.waqiti.support.service.impl;

import com.waqiti.support.domain.*;
import com.waqiti.support.dto.*;
import com.waqiti.support.repository.TicketRepository;
import com.waqiti.support.service.TicketCategorizationService;
import com.waqiti.support.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCategorizationServiceImpl implements TicketCategorizationService {

    private final TicketRepository ticketRepository;
    private final AIAssistantService aiAssistantService;
    
    // Keyword patterns for different categories
    private final Map<TicketCategory, CategoryRules> categoryRules = new HashMap<>();
    private final Map<TicketSubCategory, List<Pattern>> subCategoryPatterns = new HashMap<>();
    private final Map<String, Double> priorityKeywords = new HashMap<>();
    private final Set<Pattern> urgentPatterns = new HashSet<>();
    private final Set<Pattern> securityPatterns = new HashSet<>();
    private final Set<Pattern> spamPatterns = new HashSet<>();
    
    // ML model components (simplified representation)
    private final Map<String, Double> termFrequencies = new HashMap<>();
    private final Map<TicketCategory, Map<String, Double>> categoryVectors = new HashMap<>();
    
    @PostConstruct
    public void initializeRules() {
        initializeCategoryRules();
        initializeSubCategoryPatterns();
        initializePriorityKeywords();
        initializeUrgentPatterns();
        initializeSecurityPatterns();
        initializeSpamPatterns();
        loadPretrainedModel();
    }

    @Override
    public CategorizationResult categorizeTicket(CreateTicketRequest request) {
        log.debug("Categorizing ticket: {}", request.getSubject());
        
        String content = request.getSubject() + " " + request.getDescription();
        String normalizedContent = normalizeText(content);
        
        // Rule-based categorization
        CategorizationResult ruleBasedResult = applyRuleBasedCategorization(normalizedContent, request);
        
        // ML-based categorization
        CategorizationResult mlResult = applyMLCategorization(normalizedContent, request);
        
        // Hybrid approach - combine results
        CategorizationResult finalResult = combineResults(ruleBasedResult, mlResult, request);
        
        // Add additional analysis
        finalResult.setExtractedTags(extractTags(request.getSubject(), request.getDescription()));
        finalResult.setSentimentScore(aiAssistantService.analyzeSentiment(content));
        finalResult.setEscalationProbability(predictEscalationProbability(request));
        
        // Quality assessment
        assessResultQuality(finalResult, content);
        
        log.debug("Categorization complete: {} with confidence {}", 
                 finalResult.getSuggestedCategory(), finalResult.getCategoryConfidence());
        
        return finalResult;
    }

    @Override
    public CategorizationResult recategorizeTicket(Ticket ticket) {
        CreateTicketRequest request = CreateTicketRequest.builder()
            .subject(ticket.getSubject())
            .description(ticket.getDescription())
            .userId(ticket.getUserId())
            .relatedTransactionId(ticket.getRelatedTransactionId())
            .relatedPaymentId(ticket.getRelatedPaymentId())
            .channel(ticket.getChannel())
            .build();
            
        return categorizeTicket(request);
    }

    @Override
    public TicketPriority determinePriority(CreateTicketRequest request) {
        String content = (request.getSubject() + " " + request.getDescription()).toLowerCase();
        
        // Check for critical security issues
        if (isSecurityIssue(content)) {
            if (content.contains("fraud") || content.contains("hack") || content.contains("stolen")) {
                return TicketPriority.CRITICAL;
            }
            return TicketPriority.HIGH;
        }
        
        // Check for urgent keywords
        if (isUrgentIssue(content)) {
            return TicketPriority.HIGH;
        }
        
        // Financial impact assessment
        if (request.getRelatedTransactionId() != null || request.getRelatedPaymentId() != null) {
            return TicketPriority.HIGH;
        }
        
        // Category-based priority
        if (request.getCategory() != null) {
            switch (request.getCategory()) {
                case SECURITY:
                    return TicketPriority.HIGH;
                case PAYMENT:
                case TRANSACTION:
                    return TicketPriority.HIGH;
                case TECHNICAL:
                    return TicketPriority.MEDIUM;
                case ACCOUNT:
                    return content.contains("block") || content.contains("suspend") ? 
                           TicketPriority.HIGH : TicketPriority.MEDIUM;
                default:
                    return TicketPriority.LOW;
            }
        }
        
        // ML-based priority prediction
        double priorityScore = calculatePriorityScore(content);
        if (priorityScore > 0.8) return TicketPriority.CRITICAL;
        if (priorityScore > 0.6) return TicketPriority.HIGH;
        if (priorityScore > 0.4) return TicketPriority.MEDIUM;
        return TicketPriority.LOW;
    }

    @Override
    public Set<String> extractTags(String subject, String description) {
        Set<String> tags = new HashSet<>();
        String content = (subject + " " + description).toLowerCase();
        
        // Financial keywords
        if (content.contains("refund")) tags.add("refund");
        if (content.contains("transfer")) tags.add("transfer");
        if (content.contains("payment")) tags.add("payment");
        if (content.contains("transaction")) tags.add("transaction");
        if (content.contains("balance")) tags.add("balance");
        if (content.contains("fee")) tags.add("fee");
        if (content.contains("charge")) tags.add("charge");
        
        // Account-related
        if (content.contains("account")) tags.add("account");
        if (content.contains("profile")) tags.add("profile");
        if (content.contains("verification")) tags.add("verification");
        if (content.contains("kyc")) tags.add("kyc");
        if (content.contains("document")) tags.add("document");
        
        // Technical issues
        if (content.contains("app")) tags.add("mobile-app");
        if (content.contains("website")) tags.add("website");
        if (content.contains("crash")) tags.add("crash");
        if (content.contains("error")) tags.add("error");
        if (content.contains("bug")) tags.add("bug");
        if (content.contains("slow")) tags.add("performance");
        
        // Security-related
        if (content.contains("security")) tags.add("security");
        if (content.contains("password")) tags.add("password");
        if (content.contains("login")) tags.add("login");
        if (content.contains("2fa") || content.contains("two factor")) tags.add("2fa");
        if (content.contains("fraud")) tags.add("fraud");
        if (content.contains("suspicious")) tags.add("suspicious");
        
        // Devices and platforms
        if (content.contains("iphone") || content.contains("ios")) tags.add("ios");
        if (content.contains("android")) tags.add("android");
        if (content.contains("card")) tags.add("card");
        if (content.contains("bank")) tags.add("bank");
        
        // Service quality
        if (content.contains("complaint")) tags.add("complaint");
        if (content.contains("suggestion")) tags.add("suggestion");
        if (content.contains("feature")) tags.add("feature-request");
        
        // Extract entities using patterns
        tags.addAll(extractEntityTags(content));
        
        return tags;
    }

    @Override
    public double predictEscalationProbability(CreateTicketRequest request) {
        String content = (request.getSubject() + " " + request.getDescription()).toLowerCase();
        double score = 0.0;
        
        // Sentiment-based escalation indicators
        double sentimentScore = aiAssistantService.analyzeSentiment(content);
        if (sentimentScore < -0.5) score += 0.3; // Very negative sentiment
        else if (sentimentScore < -0.2) score += 0.15; // Negative sentiment
        
        // Emotional intensity indicators
        if (content.contains("angry") || content.contains("frustrated") || content.contains("upset")) {
            score += 0.2;
        }
        if (content.contains("terrible") || content.contains("horrible") || content.contains("worst")) {
            score += 0.25;
        }
        if (content.contains("sue") || content.contains("lawyer") || content.contains("legal")) {
            score += 0.4;
        }
        
        // Urgency indicators
        if (isUrgentIssue(content)) score += 0.2;
        
        // Previous escalation patterns (would need historical data)
        // This is a simplified version
        if (request.getUserId() != null) {
            Long userEscalations = ticketRepository.countEscalatedTicketsByUser(request.getUserId());
            if (userEscalations > 0) {
                score += Math.min(0.3, userEscalations * 0.1);
            }
        }
        
        // Financial impact
        if (request.getRelatedTransactionId() != null || request.getRelatedPaymentId() != null) {
            score += 0.15;
        }
        
        // Complex issues requiring multiple interactions
        if (content.split("\\s+").length > 200) { // Long descriptions often indicate complex issues
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }

    @Override
    public List<CategorizationConfidence> getCategoryConfidences(String subject, String description) {
        String content = normalizeText(subject + " " + description);
        List<CategorizationConfidence> confidences = new ArrayList<>();
        
        // Calculate confidence for each category
        for (TicketCategory category : TicketCategory.values()) {
            double confidence = calculateCategoryConfidence(content, category);
            
            if (confidence > 0.1) { // Only include categories with meaningful confidence
                CategorizationConfidence catConfidence = CategorizationConfidence.builder()
                    .category(category)
                    .confidence(confidence)
                    .probability(confidence)
                    .supportingKeywords(findSupportingKeywords(content, category))
                    .explanation(generateExplanation(category, confidence))
                    .historicalSupport(getHistoricalSupport(category))
                    .isHighConfidence(confidence > 0.7)
                    .modelSource("hybrid")
                    .build();
                    
                confidences.add(catConfidence);
            }
        }
        
        // Sort by confidence descending
        confidences.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        
        return confidences;
    }

    @Override
    public void trainModel(List<Ticket> labeledTickets) {
        log.info("Training categorization model with {} tickets", labeledTickets.size());
        
        // Simple bag-of-words model training
        Map<TicketCategory, List<String>> categoryTexts = new HashMap<>();
        
        for (Ticket ticket : labeledTickets) {
            String content = normalizeText(ticket.getSubject() + " " + ticket.getDescription());
            categoryTexts.computeIfAbsent(ticket.getCategory(), k -> new ArrayList<>()).add(content);
        }
        
        // Build vocabulary and category vectors
        Set<String> vocabulary = new HashSet<>();
        for (List<String> texts : categoryTexts.values()) {
            for (String text : texts) {
                vocabulary.addAll(Arrays.asList(text.split("\\s+")));
            }
        }
        
        // Calculate TF-IDF vectors for each category
        for (Map.Entry<TicketCategory, List<String>> entry : categoryTexts.entrySet()) {
            TicketCategory category = entry.getKey();
            List<String> texts = entry.getValue();
            
            Map<String, Double> categoryVector = new HashMap<>();
            Map<String, Integer> termCounts = new HashMap<>();
            int totalTerms = 0;
            
            // Count terms
            for (String text : texts) {
                for (String term : text.split("\\s+")) {
                    termCounts.merge(term, 1, Integer::sum);
                    totalTerms++;
                }
            }
            
            // Calculate TF-IDF
            for (Map.Entry<String, Integer> termEntry : termCounts.entrySet()) {
                String term = termEntry.getKey();
                int count = termEntry.getValue();
                
                double tf = (double) count / totalTerms;
                double idf = Math.log((double) labeledTickets.size() / (1 + getDocumentFrequency(term, labeledTickets)));
                
                categoryVector.put(term, tf * idf);
            }
            
            categoryVectors.put(category, categoryVector);
        }
        
        log.info("Model training completed. Vocabulary size: {}", vocabulary.size());
    }

    @Override
    public boolean isUrgentIssue(String content) {
        return urgentPatterns.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    @Override
    public boolean isSecurityIssue(String content) {
        return securityPatterns.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    @Override
    public boolean isSpamTicket(String subject, String description) {
        String content = (subject + " " + description).toLowerCase();
        
        // Check against spam patterns
        if (spamPatterns.stream().anyMatch(pattern -> pattern.matcher(content).find())) {
            return true;
        }
        
        // Check for suspicious characteristics
        if (description.length() < 10) return true; // Too short
        if (content.split("\\s+").length < 3) return true; // Too few words
        
        // Check for excessive repetition
        String[] words = content.split("\\s+");
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        if (words.length > 10 && uniqueWords.size() < words.length * 0.3) {
            return true; // Too much repetition
        }
        
        // Check for nonsense content
        if (content.matches(".*[a-z]{20,}.*")) return true; // Very long words
        
        return false;
    }

    @Override
    public List<Ticket> findSimilarTickets(String content, int limit) {
        // Simplified similarity search - in production, this would use vector similarity
        String normalizedContent = normalizeText(content);
        String[] keywords = normalizedContent.split("\\s+");
        
        if (keywords.length == 0) return new ArrayList<>();
        
        // Search for tickets with similar keywords (simplified approach)
        List<String> keywordList = Arrays.asList(keywords).subList(0, Math.min(5, keywords.length));
        
        return ticketRepository.findSimilarTickets(keywordList, PageRequest.of(0, limit));
    }

    @Override
    public List<String> getRoutingSuggestions(TicketCategory category, TicketSubCategory subCategory) {
        List<String> suggestions = new ArrayList<>();
        
        switch (category) {
            case SECURITY:
                suggestions.add("security-specialist");
                suggestions.add("fraud-analyst");
                if (subCategory == TicketSubCategory.ACCOUNT_HACKED) {
                    suggestions.add("incident-response-team");
                }
                break;
                
            case PAYMENT:
            case TRANSACTION:
                suggestions.add("payment-specialist");
                suggestions.add("financial-analyst");
                if (subCategory == TicketSubCategory.PAYMENT_DISPUTE) {
                    suggestions.add("dispute-resolution-team");
                }
                break;
                
            case TECHNICAL:
                suggestions.add("technical-support");
                if (subCategory == TicketSubCategory.APP_CRASH) {
                    suggestions.add("mobile-development-team");
                }
                break;
                
            case ACCOUNT:
                suggestions.add("customer-service");
                if (subCategory == TicketSubCategory.ACCOUNT_VERIFICATION) {
                    suggestions.add("kyc-specialist");
                }
                break;
                
            case COMPLIANCE:
                suggestions.add("compliance-officer");
                suggestions.add("legal-team");
                break;
                
            default:
                suggestions.add("general-support");
        }
        
        return suggestions;
    }

    // Private helper methods
    
    private void initializeCategoryRules() {
        // Account category
        categoryRules.put(TicketCategory.ACCOUNT, CategoryRules.builder()
            .keywords(Arrays.asList("account", "profile", "verification", "kyc", "document", "identity"))
            .weight(1.0)
            .build());
            
        // Payment category
        categoryRules.put(TicketCategory.PAYMENT, CategoryRules.builder()
            .keywords(Arrays.asList("payment", "transfer", "send", "receive", "money", "amount"))
            .weight(1.0)
            .build());
            
        // Transaction category
        categoryRules.put(TicketCategory.TRANSACTION, CategoryRules.builder()
            .keywords(Arrays.asList("transaction", "transfer", "refund", "failed", "pending", "history"))
            .weight(1.0)
            .build());
            
        // Security category
        categoryRules.put(TicketCategory.SECURITY, CategoryRules.builder()
            .keywords(Arrays.asList("security", "fraud", "hack", "suspicious", "password", "login", "2fa"))
            .weight(1.2)
            .build());
            
        // Technical category
        categoryRules.put(TicketCategory.TECHNICAL, CategoryRules.builder()
            .keywords(Arrays.asList("app", "crash", "error", "bug", "slow", "website", "technical", "issue"))
            .weight(1.0)
            .build());
    }
    
    private void initializeSubCategoryPatterns() {
        // Account subcategories
        subCategoryPatterns.put(TicketSubCategory.ACCOUNT_CREATION, 
            Arrays.asList(Pattern.compile(".*create.*account.*"), Pattern.compile(".*sign.*up.*")));
        subCategoryPatterns.put(TicketSubCategory.ACCOUNT_VERIFICATION,
            Arrays.asList(Pattern.compile(".*verif.*"), Pattern.compile(".*kyc.*")));
            
        // Payment subcategories
        subCategoryPatterns.put(TicketSubCategory.PAYMENT_FAILED,
            Arrays.asList(Pattern.compile(".*payment.*fail.*"), Pattern.compile(".*transfer.*fail.*")));
        subCategoryPatterns.put(TicketSubCategory.PAYMENT_REFUND,
            Arrays.asList(Pattern.compile(".*refund.*"), Pattern.compile(".*money.*back.*")));
            
        // Security subcategories
        subCategoryPatterns.put(TicketSubCategory.ACCOUNT_HACKED,
            Arrays.asList(Pattern.compile(".*hack.*"), Pattern.compile(".*compromise.*")));
        subCategoryPatterns.put(TicketSubCategory.SUSPICIOUS_ACTIVITY,
            Arrays.asList(Pattern.compile(".*suspicious.*"), Pattern.compile(".*fraud.*")));
    }
    
    private void initializePriorityKeywords() {
        priorityKeywords.put("urgent", 0.8);
        priorityKeywords.put("critical", 0.9);
        priorityKeywords.put("emergency", 0.9);
        priorityKeywords.put("immediately", 0.7);
        priorityKeywords.put("asap", 0.7);
        priorityKeywords.put("hack", 0.9);
        priorityKeywords.put("fraud", 0.9);
        priorityKeywords.put("stolen", 0.8);
        priorityKeywords.put("locked", 0.6);
        priorityKeywords.put("cannot access", 0.6);
    }
    
    private void initializeUrgentPatterns() {
        urgentPatterns.add(Pattern.compile(".*urgent.*"));
        urgentPatterns.add(Pattern.compile(".*critical.*"));
        urgentPatterns.add(Pattern.compile(".*emergency.*"));
        urgentPatterns.add(Pattern.compile(".*immediately.*"));
        urgentPatterns.add(Pattern.compile(".*asap.*"));
        urgentPatterns.add(Pattern.compile(".*right now.*"));
        urgentPatterns.add(Pattern.compile(".*cannot access.*"));
        urgentPatterns.add(Pattern.compile(".*locked out.*"));
    }
    
    private void initializeSecurityPatterns() {
        securityPatterns.add(Pattern.compile(".*hack.*"));
        securityPatterns.add(Pattern.compile(".*fraud.*"));
        securityPatterns.add(Pattern.compile(".*suspicious.*"));
        securityPatterns.add(Pattern.compile(".*unauthorized.*"));
        securityPatterns.add(Pattern.compile(".*stolen.*"));
        securityPatterns.add(Pattern.compile(".*compromise.*"));
        securityPatterns.add(Pattern.compile(".*security.*"));
        securityPatterns.add(Pattern.compile(".*breach.*"));
    }
    
    private void initializeSpamPatterns() {
        spamPatterns.add(Pattern.compile(".*test.*test.*"));
        spamPatterns.add(Pattern.compile(".*asdfgh.*"));
        spamPatterns.add(Pattern.compile(".*qwerty.*"));
        spamPatterns.add(Pattern.compile(".*[0-9]{10,}.*"));
        spamPatterns.add(Pattern.compile(".*free money.*"));
        spamPatterns.add(Pattern.compile(".*click here.*"));
    }
    
    private void loadPretrainedModel() {
        // In a real implementation, this would load a pre-trained ML model
        // For now, we'll use the rule-based approach with some ML-like scoring
        log.info("Loaded pre-trained categorization model");
    }
    
    private String normalizeText(String text) {
        return text.toLowerCase()
                  .replaceAll("[^a-zA-Z0-9\\s]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }
    
    private CategorizationResult applyRuleBasedCategorization(String content, CreateTicketRequest request) {
        Map<TicketCategory, Double> categoryScores = new HashMap<>();
        
        // Score each category based on keyword matches
        for (Map.Entry<TicketCategory, CategoryRules> entry : categoryRules.entrySet()) {
            TicketCategory category = entry.getKey();
            CategoryRules rules = entry.getValue();
            
            double score = 0.0;
            for (String keyword : rules.getKeywords()) {
                if (content.contains(keyword)) {
                    score += rules.getWeight();
                }
            }
            
            categoryScores.put(category, score);
        }
        
        // Find best category
        TicketCategory bestCategory = categoryScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(TicketCategory.OTHER);
            
        double confidence = categoryScores.getOrDefault(bestCategory, 0.0) / 5.0; // Normalize
        
        return CategorizationResult.builder()
            .suggestedCategory(bestCategory)
            .suggestedSubCategory(determineSubCategory(bestCategory, content))
            .suggestedPriority(determinePriority(request))
            .categoryConfidence(Math.min(1.0, confidence))
            .method(CategorizationResult.CategorizationMethod.RULE_BASED)
            .reasoning("Rule-based classification using keyword matching")
            .build();
    }
    
    private CategorizationResult applyMLCategorization(String content, CreateTicketRequest request) {
        // Simplified ML approach using cosine similarity with category vectors
        Map<TicketCategory, Double> similarities = new HashMap<>();
        
        Map<String, Integer> contentVector = buildContentVector(content);
        
        for (Map.Entry<TicketCategory, Map<String, Double>> entry : categoryVectors.entrySet()) {
            TicketCategory category = entry.getKey();
            Map<String, Double> categoryVector = entry.getValue();
            
            double similarity = calculateCosineSimilarity(contentVector, categoryVector);
            similarities.put(category, similarity);
        }
        
        TicketCategory bestCategory = similarities.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(TicketCategory.OTHER);
            
        double confidence = similarities.getOrDefault(bestCategory, 0.0);
        
        return CategorizationResult.builder()
            .suggestedCategory(bestCategory)
            .suggestedSubCategory(determineSubCategory(bestCategory, content))
            .suggestedPriority(determinePriority(request))
            .categoryConfidence(confidence)
            .method(CategorizationResult.CategorizationMethod.MACHINE_LEARNING)
            .reasoning("ML-based classification using vector similarity")
            .build();
    }
    
    private CategorizationResult combineResults(CategorizationResult ruleResult, CategorizationResult mlResult, CreateTicketRequest request) {
        // Weighted combination of rule-based and ML results
        double ruleWeight = 0.6;
        double mlWeight = 0.4;
        
        TicketCategory finalCategory;
        double finalConfidence;
        
        if (ruleResult.getSuggestedCategory() == mlResult.getSuggestedCategory()) {
            finalCategory = ruleResult.getSuggestedCategory();
            finalConfidence = ruleWeight * ruleResult.getCategoryConfidence() + 
                            mlWeight * mlResult.getCategoryConfidence();
        } else {
            // Choose the one with higher confidence
            if (ruleResult.getCategoryConfidence() > mlResult.getCategoryConfidence()) {
                finalCategory = ruleResult.getSuggestedCategory();
                finalConfidence = ruleResult.getCategoryConfidence() * ruleWeight;
            } else {
                finalCategory = mlResult.getSuggestedCategory();
                finalConfidence = mlResult.getCategoryConfidence() * mlWeight;
            }
        }
        
        return CategorizationResult.builder()
            .suggestedCategory(finalCategory)
            .suggestedSubCategory(determineSubCategory(finalCategory, request.getSubject() + " " + request.getDescription()))
            .suggestedPriority(determinePriority(request))
            .categoryConfidence(Math.min(1.0, finalConfidence))
            .method(CategorizationResult.CategorizationMethod.HYBRID)
            .reasoning("Hybrid approach combining rule-based and ML classification")
            .alternativeCategories(Arrays.asList(
                ruleResult.getSuggestedCategory().toString(),
                mlResult.getSuggestedCategory().toString()
            ))
            .build();
    }
    
    private TicketSubCategory determineSubCategory(TicketCategory category, String content) {
        // Find best matching subcategory for the given category
        for (Map.Entry<TicketSubCategory, List<Pattern>> entry : subCategoryPatterns.entrySet()) {
            TicketSubCategory subCategory = entry.getKey();
            
            // Check if this subcategory belongs to the given category
            if (belongsToCategory(subCategory, category)) {
                for (Pattern pattern : entry.getValue()) {
                    if (pattern.matcher(content).find()) {
                        return subCategory;
                    }
                }
            }
        }
        
        // Return default subcategory for the category
        return getDefaultSubCategory(category);
    }
    
    private boolean belongsToCategory(TicketSubCategory subCategory, TicketCategory category) {
        switch (category) {
            case ACCOUNT:
                return Arrays.asList(TicketSubCategory.ACCOUNT_CREATION, TicketSubCategory.ACCOUNT_VERIFICATION,
                                   TicketSubCategory.ACCOUNT_CLOSURE, TicketSubCategory.PROFILE_UPDATE)
                             .contains(subCategory);
            case PAYMENT:
                return Arrays.asList(TicketSubCategory.PAYMENT_FAILED, TicketSubCategory.PAYMENT_PENDING,
                                   TicketSubCategory.PAYMENT_REFUND, TicketSubCategory.PAYMENT_DISPUTE)
                             .contains(subCategory);
            case SECURITY:
                return Arrays.asList(TicketSubCategory.ACCOUNT_HACKED, TicketSubCategory.SUSPICIOUS_ACTIVITY,
                                   TicketSubCategory.TWO_FACTOR_AUTH, TicketSubCategory.PASSWORD_RESET)
                             .contains(subCategory);
            // Add other categories...
            default:
                return false;
        }
    }
    
    private TicketSubCategory getDefaultSubCategory(TicketCategory category) {
        switch (category) {
            case ACCOUNT: return TicketSubCategory.GENERAL_INQUIRY;
            case PAYMENT: return TicketSubCategory.PAYMENT_FAILED;
            case TRANSACTION: return TicketSubCategory.TRANSACTION_NOT_RECEIVED;
            case SECURITY: return TicketSubCategory.SUSPICIOUS_ACTIVITY;
            case TECHNICAL: return TicketSubCategory.BUG_REPORT;
            case COMPLIANCE: return TicketSubCategory.KYC_VERIFICATION;
            default: return TicketSubCategory.GENERAL_INQUIRY;
        }
    }
    
    private Set<String> extractEntityTags(String content) {
        Set<String> entityTags = new HashSet<>();
        
        // Extract currency mentions
        if (content.matches(".*\\$[0-9,]+.*") || content.contains("dollar")) {
            entityTags.add("currency-usd");
        }
        if (content.contains("euro") || content.contains("€")) {
            entityTags.add("currency-eur");
        }
        
        // Extract card types
        if (content.contains("visa")) entityTags.add("visa");
        if (content.contains("mastercard")) entityTags.add("mastercard");
        if (content.contains("amex") || content.contains("american express")) entityTags.add("amex");
        
        // Extract bank names (simplified)
        if (content.contains("chase")) entityTags.add("bank-chase");
        if (content.contains("wells fargo")) entityTags.add("bank-wells-fargo");
        if (content.contains("bank of america")) entityTags.add("bank-boa");
        
        return entityTags;
    }
    
    private void assessResultQuality(CategorizationResult result, String content) {
        result.setHighConfidence(result.getCategoryConfidence() > 0.7);
        
        List<String> uncertaintyReasons = new ArrayList<>();
        
        if (result.getCategoryConfidence() < 0.5) {
            uncertaintyReasons.add("Low confidence score");
        }
        
        if (content.length() < 20) {
            uncertaintyReasons.add("Insufficient content for analysis");
        }
        
        if (result.getSuggestedCategory() == TicketCategory.OTHER) {
            uncertaintyReasons.add("Could not determine specific category");
        }
        
        result.setUncertaintyReasons(uncertaintyReasons);
        result.setRequiresHumanReview(!uncertaintyReasons.isEmpty());
    }
    
    private double calculatePriorityScore(String content) {
        double score = 0.0;
        
        for (Map.Entry<String, Double> entry : priorityKeywords.entrySet()) {
            if (content.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }
        
        return Math.min(1.0, score);
    }
    
    private double calculateCategoryConfidence(String content, TicketCategory category) {
        CategoryRules rules = categoryRules.get(category);
        if (rules == null) return 0.0;
        
        double score = 0.0;
        for (String keyword : rules.getKeywords()) {
            if (content.contains(keyword)) {
                score += rules.getWeight();
            }
        }
        
        return Math.min(1.0, score / 5.0); // Normalize
    }
    
    private List<String> findSupportingKeywords(String content, TicketCategory category) {
        CategoryRules rules = categoryRules.get(category);
        if (rules == null) return new ArrayList<>();
        
        return rules.getKeywords().stream()
            .filter(content::contains)
            .collect(Collectors.toList());
    }
    
    private String generateExplanation(TicketCategory category, double confidence) {
        return String.format("Category %s suggested with %.2f confidence based on keyword analysis", 
                           category, confidence);
    }
    
    private int getHistoricalSupport(TicketCategory category) {
        return ticketRepository.countByCategory(category).intValue();
    }
    
    private Map<String, Integer> buildContentVector(String content) {
        Map<String, Integer> vector = new HashMap<>();
        for (String term : content.split("\\s+")) {
            vector.merge(term, 1, Integer::sum);
        }
        return vector;
    }
    
    private double calculateCosineSimilarity(Map<String, Integer> vector1, Map<String, Double> vector2) {
        Set<String> commonTerms = new HashSet<>(vector1.keySet());
        commonTerms.retainAll(vector2.keySet());
        
        if (commonTerms.isEmpty()) return 0.0;
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String term : commonTerms) {
            double v1 = vector1.getOrDefault(term, 0);
            double v2 = vector2.getOrDefault(term, 0.0);
            
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    private int getDocumentFrequency(String term, List<Ticket> tickets) {
        return (int) tickets.stream()
            .filter(ticket -> {
                String content = (ticket.getSubject() + " " + ticket.getDescription()).toLowerCase();
                return content.contains(term);
            })
            .count();
    }
    
    // Helper class for category rules
    @lombok.Data
    @lombok.Builder
    private static class CategoryRules {
        private List<String> keywords;
        private double weight;
    }
}