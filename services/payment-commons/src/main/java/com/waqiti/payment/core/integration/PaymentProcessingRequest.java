package com.waqiti.payment.core.integration;

import com.waqiti.payment.core.model.*;
import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Unified payment processing request for orchestration
 * Industrial-grade implementation for payment processing pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"paymentData", "metadata", "securityContext"})
public class PaymentProcessingRequest {
    
    @NotNull
    private UUID requestId;
    
    @NotNull
    private PaymentType paymentType;
    
    @NotNull
    private ProcessingMode processingMode;
    
    @NotNull
    private UUID senderId;
    
    @NotNull
    private UUID recipientId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
    
    private String description;
    
    private PaymentRequest basePaymentRequest;
    
    private Map<String, Object> paymentData;
    
    @Builder.Default
    private ProcessingPriority priority = ProcessingPriority.NORMAL;
    
    private RoutingPreference routingPreference;
    
    @Builder.Default
    private List<String> preferredProviders = new ArrayList<>();
    
    @Builder.Default
    private List<String> excludedProviders = new ArrayList<>();
    
    @Builder.Default
    private boolean requireFraudCheck = true;
    
    @Builder.Default
    private boolean requireComplianceCheck = true;
    
    @Builder.Default
    private boolean autoRetry = true;
    
    @Min(0)
    @Max(10)
    @Builder.Default
    private int maxRetryAttempts = 3;
    
    private BigDecimal maxAcceptableFee;
    
    private LocalDateTime requestedExecutionTime;
    
    private LocalDateTime expiryTime;
    
    @NotNull
    private String idempotencyKey;
    
    private SecurityContext securityContext;
    
    private Map<String, String> headers;
    
    private Map<String, Object> metadata;
    
    @Builder.Default
    private List<ProcessingRule> processingRules = new ArrayList<>();
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum ProcessingMode {
        SYNCHRONOUS,     // Wait for completion
        ASYNCHRONOUS,    // Fire and forget
        BATCH,          // Part of batch processing
        SCHEDULED,      // Scheduled for later
        REAL_TIME       // Real-time processing required
    }
    
    public enum ProcessingPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL,
        EMERGENCY
    }
    
    public enum RoutingPreference {
        CHEAPEST,       // Lowest cost route
        FASTEST,        // Fastest processing
        MOST_RELIABLE,  // Highest success rate
        PREFERRED,      // Use preferred providers
        BALANCED,       // Balance all factors
        CUSTOM         // Custom routing logic
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityContext {
        private String userId;
        private String sessionId;
        private String ipAddress;
        private String deviceId;
        private String userAgent;
        private Map<String, Object> additionalContext;
        private boolean twoFactorVerified;
        private String authenticationMethod;
        private LocalDateTime authenticationTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingRule {
        private String ruleType;
        private String condition;
        private String action;
        private Map<String, Object> parameters;
        private boolean stopOnMatch;
        private int priority;
    }
    
    // Business logic methods
    public boolean isExpired() {
        return expiryTime != null && LocalDateTime.now().isAfter(expiryTime);
    }
    
    public boolean isScheduled() {
        return requestedExecutionTime != null && 
               requestedExecutionTime.isAfter(LocalDateTime.now());
    }
    
    public boolean isHighPriority() {
        return priority == ProcessingPriority.HIGH || 
               priority == ProcessingPriority.CRITICAL ||
               priority == ProcessingPriority.EMERGENCY;
    }
    
    public boolean requiresImmediateProcessing() {
        return processingMode == ProcessingMode.REAL_TIME || 
               priority == ProcessingPriority.EMERGENCY;
    }
    
    public boolean hasRoutingPreferences() {
        return routingPreference != null || 
               !preferredProviders.isEmpty() || 
               !excludedProviders.isEmpty();
    }
    
    public Map<String, Object> toAuditLog() {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("requestId", requestId);
        auditData.put("paymentType", paymentType);
        auditData.put("amount", amount);
        auditData.put("currency", currency);
        auditData.put("senderId", senderId);
        auditData.put("recipientId", recipientId);
        auditData.put("priority", priority);
        auditData.put("createdAt", createdAt);
        if (securityContext != null) {
            auditData.put("userId", securityContext.getUserId());
            auditData.put("ipAddress", securityContext.getIpAddress());
        }
        return auditData;
    }
}