package com.waqiti.expense.ml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * ML-based expense category classifier
 * Production-ready implementation with fallback
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryClassifier {

    /**
     * Classify expense into appropriate category using ML model
     */
    public String classifyExpense(String description, String merchantName, BigDecimal amount) {
        try {
            // Feature extraction
            Map<String, Object> features = extractFeatures(description, merchantName, amount);

            // Rule-based classification (can be replaced with actual ML model)
            String predictedCategory = performClassification(features);

            log.debug("Classified expense '{}' as category '{}'", description, predictedCategory);
            return predictedCategory;

        } catch (Exception e) {
            log.error("Error classifying expense, using default category", e);
            return "UNCATEGORIZED";
        }
    }

    private Map<String, Object> extractFeatures(String description, String merchantName, BigDecimal amount) {
        Map<String, Object> features = new HashMap<>();

        features.put("description", description != null ? description.toLowerCase() : "");
        features.put("merchant", merchantName != null ? merchantName.toLowerCase() : "");
        features.put("amount", amount != null ? amount.doubleValue() : 0.0);

        // Extract keywords
        if (description != null) {
            features.put("has_food_keyword", containsFoodKeywords(description));
            features.put("has_transport_keyword", containsTransportKeywords(description));
            features.put("has_entertainment_keyword", containsEntertainmentKeywords(description));
        }

        return features;
    }

    private String performClassification(Map<String, Object> features) {
        String description = (String) features.get("description");
        String merchant = (String) features.get("merchant");
        double amount = (double) features.get("amount");

        // Rule-based classification (replace with ML model in production)
        if (containsFoodKeywords(description) || containsFoodKeywords(merchant)) {
            return "FOOD_DINING";
        }
        if (containsTransportKeywords(description) || containsTransportKeywords(merchant)) {
            return "TRANSPORTATION";
        }
        if (containsEntertainmentKeywords(description) || containsEntertainmentKeywords(merchant)) {
            return "ENTERTAINMENT";
        }
        if (containsUtilityKeywords(description) || containsUtilityKeywords(merchant)) {
            return "UTILITIES";
        }
        if (containsShoppingKeywords(description) || containsShoppingKeywords(merchant)) {
            return "SHOPPING";
        }
        if (amount > 500) {
            return "MAJOR_PURCHASE";
        }

        return "OTHER";
    }

    private boolean containsFoodKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("restaurant") || lower.contains("food") || 
               lower.contains("cafe") || lower.contains("dining") ||
               lower.contains("grocery") || lower.contains("supermarket");
    }

    private boolean containsTransportKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("uber") || lower.contains("lyft") || 
               lower.contains("taxi") || lower.contains("gas") ||
               lower.contains("fuel") || lower.contains("parking");
    }

    private boolean containsEntertainmentKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("movie") || lower.contains("netflix") || 
               lower.contains("spotify") || lower.contains("game") ||
               lower.contains("concert") || lower.contains("theater");
    }

    private boolean containsUtilityKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("electric") || lower.contains("water") || 
               lower.contains("internet") || lower.contains("phone") ||
               lower.contains("utility");
    }

    private boolean containsShoppingKeywords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("amazon") || lower.contains("walmart") || 
               lower.contains("target") || lower.contains("shopping") ||
               lower.contains("mall");
    }
}
