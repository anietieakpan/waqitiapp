package com.waqiti.account.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.model.Account;
import com.waqiti.account.model.AccountStatus;
import com.waqiti.account.model.RecoveryRequest;
import com.waqiti.account.model.RecoveryStatus;
import com.waqiti.account.model.RecoveryMethod;
import com.waqiti.account.model.AccountRecoveryAuditLog;
import com.waqiti.account.service.AccountService;
import com.waqiti.account.service.AccountRecoveryService;
import com.waqiti.account.service.IdentityVerificationService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.compliance.ComplianceService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.SystemException;
import com.waqiti.common.kafka.KafkaMessage;
import com.waqiti.common.kafka.KafkaHeaders;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContextService;
import com.waqiti.common.validation.ValidationService;
import com.waqiti.common.vault.VaultSecretManager;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AccountRecoveryEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AccountRecoveryEventsConsumer.class);
    private static final String CONSUMER_NAME = "account-recovery-events-consumer";
    private static final String DLQ_TOPIC = "account-recovery-events-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final AccountRecoveryService accountRecoveryService;
    private final IdentityVerificationService identityVerificationService;
    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.account-recovery-events.enabled:true}")
    private boolean consumerEnabled;

    @Value("${account.recovery.max-attempts-per-day:3}")
    private int maxRecoveryAttemptsPerDay;

    @Value("${account.recovery.verification-timeout-hours:72}")
    private int verificationTimeoutHours;

    @Value("${account.recovery.require-manual-approval:true}")
    private boolean requireManualApproval;

    @Value("${account.recovery.send-notifications:true}")
    private boolean sendNotifications;

    @Value("${account.recovery.require-document-verification:true}")
    private boolean requireDocumentVerification;

    @Value("${account.recovery.security-monitoring-enabled:true}")
    private boolean securityMonitoringEnabled;

    @Value("${account.recovery.cooldown-period-days:30}")
    private int cooldownPeriodDays;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter recoveryEventTypeCounters;

    public AccountRecoveryEventsConsumer(ObjectMapper objectMapper,
                                        AccountService accountService,
                                        AccountRecoveryService accountRecoveryService,
                                        IdentityVerificationService identityVerificationService,
                                        AuditService auditService,
                                        ComplianceService complianceService,
                                        MetricsService metricsService,
                                        NotificationService notificationService,
                                        SecurityContextService securityContextService,
                                        ValidationService validationService,
                                        VaultSecretManager vaultSecretManager,
                                        KafkaTemplate<String, Object> kafkaTemplate,
                                        MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.accountRecoveryService = accountRecoveryService;
        this.identityVerificationService = identityVerificationService;
        this.auditService = auditService;
        this.complianceService = complianceService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.securityContextService = securityContextService;
        this.validationService = validationService;
        this.vaultSecretManager = vaultSecretManager;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("account_recovery_events_processed_total")
                .description("Total processed account recovery events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("account_recovery_events_errors_total")
                .description("Total account recovery events processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("account_recovery_events_dlq_total")
                .description("Total account recovery events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("account_recovery_events_processing_duration")
                .description("Account recovery events processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("AccountRecoveryEventsConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.account-recovery-events:account-recovery-events}",
        groupId = "${kafka.consumer.group-id:account-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "account-recovery-events-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "account-recovery-events-retry")
    public void processAccountRecoveryEvents(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.CORRELATION_ID, required = false) String correlationId,
            @Header(name = KafkaHeaders.TRACE_ID, required = false) String traceId,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String messageId = UUID.randomUUID().toString();

        try {
            MDC.put("messageId", messageId);
            MDC.put("correlationId", correlationId != null ? correlationId : messageId);
            MDC.put("traceId", traceId != null ? traceId : messageId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));

            if (!consumerEnabled) {
                logger.warn("Account recovery events consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing account recovery event message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidAccountRecoveryEventMessage(messageNode)) {
                logger.error("Invalid account recovery event message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String accountId = messageNode.get("accountId").asText();
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processRecoveryEvent(messageNode, accountId, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed account recovery event: messageId={}, accountId={}, eventType={}, processingTime={}ms",
                    messageId, accountId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordAccountRecoveryEventProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse account recovery event message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing account recovery event: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidAccountRecoveryEventMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("accountId") && StringUtils.hasText(messageNode.get("accountId").asText()) &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp");
        } catch (Exception e) {
            logger.error("Error validating account recovery event message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processRecoveryEvent(JsonNode messageNode, String accountId, String eventType, 
                                    String correlationId, String traceId) {
        try {
            Account account = accountService.findById(accountId);
            if (account == null) {
                throw new ValidationException("Account not found: " + accountId);
            }

            AccountRecoveryEvent recoveryEvent = parseAccountRecoveryEvent(messageNode);
            
            validateRecoveryEvent(recoveryEvent, account);
            
            switch (recoveryEvent.getEventType()) {
                case RECOVERY_REQUESTED:
                    processRecoveryRequested(recoveryEvent, account, correlationId, traceId);
                    break;
                case IDENTITY_VERIFICATION_STARTED:
                    processIdentityVerificationStarted(recoveryEvent, account, correlationId, traceId);
                    break;
                case IDENTITY_VERIFICATION_COMPLETED:
                    processIdentityVerificationCompleted(recoveryEvent, account, correlationId, traceId);
                    break;
                case DOCUMENT_VERIFICATION_REQUIRED:
                    processDocumentVerificationRequired(recoveryEvent, account, correlationId, traceId);
                    break;
                case DOCUMENT_VERIFICATION_COMPLETED:
                    processDocumentVerificationCompleted(recoveryEvent, account, correlationId, traceId);
                    break;
                case SECURITY_QUESTIONS_ANSWERED:
                    processSecurityQuestionsAnswered(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_CODE_VERIFIED:
                    processRecoveryCodeVerified(recoveryEvent, account, correlationId, traceId);
                    break;
                case MANUAL_REVIEW_REQUIRED:
                    processManualReviewRequired(recoveryEvent, account, correlationId, traceId);
                    break;
                case MANUAL_REVIEW_COMPLETED:
                    processManualReviewCompleted(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_APPROVED:
                    processRecoveryApproved(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_COMPLETED:
                    processRecoveryCompleted(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_DENIED:
                    processRecoveryDenied(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_CANCELLED:
                    processRecoveryCancelled(recoveryEvent, account, correlationId, traceId);
                    break;
                case RECOVERY_EXPIRED:
                    processRecoveryExpired(recoveryEvent, account, correlationId, traceId);
                    break;
                case SUSPICIOUS_RECOVERY_ATTEMPT:
                    processSuspiciousRecoveryAttempt(recoveryEvent, account, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown account recovery event type: {}", recoveryEvent.getEventType());
                    throw new ValidationException("Unknown recovery event type: " + recoveryEvent.getEventType());
            }

            recordRecoveryEventAudit(recoveryEvent, account, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing account recovery event for account {}: {}", accountId, e.getMessage(), e);
            throw e;
        }
    }

    private AccountRecoveryEvent parseAccountRecoveryEvent(JsonNode messageNode) {
        try {
            AccountRecoveryEvent event = new AccountRecoveryEvent();
            event.setAccountId(messageNode.get("accountId").asText());
            event.setEventType(RecoveryEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("requestId")) {
                event.setRequestId(messageNode.get("requestId").asText());
            }
            
            if (messageNode.has("recoveryMethod")) {
                event.setRecoveryMethod(RecoveryMethod.valueOf(messageNode.get("recoveryMethod").asText()));
            }
            
            if (messageNode.has("requestedBy")) {
                event.setRequestedBy(messageNode.get("requestedBy").asText());
            }
            
            if (messageNode.has("ipAddress")) {
                event.setIpAddress(messageNode.get("ipAddress").asText());
            }
            
            if (messageNode.has("userAgent")) {
                event.setUserAgent(messageNode.get("userAgent").asText());
            }
            
            if (messageNode.has("verificationScore")) {
                event.setVerificationScore(messageNode.get("verificationScore").asDouble());
            }
            
            if (messageNode.has("documentsProvided")) {
                JsonNode docsNode = messageNode.get("documentsProvided");
                List<String> docs = new ArrayList<>();
                docsNode.forEach(doc -> docs.add(doc.asText()));
                event.setDocumentsProvided(docs);
            }
            
            if (messageNode.has("securityAnswers")) {
                JsonNode answersNode = messageNode.get("securityAnswers");
                Map<String, String> answers = new HashMap<>();
                answersNode.fields().forEachRemaining(entry -> {
                    answers.put(entry.getKey(), entry.getValue().asText());
                });
                event.setSecurityAnswers(answers);
            }
            
            if (messageNode.has("reviewNotes")) {
                event.setReviewNotes(messageNode.get("reviewNotes").asText());
            }
            
            if (messageNode.has("denialReason")) {
                event.setDenialReason(messageNode.get("denialReason").asText());
            }
            
            if (messageNode.has("riskScore")) {
                event.setRiskScore(messageNode.get("riskScore").asDouble());
            }
            
            if (messageNode.has("securityFlags")) {
                JsonNode flagsNode = messageNode.get("securityFlags");
                List<String> flags = new ArrayList<>();
                flagsNode.forEach(flag -> flags.add(flag.asText()));
                event.setSecurityFlags(flags);
            }
            
            if (messageNode.has("eventMetadata")) {
                JsonNode metadataNode = messageNode.get("eventMetadata");
                Map<String, Object> metadata = new HashMap<>();
                metadataNode.fields().forEachRemaining(entry -> {
                    metadata.put(entry.getKey(), entry.getValue().asText());
                });
                event.setEventMetadata(metadata);
            }

            return event;
        } catch (Exception e) {
            logger.error("Error parsing account recovery event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid account recovery event format: " + e.getMessage());
        }
    }

    private void validateRecoveryEvent(AccountRecoveryEvent recoveryEvent, Account account) {
        // Check if account is eligible for recovery
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ValidationException("Cannot recover closed account: " + account.getId());
        }
        
        if (account.getStatus() == AccountStatus.ESCHEATMENT_PENDING || 
            account.getStatus() == AccountStatus.ESCHEATMENT_COMPLETED) {
            throw new ValidationException("Cannot recover escheated account: " + account.getId());
        }
        
        // Check recovery attempt limits
        int dailyAttempts = accountRecoveryService.getDailyRecoveryAttempts(account.getId(), 
                                                                           recoveryEvent.getTimestamp().toLocalDate());
        if (dailyAttempts >= maxRecoveryAttemptsPerDay) {
            throw new ValidationException("Daily recovery attempt limit exceeded for account: " + account.getId());
        }
        
        // Check cooldown period after successful recovery
        LocalDateTime lastRecovery = accountRecoveryService.getLastSuccessfulRecovery(account.getId());
        if (lastRecovery != null) {
            long daysSinceLastRecovery = ChronoUnit.DAYS.between(lastRecovery, recoveryEvent.getTimestamp());
            if (daysSinceLastRecovery < cooldownPeriodDays) {
                throw new ValidationException("Account recovery is in cooldown period. Days remaining: " + 
                                           (cooldownPeriodDays - daysSinceLastRecovery));
            }
        }
    }

    private void processRecoveryRequested(AccountRecoveryEvent recoveryEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing recovery requested for account: {}", account.getId());
        
        // Create recovery request
        RecoveryRequest recoveryRequest = new RecoveryRequest();
        recoveryRequest.setRequestId(UUID.randomUUID().toString());
        recoveryRequest.setAccountId(account.getId());
        recoveryRequest.setRequestedBy(recoveryEvent.getRequestedBy());
        recoveryRequest.setRecoveryMethod(recoveryEvent.getRecoveryMethod());
        recoveryRequest.setIpAddress(recoveryEvent.getIpAddress());
        recoveryRequest.setUserAgent(recoveryEvent.getUserAgent());
        recoveryRequest.setStatus(RecoveryStatus.INITIATED);
        recoveryRequest.setRequestDate(recoveryEvent.getTimestamp());
        recoveryRequest.setExpiryDate(recoveryEvent.getTimestamp().plusHours(verificationTimeoutHours));
        
        // Calculate risk score
        double riskScore = calculateRecoveryRiskScore(recoveryEvent, account);
        recoveryRequest.setRiskScore(riskScore);
        
        accountRecoveryService.createRecoveryRequest(recoveryRequest);
        
        // Determine verification requirements
        if (riskScore > 0.7 || requireDocumentVerification) {
            triggerDocumentVerificationRequired(account, recoveryRequest, correlationId, traceId);
        } else {
            triggerIdentityVerificationStarted(account, recoveryRequest, correlationId, traceId);
        }
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendAccountRecoveryInitiated(account.getUserId(), account.getId(), 
                                                           recoveryEvent.getRecoveryMethod(), correlationId);
        }
        
        auditService.recordRecoveryRequested(account.getId(), recoveryEvent.getRecoveryMethod(), 
                                           recoveryEvent.getRequestedBy(), recoveryEvent.getIpAddress(), 
                                           riskScore, correlationId);
        
        metricsService.recordRecoveryRequested(account.getId(), recoveryEvent.getRecoveryMethod(), riskScore);
        
        logger.info("Recovery requested for account: {}, requestId: {}, riskScore: {}", 
                   account.getId(), recoveryRequest.getRequestId(), riskScore);
    }

    private void processIdentityVerificationStarted(AccountRecoveryEvent recoveryEvent, Account account, 
                                                  String correlationId, String traceId) {
        logger.info("Processing identity verification started for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.IDENTITY_VERIFICATION);
            recoveryRequest.setVerificationStarted(recoveryEvent.getTimestamp());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Create identity verification challenge
            String verificationId = identityVerificationService.createVerificationChallenge(
                account.getUserId(), "ACCOUNT_RECOVERY", recoveryRequest.getRequestId());
            
            // Send verification instructions
            if (sendNotifications) {
                notificationService.sendIdentityVerificationInstructions(account.getUserId(), 
                                                                       verificationId, correlationId);
            }
            
            auditService.recordIdentityVerificationStarted(account.getId(), recoveryRequest.getRequestId(), 
                                                         verificationId, correlationId);
            
            metricsService.recordIdentityVerificationStarted(account.getId());
            
            logger.info("Identity verification started for account: {}, verificationId: {}", 
                       account.getId(), verificationId);
        }
    }

    private void processIdentityVerificationCompleted(AccountRecoveryEvent recoveryEvent, Account account, 
                                                    String correlationId, String traceId) {
        logger.info("Processing identity verification completed for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setVerificationCompleted(recoveryEvent.getTimestamp());
            recoveryRequest.setVerificationScore(recoveryEvent.getVerificationScore());
            
            if (recoveryEvent.getVerificationScore() >= 0.8) {
                logger.info("Identity verification passed for account: {}, score: {}", 
                           account.getId(), recoveryEvent.getVerificationScore());
                
                if (requireManualApproval) {
                    recoveryRequest.setStatus(RecoveryStatus.MANUAL_REVIEW);
                    triggerManualReviewRequired(account, recoveryRequest, correlationId, traceId);
                } else {
                    recoveryRequest.setStatus(RecoveryStatus.APPROVED);
                    triggerRecoveryApproved(account, recoveryRequest, correlationId, traceId);
                }
            } else {
                logger.warn("Identity verification failed for account: {}, score: {}", 
                           account.getId(), recoveryEvent.getVerificationScore());
                
                recoveryRequest.setStatus(RecoveryStatus.DENIED);
                recoveryRequest.setDenialReason("Identity verification score too low");
                triggerRecoveryDenied(account, recoveryRequest, correlationId, traceId);
            }
            
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordIdentityVerificationCompleted(account.getId(), recoveryRequest.getRequestId(), 
                                                           recoveryEvent.getVerificationScore(), correlationId);
            
            metricsService.recordIdentityVerificationCompleted(account.getId(), recoveryEvent.getVerificationScore());
        }
    }

    private void processDocumentVerificationRequired(AccountRecoveryEvent recoveryEvent, Account account, 
                                                   String correlationId, String traceId) {
        logger.info("Processing document verification required for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.DOCUMENT_VERIFICATION);
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Send document upload instructions
            List<String> requiredDocuments = determineRequiredDocuments(account, recoveryRequest);
            
            if (sendNotifications) {
                notificationService.sendDocumentUploadInstructions(account.getUserId(), 
                                                                 requiredDocuments, correlationId);
            }
            
            auditService.recordDocumentVerificationRequired(account.getId(), recoveryRequest.getRequestId(), 
                                                          requiredDocuments, correlationId);
            
            metricsService.recordDocumentVerificationRequired(account.getId());
            
            logger.info("Document verification required for account: {}, documents: {}", 
                       account.getId(), requiredDocuments);
        }
    }

    private void processDocumentVerificationCompleted(AccountRecoveryEvent recoveryEvent, Account account, 
                                                    String correlationId, String traceId) {
        logger.info("Processing document verification completed for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setDocumentsProvided(recoveryEvent.getDocumentsProvided());
            recoveryRequest.setDocumentVerificationCompleted(recoveryEvent.getTimestamp());
            
            // Verify documents
            boolean documentsValid = identityVerificationService.verifyDocuments(
                account.getUserId(), recoveryEvent.getDocumentsProvided());
            
            if (documentsValid) {
                logger.info("Document verification passed for account: {}", account.getId());
                
                // Proceed to identity verification
                recoveryRequest.setStatus(RecoveryStatus.IDENTITY_VERIFICATION);
                triggerIdentityVerificationStarted(account, recoveryRequest, correlationId, traceId);
            } else {
                logger.warn("Document verification failed for account: {}", account.getId());
                
                recoveryRequest.setStatus(RecoveryStatus.DENIED);
                recoveryRequest.setDenialReason("Document verification failed");
                triggerRecoveryDenied(account, recoveryRequest, correlationId, traceId);
            }
            
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordDocumentVerificationCompleted(account.getId(), recoveryRequest.getRequestId(), 
                                                           recoveryEvent.getDocumentsProvided(), 
                                                           documentsValid, correlationId);
            
            metricsService.recordDocumentVerificationCompleted(account.getId(), documentsValid);
        }
    }

    private void processSecurityQuestionsAnswered(AccountRecoveryEvent recoveryEvent, Account account, 
                                                String correlationId, String traceId) {
        logger.info("Processing security questions answered for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            // Verify security answers
            boolean answersCorrect = identityVerificationService.verifySecurityAnswers(
                account.getUserId(), recoveryEvent.getSecurityAnswers());
            
            if (answersCorrect) {
                logger.info("Security questions answered correctly for account: {}", account.getId());
                
                recoveryRequest.setSecurityQuestionsAnswered(true);
                recoveryRequest.setVerificationScore(
                    (recoveryRequest.getVerificationScore() != null ? recoveryRequest.getVerificationScore() : 0) + 0.3);
                
                // Check if additional verification is needed
                if (recoveryRequest.getVerificationScore() >= 0.8) {
                    recoveryRequest.setStatus(RecoveryStatus.APPROVED);
                    triggerRecoveryApproved(account, recoveryRequest, correlationId, traceId);
                }
            } else {
                logger.warn("Security questions answered incorrectly for account: {}", account.getId());
                
                recoveryRequest.setSecurityQuestionsAnswered(false);
                recoveryRequest.setStatus(RecoveryStatus.DENIED);
                recoveryRequest.setDenialReason("Security questions answered incorrectly");
                triggerRecoveryDenied(account, recoveryRequest, correlationId, traceId);
            }
            
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordSecurityQuestionsAnswered(account.getId(), recoveryRequest.getRequestId(), 
                                                       answersCorrect, correlationId);
            
            metricsService.recordSecurityQuestionsAnswered(account.getId(), answersCorrect);
        }
    }

    private void processRecoveryCodeVerified(AccountRecoveryEvent recoveryEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing recovery code verified for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            String recoveryCode = (String) recoveryEvent.getEventMetadata().get("recoveryCode");
            
            // Verify recovery code
            boolean codeValid = accountRecoveryService.verifyRecoveryCode(account.getId(), recoveryCode);
            
            if (codeValid) {
                logger.info("Recovery code verified successfully for account: {}", account.getId());
                
                recoveryRequest.setRecoveryCodeVerified(true);
                recoveryRequest.setStatus(RecoveryStatus.APPROVED);
                triggerRecoveryApproved(account, recoveryRequest, correlationId, traceId);
            } else {
                logger.warn("Invalid recovery code for account: {}", account.getId());
                
                recoveryRequest.setRecoveryCodeVerified(false);
                recoveryRequest.setStatus(RecoveryStatus.DENIED);
                recoveryRequest.setDenialReason("Invalid recovery code");
                triggerRecoveryDenied(account, recoveryRequest, correlationId, traceId);
            }
            
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordRecoveryCodeVerified(account.getId(), recoveryRequest.getRequestId(), 
                                                  codeValid, correlationId);
            
            metricsService.recordRecoveryCodeVerified(account.getId(), codeValid);
        }
    }

    private void processManualReviewRequired(AccountRecoveryEvent recoveryEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing manual review required for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.MANUAL_REVIEW);
            recoveryRequest.setManualReviewStarted(recoveryEvent.getTimestamp());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Create manual review task
            Map<String, Object> reviewTask = new HashMap<>();
            reviewTask.put("accountId", account.getId());
            reviewTask.put("requestId", recoveryRequest.getRequestId());
            reviewTask.put("taskType", "ACCOUNT_RECOVERY_REVIEW");
            reviewTask.put("priority", "HIGH");
            reviewTask.put("verificationScore", recoveryRequest.getVerificationScore());
            reviewTask.put("riskScore", recoveryRequest.getRiskScore());
            reviewTask.put("correlationId", correlationId);
            
            kafkaTemplate.send("manual-review-tasks", reviewTask);
            
            auditService.recordManualReviewRequired(account.getId(), recoveryRequest.getRequestId(), 
                                                  recoveryRequest.getVerificationScore(), 
                                                  recoveryRequest.getRiskScore(), correlationId);
            
            metricsService.recordManualReviewRequired(account.getId());
            
            logger.info("Manual review required for account recovery: {}, requestId: {}", 
                       account.getId(), recoveryRequest.getRequestId());
        }
    }

    private void processManualReviewCompleted(AccountRecoveryEvent recoveryEvent, Account account, 
                                            String correlationId, String traceId) {
        logger.info("Processing manual review completed for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setManualReviewCompleted(recoveryEvent.getTimestamp());
            recoveryRequest.setReviewNotes(recoveryEvent.getReviewNotes());
            
            boolean approved = Boolean.parseBoolean((String) recoveryEvent.getEventMetadata().get("approved"));
            
            if (approved) {
                logger.info("Manual review approved for account: {}", account.getId());
                
                recoveryRequest.setStatus(RecoveryStatus.APPROVED);
                triggerRecoveryApproved(account, recoveryRequest, correlationId, traceId);
            } else {
                logger.info("Manual review denied for account: {}", account.getId());
                
                recoveryRequest.setStatus(RecoveryStatus.DENIED);
                recoveryRequest.setDenialReason(recoveryEvent.getDenialReason());
                triggerRecoveryDenied(account, recoveryRequest, correlationId, traceId);
            }
            
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordManualReviewCompleted(account.getId(), recoveryRequest.getRequestId(), 
                                                   approved, recoveryEvent.getReviewNotes(), correlationId);
            
            metricsService.recordManualReviewCompleted(account.getId(), approved);
        }
    }

    private void processRecoveryApproved(AccountRecoveryEvent recoveryEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing recovery approved for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.APPROVED);
            recoveryRequest.setApprovalDate(recoveryEvent.getTimestamp());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Initiate account recovery
            beginAccountRecovery(account, recoveryRequest, correlationId, traceId);
            
            // Send approval notification
            if (sendNotifications) {
                notificationService.sendAccountRecoveryApproved(account.getUserId(), account.getId(), correlationId);
            }
            
            auditService.recordRecoveryApproved(account.getId(), recoveryRequest.getRequestId(), correlationId);
            
            metricsService.recordRecoveryApproved(account.getId());
            
            logger.info("Recovery approved for account: {}, initiating recovery process", account.getId());
        }
    }

    private void processRecoveryCompleted(AccountRecoveryEvent recoveryEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing recovery completed for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.COMPLETED);
            recoveryRequest.setCompletionDate(recoveryEvent.getTimestamp());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Update account status
            account.setStatus(AccountStatus.ACTIVE);
            account.setAccountLocked(false);
            account.setLockReason(null);
            account.setLockTimestamp(null);
            account.setLastRecoveryDate(recoveryEvent.getTimestamp());
            accountService.updateAccount(account);
            
            // Reset security settings
            accountRecoveryService.resetAccountSecurity(account.getId());
            
            // Send completion notification
            if (sendNotifications) {
                notificationService.sendAccountRecoveryCompleted(account.getUserId(), account.getId(), correlationId);
            }
            
            auditService.recordRecoveryCompleted(account.getId(), recoveryRequest.getRequestId(), correlationId);
            
            metricsService.recordRecoveryCompleted(account.getId());
            
            logger.info("Recovery completed successfully for account: {}", account.getId());
        }
    }

    private void processRecoveryDenied(AccountRecoveryEvent recoveryEvent, Account account, 
                                     String correlationId, String traceId) {
        logger.info("Processing recovery denied for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.DENIED);
            recoveryRequest.setDenialDate(recoveryEvent.getTimestamp());
            recoveryRequest.setDenialReason(recoveryEvent.getDenialReason());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            // Send denial notification
            if (sendNotifications) {
                notificationService.sendAccountRecoveryDenied(account.getUserId(), account.getId(), 
                                                            recoveryEvent.getDenialReason(), correlationId);
            }
            
            auditService.recordRecoveryDenied(account.getId(), recoveryRequest.getRequestId(), 
                                            recoveryEvent.getDenialReason(), correlationId);
            
            metricsService.recordRecoveryDenied(account.getId());
            
            logger.info("Recovery denied for account: {}, reason: {}", account.getId(), recoveryEvent.getDenialReason());
        }
    }

    private void processRecoveryCancelled(AccountRecoveryEvent recoveryEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing recovery cancelled for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.CANCELLED);
            recoveryRequest.setCancellationDate(recoveryEvent.getTimestamp());
            recoveryRequest.setCancellationReason((String) recoveryEvent.getEventMetadata().get("reason"));
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordRecoveryCancelled(account.getId(), recoveryRequest.getRequestId(), 
                                               recoveryRequest.getCancellationReason(), correlationId);
            
            metricsService.recordRecoveryCancelled(account.getId());
            
            logger.info("Recovery cancelled for account: {}", account.getId());
        }
    }

    private void processRecoveryExpired(AccountRecoveryEvent recoveryEvent, Account account, 
                                      String correlationId, String traceId) {
        logger.info("Processing recovery expired for account: {}", account.getId());
        
        RecoveryRequest recoveryRequest = accountRecoveryService.getRecoveryRequest(recoveryEvent.getRequestId());
        if (recoveryRequest != null) {
            recoveryRequest.setStatus(RecoveryStatus.EXPIRED);
            recoveryRequest.setExpiryDate(recoveryEvent.getTimestamp());
            accountRecoveryService.updateRecoveryRequest(recoveryRequest);
            
            auditService.recordRecoveryExpired(account.getId(), recoveryRequest.getRequestId(), correlationId);
            
            metricsService.recordRecoveryExpired(account.getId());
            
            logger.info("Recovery expired for account: {}", account.getId());
        }
    }

    private void processSuspiciousRecoveryAttempt(AccountRecoveryEvent recoveryEvent, Account account, 
                                                String correlationId, String traceId) {
        logger.warn("Processing suspicious recovery attempt for account: {}", account.getId());
        
        String suspicionReason = (String) recoveryEvent.getEventMetadata().get("reason");
        double riskScore = recoveryEvent.getRiskScore() != null ? recoveryEvent.getRiskScore() : 0.9;
        
        // Block recovery attempts temporarily
        accountRecoveryService.blockRecoveryAttempts(account.getId(), "Suspicious activity detected", 24);
        
        // Create security incident
        Map<String, Object> securityIncident = new HashMap<>();
        securityIncident.put("accountId", account.getId());
        securityIncident.put("incidentType", "SUSPICIOUS_RECOVERY_ATTEMPT");
        securityIncident.put("severity", "HIGH");
        securityIncident.put("reason", suspicionReason);
        securityIncident.put("riskScore", riskScore);
        securityIncident.put("ipAddress", recoveryEvent.getIpAddress());
        securityIncident.put("timestamp", recoveryEvent.getTimestamp().toString());
        securityIncident.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-incidents", securityIncident);
        
        // Send security alert
        if (sendNotifications) {
            notificationService.sendSuspiciousRecoveryAlert(account.getUserId(), account.getId(), 
                                                          suspicionReason, correlationId);
        }
        
        auditService.recordSuspiciousRecoveryAttempt(account.getId(), suspicionReason, riskScore, 
                                                   recoveryEvent.getIpAddress(), correlationId);
        
        metricsService.recordSuspiciousRecoveryAttempt(account.getId(), riskScore);
        
        logger.warn("Suspicious recovery attempt detected for account: {}, reason: {}, risk: {}", 
                   account.getId(), suspicionReason, riskScore);
    }

    private double calculateRecoveryRiskScore(AccountRecoveryEvent recoveryEvent, Account account) {
        double riskScore = 0.0;
        
        // Risk from IP address
        if (recoveryEvent.getIpAddress() != null) {
            // Check IP reputation
            riskScore += 0.2; // Placeholder - would integrate with IP reputation service
        }
        
        // Risk from account status
        if (account.getStatus() == AccountStatus.SUSPENDED || account.getStatus() == AccountStatus.RESTRICTED) {
            riskScore += 0.3;
        }
        
        // Risk from recent recovery attempts
        int recentAttempts = accountRecoveryService.getRecentRecoveryAttempts(account.getId(), 30);
        if (recentAttempts > 0) {
            riskScore += Math.min(recentAttempts * 0.1, 0.3);
        }
        
        // Risk from account age
        long accountAgeDays = ChronoUnit.DAYS.between(account.getCreatedDate(), LocalDateTime.now());
        if (accountAgeDays < 30) {
            riskScore += 0.2;
        }
        
        // Risk from recovery method
        if (recoveryEvent.getRecoveryMethod() == RecoveryMethod.EMAIL_ONLY) {
            riskScore += 0.1;
        }
        
        return Math.min(riskScore, 1.0);
    }

    private List<String> determineRequiredDocuments(Account account, RecoveryRequest recoveryRequest) {
        List<String> documents = new ArrayList<>();
        
        documents.add("GOVERNMENT_ID");
        documents.add("PROOF_OF_ADDRESS");
        
        if (recoveryRequest.getRiskScore() > 0.7) {
            documents.add("BANK_STATEMENT");
            documents.add("SELFIE_WITH_ID");
        }
        
        if (account.getAccountType() == "BUSINESS") {
            documents.add("BUSINESS_REGISTRATION");
        }
        
        return documents;
    }

    private void beginAccountRecovery(Account account, RecoveryRequest recoveryRequest, 
                                    String correlationId, String traceId) {
        logger.info("Beginning account recovery for account: {}", account.getId());
        
        // Reset account credentials
        accountRecoveryService.resetAccountCredentials(account.getId());
        
        // Generate temporary access code
        String temporaryCode = accountRecoveryService.generateTemporaryAccessCode(account.getId());
        
        // Send recovery instructions
        if (sendNotifications) {
            notificationService.sendAccountRecoveryInstructions(account.getUserId(), account.getId(), 
                                                              temporaryCode, correlationId);
        }
        
        // Schedule recovery completion
        Map<String, Object> completionEvent = new HashMap<>();
        completionEvent.put("accountId", account.getId());
        completionEvent.put("requestId", recoveryRequest.getRequestId());
        completionEvent.put("eventType", "RECOVERY_COMPLETED");
        completionEvent.put("timestamp", LocalDateTime.now().toString());
        completionEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", completionEvent);
    }

    private void triggerDocumentVerificationRequired(Account account, RecoveryRequest recoveryRequest, 
                                                   String correlationId, String traceId) {
        Map<String, Object> docEvent = new HashMap<>();
        docEvent.put("accountId", account.getId());
        docEvent.put("requestId", recoveryRequest.getRequestId());
        docEvent.put("eventType", "DOCUMENT_VERIFICATION_REQUIRED");
        docEvent.put("timestamp", LocalDateTime.now().toString());
        docEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", docEvent);
    }

    private void triggerIdentityVerificationStarted(Account account, RecoveryRequest recoveryRequest, 
                                                  String correlationId, String traceId) {
        Map<String, Object> idEvent = new HashMap<>();
        idEvent.put("accountId", account.getId());
        idEvent.put("requestId", recoveryRequest.getRequestId());
        idEvent.put("eventType", "IDENTITY_VERIFICATION_STARTED");
        idEvent.put("timestamp", LocalDateTime.now().toString());
        idEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", idEvent);
    }

    private void triggerManualReviewRequired(Account account, RecoveryRequest recoveryRequest, 
                                           String correlationId, String traceId) {
        Map<String, Object> reviewEvent = new HashMap<>();
        reviewEvent.put("accountId", account.getId());
        reviewEvent.put("requestId", recoveryRequest.getRequestId());
        reviewEvent.put("eventType", "MANUAL_REVIEW_REQUIRED");
        reviewEvent.put("timestamp", LocalDateTime.now().toString());
        reviewEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", reviewEvent);
    }

    private void triggerRecoveryApproved(Account account, RecoveryRequest recoveryRequest, 
                                       String correlationId, String traceId) {
        Map<String, Object> approvalEvent = new HashMap<>();
        approvalEvent.put("accountId", account.getId());
        approvalEvent.put("requestId", recoveryRequest.getRequestId());
        approvalEvent.put("eventType", "RECOVERY_APPROVED");
        approvalEvent.put("timestamp", LocalDateTime.now().toString());
        approvalEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", approvalEvent);
    }

    private void triggerRecoveryDenied(Account account, RecoveryRequest recoveryRequest, 
                                     String correlationId, String traceId) {
        Map<String, Object> denialEvent = new HashMap<>();
        denialEvent.put("accountId", account.getId());
        denialEvent.put("requestId", recoveryRequest.getRequestId());
        denialEvent.put("eventType", "RECOVERY_DENIED");
        denialEvent.put("denialReason", recoveryRequest.getDenialReason());
        denialEvent.put("timestamp", LocalDateTime.now().toString());
        denialEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-recovery-events", denialEvent);
    }

    private void recordRecoveryEventAudit(AccountRecoveryEvent recoveryEvent, Account account, 
                                        String correlationId, String traceId) {
        AccountRecoveryAuditLog auditLog = new AccountRecoveryAuditLog();
        auditLog.setAccountId(account.getId());
        auditLog.setEventType(recoveryEvent.getEventType());
        auditLog.setRequestId(recoveryEvent.getRequestId());
        auditLog.setRecoveryMethod(recoveryEvent.getRecoveryMethod());
        auditLog.setRequestedBy(recoveryEvent.getRequestedBy());
        auditLog.setIpAddress(recoveryEvent.getIpAddress());
        auditLog.setUserAgent(recoveryEvent.getUserAgent());
        auditLog.setVerificationScore(recoveryEvent.getVerificationScore());
        auditLog.setRiskScore(recoveryEvent.getRiskScore());
        auditLog.setTimestamp(recoveryEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordAccountRecoveryEventAudit(auditLog);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing account recovery event message: {}", error.getMessage(), error);
            
            sendToDlq(message, topic, error.getMessage(), error, correlationId, traceId);
            acknowledgment.acknowledge();
            
        } catch (Exception dlqError) {
            logger.error("Failed to send message to DLQ: {}", dlqError.getMessage(), dlqError);
            acknowledgment.nack();
        }
    }

    private void sendToDlq(String originalMessage, String originalTopic, String errorReason, 
                          Exception error, String correlationId, String traceId) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalMessage", originalMessage);
            dlqMessage.put("originalTopic", originalTopic);
            dlqMessage.put("errorReason", errorReason);
            dlqMessage.put("errorTimestamp", LocalDateTime.now().toString());
            dlqMessage.put("correlationId", correlationId);
            dlqMessage.put("traceId", traceId);
            dlqMessage.put("consumerName", CONSUMER_NAME);
            
            if (error != null) {
                dlqMessage.put("errorClass", error.getClass().getSimpleName());
                dlqMessage.put("errorMessage", error.getMessage());
            }
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            dlqCounter.increment();
            
            logger.info("Sent message to DLQ: topic={}, reason={}", DLQ_TOPIC, errorReason);
            
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    public void handleCircuitBreakerFallback(String message, String topic, int partition, long offset,
                                           String correlationId, String traceId, ConsumerRecord<String, String> record,
                                           Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker fallback triggered for account recovery events consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public enum RecoveryEventType {
        RECOVERY_REQUESTED,
        IDENTITY_VERIFICATION_STARTED,
        IDENTITY_VERIFICATION_COMPLETED,
        DOCUMENT_VERIFICATION_REQUIRED,
        DOCUMENT_VERIFICATION_COMPLETED,
        SECURITY_QUESTIONS_ANSWERED,
        RECOVERY_CODE_VERIFIED,
        MANUAL_REVIEW_REQUIRED,
        MANUAL_REVIEW_COMPLETED,
        RECOVERY_APPROVED,
        RECOVERY_COMPLETED,
        RECOVERY_DENIED,
        RECOVERY_CANCELLED,
        RECOVERY_EXPIRED,
        SUSPICIOUS_RECOVERY_ATTEMPT
    }

    public static class AccountRecoveryEvent {
        private String accountId;
        private RecoveryEventType eventType;
        private LocalDateTime timestamp;
        private String requestId;
        private RecoveryMethod recoveryMethod;
        private String requestedBy;
        private String ipAddress;
        private String userAgent;
        private Double verificationScore;
        private List<String> documentsProvided;
        private Map<String, String> securityAnswers;
        private String reviewNotes;
        private String denialReason;
        private Double riskScore;
        private List<String> securityFlags;
        private Map<String, Object> eventMetadata;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public RecoveryEventType getEventType() { return eventType; }
        public void setEventType(RecoveryEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public RecoveryMethod getRecoveryMethod() { return recoveryMethod; }
        public void setRecoveryMethod(RecoveryMethod recoveryMethod) { this.recoveryMethod = recoveryMethod; }

        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public Double getVerificationScore() { return verificationScore; }
        public void setVerificationScore(Double verificationScore) { this.verificationScore = verificationScore; }

        public List<String> getDocumentsProvided() { return documentsProvided; }
        public void setDocumentsProvided(List<String> documentsProvided) { this.documentsProvided = documentsProvided; }

        public Map<String, String> getSecurityAnswers() { return securityAnswers; }
        public void setSecurityAnswers(Map<String, String> securityAnswers) { this.securityAnswers = securityAnswers; }

        public String getReviewNotes() { return reviewNotes; }
        public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

        public String getDenialReason() { return denialReason; }
        public void setDenialReason(String denialReason) { this.denialReason = denialReason; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getSecurityFlags() { return securityFlags; }
        public void setSecurityFlags(List<String> securityFlags) { this.securityFlags = securityFlags; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}