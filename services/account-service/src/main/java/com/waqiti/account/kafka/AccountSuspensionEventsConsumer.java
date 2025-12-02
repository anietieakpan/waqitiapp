package com.waqiti.account.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.model.Account;
import com.waqiti.account.model.AccountStatus;
import com.waqiti.account.model.SuspensionReason;
import com.waqiti.account.model.SuspensionStatus;
import com.waqiti.account.model.AccountSuspensionAuditLog;
import com.waqiti.account.service.AccountService;
import com.waqiti.account.service.AccountSuspensionService;
import com.waqiti.account.service.AccountLimitsService;
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
public class AccountSuspensionEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AccountSuspensionEventsConsumer.class);
    private static final String CONSUMER_NAME = "account-suspension-events-consumer";
    private static final String DLQ_TOPIC = "account-suspension-events-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 12;

    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final AccountSuspensionService accountSuspensionService;
    private final AccountLimitsService accountLimitsService;
    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.account-suspension-events.enabled:true}")
    private boolean consumerEnabled;

    @Value("${account.suspension.auto-suspend-on-fraud:true}")
    private boolean autoSuspendOnFraud;

    @Value("${account.suspension.require-manual-review:false}")
    private boolean requireManualReview;

    @Value("${account.suspension.grace-period-hours:24}")
    private int gracePeriodHours;

    @Value("${account.suspension.send-notifications:true}")
    private boolean sendNotifications;

    @Value("${account.suspension.allow-partial-operations:true}")
    private boolean allowPartialOperations;

    @Value("${account.suspension.max-suspension-duration-days:90}")
    private int maxSuspensionDurationDays;

    @Value("${account.suspension.auto-lift-temporary:true}")
    private boolean autoLiftTemporary;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter suspensionEventTypeCounters;

    public AccountSuspensionEventsConsumer(ObjectMapper objectMapper,
                                          AccountService accountService,
                                          AccountSuspensionService accountSuspensionService,
                                          AccountLimitsService accountLimitsService,
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
        this.accountSuspensionService = accountSuspensionService;
        this.accountLimitsService = accountLimitsService;
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
        this.processedCounter = Counter.builder("account_suspension_events_processed_total")
                .description("Total processed account suspension events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("account_suspension_events_errors_total")
                .description("Total account suspension events processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("account_suspension_events_dlq_total")
                .description("Total account suspension events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("account_suspension_events_processing_duration")
                .description("Account suspension events processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("AccountSuspensionEventsConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.account-suspension-events:account-suspension-events}",
        groupId = "${kafka.consumer.group-id:account-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "account-suspension-events-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "account-suspension-events-retry")
    public void processAccountSuspensionEvents(
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
                logger.warn("Account suspension events consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing account suspension event message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidAccountSuspensionEventMessage(messageNode)) {
                logger.error("Invalid account suspension event message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String accountId = messageNode.get("accountId").asText();
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processSuspensionEvent(messageNode, accountId, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed account suspension event: messageId={}, accountId={}, eventType={}, processingTime={}ms",
                    messageId, accountId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordAccountSuspensionEventProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse account suspension event message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing account suspension event: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidAccountSuspensionEventMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("accountId") && StringUtils.hasText(messageNode.get("accountId").asText()) &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp") &&
                   messageNode.has("reason");
        } catch (Exception e) {
            logger.error("Error validating account suspension event message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processSuspensionEvent(JsonNode messageNode, String accountId, String eventType, 
                                      String correlationId, String traceId) {
        try {
            Account account = accountService.findById(accountId);
            if (account == null) {
                throw new ValidationException("Account not found: " + accountId);
            }

            AccountSuspensionEvent suspensionEvent = parseAccountSuspensionEvent(messageNode);
            
            validateSuspensionEvent(suspensionEvent, account);
            
            switch (suspensionEvent.getEventType()) {
                case SUSPENSION_REQUESTED:
                    processSuspensionRequested(suspensionEvent, account, correlationId, traceId);
                    break;
                case IMMEDIATE_SUSPENSION:
                    processImmediateSuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case TEMPORARY_SUSPENSION:
                    processTemporarySuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case PARTIAL_SUSPENSION:
                    processPartialSuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case COMPLIANCE_SUSPENSION:
                    processComplianceSuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case FRAUD_SUSPENSION:
                    processFraudSuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case SECURITY_SUSPENSION:
                    processSecuritySuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case REGULATORY_SUSPENSION:
                    processRegulatorySuspension(suspensionEvent, account, correlationId, traceId);
                    break;
                case SUSPENSION_REVIEW:
                    processSuspensionReview(suspensionEvent, account, correlationId, traceId);
                    break;
                case SUSPENSION_LIFTED:
                    processSuspensionLifted(suspensionEvent, account, correlationId, traceId);
                    break;
                case SUSPENSION_EXTENDED:
                    processSuspensionExtended(suspensionEvent, account, correlationId, traceId);
                    break;
                case SUSPENSION_APPEALED:
                    processSuspensionAppealed(suspensionEvent, account, correlationId, traceId);
                    break;
                case APPEAL_APPROVED:
                    processAppealApproved(suspensionEvent, account, correlationId, traceId);
                    break;
                case APPEAL_DENIED:
                    processAppealDenied(suspensionEvent, account, correlationId, traceId);
                    break;
                case AUTO_SUSPENSION_TRIGGERED:
                    processAutoSuspensionTriggered(suspensionEvent, account, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown account suspension event type: {}", suspensionEvent.getEventType());
                    throw new ValidationException("Unknown suspension event type: " + suspensionEvent.getEventType());
            }

            recordSuspensionEventAudit(suspensionEvent, account, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing account suspension event for account {}: {}", accountId, e.getMessage(), e);
            throw e;
        }
    }

    private AccountSuspensionEvent parseAccountSuspensionEvent(JsonNode messageNode) {
        try {
            AccountSuspensionEvent event = new AccountSuspensionEvent();
            event.setAccountId(messageNode.get("accountId").asText());
            event.setEventType(SuspensionEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            event.setReason(SuspensionReason.valueOf(messageNode.get("reason").asText()));
            
            if (messageNode.has("suspensionId")) {
                event.setSuspensionId(messageNode.get("suspensionId").asText());
            }
            
            if (messageNode.has("requestedBy")) {
                event.setRequestedBy(messageNode.get("requestedBy").asText());
            }
            
            if (messageNode.has("duration")) {
                event.setDuration(messageNode.get("duration").asInt());
            }
            
            if (messageNode.has("expiryDate")) {
                event.setExpiryDate(LocalDateTime.parse(messageNode.get("expiryDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            
            if (messageNode.has("description")) {
                event.setDescription(messageNode.get("description").asText());
            }
            
            if (messageNode.has("restrictions")) {
                JsonNode restrictionsNode = messageNode.get("restrictions");
                List<String> restrictions = new ArrayList<>();
                restrictionsNode.forEach(restriction -> restrictions.add(restriction.asText()));
                event.setRestrictions(restrictions);
            }
            
            if (messageNode.has("allowedOperations")) {
                JsonNode allowedNode = messageNode.get("allowedOperations");
                List<String> allowed = new ArrayList<>();
                allowedNode.forEach(op -> allowed.add(op.asText()));
                event.setAllowedOperations(allowed);
            }
            
            if (messageNode.has("evidenceProvided")) {
                JsonNode evidenceNode = messageNode.get("evidenceProvided");
                Map<String, String> evidence = new HashMap<>();
                evidenceNode.fields().forEachRemaining(entry -> {
                    evidence.put(entry.getKey(), entry.getValue().asText());
                });
                event.setEvidenceProvided(evidence);
            }
            
            if (messageNode.has("appealReason")) {
                event.setAppealReason(messageNode.get("appealReason").asText());
            }
            
            if (messageNode.has("reviewNotes")) {
                event.setReviewNotes(messageNode.get("reviewNotes").asText());
            }
            
            if (messageNode.has("riskScore")) {
                event.setRiskScore(messageNode.get("riskScore").asDouble());
            }
            
            if (messageNode.has("complianceFlags")) {
                JsonNode flagsNode = messageNode.get("complianceFlags");
                List<String> flags = new ArrayList<>();
                flagsNode.forEach(flag -> flags.add(flag.asText()));
                event.setComplianceFlags(flags);
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
            logger.error("Error parsing account suspension event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid account suspension event format: " + e.getMessage());
        }
    }

    private void validateSuspensionEvent(AccountSuspensionEvent suspensionEvent, Account account) {
        // Check if account is already closed
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ValidationException("Cannot suspend closed account: " + account.getId());
        }
        
        // Check if account is already suspended
        if (account.getStatus() == AccountStatus.SUSPENDED && 
            suspensionEvent.getEventType() == SuspensionEventType.SUSPENSION_REQUESTED) {
            throw new ValidationException("Account is already suspended: " + account.getId());
        }
        
        // Validate suspension duration
        if (suspensionEvent.getDuration() != null && suspensionEvent.getDuration() > maxSuspensionDurationDays) {
            throw new ValidationException("Suspension duration exceeds maximum allowed: " + maxSuspensionDurationDays + " days");
        }
    }

    private void processSuspensionRequested(AccountSuspensionEvent suspensionEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing suspension requested for account: {}", account.getId());
        
        // Create suspension record
        String suspensionId = accountSuspensionService.createSuspension(
            account.getId(),
            suspensionEvent.getReason(),
            suspensionEvent.getRequestedBy(),
            suspensionEvent.getDuration(),
            suspensionEvent.getDescription()
        );
        
        suspensionEvent.setSuspensionId(suspensionId);
        
        if (requireManualReview && suspensionEvent.getReason() != SuspensionReason.FRAUD) {
            logger.info("Manual review required for suspension request: {}", suspensionId);
            createManualReviewTask(account, suspensionEvent, correlationId);
        } else {
            // Proceed with immediate suspension
            triggerImmediateSuspension(account, suspensionEvent, correlationId, traceId);
        }
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendSuspensionNotification(account.getUserId(), account.getId(), 
                                                         suspensionEvent.getReason().toString(), 
                                                         "requested", correlationId);
        }
        
        auditService.recordSuspensionRequested(account.getId(), suspensionId, 
                                             suspensionEvent.getReason(), 
                                             suspensionEvent.getRequestedBy(), correlationId);
        
        metricsService.recordSuspensionRequested(account.getId(), suspensionEvent.getReason());
        
        logger.info("Suspension requested for account: {}, suspensionId: {}, reason: {}", 
                   account.getId(), suspensionId, suspensionEvent.getReason());
    }

    private void processImmediateSuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing immediate suspension for account: {}", account.getId());
        
        // Apply suspension immediately
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason(suspensionEvent.getReason().toString());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        
        // Apply restrictions
        applyAccountRestrictions(account, suspensionEvent);
        
        accountService.updateAccount(account);
        
        // Update suspension status
        accountSuspensionService.updateSuspensionStatus(suspensionEvent.getSuspensionId(), 
                                                       SuspensionStatus.ACTIVE);
        
        // Invalidate active sessions
        invalidateUserSessions(account.getUserId(), "Account suspended: " + suspensionEvent.getReason());
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendImmediateSuspensionNotification(account.getUserId(), account.getId(), 
                                                                  suspensionEvent.getReason().toString(), correlationId);
        }
        
        auditService.recordImmediateSuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                             suspensionEvent.getReason(), correlationId);
        
        metricsService.recordImmediateSuspension(account.getId());
        
        logger.info("Account immediately suspended: {}, reason: {}", account.getId(), suspensionEvent.getReason());
    }

    private void processTemporarySuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing temporary suspension for account: {}", account.getId());
        
        // Apply temporary suspension
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason(suspensionEvent.getReason().toString());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        
        LocalDateTime expiryDate = suspensionEvent.getExpiryDate() != null ? 
                                 suspensionEvent.getExpiryDate() : 
                                 suspensionEvent.getTimestamp().plusDays(suspensionEvent.getDuration());
        
        account.setSuspensionExpiryDate(expiryDate);
        
        accountService.updateAccount(account);
        
        // Schedule automatic lifting if enabled
        if (autoLiftTemporary) {
            scheduleAutomaticLifting(account.getId(), suspensionEvent.getSuspensionId(), expiryDate, correlationId);
        }
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendTemporarySuspensionNotification(account.getUserId(), account.getId(), 
                                                                  suspensionEvent.getReason().toString(), 
                                                                  expiryDate, correlationId);
        }
        
        auditService.recordTemporarySuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                             suspensionEvent.getReason(), expiryDate, correlationId);
        
        metricsService.recordTemporarySuspension(account.getId(), suspensionEvent.getDuration());
        
        logger.info("Temporary suspension applied to account: {}, expires: {}", account.getId(), expiryDate);
    }

    private void processPartialSuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing partial suspension for account: {}", account.getId());
        
        if (!allowPartialOperations) {
            logger.info("Partial operations not allowed, applying full suspension");
            processImmediateSuspension(suspensionEvent, account, correlationId, traceId);
            return;
        }
        
        // Apply partial suspension
        account.setStatus(AccountStatus.RESTRICTED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason(suspensionEvent.getReason().toString());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        
        // Apply specific restrictions
        applyPartialRestrictions(account, suspensionEvent);
        
        accountService.updateAccount(account);
        
        // Send notification with allowed operations
        if (sendNotifications) {
            notificationService.sendPartialSuspensionNotification(account.getUserId(), account.getId(), 
                                                                suspensionEvent.getRestrictions(), 
                                                                suspensionEvent.getAllowedOperations(), correlationId);
        }
        
        auditService.recordPartialSuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                           suspensionEvent.getRestrictions(), 
                                           suspensionEvent.getAllowedOperations(), correlationId);
        
        metricsService.recordPartialSuspension(account.getId());
        
        logger.info("Partial suspension applied to account: {}, restrictions: {}", 
                   account.getId(), suspensionEvent.getRestrictions());
    }

    private void processComplianceSuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing compliance suspension for account: {}", account.getId());
        
        // Apply compliance suspension
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason("COMPLIANCE: " + suspensionEvent.getReason());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        account.setComplianceHold(true);
        
        accountService.updateAccount(account);
        
        // Report to compliance
        complianceService.reportAccountSuspension(account.getId(), suspensionEvent.getReason().toString(), 
                                                suspensionEvent.getComplianceFlags(), correlationId);
        
        // Create compliance review task
        createComplianceReviewTask(account, suspensionEvent, correlationId);
        
        // Send regulatory notification if required
        if (suspensionEvent.getComplianceFlags() != null && 
            suspensionEvent.getComplianceFlags().contains("REGULATORY_REPORTING")) {
            complianceService.sendRegulatoryNotification(account.getId(), suspensionEvent.getReason().toString(), correlationId);
        }
        
        auditService.recordComplianceSuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                              suspensionEvent.getComplianceFlags(), correlationId);
        
        metricsService.recordComplianceSuspension(account.getId());
        
        logger.info("Compliance suspension applied to account: {}, flags: {}", 
                   account.getId(), suspensionEvent.getComplianceFlags());
    }

    private void processFraudSuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                      String correlationId, String traceId) {
        logger.warn("Processing fraud suspension for account: {}", account.getId());
        
        if (!autoSuspendOnFraud) {
            logger.info("Auto-suspend on fraud disabled, creating review task");
            createFraudReviewTask(account, suspensionEvent, correlationId);
            return;
        }
        
        // Apply immediate fraud suspension
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason("FRAUD: " + suspensionEvent.getDescription());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        account.setFraudFlag(true);
        
        // Apply full restrictions
        applyFullRestrictions(account);
        
        accountService.updateAccount(account);
        
        // Freeze all funds
        accountService.freezeAccountFunds(account.getId(), "Fraud suspension");
        
        // Invalidate all sessions immediately
        invalidateUserSessions(account.getUserId(), "FRAUD DETECTED - Account suspended");
        
        // Create fraud investigation
        createFraudInvestigation(account, suspensionEvent, correlationId);
        
        // Send urgent notification
        if (sendNotifications) {
            notificationService.sendFraudSuspensionAlert(account.getUserId(), account.getId(), 
                                                       suspensionEvent.getDescription(), correlationId);
        }
        
        auditService.recordFraudSuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                         suspensionEvent.getEvidenceProvided(), correlationId);
        
        metricsService.recordFraudSuspension(account.getId());
        
        logger.warn("Fraud suspension applied to account: {}, evidence: {}", 
                   account.getId(), suspensionEvent.getEvidenceProvided());
    }

    private void processSecuritySuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                         String correlationId, String traceId) {
        logger.warn("Processing security suspension for account: {}", account.getId());
        
        // Apply security suspension
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason("SECURITY: " + suspensionEvent.getReason());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        account.setSecurityHold(true);
        
        accountService.updateAccount(account);
        
        // Force password reset
        accountService.requirePasswordReset(account.getUserId());
        
        // Force 2FA re-enrollment
        accountService.require2FAReEnrollment(account.getUserId());
        
        // Invalidate all sessions
        invalidateUserSessions(account.getUserId(), "Security incident - Account suspended");
        
        // Create security incident
        createSecurityIncident(account, suspensionEvent, correlationId);
        
        // Send security alert
        if (sendNotifications) {
            notificationService.sendSecuritySuspensionAlert(account.getUserId(), account.getId(), 
                                                          suspensionEvent.getReason().toString(), correlationId);
        }
        
        auditService.recordSecuritySuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                            suspensionEvent.getRiskScore(), correlationId);
        
        metricsService.recordSecuritySuspension(account.getId());
        
        logger.warn("Security suspension applied to account: {}, risk: {}", 
                   account.getId(), suspensionEvent.getRiskScore());
    }

    private void processRegulatorySuspension(AccountSuspensionEvent suspensionEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing regulatory suspension for account: {}", account.getId());
        
        // Apply regulatory suspension
        account.setStatus(AccountStatus.SUSPENDED);
        account.setSuspendedDate(suspensionEvent.getTimestamp());
        account.setSuspensionReason("REGULATORY: " + suspensionEvent.getReason());
        account.setSuspensionId(suspensionEvent.getSuspensionId());
        account.setRegulatoryHold(true);
        
        accountService.updateAccount(account);
        
        // Report to regulatory authorities
        complianceService.reportRegulatoryAction(account.getId(), "SUSPENSION", 
                                               suspensionEvent.getReason().toString(), correlationId);
        
        // Freeze all operations
        applyFullRestrictions(account);
        
        // Create regulatory compliance task
        createRegulatoryComplianceTask(account, suspensionEvent, correlationId);
        
        auditService.recordRegulatorySuspension(account.getId(), suspensionEvent.getSuspensionId(), 
                                              suspensionEvent.getReason(), correlationId);
        
        metricsService.recordRegulatorySuspension(account.getId());
        
        logger.info("Regulatory suspension applied to account: {}", account.getId());
    }

    private void processSuspensionReview(AccountSuspensionEvent suspensionEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing suspension review for account: {}", account.getId());
        
        // Update suspension status to under review
        accountSuspensionService.updateSuspensionStatus(suspensionEvent.getSuspensionId(), 
                                                       SuspensionStatus.UNDER_REVIEW);
        
        // Create review task
        Map<String, Object> reviewTask = new HashMap<>();
        reviewTask.put("accountId", account.getId());
        reviewTask.put("suspensionId", suspensionEvent.getSuspensionId());
        reviewTask.put("taskType", "SUSPENSION_REVIEW");
        reviewTask.put("priority", "HIGH");
        reviewTask.put("reviewNotes", suspensionEvent.getReviewNotes());
        reviewTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("review-tasks", reviewTask);
        
        auditService.recordSuspensionReview(account.getId(), suspensionEvent.getSuspensionId(), 
                                          suspensionEvent.getReviewNotes(), correlationId);
        
        metricsService.recordSuspensionReview(account.getId());
        
        logger.info("Suspension review initiated for account: {}, suspensionId: {}", 
                   account.getId(), suspensionEvent.getSuspensionId());
    }

    private void processSuspensionLifted(AccountSuspensionEvent suspensionEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing suspension lifted for account: {}", account.getId());
        
        // Lift suspension
        account.setStatus(AccountStatus.ACTIVE);
        account.setSuspendedDate(null);
        account.setSuspensionReason(null);
        account.setSuspensionId(null);
        account.setSuspensionExpiryDate(null);
        account.setComplianceHold(false);
        account.setSecurityHold(false);
        account.setRegulatoryHold(false);
        
        // Remove restrictions
        removeAccountRestrictions(account);
        
        accountService.updateAccount(account);
        
        // Update suspension status
        accountSuspensionService.updateSuspensionStatus(suspensionEvent.getSuspensionId(), 
                                                       SuspensionStatus.LIFTED);
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendSuspensionLiftedNotification(account.getUserId(), account.getId(), correlationId);
        }
        
        auditService.recordSuspensionLifted(account.getId(), suspensionEvent.getSuspensionId(), 
                                          suspensionEvent.getReviewNotes(), correlationId);
        
        metricsService.recordSuspensionLifted(account.getId());
        
        logger.info("Suspension lifted for account: {}", account.getId());
    }

    private void processSuspensionExtended(AccountSuspensionEvent suspensionEvent, Account account, 
                                         String correlationId, String traceId) {
        logger.info("Processing suspension extended for account: {}", account.getId());
        
        LocalDateTime newExpiryDate = suspensionEvent.getExpiryDate() != null ? 
                                    suspensionEvent.getExpiryDate() : 
                                    account.getSuspensionExpiryDate().plusDays(suspensionEvent.getDuration());
        
        account.setSuspensionExpiryDate(newExpiryDate);
        accountService.updateAccount(account);
        
        // Update suspension record
        accountSuspensionService.extendSuspension(suspensionEvent.getSuspensionId(), 
                                                 newExpiryDate, suspensionEvent.getDescription());
        
        // Reschedule automatic lifting if enabled
        if (autoLiftTemporary) {
            scheduleAutomaticLifting(account.getId(), suspensionEvent.getSuspensionId(), 
                                   newExpiryDate, correlationId);
        }
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendSuspensionExtendedNotification(account.getUserId(), account.getId(), 
                                                                 newExpiryDate, correlationId);
        }
        
        auditService.recordSuspensionExtended(account.getId(), suspensionEvent.getSuspensionId(), 
                                            newExpiryDate, suspensionEvent.getDescription(), correlationId);
        
        metricsService.recordSuspensionExtended(account.getId());
        
        logger.info("Suspension extended for account: {}, new expiry: {}", account.getId(), newExpiryDate);
    }

    private void processSuspensionAppealed(AccountSuspensionEvent suspensionEvent, Account account, 
                                         String correlationId, String traceId) {
        logger.info("Processing suspension appeal for account: {}", account.getId());
        
        // Create appeal record
        String appealId = accountSuspensionService.createAppeal(
            suspensionEvent.getSuspensionId(),
            suspensionEvent.getAppealReason(),
            suspensionEvent.getEvidenceProvided()
        );
        
        // Update suspension status
        accountSuspensionService.updateSuspensionStatus(suspensionEvent.getSuspensionId(), 
                                                       SuspensionStatus.APPEALED);
        
        // Create appeal review task
        createAppealReviewTask(account, suspensionEvent, appealId, correlationId);
        
        // Send acknowledgment
        if (sendNotifications) {
            notificationService.sendAppealAcknowledgment(account.getUserId(), account.getId(), 
                                                       appealId, correlationId);
        }
        
        auditService.recordSuspensionAppealed(account.getId(), suspensionEvent.getSuspensionId(), 
                                            appealId, suspensionEvent.getAppealReason(), correlationId);
        
        metricsService.recordSuspensionAppealed(account.getId());
        
        logger.info("Suspension appeal created for account: {}, appealId: {}", account.getId(), appealId);
    }

    private void processAppealApproved(AccountSuspensionEvent suspensionEvent, Account account, 
                                     String correlationId, String traceId) {
        logger.info("Processing appeal approved for account: {}", account.getId());
        
        // Lift suspension
        processSuspensionLifted(suspensionEvent, account, correlationId, traceId);
        
        // Update appeal status
        accountSuspensionService.updateAppealStatus(suspensionEvent.getSuspensionId(), "APPROVED");
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendAppealApprovedNotification(account.getUserId(), account.getId(), correlationId);
        }
        
        auditService.recordAppealApproved(account.getId(), suspensionEvent.getSuspensionId(), 
                                        suspensionEvent.getReviewNotes(), correlationId);
        
        metricsService.recordAppealApproved(account.getId());
        
        logger.info("Appeal approved for account: {}", account.getId());
    }

    private void processAppealDenied(AccountSuspensionEvent suspensionEvent, Account account, 
                                  String correlationId, String traceId) {
        logger.info("Processing appeal denied for account: {}", account.getId());
        
        // Update appeal status
        accountSuspensionService.updateAppealStatus(suspensionEvent.getSuspensionId(), "DENIED");
        
        // Send notification with reason
        if (sendNotifications) {
            notificationService.sendAppealDeniedNotification(account.getUserId(), account.getId(), 
                                                           suspensionEvent.getReviewNotes(), correlationId);
        }
        
        auditService.recordAppealDenied(account.getId(), suspensionEvent.getSuspensionId(), 
                                      suspensionEvent.getReviewNotes(), correlationId);
        
        metricsService.recordAppealDenied(account.getId());
        
        logger.info("Appeal denied for account: {}, reason: {}", account.getId(), suspensionEvent.getReviewNotes());
    }

    private void processAutoSuspensionTriggered(AccountSuspensionEvent suspensionEvent, Account account, 
                                              String correlationId, String traceId) {
        logger.warn("Processing auto suspension triggered for account: {}", account.getId());
        
        // Apply automatic suspension based on trigger
        suspensionEvent.setRequestedBy("SYSTEM");
        
        if (suspensionEvent.getReason() == SuspensionReason.FRAUD && autoSuspendOnFraud) {
            processFraudSuspension(suspensionEvent, account, correlationId, traceId);
        } else {
            processImmediateSuspension(suspensionEvent, account, correlationId, traceId);
        }
        
        auditService.recordAutoSuspension(account.getId(), suspensionEvent.getReason(), 
                                        suspensionEvent.getRiskScore(), correlationId);
        
        metricsService.recordAutoSuspension(account.getId());
        
        logger.warn("Auto suspension triggered for account: {}, reason: {}", 
                   account.getId(), suspensionEvent.getReason());
    }

    private void applyAccountRestrictions(Account account, AccountSuspensionEvent suspensionEvent) {
        // Apply transaction limits
        accountLimitsService.applySuspensionLimits(account.getId());
        
        // Disable features
        account.setTransactionsEnabled(false);
        account.setTransfersEnabled(false);
        account.setWithdrawalsEnabled(false);
        account.setDepositsEnabled(true); // Allow deposits even when suspended
    }

    private void applyPartialRestrictions(Account account, AccountSuspensionEvent suspensionEvent) {
        List<String> restrictions = suspensionEvent.getRestrictions();
        
        if (restrictions.contains("NO_WITHDRAWALS")) {
            account.setWithdrawalsEnabled(false);
        }
        if (restrictions.contains("NO_TRANSFERS")) {
            account.setTransfersEnabled(false);
        }
        if (restrictions.contains("NO_INTERNATIONAL")) {
            account.setInternationalTransfersEnabled(false);
        }
        if (restrictions.contains("REDUCED_LIMITS")) {
            accountLimitsService.applyReducedLimits(account.getId());
        }
    }

    private void applyFullRestrictions(Account account) {
        account.setTransactionsEnabled(false);
        account.setTransfersEnabled(false);
        account.setWithdrawalsEnabled(false);
        account.setDepositsEnabled(false);
        account.setOnlineBankingEnabled(false);
        account.setMobileBankingEnabled(false);
        account.setDebitCardEnabled(false);
        
        accountLimitsService.applyZeroLimits(account.getId());
    }

    private void removeAccountRestrictions(Account account) {
        account.setTransactionsEnabled(true);
        account.setTransfersEnabled(true);
        account.setWithdrawalsEnabled(true);
        account.setDepositsEnabled(true);
        account.setOnlineBankingEnabled(true);
        account.setMobileBankingEnabled(true);
        account.setDebitCardEnabled(true);
        account.setInternationalTransfersEnabled(true);
        
        accountLimitsService.restoreNormalLimits(account.getId());
    }

    private void invalidateUserSessions(String userId, String reason) {
        Map<String, Object> sessionEvent = new HashMap<>();
        sessionEvent.put("userId", userId);
        sessionEvent.put("eventType", "INVALIDATE_ALL_SESSIONS");
        sessionEvent.put("reason", reason);
        sessionEvent.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("user-session-events", sessionEvent);
    }

    private void scheduleAutomaticLifting(String accountId, String suspensionId, LocalDateTime expiryDate, 
                                        String correlationId) {
        Map<String, Object> liftEvent = new HashMap<>();
        liftEvent.put("accountId", accountId);
        liftEvent.put("suspensionId", suspensionId);
        liftEvent.put("eventType", "SUSPENSION_LIFTED");
        liftEvent.put("scheduledTime", expiryDate.toString());
        liftEvent.put("reason", "AUTOMATIC_EXPIRY");
        liftEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("scheduled-events", liftEvent);
    }

    private void createManualReviewTask(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> reviewTask = new HashMap<>();
        reviewTask.put("accountId", account.getId());
        reviewTask.put("suspensionId", suspensionEvent.getSuspensionId());
        reviewTask.put("taskType", "SUSPENSION_MANUAL_REVIEW");
        reviewTask.put("priority", "MEDIUM");
        reviewTask.put("reason", suspensionEvent.getReason().toString());
        reviewTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("manual-review-tasks", reviewTask);
    }

    private void createComplianceReviewTask(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> complianceTask = new HashMap<>();
        complianceTask.put("accountId", account.getId());
        complianceTask.put("suspensionId", suspensionEvent.getSuspensionId());
        complianceTask.put("taskType", "COMPLIANCE_REVIEW");
        complianceTask.put("priority", "HIGH");
        complianceTask.put("complianceFlags", suspensionEvent.getComplianceFlags());
        complianceTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("compliance-review-tasks", complianceTask);
    }

    private void createFraudReviewTask(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> fraudTask = new HashMap<>();
        fraudTask.put("accountId", account.getId());
        fraudTask.put("suspensionId", suspensionEvent.getSuspensionId());
        fraudTask.put("taskType", "FRAUD_REVIEW");
        fraudTask.put("priority", "CRITICAL");
        fraudTask.put("evidence", suspensionEvent.getEvidenceProvided());
        fraudTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("fraud-review-tasks", fraudTask);
    }

    private void createFraudInvestigation(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> investigation = new HashMap<>();
        investigation.put("accountId", account.getId());
        investigation.put("suspensionId", suspensionEvent.getSuspensionId());
        investigation.put("investigationType", "FRAUD_SUSPENSION");
        investigation.put("priority", "CRITICAL");
        investigation.put("evidence", suspensionEvent.getEvidenceProvided());
        investigation.put("timestamp", LocalDateTime.now().toString());
        investigation.put("correlationId", correlationId);
        
        kafkaTemplate.send("fraud-investigations", investigation);
    }

    private void createSecurityIncident(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> incident = new HashMap<>();
        incident.put("accountId", account.getId());
        incident.put("incidentType", "SECURITY_SUSPENSION");
        incident.put("severity", "HIGH");
        incident.put("reason", suspensionEvent.getReason().toString());
        incident.put("riskScore", suspensionEvent.getRiskScore());
        incident.put("timestamp", LocalDateTime.now().toString());
        incident.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-incidents", incident);
    }

    private void createRegulatoryComplianceTask(Account account, AccountSuspensionEvent suspensionEvent, String correlationId) {
        Map<String, Object> regulatoryTask = new HashMap<>();
        regulatoryTask.put("accountId", account.getId());
        regulatoryTask.put("suspensionId", suspensionEvent.getSuspensionId());
        regulatoryTask.put("taskType", "REGULATORY_COMPLIANCE");
        regulatoryTask.put("priority", "CRITICAL");
        regulatoryTask.put("reason", suspensionEvent.getReason().toString());
        regulatoryTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("regulatory-compliance-tasks", regulatoryTask);
    }

    private void createAppealReviewTask(Account account, AccountSuspensionEvent suspensionEvent, 
                                      String appealId, String correlationId) {
        Map<String, Object> appealTask = new HashMap<>();
        appealTask.put("accountId", account.getId());
        appealTask.put("suspensionId", suspensionEvent.getSuspensionId());
        appealTask.put("appealId", appealId);
        appealTask.put("taskType", "APPEAL_REVIEW");
        appealTask.put("priority", "HIGH");
        appealTask.put("appealReason", suspensionEvent.getAppealReason());
        appealTask.put("evidence", suspensionEvent.getEvidenceProvided());
        appealTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("appeal-review-tasks", appealTask);
    }

    private void triggerImmediateSuspension(Account account, AccountSuspensionEvent suspensionEvent, 
                                          String correlationId, String traceId) {
        Map<String, Object> immediateEvent = new HashMap<>();
        immediateEvent.put("accountId", account.getId());
        immediateEvent.put("suspensionId", suspensionEvent.getSuspensionId());
        immediateEvent.put("eventType", "IMMEDIATE_SUSPENSION");
        immediateEvent.put("reason", suspensionEvent.getReason().toString());
        immediateEvent.put("timestamp", LocalDateTime.now().toString());
        immediateEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-suspension-events", immediateEvent);
    }

    private void recordSuspensionEventAudit(AccountSuspensionEvent suspensionEvent, Account account, 
                                          String correlationId, String traceId) {
        AccountSuspensionAuditLog auditLog = new AccountSuspensionAuditLog();
        auditLog.setAccountId(account.getId());
        auditLog.setEventType(suspensionEvent.getEventType());
        auditLog.setSuspensionId(suspensionEvent.getSuspensionId());
        auditLog.setReason(suspensionEvent.getReason());
        auditLog.setRequestedBy(suspensionEvent.getRequestedBy());
        auditLog.setDuration(suspensionEvent.getDuration());
        auditLog.setRestrictions(suspensionEvent.getRestrictions());
        auditLog.setRiskScore(suspensionEvent.getRiskScore());
        auditLog.setTimestamp(suspensionEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordAccountSuspensionEventAudit(auditLog);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing account suspension event message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for account suspension events consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public enum SuspensionEventType {
        SUSPENSION_REQUESTED,
        IMMEDIATE_SUSPENSION,
        TEMPORARY_SUSPENSION,
        PARTIAL_SUSPENSION,
        COMPLIANCE_SUSPENSION,
        FRAUD_SUSPENSION,
        SECURITY_SUSPENSION,
        REGULATORY_SUSPENSION,
        SUSPENSION_REVIEW,
        SUSPENSION_LIFTED,
        SUSPENSION_EXTENDED,
        SUSPENSION_APPEALED,
        APPEAL_APPROVED,
        APPEAL_DENIED,
        AUTO_SUSPENSION_TRIGGERED
    }

    public static class AccountSuspensionEvent {
        private String accountId;
        private SuspensionEventType eventType;
        private LocalDateTime timestamp;
        private SuspensionReason reason;
        private String suspensionId;
        private String requestedBy;
        private Integer duration;
        private LocalDateTime expiryDate;
        private String description;
        private List<String> restrictions;
        private List<String> allowedOperations;
        private Map<String, String> evidenceProvided;
        private String appealReason;
        private String reviewNotes;
        private Double riskScore;
        private List<String> complianceFlags;
        private Map<String, Object> eventMetadata;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public SuspensionEventType getEventType() { return eventType; }
        public void setEventType(SuspensionEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public SuspensionReason getReason() { return reason; }
        public void setReason(SuspensionReason reason) { this.reason = reason; }

        public String getSuspensionId() { return suspensionId; }
        public void setSuspensionId(String suspensionId) { this.suspensionId = suspensionId; }

        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }

        public LocalDateTime getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getRestrictions() { return restrictions; }
        public void setRestrictions(List<String> restrictions) { this.restrictions = restrictions; }

        public List<String> getAllowedOperations() { return allowedOperations; }
        public void setAllowedOperations(List<String> allowedOperations) { this.allowedOperations = allowedOperations; }

        public Map<String, String> getEvidenceProvided() { return evidenceProvided; }
        public void setEvidenceProvided(Map<String, String> evidenceProvided) { this.evidenceProvided = evidenceProvided; }

        public String getAppealReason() { return appealReason; }
        public void setAppealReason(String appealReason) { this.appealReason = appealReason; }

        public String getReviewNotes() { return reviewNotes; }
        public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getComplianceFlags() { return complianceFlags; }
        public void setComplianceFlags(List<String> complianceFlags) { this.complianceFlags = complianceFlags; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}