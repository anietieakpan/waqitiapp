package com.waqiti.payment.service;

import com.waqiti.payment.exception.PaymentProcessingException;
import com.waqiti.payment.dto.PaymentErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced error handling service for payment processing
 * Provides comprehensive error tracking, logging, and recovery mechanisms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedPaymentErrorHandlingService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${payment.error.kafka.topic:payment-errors}")
    private String errorTopic;
    
    @Value("${payment.error.enable-kafka:true}")
    private boolean enableKafkaErrorPublishing;
    
    /**
     * Handle and process payment errors with comprehensive logging and tracking
     */
    public PaymentErrorResponse handlePaymentError(Exception exception, String correlationId, 
                                                 String operationType, Map<String, Object> context) {
        
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Log error with structured data
        logPaymentError(exception, errorId, correlationId, operationType, context, timestamp);
        
        // Publish error event for monitoring and analytics
        if (enableKafkaErrorPublishing) {
            publishErrorEvent(exception, errorId, correlationId, operationType, context, timestamp);
        }
        
        // Create standardized error response
        return createErrorResponse(exception, errorId, correlationId, timestamp);
    }
    
    /**
     * Handle specific validation errors with enhanced context
     */
    public PaymentErrorResponse handleValidationError(String field, Object value, String validationRule, 
                                                    String correlationId) {
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        Map<String, Object> context = Map.of(
            "field", field,
            "value", value != null ? value.toString() : "null",
            "validation_rule", validationRule
        );
        
        log.warn("Payment validation error [{}] - Field: {}, Rule: {}, Value: {}, Correlation: {}", 
                errorId, field, validationRule, value, correlationId);
        
        if (enableKafkaErrorPublishing) {
            publishValidationErrorEvent(errorId, correlationId, field, validationRule, value, timestamp);
        }
        
        return PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .errorCode("VALIDATION_ERROR")
                .errorMessage(String.format("Validation failed for field '%s': %s", field, validationRule))
                .timestamp(timestamp)
                .field(field)
                .validationRule(validationRule)
                .build();
    }
    
    /**
     * Handle security-related errors with enhanced audit logging
     */
    public PaymentErrorResponse handleSecurityError(String securityViolation, String userId, 
                                                  String ipAddress, String correlationId) {
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Critical security logging
        log.error("SECURITY VIOLATION [{}] - User: {}, IP: {}, Violation: {}, Correlation: {}", 
                errorId, userId, ipAddress, securityViolation, correlationId);
        
        Map<String, Object> securityContext = Map.of(
            "user_id", userId,
            "ip_address", ipAddress,
            "security_violation", securityViolation,
            "severity", "HIGH"
        );
        
        if (enableKafkaErrorPublishing) {
            publishSecurityErrorEvent(errorId, correlationId, securityContext, timestamp);
        }
        
        return PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .errorCode("SECURITY_ERROR")
                .errorMessage("Security violation detected - access denied")
                .timestamp(timestamp)
                .severity("HIGH")
                .build();
    }
    
    /**
     * Handle network/integration errors with retry information
     */
    public PaymentErrorResponse handleNetworkError(Exception exception, String service, int attemptCount, 
                                                 String correlationId) {
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        Map<String, Object> networkContext = Map.of(
            "service", service,
            "attempt_count", attemptCount,
            "error_type", "NETWORK_ERROR",
            "retryable", isRetryableError(exception)
        );
        
        log.error("Network error [{}] - Service: {}, Attempt: {}, Retryable: {}, Correlation: {}, Error: {}", 
                errorId, service, attemptCount, isRetryableError(exception), correlationId, exception.getMessage());
        
        if (enableKafkaErrorPublishing) {
            publishErrorEvent(exception, errorId, correlationId, "NETWORK_ERROR", networkContext, timestamp);
        }
        
        return PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .errorCode("NETWORK_ERROR")
                .errorMessage(String.format("Network error communicating with %s (attempt %d)", service, attemptCount))
                .timestamp(timestamp)
                .retryable(isRetryableError(exception))
                .service(service)
                .attemptCount(attemptCount)
                .build();
    }
    
    /**
     * Handle business logic errors with detailed context
     */
    public PaymentErrorResponse handleBusinessLogicError(String businessRule, String violation, 
                                                       Map<String, Object> businessContext, String correlationId) {
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        log.warn("Business logic error [{}] - Rule: {}, Violation: {}, Context: {}, Correlation: {}", 
                errorId, businessRule, violation, businessContext, correlationId);
        
        Map<String, Object> context = new HashMap<>(businessContext);
        context.put("business_rule", businessRule);
        context.put("violation", violation);
        
        if (enableKafkaErrorPublishing) {
            publishBusinessErrorEvent(errorId, correlationId, businessRule, violation, context, timestamp);
        }
        
        return PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .errorCode("BUSINESS_RULE_VIOLATION")
                .errorMessage(String.format("Business rule violation: %s - %s", businessRule, violation))
                .timestamp(timestamp)
                .businessRule(businessRule)
                .businessContext(businessContext)
                .build();
    }
    
    /**
     * Handle fraud detection errors
     */
    public PaymentErrorResponse handleFraudDetectionError(String fraudIndicator, double riskScore, 
                                                        Map<String, Object> riskFactors, String correlationId) {
        String errorId = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.now();
        
        log.error("FRAUD DETECTED [{}] - Indicator: {}, Risk Score: {}, Factors: {}, Correlation: {}", 
                errorId, fraudIndicator, riskScore, riskFactors, correlationId);
        
        Map<String, Object> fraudContext = Map.of(
            "fraud_indicator", fraudIndicator,
            "risk_score", riskScore,
            "risk_factors", riskFactors,
            "severity", riskScore > 0.8 ? "CRITICAL" : riskScore > 0.5 ? "HIGH" : "MEDIUM"
        );
        
        if (enableKafkaErrorPublishing) {
            publishFraudErrorEvent(errorId, correlationId, fraudContext, timestamp);
        }
        
        return PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .errorCode("FRAUD_DETECTED")
                .errorMessage("Transaction blocked due to fraud detection")
                .timestamp(timestamp)
                .fraudIndicator(fraudIndicator)
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .severity(riskScore > 0.8 ? "CRITICAL" : riskScore > 0.5 ? "HIGH" : "MEDIUM")
                .build();
    }
    
    // Private helper methods
    
    private void logPaymentError(Exception exception, String errorId, String correlationId, 
                               String operationType, Map<String, Object> context, LocalDateTime timestamp) {
        
        if (exception instanceof PaymentProcessingException) {
            PaymentProcessingException ppe = (PaymentProcessingException) exception;
            log.error("Payment processing error [{}] - Operation: {}, Code: {}, Message: {}, Context: {}, Correlation: {}", 
                    errorId, operationType, ppe.getErrorCode(), ppe.getMessage(), context, correlationId, exception);
        } else {
            log.error("Unexpected payment error [{}] - Operation: {}, Type: {}, Message: {}, Context: {}, Correlation: {}", 
                    errorId, operationType, exception.getClass().getSimpleName(), exception.getMessage(), context, correlationId, exception);
        }
    }
    
    private PaymentErrorResponse createErrorResponse(Exception exception, String errorId, 
                                                   String correlationId, LocalDateTime timestamp) {
        
        PaymentErrorResponse.PaymentErrorResponseBuilder builder = PaymentErrorResponse.builder()
                .errorId(errorId)
                .correlationId(correlationId)
                .timestamp(timestamp);
        
        if (exception instanceof PaymentProcessingException) {
            PaymentProcessingException ppe = (PaymentProcessingException) exception;
            return builder
                    .errorCode(ppe.getErrorCode())
                    .errorMessage(ppe.getMessage())
                    .httpStatus(ppe.getHttpStatus())
                    .build();
        } else {
            return builder
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage("An unexpected error occurred during payment processing")
                    .build();
        }
    }
    
    private boolean isRetryableError(Exception exception) {
        // Define retryable error patterns
        if (exception instanceof java.net.ConnectException ||
            exception instanceof java.net.SocketTimeoutException ||
            exception instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") ||
                   lowerMessage.contains("connection") ||
                   lowerMessage.contains("unavailable") ||
                   lowerMessage.contains("temporary");
        }
        
        return false;
    }
    
    // Kafka event publishing methods
    
    private void publishErrorEvent(Exception exception, String errorId, String correlationId, 
                                 String operationType, Map<String, Object> context, LocalDateTime timestamp) {
        try {
            Map<String, Object> errorEvent = Map.of(
                "error_id", errorId,
                "correlation_id", correlationId,
                "operation_type", operationType,
                "error_type", exception.getClass().getSimpleName(),
                "error_message", exception.getMessage(),
                "context", context,
                "timestamp", timestamp.toString()
            );
            
            kafkaTemplate.send(errorTopic, errorId, errorEvent);
        } catch (Exception e) {
            log.error("Failed to publish error event to Kafka: {}", e.getMessage());
        }
    }
    
    private void publishValidationErrorEvent(String errorId, String correlationId, String field, 
                                           String validationRule, Object value, LocalDateTime timestamp) {
        try {
            Map<String, Object> validationEvent = Map.of(
                "error_id", errorId,
                "correlation_id", correlationId,
                "error_type", "VALIDATION_ERROR",
                "field", field,
                "validation_rule", validationRule,
                "invalid_value", value != null ? value.toString() : "null",
                "timestamp", timestamp.toString()
            );
            
            kafkaTemplate.send(errorTopic, errorId, validationEvent);
        } catch (Exception e) {
            log.error("Failed to publish validation error event to Kafka: {}", e.getMessage());
        }
    }
    
    private void publishSecurityErrorEvent(String errorId, String correlationId, 
                                         Map<String, Object> securityContext, LocalDateTime timestamp) {
        try {
            Map<String, Object> securityEvent = new HashMap<>();
            securityEvent.put("error_id", errorId);
            securityEvent.put("correlation_id", correlationId);
            securityEvent.put("error_type", "SECURITY_ERROR");
            securityEvent.put("timestamp", timestamp.toString());
            securityEvent.putAll(securityContext);
            
            kafkaTemplate.send("security-violations", errorId, securityEvent);
        } catch (Exception e) {
            log.error("Failed to publish security error event to Kafka: {}", e.getMessage());
        }
    }
    
    private void publishBusinessErrorEvent(String errorId, String correlationId, String businessRule, 
                                         String violation, Map<String, Object> context, LocalDateTime timestamp) {
        try {
            Map<String, Object> businessEvent = Map.of(
                "error_id", errorId,
                "correlation_id", correlationId,
                "error_type", "BUSINESS_RULE_VIOLATION",
                "business_rule", businessRule,
                "violation", violation,
                "context", context,
                "timestamp", timestamp.toString()
            );
            
            kafkaTemplate.send(errorTopic, errorId, businessEvent);
        } catch (Exception e) {
            log.error("Failed to publish business error event to Kafka: {}", e.getMessage());
        }
    }
    
    private void publishFraudErrorEvent(String errorId, String correlationId, 
                                      Map<String, Object> fraudContext, LocalDateTime timestamp) {
        try {
            Map<String, Object> fraudEvent = new HashMap<>();
            fraudEvent.put("error_id", errorId);
            fraudEvent.put("correlation_id", correlationId);
            fraudEvent.put("error_type", "FRAUD_DETECTED");
            fraudEvent.put("timestamp", timestamp.toString());
            fraudEvent.putAll(fraudContext);
            
            kafkaTemplate.send("fraud-alerts", errorId, fraudEvent);
        } catch (Exception e) {
            log.error("Failed to publish fraud error event to Kafka: {}", e.getMessage());
        }
    }
}