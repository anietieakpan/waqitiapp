package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event for account monitoring activities
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountMonitoringEvent extends UserEvent {
    
    private String monitoringType; // TRANSACTION_PATTERN, LOGIN_PATTERN, RISK_ASSESSMENT, COMPLIANCE_CHECK, FRAUD_DETECTION
    private String monitoringLevel; // STANDARD, ENHANCED, CRITICAL
    private String alertLevel; // INFO, WARNING, ALERT, CRITICAL
    private String anomalyDetected;
    private BigDecimal riskScore;
    private String riskCategory;
    private List<String> suspiciousIndicators;
    private Map<String, Object> monitoringMetrics;
    private LocalDateTime detectionTime;
    private boolean actionRequired;
    private String recommendedAction;
    private boolean automaticActionTaken;
    private String actionTaken;
    private String reviewStatus;
    private String assignedTo;
    private Map<String, String> contextData;
    
    public AccountMonitoringEvent() {
        super("ACCOUNT_MONITORING");
    }
    
    public static AccountMonitoringEvent suspiciousActivity(String userId, String anomaly, BigDecimal riskScore, 
                                                          List<String> indicators) {
        AccountMonitoringEvent event = new AccountMonitoringEvent();
        event.setUserId(userId);
        event.setMonitoringType("FRAUD_DETECTION");
        event.setMonitoringLevel("ENHANCED");
        event.setAlertLevel("WARNING");
        event.setAnomalyDetected(anomaly);
        event.setRiskScore(riskScore);
        event.setSuspiciousIndicators(indicators);
        event.setDetectionTime(LocalDateTime.now());
        event.setActionRequired(true);
        event.setRecommendedAction("Review account activity");
        return event;
    }
    
    public static AccountMonitoringEvent highRiskTransaction(String userId, String riskCategory, 
                                                           BigDecimal riskScore, String action) {
        AccountMonitoringEvent event = new AccountMonitoringEvent();
        event.setUserId(userId);
        event.setMonitoringType("TRANSACTION_PATTERN");
        event.setMonitoringLevel("CRITICAL");
        event.setAlertLevel("CRITICAL");
        event.setRiskCategory(riskCategory);
        event.setRiskScore(riskScore);
        event.setDetectionTime(LocalDateTime.now());
        event.setActionRequired(true);
        event.setAutomaticActionTaken(true);
        event.setActionTaken(action);
        return event;
    }
    
    public static AccountMonitoringEvent complianceCheck(String userId, String monitoringLevel, 
                                                       Map<String, Object> metrics) {
        AccountMonitoringEvent event = new AccountMonitoringEvent();
        event.setUserId(userId);
        event.setMonitoringType("COMPLIANCE_CHECK");
        event.setMonitoringLevel(monitoringLevel);
        event.setAlertLevel("INFO");
        event.setMonitoringMetrics(metrics);
        event.setDetectionTime(LocalDateTime.now());
        event.setActionRequired(false);
        return event;
    }
}