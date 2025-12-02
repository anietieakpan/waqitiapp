package com.waqiti.security.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.security.model.*;
import com.waqiti.security.repository.AccountTakeoverRepository;
import com.waqiti.security.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for account takeover detection
 * Handles real-time ATO detection, account protection, and automated response
 * 
 * Critical for: Account security, fraud prevention, identity protection
 * SLA: Must process ATO signals within 5 seconds for immediate account protection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountTakeoverDetectionConsumer {

    private final AccountTakeoverRepository atoRepository;
    private final ATODetectionService atoDetectionService;
    private final AccountProtectionService protectionService;
    private final IdentityVerificationService identityService;
    private final SessionManagementService sessionService;
    private final DeviceAnalysisService deviceAnalysisService;
    private final BehavioralAnalysisService behavioralService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Additional required services
    private final AccountService accountService;
    private final UserService userService;
    private final CaseManagementService caseManagementService;
    private final TransactionService transactionService;
    private final AuthService authService;
    private final MonitoringService monitoringService;
    private final DeviceService deviceService;
    private final IpBlockingService ipBlockingService;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final DashboardService dashboardService;
    private final RiskService riskService;
    private final EmergencyAlertService emergencyAlertService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FailedEventRepository failedEventRepository;
    private final AlertingService alertingService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 5000; // 5 seconds
    private static final Set<String> CRITICAL_ATO_INDICATORS = Set.of(
        "CONFIRMED_ATO", "CREDENTIAL_COMPROMISE", "SESSION_HIJACK", 
        "ACCOUNT_MANIPULATION", "UNAUTHORIZED_ACCESS_CONFIRMED", "IDENTITY_THEFT"
    );
    
    @KafkaListener(
        topics = {"account-takeover-detection"},
        groupId = "account-takeover-detection-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "account-takeover-detection-processor", fallbackMethod = "handleATODetectionFailure")
    @Retry(name = "account-takeover-detection-processor")
    public void processAccountTakeoverDetection(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing ATO detection: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ATODetectionRequest request = extractATODetectionRequest(payload);
            
            // Validate ATO detection request
            validateATORequest(request);
            
            // Check for duplicate request
            if (isDuplicateRequest(request)) {
                log.warn("Duplicate ATO detection request: {}, skipping", request.getRequestId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich request with contextual data
            ATODetectionRequest enrichedRequest = enrichATORequest(request);
            
            // Perform comprehensive ATO detection
            ATODetectionResult detectionResult = performATODetection(enrichedRequest);
            
            // Process ATO detection result
            ATOProcessingResult result = processATODetection(enrichedRequest, detectionResult);
            
            // Execute immediate protection measures
            if (detectionResult.requiresImmediateAction()) {
                executeImmediateProtection(enrichedRequest, detectionResult, result);
            }
            
            // Perform account recovery if needed
            if (detectionResult.requiresAccountRecovery()) {
                initiateAccountRecovery(enrichedRequest, detectionResult, result);
            }
            
            // Analyze attack patterns
            if (detectionResult.enablesPatternAnalysis()) {
                analyzeATOPatterns(enrichedRequest, detectionResult, result);
            }
            
            // Update security controls
            updateSecurityControls(enrichedRequest, detectionResult);
            
            // Trigger automated workflows
            if (detectionResult.hasAutomatedWorkflows()) {
                triggerATOWorkflows(enrichedRequest, detectionResult);
            }
            
            // Send ATO notifications
            sendATONotifications(enrichedRequest, detectionResult, result);
            
            // Update monitoring systems
            updateMonitoringSystems(enrichedRequest, result);
            
            // Audit ATO detection
            auditATODetection(enrichedRequest, result, event);
            
            // Record metrics
            recordATOMetrics(enrichedRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed ATO detection: {} account: {} indicators: {} confidence: {} in {}ms", 
                    enrichedRequest.getRequestId(), enrichedRequest.getAccountId(), 
                    detectionResult.getIndicators().size(), detectionResult.getConfidence(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for ATO detection: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalATOException e) {
            log.error("Critical ATO processing failed: {}", eventId, e);
            handleCriticalATOError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process ATO detection: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ATODetectionRequest extractATODetectionRequest(Map<String, Object> payload) {
        return ATODetectionRequest.builder()
            .requestId(extractString(payload, "requestId", UUID.randomUUID().toString()))
            .accountId(extractString(payload, "accountId", null))
            .userId(extractString(payload, "userId", null))
            .sessionId(extractString(payload, "sessionId", null))
            .detectionTrigger(extractString(payload, "detectionTrigger", null))
            .sourceIp(extractString(payload, "sourceIp", null))
            .userAgent(extractString(payload, "userAgent", null))
            .deviceId(extractString(payload, "deviceId", null))
            .deviceFingerprint(extractString(payload, "deviceFingerprint", null))
            .country(extractString(payload, "country", null))
            .region(extractString(payload, "region", null))
            .city(extractString(payload, "city", null))
            .activityType(extractString(payload, "activityType", null))
            .suspiciousActivities(extractStringList(payload, "suspiciousActivities"))
            .behavioralChanges(extractStringList(payload, "behavioralChanges"))
            .recentTransactions(extractTransactionList(payload, "recentTransactions"))
            .loginAttempts(extractInteger(payload, "loginAttempts", 0))
            .failedAttempts(extractInteger(payload, "failedAttempts", 0))
            .passwordChangeAttempts(extractInteger(payload, "passwordChangeAttempts", 0))
            .emailChangeAttempts(extractInteger(payload, "emailChangeAttempts", 0))
            .phoneChangeAttempts(extractInteger(payload, "phoneChangeAttempts", 0))
            .beneficiaryAdditions(extractInteger(payload, "beneficiaryAdditions", 0))
            .unusualTransferAmount(extractBigDecimal(payload, "unusualTransferAmount"))
            .riskScore(extractInteger(payload, "riskScore", 0))
            .urgency(ATOUrgency.fromString(extractString(payload, "urgency", "NORMAL")))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .metadata(extractMap(payload, "metadata"))
            .detectedAt(extractInstant(payload, "detectedAt"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateATORequest(ATODetectionRequest request) {
        if (request.getAccountId() == null || request.getAccountId().isEmpty()) {
            throw new ValidationException("Account ID is required");
        }
        
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        
        if (request.getDetectionTrigger() == null || request.getDetectionTrigger().isEmpty()) {
            throw new ValidationException("Detection trigger is required");
        }
        
        if (request.getDetectedAt() == null) {
            throw new ValidationException("Detection timestamp is required");
        }
        
        // Validate account exists
        if (!accountService.accountExists(request.getAccountId())) {
            throw new ValidationException("Account not found: " + request.getAccountId());
        }
        
        // Validate user exists
        if (!userService.userExists(request.getUserId())) {
            throw new ValidationException("User not found: " + request.getUserId());
        }
    }

    private boolean isDuplicateRequest(ATODetectionRequest request) {
        return atoRepository.existsSimilarRequest(
            request.getAccountId(),
            request.getDetectionTrigger(),
            Instant.now().minus(15, ChronoUnit.MINUTES)
        );
    }

    private ATODetectionRequest enrichATORequest(ATODetectionRequest request) {
        // Enrich with account information
        AccountProfile accountProfile = accountService.getAccountProfile(request.getAccountId());
        if (accountProfile != null) {
            request.setAccountType(accountProfile.getAccountType());
            request.setAccountAge(accountProfile.getAccountAge());
            request.setAccountValue(accountProfile.getTotalValue());
            request.setAccountStatus(accountProfile.getStatus());
            request.setLastActivityDate(accountProfile.getLastActivityDate());
        }
        
        // Enrich with user profile data
        UserSecurityProfile userProfile = userService.getUserSecurityProfile(request.getUserId());
        if (userProfile != null) {
            request.setUserRiskLevel(userProfile.getRiskLevel());
            request.setUserTrustScore(userProfile.getTrustScore());
            request.setLastSuccessfulLogin(userProfile.getLastSuccessfulLogin());
            request.setTypicalActivityPatterns(userProfile.getTypicalActivityPatterns());
        }
        
        // Enrich with device information
        if (request.getDeviceId() != null) {
            DeviceProfile deviceProfile = deviceAnalysisService.getDeviceProfile(request.getDeviceId());
            if (deviceProfile != null) {
                request.setKnownDevice(deviceProfile.isKnownDevice());
                request.setDeviceRiskScore(deviceProfile.getRiskScore());
                request.setDeviceLastSeen(deviceProfile.getLastSeen());
            }
        }
        
        // Enrich with recent ATO history
        ATOHistory atoHistory = atoRepository.getATOHistory(
            request.getAccountId(),
            Instant.now().minus(90, ChronoUnit.DAYS)
        );
        
        request.setPreviousATOAttempts(atoHistory.getTotalAttempts());
        request.setPreviousATOConfirmed(atoHistory.getConfirmedATOs());
        request.setLastATOAttemptDate(atoHistory.getLastAttemptDate());
        
        // Enrich with behavioral baseline
        BehavioralBaseline baseline = behavioralService.getUserBaseline(request.getUserId());
        request.setBehavioralBaseline(baseline);
        
        return request;
    }

    private ATODetectionResult performATODetection(ATODetectionRequest request) {
        ATODetectionResult result = new ATODetectionResult();
        result.setRequestId(request.getRequestId());
        result.setAccountId(request.getAccountId());
        result.setDetectionTime(Instant.now());
        
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Account activity indicators
        indicators.addAll(detectAccountActivityIndicators(request));
        
        // Authentication anomaly indicators
        indicators.addAll(detectAuthenticationAnomalies(request));
        
        // Behavioral change indicators
        indicators.addAll(detectBehavioralChanges(request));
        
        // Device and location indicators
        indicators.addAll(detectDeviceLocationIndicators(request));
        
        // Transaction pattern indicators
        indicators.addAll(detectTransactionPatternIndicators(request));
        
        // Profile modification indicators
        indicators.addAll(detectProfileModificationIndicators(request));
        
        // Session anomaly indicators
        indicators.addAll(detectSessionAnomalies(request));
        
        result.setIndicators(indicators);
        
        // Calculate ATO confidence score
        double confidence = calculateATOConfidence(indicators, request);
        result.setConfidence(confidence);
        
        // Determine ATO likelihood
        ATOLikelihood likelihood = determineATOLikelihood(confidence, indicators);
        result.setLikelihood(likelihood);
        
        // Identify attack vector
        ATOAttackVector attackVector = identifyAttackVector(indicators, request);
        result.setAttackVector(attackVector);
        
        // Determine required actions
        result.setRequiresImmediateAction(requiresImmediateAction(likelihood, indicators));
        result.setRequiresAccountRecovery(requiresAccountRecovery(likelihood, confidence));
        result.setEnablesPatternAnalysis(enablesPatternAnalysis(indicators));
        result.setHasAutomatedWorkflows(hasAutomatedWorkflows(likelihood));
        
        // Generate recommendations
        result.setRecommendations(generateATORecommendations(likelihood, indicators, attackVector));
        
        return result;
    }

    private List<ATOIndicator> detectAccountActivityIndicators(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Unusual login patterns
        if (request.getLoginAttempts() > 10) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("EXCESSIVE_LOGIN_ATTEMPTS")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.85)
                .description("Excessive login attempts detected")
                .evidence(Map.of("login_attempts", request.getLoginAttempts()))
                .weight(0.8)
                .build());
        }
        
        // High failure rate
        if (request.getFailedAttempts() > 0) {
            double failureRate = (double) request.getFailedAttempts() / 
                                (request.getLoginAttempts() > 0 ? request.getLoginAttempts() : 1);
            if (failureRate > 0.5) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("HIGH_FAILURE_RATE")
                    .severity(IndicatorSeverity.MEDIUM)
                    .confidence(0.7)
                    .description("High authentication failure rate")
                    .evidence(Map.of(
                        "failure_rate", failureRate,
                        "failed_attempts", request.getFailedAttempts()
                    ))
                    .weight(0.6)
                    .build());
            }
        }
        
        // Password change attempts
        if (request.getPasswordChangeAttempts() > 2) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("MULTIPLE_PASSWORD_CHANGES")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.9)
                .description("Multiple password change attempts")
                .evidence(Map.of("attempts", request.getPasswordChangeAttempts()))
                .weight(0.85)
                .build());
        }
        
        // Email/phone change attempts
        if (request.getEmailChangeAttempts() > 0 || request.getPhoneChangeAttempts() > 0) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("CONTACT_INFO_CHANGE_ATTEMPT")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.88)
                .description("Attempted to change contact information")
                .evidence(Map.of(
                    "email_changes", request.getEmailChangeAttempts(),
                    "phone_changes", request.getPhoneChangeAttempts()
                ))
                .weight(0.9)
                .build());
        }
        
        // New beneficiary additions
        if (request.getBeneficiaryAdditions() > 0) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("NEW_BENEFICIARY_ADDED")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.85)
                .description("New beneficiary added to account")
                .evidence(Map.of("additions", request.getBeneficiaryAdditions()))
                .weight(0.85)
                .build());
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectAuthenticationAnomalies(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Time-based anomalies
        if (request.getLastSuccessfulLogin() != null) {
            long hoursSinceLastLogin = ChronoUnit.HOURS.between(
                request.getLastSuccessfulLogin(),
                request.getDetectedAt()
            );
            
            // Long dormancy followed by sudden activity
            if (hoursSinceLastLogin > 720) { // 30 days
                indicators.add(ATOIndicator.builder()
                    .indicatorType("DORMANT_ACCOUNT_REACTIVATION")
                    .severity(IndicatorSeverity.MEDIUM)
                    .confidence(0.65)
                    .description("Account reactivated after long dormancy")
                    .evidence(Map.of("days_dormant", hoursSinceLastLogin / 24))
                    .weight(0.55)
                    .build());
            }
        }
        
        // Unusual authentication method
        AuthenticationAnalysis authAnalysis = atoDetectionService.analyzeAuthentication(
            request.getUserId(),
            request.getSessionId()
        );
        
        if (authAnalysis.hasUnusualMethod()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("UNUSUAL_AUTH_METHOD")
                .severity(IndicatorSeverity.MEDIUM)
                .confidence(0.7)
                .description("Authentication using unusual method")
                .evidence(Map.of(
                    "method", authAnalysis.getAuthMethod(),
                    "typical_methods", authAnalysis.getTypicalMethods()
                ))
                .weight(0.6)
                .build());
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectBehavioralChanges(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        if (request.getBehavioralBaseline() != null && request.getBehavioralChanges() != null) {
            // Navigation pattern changes
            if (request.getBehavioralChanges().contains("NAVIGATION_PATTERN_CHANGE")) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("NAVIGATION_PATTERN_CHANGE")
                    .severity(IndicatorSeverity.MEDIUM)
                    .confidence(0.7)
                    .description("Significant change in navigation patterns")
                    .evidence(Map.of("changes", "Navigation sequence differs from baseline"))
                    .weight(0.65)
                    .build());
            }
            
            // Typing pattern changes
            if (request.getBehavioralChanges().contains("TYPING_PATTERN_CHANGE")) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("TYPING_BIOMETRIC_MISMATCH")
                    .severity(IndicatorSeverity.HIGH)
                    .confidence(0.8)
                    .description("Typing biometric pattern mismatch")
                    .evidence(Map.of("changes", "Keystroke dynamics differ significantly"))
                    .weight(0.75)
                    .build());
            }
            
            // Mouse movement changes
            if (request.getBehavioralChanges().contains("MOUSE_PATTERN_CHANGE")) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("MOUSE_MOVEMENT_ANOMALY")
                    .severity(IndicatorSeverity.MEDIUM)
                    .confidence(0.65)
                    .description("Mouse movement pattern anomaly")
                    .evidence(Map.of("changes", "Mouse movement differs from baseline"))
                    .weight(0.6)
                    .build());
            }
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectDeviceLocationIndicators(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Unknown device
        if (!request.isKnownDevice()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("UNKNOWN_DEVICE")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.85)
                .description("Access from unknown device")
                .evidence(Map.of(
                    "device_id", request.getDeviceId(),
                    "device_fingerprint", request.getDeviceFingerprint()
                ))
                .weight(0.8)
                .build());
        }
        
        // High-risk device
        if (request.getDeviceRiskScore() != null && request.getDeviceRiskScore() > 75) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("HIGH_RISK_DEVICE")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.8)
                .description("Access from high-risk device")
                .evidence(Map.of("device_risk_score", request.getDeviceRiskScore()))
                .weight(0.75)
                .build());
        }
        
        // Location anomalies
        LocationAnalysis locationAnalysis = atoDetectionService.analyzeLocation(
            request.getUserId(),
            request.getCountry(),
            request.getCity()
        );
        
        if (locationAnalysis.isHighRisk()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("HIGH_RISK_LOCATION")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.75)
                .description("Access from high-risk location")
                .evidence(Map.of(
                    "location", request.getCountry() + ", " + request.getCity(),
                    "risk_factors", locationAnalysis.getRiskFactors()
                ))
                .weight(0.7)
                .build());
        }
        
        if (locationAnalysis.isNewLocation()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("NEW_LOCATION")
                .severity(IndicatorSeverity.MEDIUM)
                .confidence(0.6)
                .description("Access from new location")
                .evidence(Map.of(
                    "new_location", request.getCountry() + ", " + request.getCity(),
                    "typical_locations", locationAnalysis.getTypicalLocations()
                ))
                .weight(0.5)
                .build());
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectTransactionPatternIndicators(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Unusual transfer amount
        if (request.getUnusualTransferAmount() != null && 
            request.getUnusualTransferAmount().compareTo(BigDecimal.ZERO) > 0) {
            
            TransactionAnalysis txAnalysis = atoDetectionService.analyzeTransactionPattern(
                request.getAccountId(),
                request.getUnusualTransferAmount()
            );
            
            if (txAnalysis.isUnusualAmount()) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("UNUSUAL_TRANSACTION_AMOUNT")
                    .severity(IndicatorSeverity.HIGH)
                    .confidence(0.82)
                    .description("Unusual transaction amount detected")
                    .evidence(Map.of(
                        "amount", request.getUnusualTransferAmount(),
                        "typical_range", txAnalysis.getTypicalRange(),
                        "deviation", txAnalysis.getDeviationPercentage()
                    ))
                    .weight(0.8)
                    .build());
            }
        }
        
        // Rapid transactions
        if (request.getRecentTransactions() != null && request.getRecentTransactions().size() > 5) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("RAPID_TRANSACTIONS")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.75)
                .description("Rapid succession of transactions")
                .evidence(Map.of(
                    "transaction_count", request.getRecentTransactions().size(),
                    "time_window", "1 hour"
                ))
                .weight(0.7)
                .build());
        }
        
        // Money movement patterns
        MoneyMovementAnalysis moneyAnalysis = atoDetectionService.analyzeMoneyMovement(
            request.getAccountId(),
            request.getRecentTransactions()
        );
        
        if (moneyAnalysis.hasExfiltrationPattern()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("MONEY_EXFILTRATION_PATTERN")
                .severity(IndicatorSeverity.CRITICAL)
                .confidence(0.9)
                .description("Money exfiltration pattern detected")
                .evidence(Map.of(
                    "pattern", moneyAnalysis.getPatternType(),
                    "total_amount", moneyAnalysis.getTotalAmount()
                ))
                .weight(0.95)
                .build());
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectProfileModificationIndicators(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        // Profile modification analysis
        ProfileModificationAnalysis profileAnalysis = atoDetectionService.analyzeProfileModifications(
            request.getAccountId(),
            request.getDetectedAt().minus(1, ChronoUnit.HOURS)
        );
        
        if (profileAnalysis.hasSecuritySettingsChange()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("SECURITY_SETTINGS_MODIFIED")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.85)
                .description("Security settings were modified")
                .evidence(Map.of(
                    "changes", profileAnalysis.getSecurityChanges()
                ))
                .weight(0.85)
                .build());
        }
        
        if (profileAnalysis.hasNotificationSettingsChange()) {
            indicators.add(ATOIndicator.builder()
                .indicatorType("NOTIFICATION_SETTINGS_DISABLED")
                .severity(IndicatorSeverity.HIGH)
                .confidence(0.8)
                .description("Notification settings were disabled")
                .evidence(Map.of(
                    "disabled_notifications", profileAnalysis.getDisabledNotifications()
                ))
                .weight(0.75)
                .build());
        }
        
        return indicators;
    }

    private List<ATOIndicator> detectSessionAnomalies(ATODetectionRequest request) {
        List<ATOIndicator> indicators = new ArrayList<>();
        
        if (request.getSessionId() != null) {
            SessionAnalysis sessionAnalysis = sessionService.analyzeSession(request.getSessionId());
            
            // Session hijacking indicators
            if (sessionAnalysis.hasIpChange()) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("SESSION_IP_CHANGE")
                    .severity(IndicatorSeverity.HIGH)
                    .confidence(0.85)
                    .description("Session IP address changed mid-session")
                    .evidence(Map.of(
                        "original_ip", sessionAnalysis.getOriginalIp(),
                        "current_ip", request.getSourceIp()
                    ))
                    .weight(0.85)
                    .build());
            }
            
            if (sessionAnalysis.hasUserAgentChange()) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("SESSION_USER_AGENT_CHANGE")
                    .severity(IndicatorSeverity.HIGH)
                    .confidence(0.8)
                    .description("User agent changed mid-session")
                    .evidence(Map.of(
                        "original_user_agent", sessionAnalysis.getOriginalUserAgent(),
                        "current_user_agent", request.getUserAgent()
                    ))
                    .weight(0.8)
                    .build());
            }
            
            if (sessionAnalysis.hasAnomalousActivity()) {
                indicators.add(ATOIndicator.builder()
                    .indicatorType("SESSION_ANOMALY")
                    .severity(IndicatorSeverity.MEDIUM)
                    .confidence(0.7)
                    .description("Anomalous session activity detected")
                    .evidence(Map.of(
                        "anomaly_type", sessionAnalysis.getAnomalyType(),
                        "confidence", sessionAnalysis.getAnomalyConfidence()
                    ))
                    .weight(0.65)
                    .build());
            }
        }
        
        return indicators;
    }

    private double calculateATOConfidence(List<ATOIndicator> indicators, ATODetectionRequest request) {
        if (indicators.isEmpty()) {
            return 0.1; // Minimal confidence for no indicators
        }
        
        // Weighted confidence calculation
        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;
        
        for (ATOIndicator indicator : indicators) {
            double score = indicator.getConfidence() * getIndicatorSeverityMultiplier(indicator.getSeverity());
            totalWeightedScore += score * indicator.getWeight();
            totalWeight += indicator.getWeight();
        }
        
        double baseConfidence = totalWeightedScore / totalWeight;
        
        // Boost confidence for critical indicators
        boolean hasCriticalIndicator = indicators.stream()
            .anyMatch(i -> CRITICAL_ATO_INDICATORS.contains(i.getIndicatorType()));
        
        if (hasCriticalIndicator) {
            baseConfidence = Math.min(1.0, baseConfidence + 0.2);
        }
        
        // Boost confidence for multiple strong indicators
        long strongIndicators = indicators.stream()
            .filter(i -> i.getConfidence() > 0.8)
            .count();
        
        if (strongIndicators >= 3) {
            baseConfidence = Math.min(1.0, baseConfidence + 0.15);
        }
        
        // Adjust based on historical ATO attempts
        if (request.getPreviousATOConfirmed() > 0) {
            baseConfidence = Math.min(1.0, baseConfidence + 0.1);
        }
        
        return baseConfidence;
    }

    private double getIndicatorSeverityMultiplier(IndicatorSeverity severity) {
        switch (severity) {
            case CRITICAL: return 1.0;
            case HIGH: return 0.85;
            case MEDIUM: return 0.7;
            case LOW: return 0.5;
            default: return 0.5;
        }
    }

    private ATOLikelihood determineATOLikelihood(double confidence, List<ATOIndicator> indicators) {
        // Critical indicators override confidence calculation
        boolean hasCriticalIndicator = indicators.stream()
            .anyMatch(i -> i.getSeverity() == IndicatorSeverity.CRITICAL);
        
        if (hasCriticalIndicator || confidence >= 0.85) {
            return ATOLikelihood.CONFIRMED;
        }
        
        if (confidence >= 0.7) {
            return ATOLikelihood.HIGHLY_LIKELY;
        }
        
        if (confidence >= 0.5) {
            return ATOLikelihood.LIKELY;
        }
        
        if (confidence >= 0.3) {
            return ATOLikelihood.POSSIBLE;
        }
        
        return ATOLikelihood.UNLIKELY;
    }

    private ATOAttackVector identifyAttackVector(List<ATOIndicator> indicators, ATODetectionRequest request) {
        // Analyze indicators to identify attack vector
        Map<String, Integer> vectorScores = new HashMap<>();
        
        for (ATOIndicator indicator : indicators) {
            switch (indicator.getIndicatorType()) {
                case "EXCESSIVE_LOGIN_ATTEMPTS":
                case "HIGH_FAILURE_RATE":
                    vectorScores.merge("CREDENTIAL_STUFFING", 1, Integer::sum);
                    break;
                    
                case "SESSION_IP_CHANGE":
                case "SESSION_USER_AGENT_CHANGE":
                    vectorScores.merge("SESSION_HIJACKING", 1, Integer::sum);
                    break;
                    
                case "MULTIPLE_PASSWORD_CHANGES":
                case "CONTACT_INFO_CHANGE_ATTEMPT":
                    vectorScores.merge("CREDENTIAL_COMPROMISE", 1, Integer::sum);
                    break;
                    
                case "UNKNOWN_DEVICE":
                case "HIGH_RISK_DEVICE":
                    vectorScores.merge("DEVICE_COMPROMISE", 1, Integer::sum);
                    break;
                    
                case "TYPING_BIOMETRIC_MISMATCH":
                case "NAVIGATION_PATTERN_CHANGE":
                    vectorScores.merge("ACCOUNT_COMPROMISE", 1, Integer::sum);
                    break;
                    
                default:
                    vectorScores.merge("UNKNOWN", 1, Integer::sum);
            }
        }
        
        // Return most likely attack vector
        return vectorScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> ATOAttackVector.valueOf(e.getKey()))
            .orElse(ATOAttackVector.UNKNOWN);
    }

    private boolean requiresImmediateAction(ATOLikelihood likelihood, List<ATOIndicator> indicators) {
        return likelihood == ATOLikelihood.CONFIRMED ||
               likelihood == ATOLikelihood.HIGHLY_LIKELY ||
               indicators.stream().anyMatch(i -> i.getSeverity() == IndicatorSeverity.CRITICAL);
    }

    private boolean requiresAccountRecovery(ATOLikelihood likelihood, double confidence) {
        return likelihood == ATOLikelihood.CONFIRMED ||
               (likelihood == ATOLikelihood.HIGHLY_LIKELY && confidence > 0.8);
    }

    private boolean enablesPatternAnalysis(List<ATOIndicator> indicators) {
        return indicators.size() >= 3 ||
               indicators.stream().anyMatch(i -> i.getConfidence() > 0.8);
    }

    private boolean hasAutomatedWorkflows(ATOLikelihood likelihood) {
        return likelihood != ATOLikelihood.UNLIKELY;
    }

    private List<String> generateATORecommendations(ATOLikelihood likelihood, 
                                                   List<ATOIndicator> indicators, 
                                                   ATOAttackVector attackVector) {
        List<String> recommendations = new ArrayList<>();
        
        switch (likelihood) {
            case CONFIRMED:
                recommendations.add("IMMEDIATE_ACCOUNT_LOCKDOWN");
                recommendations.add("FORCE_PASSWORD_RESET");
                recommendations.add("TERMINATE_ALL_SESSIONS");
                recommendations.add("INITIATE_ACCOUNT_RECOVERY");
                recommendations.add("NOTIFY_USER_IMMEDIATELY");
                break;
                
            case HIGHLY_LIKELY:
                recommendations.add("TEMPORARY_ACCOUNT_FREEZE");
                recommendations.add("REQUIRE_IDENTITY_VERIFICATION");
                recommendations.add("ENHANCED_MONITORING");
                recommendations.add("NOTIFY_USER");
                break;
                
            case LIKELY:
                recommendations.add("REQUIRE_ADDITIONAL_AUTHENTICATION");
                recommendations.add("INCREASE_MONITORING");
                recommendations.add("REVIEW_RECENT_ACTIVITY");
                break;
                
            case POSSIBLE:
                recommendations.add("MONITOR_ACCOUNT");
                recommendations.add("FLAG_FOR_REVIEW");
                break;
                
            default:
                recommendations.add("CONTINUE_NORMAL_MONITORING");
        }
        
        // Attack vector specific recommendations
        switch (attackVector) {
            case CREDENTIAL_STUFFING:
                recommendations.add("IMPLEMENT_RATE_LIMITING");
                recommendations.add("REQUIRE_CAPTCHA");
                break;
                
            case SESSION_HIJACKING:
                recommendations.add("INVALIDATE_CURRENT_SESSION");
                recommendations.add("IMPLEMENT_SESSION_BINDING");
                break;
                
            case CREDENTIAL_COMPROMISE:
                recommendations.add("FORCE_PASSWORD_CHANGE");
                recommendations.add("REVIEW_SECURITY_QUESTIONS");
                break;
                
            case DEVICE_COMPROMISE:
                recommendations.add("REQUIRE_DEVICE_VERIFICATION");
                recommendations.add("IMPLEMENT_DEVICE_FINGERPRINTING");
                break;
        }
        
        return recommendations.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    private ATOProcessingResult processATODetection(ATODetectionRequest request, 
                                                  ATODetectionResult detectionResult) {
        ATOProcessingResult result = new ATOProcessingResult();
        result.setRequestId(request.getRequestId());
        result.setAccountId(request.getAccountId());
        result.setDetectionResult(detectionResult);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save ATO detection event
            ATOEvent atoEvent = ATOEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .userId(request.getUserId())
                .likelihood(detectionResult.getLikelihood())
                .confidence(detectionResult.getConfidence())
                .attackVector(detectionResult.getAttackVector())
                .indicators(detectionResult.getIndicators())
                .status(ATOStatus.DETECTED)
                .detectedAt(request.getDetectedAt())
                .createdAt(Instant.now())
                .build();
            
            ATOEvent savedEvent = atoRepository.saveATOEvent(atoEvent);
            result.setSavedEvent(savedEvent);
            
            // Create investigation case for likely ATOs
            if (detectionResult.getLikelihood().ordinal() >= ATOLikelihood.LIKELY.ordinal()) {
                ATOCase atoCase = caseManagementService.createATOCase(
                    request,
                    detectionResult,
                    "ATO_INVESTIGATION_TEAM"
                );
                result.setInvestigationCase(atoCase);
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process ATO detection: {}", request.getRequestId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ATOProcessingException("ATO processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private void executeImmediateProtection(ATODetectionRequest request, ATODetectionResult detectionResult, 
                                          ATOProcessingResult result) {
        List<String> protectionMeasures = new ArrayList<>();
        
        // Account lockdown for confirmed ATO
        if (detectionResult.getLikelihood() == ATOLikelihood.CONFIRMED) {
            protectionService.lockdownAccount(request.getAccountId(), "CONFIRMED_ATO");
            protectionMeasures.add("ACCOUNT_LOCKED_DOWN");
            
            // Terminate all active sessions
            sessionService.terminateAllSessions(request.getUserId(), "ATO_DETECTED");
            protectionMeasures.add("SESSIONS_TERMINATED");
            
            // Block all pending transactions
            transactionService.blockPendingTransactions(request.getAccountId(), "ATO_PROTECTION");
            protectionMeasures.add("TRANSACTIONS_BLOCKED");
        }
        
        // Temporary freeze for highly likely ATO
        else if (detectionResult.getLikelihood() == ATOLikelihood.HIGHLY_LIKELY) {
            protectionService.temporaryAccountFreeze(request.getAccountId(), 24); // 24 hours
            protectionMeasures.add("ACCOUNT_TEMPORARILY_FROZEN");
            
            // Require step-up authentication
            authService.requireStepUpAuth(request.getUserId(), "ATO_RISK");
            protectionMeasures.add("STEP_UP_AUTH_REQUIRED");
        }
        
        // Enhanced monitoring for likely ATO
        else if (detectionResult.getLikelihood() == ATOLikelihood.LIKELY) {
            monitoringService.enableEnhancedMonitoring(request.getAccountId(), "ATO_RISK");
            protectionMeasures.add("ENHANCED_MONITORING_ENABLED");
        }
        
        // Device-specific protections
        if (request.getDeviceId() != null && !request.isKnownDevice()) {
            deviceService.blockDevice(request.getDeviceId(), "UNKNOWN_DEVICE_ATO");
            protectionMeasures.add("DEVICE_BLOCKED");
        }
        
        // IP-specific protections
        if (request.getSourceIp() != null && detectionResult.getConfidence() > 0.7) {
            ipBlockingService.blockIp(request.getSourceIp(), "ATO_SOURCE", 72); // 72 hours
            protectionMeasures.add("IP_BLOCKED");
        }
        
        result.setProtectionMeasures(protectionMeasures);
    }

    private void initiateAccountRecovery(ATODetectionRequest request, ATODetectionResult detectionResult, 
                                       ATOProcessingResult result) {
        AccountRecoveryProcess recovery = identityService.initiateAccountRecovery(
            request.getAccountId(),
            request.getUserId(),
            RecoveryReason.ATO_DETECTED
        );
        
        result.setAccountRecovery(recovery);
        
        // Generate recovery token
        String recoveryToken = identityService.generateSecureRecoveryToken(
            request.getUserId(),
            24 // 24 hours validity
        );
        
        // Send recovery notifications through verified channels
        notificationService.sendAccountRecoveryNotification(
            request.getUserId(),
            recoveryToken,
            recovery.getRecoveryId()
        );
    }

    private void analyzeATOPatterns(ATODetectionRequest request, ATODetectionResult detectionResult, 
                                  ATOProcessingResult result) {
        // Analyze attack patterns for threat intelligence
        ATOPatternAnalysis patternAnalysis = atoDetectionService.analyzeATOPatterns(
            request.getAccountId(),
            detectionResult.getIndicators(),
            detectionResult.getAttackVector()
        );
        
        result.setPatternAnalysis(patternAnalysis);
        
        // Update threat intelligence
        if (patternAnalysis.hasNewPattern()) {
            threatIntelligenceService.updateATOPatterns(patternAnalysis);
        }
        
        // Check for coordinated attacks
        if (patternAnalysis.suggestsCoordinatedAttack()) {
            List<String> relatedAccounts = atoDetectionService.findRelatedATOTargets(
                request.getSourceIp(),
                request.getDeviceFingerprint(),
                detectionResult.getAttackVector()
            );
            
            if (!relatedAccounts.isEmpty()) {
                // Protect related accounts
                for (String accountId : relatedAccounts) {
                    protectionService.applyPreventiveProtection(accountId, "COORDINATED_ATO_RISK");
                }
                
                result.setRelatedAccountsProtected(relatedAccounts.size());
            }
        }
    }

    private void updateSecurityControls(ATODetectionRequest request, ATODetectionResult detectionResult) {
        // Update authentication requirements
        if (detectionResult.getLikelihood().ordinal() >= ATOLikelihood.LIKELY.ordinal()) {
            authService.updateAuthRequirements(
                request.getUserId(),
                AuthRequirementLevel.ENHANCED
            );
        }
        
        // Update session policies
        if (detectionResult.getAttackVector() == ATOAttackVector.SESSION_HIJACKING) {
            sessionService.updateSessionPolicy(
                request.getUserId(),
                SessionPolicy.STRICT_BINDING
            );
        }
        
        // Update device trust levels
        if (request.getDeviceId() != null) {
            deviceService.updateDeviceTrust(
                request.getDeviceId(),
                Math.max(0, 100 - (int)(detectionResult.getConfidence() * 100))
            );
        }
    }

    private void triggerATOWorkflows(ATODetectionRequest request, ATODetectionResult detectionResult) {
        List<String> workflows = getATOWorkflows(detectionResult.getLikelihood(), detectionResult.getAttackVector());
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, request, detectionResult);
                } catch (Exception e) {
                    log.error("Failed to trigger ATO workflow {} for request {}", 
                             workflowType, request.getRequestId(), e);
                }
            });
        }
    }

    private List<String> getATOWorkflows(ATOLikelihood likelihood, ATOAttackVector attackVector) {
        Map<ATOLikelihood, List<String>> workflowMapping = Map.of(
            ATOLikelihood.CONFIRMED, Arrays.asList("ATO_INCIDENT_RESPONSE", "ACCOUNT_RECOVERY", "USER_NOTIFICATION"),
            ATOLikelihood.HIGHLY_LIKELY, Arrays.asList("ATO_INVESTIGATION", "ENHANCED_VERIFICATION", "USER_ALERT"),
            ATOLikelihood.LIKELY, Arrays.asList("ATO_REVIEW", "ACTIVITY_ANALYSIS", "MONITORING_INCREASE"),
            ATOLikelihood.POSSIBLE, Arrays.asList("STANDARD_REVIEW", "MONITORING_UPDATE")
        );
        
        return workflowMapping.getOrDefault(likelihood, Arrays.asList("STANDARD_MONITORING"));
    }

    private void sendATONotifications(ATODetectionRequest request, ATODetectionResult detectionResult, 
                                    ATOProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "accountId", request.getAccountId(),
            "userId", request.getUserId(),
            "likelihood", detectionResult.getLikelihood().toString(),
            "confidence", String.format("%.2f%%", detectionResult.getConfidence() * 100),
            "attackVector", detectionResult.getAttackVector().toString(),
            "indicatorCount", detectionResult.getIndicators().size(),
            "detectedAt", request.getDetectedAt()
        );
        
        // Critical notifications for confirmed ATO
        if (detectionResult.getLikelihood() == ATOLikelihood.CONFIRMED) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalATOAlert(notificationData);
                notificationService.sendUserEmergencyAlert(request.getUserId(), notificationData);
                notificationService.sendExecutiveAlert("CONFIRMED_ATO", notificationData);
            });
        }
        
        // High priority notifications for likely ATO
        if (detectionResult.getLikelihood().ordinal() >= ATOLikelihood.LIKELY.ordinal()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighPriorityATOAlert(notificationData);
                notificationService.sendUserSecurityAlert(request.getUserId(), notificationData);
            });
        }
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "SECURITY_TEAM",
                "ATO_DETECTED",
                notificationData
            );
        });
    }

    private void updateMonitoringSystems(ATODetectionRequest request, ATOProcessingResult result) {
        // Update ATO monitoring dashboard
        dashboardService.updateATODashboard(request, result);
        
        // Update account risk profiles
        riskService.updateAccountRiskProfile(request.getAccountId(), result);
        
        // Update threat intelligence systems
        threatIntelligenceService.updateATOIntelligence(request, result);
    }

    private void auditATODetection(ATODetectionRequest request, ATOProcessingResult result, 
                                 GenericKafkaEvent originalEvent) {
        auditService.auditATODetection(
            request.getRequestId(),
            request.getAccountId(),
            request.getUserId(),
            result.getDetectionResult().getLikelihood().toString(),
            result.getDetectionResult().getConfidence(),
            result.getDetectionResult().getAttackVector().toString(),
            originalEvent.getEventId()
        );
    }

    private void recordATOMetrics(ATODetectionRequest request, ATOProcessingResult result, 
                                long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordATOMetrics(
            result.getDetectionResult().getLikelihood().toString(),
            result.getDetectionResult().getAttackVector().toString(),
            result.getDetectionResult().getConfidence(),
            result.getDetectionResult().getIndicators().size(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record indicator metrics
        for (ATOIndicator indicator : result.getDetectionResult().getIndicators()) {
            metricsService.recordATOIndicatorMetrics(
                indicator.getIndicatorType(),
                indicator.getSeverity().toString(),
                indicator.getConfidence()
            );
        }
    }

    // Helper methods for transaction extraction
    @SuppressWarnings("unchecked")
    private List<Transaction> extractTransactionList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<Map<String, Object>> txList = (List<Map<String, Object>>) value;
            return txList.stream()
                .map(this::mapToTransaction)
                .collect(java.util.stream.Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Transaction mapToTransaction(Map<String, Object> txMap) {
        return Transaction.builder()
            .transactionId(extractString(txMap, "transactionId", null))
            .amount(extractBigDecimal(txMap, "amount"))
            .currency(extractString(txMap, "currency", "USD"))
            .type(extractString(txMap, "type", null))
            .timestamp(extractInstant(txMap, "timestamp"))
            .build();
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("ato-detection-validation-errors", event);
    }

    private void handleCriticalATOError(GenericKafkaEvent event, CriticalATOException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_ATO_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("ato-detection-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying ATO detection {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("account-takeover-detection-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for ATO detection {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "account-takeover-detection");
        
        kafkaTemplate.send("account-takeover-detection.DLQ", event);
        
        alertingService.createDLQAlert(
            "account-takeover-detection",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleATODetectionFailure(GenericKafkaEvent event, String topic, int partition,
                                        long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for ATO detection: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "ATO Detection Circuit Breaker Open",
            "Account takeover detection is failing. Account security severely compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalATOException extends RuntimeException {
        public CriticalATOException(String message) {
            super(message);
        }
    }

    public static class ATOProcessingException extends RuntimeException {
        public ATOProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}