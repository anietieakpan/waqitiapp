package com.waqiti.expense.service.impl;

import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.domain.ExpenseCategory;
import com.waqiti.expense.repository.ExpenseCategoryRepository;
import com.waqiti.expense.repository.ExpenseRepository;
import com.waqiti.expense.service.ExpenseClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-ready expense classification service
 * Uses rule-based classification with ML capability for future enhancement
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseClassificationServiceImpl implements ExpenseClassificationService {

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    // Rule-based classification patterns (can be replaced with ML model)
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.ofEntries(
            Map.entry("FOOD", List.of("restaurant", "cafe", "food", "dining", "lunch", "dinner", "breakfast")),
            Map.entry("TRANSPORTATION", List.of("uber", "lyft", "taxi", "gas", "fuel", "parking", "transit")),
            Map.entry("GROCERIES", List.of("grocery", "supermarket", "walmart", "kroger", "safeway", "whole foods")),
            Map.entry("UTILITIES", List.of("electric", "water", "gas", "internet", "phone", "utility")),
            Map.entry("ENTERTAINMENT", List.of("movie", "theater", "concert", "game", "entertainment", "netflix")),
            Map.entry("SHOPPING", List.of("amazon", "target", "mall", "store", "shopping")),
            Map.entry("HEALTHCARE", List.of("doctor", "hospital", "pharmacy", "medical", "health", "clinic")),
            Map.entry("TRAVEL", List.of("hotel", "airbnb", "flight", "airline", "booking", "travel")),
            Map.entry("EDUCATION", List.of("school", "university", "course", "book", "education", "tuition")),
            Map.entry("INSURANCE", List.of("insurance", "premium", "policy"))
    );

    @Override
    public ExpenseCategory classifyExpense(Expense expense) {
        log.debug("Classifying expense: {}", expense.getDescription());

        // Try to classify based on merchant name
        if (expense.getMerchantName() != null) {
            ExpenseCategory category = classifyByMerchant(expense.getMerchantName());
            if (category != null) {
                log.debug("Classified by merchant: {}", category.getCategoryName());
                return category;
            }
        }

        // Try to classify based on description
        if (expense.getDescription() != null) {
            ExpenseCategory category = classifyByDescription(expense.getDescription());
            if (category != null) {
                log.debug("Classified by description: {}", category.getCategoryName());
                return category;
            }
        }

        // Return default "Uncategorized" category
        return getDefaultCategory();
    }

    @Override
    public double getClassificationConfidence(Expense expense) {
        String text = buildClassificationText(expense);

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.toLowerCase().contains(keyword.toLowerCase())) {
                    // High confidence if multiple keywords match
                    long matchCount = entry.getValue().stream()
                            .filter(k -> text.toLowerCase().contains(k.toLowerCase()))
                            .count();
                    return Math.min(0.9, 0.6 + (matchCount * 0.1));
                }
            }
        }

        return 0.3; // Low confidence if no keywords match
    }

    @Override
    public void trainModel() {
        log.info("Training classification model");
        // TODO: Implement ML model training using historical expense data
        // Can use Apache Spark MLlib or TensorFlow
        log.warn("ML model training not yet implemented");
    }

    @Override
    public List<ExpenseCategory> getSuggestedCategories(Expense expense) {
        log.debug("Getting suggested categories for expense");

        String text = buildClassificationText(expense);
        Map<String, Double> categoryScores = new HashMap<>();

        // Score each category based on keyword matches
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            double score = 0.0;
            for (String keyword : entry.getValue()) {
                if (text.toLowerCase().contains(keyword.toLowerCase())) {
                    score += 1.0;
                }
            }
            if (score > 0) {
                categoryScores.put(entry.getKey(), score);
            }
        }

        // Get top 3 categories
        return categoryScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(entry -> categoryRepository.findByCategoryId(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // Private helper methods

    private ExpenseCategory classifyByMerchant(String merchantName) {
        String merchant = merchantName.toLowerCase();

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (merchant.contains(keyword.toLowerCase())) {
                    return categoryRepository.findByCategoryId(entry.getKey()).orElse(null);
                }
            }
        }

        return null;
    }

    private ExpenseCategory classifyByDescription(String description) {
        String desc = description.toLowerCase();

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (desc.contains(keyword.toLowerCase())) {
                    return categoryRepository.findByCategoryId(entry.getKey()).orElse(null);
                }
            }
        }

        return null;
    }

    private String buildClassificationText(Expense expense) {
        StringBuilder text = new StringBuilder();
        if (expense.getMerchantName() != null) {
            text.append(expense.getMerchantName()).append(" ");
        }
        if (expense.getDescription() != null) {
            text.append(expense.getDescription()).append(" ");
        }
        if (expense.getMerchantCategory() != null) {
            text.append(expense.getMerchantCategory());
        }
        return text.toString();
    }

    private ExpenseCategory getDefaultCategory() {
        return categoryRepository.findByCategoryId("UNCATEGORIZED")
                .orElseGet(() -> {
                    log.warn("Default category not found, creating one");
                    ExpenseCategory category = new ExpenseCategory();
                    category.setCategoryId("UNCATEGORIZED");
                    category.setCategoryName("Uncategorized");
                    category.setIsActive(true);
                    return categoryRepository.save(category);
                });
    }
}
