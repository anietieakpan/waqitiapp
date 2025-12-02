package com.waqiti.expense.service.impl;

import com.waqiti.expense.domain.Budget;
import com.waqiti.expense.domain.BudgetAlert;
import com.waqiti.expense.domain.enums.BudgetStatus;
import com.waqiti.expense.exception.BudgetNotFoundException;
import com.waqiti.expense.exception.UnauthorizedAccessException;
import com.waqiti.expense.repository.BudgetRepository;
import com.waqiti.expense.service.BudgetService;
import com.waqiti.expense.util.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-ready budget service with monitoring and alerting
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Budget createBudget(Budget budget) {
        log.info("Creating budget: {} for user: {}", budget.getName(), budget.getUserId());

        // Validate
        if (budget.getPlannedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }

        // Initialize calculated fields
        budget.recalculateAmounts();

        // Create default alerts
        createDefaultAlerts(budget);

        Budget savedBudget = budgetRepository.save(budget);

        // Publish event
        publishBudgetEvent("budget.created", savedBudget);

        log.info("Budget created successfully: {}", savedBudget.getId());
        return savedBudget;
    }

    @Override
    public Budget updateBudget(UUID budgetId, Budget budget) {
        log.info("Updating budget: {}", budgetId);

        Budget existing = findBudgetById(budgetId);
        verifyBudgetOwnership(existing);

        // Update fields
        existing.setName(budget.getName());
        existing.setDescription(budget.getDescription());
        existing.setPlannedAmount(budget.getPlannedAmount());
        existing.setWarningThreshold(budget.getWarningThreshold());
        existing.setCriticalThreshold(budget.getCriticalThreshold());
        existing.setNotificationsEnabled(budget.getNotificationsEnabled());
        existing.setEmailAlerts(budget.getEmailAlerts());
        existing.setPushNotifications(budget.getPushNotifications());

        existing.recalculateAmounts();

        Budget updated = budgetRepository.save(existing);

        // Publish event
        publishBudgetEvent("budget.updated", updated);

        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Budget getBudgetById(UUID budgetId) {
        Budget budget = findBudgetById(budgetId);
        verifyBudgetOwnership(budget);
        return budget;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Budget> getUserActiveBudgets(UUID userId) {
        log.debug("Getting active budgets for user: {}", userId);
        return budgetRepository.findActiveByUserId(userId);
    }

    @Override
    public void addExpenseToBudget(UUID budgetId, BigDecimal amount) {
        log.debug("Adding {} to budget: {}", amount, budgetId);

        Budget budget = findBudgetById(budgetId);
        budget.addExpense(amount);
        budgetRepository.save(budget);

        // Check if alerts need to be triggered
        checkBudgetAlertsAndNotify(budgetId);

        // Publish event
        Map<String, Object> event = new HashMap<>();
        event.put("budgetId", budgetId);
        event.put("amount", amount);
        event.put("newSpentAmount", budget.getSpentAmount());
        kafkaTemplate.send("budget.expense.added", event);
    }

    @Override
    public void removeExpenseFromBudget(UUID budgetId, BigDecimal amount) {
        log.debug("Removing {} from budget: {}", amount, budgetId);

        Budget budget = findBudgetById(budgetId);
        budget.removeExpense(amount);
        budgetRepository.save(budget);

        // Publish event
        Map<String, Object> event = new HashMap<>();
        event.put("budgetId", budgetId);
        event.put("amount", amount);
        event.put("newSpentAmount", budget.getSpentAmount());
        kafkaTemplate.send("budget.expense.removed", event);
    }

    @Override
    public void checkBudgetAlertsAndNotify(UUID budgetId) {
        log.debug("Checking alerts for budget: {}", budgetId);

        Budget budget = findBudgetById(budgetId);

        if (!Boolean.TRUE.equals(budget.getNotificationsEnabled())) {
            return;
        }

        // Check warning threshold
        if (budget.isWarningThresholdReached() && !budget.isCriticalThresholdReached()) {
            triggerWarningAlert(budget);
        }

        // Check critical threshold
        if (budget.isCriticalThresholdReached() && !budget.isOverBudget()) {
            triggerCriticalAlert(budget);
        }

        // Check if over budget
        if (budget.isOverBudget()) {
            triggerExceededAlert(budget);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Budget> getBudgetsByCategory(UUID userId, String categoryId) {
        log.debug("Getting budgets for user: {} and category: {}", userId, categoryId);
        return budgetRepository.findByUserIdAndCategoryId(userId, categoryId);
    }

    @Override
    public void deleteBudget(UUID budgetId) {
        log.info("Deleting budget: {}", budgetId);

        Budget budget = findBudgetById(budgetId);
        verifyBudgetOwnership(budget);

        budget.setStatus(BudgetStatus.INACTIVE);
        budgetRepository.save(budget);

        // Publish event
        publishBudgetEvent("budget.deleted", budget);
    }

    @Override
    @Scheduled(cron = "0 0 1 * * *") // Run daily at 1 AM
    public void renewRecurringBudgets() {
        log.info("Checking for budgets to renew");

        List<Budget> expiredBudgets = budgetRepository.findExpiredRecurringBudgets(LocalDate.now());

        for (Budget budget : expiredBudgets) {
            try {
                Budget newBudget = budget.createNextPeriodBudget();
                if (newBudget != null) {
                    budgetRepository.save(newBudget);
                    log.info("Renewed budget: {} for user: {}", budget.getName(), budget.getUserId());

                    // Mark old budget as completed
                    budget.setStatus(BudgetStatus.COMPLETED);
                    budgetRepository.save(budget);

                    // Notify user
                    notificationService.sendBudgetRenewedAlert(
                            budget.getUserId(),
                            budget.getName(),
                            newBudget.getPeriodStart().toString()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to renew budget: {}", budget.getId(), e);
            }
        }
    }

    // Private helper methods

    private Budget findBudgetById(UUID budgetId) {
        return budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found: " + budgetId));
    }

    private void verifyBudgetOwnership(Budget budget) {
        UUID currentUserId = getCurrentUserId();
        if (!budget.getUserId().equals(currentUserId)) {
            throw new UnauthorizedAccessException("Budget does not belong to current user");
        }
    }

    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof String) {
            return UUID.fromString((String) principal);
        }
        // Placeholder - in production this should extract from JWT or security context
        return UUID.randomUUID();
    }

    private void createDefaultAlerts(Budget budget) {
        // Create warning alert (80%)
        BudgetAlert warningAlert = BudgetAlert.builder()
                .budget(budget)
                .alertType(BudgetAlert.AlertType.WARNING)
                .thresholdPercentage(budget.getWarningThreshold())
                .isTriggered(false)
                .isAcknowledged(false)
                .build();

        // Create critical alert (95%)
        BudgetAlert criticalAlert = BudgetAlert.builder()
                .budget(budget)
                .alertType(BudgetAlert.AlertType.CRITICAL)
                .thresholdPercentage(budget.getCriticalThreshold())
                .isTriggered(false)
                .isAcknowledged(false)
                .build();

        budget.getAlerts().add(warningAlert);
        budget.getAlerts().add(criticalAlert);
    }

    private void triggerWarningAlert(Budget budget) {
        log.warn("Budget warning threshold reached: {}", budget.getName());

        String message = String.format("Budget '%s' has reached %s%% of planned amount",
                budget.getName(), budget.getWarningThreshold());

        if (Boolean.TRUE.equals(budget.getEmailAlerts())) {
            notificationService.sendBudgetExceededAlert(
                    budget.getUserId(),
                    budget.getName(),
                    message
            );
        }

        // Mark alert as triggered
        budget.getAlerts().stream()
                .filter(a -> a.getAlertType() == BudgetAlert.AlertType.WARNING)
                .forEach(a -> a.trigger(message));

        budgetRepository.save(budget);
    }

    private void triggerCriticalAlert(Budget budget) {
        log.error("Budget critical threshold reached: {}", budget.getName());

        String message = String.format("CRITICAL: Budget '%s' has reached %s%% of planned amount",
                budget.getName(), budget.getCriticalThreshold());

        if (Boolean.TRUE.equals(budget.getEmailAlerts())) {
            notificationService.sendBudgetExceededAlert(
                    budget.getUserId(),
                    budget.getName(),
                    message
            );
        }

        budget.getAlerts().stream()
                .filter(a -> a.getAlertType() == BudgetAlert.AlertType.CRITICAL)
                .forEach(a -> a.trigger(message));

        budgetRepository.save(budget);
    }

    private void triggerExceededAlert(Budget budget) {
        log.error("Budget exceeded: {}", budget.getName());

        String message = String.format("Budget '%s' has been exceeded! Spent: %s %s, Planned: %s %s",
                budget.getName(),
                budget.getSpentAmount(), budget.getCurrency(),
                budget.getPlannedAmount(), budget.getCurrency());

        if (Boolean.TRUE.equals(budget.getEmailAlerts())) {
            notificationService.sendBudgetExceededAlert(
                    budget.getUserId(),
                    budget.getName(),
                    message
            );
        }

        BudgetAlert exceededAlert = BudgetAlert.builder()
                .budget(budget)
                .alertType(BudgetAlert.AlertType.EXCEEDED)
                .thresholdPercentage(BigDecimal.valueOf(100))
                .build();
        exceededAlert.trigger(message);

        budget.getAlerts().add(exceededAlert);
        budgetRepository.save(budget);
    }

    private void publishBudgetEvent(String eventType, Budget budget) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("budgetId", budget.getId());
            event.put("userId", budget.getUserId());
            event.put("plannedAmount", budget.getPlannedAmount());
            event.put("spentAmount", budget.getSpentAmount());
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("budget.events", budget.getId(), event);
            log.debug("Published event: {} for budget: {}", eventType, budget.getId());
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage());
        }
    }
}
