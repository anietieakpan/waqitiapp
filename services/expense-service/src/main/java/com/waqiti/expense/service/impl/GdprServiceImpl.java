package com.waqiti.expense.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.repository.BudgetRepository;
import com.waqiti.expense.repository.ExpenseRepository;
import com.waqiti.expense.service.GdprService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GDPR compliance service implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GdprServiceImpl implements GdprService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportUserData() {
        UUID userId = getCurrentUserId();
        log.info("Exporting data for user: {}", userId);

        try {
            Map<String, Object> userData = new HashMap<>();
            userData.put("exportDate", LocalDateTime.now());
            userData.put("userId", userId.toString());

            // Export expenses
            List<Expense> expenses = expenseRepository.findAll().stream()
                    .filter(e -> e.getUserId().equals(userId))
                    .toList();
            userData.put("expenses", expenses);
            userData.put("expenseCount", expenses.size());

            // Export budgets
            var budgets = budgetRepository.findActiveByUserId(userId);
            userData.put("budgets", budgets);
            userData.put("budgetCount", budgets.size());

            return objectMapper.writeValueAsBytes(userData);
        } catch (Exception e) {
            log.error("Failed to export user data", e);
            throw new RuntimeException("Data export failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteUserData() {
        UUID userId = getCurrentUserId();
        log.warn("Deleting all data for user: {}", userId);

        // Delete expenses
        List<Expense> expenses = expenseRepository.findAll().stream()
                .filter(e -> e.getUserId().equals(userId))
                .toList();
        expenseRepository.deleteAll(expenses);
        log.info("Deleted {} expenses for user: {}", expenses.size(), userId);

        // Delete budgets
        var budgets = budgetRepository.findActiveByUserId(userId);
        budgetRepository.deleteAll(budgets);
        log.info("Deleted {} budgets for user: {}", budgets.size(), userId);

        // TODO: Delete related data from other tables
        // - Expense attachments
        // - Expense tags
        // - Budget alerts
        // - Any audit logs (consider retention requirements)

        log.info("Data deletion completed for user: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getDataSummary() {
        UUID userId = getCurrentUserId();
        log.info("Getting data summary for user: {}", userId);

        Map<String, Object> summary = new HashMap<>();

        // Count expenses
        long expenseCount = expenseRepository.findAll().stream()
                .filter(e -> e.getUserId().equals(userId))
                .count();

        // Count budgets
        long budgetCount = budgetRepository.findActiveByUserId(userId).size();

        summary.put("totalExpenses", expenseCount);
        summary.put("totalBudgets", budgetCount);
        summary.put("dataRetentionPeriod", "7 years");
        summary.put("lastAccessed", LocalDateTime.now());

        return summary;
    }

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof String) {
            return UUID.fromString((String) principal);
        }
        // Placeholder - in production this should extract from JWT or security context
        return UUID.randomUUID();
    }
}
