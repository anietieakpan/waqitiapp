package com.waqiti.integration.audit;

import com.waqiti.integration.dto.*;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit service for integration operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationAuditService {

    private final AuditService auditService;

    /**
     * Log successful user creation
     */
    public void logUserCreation(UserRegistrationRequest request, UserRegistrationResponse response, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "USER_CREATION");
        auditData.put("userId", response.getId());
        auditData.put("userName", request.getName());
        auditData.put("userEmail", request.getEmail());
        auditData.put("status", response.getStatus());
        auditData.put("durationMs", durationMs);
        auditData.put("isPendingIntegration", response.isPendingIntegration());
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_USER_CREATION_SUCCESS",
                response.getId(),
                "User created successfully",
                auditData
        );

        log.info("Audited successful user creation: userId={}, email={}, duration={}ms", 
                response.getId(), request.getEmail(), durationMs);
    }

    /**
     * Log failed user creation
     */
    public void logUserCreationFailure(UserRegistrationRequest request, Exception error, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "USER_CREATION");
        auditData.put("userName", request.getName());
        auditData.put("userEmail", request.getEmail());
        auditData.put("error", error.getMessage());
        auditData.put("errorType", error.getClass().getSimpleName());
        auditData.put("durationMs", durationMs);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_USER_CREATION_FAILURE",
                request.getEmail(),
                "User creation failed: " + error.getMessage(),
                auditData
        );

        log.warn("Audited failed user creation: email={}, error={}, duration={}ms", 
                request.getEmail(), error.getMessage(), durationMs);
    }

    /**
     * Log successful account creation
     */
    public void logAccountCreation(String userId, AccountCreationRequest request, 
                                 AccountResponse response, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "ACCOUNT_CREATION");
        auditData.put("userId", userId);
        auditData.put("accountId", response.getId());
        auditData.put("currency", request.getCurrency());
        auditData.put("initialBalance", request.getInitialBalance());
        auditData.put("status", response.getStatus());
        auditData.put("durationMs", durationMs);
        auditData.put("isPendingIntegration", response.isPendingIntegration());
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_ACCOUNT_CREATION_SUCCESS",
                userId,
                "Account created successfully",
                auditData
        );

        log.info("Audited successful account creation: userId={}, accountId={}, currency={}, duration={}ms", 
                userId, response.getId(), request.getCurrency(), durationMs);
    }

    /**
     * Log failed account creation
     */
    public void logAccountCreationFailure(String userId, AccountCreationRequest request, 
                                        Exception error, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "ACCOUNT_CREATION");
        auditData.put("userId", userId);
        auditData.put("currency", request.getCurrency());
        auditData.put("initialBalance", request.getInitialBalance());
        auditData.put("error", error.getMessage());
        auditData.put("errorType", error.getClass().getSimpleName());
        auditData.put("durationMs", durationMs);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_ACCOUNT_CREATION_FAILURE",
                userId,
                "Account creation failed: " + error.getMessage(),
                auditData
        );

        log.warn("Audited failed account creation: userId={}, currency={}, error={}, duration={}ms", 
                userId, request.getCurrency(), error.getMessage(), durationMs);
    }

    /**
     * Log successful payment
     */
    public void logPayment(String userId, PaymentRequest request, PaymentResponse response, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "PAYMENT");
        auditData.put("paymentId", response.getId());
        auditData.put("fromUserId", userId);
        auditData.put("toUserId", request.getDestination().getRecipientId());
        auditData.put("amount", request.getAmount());
        auditData.put("currency", request.getCurrency());
        auditData.put("description", request.getDescription());
        auditData.put("status", response.getStatus());
        auditData.put("durationMs", durationMs);
        auditData.put("isPendingIntegration", response.isPendingIntegration());
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_PAYMENT_SUCCESS",
                userId,
                "Payment processed successfully",
                auditData
        );

        log.info("Audited successful payment: paymentId={}, from={}, to={}, amount={} {}, duration={}ms", 
                response.getId(), userId, request.getDestination().getRecipientId(), 
                request.getAmount(), request.getCurrency(), durationMs);
    }

    /**
     * Log failed payment
     */
    public void logPaymentFailure(String userId, PaymentRequest request, Exception error, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "PAYMENT");
        auditData.put("fromUserId", userId);
        auditData.put("toUserId", request.getDestination().getRecipientId());
        auditData.put("amount", request.getAmount());
        auditData.put("currency", request.getCurrency());
        auditData.put("description", request.getDescription());
        auditData.put("error", error.getMessage());
        auditData.put("errorType", error.getClass().getSimpleName());
        auditData.put("durationMs", durationMs);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_PAYMENT_FAILURE",
                userId,
                "Payment failed: " + error.getMessage(),
                auditData
        );

        log.warn("Audited failed payment: from={}, to={}, amount={} {}, error={}, duration={}ms", 
                userId, request.getDestination().getRecipientId(), 
                request.getAmount(), request.getCurrency(), error.getMessage(), durationMs);
    }

    /**
     * Log compliance check
     */
    public void logComplianceCheck(String userId, String operation, boolean passed, String details) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "COMPLIANCE_CHECK");
        auditData.put("userId", userId);
        auditData.put("checkType", operation);
        auditData.put("passed", passed);
        auditData.put("details", details);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                passed ? "INTEGRATION_COMPLIANCE_PASS" : "INTEGRATION_COMPLIANCE_FAIL",
                userId,
                String.format("Compliance check %s: %s", passed ? "passed" : "failed", operation),
                auditData
        );

        log.info("Audited compliance check: userId={}, operation={}, passed={}, details={}", 
                userId, operation, passed, details);
    }

    /**
     * Log security event
     */
    public void logSecurityEvent(String userId, String eventType, String description, Map<String, Object> context) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "SECURITY_EVENT");
        auditData.put("userId", userId);
        auditData.put("eventType", eventType);
        auditData.put("description", description);
        auditData.put("context", context);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_SECURITY_EVENT",
                userId,
                String.format("Security event: %s - %s", eventType, description),
                auditData
        );

        log.warn("Audited security event: userId={}, type={}, description={}", 
                userId, eventType, description);
    }

    /**
     * Log API access
     */
    public void logApiAccess(String userId, String endpoint, String method, int statusCode, long durationMs) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "API_ACCESS");
        auditData.put("userId", userId);
        auditData.put("endpoint", endpoint);
        auditData.put("method", method);
        auditData.put("statusCode", statusCode);
        auditData.put("durationMs", durationMs);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                statusCode < 400 ? "INTEGRATION_API_SUCCESS" : "INTEGRATION_API_ERROR",
                userId,
                String.format("API access: %s %s - %d", method, endpoint, statusCode),
                auditData
        );

        log.debug("Audited API access: userId={}, {} {}, status={}, duration={}ms", 
                userId, method, endpoint, statusCode, durationMs);
    }

    /**
     * Log data access event
     */
    public void logDataAccess(String userId, String dataType, String operation, String resourceId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "DATA_ACCESS");
        auditData.put("userId", userId);
        auditData.put("dataType", dataType);
        auditData.put("accessOperation", operation);
        auditData.put("resourceId", resourceId);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_DATA_ACCESS",
                userId,
                String.format("Data access: %s %s on %s", operation, dataType, resourceId),
                auditData
        );

        log.debug("Audited data access: userId={}, operation={}, dataType={}, resourceId={}", 
                userId, operation, dataType, resourceId);
    }

    /**
     * Log system configuration change
     */
    public void logConfigurationChange(String userId, String configKey, String oldValue, String newValue) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("operation", "CONFIGURATION_CHANGE");
        auditData.put("userId", userId);
        auditData.put("configKey", configKey);
        auditData.put("oldValue", oldValue);
        auditData.put("newValue", newValue);
        auditData.put("timestamp", Instant.now());

        auditService.logEvent(
                "INTEGRATION_CONFIG_CHANGE",
                userId,
                String.format("Configuration changed: %s", configKey),
                auditData
        );

        log.info("Audited configuration change: userId={}, key={}, oldValue={}, newValue={}", 
                userId, configKey, oldValue, newValue);
    }
}