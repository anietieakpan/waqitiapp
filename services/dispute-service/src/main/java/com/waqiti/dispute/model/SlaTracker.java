package com.waqiti.dispute.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.Duration;

/**
 * SLA (Service Level Agreement) tracking for disputes
 */
@Data
public class SlaTracker {
    
    private String disputeId;
    private LocalDateTime createdAt;
    
    // SLA deadlines
    private LocalDateTime initialResponseDeadline;
    private LocalDateTime resolutionDeadline;
    private LocalDateTime chargebackDeadline;
    private LocalDateTime escalationDeadline;
    
    // SLA status
    private boolean initialResponseMet = false;
    private boolean resolutionMet = false;
    private boolean chargebackResponseMet = false;
    private boolean escalationHandled = false;
    
    // Response times
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolutionResponseAt;
    private LocalDateTime chargebackResponseAt;
    
    // Breach tracking
    private int breachCount = 0;
    private LocalDateTime firstBreachAt;
    private String breachReason;
    
    public SlaTracker(String disputeId) {
        this.disputeId = disputeId;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Check if any SLA is breached
     */
    public boolean hasBreachedSla() {
        LocalDateTime now = LocalDateTime.now();
        
        if (!initialResponseMet && initialResponseDeadline != null && 
            now.isAfter(initialResponseDeadline)) {
            return true;
        }
        
        if (!resolutionMet && resolutionDeadline != null && 
            now.isAfter(resolutionDeadline)) {
            return true;
        }
        
        if (!chargebackResponseMet && chargebackDeadline != null && 
            now.isAfter(chargebackDeadline)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get time remaining for initial response
     */
    public Duration getInitialResponseTimeRemaining() {
        if (initialResponseMet || initialResponseDeadline == null) {
            return Duration.ZERO;
        }
        
        Duration remaining = Duration.between(LocalDateTime.now(), initialResponseDeadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
    
    /**
     * Get time remaining for resolution
     */
    public Duration getResolutionTimeRemaining() {
        if (resolutionMet || resolutionDeadline == null) {
            return Duration.ZERO;
        }
        
        Duration remaining = Duration.between(LocalDateTime.now(), resolutionDeadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
    
    /**
     * Get time remaining for chargeback response
     */
    public Duration getChargebackTimeRemaining() {
        if (chargebackResponseMet || chargebackDeadline == null) {
            return Duration.ZERO;
        }
        
        Duration remaining = Duration.between(LocalDateTime.now(), chargebackDeadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
    
    /**
     * Record initial response
     */
    public void recordInitialResponse() {
        firstResponseAt = LocalDateTime.now();
        initialResponseMet = true;
    }
    
    /**
     * Record resolution response
     */
    public void recordResolution() {
        resolutionResponseAt = LocalDateTime.now();
        resolutionMet = true;
    }
    
    /**
     * Record chargeback response
     */
    public void recordChargebackResponse() {
        chargebackResponseAt = LocalDateTime.now();
        chargebackResponseMet = true;
    }
    
    /**
     * Record SLA breach
     */
    public void recordBreach(String reason) {
        breachCount++;
        if (firstBreachAt == null) {
            firstBreachAt = LocalDateTime.now();
        }
        this.breachReason = reason;
    }
    
    /**
     * Get SLA compliance percentage
     */
    public double getCompliancePercentage() {
        int totalSlas = 0;
        int metSlas = 0;
        
        if (initialResponseDeadline != null) {
            totalSlas++;
            if (initialResponseMet) metSlas++;
        }
        
        if (resolutionDeadline != null) {
            totalSlas++;
            if (resolutionMet) metSlas++;
        }
        
        if (chargebackDeadline != null) {
            totalSlas++;
            if (chargebackResponseMet) metSlas++;
        }
        
        return totalSlas > 0 ? (double) metSlas / totalSlas * 100 : 100.0;
    }
    
    /**
     * Get urgency level based on remaining time
     */
    public UrgencyLevel getUrgencyLevel() {
        // Check chargeback first (most critical)
        if (chargebackDeadline != null && !chargebackResponseMet) {
            Duration remaining = getChargebackTimeRemaining();
            if (remaining.toHours() <= 6) return UrgencyLevel.CRITICAL;
            if (remaining.toHours() <= 24) return UrgencyLevel.HIGH;
        }
        
        // Check resolution deadline
        if (resolutionDeadline != null && !resolutionMet) {
            Duration remaining = getResolutionTimeRemaining();
            if (remaining.toHours() <= 24) return UrgencyLevel.HIGH;
            if (remaining.toDays() <= 2) return UrgencyLevel.MEDIUM;
        }
        
        // Check initial response
        if (initialResponseDeadline != null && !initialResponseMet) {
            Duration remaining = getInitialResponseTimeRemaining();
            if (remaining.toHours() <= 4) return UrgencyLevel.HIGH;
            if (remaining.toHours() <= 12) return UrgencyLevel.MEDIUM;
        }
        
        return UrgencyLevel.LOW;
    }
    
    public enum UrgencyLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}