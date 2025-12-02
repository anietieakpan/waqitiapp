package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Budget alert events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudgetAlertEvent extends BusinessEvent {
    
    private String budgetId;
    private String budgetName;
    private String departmentId;
    private String departmentName;
    private String categoryId;
    private String categoryName;
    private String alertType; // THRESHOLD_WARNING, THRESHOLD_EXCEEDED, BUDGET_EXHAUSTED, FORECAST_OVERRUN
    private BigDecimal budgetAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal thresholdPercentage;
    private String currency;
    private String period; // MONTHLY, QUARTERLY, ANNUAL
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String managerId;
    private String managerEmail;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String actionRequired;
    private BigDecimal projectedOverrun;
    private LocalDateTime alertTriggeredAt;
    
    public BudgetAlertEvent() {
        super("BUDGET_ALERT");
    }
    
    public static BudgetAlertEvent thresholdWarning(String businessId, String budgetId, String budgetName, 
                                                  BigDecimal budgetAmount, BigDecimal spentAmount, 
                                                  BigDecimal thresholdPercentage, String managerId, String currency) {
        BudgetAlertEvent event = new BudgetAlertEvent();
        event.setBusinessId(businessId);
        event.setBudgetId(budgetId);
        event.setBudgetName(budgetName);
        event.setAlertType("THRESHOLD_WARNING");
        event.setBudgetAmount(budgetAmount);
        event.setSpentAmount(spentAmount);
        event.setRemainingAmount(budgetAmount.subtract(spentAmount));
        event.setThresholdPercentage(thresholdPercentage);
        event.setCurrency(currency);
        event.setManagerId(managerId);
        event.setSeverity("MEDIUM");
        event.setActionRequired("Monitor spending closely");
        event.setAlertTriggeredAt(LocalDateTime.now());
        return event;
    }
    
    public static BudgetAlertEvent thresholdExceeded(String businessId, String budgetId, String budgetName, 
                                                   BigDecimal budgetAmount, BigDecimal spentAmount, 
                                                   String managerId, String currency) {
        BudgetAlertEvent event = new BudgetAlertEvent();
        event.setBusinessId(businessId);
        event.setBudgetId(budgetId);
        event.setBudgetName(budgetName);
        event.setAlertType("THRESHOLD_EXCEEDED");
        event.setBudgetAmount(budgetAmount);
        event.setSpentAmount(spentAmount);
        event.setRemainingAmount(budgetAmount.subtract(spentAmount));
        event.setCurrency(currency);
        event.setManagerId(managerId);
        event.setSeverity("HIGH");
        event.setActionRequired("Review and approve additional spending");
        event.setAlertTriggeredAt(LocalDateTime.now());
        return event;
    }
    
    public static BudgetAlertEvent budgetExhausted(String businessId, String budgetId, String budgetName, 
                                                 BigDecimal budgetAmount, String managerId, String currency) {
        BudgetAlertEvent event = new BudgetAlertEvent();
        event.setBusinessId(businessId);
        event.setBudgetId(budgetId);
        event.setBudgetName(budgetName);
        event.setAlertType("BUDGET_EXHAUSTED");
        event.setBudgetAmount(budgetAmount);
        event.setSpentAmount(budgetAmount);
        event.setRemainingAmount(BigDecimal.ZERO);
        event.setCurrency(currency);
        event.setManagerId(managerId);
        event.setSeverity("CRITICAL");
        event.setActionRequired("Budget exhausted - no further spending allowed without approval");
        event.setAlertTriggeredAt(LocalDateTime.now());
        return event;
    }
    
    public static BudgetAlertEvent forecastOverrun(String businessId, String budgetId, String budgetName, 
                                                 BigDecimal budgetAmount, BigDecimal currentSpent, 
                                                 BigDecimal projectedOverrun, String managerId, String currency, 
                                                 LocalDateTime periodEnd) {
        BudgetAlertEvent event = new BudgetAlertEvent();
        event.setBusinessId(businessId);
        event.setBudgetId(budgetId);
        event.setBudgetName(budgetName);
        event.setAlertType("FORECAST_OVERRUN");
        event.setBudgetAmount(budgetAmount);
        event.setSpentAmount(currentSpent);
        event.setProjectedOverrun(projectedOverrun);
        event.setCurrency(currency);
        event.setManagerId(managerId);
        event.setPeriodEnd(periodEnd);
        event.setSeverity("HIGH");
        event.setActionRequired("Review spending forecast and take corrective action");
        event.setAlertTriggeredAt(LocalDateTime.now());
        return event;
    }
    
    public static BudgetAlertEvent departmentBudgetAlert(String businessId, String departmentId, String departmentName, 
                                                       BigDecimal totalBudget, BigDecimal totalSpent, 
                                                       String managerId, String currency) {
        BudgetAlertEvent event = new BudgetAlertEvent();
        event.setBusinessId(businessId);
        event.setDepartmentId(departmentId);
        event.setDepartmentName(departmentName);
        event.setAlertType("THRESHOLD_WARNING");
        event.setBudgetAmount(totalBudget);
        event.setSpentAmount(totalSpent);
        event.setRemainingAmount(totalBudget.subtract(totalSpent));
        event.setCurrency(currency);
        event.setManagerId(managerId);
        event.setSeverity("MEDIUM");
        event.setActionRequired("Review department spending across all categories");
        event.setAlertTriggeredAt(LocalDateTime.now());
        return event;
    }
}