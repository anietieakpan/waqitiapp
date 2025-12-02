package com.waqiti.account.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.model.Account;
import com.waqiti.account.model.AccountLimits;
import com.waqiti.account.model.LimitType;
import com.waqiti.account.model.LimitUpdateReason;
import com.waqiti.account.model.LimitUpdateAuditLog;
import com.waqiti.account.service.AccountService;
import com.waqiti.account.service.AccountLimitsService;
import com.waqiti.account.service.RiskAssessmentService;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AccountLimitsUpdatesConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AccountLimitsUpdatesConsumer.class);
    private static final String CONSUMER_NAME = "account-limits-updates-consumer";
    private static final String DLQ_TOPIC = "account-limits-updates-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final AccountLimitsService accountLimitsService;
    private final RiskAssessmentService riskAssessmentService;
    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.account-limits-updates.enabled:true}")
    private boolean consumerEnabled;

    @Value("${kafka.consumer.account-limits-updates.auto-apply-risk-based-limits:true}")
    private boolean autoApplyRiskBasedLimits;

    @Value("${kafka.consumer.account-limits-updates.require-manual-approval:false}")
    private boolean requireManualApproval;

    @Value("${kafka.consumer.account-limits-updates.max-daily-transaction-amount:100000}")
    private BigDecimal maxDailyTransactionAmount;

    @Value("${kafka.consumer.account-limits-updates.max-monthly-transaction-amount:1000000}")
    private BigDecimal maxMonthlyTransactionAmount;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter limitTypeCounters;

    public AccountLimitsUpdatesConsumer(ObjectMapper objectMapper,
                                       AccountService accountService,
                                       AccountLimitsService accountLimitsService,
                                       RiskAssessmentService riskAssessmentService,
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
        this.accountLimitsService = accountLimitsService;
        this.riskAssessmentService = riskAssessmentService;
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
        this.processedCounter = Counter.builder("account_limits_updates_processed_total")
                .description("Total processed account limits updates")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("account_limits_updates_errors_total")
                .description("Total account limits updates processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("account_limits_updates_dlq_total")
                .description("Total account limits updates sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("account_limits_updates_processing_duration")
                .description("Account limits updates processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("AccountLimitsUpdatesConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.account-limits-updates:account-limits-updates}",
        groupId = "${kafka.consumer.group-id:account-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "account-limits-updates-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "account-limits-updates-retry")
    public void processAccountLimitsUpdates(
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
                logger.warn("Account limits updates consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing account limits updates message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidAccountLimitsUpdateMessage(messageNode)) {
                logger.error("Invalid account limits update message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String accountId = messageNode.get("accountId").asText();
            String limitUpdateType = messageNode.get("limitUpdateType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processLimitUpdate(messageNode, accountId, limitUpdateType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed account limits update: messageId={}, accountId={}, limitUpdateType={}, processingTime={}ms",
                    messageId, accountId, limitUpdateType, processingTime);

            processedCounter.increment();
            metricsService.recordAccountLimitsUpdateProcessed(limitUpdateType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse account limits update message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing account limits update: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidAccountLimitsUpdateMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("accountId") && StringUtils.hasText(messageNode.get("accountId").asText()) &&
                   messageNode.has("limitUpdateType") && StringUtils.hasText(messageNode.get("limitUpdateType").asText()) &&
                   messageNode.has("timestamp") &&
                   (messageNode.has("limitChanges") || messageNode.has("riskParameters"));
        } catch (Exception e) {
            logger.error("Error validating account limits update message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processLimitUpdate(JsonNode messageNode, String accountId, String limitUpdateType, 
                                  String correlationId, String traceId) {
        try {
            Account account = accountService.findById(accountId);
            if (account == null) {
                throw new ValidationException("Account not found: " + accountId);
            }

            AccountLimitUpdateEvent updateEvent = parseAccountLimitUpdateEvent(messageNode);
            
            validateLimitUpdate(updateEvent, account);
            
            switch (updateEvent.getLimitUpdateType()) {
                case TRANSACTION_LIMIT_UPDATE:
                    processTransactionLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case DAILY_LIMIT_UPDATE:
                    processDailyLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case MONTHLY_LIMIT_UPDATE:
                    processMonthlyLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case RISK_BASED_LIMIT_ADJUSTMENT:
                    processRiskBasedLimitAdjustment(updateEvent, account, correlationId, traceId);
                    break;
                case COMPLIANCE_LIMIT_UPDATE:
                    processComplianceLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case TEMPORARY_LIMIT_INCREASE:
                    processTemporaryLimitIncrease(updateEvent, account, correlationId, traceId);
                    break;
                case VELOCITY_LIMIT_UPDATE:
                    processVelocityLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case ATM_WITHDRAWAL_LIMIT_UPDATE:
                    processAtmWithdrawalLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case INTERNATIONAL_LIMIT_UPDATE:
                    processInternationalLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case MERCHANT_CATEGORY_LIMIT_UPDATE:
                    processMerchantCategoryLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case OVERDRAFT_LIMIT_UPDATE:
                    processOverdraftLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case CREDIT_LIMIT_UPDATE:
                    processCreditLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case CONTACTLESS_LIMIT_UPDATE:
                    processContactlessLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case SPENDING_LIMIT_UPDATE:
                    processSpendingLimitUpdate(updateEvent, account, correlationId, traceId);
                    break;
                case LIMIT_RESET:
                    processLimitReset(updateEvent, account, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown limit update type: {}", updateEvent.getLimitUpdateType());
                    throw new ValidationException("Unknown limit update type: " + updateEvent.getLimitUpdateType());
            }

            recordLimitUpdateAudit(updateEvent, account, correlationId, traceId);
            
            if (updateEvent.shouldNotifyUser()) {
                sendUserNotification(updateEvent, account, correlationId, traceId);
            }

        } catch (Exception e) {
            logger.error("Error processing limit update for account {}: {}", accountId, e.getMessage(), e);
            throw e;
        }
    }

    private AccountLimitUpdateEvent parseAccountLimitUpdateEvent(JsonNode messageNode) {
        try {
            AccountLimitUpdateEvent event = new AccountLimitUpdateEvent();
            event.setAccountId(messageNode.get("accountId").asText());
            event.setLimitUpdateType(LimitUpdateType.valueOf(messageNode.get("limitUpdateType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("requestedBy")) {
                event.setRequestedBy(messageNode.get("requestedBy").asText());
            }
            
            if (messageNode.has("reason")) {
                event.setReason(LimitUpdateReason.valueOf(messageNode.get("reason").asText()));
            }
            
            if (messageNode.has("limitChanges")) {
                JsonNode limitChangesNode = messageNode.get("limitChanges");
                Map<LimitType, BigDecimal> limitChanges = new HashMap<>();
                
                limitChangesNode.fields().forEachRemaining(entry -> {
                    LimitType limitType = LimitType.valueOf(entry.getKey());
                    BigDecimal amount = new BigDecimal(entry.getValue().asText());
                    limitChanges.put(limitType, amount);
                });
                
                event.setLimitChanges(limitChanges);
            }
            
            if (messageNode.has("effectiveDate")) {
                event.setEffectiveDate(LocalDateTime.parse(messageNode.get("effectiveDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                event.setEffectiveDate(LocalDateTime.now());
            }
            
            if (messageNode.has("expiryDate")) {
                event.setExpiryDate(LocalDateTime.parse(messageNode.get("expiryDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
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
            
            if (messageNode.has("approvalRequired")) {
                event.setApprovalRequired(messageNode.get("approvalRequired").asBoolean());
            }
            
            if (messageNode.has("notifyUser")) {
                event.setNotifyUser(messageNode.get("notifyUser").asBoolean(true));
            } else {
                event.setNotifyUser(true);
            }

            return event;
        } catch (Exception e) {
            logger.error("Error parsing account limit update event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid account limit update event format: " + e.getMessage());
        }
    }

    private void validateLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account) {
        if (updateEvent.getLimitChanges() != null) {
            for (Map.Entry<LimitType, BigDecimal> entry : updateEvent.getLimitChanges().entrySet()) {
                BigDecimal newLimit = entry.getValue();
                
                if (newLimit.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Limit cannot be negative: " + entry.getKey());
                }
                
                if (newLimit.compareTo(maxDailyTransactionAmount) > 0 && 
                    (entry.getKey() == LimitType.DAILY_TRANSACTION_LIMIT || entry.getKey() == LimitType.DAILY_SPENDING_LIMIT)) {
                    throw new ValidationException("Daily limit exceeds maximum allowed: " + maxDailyTransactionAmount);
                }
                
                if (newLimit.compareTo(maxMonthlyTransactionAmount) > 0 && 
                    (entry.getKey() == LimitType.MONTHLY_TRANSACTION_LIMIT || entry.getKey() == LimitType.MONTHLY_SPENDING_LIMIT)) {
                    throw new ValidationException("Monthly limit exceeds maximum allowed: " + maxMonthlyTransactionAmount);
                }
            }
        }
        
        if (!account.isActive()) {
            throw new ValidationException("Cannot update limits for inactive account: " + account.getId());
        }
        
        if (updateEvent.isApprovalRequired() && requireManualApproval) {
            throw new ValidationException("Manual approval required for this limit update");
        }
        
        if (updateEvent.getComplianceFlags() != null && !updateEvent.getComplianceFlags().isEmpty()) {
            complianceService.validateLimitUpdateCompliance(updateEvent, account);
        }
    }

    private void processTransactionLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                             String correlationId, String traceId) {
        logger.info("Processing transaction limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.TRANSACTION_LIMIT);
            newLimits.setTransactionLimit(newLimit);
            
            logger.info("Updated transaction limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getTransactionLimit(), newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.SINGLE_TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.SINGLE_TRANSACTION_LIMIT);
            newLimits.setSingleTransactionLimit(newLimit);
            
            logger.info("Updated single transaction limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getSingleTransactionLimit(), newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordLimitUpdate(account.getId(), LimitType.TRANSACTION_LIMIT, 
                                     currentLimits.getTransactionLimit(), newLimits.getTransactionLimit(),
                                     updateEvent.getReason(), updateEvent.getRequestedBy(), correlationId);
    }

    private void processDailyLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                       String correlationId, String traceId) {
        logger.info("Processing daily limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_TRANSACTION_LIMIT);
            newLimits.setDailyTransactionLimit(newLimit);
            
            logger.info("Updated daily transaction limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getDailyTransactionLimit(), newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_SPENDING_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_SPENDING_LIMIT);
            newLimits.setDailySpendingLimit(newLimit);
            
            logger.info("Updated daily spending limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getDailySpendingLimit(), newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_ATM_WITHDRAWAL_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_ATM_WITHDRAWAL_LIMIT);
            newLimits.setDailyAtmWithdrawalLimit(newLimit);
            
            logger.info("Updated daily ATM withdrawal limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getDailyAtmWithdrawalLimit(), newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordLimitUpdate(account.getId(), LimitType.DAILY_TRANSACTION_LIMIT, 
                                     currentLimits.getDailyTransactionLimit(), newLimits.getDailyTransactionLimit(),
                                     updateEvent.getReason(), updateEvent.getRequestedBy(), correlationId);
    }

    private void processMonthlyLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                         String correlationId, String traceId) {
        logger.info("Processing monthly limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.MONTHLY_TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.MONTHLY_TRANSACTION_LIMIT);
            newLimits.setMonthlyTransactionLimit(newLimit);
            
            logger.info("Updated monthly transaction limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getMonthlyTransactionLimit(), newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.MONTHLY_SPENDING_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.MONTHLY_SPENDING_LIMIT);
            newLimits.setMonthlySpendingLimit(newLimit);
            
            logger.info("Updated monthly spending limit for account {}: {} -> {}", 
                       account.getId(), currentLimits.getMonthlySpendingLimit(), newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordLimitUpdate(account.getId(), LimitType.MONTHLY_TRANSACTION_LIMIT, 
                                     currentLimits.getMonthlyTransactionLimit(), newLimits.getMonthlyTransactionLimit(),
                                     updateEvent.getReason(), updateEvent.getRequestedBy(), correlationId);
    }

    private void processRiskBasedLimitAdjustment(AccountLimitUpdateEvent updateEvent, Account account, 
                                               String correlationId, String traceId) {
        logger.info("Processing risk-based limit adjustment for account: {}", account.getId());
        
        if (!autoApplyRiskBasedLimits) {
            logger.info("Auto-apply risk-based limits is disabled, skipping adjustment for account: {}", account.getId());
            return;
        }
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        double riskScore = updateEvent.getRiskScore() != null ? updateEvent.getRiskScore() : 
                          riskAssessmentService.calculateAccountRiskScore(account.getId());
        
        BigDecimal riskAdjustmentFactor = calculateRiskAdjustmentFactor(riskScore);
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.RISK_ADJUSTED_DAILY_LIMIT)) {
            BigDecimal baseLimit = currentLimits.getDailyTransactionLimit();
            BigDecimal adjustedLimit = baseLimit.multiply(riskAdjustmentFactor);
            newLimits.setDailyTransactionLimit(adjustedLimit);
            
            logger.info("Risk-adjusted daily limit for account {}: {} -> {} (risk score: {}, factor: {})", 
                       account.getId(), baseLimit, adjustedLimit, riskScore, riskAdjustmentFactor);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.RISK_ADJUSTED_TRANSACTION_LIMIT)) {
            BigDecimal baseLimit = currentLimits.getTransactionLimit();
            BigDecimal adjustedLimit = baseLimit.multiply(riskAdjustmentFactor);
            newLimits.setTransactionLimit(adjustedLimit);
            
            logger.info("Risk-adjusted transaction limit for account {}: {} -> {} (risk score: {}, factor: {})", 
                       account.getId(), baseLimit, adjustedLimit, riskScore, riskAdjustmentFactor);
        }
        
        newLimits.setRiskScore(riskScore);
        newLimits.setLastRiskAssessment(LocalDateTime.now());
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy("RISK_ENGINE");
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordRiskBasedLimitAdjustment(account.getId(), riskScore, riskAdjustmentFactor, 
                                                  currentLimits, newLimits, correlationId);
    }

    private BigDecimal calculateRiskAdjustmentFactor(double riskScore) {
        if (riskScore >= 0.8) {
            return new BigDecimal("0.3"); // High risk: 30% of normal limits
        } else if (riskScore >= 0.6) {
            return new BigDecimal("0.5"); // Medium-high risk: 50% of normal limits
        } else if (riskScore >= 0.4) {
            return new BigDecimal("0.7"); // Medium risk: 70% of normal limits
        } else if (riskScore >= 0.2) {
            return new BigDecimal("0.9"); // Low-medium risk: 90% of normal limits
        } else {
            return new BigDecimal("1.0"); // Low risk: 100% of normal limits
        }
    }

    private void processComplianceLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                            String correlationId, String traceId) {
        logger.info("Processing compliance limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        // Apply compliance-driven limit changes
        for (Map.Entry<LimitType, BigDecimal> entry : updateEvent.getLimitChanges().entrySet()) {
            LimitType limitType = entry.getKey();
            BigDecimal newLimit = entry.getValue();
            
            switch (limitType) {
                case AML_DAILY_LIMIT:
                    newLimits.setAmlDailyLimit(newLimit);
                    break;
                case KYC_TRANSACTION_LIMIT:
                    newLimits.setKycTransactionLimit(newLimit);
                    break;
                case SANCTIONS_LIMIT:
                    newLimits.setSanctionsLimit(newLimit);
                    break;
                case CTR_REPORTING_LIMIT:
                    newLimits.setCtrReportingLimit(newLimit);
                    break;
                default:
                    logger.warn("Unknown compliance limit type: {}", limitType);
            }
        }
        
        newLimits.setComplianceFlags(updateEvent.getComplianceFlags());
        newLimits.setLastComplianceUpdate(LocalDateTime.now());
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordComplianceLimitUpdate(account.getId(), updateEvent.getComplianceFlags(), 
                                                currentLimits, newLimits, updateEvent.getReason(), 
                                                updateEvent.getRequestedBy(), correlationId);
    }

    private void processTemporaryLimitIncrease(AccountLimitUpdateEvent updateEvent, Account account, 
                                             String correlationId, String traceId) {
        logger.info("Processing temporary limit increase for account: {}", account.getId());
        
        if (updateEvent.getExpiryDate() == null) {
            throw new ValidationException("Expiry date is required for temporary limit increases");
        }
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        for (Map.Entry<LimitType, BigDecimal> entry : updateEvent.getLimitChanges().entrySet()) {
            LimitType limitType = entry.getKey();
            BigDecimal newLimit = entry.getValue();
            
            switch (limitType) {
                case TEMPORARY_DAILY_INCREASE:
                    newLimits.setTemporaryDailyIncrease(newLimit);
                    newLimits.setTemporaryIncreaseExpiry(updateEvent.getExpiryDate());
                    break;
                case TEMPORARY_TRANSACTION_INCREASE:
                    newLimits.setTemporaryTransactionIncrease(newLimit);
                    newLimits.setTemporaryIncreaseExpiry(updateEvent.getExpiryDate());
                    break;
                default:
                    logger.warn("Unsupported temporary limit type: {}", limitType);
            }
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        // Schedule automatic expiry
        scheduleTemporaryLimitExpiry(account.getId(), updateEvent.getExpiryDate(), correlationId);
        
        auditService.recordTemporaryLimitIncrease(account.getId(), updateEvent.getLimitChanges(), 
                                                updateEvent.getExpiryDate(), updateEvent.getRequestedBy(), correlationId);
    }

    private void processVelocityLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing velocity limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.VELOCITY_TRANSACTION_COUNT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.VELOCITY_TRANSACTION_COUNT);
            newLimits.setVelocityTransactionCount(newLimit.intValue());
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.VELOCITY_TIME_WINDOW)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.VELOCITY_TIME_WINDOW);
            newLimits.setVelocityTimeWindow(newLimit.intValue());
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.VELOCITY_AMOUNT_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.VELOCITY_AMOUNT_LIMIT);
            newLimits.setVelocityAmountLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordVelocityLimitUpdate(account.getId(), currentLimits, newLimits, 
                                             updateEvent.getRequestedBy(), correlationId);
    }

    private void processAtmWithdrawalLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                               String correlationId, String traceId) {
        logger.info("Processing ATM withdrawal limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_ATM_WITHDRAWAL_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_ATM_WITHDRAWAL_LIMIT);
            newLimits.setDailyAtmWithdrawalLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.SINGLE_ATM_WITHDRAWAL_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.SINGLE_ATM_WITHDRAWAL_LIMIT);
            newLimits.setSingleAtmWithdrawalLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.ATM_WITHDRAWAL_COUNT_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.ATM_WITHDRAWAL_COUNT_LIMIT);
            newLimits.setAtmWithdrawalCountLimit(newLimit.intValue());
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordAtmLimitUpdate(account.getId(), currentLimits, newLimits, 
                                        updateEvent.getRequestedBy(), correlationId);
    }

    private void processInternationalLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                               String correlationId, String traceId) {
        logger.info("Processing international limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.INTERNATIONAL_TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.INTERNATIONAL_TRANSACTION_LIMIT);
            newLimits.setInternationalTransactionLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.INTERNATIONAL_DAILY_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.INTERNATIONAL_DAILY_LIMIT);
            newLimits.setInternationalDailyLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.INTERNATIONAL_MONTHLY_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.INTERNATIONAL_MONTHLY_LIMIT);
            newLimits.setInternationalMonthlyLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordInternationalLimitUpdate(account.getId(), currentLimits, newLimits, 
                                                  updateEvent.getRequestedBy(), correlationId);
    }

    private void processMerchantCategoryLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                                  String correlationId, String traceId) {
        logger.info("Processing merchant category limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        for (Map.Entry<LimitType, BigDecimal> entry : updateEvent.getLimitChanges().entrySet()) {
            LimitType limitType = entry.getKey();
            BigDecimal newLimit = entry.getValue();
            
            if (limitType.name().startsWith("MCC_")) {
                String merchantCategory = limitType.name().substring(4);
                newLimits.setMerchantCategoryLimit(merchantCategory, newLimit);
            }
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordMerchantCategoryLimitUpdate(account.getId(), updateEvent.getLimitChanges(), 
                                                     updateEvent.getRequestedBy(), correlationId);
    }

    private void processOverdraftLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                           String correlationId, String traceId) {
        logger.info("Processing overdraft limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.OVERDRAFT_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.OVERDRAFT_LIMIT);
            newLimits.setOverdraftLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_OVERDRAFT_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_OVERDRAFT_LIMIT);
            newLimits.setDailyOverdraftLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordOverdraftLimitUpdate(account.getId(), currentLimits, newLimits, 
                                               updateEvent.getRequestedBy(), correlationId);
    }

    private void processCreditLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                        String correlationId, String traceId) {
        logger.info("Processing credit limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.CREDIT_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.CREDIT_LIMIT);
            newLimits.setCreditLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.CASH_ADVANCE_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.CASH_ADVANCE_LIMIT);
            newLimits.setCashAdvanceLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordCreditLimitUpdate(account.getId(), currentLimits, newLimits, 
                                           updateEvent.getRequestedBy(), correlationId);
    }

    private void processContactlessLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                             String correlationId, String traceId) {
        logger.info("Processing contactless limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.CONTACTLESS_TRANSACTION_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.CONTACTLESS_TRANSACTION_LIMIT);
            newLimits.setContactlessTransactionLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.CONTACTLESS_DAILY_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.CONTACTLESS_DAILY_LIMIT);
            newLimits.setContactlessDailyLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordContactlessLimitUpdate(account.getId(), currentLimits, newLimits, 
                                                 updateEvent.getRequestedBy(), correlationId);
    }

    private void processSpendingLimitUpdate(AccountLimitUpdateEvent updateEvent, Account account, 
                                          String correlationId, String traceId) {
        logger.info("Processing spending limit update for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits newLimits = currentLimits.copy();
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.DAILY_SPENDING_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.DAILY_SPENDING_LIMIT);
            newLimits.setDailySpendingLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.WEEKLY_SPENDING_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.WEEKLY_SPENDING_LIMIT);
            newLimits.setWeeklySpendingLimit(newLimit);
        }
        
        if (updateEvent.getLimitChanges().containsKey(LimitType.MONTHLY_SPENDING_LIMIT)) {
            BigDecimal newLimit = updateEvent.getLimitChanges().get(LimitType.MONTHLY_SPENDING_LIMIT);
            newLimits.setMonthlySpendingLimit(newLimit);
        }
        
        newLimits.setLastUpdated(updateEvent.getTimestamp());
        newLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), newLimits);
        
        auditService.recordSpendingLimitUpdate(account.getId(), currentLimits, newLimits, 
                                             updateEvent.getRequestedBy(), correlationId);
    }

    private void processLimitReset(AccountLimitUpdateEvent updateEvent, Account account, 
                                 String correlationId, String traceId) {
        logger.info("Processing limit reset for account: {}", account.getId());
        
        AccountLimits currentLimits = accountLimitsService.getAccountLimits(account.getId());
        AccountLimits defaultLimits = accountLimitsService.getDefaultLimitsForAccount(account);
        
        defaultLimits.setLastUpdated(updateEvent.getTimestamp());
        defaultLimits.setUpdatedBy(updateEvent.getRequestedBy());
        
        accountLimitsService.updateAccountLimits(account.getId(), defaultLimits);
        
        auditService.recordLimitReset(account.getId(), currentLimits, defaultLimits, 
                                    updateEvent.getRequestedBy(), correlationId);
    }

    private void scheduleTemporaryLimitExpiry(String accountId, LocalDateTime expiryDate, String correlationId) {
        // This would typically schedule a job to automatically revert temporary limits
        logger.info("Scheduling temporary limit expiry for account {} at {}", accountId, expiryDate);
        
        Map<String, Object> expiryEvent = new HashMap<>();
        expiryEvent.put("accountId", accountId);
        expiryEvent.put("expiryDate", expiryDate.toString());
        expiryEvent.put("correlationId", correlationId);
        expiryEvent.put("eventType", "TEMPORARY_LIMIT_EXPIRY");
        
        kafkaTemplate.send("scheduled-events", expiryEvent);
    }

    private void recordLimitUpdateAudit(AccountLimitUpdateEvent updateEvent, Account account, 
                                      String correlationId, String traceId) {
        LimitUpdateAuditLog auditLog = new LimitUpdateAuditLog();
        auditLog.setAccountId(account.getId());
        auditLog.setLimitUpdateType(updateEvent.getLimitUpdateType());
        auditLog.setLimitChanges(updateEvent.getLimitChanges());
        auditLog.setReason(updateEvent.getReason());
        auditLog.setRequestedBy(updateEvent.getRequestedBy());
        auditLog.setTimestamp(updateEvent.getTimestamp());
        auditLog.setEffectiveDate(updateEvent.getEffectiveDate());
        auditLog.setExpiryDate(updateEvent.getExpiryDate());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordLimitUpdateAudit(auditLog);
    }

    private void sendUserNotification(AccountLimitUpdateEvent updateEvent, Account account, 
                                    String correlationId, String traceId) {
        try {
            String notificationMessage = buildLimitUpdateNotificationMessage(updateEvent, account);
            
            notificationService.sendLimitUpdateNotification(
                account.getUserId(), 
                account.getId(), 
                updateEvent.getLimitUpdateType().toString(),
                notificationMessage,
                correlationId
            );
            
            logger.info("Sent limit update notification to user {} for account {}", 
                       account.getUserId(), account.getId());
        } catch (Exception e) {
            logger.error("Failed to send limit update notification: {}", e.getMessage(), e);
        }
    }

    private String buildLimitUpdateNotificationMessage(AccountLimitUpdateEvent updateEvent, Account account) {
        StringBuilder message = new StringBuilder();
        message.append("Your account limits have been updated.\n\n");
        
        switch (updateEvent.getLimitUpdateType()) {
            case TRANSACTION_LIMIT_UPDATE:
                message.append("Transaction limits have been modified based on your account activity.");
                break;
            case DAILY_LIMIT_UPDATE:
                message.append("Daily transaction limits have been updated.");
                break;
            case MONTHLY_LIMIT_UPDATE:
                message.append("Monthly transaction limits have been updated.");
                break;
            case RISK_BASED_LIMIT_ADJUSTMENT:
                message.append("Limits have been adjusted based on risk assessment.");
                break;
            case COMPLIANCE_LIMIT_UPDATE:
                message.append("Limits have been updated for compliance requirements.");
                break;
            case TEMPORARY_LIMIT_INCREASE:
                message.append("Temporary limit increase has been applied to your account.");
                if (updateEvent.getExpiryDate() != null) {
                    message.append(" This increase will expire on ")
                           .append(updateEvent.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
                break;
            default:
                message.append("Account limits have been updated.");
        }
        
        message.append("\n\nIf you have questions about these changes, please contact customer support.");
        
        return message.toString();
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing account limits update message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for account limits updates consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public static class AccountLimitUpdateEvent {
        private String accountId;
        private LimitUpdateType limitUpdateType;
        private LocalDateTime timestamp;
        private String requestedBy;
        private LimitUpdateReason reason;
        private Map<LimitType, BigDecimal> limitChanges;
        private LocalDateTime effectiveDate;
        private LocalDateTime expiryDate;
        private Double riskScore;
        private List<String> complianceFlags;
        private boolean approvalRequired;
        private boolean notifyUser;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public LimitUpdateType getLimitUpdateType() { return limitUpdateType; }
        public void setLimitUpdateType(LimitUpdateType limitUpdateType) { this.limitUpdateType = limitUpdateType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

        public LimitUpdateReason getReason() { return reason; }
        public void setReason(LimitUpdateReason reason) { this.reason = reason; }

        public Map<LimitType, BigDecimal> getLimitChanges() { return limitChanges; }
        public void setLimitChanges(Map<LimitType, BigDecimal> limitChanges) { this.limitChanges = limitChanges; }

        public LocalDateTime getEffectiveDate() { return effectiveDate; }
        public void setEffectiveDate(LocalDateTime effectiveDate) { this.effectiveDate = effectiveDate; }

        public LocalDateTime getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getComplianceFlags() { return complianceFlags; }
        public void setComplianceFlags(List<String> complianceFlags) { this.complianceFlags = complianceFlags; }

        public boolean isApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }

        public boolean shouldNotifyUser() { return notifyUser; }
        public void setNotifyUser(boolean notifyUser) { this.notifyUser = notifyUser; }
    }
}