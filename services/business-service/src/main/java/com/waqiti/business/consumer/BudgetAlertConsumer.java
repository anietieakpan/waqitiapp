package com.waqiti.business.consumer;

import com.waqiti.business.event.BudgetAlertEvent;
import com.waqiti.business.service.BusinessAnalyticsService;
import com.waqiti.business.service.BusinessNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for budget alert events
 * Handles budget monitoring, threshold alerts, and budget management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetAlertConsumer {

    private final BusinessAnalyticsService analyticsService;
    private final BusinessNotificationService notificationService;

    @KafkaListener(topics = "budget-alerts", groupId = "budget-alert-processor")
    public void processBudgetAlertEvent(@Payload BudgetAlertEvent event,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment) {
        try {
            log.info("Processing budget alert event: {} type: {} budget: {} severity: {}", 
                    event.getEventId(), event.getAlertType(), event.getBudgetName(), event.getSeverity());
            
            // Validate event
            validateBudgetAlertEvent(event);
            
            // Process based on alert type
            switch (event.getAlertType()) {
                case "THRESHOLD_WARNING" -> handleThresholdWarning(event);
                case "THRESHOLD_EXCEEDED" -> handleThresholdExceeded(event);
                case "BUDGET_EXHAUSTED" -> handleBudgetExhausted(event);
                case "FORECAST_OVERRUN" -> handleForecastOverrun(event);
                default -> {
                    log.warn("Unknown budget alert type: {} for event: {}", event.getAlertType(), event.getEventId());
                    // Don't throw exception for unknown types to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed budget alert event: {} type: {}", 
                    event.getEventId(), event.getAlertType());
            
        } catch (Exception e) {
            log.error("Failed to process budget alert event: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Budget alert event processing failed", e);
        }
    }

    private void validateBudgetAlertEvent(BudgetAlertEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for budget alert");
        }
        
        if (event.getBudgetId() == null || event.getBudgetId().trim().isEmpty()) {
            throw new IllegalArgumentException("Budget ID is required for budget alert");
        }
        
        if (event.getAlertType() == null || event.getAlertType().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert type is required for budget alert");
        }
    }

    private void handleThresholdWarning(BudgetAlertEvent event) {
        try {
            log.info("Handling threshold warning: {} budget: {} spent: {} of {} ({}%)", 
                    event.getEventId(), event.getBudgetName(), event.getSpentAmount(), 
                    event.getBudgetAmount(), event.getThresholdPercentage());
            
            // Record budget alert
            analyticsService.recordBudgetAlert(
                event.getBusinessId(),
                event.getBudgetId(),
                "THRESHOLD_WARNING",
                event.getSpentAmount(),
                event.getBudgetAmount(),
                event.getThresholdPercentage()
            );
            
            // Send warning notification to budget manager
            notificationService.sendBudgetAlertEmail(
                event.getBusinessId(),
                event.getManagerEmail(),
                "Budget Threshold Warning",
                String.format("Budget '%s' has reached %s%% of allocated amount (%s %s of %s %s spent)", 
                            event.getBudgetName(), event.getThresholdPercentage(), 
                            event.getSpentAmount(), event.getCurrency(),
                            event.getBudgetAmount(), event.getCurrency()),
                event.getBudgetId(),
                event.getBudgetAmount(),
                event.getSpentAmount(),
                event.getCurrency()
            );
            
            // Send in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getManagerId(),
                "BUDGET_WARNING",
                "Budget Threshold Warning",
                String.format("Budget '%s' is approaching its limit", event.getBudgetName()),
                "MEDIUM",
                event.getBudgetId()
            );
            
            // Update budget monitoring dashboard
            analyticsService.updateBudgetDashboard(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getSpentAmount(),
                event.getRemainingAmount(),
                "WARNING"
            );
            
            // Check if spending trend requires attention
            analyticsService.analyzeSpendingTrend(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getSpentAmount(),
                event.getPeriodStart(),
                event.getPeriodEnd()
            );
            
            log.info("Threshold warning processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle threshold warning for budget {}: {}", 
                    event.getBudgetId(), e.getMessage(), e);
            throw new RuntimeException("Threshold warning processing failed", e);
        }
    }

    private void handleThresholdExceeded(BudgetAlertEvent event) {
        try {
            log.info("Handling threshold exceeded: {} budget: {} spent: {} exceeds budget: {}", 
                    event.getEventId(), event.getBudgetName(), event.getSpentAmount(), event.getBudgetAmount());
            
            // Record critical budget alert
            analyticsService.recordBudgetAlert(
                event.getBusinessId(),
                event.getBudgetId(),
                "THRESHOLD_EXCEEDED",
                event.getSpentAmount(),
                event.getBudgetAmount(),
                event.getThresholdPercentage()
            );
            
            // Send critical notification to manager
            notificationService.sendCriticalBudgetAlert(
                event.getBusinessId(),
                event.getManagerId(),
                event.getBudgetId(),
                "THRESHOLD_EXCEEDED",
                event.getBudgetAmount(),
                event.getSpentAmount(),
                event.getCurrency()
            );
            
            // Send urgent in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getManagerId(),
                "BUDGET_EXCEEDED",
                "Budget Threshold Exceeded",
                String.format("Budget '%s' has exceeded its allocated amount. Immediate action required.", 
                            event.getBudgetName()),
                "HIGH",
                event.getBudgetId()
            );
            
            // Trigger spending freeze if configured
            analyticsService.checkSpendingFreezePolicy(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getSpentAmount(),
                event.getBudgetAmount()
            );
            
            // Escalate to finance team
            analyticsService.escalateToFinanceTeam(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getBudgetName(),
                event.getSpentAmount(),
                event.getBudgetAmount(),
                event.getCurrency()
            );
            
            // Update budget status to require approval
            analyticsService.requireApprovalForFutureSpending(
                event.getBusinessId(),
                event.getBudgetId(),
                "THRESHOLD_EXCEEDED"
            );
            
            log.info("Threshold exceeded processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle threshold exceeded for budget {}: {}", 
                    event.getBudgetId(), e.getMessage(), e);
            throw new RuntimeException("Threshold exceeded processing failed", e);
        }
    }

    private void handleBudgetExhausted(BudgetAlertEvent event) {
        try {
            log.info("Handling budget exhausted: {} budget: {} fully spent: {}", 
                    event.getEventId(), event.getBudgetName(), event.getBudgetAmount());
            
            // Record critical budget exhaustion
            analyticsService.recordBudgetAlert(
                event.getBusinessId(),
                event.getBudgetId(),
                "BUDGET_EXHAUSTED",
                event.getSpentAmount(),
                event.getBudgetAmount(),
                null
            );
            
            // Immediately freeze all spending for this budget
            analyticsService.freezeBudgetSpending(
                event.getBusinessId(),
                event.getBudgetId(),
                "BUDGET_EXHAUSTED"
            );
            
            // Send critical alert to all stakeholders
            notificationService.sendCriticalBudgetAlert(
                event.getBusinessId(),
                event.getManagerId(),
                event.getBudgetId(),
                "BUDGET_EXHAUSTED",
                event.getBudgetAmount(),
                event.getSpentAmount(),
                event.getCurrency()
            );
            
            // Send urgent notification to finance team
            analyticsService.notifyFinanceTeamBudgetExhausted(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getBudgetName(),
                event.getBudgetAmount(),
                event.getCurrency()
            );
            
            // Create emergency budget request workflow
            analyticsService.createEmergencyBudgetRequest(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getBudgetName(),
                event.getDepartmentId(),
                event.getManagerId()
            );
            
            // Update all pending expense approvals
            analyticsService.flagPendingExpensesForBudgetExhaustion(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getCategoryId()
            );
            
            // Generate detailed budget exhaustion report
            analyticsService.generateBudgetExhaustionReport(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getPeriodStart(),
                event.getPeriodEnd()
            );
            
            log.info("Budget exhausted processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle budget exhausted for budget {}: {}", 
                    event.getBudgetId(), e.getMessage(), e);
            throw new RuntimeException("Budget exhausted processing failed", e);
        }
    }

    private void handleForecastOverrun(BudgetAlertEvent event) {
        try {
            log.info("Handling forecast overrun: {} budget: {} projected overrun: {} period end: {}", 
                    event.getEventId(), event.getBudgetName(), event.getProjectedOverrun(), event.getPeriodEnd());
            
            // Record forecast overrun alert
            analyticsService.recordBudgetAlert(
                event.getBusinessId(),
                event.getBudgetId(),
                "FORECAST_OVERRUN",
                event.getSpentAmount(),
                event.getBudgetAmount(),
                null
            );
            
            // Generate detailed forecast analysis
            analyticsService.generateForecastAnalysis(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getSpentAmount(),
                event.getBudgetAmount(),
                event.getProjectedOverrun(),
                event.getPeriodEnd()
            );
            
            // Send forecast alert to manager
            notificationService.sendBudgetAlertEmail(
                event.getBusinessId(),
                event.getManagerEmail(),
                "Budget Forecast Overrun Alert",
                String.format("Budget '%s' is projected to overrun by %s %s by period end (%s). " +
                            "Current spending: %s %s of %s %s allocated.", 
                            event.getBudgetName(), event.getProjectedOverrun(), event.getCurrency(),
                            event.getPeriodEnd(), event.getSpentAmount(), event.getCurrency(),
                            event.getBudgetAmount(), event.getCurrency()),
                event.getBudgetId(),
                event.getBudgetAmount(),
                event.getSpentAmount(),
                event.getCurrency()
            );
            
            // Create corrective action plan
            analyticsService.createCorrectiveActionPlan(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getProjectedOverrun(),
                event.getPeriodEnd()
            );
            
            // Send recommendations to stakeholders
            analyticsService.sendSpendingRecommendations(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getManagerId(),
                event.getProjectedOverrun(),
                event.getPeriodEnd()
            );
            
            // Update budget controls
            analyticsService.implementPreventiveControls(
                event.getBusinessId(),
                event.getBudgetId(),
                event.getProjectedOverrun()
            );
            
            log.info("Forecast overrun processed successfully: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to handle forecast overrun for budget {}: {}", 
                    event.getBudgetId(), e.getMessage(), e);
            throw new RuntimeException("Forecast overrun processing failed", e);
        }
    }
}