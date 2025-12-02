package com.waqiti.payment.kafka;

import com.waqiti.common.events.Payment3DSAuthenticationEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.ThreeDSAuthentication;
import com.waqiti.payment.domain.AuthenticationStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.ThreeDSAuthenticationRepository;
import com.waqiti.payment.service.ThreeDSAuthenticationService;
import com.waqiti.payment.service.StepUpAuthenticationService;
import com.waqiti.payment.service.PaymentFraudService;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.AuthenticationException;
import com.waqiti.payment.metrics.AuthenticationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.fraud.FraudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/**
 * CRITICAL Consumer for 3D Secure Authentication Events
 * 
 * Handles all 3D Secure (3DS) authentication scenarios including:
 * - 3DS v1 and v2 authentication flows
 * - Challenge and frictionless authentication
 * - Step-up authentication triggers
 * - Authentication result processing
 * - Exemption handling and risk-based authentication
 * - Cross-border authentication compliance
 * - Strong Customer Authentication (SCA) compliance
 * - Authentication abandonment and retry logic
 * 
 * This is CRITICAL for PSD2 compliance and fraud prevention.
 * Proper 3DS implementation reduces fraud liability shift
 * while maintaining good customer experience.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Payment3DSAuthenticationEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final ThreeDSAuthenticationRepository authRepository;
    private final ThreeDSAuthenticationService threeDSService;
    private final StepUpAuthenticationService stepUpService;
    private final PaymentFraudService paymentFraudService;
    private final PaymentGatewayService gatewayService;
    private final AuthenticationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final FraudService fraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    // 3DS Authentication thresholds and limits
    private static final BigDecimal LOW_VALUE_EXEMPTION_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal SCA_THRESHOLD = new BigDecimal("500"); // PSD2 SCA threshold
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("1000");
    
    // Authentication timeouts
    private static final Duration CHALLENGE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration FRICTIONLESS_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STEP_UP_TIMEOUT = Duration.ofMinutes(5);
    
    // Risk scoring thresholds
    private static final double HIGH_RISK_SCORE_THRESHOLD = 75.0;
    private static final double MEDIUM_RISK_SCORE_THRESHOLD = 50.0;
    private static final double LOW_RISK_SCORE_THRESHOLD = 25.0;
    
    // Exemption transaction limits
    private static final int DAILY_LVT_EXEMPTION_LIMIT = 5; // Low Value Transaction
    private static final BigDecimal DAILY_LVT_AMOUNT_LIMIT = new BigDecimal("100");
    
    /**
     * Primary handler for 3DS authentication events
     * Processes all 3DS authentication flows and results
     */
    @KafkaListener(
        topics = "payment-3ds-authentication-events",
        groupId = "payment-3ds-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "12"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePayment3DSAuthenticationEvent(
            @Payload Payment3DSAuthenticationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            org.apache.kafka.clients.consumer.ConsumerRecord<String, Payment3DSAuthenticationEvent> record,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("3ds-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing 3DS authentication event: paymentId={}, eventType={}, status={}, correlation={}",
            event.getPaymentId(), event.getEventType(), event.getAuthenticationStatus(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(event.getPaymentId(), "3DS_AUTHENTICATION");
            validateAuthenticationEvent(event);
            
            // Check for authentication fraud patterns
            if (fraudService.isSuspicious3DSPattern(event)) {
                log.warn("Suspicious 3DS pattern detected: paymentId={}", event.getPaymentId());
                handleSuspicious3DSActivity(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case AUTHENTICATION_REQUIRED:
                    processAuthenticationRequired(event, correlationId);
                    break;
                case CHALLENGE_INITIATED:
                    processChallengeInitiated(event, correlationId);
                    break;
                case CHALLENGE_COMPLETED:
                    processChallengeCompleted(event, correlationId);
                    break;
                case FRICTIONLESS_COMPLETED:
                    processFrictionlessCompleted(event, correlationId);
                    break;
                case AUTHENTICATION_SUCCESS:
                    processAuthenticationSuccess(event, correlationId);
                    break;
                case AUTHENTICATION_FAILED:
                    processAuthenticationFailed(event, correlationId);
                    break;
                case AUTHENTICATION_ABANDONED:
                    processAuthenticationAbandoned(event, correlationId);
                    break;
                case EXEMPTION_APPLIED:
                    processExemptionApplied(event, correlationId);
                    break;
                case STEP_UP_REQUIRED:
                    processStepUpRequired(event, correlationId);
                    break;
                case AUTHENTICATION_TIMEOUT:
                    processAuthenticationTimeout(event, correlationId);
                    break;
                default:
                    log.warn("Unknown 3DS event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the authentication operation
            auditService.logFinancialEvent(
                "3DS_AUTH_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "authenticationStatus", event.getAuthenticationStatus() != null ? 
                        event.getAuthenticationStatus() : "UNKNOWN",
                    "threeDSVersion", event.getThreeDSVersion() != null ? 
                        event.getThreeDSVersion() : "UNKNOWN",
                    "challengeRequired", event.isChallengeRequired(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing event: topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> {
                    log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                            record.topic(), record.offset(), result.getDestinationTopic(), result.getFailureCategory());
                })
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            record.topic(), record.partition(), record.offset(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Event processing failed", e);
        }
    }
    
    /**
     * Processes authentication requirement determination
     */
    private void processAuthenticationRequired(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing authentication requirement: paymentId={}, amount={}, region={}",
            payment.getId(), payment.getAmount(), payment.getRegion());
        
        // Determine if 3DS authentication is required
        AuthenticationRequirement requirement = determineAuthenticationRequirement(payment, event);
        
        // Create 3DS authentication record
        ThreeDSAuthentication auth = createAuthenticationRecord(payment, event, requirement, correlationId);
        authRepository.save(auth);
        
        // Update payment with 3DS information
        payment.setThreeDSAuthenticationId(auth.getId());
        payment.setThreeDSVersion(event.getThreeDSVersion());
        payment.setAuthenticationRequired(requirement.isRequired());
        payment.setExemptionType(requirement.getExemptionType());
        paymentRepository.save(payment);
        
        if (requirement.isRequired()) {
            // Initiate 3DS authentication
            initiate3DSAuthentication(payment, auth, requirement, correlationId);
        } else {
            // Apply exemption and continue processing
            processExemptionApplied(event.toBuilder()
                .eventType("EXEMPTION_APPLIED")
                .exemptionType(requirement.getExemptionType())
                .exemptionReason(requirement.getExemptionReason())
                .build(), correlationId);
        }
        
        // Update metrics
        metricsService.recordAuthenticationRequirement(
            requirement.isRequired(),
            requirement.getExemptionType(),
            payment.getAmount()
        );
        
        log.info("Authentication requirement processed: paymentId={}, required={}, exemption={}",
            payment.getId(), requirement.isRequired(), requirement.getExemptionType());
    }
    
    /**
     * Determines if 3DS authentication is required based on various factors
     */
    private AuthenticationRequirement determineAuthenticationRequirement(Payment payment, 
            Payment3DSAuthenticationEvent event) {
        
        // Check for mandatory SCA requirements (PSD2)
        if (isEEATransaction(payment) && payment.getAmount().compareTo(SCA_THRESHOLD) > 0) {
            return AuthenticationRequirement.required("SCA_MANDATORY", "PSD2 SCA requirement");
        }
        
        // Check for low value transaction exemption
        if (payment.getAmount().compareTo(LOW_VALUE_EXEMPTION_THRESHOLD) <= 0 &&
            canApplyLVTExemption(payment)) {
            return AuthenticationRequirement.exempted("LOW_VALUE", "Transaction under €30");
        }
        
        // Check for trusted merchant exemption
        if (isTrustedMerchant(payment.getMerchantId()) &&
            payment.getAmount().compareTo(SCA_THRESHOLD) <= 0) {
            return AuthenticationRequirement.exempted("TRUSTED_MERCHANT", 
                "Transaction with trusted merchant");
        }
        
        // Check for recurring payment exemption
        if (payment.isRecurringPayment() && payment.getInitialAuthenticationCompleted()) {
            return AuthenticationRequirement.exempted("RECURRING", 
                "Subsequent recurring payment");
        }
        
        // Risk-based analysis
        double riskScore = calculateTransactionRiskScore(payment);
        
        if (riskScore >= HIGH_RISK_SCORE_THRESHOLD) {
            return AuthenticationRequirement.required("HIGH_RISK", 
                String.format("High risk score: %.1f", riskScore));
        }
        
        // Check for Transaction Risk Analysis (TRA) exemption
        if (riskScore <= LOW_RISK_SCORE_THRESHOLD && 
            payment.getAmount().compareTo(SCA_THRESHOLD) <= 0 &&
            canApplyTRAExemption(payment)) {
            return AuthenticationRequirement.exempted("TRA", 
                String.format("Low risk score: %.1f", riskScore));
        }
        
        // Default to authentication required for safety
        return AuthenticationRequirement.required("DEFAULT", "Standard authentication flow");
    }
    
    /**
     * Initiates 3DS authentication flow
     */
    private void initiate3DSAuthentication(Payment payment, ThreeDSAuthentication auth,
            AuthenticationRequirement requirement, String correlationId) {
        
        log.info("Initiating 3DS authentication: paymentId={}, version={}", 
            payment.getId(), auth.getThreeDSVersion());
        
        try {
            // Prepare authentication request
            Map<String, Object> authRequest = prepare3DSAuthRequest(payment, auth);
            
            // Send to payment gateway for 3DS processing
            String threeDSResponse = threeDSService.initiateAuthentication(
                payment.getGatewayId(),
                authRequest,
                correlationId
            );
            
            // Parse response and determine flow
            if (threeDSService.isChallengeRequired(threeDSResponse)) {
                // Challenge flow required
                auth.setFlowType("CHALLENGE");
                auth.setChallengeRequired(true);
                auth.setChallengeUrl(threeDSService.extractChallengeUrl(threeDSResponse));
                auth.setExpectedChallengeCompletion(LocalDateTime.now().plus(CHALLENGE_TIMEOUT));
                authRepository.save(auth);
                
                // Update payment status
                payment.setStatus(PaymentStatus.AUTHENTICATION_PENDING);
                paymentRepository.save(payment);
                
                // Publish challenge initiated event
                publishChallengeInitiated(payment, auth, correlationId);
                
            } else {
                // Frictionless flow
                auth.setFlowType("FRICTIONLESS");
                auth.setChallengeRequired(false);
                auth.setExpectedCompletion(LocalDateTime.now().plus(FRICTIONLESS_TIMEOUT));
                authRepository.save(auth);
                
                // Process frictionless authentication result
                processFrictionlessAuthentication(payment, auth, threeDSResponse, correlationId);
            }
            
        } catch (Exception e) {
            log.error("Failed to initiate 3DS authentication: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            auth.setStatus(AuthenticationStatus.FAILED);
            auth.setFailureReason(e.getMessage());
            authRepository.save(auth);
            
            handleAuthenticationFailure(payment, auth, "INITIATION_FAILED", correlationId);
        }
    }
    
    /**
     * Processes challenge initiated events
     */
    private void processChallengeInitiated(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        ThreeDSAuthentication auth = getAuthenticationRecord(payment.getThreeDSAuthenticationId());
        
        log.info("Processing challenge initiated: paymentId={}, challengeUrl={}", 
            payment.getId(), event.getChallengeUrl());
        
        // Update authentication record
        auth.setStatus(AuthenticationStatus.CHALLENGE_PENDING);
        auth.setChallengeInitiatedAt(LocalDateTime.now());
        auth.setChallengeUrl(event.getChallengeUrl());
        authRepository.save(auth);
        
        // Send customer notification with challenge instructions
        sendChallengeNotification(payment, auth, correlationId);
        
        // Start challenge timeout monitoring
        scheduleAuthenticationTimeout(auth, CHALLENGE_TIMEOUT, correlationId);
        
        // Update metrics
        metricsService.recordChallengeInitiated(auth.getThreeDSVersion());
        
        log.info("Challenge initiated: paymentId={}, authId={}", payment.getId(), auth.getId());
    }
    
    /**
     * Processes challenge completion events
     */
    private void processChallengeCompleted(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        ThreeDSAuthentication auth = getAuthenticationRecord(payment.getThreeDSAuthenticationId());
        
        log.info("Processing challenge completion: paymentId={}, status={}", 
            payment.getId(), event.getAuthenticationStatus());
        
        // Update authentication record with challenge result
        auth.setChallengeCompletedAt(LocalDateTime.now());
        auth.setAuthenticationValue(event.getAuthenticationValue());
        auth.setElectronicCommerceIndicator(event.getEci());
        auth.setTransactionId(event.getTransactionId());
        
        // Process based on challenge result
        if ("SUCCESS".equals(event.getAuthenticationStatus())) {
            auth.setStatus(AuthenticationStatus.SUCCESS);
            auth.setAuthenticatedAt(LocalDateTime.now());
            authRepository.save(auth);
            
            processAuthenticationSuccess(event, correlationId);
            
        } else if ("FAILED".equals(event.getAuthenticationStatus())) {
            auth.setStatus(AuthenticationStatus.FAILED);
            auth.setFailureReason(event.getFailureReason());
            authRepository.save(auth);
            
            processAuthenticationFailed(event, correlationId);
            
        } else {
            log.warn("Unknown challenge completion status: {}", event.getAuthenticationStatus());
        }
        
        // Update metrics
        metricsService.recordChallengeCompleted(
            auth.getThreeDSVersion(),
            event.getAuthenticationStatus()
        );
    }
    
    /**
     * Processes frictionless authentication completion
     */
    private void processFrictionlessCompleted(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        ThreeDSAuthentication auth = getAuthenticationRecord(payment.getThreeDSAuthenticationId());
        
        log.info("Processing frictionless completion: paymentId={}, status={}", 
            payment.getId(), event.getAuthenticationStatus());
        
        // Update authentication record
        auth.setStatus("SUCCESS".equals(event.getAuthenticationStatus()) ? 
            AuthenticationStatus.SUCCESS : AuthenticationStatus.FAILED);
        auth.setFrictionlessCompletedAt(LocalDateTime.now());
        auth.setAuthenticationValue(event.getAuthenticationValue());
        auth.setElectronicCommerceIndicator(event.getEci());
        auth.setTransactionId(event.getTransactionId());
        authRepository.save(auth);
        
        // Process result
        if ("SUCCESS".equals(event.getAuthenticationStatus())) {
            processAuthenticationSuccess(event, correlationId);
        } else {
            processAuthenticationFailed(event, correlationId);
        }
        
        // Update metrics
        metricsService.recordFrictionlessCompleted(
            auth.getThreeDSVersion(),
            event.getAuthenticationStatus()
        );
    }
    
    /**
     * Processes successful authentication
     */
    private void processAuthenticationSuccess(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing authentication success: paymentId={}", payment.getId());
        
        // Update payment status and proceed with processing
        payment.setStatus(PaymentStatus.AUTHENTICATED);
        payment.setAuthenticatedAt(LocalDateTime.now());
        payment.setLiabilityShift(true); // Authentication success provides liability shift
        paymentRepository.save(payment);
        
        // Publish payment status update
        publishPaymentStatusUpdate(payment, "AUTHENTICATION_SUCCESS", correlationId);
        
        // Continue with payment processing
        kafkaTemplate.send("payment-processing-events", Map.of(
            "paymentId", payment.getId(),
            "eventType", "CONTINUE_PROCESSING",
            "authenticationCompleted", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        // Update fraud service with successful authentication
        paymentFraudService.recordSuccessfulAuthentication(payment.getId(), correlationId);
        
        // Update metrics
        metricsService.recordAuthenticationSuccess(payment.getAmount());
        
        log.info("Authentication successful: paymentId={}, liability shift: YES", payment.getId());
    }
    
    /**
     * Processes failed authentication
     */
    private void processAuthenticationFailed(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing authentication failure: paymentId={}, reason={}", 
            payment.getId(), event.getFailureReason());
        
        // Check if step-up authentication should be attempted
        if (shouldAttemptStepUp(payment, event)) {
            processStepUpRequired(event.toBuilder()
                .eventType("STEP_UP_REQUIRED")
                .stepUpReason("Primary authentication failed")
                .build(), correlationId);
            return;
        }
        
        // Update payment status
        payment.setStatus(PaymentStatus.AUTHENTICATION_FAILED);
        payment.setAuthenticationFailedAt(LocalDateTime.now());
        payment.setAuthenticationFailureReason(event.getFailureReason());
        payment.setLiabilityShift(false); // No liability shift on failure
        paymentRepository.save(payment);
        
        // Assess fraud risk for failed authentication
        double riskScore = paymentFraudService.assessFailedAuthenticationRisk(payment);
        
        if (riskScore >= HIGH_RISK_SCORE_THRESHOLD) {
            // High risk - block payment and send fraud alert
            payment.setStatus(PaymentStatus.BLOCKED);
            paymentRepository.save(payment);
            
            publishFraudAlert(payment, riskScore, "AUTHENTICATION_FAILURE_HIGH_RISK", correlationId);
            
        } else {
            // Lower risk - allow merchant to decide on processing
            publishPaymentStatusUpdate(payment, "AUTHENTICATION_FAILED", correlationId);
        }
        
        // Send customer notification
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Authentication Failed",
            "We were unable to authenticate your payment. Please try again or use a different payment method.",
            NotificationService.Priority.MEDIUM
        );
        
        // Update metrics
        metricsService.recordAuthenticationFailure(event.getFailureReason());
        
        log.info("Authentication failed: paymentId={}, riskScore={}", payment.getId(), riskScore);
    }
    
    /**
     * Processes authentication abandonment
     */
    private void processAuthenticationAbandoned(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        ThreeDSAuthentication auth = getAuthenticationRecord(payment.getThreeDSAuthenticationId());
        
        log.info("Processing authentication abandonment: paymentId={}", payment.getId());
        
        // Update authentication record
        auth.setStatus(AuthenticationStatus.ABANDONED);
        auth.setAbandonedAt(LocalDateTime.now());
        authRepository.save(auth);
        
        // Update payment status
        payment.setStatus(PaymentStatus.ABANDONED);
        payment.setAbandonedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // Publish payment status update
        publishPaymentStatusUpdate(payment, "AUTHENTICATION_ABANDONED", correlationId);
        
        // Update metrics
        metricsService.recordAuthenticationAbandonment();
        
        log.info("Authentication abandoned: paymentId={}", payment.getId());
    }
    
    /**
     * Processes exemption application
     */
    private void processExemptionApplied(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing exemption application: paymentId={}, exemption={}", 
            payment.getId(), event.getExemptionType());
        
        // Update payment with exemption information
        payment.setExemptionApplied(true);
        payment.setExemptionType(event.getExemptionType());
        payment.setExemptionReason(event.getExemptionReason());
        payment.setLiabilityShift(exemptionProvidesLiabilityShift(event.getExemptionType()));
        paymentRepository.save(payment);
        
        // Continue with payment processing
        kafkaTemplate.send("payment-processing-events", Map.of(
            "paymentId", payment.getId(),
            "eventType", "CONTINUE_PROCESSING",
            "exemptionApplied", true,
            "exemptionType", event.getExemptionType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        // Update exemption usage tracking
        updateExemptionUsage(payment, event.getExemptionType());
        
        // Update metrics
        metricsService.recordExemptionApplied(event.getExemptionType(), payment.getAmount());
        
        log.info("Exemption applied: paymentId={}, type={}, liability shift: {}",
            payment.getId(), event.getExemptionType(), payment.getLiabilityShift());
    }
    
    /**
     * Processes step-up authentication requirement
     */
    private void processStepUpRequired(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing step-up authentication: paymentId={}, reason={}", 
            payment.getId(), event.getStepUpReason());
        
        try {
            // Initiate step-up authentication
            String stepUpResult = stepUpService.initiateStepUpAuthentication(
                payment.getId(),
                event.getStepUpReason(),
                correlationId
            );
            
            // Update payment status
            payment.setStatus(PaymentStatus.STEP_UP_AUTHENTICATION);
            payment.setStepUpInitiatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Schedule step-up timeout
            scheduleAuthenticationTimeout(
                getAuthenticationRecord(payment.getThreeDSAuthenticationId()),
                STEP_UP_TIMEOUT,
                correlationId
            );
            
            log.info("Step-up authentication initiated: paymentId={}", payment.getId());
            
        } catch (Exception e) {
            log.error("Failed to initiate step-up authentication: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            // Fall back to authentication failure
            processAuthenticationFailed(event.toBuilder()
                .eventType("AUTHENTICATION_FAILED")
                .failureReason("Step-up authentication failed to initiate")
                .build(), correlationId);
        }
    }
    
    /**
     * Processes authentication timeout
     */
    private void processAuthenticationTimeout(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        ThreeDSAuthentication auth = getAuthenticationRecord(payment.getThreeDSAuthenticationId());
        
        log.warn("Processing authentication timeout: paymentId={}, timeoutType={}", 
            payment.getId(), event.getTimeoutType());
        
        // Update authentication record
        auth.setStatus(AuthenticationStatus.TIMEOUT);
        auth.setTimeoutAt(LocalDateTime.now());
        authRepository.save(auth);
        
        // Update payment status
        payment.setStatus(PaymentStatus.AUTHENTICATION_TIMEOUT);
        payment.setAuthenticationTimeoutAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // Publish payment status update
        publishPaymentStatusUpdate(payment, "AUTHENTICATION_TIMEOUT", correlationId);
        
        // Send customer notification
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Authentication Timed Out",
            "Your payment authentication session has expired. Please try again.",
            NotificationService.Priority.MEDIUM
        );
        
        // Update metrics
        metricsService.recordAuthenticationTimeout(event.getTimeoutType());
    }
    
    /**
     * Utility methods for 3DS processing
     */
    private void processFrictionlessAuthentication(Payment payment, ThreeDSAuthentication auth,
            String threeDSResponse, String correlationId) {
        
        // Process frictionless authentication result
        String authStatus = threeDSService.extractAuthenticationStatus(threeDSResponse);
        String authValue = threeDSService.extractAuthenticationValue(threeDSResponse);
        String eci = threeDSService.extractECI(threeDSResponse);
        
        Payment3DSAuthenticationEvent completionEvent = Payment3DSAuthenticationEvent.builder()
            .paymentId(payment.getId())
            .eventType("FRICTIONLESS_COMPLETED")
            .authenticationStatus(authStatus)
            .authenticationValue(authValue)
            .eci(eci)
            .transactionId(threeDSService.extractTransactionId(threeDSResponse))
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-3ds-authentication-events", completionEvent);
    }
    
    private Map<String, Object> prepare3DSAuthRequest(Payment payment, ThreeDSAuthentication auth) {
        return Map.of(
            "paymentId", payment.getId(),
            "amount", payment.getAmount(),
            "currency", payment.getCurrency(),
            "cardNumber", payment.getCardNumber(),
            "merchantId", payment.getMerchantId(),
            "threeDSVersion", auth.getThreeDSVersion(),
            "browserInfo", payment.getBrowserInfo() != null ? payment.getBrowserInfo() : Map.of(),
            "billingAddress", payment.getBillingAddress() != null ? payment.getBillingAddress() : Map.of()
        );
    }
    
    private double calculateTransactionRiskScore(Payment payment) {
        // Implement risk scoring algorithm
        double baseScore = 30.0; // Base risk score
        
        // Factor in amount
        if (payment.getAmount().compareTo(HIGH_RISK_THRESHOLD) > 0) {
            baseScore += 20.0;
        }
        
        // Factor in merchant risk
        if (payment.getMerchantRiskScore() != null) {
            baseScore += payment.getMerchantRiskScore() * 0.3;
        }
        
        // Factor in customer history
        if (payment.getCustomerRiskScore() != null) {
            baseScore += payment.getCustomerRiskScore() * 0.4;
        }
        
        // Factor in geographic risk
        if (isHighRiskRegion(payment.getRegion())) {
            baseScore += 15.0;
        }
        
        return Math.min(100.0, baseScore);
    }
    
    /**
     * Validation and utility methods
     */
    private void validateAuthenticationEvent(Payment3DSAuthenticationEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found: " + paymentId));
    }
    
    private ThreeDSAuthentication getAuthenticationRecord(String authId) {
        return authRepository.findById(authId)
            .orElseThrow(() -> new AuthenticationException(
                "Authentication record not found: " + authId));
    }
    
    private ThreeDSAuthentication createAuthenticationRecord(Payment payment, 
            Payment3DSAuthenticationEvent event, AuthenticationRequirement requirement, 
            String correlationId) {
        
        return ThreeDSAuthentication.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .threeDSVersion(event.getThreeDSVersion() != null ? event.getThreeDSVersion() : "2.1.0")
            .status(AuthenticationStatus.INITIATED)
            .initiatedAt(LocalDateTime.now())
            .required(requirement.isRequired())
            .exemptionType(requirement.getExemptionType())
            .exemptionReason(requirement.getExemptionReason())
            .correlationId(correlationId)
            .build();
    }
    
    private boolean isEEATransaction(Payment payment) {
        // Check if transaction is within European Economic Area
        List<String> eeaCountries = Arrays.asList("AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", 
            "ES", "FI", "FR", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", 
            "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK");
        
        return eeaCountries.contains(payment.getIssuerCountry()) && 
               eeaCountries.contains(payment.getAcquirerCountry());
    }
    
    private boolean canApplyLVTExemption(Payment payment) {
        // Check daily LVT exemption limits
        BigDecimal todayLVTAmount = paymentRepository.getTodayLVTExemptionAmount(
            payment.getCustomerId(), LocalDateTime.now().toLocalDate());
        int todayLVTCount = paymentRepository.getTodayLVTExemptionCount(
            payment.getCustomerId(), LocalDateTime.now().toLocalDate());
        
        return todayLVTCount < DAILY_LVT_EXEMPTION_LIMIT && 
               todayLVTAmount.add(payment.getAmount()).compareTo(DAILY_LVT_AMOUNT_LIMIT) <= 0;
    }
    
    private boolean isTrustedMerchant(String merchantId) {
        // Check if merchant is in trusted merchant list
        return merchantId != null && 
               paymentRepository.isTrustedMerchant(merchantId);
    }
    
    private boolean canApplyTRAExemption(Payment payment) {
        // Check Transaction Risk Analysis exemption eligibility
        return paymentRepository.getMerchantFraudRate(payment.getMerchantId()) < 0.013 && // 1.3%
               paymentRepository.getAcquirerFraudRate(payment.getAcquirerId()) < 0.06; // 6%
    }
    
    private boolean shouldAttemptStepUp(Payment payment, Payment3DSAuthenticationEvent event) {
        // Determine if step-up authentication should be attempted
        return payment.getAmount().compareTo(HIGH_RISK_THRESHOLD) > 0 &&
               !"HARD_DECLINE".equals(event.getFailureReason());
    }
    
    private boolean exemptionProvidesLiabilityShift(String exemptionType) {
        // Determine if exemption provides liability shift protection
        return "TRA".equals(exemptionType) || 
               "LOW_VALUE".equals(exemptionType) ||
               "RECURRING".equals(exemptionType);
    }
    
    private boolean isHighRiskRegion(String region) {
        List<String> highRiskRegions = Arrays.asList("XX", "YY", "ZZ"); // Configure as needed
        return highRiskRegions.contains(region);
    }
    
    private void updateExemptionUsage(Payment payment, String exemptionType) {
        // Track exemption usage for regulatory reporting
        kafkaTemplate.send("exemption-usage-events", Map.of(
            "customerId", payment.getCustomerId(),
            "merchantId", payment.getMerchantId(),
            "exemptionType", exemptionType,
            "amount", payment.getAmount(),
            "timestamp", Instant.now()
        ));
    }
    
    private void scheduleAuthenticationTimeout(ThreeDSAuthentication auth, Duration timeout, 
            String correlationId) {
        // Schedule timeout monitoring (implementation would use scheduler)
        log.debug("Scheduled authentication timeout: authId={}, timeout={}", 
            auth.getId(), timeout);
    }
    
    private void publishChallengeInitiated(Payment payment, ThreeDSAuthentication auth, 
            String correlationId) {
        
        Payment3DSAuthenticationEvent challengeEvent = Payment3DSAuthenticationEvent.builder()
            .paymentId(payment.getId())
            .eventType("CHALLENGE_INITIATED")
            .challengeUrl(auth.getChallengeUrl())
            .threeDSVersion(auth.getThreeDSVersion())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-3ds-authentication-events", challengeEvent);
    }
    
    private void sendChallengeNotification(Payment payment, ThreeDSAuthentication auth, 
            String correlationId) {
        
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Authentication Required",
            String.format("Please complete the authentication for your payment of $%s. " +
                "You will be redirected to complete this step.", payment.getAmount()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void handleAuthenticationFailure(Payment payment, ThreeDSAuthentication auth, 
            String reason, String correlationId) {
        
        payment.setStatus(PaymentStatus.AUTHENTICATION_FAILED);
        payment.setAuthenticationFailureReason(reason);
        paymentRepository.save(payment);
        
        publishPaymentStatusUpdate(payment, "AUTHENTICATION_FAILED", correlationId);
    }
    
    private void handleSuspicious3DSActivity(Payment3DSAuthenticationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setStatus(PaymentStatus.FRAUD_REVIEW);
        payment.setFraudReason("Suspicious 3DS authentication pattern");
        paymentRepository.save(payment);
        
        notificationService.sendSecurityAlert(
            "Suspicious 3DS Authentication Pattern",
            String.format("Unusual 3DS authentication pattern detected for payment %s", 
                event.getPaymentId()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void publishPaymentStatusUpdate(Payment payment, String reason, String correlationId) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus().toString())
            .reason(reason)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-status-updated-events", statusEvent);
    }
    
    private void publishFraudAlert(Payment payment, double riskScore, String reason, 
            String correlationId) {
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .paymentId(payment.getId())
            .riskScore(riskScore)
            .alertReason(reason)
            .severity("HIGH")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
    }
    
    private void handleAuthenticationEventError(Payment3DSAuthenticationEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-3ds-authentication-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "3DS Authentication Event Processing Failed",
            String.format("Failed to process 3DS authentication event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementAuthenticationEventError(event.getEventType());
    }
    
    /**
     * Inner classes for authentication logic
     */
    @lombok.Builder
    @lombok.Data
    public static class AuthenticationRequirement {
        private boolean required;
        private String exemptionType;
        private String exemptionReason;
        private String requirementReason;
        
        public static AuthenticationRequirement required(String type, String reason) {
            return AuthenticationRequirement.builder()
                .required(true)
                .requirementReason(reason)
                .build();
        }
        
        public static AuthenticationRequirement exempted(String exemptionType, String reason) {
            return AuthenticationRequirement.builder()
                .required(false)
                .exemptionType(exemptionType)
                .exemptionReason(reason)
                .build();
        }
    }
}