package com.waqiti.common.event;

import lombok.Getter;

import java.util.Map;

/**
 * Domain event for recurring payment operations
 */
@Getter
public class RecurringPaymentEvent extends AbstractDomainEvent {
    
    private final String action;
    private final String recurringPaymentId;
    private final String userId;
    private final Map<String, Object> payload;
    
    private RecurringPaymentEvent(String action, String recurringPaymentId, String userId, Map<String, Object> payload) {
        super();
        this.action = action;
        this.recurringPaymentId = recurringPaymentId;
        this.userId = userId;
        this.payload = payload;
    }
    
    @Override
    public String getTopic() {
        return "recurring-payment-events";
    }
    
    @Override
    public String getEventType() {
        return "RecurringPayment" + action;
    }
    
    // Factory methods for different recurring payment events
    
    public static RecurringPaymentEvent recurringCreated(Object recurring) {
        return new RecurringPaymentEvent(
            "Created",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent recurringUpdated(Object recurring) {
        return new RecurringPaymentEvent(
            "Updated",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent recurringPaused(Object recurring) {
        return new RecurringPaymentEvent(
            "Paused",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent recurringResumed(Object recurring) {
        return new RecurringPaymentEvent(
            "Resumed",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent recurringCancelled(Object recurring) {
        return new RecurringPaymentEvent(
            "Cancelled",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent recurringCompleted(Object recurring) {
        return new RecurringPaymentEvent(
            "Completed",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring)
        );
    }
    
    public static RecurringPaymentEvent executionCompleted(Object recurring, Object execution) {
        return new RecurringPaymentEvent(
            "ExecutionCompleted",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring, "execution", execution)
        );
    }
    
    public static RecurringPaymentEvent executionFailed(Object recurring, Object execution) {
        return new RecurringPaymentEvent(
            "ExecutionFailed",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring, "execution", execution)
        );
    }
    
    public static RecurringPaymentEvent fraudDetected(Object recurring, Object execution, Object fraudResult) {
        return new RecurringPaymentEvent(
            "FraudDetected",
            extractId(recurring),
            extractUserId(recurring),
            Map.of("recurring", recurring, "execution", execution, "fraudResult", fraudResult)
        );
    }
    
    // Helper methods to extract common fields
    private static String extractId(Object recurring) {
        try {
            return (String) recurring.getClass().getMethod("getId").invoke(recurring);
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private static String extractUserId(Object recurring) {
        try {
            return (String) recurring.getClass().getMethod("getUserId").invoke(recurring);
        } catch (Exception e) {
            return "unknown";
        }
    }
}