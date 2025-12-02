package com.waqiti.account.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountStatus;
import com.waqiti.account.domain.DormancyLevel;
import com.waqiti.account.domain.DormancyAction;
import com.waqiti.account.domain.DormantAccountAuditLog;
import com.waqiti.account.service.AccountService;
import com.waqiti.account.service.DormancyAnalysisService;
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
public class DormantAccountMonitoringConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DormantAccountMonitoringConsumer.class);
    private static final String CONSUMER_NAME = "dormant-account-monitoring-consumer";
    private static final String DLQ_TOPIC = "dormant-account-monitoring-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 20;

    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final DormancyAnalysisService dormancyAnalysisService;
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
    
    public DormantAccountMonitoringConsumer(ObjectMapper objectMapper,
                                           AccountService accountService,
                                           DormancyAnalysisService dormancyAnalysisService,
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
        this.dormancyAnalysisService = dormancyAnalysisService;
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

    @Value("${kafka.consumer.dormant-account-monitoring.enabled:true}")
    private boolean consumerEnabled;

    @Value("${account.dormancy.inactive-threshold-days:90}")
    private int inactiveThresholdDays;

    @Value("${account.dormancy.dormant-threshold-days:365}")
    private int dormantThresholdDays;

    @Value("${account.dormancy.escheatment-threshold-days:1825}")
    private int escheatmentThresholdDays;

    @Value("${account.dormancy.auto-apply-restrictions:true}")
    private boolean autoApplyRestrictions;

    @Value("${account.dormancy.send-reactivation-notifications:true}")
    private boolean sendReactivationNotifications;

    @Value("${account.dormancy.min-balance-for-monitoring:0.01}")
    private BigDecimal minBalanceForMonitoring;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter dormancyLevelCounters;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("dormant_account_monitoring_processed_total")
                .description("Total processed dormant account monitoring events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("dormant_account_monitoring_errors_total")
                .description("Total dormant account monitoring processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("dormant_account_monitoring_dlq_total")
                .description("Total dormant account monitoring events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("dormant_account_monitoring_processing_duration")
                .description("Dormant account monitoring processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("DormantAccountMonitoringConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.dormant-account-monitoring:dormant-account-monitoring}",
        groupId = "${kafka.consumer.group-id:account-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "dormant-account-monitoring-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "dormant-account-monitoring-retry")
    public void processDormantAccountMonitoring(
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
                logger.warn("Dormant account monitoring consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing dormant account monitoring message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidDormantAccountMonitoringMessage(messageNode)) {
                logger.error("Invalid dormant account monitoring message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String accountId = messageNode.get("accountId").asText();
            String monitoringType = messageNode.get("monitoringType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processDormancyMonitoring(messageNode, accountId, monitoringType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed dormant account monitoring: messageId={}, accountId={}, monitoringType={}, processingTime={}ms",
                    messageId, accountId, monitoringType, processingTime);

            processedCounter.increment();
            metricsService.recordDormantAccountMonitoringProcessed(monitoringType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse dormant account monitoring message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing dormant account monitoring: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidDormantAccountMonitoringMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("accountId") && StringUtils.hasText(messageNode.get("accountId").asText()) &&
                   messageNode.has("monitoringType") && StringUtils.hasText(messageNode.get("monitoringType").asText()) &&
                   messageNode.has("timestamp") &&
                   messageNode.has("lastActivityDate");
        } catch (Exception e) {
            logger.error("Error validating dormant account monitoring message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processDormancyMonitoring(JsonNode messageNode, String accountId, String monitoringType, 
                                         String correlationId, String traceId) {
        try {
            Account account = accountService.findById(accountId);
            if (account == null) {
                throw new ValidationException("Account not found: " + accountId);
            }

            DormantAccountMonitoringEvent monitoringEvent = parseDormantAccountMonitoringEvent(messageNode);
            
            validateDormancyMonitoring(monitoringEvent, account);
            
            switch (monitoringEvent.getMonitoringType()) {
                case INACTIVITY_CHECK:
                    processInactivityCheck(monitoringEvent, account, correlationId, traceId);
                    break;
                case DORMANCY_ASSESSMENT:
                    processDormancyAssessment(monitoringEvent, account, correlationId, traceId);
                    break;
                case REACTIVATION_MONITORING:
                    processReactivationMonitoring(monitoringEvent, account, correlationId, traceId);
                    break;
                case AUTOMATED_RESTRICTIONS:
                    processAutomatedRestrictions(monitoringEvent, account, correlationId, traceId);
                    break;
                case BALANCE_MONITORING:
                    processBalanceMonitoring(monitoringEvent, account, correlationId, traceId);
                    break;
                case ESCALATION_REVIEW:
                    processEscalationReview(monitoringEvent, account, correlationId, traceId);
                    break;
                case ESCHEATMENT_PREPARATION:
                    processEscheatmentPreparation(monitoringEvent, account, correlationId, traceId);
                    break;
                case COMPLIANCE_REVIEW:
                    processComplianceReview(monitoringEvent, account, correlationId, traceId);
                    break;
                case NOTIFICATION_TRIGGER:
                    processNotificationTrigger(monitoringEvent, account, correlationId, traceId);
                    break;
                case ACTIVITY_VERIFICATION:
                    processActivityVerification(monitoringEvent, account, correlationId, traceId);
                    break;
                case PERIODIC_REVIEW:
                    processPeriodicReview(monitoringEvent, account, correlationId, traceId);
                    break;
                case STATUS_TRANSITION:
                    processStatusTransition(monitoringEvent, account, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown dormancy monitoring type: {}", monitoringEvent.getMonitoringType());
                    throw new ValidationException("Unknown monitoring type: " + monitoringEvent.getMonitoringType());
            }

            recordDormancyMonitoringAudit(monitoringEvent, account, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing dormancy monitoring for account {}: {}", accountId, e.getMessage(), e);
            throw e;
        }
    }

    private DormantAccountMonitoringEvent parseDormantAccountMonitoringEvent(JsonNode messageNode) {
        try {
            DormantAccountMonitoringEvent event = new DormantAccountMonitoringEvent();
            event.setAccountId(messageNode.get("accountId").asText());
            event.setMonitoringType(DormancyMonitoringType.valueOf(messageNode.get("monitoringType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            event.setLastActivityDate(LocalDateTime.parse(messageNode.get("lastActivityDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("currentBalance")) {
                event.setCurrentBalance(new BigDecimal(messageNode.get("currentBalance").asText()));
            }
            
            if (messageNode.has("dormancyLevel")) {
                event.setDormancyLevel(DormancyLevel.valueOf(messageNode.get("dormancyLevel").asText()));
            }
            
            if (messageNode.has("daysInactive")) {
                event.setDaysInactive(messageNode.get("daysInactive").asInt());
            } else {
                event.setDaysInactive((int) ChronoUnit.DAYS.between(event.getLastActivityDate(), event.getTimestamp()));
            }
            
            if (messageNode.has("activityMetrics")) {
                JsonNode metricsNode = messageNode.get("activityMetrics");
                Map<String, Object> metrics = new HashMap<>();
                metricsNode.fields().forEachRemaining(entry -> {
                    metrics.put(entry.getKey(), entry.getValue().asText());
                });
                event.setActivityMetrics(metrics);
            }
            
            if (messageNode.has("previousStatus")) {
                event.setPreviousStatus(AccountStatus.valueOf(messageNode.get("previousStatus").asText()));
            }
            
            if (messageNode.has("riskFactors")) {
                JsonNode riskNode = messageNode.get("riskFactors");
                List<String> riskFactors = new ArrayList<>();
                riskNode.forEach(factor -> riskFactors.add(factor.asText()));
                event.setRiskFactors(riskFactors);
            }
            
            if (messageNode.has("recommendedActions")) {
                JsonNode actionsNode = messageNode.get("recommendedActions");
                List<DormancyAction> actions = new ArrayList<>();
                actionsNode.forEach(action -> actions.add(DormancyAction.valueOf(action.asText())));
                event.setRecommendedActions(actions);
            }
            
            if (messageNode.has("autoExecute")) {
                event.setAutoExecute(messageNode.get("autoExecute").asBoolean());
            }
            
            if (messageNode.has("escalationRequired")) {
                event.setEscalationRequired(messageNode.get("escalationRequired").asBoolean());
            }

            return event;
        } catch (Exception e) {
            logger.error("Error parsing dormant account monitoring event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid dormant account monitoring event format: " + e.getMessage());
        }
    }

    private void validateDormancyMonitoring(DormantAccountMonitoringEvent monitoringEvent, Account account) {
        if (!account.isActive() && account.getStatus() != AccountStatus.DORMANT && 
            account.getStatus() != AccountStatus.INACTIVE) {
            throw new ValidationException("Account status not suitable for dormancy monitoring: " + account.getStatus());
        }
        
        if (monitoringEvent.getCurrentBalance() != null && 
            monitoringEvent.getCurrentBalance().compareTo(minBalanceForMonitoring) < 0) {
            logger.info("Account balance below monitoring threshold: {}", monitoringEvent.getCurrentBalance());
        }
        
        if (monitoringEvent.getDaysInactive() < 0) {
            throw new ValidationException("Days inactive cannot be negative: " + monitoringEvent.getDaysInactive());
        }
    }

    private void processInactivityCheck(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                      String correlationId, String traceId) {
        logger.info("Processing inactivity check for account: {}", account.getId());
        
        int daysInactive = monitoringEvent.getDaysInactive();
        DormancyLevel currentLevel = determineDormancyLevel(daysInactive);
        
        logger.info("Account {} has been inactive for {} days, dormancy level: {}", 
                   account.getId(), daysInactive, currentLevel);
        
        if (currentLevel != account.getDormancyLevel()) {
            account.setDormancyLevel(currentLevel);
            account.setLastDormancyAssessment(LocalDateTime.now());
            accountService.updateAccount(account);
            
            logger.info("Updated dormancy level for account {}: {}", account.getId(), currentLevel);
        }
        
        // Trigger appropriate actions based on inactivity period
        if (daysInactive >= inactiveThresholdDays && daysInactive < dormantThresholdDays) {
            triggerInactiveAccountActions(account, daysInactive, correlationId, traceId);
        } else if (daysInactive >= dormantThresholdDays && daysInactive < escheatmentThresholdDays) {
            triggerDormantAccountActions(account, daysInactive, correlationId, traceId);
        } else if (daysInactive >= escheatmentThresholdDays) {
            triggerEscheatmentActions(account, daysInactive, correlationId, traceId);
        }
        
        metricsService.recordAccountInactivityCheck(account.getId(), daysInactive, currentLevel);
    }

    private DormancyLevel determineDormancyLevel(int daysInactive) {
        if (daysInactive < inactiveThresholdDays) {
            return DormancyLevel.ACTIVE;
        } else if (daysInactive < dormantThresholdDays) {
            return DormancyLevel.INACTIVE;
        } else if (daysInactive < escheatmentThresholdDays) {
            return DormancyLevel.DORMANT;
        } else {
            return DormancyLevel.ESCHEATMENT_ELIGIBLE;
        }
    }

    private void triggerInactiveAccountActions(Account account, int daysInactive, String correlationId, String traceId) {
        logger.info("Triggering inactive account actions for account: {}", account.getId());
        
        // Send first inactivity notification
        if (sendReactivationNotifications) {
            notificationService.sendInactivityNotification(account.getUserId(), account.getId(), 
                                                          daysInactive, "FIRST_WARNING", correlationId);
        }
        
        // Record inactivity milestone
        auditService.recordInactivityMilestone(account.getId(), "INACTIVE_THRESHOLD_REACHED", 
                                             daysInactive, correlationId);
        
        // Schedule follow-up monitoring
        scheduleFollowUpMonitoring(account.getId(), DormancyMonitoringType.INACTIVITY_CHECK, 30, correlationId);
    }

    private void triggerDormantAccountActions(Account account, int daysInactive, String correlationId, String traceId) {
        logger.info("Triggering dormant account actions for account: {}", account.getId());
        
        if (account.getStatus() != AccountStatus.DORMANT) {
            account.setStatus(AccountStatus.DORMANT);
            account.setStatusChangeDate(LocalDateTime.now());
            accountService.updateAccount(account);
            
            logger.info("Changed account status to DORMANT: {}", account.getId());
        }
        
        if (autoApplyRestrictions) {
            applyDormantAccountRestrictions(account, correlationId, traceId);
        }
        
        // Send dormancy notification
        if (sendReactivationNotifications) {
            notificationService.sendDormancyNotification(account.getUserId(), account.getId(), 
                                                        daysInactive, correlationId);
        }
        
        // Initiate compliance review
        complianceService.initiateDormantAccountReview(account.getId(), correlationId);
        
        auditService.recordDormancyMilestone(account.getId(), "DORMANT_STATUS_APPLIED", 
                                           daysInactive, correlationId);
    }

    private void triggerEscheatmentActions(Account account, int daysInactive, String correlationId, String traceId) {
        logger.info("Triggering escheatment actions for account: {}", account.getId());
        
        // Send final notification before escheatment
        if (sendReactivationNotifications) {
            notificationService.sendEscheatmentWarningNotification(account.getUserId(), account.getId(), 
                                                                  daysInactive, correlationId);
        }
        
        // Begin escheatment preparation
        complianceService.beginEscheatmentProcess(account.getId(), correlationId);
        
        auditService.recordEscheatmentMilestone(account.getId(), "ESCHEATMENT_ELIGIBLE", 
                                              daysInactive, correlationId);
        
        // Notify regulatory authorities if required
        complianceService.notifyRegulatoryEscheatment(account.getId(), correlationId);
    }

    private void applyDormantAccountRestrictions(Account account, String correlationId, String traceId) {
        logger.info("Applying dormant account restrictions for account: {}", account.getId());
        
        // Reduce transaction limits
        accountLimitsService.applyDormantAccountLimits(account.getId());
        
        // Disable certain features
        account.setOnlineBankingEnabled(false);
        account.setMobileBankingEnabled(false);
        account.setDebitCardEnabled(false);
        
        accountService.updateAccount(account);
        
        auditService.recordDormantRestrictions(account.getId(), correlationId);
        
        logger.info("Applied dormant account restrictions for account: {}", account.getId());
    }

    private void processDormancyAssessment(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                         String correlationId, String traceId) {
        logger.info("Processing dormancy assessment for account: {}", account.getId());
        
        DormancyAssessmentResult assessment = dormancyAnalysisService.performDormancyAssessment(account, monitoringEvent);
        
        account.setDormancyLevel(assessment.getDormancyLevel());
        account.setDormancyScore(assessment.getDormancyScore());
        account.setLastDormancyAssessment(LocalDateTime.now());
        
        if (assessment.getRecommendedActions() != null && !assessment.getRecommendedActions().isEmpty()) {
            if (autoApplyRestrictions && assessment.shouldAutoExecute()) {
                executeRecommendedActions(account, assessment.getRecommendedActions(), correlationId, traceId);
            } else {
                scheduleManualReview(account.getId(), assessment.getRecommendedActions(), correlationId);
            }
        }
        
        accountService.updateAccount(account);
        
        auditService.recordDormancyAssessment(account.getId(), assessment, correlationId);
    }

    private void executeRecommendedActions(Account account, List<DormancyAction> actions, 
                                         String correlationId, String traceId) {
        logger.info("Executing recommended dormancy actions for account: {}", account.getId());
        
        for (DormancyAction action : actions) {
            switch (action) {
                case APPLY_RESTRICTIONS:
                    applyDormantAccountRestrictions(account, correlationId, traceId);
                    break;
                case SEND_NOTIFICATION:
                    notificationService.sendDormancyNotification(account.getUserId(), account.getId(), 
                                                               account.getDaysInactive(), correlationId);
                    break;
                case REDUCE_LIMITS:
                    accountLimitsService.applyDormantAccountLimits(account.getId());
                    break;
                case INITIATE_COMPLIANCE_REVIEW:
                    complianceService.initiateDormantAccountReview(account.getId(), correlationId);
                    break;
                case SCHEDULE_CLOSURE_REVIEW:
                    scheduleClosureReview(account.getId(), correlationId);
                    break;
                default:
                    logger.warn("Unknown dormancy action: {}", action);
            }
        }
        
        auditService.recordDormancyActionsExecuted(account.getId(), actions, correlationId);
    }

    private void processReactivationMonitoring(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                             String correlationId, String traceId) {
        logger.info("Processing reactivation monitoring for account: {}", account.getId());
        
        if (account.getStatus() == AccountStatus.DORMANT || account.getStatus() == AccountStatus.INACTIVE) {
            // Check for signs of reactivation
            boolean hasRecentActivity = dormancyAnalysisService.checkForRecentActivity(account.getId(), 30);
            
            if (hasRecentActivity) {
                logger.info("Detected recent activity for dormant account: {}", account.getId());
                
                // Begin reactivation process
                beginReactivationProcess(account, correlationId, traceId);
            } else {
                // Continue monitoring
                scheduleFollowUpMonitoring(account.getId(), DormancyMonitoringType.REACTIVATION_MONITORING, 
                                         7, correlationId);
            }
        }
    }

    private void beginReactivationProcess(Account account, String correlationId, String traceId) {
        logger.info("Beginning reactivation process for account: {}", account.getId());
        
        // Remove dormant restrictions
        removeDormantAccountRestrictions(account, correlationId, traceId);
        
        // Update account status
        account.setStatus(AccountStatus.ACTIVE);
        account.setDormancyLevel(DormancyLevel.ACTIVE);
        account.setStatusChangeDate(LocalDateTime.now());
        account.setLastActivityDate(LocalDateTime.now());
        
        accountService.updateAccount(account);
        
        // Send reactivation notification
        notificationService.sendReactivationNotification(account.getUserId(), account.getId(), correlationId);
        
        auditService.recordAccountReactivation(account.getId(), correlationId);
        
        logger.info("Successfully reactivated account: {}", account.getId());
    }

    private void removeDormantAccountRestrictions(Account account, String correlationId, String traceId) {
        logger.info("Removing dormant account restrictions for account: {}", account.getId());
        
        // Restore transaction limits
        accountLimitsService.restoreNormalLimits(account.getId());
        
        // Re-enable features
        account.setOnlineBankingEnabled(true);
        account.setMobileBankingEnabled(true);
        account.setDebitCardEnabled(true);
        
        auditService.recordDormantRestrictionsRemoved(account.getId(), correlationId);
    }

    private void processAutomatedRestrictions(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                            String correlationId, String traceId) {
        logger.info("Processing automated restrictions for account: {}", account.getId());
        
        if (autoApplyRestrictions && monitoringEvent.getRecommendedActions() != null) {
            executeRecommendedActions(account, monitoringEvent.getRecommendedActions(), correlationId, traceId);
        } else {
            logger.info("Auto-apply restrictions disabled, creating manual review task");
            scheduleManualReview(account.getId(), monitoringEvent.getRecommendedActions(), correlationId);
        }
    }

    private void processBalanceMonitoring(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing balance monitoring for account: {}", account.getId());
        
        BigDecimal currentBalance = monitoringEvent.getCurrentBalance();
        
        if (currentBalance != null) {
            if (currentBalance.compareTo(minBalanceForMonitoring) < 0) {
                logger.info("Account balance below monitoring threshold: {} < {}", 
                           currentBalance, minBalanceForMonitoring);
                
                // Consider for zero-balance closure
                if (currentBalance.compareTo(BigDecimal.ZERO) == 0 && 
                    monitoringEvent.getDaysInactive() > dormantThresholdDays) {
                    scheduleZeroBalanceClosure(account.getId(), correlationId);
                }
            }
            
            // Check for fee assessments
            if (account.getStatus() == AccountStatus.DORMANT) {
                processDormancyFeeAssessment(account, currentBalance, correlationId, traceId);
            }
        }
    }

    private void processDormancyFeeAssessment(Account account, BigDecimal currentBalance, 
                                            String correlationId, String traceId) {
        logger.info("Processing dormancy fee assessment for account: {}", account.getId());
        
        // Check if dormancy fees should be applied
        boolean feeApplicable = complianceService.isDormancyFeeApplicable(account.getId());
        
        if (feeApplicable && currentBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dormancyFee = complianceService.calculateDormancyFee(account.getId(), currentBalance);
            
            if (dormancyFee.compareTo(BigDecimal.ZERO) > 0) {
                // Create fee assessment
                Map<String, Object> feeEvent = new HashMap<>();
                feeEvent.put("accountId", account.getId());
                feeEvent.put("feeType", "DORMANCY_FEE");
                feeEvent.put("feeAmount", dormancyFee.toString());
                feeEvent.put("correlationId", correlationId);
                
                kafkaTemplate.send("fee-assessment", feeEvent);
                
                auditService.recordDormancyFeeAssessment(account.getId(), dormancyFee, correlationId);
                
                logger.info("Assessed dormancy fee for account {}: {}", account.getId(), dormancyFee);
            }
        }
    }

    private void processEscalationReview(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing escalation review for account: {}", account.getId());
        
        if (monitoringEvent.isEscalationRequired()) {
            // Create escalation case
            Map<String, Object> escalationCase = new HashMap<>();
            escalationCase.put("accountId", account.getId());
            escalationCase.put("escalationType", "DORMANCY_REVIEW");
            escalationCase.put("priority", "HIGH");
            escalationCase.put("daysInactive", monitoringEvent.getDaysInactive());
            escalationCase.put("currentBalance", monitoringEvent.getCurrentBalance());
            escalationCase.put("riskFactors", monitoringEvent.getRiskFactors());
            escalationCase.put("correlationId", correlationId);
            
            kafkaTemplate.send("escalation-cases", escalationCase);
            
            auditService.recordDormancyEscalation(account.getId(), escalationCase, correlationId);
            
            logger.info("Created dormancy escalation case for account: {}", account.getId());
        }
    }

    private void processEscheatmentPreparation(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                             String correlationId, String traceId) {
        logger.info("Processing escheatment preparation for account: {}", account.getId());
        
        if (monitoringEvent.getDaysInactive() >= escheatmentThresholdDays) {
            // Begin escheatment preparation process
            complianceService.beginEscheatmentProcess(account.getId(), correlationId);
            
            // Send final notifications
            notificationService.sendEscheatmentFinalNotice(account.getUserId(), account.getId(), 
                                                          monitoringEvent.getDaysInactive(), correlationId);
            
            // Update account status
            account.setStatus(AccountStatus.ESCHEATMENT_PENDING);
            account.setStatusChangeDate(LocalDateTime.now());
            accountService.updateAccount(account);
            
            auditService.recordEscheatmentPreparation(account.getId(), correlationId);
            
            logger.info("Initiated escheatment preparation for account: {}", account.getId());
        }
    }

    private void processComplianceReview(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing compliance review for account: {}", account.getId());
        
        // Perform regulatory compliance checks
        ComplianceReviewResult reviewResult = complianceService.performDormantAccountComplianceReview(
            account.getId(), monitoringEvent);
        
        if (!reviewResult.isCompliant()) {
            logger.warn("Compliance issues found for dormant account: {}", account.getId());
            
            // Create compliance violation case
            complianceService.createComplianceViolation(account.getId(), reviewResult.getViolations(), correlationId);
        }
        
        auditService.recordComplianceReview(account.getId(), reviewResult, correlationId);
    }

    private void processNotificationTrigger(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing notification trigger for account: {}", account.getId());
        
        if (sendReactivationNotifications) {
            String notificationType = determineNotificationType(monitoringEvent.getDaysInactive());
            
            notificationService.sendDormancyNotification(account.getUserId(), account.getId(), 
                                                        monitoringEvent.getDaysInactive(), 
                                                        notificationType, correlationId);
            
            auditService.recordNotificationSent(account.getId(), notificationType, correlationId);
            
            logger.info("Sent dormancy notification to user {} for account {}: {}", 
                       account.getUserId(), account.getId(), notificationType);
        }
    }

    private String determineNotificationType(int daysInactive) {
        if (daysInactive >= escheatmentThresholdDays - 30) {
            return "FINAL_WARNING";
        } else if (daysInactive >= dormantThresholdDays) {
            return "DORMANCY_WARNING";
        } else if (daysInactive >= inactiveThresholdDays) {
            return "INACTIVITY_WARNING";
        } else {
            return "GENERAL_REMINDER";
        }
    }

    private void processActivityVerification(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing activity verification for account: {}", account.getId());
        
        // Verify recent activity
        boolean hasValidActivity = dormancyAnalysisService.verifyRecentActivity(account.getId(), 
                                                                               monitoringEvent.getActivityMetrics());
        
        if (hasValidActivity) {
            logger.info("Valid activity detected for account: {}", account.getId());
            
            // Reset dormancy tracking
            account.setLastActivityDate(LocalDateTime.now());
            account.setDormancyLevel(DormancyLevel.ACTIVE);
            
            if (account.getStatus() == AccountStatus.DORMANT || account.getStatus() == AccountStatus.INACTIVE) {
                beginReactivationProcess(account, correlationId, traceId);
            }
        } else {
            logger.info("No valid activity detected for account: {}", account.getId());
            
            // Continue dormancy monitoring
            scheduleFollowUpMonitoring(account.getId(), DormancyMonitoringType.ACTIVITY_VERIFICATION, 
                                     14, correlationId);
        }
        
        auditService.recordActivityVerification(account.getId(), hasValidActivity, 
                                               monitoringEvent.getActivityMetrics(), correlationId);
    }

    private void processPeriodicReview(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                     String correlationId, String traceId) {
        logger.info("Processing periodic review for account: {}", account.getId());
        
        // Perform comprehensive dormancy review
        DormancyReviewResult reviewResult = dormancyAnalysisService.performPeriodicReview(account, monitoringEvent);
        
        // Update account dormancy information
        account.setDormancyLevel(reviewResult.getDormancyLevel());
        account.setDormancyScore(reviewResult.getDormancyScore());
        account.setLastDormancyAssessment(LocalDateTime.now());
        
        // Execute review recommendations
        if (reviewResult.getRecommendedActions() != null && !reviewResult.getRecommendedActions().isEmpty()) {
            if (autoApplyRestrictions) {
                executeRecommendedActions(account, reviewResult.getRecommendedActions(), correlationId, traceId);
            } else {
                scheduleManualReview(account.getId(), reviewResult.getRecommendedActions(), correlationId);
            }
        }
        
        accountService.updateAccount(account);
        
        auditService.recordPeriodicReview(account.getId(), reviewResult, correlationId);
        
        // Schedule next periodic review
        scheduleFollowUpMonitoring(account.getId(), DormancyMonitoringType.PERIODIC_REVIEW, 90, correlationId);
    }

    private void processStatusTransition(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing status transition for account: {}", account.getId());
        
        AccountStatus previousStatus = monitoringEvent.getPreviousStatus() != null ? 
                                     monitoringEvent.getPreviousStatus() : account.getStatus();
        DormancyLevel newDormancyLevel = monitoringEvent.getDormancyLevel();
        
        if (newDormancyLevel != account.getDormancyLevel()) {
            account.setDormancyLevel(newDormancyLevel);
            
            // Update account status based on dormancy level
            AccountStatus newStatus = mapDormancyLevelToStatus(newDormancyLevel);
            if (newStatus != account.getStatus()) {
                account.setStatus(newStatus);
                account.setStatusChangeDate(LocalDateTime.now());
            }
            
            accountService.updateAccount(account);
            
            auditService.recordStatusTransition(account.getId(), previousStatus, newStatus, 
                                               newDormancyLevel, correlationId);
            
            logger.info("Updated account status transition: {} -> {} (dormancy level: {})", 
                       previousStatus, newStatus, newDormancyLevel);
        }
    }

    private AccountStatus mapDormancyLevelToStatus(DormancyLevel dormancyLevel) {
        switch (dormancyLevel) {
            case ACTIVE:
                return AccountStatus.ACTIVE;
            case INACTIVE:
                return AccountStatus.INACTIVE;
            case DORMANT:
                return AccountStatus.DORMANT;
            case ESCHEATMENT_ELIGIBLE:
                return AccountStatus.ESCHEATMENT_PENDING;
            default:
                return AccountStatus.ACTIVE;
        }
    }

    private void scheduleFollowUpMonitoring(String accountId, DormancyMonitoringType monitoringType, 
                                          int delayDays, String correlationId) {
        logger.info("Scheduling follow-up monitoring for account {}: {} in {} days", 
                   accountId, monitoringType, delayDays);
        
        Map<String, Object> followUpEvent = new HashMap<>();
        followUpEvent.put("accountId", accountId);
        followUpEvent.put("monitoringType", monitoringType.toString());
        followUpEvent.put("scheduledDate", LocalDateTime.now().plusDays(delayDays).toString());
        followUpEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("scheduled-events", followUpEvent);
    }

    private void scheduleManualReview(String accountId, List<DormancyAction> recommendedActions, String correlationId) {
        logger.info("Scheduling manual review for account: {}", accountId);
        
        Map<String, Object> reviewTask = new HashMap<>();
        reviewTask.put("accountId", accountId);
        reviewTask.put("taskType", "DORMANCY_MANUAL_REVIEW");
        reviewTask.put("recommendedActions", recommendedActions);
        reviewTask.put("priority", "MEDIUM");
        reviewTask.put("correlationId", correlationId);
        
        kafkaTemplate.send("manual-review-tasks", reviewTask);
    }

    private void scheduleClosureReview(String accountId, String correlationId) {
        logger.info("Scheduling closure review for account: {}", accountId);
        
        Map<String, Object> closureReview = new HashMap<>();
        closureReview.put("accountId", accountId);
        closureReview.put("reviewType", "DORMANCY_CLOSURE");
        closureReview.put("priority", "HIGH");
        closureReview.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-closure-reviews", closureReview);
    }

    private void scheduleZeroBalanceClosure(String accountId, String correlationId) {
        logger.info("Scheduling zero balance closure for account: {}", accountId);
        
        Map<String, Object> zeroBalanceClosure = new HashMap<>();
        zeroBalanceClosure.put("accountId", accountId);
        zeroBalanceClosure.put("closureType", "ZERO_BALANCE_DORMANT");
        zeroBalanceClosure.put("correlationId", correlationId);
        
        kafkaTemplate.send("account-closure-events", zeroBalanceClosure);
    }

    private void recordDormancyMonitoringAudit(DormantAccountMonitoringEvent monitoringEvent, Account account, 
                                             String correlationId, String traceId) {
        DormantAccountAuditLog auditLog = new DormantAccountAuditLog();
        auditLog.setAccountId(account.getId());
        auditLog.setMonitoringType(monitoringEvent.getMonitoringType());
        auditLog.setDaysInactive(monitoringEvent.getDaysInactive());
        auditLog.setDormancyLevel(monitoringEvent.getDormancyLevel());
        auditLog.setCurrentBalance(monitoringEvent.getCurrentBalance());
        auditLog.setActivityMetrics(monitoringEvent.getActivityMetrics());
        auditLog.setTimestamp(monitoringEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordDormantAccountMonitoringAudit(auditLog);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing dormant account monitoring message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for dormant account monitoring consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public enum DormancyMonitoringType {
        INACTIVITY_CHECK,
        DORMANCY_ASSESSMENT,
        REACTIVATION_MONITORING,
        AUTOMATED_RESTRICTIONS,
        BALANCE_MONITORING,
        ESCALATION_REVIEW,
        ESCHEATMENT_PREPARATION,
        COMPLIANCE_REVIEW,
        NOTIFICATION_TRIGGER,
        ACTIVITY_VERIFICATION,
        PERIODIC_REVIEW,
        STATUS_TRANSITION
    }

    public static class DormantAccountMonitoringEvent {
        private String accountId;
        private DormancyMonitoringType monitoringType;
        private LocalDateTime timestamp;
        private LocalDateTime lastActivityDate;
        private BigDecimal currentBalance;
        private DormancyLevel dormancyLevel;
        private int daysInactive;
        private Map<String, Object> activityMetrics;
        private AccountStatus previousStatus;
        private List<String> riskFactors;
        private List<DormancyAction> recommendedActions;
        private boolean autoExecute;
        private boolean escalationRequired;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public DormancyMonitoringType getMonitoringType() { return monitoringType; }
        public void setMonitoringType(DormancyMonitoringType monitoringType) { this.monitoringType = monitoringType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public LocalDateTime getLastActivityDate() { return lastActivityDate; }
        public void setLastActivityDate(LocalDateTime lastActivityDate) { this.lastActivityDate = lastActivityDate; }

        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

        public DormancyLevel getDormancyLevel() { return dormancyLevel; }
        public void setDormancyLevel(DormancyLevel dormancyLevel) { this.dormancyLevel = dormancyLevel; }

        public int getDaysInactive() { return daysInactive; }
        public void setDaysInactive(int daysInactive) { this.daysInactive = daysInactive; }

        public Map<String, Object> getActivityMetrics() { return activityMetrics; }
        public void setActivityMetrics(Map<String, Object> activityMetrics) { this.activityMetrics = activityMetrics; }

        public AccountStatus getPreviousStatus() { return previousStatus; }
        public void setPreviousStatus(AccountStatus previousStatus) { this.previousStatus = previousStatus; }

        public List<String> getRiskFactors() { return riskFactors; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }

        public List<DormancyAction> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<DormancyAction> recommendedActions) { this.recommendedActions = recommendedActions; }

        public boolean isAutoExecute() { return autoExecute; }
        public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }

        public boolean isEscalationRequired() { return escalationRequired; }
        public void setEscalationRequired(boolean escalationRequired) { this.escalationRequired = escalationRequired; }
    }

    public static class DormancyAssessmentResult {
        private DormancyLevel dormancyLevel;
        private double dormancyScore;
        private List<DormancyAction> recommendedActions;
        private boolean shouldAutoExecute;

        public DormancyLevel getDormancyLevel() { return dormancyLevel; }
        public void setDormancyLevel(DormancyLevel dormancyLevel) { this.dormancyLevel = dormancyLevel; }

        public double getDormancyScore() { return dormancyScore; }
        public void setDormancyScore(double dormancyScore) { this.dormancyScore = dormancyScore; }

        public List<DormancyAction> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<DormancyAction> recommendedActions) { this.recommendedActions = recommendedActions; }

        public boolean shouldAutoExecute() { return shouldAutoExecute; }
        public void setShouldAutoExecute(boolean shouldAutoExecute) { this.shouldAutoExecute = shouldAutoExecute; }
    }

    public static class DormancyReviewResult {
        private DormancyLevel dormancyLevel;
        private double dormancyScore;
        private List<DormancyAction> recommendedActions;

        public DormancyLevel getDormancyLevel() { return dormancyLevel; }
        public void setDormancyLevel(DormancyLevel dormancyLevel) { this.dormancyLevel = dormancyLevel; }

        public double getDormancyScore() { return dormancyScore; }
        public void setDormancyScore(double dormancyScore) { this.dormancyScore = dormancyScore; }

        public List<DormancyAction> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<DormancyAction> recommendedActions) { this.recommendedActions = recommendedActions; }
    }

    public static class ComplianceReviewResult {
        private boolean compliant;
        private List<String> violations;

        public boolean isCompliant() { return compliant; }
        public void setCompliant(boolean compliant) { this.compliant = compliant; }

        public List<String> getViolations() { return violations; }
        public void setViolations(List<String> violations) { this.violations = violations; }
    }
}