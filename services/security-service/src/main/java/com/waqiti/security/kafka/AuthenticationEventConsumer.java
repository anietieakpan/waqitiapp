package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.security.service.EnhancedMultiFactorAuthService;
import com.waqiti.security.service.BiometricService;
import com.waqiti.security.service.AuditTrailService;
import com.waqiti.security.service.SecurityActionService;
import com.waqiti.security.entity.AuthenticationAttempt;
import com.waqiti.security.entity.SecurityEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #242: Authentication Event Consumer
 * Processes MFA, biometric authentication, and identity verification
 * Implements 12-step zero-tolerance processing for authentication security
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventConsumer extends BaseKafkaConsumer {

    private final EnhancedMultiFactorAuthService mfaService;
    private final BiometricService biometricService;
    private final AuditTrailService auditService;
    private final SecurityActionService securityActionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "authentication-events", groupId = "authentication-group")
    @CircuitBreaker(name = "authentication-consumer")
    @Retry(name = "authentication-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleAuthenticationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "authentication-event");
        
        try {
            log.info("Step 1: Processing authentication event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String authMethod = eventData.path("authMethod").asText();
            String deviceId = eventData.path("deviceId").asText();
            String ipAddress = eventData.path("ipAddress").asText();
            String userAgent = eventData.path("userAgent").asText();
            String geolocation = eventData.path("geolocation").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            boolean successful = eventData.path("successful").asBoolean();
            
            log.info("Step 2: Extracted authentication details: userId={}, method={}, device={}, success={}", 
                    userId, authMethod, deviceId, successful);
            
            // Step 3: Authentication method validation and security assessment
            log.info("Step 3: Validating authentication method and conducting security assessment");
            AuthenticationAttempt authAttempt = mfaService.createAuthenticationAttempt(eventData);
            
            if (!mfaService.isValidAuthenticationMethod(authMethod)) {
                securityActionService.flagInvalidAuthMethod(userId, authMethod);
                throw new IllegalStateException("Invalid authentication method: " + authMethod);
            }
            
            mfaService.validateAuthenticationStrength(authAttempt);
            
            // Step 4: Multi-factor authentication processing
            log.info("Step 4: Processing multi-factor authentication requirements");
            if ("MFA".equals(authMethod) || "BIOMETRIC_MFA".equals(authMethod)) {
                boolean mfaRequired = mfaService.requiresMFA(userId, deviceId, ipAddress);
                if (mfaRequired) {
                    mfaService.processMFAChallenge(authAttempt);
                    mfaService.validateMFAResponse(authAttempt);
                    
                    if (!mfaService.verifyMFACode(authAttempt)) {
                        securityActionService.handleMFAFailure(userId, authAttempt);
                        return;
                    }
                }
            }
            
            // Step 5: Biometric authentication verification
            log.info("Step 5: Conducting biometric authentication verification");
            if ("BIOMETRIC".equals(authMethod) || "BIOMETRIC_MFA".equals(authMethod)) {
                String biometricTemplate = eventData.path("biometricTemplate").asText();
                String biometricType = eventData.path("biometricType").asText();
                
                boolean biometricValid = biometricService.verifyBiometric(userId, biometricTemplate, biometricType);
                if (!biometricValid) {
                    securityActionService.handleBiometricFailure(userId, authAttempt);
                    biometricService.flagBiometricAnomaly(userId, biometricType);
                    return;
                }
                
                biometricService.updateBiometricTemplate(userId, biometricTemplate, biometricType);
                biometricService.assessBiometricQuality(biometricTemplate, biometricType);
            }
            
            // Step 6: Device and location risk assessment
            log.info("Step 6: Assessing device and location risk factors");
            boolean trustedDevice = mfaService.isTrustedDevice(userId, deviceId);
            boolean knownLocation = mfaService.isKnownLocation(userId, geolocation);
            
            if (!trustedDevice) {
                mfaService.requireDeviceRegistration(userId, deviceId, authAttempt);
            }
            
            if (!knownLocation) {
                mfaService.flagLocationAnomaly(userId, geolocation, authAttempt);
                mfaService.requireLocationVerification(userId, geolocation);
            }
            
            int riskScore = mfaService.calculateAuthenticationRiskScore(authAttempt);
            
            // Step 7: Behavioral analysis and anomaly detection
            log.info("Step 7: Performing behavioral analysis and anomaly detection");
            boolean behavioralAnomaly = mfaService.detectBehavioralAnomaly(userId, authAttempt);
            if (behavioralAnomaly) {
                securityActionService.escalateBehavioralAnomaly(userId, authAttempt);
                mfaService.requireStepUpAuthentication(userId, authAttempt);
            }
            
            mfaService.updateUserBehaviorProfile(userId, authAttempt);
            mfaService.analyzeAuthenticationPattern(userId, timestamp);
            
            // Step 8: Session security and token management
            log.info("Step 8: Managing session security and authentication tokens");
            if (successful) {
                String sessionToken = mfaService.generateSecureSessionToken(userId, deviceId);
                mfaService.establishSecureSession(userId, sessionToken, authAttempt);
                mfaService.configureSessionSecurity(userId, sessionToken, riskScore);
                
                if (riskScore > 75) {
                    mfaService.requireContinuousAuthentication(userId, sessionToken);
                }
            } else {
                mfaService.handleAuthenticationFailure(userId, authAttempt);
                mfaService.incrementFailureCounter(userId, deviceId);
                
                if (mfaService.exceedsFailureThreshold(userId)) {
                    securityActionService.lockUserAccount(userId, "EXCESSIVE_AUTH_FAILURES");
                }
            }
            
            // Step 9: Fraud and account takeover detection
            log.info("Step 9: Detecting fraud and account takeover attempts");
            boolean suspiciousActivity = mfaService.detectSuspiciousActivity(userId, authAttempt);
            if (suspiciousActivity) {
                securityActionService.flagPotentialAccountTakeover(userId, authAttempt);
                mfaService.initiateSecurityProtocols(userId, authAttempt);
            }
            
            mfaService.validateUserCredentials(userId, authAttempt);
            
            // Step 10: Compliance and regulatory requirements
            log.info("Step 10: Ensuring compliance with authentication regulations");
            auditService.logAuthenticationEvent(authAttempt);
            auditService.validateSOXCompliance(authAttempt);
            auditService.ensureGDPRCompliance(userId, authAttempt);
            
            if (mfaService.requiresRegulatoryReporting(authAttempt)) {
                auditService.generateRegulatoryAuthReport(authAttempt);
            }
            
            // Step 11: Security monitoring and alerting
            log.info("Step 11: Updating security monitoring and generating alerts");
            SecurityEvent securityEvent = securityActionService.createSecurityEvent(authAttempt);
            securityActionService.updateSecurityMetrics(userId, authAttempt);
            
            if (riskScore > 90) {
                securityActionService.generateHighRiskAlert(userId, authAttempt);
                securityActionService.notifySecurityTeam(userId, authAttempt);
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed authentication event: userId={}, eventId={}", userId, eventId);
            
        } catch (Exception e) {
            log.error("Error processing authentication event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("authMethod") || !eventData.has("deviceId") ||
            !eventData.has("ipAddress") || !eventData.has("timestamp") ||
            !eventData.has("successful")) {
            throw new IllegalArgumentException("Invalid authentication event structure");
        }
    }
}