package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.crypto.model.*;
import com.waqiti.crypto.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;
import com.waqiti.common.security.SecurityContextHolder;
import com.waqiti.common.security.hsm.HsmService;

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DigitalAssetCustodyEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DigitalAssetCustodyEventConsumer.class);
    
    private static final String TOPIC = "waqiti.crypto.digital-asset-custody";
    private static final String CONSUMER_GROUP = "digital-asset-custody-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.crypto.digital-asset-custody.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final DigitalAssetCustodyService custodyService;
    private final DigitalAssetSecurityService securityService;
    private final DigitalAssetVaultService vaultService;
    private final DigitalAssetComplianceService complianceService;
    private final DigitalAssetAuditService auditService;
    private final DigitalAssetInsuranceService insuranceService;
    private final DigitalAssetNotificationService notificationService;
    private final HsmService hsmService;
    private final SecurityContextHolder securityContextHolder;
    
    @Value("${crypto.custody.max-single-deposit:1000000.00}")
    private BigDecimal maxSingleDepositAmount;
    
    @Value("${crypto.custody.min-withdrawal-approval:10000.00}")
    private BigDecimal minWithdrawalApprovalAmount;
    
    @Value("${crypto.custody.insurance-coverage:100000000.00}")
    private BigDecimal insuranceCoverageAmount;
    
    @Value("${crypto.custody.cold-storage-threshold:50000.00}")
    private BigDecimal coldStorageThreshold;
    
    @Value("${crypto.custody.multi-sig-threshold:25000.00}")
    private BigDecimal multiSigThreshold;
    
    @Value("${crypto.custody.rate-limit.global:500}")
    private int globalRateLimit;
    
    @Value("${crypto.custody.batch-size:20}")
    private int batchSize;
    
    @Value("${crypto.custody.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${crypto.custody.circuit-breaker.failure-rate:50}")
    private float circuitBreakerFailureRate;
    
    @Value("${crypto.custody.circuit-breaker.wait-duration:60}")
    private long circuitBreakerWaitDuration;
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(6);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(8);
    private final ExecutorService securityExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService vaultExecutor = Executors.newFixedThreadPool(2);
    
    private CircuitBreaker circuitBreaker;
    private Retry retryConfig;
    
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter assetsDepositedCounter;
    private Counter assetsWithdrawnCounter;
    private Counter assetsTransferredCounter;
    private Counter securityEventsCounter;
    private Counter complianceViolationsCounter;
    private Counter insuranceClaimsCounter;
    private Counter coldStorageMovesCounter;
    private Counter hotWalletActivitiesCounter;
    private Counter multiSigTransactionsCounter;
    private Counter auditEventsCounter;
    private Counter unauthorizedAccessCounter;
    
    private Timer messageProcessingTimer;
    private Timer depositProcessingTimer;
    private Timer withdrawalProcessingTimer;
    private Timer transferProcessingTimer;
    private Timer securityValidationTimer;
    private Timer complianceCheckTimer;
    private Timer auditProcessingTimer;
    private Timer vaultOperationTimer;
    
    private final AtomicLong totalAssetsUnderCustody = new AtomicLong(0);
    private final AtomicLong coldStorageAssets = new AtomicLong(0);
    private final AtomicLong hotWalletAssets = new AtomicLong(0);
    private final AtomicLong activeVaults = new AtomicLong(0);
    private final AtomicLong securityIncidents = new AtomicLong(0);
    private final AtomicInteger currentGlobalRate = new AtomicInteger(0);
    
    private final ConcurrentHashMap<String, DigitalAssetCustodyAccount> custodyAccounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DigitalAssetVault> vaults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DigitalAssetSecurityKey> securityKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DigitalAssetCompliance> complianceStates = new ConcurrentHashMap<>();
    private final BlockingQueue<DigitalAssetCustodyOperation> operationQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<DigitalAssetSecurityEvent> securityEventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<DigitalAssetAuditEvent> auditEventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<DigitalAssetCustodyBatch> batchQueue = new LinkedBlockingQueue<>();
    private final PriorityBlockingQueue<DigitalAssetCustodyOperation> priorityQueue = 
        new PriorityBlockingQueue<>(500, Comparator.comparing(DigitalAssetCustodyOperation::getPriorityScore).reversed());
    
    public DigitalAssetCustodyEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            DigitalAssetCustodyService custodyService,
            DigitalAssetSecurityService securityService,
            DigitalAssetVaultService vaultService,
            DigitalAssetComplianceService complianceService,
            DigitalAssetAuditService auditService,
            DigitalAssetInsuranceService insuranceService,
            DigitalAssetNotificationService notificationService,
            HsmService hsmService,
            SecurityContextHolder securityContextHolder) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.custodyService = custodyService;
        this.securityService = securityService;
        this.vaultService = vaultService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.insuranceService = insuranceService;
        this.notificationService = notificationService;
        this.hsmService = hsmService;
        this.securityContextHolder = securityContextHolder;
    }
    
    @PostConstruct
    public void init() {
        initializeCircuitBreaker();
        initializeRetry();
        initializeMetrics();
        startOperationProcessor();
        startBatchProcessor();
        startSecurityEventProcessor();
        startAuditEventProcessor();
        startVaultMonitor();
        startRateLimitReset();
        logger.info("DigitalAssetCustodyEventConsumer initialized successfully");
    }
    
    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        securityExecutor.shutdown();
        vaultExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
            if (!securityExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                securityExecutor.shutdownNow();
            }
            if (!vaultExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                vaultExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            securityExecutor.shutdownNow();
            vaultExecutor.shutdownNow();
        }
        logger.info("DigitalAssetCustodyEventConsumer cleanup completed");
    }
    
    private void initializeCircuitBreaker() {
        circuitBreaker = CircuitBreaker.of("digital-asset-custody-circuit-breaker",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreakerFailureRate)
                .waitDurationInOpenState(Duration.ofSeconds(circuitBreakerWaitDuration))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build());
    }
    
    private void initializeRetry() {
        retryConfig = Retry.of("digital-asset-custody-retry",
            RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(2000))
                .exponentialBackoffMultiplier(2.0)
                .retryOnException(throwable -> !(throwable instanceof SecurityException))
                .build());
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("digital_asset_custody_messages_processed_total")
            .description("Total digital asset custody messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("digital_asset_custody_messages_failed_total")
            .description("Total digital asset custody messages failed")
            .register(meterRegistry);
            
        assetsDepositedCounter = Counter.builder("digital_asset_custody_deposits_total")
            .description("Total digital asset deposits")
            .register(meterRegistry);
            
        assetsWithdrawnCounter = Counter.builder("digital_asset_custody_withdrawals_total")
            .description("Total digital asset withdrawals")
            .register(meterRegistry);
            
        assetsTransferredCounter = Counter.builder("digital_asset_custody_transfers_total")
            .description("Total digital asset transfers")
            .register(meterRegistry);
            
        securityEventsCounter = Counter.builder("digital_asset_custody_security_events_total")
            .description("Total digital asset custody security events")
            .register(meterRegistry);
            
        complianceViolationsCounter = Counter.builder("digital_asset_custody_compliance_violations_total")
            .description("Total digital asset custody compliance violations")
            .register(meterRegistry);
            
        insuranceClaimsCounter = Counter.builder("digital_asset_custody_insurance_claims_total")
            .description("Total digital asset custody insurance claims")
            .register(meterRegistry);
            
        coldStorageMovesCounter = Counter.builder("digital_asset_custody_cold_storage_moves_total")
            .description("Total digital asset cold storage moves")
            .register(meterRegistry);
            
        hotWalletActivitiesCounter = Counter.builder("digital_asset_custody_hot_wallet_activities_total")
            .description("Total digital asset hot wallet activities")
            .register(meterRegistry);
            
        multiSigTransactionsCounter = Counter.builder("digital_asset_custody_multisig_transactions_total")
            .description("Total digital asset multi-signature transactions")
            .register(meterRegistry);
            
        auditEventsCounter = Counter.builder("digital_asset_custody_audit_events_total")
            .description("Total digital asset custody audit events")
            .register(meterRegistry);
            
        unauthorizedAccessCounter = Counter.builder("digital_asset_custody_unauthorized_access_total")
            .description("Total unauthorized access attempts")
            .register(meterRegistry);
        
        messageProcessingTimer = Timer.builder("digital_asset_custody_message_processing_duration")
            .description("Digital asset custody message processing duration")
            .register(meterRegistry);
            
        depositProcessingTimer = Timer.builder("digital_asset_custody_deposit_processing_duration")
            .description("Digital asset custody deposit processing duration")
            .register(meterRegistry);
            
        withdrawalProcessingTimer = Timer.builder("digital_asset_custody_withdrawal_processing_duration")
            .description("Digital asset custody withdrawal processing duration")
            .register(meterRegistry);
            
        transferProcessingTimer = Timer.builder("digital_asset_custody_transfer_processing_duration")
            .description("Digital asset custody transfer processing duration")
            .register(meterRegistry);
            
        securityValidationTimer = Timer.builder("digital_asset_custody_security_validation_duration")
            .description("Digital asset custody security validation duration")
            .register(meterRegistry);
            
        complianceCheckTimer = Timer.builder("digital_asset_custody_compliance_check_duration")
            .description("Digital asset custody compliance check duration")
            .register(meterRegistry);
            
        auditProcessingTimer = Timer.builder("digital_asset_custody_audit_processing_duration")
            .description("Digital asset custody audit processing duration")
            .register(meterRegistry);
            
        vaultOperationTimer = Timer.builder("digital_asset_custody_vault_operation_duration")
            .description("Digital asset custody vault operation duration")
            .register(meterRegistry);
        
        Gauge.builder("digital_asset_custody_total_assets")
            .description("Total assets under custody")
            .register(meterRegistry, this, value -> totalAssetsUnderCustody.get());
            
        Gauge.builder("digital_asset_custody_cold_storage_assets")
            .description("Assets in cold storage")
            .register(meterRegistry, this, value -> coldStorageAssets.get());
            
        Gauge.builder("digital_asset_custody_hot_wallet_assets")
            .description("Assets in hot wallets")
            .register(meterRegistry, this, value -> hotWalletAssets.get());
            
        Gauge.builder("digital_asset_custody_active_vaults")
            .description("Number of active vaults")
            .register(meterRegistry, this, value -> activeVaults.get());
            
        Gauge.builder("digital_asset_custody_security_incidents")
            .description("Number of security incidents")
            .register(meterRegistry, this, value -> securityIncidents.get());
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processDigitalAssetCustody(@Payload String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                        Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            
            logger.info("Processing digital asset custody message: topic={}, partition={}, offset={}", 
                topic, partition, offset);
            
            if (!isWithinRateLimit()) {
                logger.warn("Rate limit exceeded, requeueing message");
                dlqService.sendToDlq(DLQ_TOPIC, message, "Rate limit exceeded", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = circuitBreaker.executeSupplier(() ->
                retryConfig.executeSupplier(() -> {
                    return executeSecurityValidatedProcessing(eventType, messageNode, requestId);
                })
            );
            
            if (processed) {
                messagesProcessedCounter.increment();
                metricsService.recordCustomMetric("digital_asset_custody_processed", 1.0, 
                    Map.of("eventType", eventType, "requestId", requestId));
                acknowledgment.acknowledge();
                logger.info("Successfully processed digital asset custody message: eventType={}, requestId={}", 
                    eventType, requestId);
            } else {
                throw new RuntimeException("Failed to process digital asset custody message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing digital asset custody message", e);
            messagesFailedCounter.increment();
            
            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("Failed to send message to DLQ", dlqException);
            }
        } finally {
            sample.stop(messageProcessingTimer);
            MDC.clear();
        }
    }
    
    private boolean executeSecurityValidatedProcessing(String eventType, JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            boolean securityValidated = securityService.validateOperationSecurity(messageNode, requestId);
            if (!securityValidated) {
                securityIncidents.incrementAndGet();
                unauthorizedAccessCounter.increment();
                logger.error("Security validation failed for custody operation: eventType={}, requestId={}", 
                    eventType, requestId);
                return false;
            }
            
            return executeProcessingStep(eventType, messageNode, requestId);
            
        } finally {
            sample.stop(securityValidationTimer);
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "ASSET_DEPOSIT_REQUESTED":
                return processAssetDeposit(messageNode, requestId);
            case "ASSET_WITHDRAWAL_REQUESTED":
                return processAssetWithdrawal(messageNode, requestId);
            case "ASSET_TRANSFER_REQUESTED":
                return processAssetTransfer(messageNode, requestId);
            case "VAULT_CREATION_REQUESTED":
                return processVaultCreation(messageNode, requestId);
            case "VAULT_ACCESS_REQUESTED":
                return processVaultAccess(messageNode, requestId);
            case "COLD_STORAGE_MOVE_REQUESTED":
                return processColdStorageMove(messageNode, requestId);
            case "HOT_WALLET_ACTIVITY":
                return processHotWalletActivity(messageNode, requestId);
            case "MULTI_SIGNATURE_TRANSACTION":
                return processMultiSignatureTransaction(messageNode, requestId);
            case "SECURITY_KEY_ROTATION":
                return processSecurityKeyRotation(messageNode, requestId);
            case "COMPLIANCE_CHECK_REQUESTED":
                return processComplianceCheck(messageNode, requestId);
            case "INSURANCE_CLAIM_FILED":
                return processInsuranceClaim(messageNode, requestId);
            case "AUDIT_TRAIL_GENERATION":
                return processAuditTrailGeneration(messageNode, requestId);
            case "SUSPICIOUS_ACTIVITY_DETECTED":
                return processSuspiciousActivityDetected(messageNode, requestId);
            case "CUSTODY_ACCOUNT_SETUP":
                return processCustodyAccountSetup(messageNode, requestId);
            case "ASSET_VALUATION_UPDATE":
                return processAssetValuationUpdate(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processAssetDeposit(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String customerId = messageNode.path("customerId").asText();
            String assetType = messageNode.path("assetType").asText();
            String depositAmount = messageNode.path("depositAmount").asText();
            String depositAddress = messageNode.path("depositAddress").asText();
            String transactionHash = messageNode.path("transactionHash").asText();
            String depositSource = messageNode.path("depositSource").asText();
            
            BigDecimal amount = new BigDecimal(depositAmount);
            
            if (amount.compareTo(maxSingleDepositAmount) > 0) {
                logger.warn("Deposit amount exceeds maximum limit: {} > {}", amount, maxSingleDepositAmount);
                return false;
            }
            
            DigitalAssetCustodyOperation operation = DigitalAssetCustodyOperation.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .operationType("DEPOSIT")
                .assetType(assetType)
                .amount(amount)
                .depositAddress(depositAddress)
                .transactionHash(transactionHash)
                .source(depositSource)
                .status("PENDING_VERIFICATION")
                .priorityScore(calculateDepositPriorityScore(amount, assetType))
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean requiresCompliance = complianceService.requiresComplianceCheck(operation);
            if (requiresCompliance) {
                operation.setStatus("PENDING_COMPLIANCE");
            }
            
            operationQueue.offer(operation);
            totalAssetsUnderCustody.addAndGet(amount.longValue());
            assetsDepositedCounter.increment();
            
            DigitalAssetAuditEvent auditEvent = DigitalAssetAuditEvent.builder()
                .operationId(operation.getId())
                .eventType("DEPOSIT_INITIATED")
                .customerId(customerId)
                .assetType(assetType)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "depositAddress", depositAddress,
                    "transactionHash", transactionHash,
                    "source", depositSource
                ))
                .build();
            
            auditEventQueue.offer(auditEvent);
            
            logger.info("Processed asset deposit: customerId={}, assetType={}, amount={}, txHash={}", 
                customerId, assetType, amount, transactionHash);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing asset deposit", e);
            return false;
        } finally {
            sample.stop(depositProcessingTimer);
        }
    }
    
    private boolean processAssetWithdrawal(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String customerId = messageNode.path("customerId").asText();
            String assetType = messageNode.path("assetType").asText();
            String withdrawalAmount = messageNode.path("withdrawalAmount").asText();
            String destinationAddress = messageNode.path("destinationAddress").asText();
            String withdrawalReason = messageNode.path("withdrawalReason").asText();
            String approvalRequired = messageNode.path("approvalRequired").asText();
            
            BigDecimal amount = new BigDecimal(withdrawalAmount);
            
            DigitalAssetCustodyOperation operation = DigitalAssetCustodyOperation.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .operationType("WITHDRAWAL")
                .assetType(assetType)
                .amount(amount)
                .destinationAddress(destinationAddress)
                .withdrawalReason(withdrawalReason)
                .approvalRequired(Boolean.parseBoolean(approvalRequired) || 
                    amount.compareTo(minWithdrawalApprovalAmount) >= 0)
                .status("PENDING_SECURITY_CHECK")
                .priorityScore(calculateWithdrawalPriorityScore(amount, assetType))
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean securityPassed = securityService.validateWithdrawalSecurity(operation);
            if (!securityPassed) {
                operation.setStatus("SECURITY_FAILED");
                securityIncidents.incrementAndGet();
                return false;
            }
            
            if (operation.isApprovalRequired()) {
                operation.setStatus("PENDING_APPROVAL");
                priorityQueue.offer(operation);
            } else {
                operationQueue.offer(operation);
            }
            
            assetsWithdrawnCounter.increment();
            
            DigitalAssetAuditEvent auditEvent = DigitalAssetAuditEvent.builder()
                .operationId(operation.getId())
                .eventType("WITHDRAWAL_INITIATED")
                .customerId(customerId)
                .assetType(assetType)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "destinationAddress", destinationAddress,
                    "withdrawalReason", withdrawalReason,
                    "approvalRequired", String.valueOf(operation.isApprovalRequired())
                ))
                .build();
            
            auditEventQueue.offer(auditEvent);
            
            logger.info("Processed asset withdrawal: customerId={}, assetType={}, amount={}, approvalRequired={}", 
                customerId, assetType, amount, operation.isApprovalRequired());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing asset withdrawal", e);
            return false;
        } finally {
            sample.stop(withdrawalProcessingTimer);
        }
    }
    
    private boolean processAssetTransfer(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String fromCustomerId = messageNode.path("fromCustomerId").asText();
            String toCustomerId = messageNode.path("toCustomerId").asText();
            String assetType = messageNode.path("assetType").asText();
            String transferAmount = messageNode.path("transferAmount").asText();
            String transferReason = messageNode.path("transferReason").asText();
            String transferType = messageNode.path("transferType").asText();
            
            BigDecimal amount = new BigDecimal(transferAmount);
            
            DigitalAssetCustodyOperation operation = DigitalAssetCustodyOperation.builder()
                .id(UUID.randomUUID().toString())
                .customerId(fromCustomerId)
                .operationType("TRANSFER")
                .assetType(assetType)
                .amount(amount)
                .toCustomerId(toCustomerId)
                .transferReason(transferReason)
                .transferType(transferType)
                .status("PENDING_VALIDATION")
                .priorityScore(calculateTransferPriorityScore(amount, transferType))
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean balanceSufficient = custodyService.checkSufficientBalance(fromCustomerId, assetType, amount);
            if (!balanceSufficient) {
                operation.setStatus("INSUFFICIENT_BALANCE");
                return false;
            }
            
            boolean complianceOk = complianceService.validateTransferCompliance(operation);
            if (!complianceOk) {
                operation.setStatus("COMPLIANCE_FAILED");
                complianceViolationsCounter.increment();
                return false;
            }
            
            operationQueue.offer(operation);
            assetsTransferredCounter.increment();
            
            DigitalAssetAuditEvent auditEvent = DigitalAssetAuditEvent.builder()
                .operationId(operation.getId())
                .eventType("TRANSFER_INITIATED")
                .customerId(fromCustomerId)
                .assetType(assetType)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "toCustomerId", toCustomerId,
                    "transferReason", transferReason,
                    "transferType", transferType
                ))
                .build();
            
            auditEventQueue.offer(auditEvent);
            
            logger.info("Processed asset transfer: from={}, to={}, assetType={}, amount={}", 
                fromCustomerId, toCustomerId, assetType, amount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing asset transfer", e);
            return false;
        } finally {
            sample.stop(transferProcessingTimer);
        }
    }
    
    private boolean processVaultCreation(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String customerId = messageNode.path("customerId").asText();
            String vaultType = messageNode.path("vaultType").asText();
            String securityLevel = messageNode.path("securityLevel").asText();
            JsonNode authorizedUsers = messageNode.path("authorizedUsers");
            String multiSigRequired = messageNode.path("multiSigRequired").asText();
            
            DigitalAssetVault vault = DigitalAssetVault.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .vaultType(vaultType)
                .securityLevel(securityLevel)
                .authorizedUsers(extractStringList(authorizedUsers))
                .multiSigRequired(Boolean.parseBoolean(multiSigRequired))
                .status("PENDING_SETUP")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            DigitalAssetSecurityKey securityKey = hsmService.generateVaultSecurityKey(vault);
            securityKeys.put(vault.getId(), securityKey);
            
            vault.setSecurityKeyId(securityKey.getId());
            vault.setStatus("ACTIVE");
            
            vaults.put(vault.getId(), vault);
            activeVaults.incrementAndGet();
            
            vaultService.createVault(vault);
            
            logger.info("Created digital asset vault: vaultId={}, customerId={}, type={}, securityLevel={}", 
                vault.getId(), customerId, vaultType, securityLevel);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error creating digital asset vault", e);
            return false;
        } finally {
            sample.stop(vaultOperationTimer);
        }
    }
    
    private boolean processVaultAccess(JsonNode messageNode, String requestId) {
        try {
            String vaultId = messageNode.path("vaultId").asText();
            String userId = messageNode.path("userId").asText();
            String accessType = messageNode.path("accessType").asText();
            String accessReason = messageNode.path("accessReason").asText();
            
            DigitalAssetVault vault = vaults.get(vaultId);
            if (vault == null) {
                vault = vaultService.getVault(vaultId);
            }
            
            if (vault == null) {
                logger.warn("Vault not found: {}", vaultId);
                return false;
            }
            
            boolean accessGranted = securityService.validateVaultAccess(vault, userId, accessType);
            if (!accessGranted) {
                unauthorizedAccessCounter.increment();
                logger.warn("Unauthorized vault access attempt: vaultId={}, userId={}, accessType={}", 
                    vaultId, userId, accessType);
                return false;
            }
            
            DigitalAssetAuditEvent auditEvent = DigitalAssetAuditEvent.builder()
                .vaultId(vaultId)
                .eventType("VAULT_ACCESS")
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                    "accessType", accessType,
                    "accessReason", accessReason,
                    "accessGranted", "true"
                ))
                .build();
            
            auditEventQueue.offer(auditEvent);
            
            logger.info("Processed vault access: vaultId={}, userId={}, accessType={}, granted={}", 
                vaultId, userId, accessType, accessGranted);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing vault access", e);
            return false;
        }
    }
    
    private boolean processColdStorageMove(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String assetType = messageNode.path("assetType").asText();
            String moveAmount = messageNode.path("moveAmount").asText();
            String moveDirection = messageNode.path("moveDirection").asText(); // TO_COLD or TO_HOT
            String moveReason = messageNode.path("moveReason").asText();
            
            BigDecimal amount = new BigDecimal(moveAmount);
            
            DigitalAssetCustodyOperation operation = DigitalAssetCustodyOperation.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .operationType("STORAGE_MOVE")
                .assetType(assetType)
                .amount(amount)
                .moveDirection(moveDirection)
                .moveReason(moveReason)
                .status("PENDING_EXECUTION")
                .priorityScore(calculateStorageMovePriorityScore(amount, moveDirection))
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean moveExecuted = custodyService.executeColdStorageMove(operation);
            if (moveExecuted) {
                if ("TO_COLD".equals(moveDirection)) {
                    coldStorageAssets.addAndGet(amount.longValue());
                    hotWalletAssets.addAndGet(-amount.longValue());
                } else {
                    coldStorageAssets.addAndGet(-amount.longValue());
                    hotWalletAssets.addAndGet(amount.longValue());
                }
                
                coldStorageMovesCounter.increment();
                operation.setStatus("COMPLETED");
            } else {
                operation.setStatus("FAILED");
            }
            
            custodyService.updateOperation(operation);
            
            logger.info("Processed cold storage move: customerId={}, assetType={}, amount={}, direction={}, executed={}", 
                customerId, assetType, amount, moveDirection, moveExecuted);
            
            return moveExecuted;
            
        } catch (Exception e) {
            logger.error("Error processing cold storage move", e);
            return false;
        }
    }
    
    private boolean processHotWalletActivity(JsonNode messageNode, String requestId) {
        try {
            String walletId = messageNode.path("walletId").asText();
            String activityType = messageNode.path("activityType").asText();
            String assetType = messageNode.path("assetType").asText();
            String activityAmount = messageNode.path("activityAmount").asText();
            String transactionHash = messageNode.path("transactionHash").asText();
            
            BigDecimal amount = new BigDecimal(activityAmount);
            
            DigitalAssetHotWalletActivity activity = DigitalAssetHotWalletActivity.builder()
                .id(UUID.randomUUID().toString())
                .walletId(walletId)
                .activityType(activityType)
                .assetType(assetType)
                .amount(amount)
                .transactionHash(transactionHash)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            custodyService.recordHotWalletActivity(activity);
            hotWalletActivitiesCounter.increment();
            
            if ("OUTGOING".equals(activityType)) {
                hotWalletAssets.addAndGet(-amount.longValue());
            } else if ("INCOMING".equals(activityType)) {
                hotWalletAssets.addAndGet(amount.longValue());
            }
            
            logger.info("Processed hot wallet activity: walletId={}, type={}, assetType={}, amount={}, txHash={}", 
                walletId, activityType, assetType, amount, transactionHash);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing hot wallet activity", e);
            return false;
        }
    }
    
    private boolean processMultiSignatureTransaction(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            String customerId = messageNode.path("customerId").asText();
            String assetType = messageNode.path("assetType").asText();
            String transactionAmount = messageNode.path("transactionAmount").asText();
            JsonNode signatures = messageNode.path("signatures");
            String requiredSignatures = messageNode.path("requiredSignatures").asText();
            
            BigDecimal amount = new BigDecimal(transactionAmount);
            int required = Integer.parseInt(requiredSignatures);
            
            DigitalAssetMultiSigTransaction transaction = DigitalAssetMultiSigTransaction.builder()
                .id(transactionId)
                .customerId(customerId)
                .assetType(assetType)
                .amount(amount)
                .signatures(extractStringList(signatures))
                .requiredSignatures(required)
                .currentSignatures(signatures.size())
                .status(signatures.size() >= required ? "APPROVED" : "PENDING_SIGNATURES")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            if (transaction.getCurrentSignatures() >= required) {
                boolean executed = custodyService.executeMultiSigTransaction(transaction);
                transaction.setStatus(executed ? "EXECUTED" : "FAILED");
                
                if (executed) {
                    multiSigTransactionsCounter.increment();
                }
            }
            
            custodyService.updateMultiSigTransaction(transaction);
            
            logger.info("Processed multi-signature transaction: id={}, customerId={}, amount={}, signatures={}/{}, status={}", 
                transactionId, customerId, amount, signatures.size(), required, transaction.getStatus());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing multi-signature transaction", e);
            return false;
        }
    }
    
    private boolean processSecurityKeyRotation(JsonNode messageNode, String requestId) {
        try {
            String keyId = messageNode.path("keyId").asText();
            String rotationType = messageNode.path("rotationType").asText();
            String rotationReason = messageNode.path("rotationReason").asText();
            
            DigitalAssetSecurityKey currentKey = securityKeys.get(keyId);
            if (currentKey == null) {
                currentKey = securityService.getSecurityKey(keyId);
            }
            
            if (currentKey == null) {
                logger.warn("Security key not found: {}", keyId);
                return false;
            }
            
            DigitalAssetSecurityKey newKey = hsmService.rotateSecurityKey(currentKey, rotationType);
            securityKeys.put(keyId, newKey);
            
            DigitalAssetSecurityEvent securityEvent = DigitalAssetSecurityEvent.builder()
                .eventType("KEY_ROTATION")
                .keyId(keyId)
                .rotationType(rotationType)
                .rotationReason(rotationReason)
                .oldKeyId(currentKey.getId())
                .newKeyId(newKey.getId())
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            securityEventQueue.offer(securityEvent);
            
            logger.info("Processed security key rotation: keyId={}, type={}, reason={}", 
                keyId, rotationType, rotationReason);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing security key rotation", e);
            return false;
        }
    }
    
    private boolean processComplianceCheck(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String operationId = messageNode.path("operationId").asText();
            String customerId = messageNode.path("customerId").asText();
            JsonNode complianceRules = messageNode.path("complianceRules");
            
            DigitalAssetCompliance compliance = complianceService.performComplianceCheck(
                operationId, customerId, complianceRules);
            
            complianceStates.put(operationId, compliance);
            
            if (!compliance.isCompliant()) {
                complianceViolationsCounter.increment();
                logger.warn("Compliance violation detected: operationId={}, violations={}", 
                    operationId, compliance.getViolations());
            }
            
            logger.info("Processed compliance check: operationId={}, compliant={}, score={}", 
                operationId, compliance.isCompliant(), compliance.getComplianceScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing compliance check", e);
            return false;
        } finally {
            sample.stop(complianceCheckTimer);
        }
    }
    
    private boolean processInsuranceClaim(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String claimType = messageNode.path("claimType").asText();
            String claimAmount = messageNode.path("claimAmount").asText();
            String incidentDate = messageNode.path("incidentDate").asText();
            String claimReason = messageNode.path("claimReason").asText();
            JsonNode supportingEvidence = messageNode.path("supportingEvidence");
            
            BigDecimal amount = new BigDecimal(claimAmount);
            
            if (amount.compareTo(insuranceCoverageAmount) > 0) {
                logger.warn("Claim amount exceeds coverage limit: {} > {}", amount, insuranceCoverageAmount);
                return false;
            }
            
            DigitalAssetInsuranceClaim claim = DigitalAssetInsuranceClaim.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .claimType(claimType)
                .claimAmount(amount)
                .incidentDate(LocalDateTime.parse(incidentDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .claimReason(claimReason)
                .supportingEvidence(extractStringList(supportingEvidence))
                .status("PENDING_REVIEW")
                .filedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            insuranceService.fileClaim(claim);
            insuranceClaimsCounter.increment();
            
            logger.info("Processed insurance claim: claimId={}, customerId={}, type={}, amount={}", 
                claim.getId(), customerId, claimType, amount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing insurance claim", e);
            return false;
        }
    }
    
    private boolean processAuditTrailGeneration(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String auditScope = messageNode.path("auditScope").asText();
            String auditPeriod = messageNode.path("auditPeriod").asText();
            JsonNode auditCriteria = messageNode.path("auditCriteria");
            
            DigitalAssetAuditTrail auditTrail = auditService.generateAuditTrail(
                auditScope, auditPeriod, auditCriteria);
            
            logger.info("Generated audit trail: scope={}, period={}, recordCount={}", 
                auditScope, auditPeriod, auditTrail.getRecordCount());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error generating audit trail", e);
            return false;
        } finally {
            sample.stop(auditProcessingTimer);
        }
    }
    
    private boolean processSuspiciousActivityDetected(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String activityType = messageNode.path("activityType").asText();
            String suspicionLevel = messageNode.path("suspicionLevel").asText();
            JsonNode suspiciousIndicators = messageNode.path("suspiciousIndicators");
            String alertSeverity = messageNode.path("alertSeverity").asText();
            
            DigitalAssetSecurityEvent securityEvent = DigitalAssetSecurityEvent.builder()
                .eventType("SUSPICIOUS_ACTIVITY")
                .customerId(customerId)
                .activityType(activityType)
                .suspicionLevel(suspicionLevel)
                .suspiciousIndicators(extractStringList(suspiciousIndicators))
                .alertSeverity(alertSeverity)
                .timestamp(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            securityEventQueue.offer(securityEvent);
            securityEventsCounter.increment();
            
            if ("HIGH".equals(suspicionLevel) || "CRITICAL".equals(suspicionLevel)) {
                securityIncidents.incrementAndGet();
                custodyService.freezeCustomerAssets(customerId, "Suspicious activity detected");
            }
            
            logger.warn("Processed suspicious activity alert: customerId={}, type={}, level={}, indicators={}", 
                customerId, activityType, suspicionLevel, suspiciousIndicators.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing suspicious activity", e);
            return false;
        }
    }
    
    private boolean processCustodyAccountSetup(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String accountType = messageNode.path("accountType").asText();
            JsonNode supportedAssets = messageNode.path("supportedAssets");
            String securityTier = messageNode.path("securityTier").asText();
            String insuranceRequired = messageNode.path("insuranceRequired").asText();
            
            DigitalAssetCustodyAccount account = DigitalAssetCustodyAccount.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .accountType(accountType)
                .supportedAssets(extractStringList(supportedAssets))
                .securityTier(securityTier)
                .insuranceRequired(Boolean.parseBoolean(insuranceRequired))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            custodyAccounts.put(customerId, account);
            custodyService.setupCustodyAccount(account);
            
            logger.info("Setup custody account: customerId={}, accountId={}, type={}, securityTier={}", 
                customerId, account.getId(), accountType, securityTier);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error setting up custody account", e);
            return false;
        }
    }
    
    private boolean processAssetValuationUpdate(JsonNode messageNode, String requestId) {
        try {
            String assetType = messageNode.path("assetType").asText();
            String currentPrice = messageNode.path("currentPrice").asText();
            String priceSource = messageNode.path("priceSource").asText();
            String valuationTimestamp = messageNode.path("valuationTimestamp").asText();
            
            BigDecimal price = new BigDecimal(currentPrice);
            LocalDateTime timestamp = LocalDateTime.parse(valuationTimestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            DigitalAssetValuation valuation = DigitalAssetValuation.builder()
                .assetType(assetType)
                .currentPrice(price)
                .priceSource(priceSource)
                .valuationTimestamp(timestamp)
                .updatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            custodyService.updateAssetValuation(valuation);
            
            logger.info("Updated asset valuation: assetType={}, price={}, source={}, timestamp={}", 
                assetType, price, priceSource, timestamp);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error updating asset valuation", e);
            return false;
        }
    }
    
    private void startOperationProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<DigitalAssetCustodyOperation> operations = new ArrayList<>();
                operationQueue.drainTo(operations, batchSize);
                
                if (!operations.isEmpty()) {
                    DigitalAssetCustodyBatch batch = DigitalAssetCustodyBatch.builder()
                        .id(UUID.randomUUID().toString())
                        .operations(operations)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                    
                    batchQueue.offer(batch);
                }
            } catch (Exception e) {
                logger.error("Error in operation processor", e);
            }
        }, 0, 3, TimeUnit.SECONDS);
    }
    
    private void startBatchProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DigitalAssetCustodyBatch batch = batchQueue.take();
                    processBatch(batch);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in batch processor", e);
                }
            }
        });
    }
    
    private void processBatch(DigitalAssetCustodyBatch batch) {
        try {
            batch.setStatus("PROCESSING");
            batch.setProcessingStarted(LocalDateTime.now());
            
            for (DigitalAssetCustodyOperation operation : batch.getOperations()) {
                try {
                    custodyService.processOperation(operation);
                } catch (Exception e) {
                    logger.error("Error processing operation in batch: {}", operation.getId(), e);
                }
            }
            
            batch.setStatus("COMPLETED");
            batch.setProcessingCompleted(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error processing batch: {}", batch.getId(), e);
            batch.setStatus("FAILED");
            batch.setProcessingCompleted(LocalDateTime.now());
        }
    }
    
    private void startSecurityEventProcessor() {
        securityExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DigitalAssetSecurityEvent event = securityEventQueue.take();
                    securityService.processSecurityEvent(event);
                    securityEventsCounter.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in security event processor", e);
                }
            }
        });
    }
    
    private void startAuditEventProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DigitalAssetAuditEvent event = auditEventQueue.take();
                    auditService.processAuditEvent(event);
                    auditEventsCounter.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in audit event processor", e);
                }
            }
        });
    }
    
    private void startVaultMonitor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                for (DigitalAssetVault vault : vaults.values()) {
                    if ("ACTIVE".equals(vault.getStatus())) {
                        vaultService.monitorVaultSecurity(vault);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in vault monitor", e);
            }
        }, 0, 10, TimeUnit.MINUTES);
    }
    
    private void startRateLimitReset() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            currentGlobalRate.set(0);
            logger.debug("Reset rate limits");
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private boolean isWithinRateLimit() {
        return currentGlobalRate.incrementAndGet() <= globalRateLimit;
    }
    
    private int calculateDepositPriorityScore(BigDecimal amount, String assetType) {
        int score = 50;
        if (amount.compareTo(coldStorageThreshold) >= 0) score += 25;
        if ("BTC".equals(assetType) || "ETH".equals(assetType)) score += 15;
        return score;
    }
    
    private int calculateWithdrawalPriorityScore(BigDecimal amount, String assetType) {
        int score = 40;
        if (amount.compareTo(minWithdrawalApprovalAmount) >= 0) score += 30;
        if ("BTC".equals(assetType) || "ETH".equals(assetType)) score += 15;
        return score;
    }
    
    private int calculateTransferPriorityScore(BigDecimal amount, String transferType) {
        int score = 30;
        if (amount.compareTo(multiSigThreshold) >= 0) score += 25;
        if ("URGENT".equals(transferType)) score += 20;
        return score;
    }
    
    private int calculateStorageMovePriorityScore(BigDecimal amount, String moveDirection) {
        int score = 35;
        if ("TO_COLD".equals(moveDirection) && amount.compareTo(coldStorageThreshold) >= 0) score += 20;
        if ("TO_HOT".equals(moveDirection)) score += 10;
        return score;
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}