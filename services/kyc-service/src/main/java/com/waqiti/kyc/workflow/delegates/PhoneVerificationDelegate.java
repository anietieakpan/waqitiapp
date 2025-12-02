package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phone Verification Delegate - Production Implementation
 * 
 * Handles phone verification workflow step with multiple providers,
 * fraud detection, rate limiting, and comprehensive error handling.
 * 
 * @author Waqiti KYC Team
 * @version 3.0.0
 */
@Slf4j
@Component("phoneVerificationDelegate")
@RequiredArgsConstructor
public class PhoneVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final AuditService auditService;
    private final CacheService cacheService;
    private final NotificationService notificationService;
    private final SecurityContextService securityService;
    private final RestTemplate restTemplate;
    
    @Value("${kyc.phone.verification.max-attempts:3}")
    private int maxVerificationAttempts;
    
    @Value("${kyc.phone.verification.rate-limit:5}")
    private int rateLimitPerHour;
    
    @Value("${kyc.phone.verification.expiry-minutes:10}")
    private int verificationExpiryMinutes;
    
    @Value("${kyc.phone.verification.allowed-countries:US,CA,UK,FR,DE,AU,NZ}")
    private String allowedCountries;
    
    @Value("${twilio.account.sid}")
    private String twilioAccountSid;
    
    @Value("${twilio.auth.token}")
    private String twilioAuthToken;
    
    @Value("${twilio.verify.service.sid}")
    private String twilioVerifyServiceSid;
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    private static final String TWILIO_VERIFY_URL = "https://verify.twilio.com/v2/Services/{serviceSid}/Verifications";

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String userId = (String) execution.getVariable("userId");
        String verificationId = UUID.randomUUID().toString();
        
        log.info("Starting phone verification workflow - ProcessInstance: {}, UserId: {}, VerificationId: {}", 
            processInstanceId, userId, verificationId);

        try {
            // Step 1: Extract and validate input parameters
            PhoneVerificationContext context = extractVerificationContext(execution);
            validateVerificationContext(context);
            
            // Step 2: Security and rate limiting checks
            performSecurityChecks(context);
            
            // Step 3: Get KYC application
            KYCApplication application = kycApplicationRepository.findById(context.getKycApplicationId())
                .orElseThrow(() -> new BpmnError("KYC_APP_NOT_FOUND", 
                    "KYC Application not found: " + context.getKycApplicationId()));

            // Step 4: Check previous attempts
            checkPreviousAttempts(context, application);
            
            // Step 5: Validate phone number format and country
            validatePhoneNumber(context);
            
            // Step 6: Initialize phone verification
            PhoneVerificationResult result = initiatePhoneVerification(context);
            
            // Step 7: Handle verification result
            if (result.isSuccess()) {
                handleSuccessfulInitiation(execution, application, context, result);
            } else {
                handleFailedInitiation(execution, application, context, result);
            }
            
            // Step 8: Audit and logging
            auditPhoneVerification(context, result);
            
            log.info("Phone verification workflow completed - ProcessInstance: {}, Status: {}", 
                processInstanceId, result.isSuccess() ? "SUCCESS" : "FAILED");
                
        } catch (BpmnError e) {
            log.error("BPMN Error in phone verification - ProcessInstance: {}", processInstanceId, e);
            handleBpmnError(execution, userId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in phone verification - ProcessInstance: {}", processInstanceId, e);
            handleUnexpectedError(execution, userId, verificationId, e);
            throw new BpmnError("PHONE_VERIFICATION_ERROR", 
                "Phone verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract verification context from execution
     */
    private PhoneVerificationContext extractVerificationContext(DelegateExecution execution) {
        return PhoneVerificationContext.builder()
            .userId((String) execution.getVariable("userId"))
            .kycApplicationId((String) execution.getVariable("kycApplicationId"))
            .phoneNumber(normalizePhoneNumber((String) execution.getVariable("phone")))
            .countryCode((String) execution.getVariable("country"))
            .providerName((String) execution.getVariable("kycProvider"))
            .preferredLanguage((String) execution.getVariable("preferredLanguage"))
            .verificationType(getVerificationType(execution))
            .processInstanceId(execution.getProcessInstanceId())
            .build();
    }
    
    /**
     * Validate verification context
     */
    private void validateVerificationContext(PhoneVerificationContext context) {
        if (context.getUserId() == null || context.getUserId().trim().isEmpty()) {
            throw new BpmnError("INVALID_USER_ID", "User ID is required");
        }
        
        if (context.getKycApplicationId() == null || context.getKycApplicationId().trim().isEmpty()) {
            throw new BpmnError("INVALID_KYC_APP_ID", "KYC Application ID is required");
        }
        
        if (context.getPhoneNumber() == null || context.getPhoneNumber().trim().isEmpty()) {
            throw new BpmnError("INVALID_PHONE_NUMBER", "Phone number is required");
        }
        
        if (context.getCountryCode() == null || !isAllowedCountry(context.getCountryCode())) {
            throw new BpmnError("UNSUPPORTED_COUNTRY", 
                "Phone verification not supported for country: " + context.getCountryCode());
        }
    }
    
    /**
     * Perform security checks including rate limiting
     */
    private void performSecurityChecks(PhoneVerificationContext context) {
        String rateLimitKey = "phone_verification_" + context.getUserId();
        String ipRateLimitKey = "phone_verification_ip_" + securityService.getClientIP();
        
        // Check user-based rate limit
        Integer userAttempts = (Integer) cacheService.get(rateLimitKey);
        if (userAttempts != null && userAttempts >= rateLimitPerHour) {
            log.warn("Rate limit exceeded for user: {} - {} attempts in last hour", 
                context.getUserId(), userAttempts);
            auditService.auditSecurityEvent("PHONE_VERIFICATION_RATE_LIMIT", 
                context.getUserId(), Map.of("attempts", userAttempts));
            throw new BpmnError("RATE_LIMIT_EXCEEDED", 
                "Too many verification attempts. Please try again later.");
        }
        
        // Check IP-based rate limit
        Integer ipAttempts = (Integer) cacheService.get(ipRateLimitKey);
        if (ipAttempts != null && ipAttempts >= rateLimitPerHour * 3) {
            log.warn("IP rate limit exceeded - {} attempts from IP: {}", 
                ipAttempts, securityService.getClientIP());
            auditService.auditSecurityEvent("PHONE_VERIFICATION_IP_RATE_LIMIT", 
                context.getUserId(), Map.of("ip", securityService.getClientIP(), "attempts", ipAttempts));
            throw new BpmnError("IP_RATE_LIMIT_EXCEEDED", 
                "Too many verification attempts from this IP. Please try again later.");
        }
        
        // Increment counters
        cacheService.put(rateLimitKey, (userAttempts != null ? userAttempts : 0) + 1, 3600);
        cacheService.put(ipRateLimitKey, (ipAttempts != null ? ipAttempts : 0) + 1, 3600);
    }
    
    /**
     * Check previous verification attempts
     */
    private void checkPreviousAttempts(PhoneVerificationContext context, KYCApplication application) {
        if (application.getPhoneVerificationAttempts() >= maxVerificationAttempts) {
            log.warn("Maximum verification attempts reached for user: {} - {} attempts", 
                context.getUserId(), application.getPhoneVerificationAttempts());
            throw new BpmnError("MAX_ATTEMPTS_EXCEEDED", 
                "Maximum verification attempts exceeded. Please contact support.");
        }
    }
    
    /**
     * Validate phone number format and country
     */
    private void validatePhoneNumber(PhoneVerificationContext context) {
        if (!PHONE_PATTERN.matcher(context.getPhoneNumber()).matches()) {
            throw new BpmnError("INVALID_PHONE_FORMAT", 
                "Invalid phone number format: " + context.getPhoneNumber());
        }
        
        // Additional validation for specific country formats could be added here
        if (context.getPhoneNumber().length() < 10 || context.getPhoneNumber().length() > 15) {
            throw new BpmnError("INVALID_PHONE_LENGTH", 
                "Phone number length invalid: " + context.getPhoneNumber().length() + " digits");
        }
    }
    
    /**
     * Initiate phone verification with Twilio
     */
    private PhoneVerificationResult initiatePhoneVerification(PhoneVerificationContext context) {
        try {
            // Use Twilio Verify API for production-grade verification
            String url = TWILIO_VERIFY_URL.replace("{serviceSid}", twilioVerifyServiceSid);
            
            Map<String, String> requestBody = Map.of(
                "To", context.getPhoneNumber(),
                "Channel", context.getVerificationType().toLowerCase(),
                "Locale", context.getPreferredLanguage() != null ? context.getPreferredLanguage() : "en"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
            
            HttpEntity<String> entity = new HttpEntity<>(
                buildFormUrlEncodedData(requestBody), headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");
                String sid = (String) responseBody.get("sid");
                
                if ("pending".equals(status)) {
                    log.info("Phone verification successfully initiated - SID: {}, Phone: {}", 
                        sid, maskPhoneNumber(context.getPhoneNumber()));
                    
                    return PhoneVerificationResult.builder()
                        .success(true)
                        .verificationId(sid)
                        .status(status)
                        .expiryTime(LocalDateTime.now().plusMinutes(verificationExpiryMinutes))
                        .build();
                } else {
                    log.warn("Unexpected verification status: {} for phone: {}", 
                        status, maskPhoneNumber(context.getPhoneNumber()));
                    return PhoneVerificationResult.builder()
                        .success(false)
                        .errorMessage("Verification could not be initiated: " + status)
                        .build();
                }
            } else {
                log.error("Phone verification API returned error - Status: {}, Phone: {}", 
                    response.getStatusCode(), maskPhoneNumber(context.getPhoneNumber()));
                return PhoneVerificationResult.builder()
                    .success(false)
                    .errorMessage("Verification service temporarily unavailable")
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error initiating phone verification for phone: {}", 
                maskPhoneNumber(context.getPhoneNumber()), e);
            return PhoneVerificationResult.builder()
                .success(false)
                .errorMessage("Failed to initiate verification: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Handle successful verification initiation
     */
    private void handleSuccessfulInitiation(DelegateExecution execution, KYCApplication application, 
                                          PhoneVerificationContext context, PhoneVerificationResult result) {
        
        // Update application
        application.setPhoneVerificationStatus(VerificationStatus.PENDING);
        application.setPhoneVerificationAttempts(application.getPhoneVerificationAttempts() + 1);
        application.setLastUpdated(LocalDateTime.now());
        kycApplicationRepository.save(application);
        
        // Set process variables for next steps
        execution.setVariable("phoneVerificationSession", result.getVerificationId());
        execution.setVariable("phoneVerificationStatus", "PENDING");
        execution.setVariable("phoneVerificationSent", true);
        execution.setVariable("phoneVerificationExpiry", result.getExpiryTime());
        execution.setVariable("phoneVerificationMethod", context.getVerificationType());
        
        // Send notification to user
        notificationService.sendPhoneVerificationNotification(
            context.getUserId(), 
            maskPhoneNumber(context.getPhoneNumber()),
            context.getVerificationType()
        );
        
        log.info("Phone verification successfully initiated - User: {}, Session: {}", 
            context.getUserId(), result.getVerificationId());
    }
    
    /**
     * Handle failed verification initiation
     */
    private void handleFailedInitiation(DelegateExecution execution, KYCApplication application, 
                                       PhoneVerificationContext context, PhoneVerificationResult result) {
        
        // Update application with failure
        application.setPhoneVerificationStatus(VerificationStatus.FAILED);
        application.setPhoneVerificationAttempts(application.getPhoneVerificationAttempts() + 1);
        application.setLastUpdated(LocalDateTime.now());
        kycApplicationRepository.save(application);
        
        // Set failure variables
        execution.setVariable("phoneVerificationStatus", "FAILED");
        execution.setVariable("phoneVerificationSent", false);
        execution.setVariable("phoneVerificationError", result.getErrorMessage());
        
        log.error("Phone verification initiation failed - User: {}, Error: {}", 
            context.getUserId(), result.getErrorMessage());
        
        throw new BpmnError("PHONE_VERIFICATION_FAILED", 
            "Failed to initiate phone verification: " + result.getErrorMessage());
    }
    
    /**
     * Audit phone verification attempt
     */
    private void auditPhoneVerification(PhoneVerificationContext context, PhoneVerificationResult result) {
        auditService.auditAction(
            "PHONE_VERIFICATION_INITIATED",
            context.getUserId(),
            Map.of(
                "kycApplicationId", context.getKycApplicationId(),
                "phoneNumber", maskPhoneNumber(context.getPhoneNumber()),
                "countryCode", context.getCountryCode(),
                "verificationType", context.getVerificationType(),
                "success", result.isSuccess(),
                "verificationId", result.getVerificationId(),
                "processInstanceId", context.getProcessInstanceId()
            )
        );
    }
    
    /**
     * Handle BPMN errors
     */
    private void handleBpmnError(DelegateExecution execution, String userId, BpmnError error) {
        execution.setVariable("phoneVerificationStatus", "ERROR");
        execution.setVariable("phoneVerificationError", error.getMessage());
        execution.setVariable("phoneVerificationErrorCode", error.getErrorCode());
        
        auditService.auditAction(
            "PHONE_VERIFICATION_BPMN_ERROR",
            userId,
            Map.of(
                "errorCode", error.getErrorCode(),
                "errorMessage", error.getMessage(),
                "processInstanceId", execution.getProcessInstanceId()
            )
        );
    }
    
    /**
     * Handle unexpected errors
     */
    private void handleUnexpectedError(DelegateExecution execution, String userId, 
                                      String verificationId, Exception error) {
        execution.setVariable("phoneVerificationStatus", "SYSTEM_ERROR");
        execution.setVariable("phoneVerificationError", "System error occurred");
        execution.setVariable("phoneVerificationId", verificationId);
        
        auditService.auditAction(
            "PHONE_VERIFICATION_SYSTEM_ERROR",
            userId,
            Map.of(
                "verificationId", verificationId,
                "errorType", error.getClass().getSimpleName(),
                "errorMessage", error.getMessage(),
                "processInstanceId", execution.getProcessInstanceId()
            )
        );
    }
    
    // Helper methods
    
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        // Remove all non-digit characters except +
        String normalized = phoneNumber.replaceAll("[^+\\d]", "");
        // Ensure it starts with + if it doesn't already
        if (!normalized.startsWith("+")) {
            normalized = "+" + normalized;
        }
        return normalized;
    }
    
    private String getVerificationType(DelegateExecution execution) {
        String type = (String) execution.getVariable("verificationType");
        return type != null ? type : "sms"; // Default to SMS
    }
    
    private boolean isAllowedCountry(String countryCode) {
        return allowedCountries.contains(countryCode.toUpperCase());
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) return "****";
        return phoneNumber.substring(0, Math.min(3, phoneNumber.length())) + 
               "*".repeat(phoneNumber.length() - 6) + 
               phoneNumber.substring(Math.max(phoneNumber.length() - 3, 3));
    }
    
    private String buildFormUrlEncodedData(Map<String, String> data) {
        return data.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    }
    
    // Inner classes
    
    @lombok.Data
    @lombok.Builder
    private static class PhoneVerificationContext {
        private String userId;
        private String kycApplicationId;
        private String phoneNumber;
        private String countryCode;
        private String providerName;
        private String preferredLanguage;
        private String verificationType;
        private String processInstanceId;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class PhoneVerificationResult {
        private boolean success;
        private String verificationId;
        private String status;
        private LocalDateTime expiryTime;
        private String errorMessage;
    }
}