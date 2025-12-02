package com.waqiti.gdpr.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;
import com.waqiti.gdpr.service.DataSubjectRequestService;
import com.waqiti.gdpr.service.ConsentManagementService;
import com.waqiti.gdpr.service.DataAnonymizationService;
import com.waqiti.gdpr.service.GDPRComplianceService;
import com.waqiti.gdpr.domain.DataSubjectRequest;
import com.waqiti.gdpr.domain.ConsentRecord;
import com.waqiti.gdpr.domain.DataProcessingActivity;
import com.waqiti.gdpr.domain.RequestAuditLog;
import com.waqiti.gdpr.model.PrivacyRequest;
import com.waqiti.gdpr.model.DataPortabilityRequest;
import com.waqiti.gdpr.model.RightToBeForgottenRequest;
import com.waqiti.gdpr.model.DataRectificationRequest;
import com.waqiti.gdpr.model.ConsentWithdrawalRequest;

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

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive Data Privacy Request Event Consumer for handling GDPR/CCPA data subject requests.
 * 
 * This consumer processes events related to:
 * - GDPR Article 15 (Right of Access) requests
 * - GDPR Article 16 (Right to Rectification) requests
 * - GDPR Article 17 (Right to Erasure/Right to be Forgotten) requests
 * - GDPR Article 18 (Right to Restriction of Processing) requests
 * - GDPR Article 20 (Right to Data Portability) requests
 * - GDPR Article 21 (Right to Object) requests
 * - GDPR Article 22 (Automated Decision-Making) requests
 * - CCPA data subject rights (access, deletion, opt-out)
 * - Consent management and withdrawal
 * - Data breach notifications
 * - Privacy impact assessments
 * - Cross-border data transfer compliance
 * 
 * Compliance Standards:
 * - GDPR (General Data Protection Regulation)
 * - CCPA (California Consumer Privacy Act)
 * - CPRA (California Privacy Rights Act)
 * - LGPD (Brazil Lei Geral de Proteção de Dados)
 * - PIPEDA (Canada Personal Information Protection)
 * - PDPA (Singapore Personal Data Protection Act)
 * - UK Data Protection Act 2018
 * - PCI DSS for payment data protection
 */
@Component
public class DataPrivacyRequestEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DataPrivacyRequestEventConsumer.class);
    
    // Kafka Configuration
    private static final String TOPIC = "waqiti.gdpr.data-privacy-request";
    private static final String CONSUMER_GROUP = "data-privacy-request-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.gdpr.data-privacy-request.dlq";
    
    // GDPR Request Types
    private static final String RIGHT_OF_ACCESS = "RIGHT_OF_ACCESS";
    private static final String RIGHT_TO_RECTIFICATION = "RIGHT_TO_RECTIFICATION";
    private static final String RIGHT_TO_ERASURE = "RIGHT_TO_ERASURE";
    private static final String RIGHT_TO_RESTRICTION = "RIGHT_TO_RESTRICTION";
    private static final String RIGHT_TO_PORTABILITY = "RIGHT_TO_PORTABILITY";
    private static final String RIGHT_TO_OBJECT = "RIGHT_TO_OBJECT";
    private static final String AUTOMATED_DECISION_RIGHTS = "AUTOMATED_DECISION_RIGHTS";
    
    // CCPA Request Types
    private static final String CCPA_ACCESS_REQUEST = "CCPA_ACCESS_REQUEST";
    private static final String CCPA_DELETION_REQUEST = "CCPA_DELETION_REQUEST";
    private static final String CCPA_OPT_OUT_REQUEST = "CCPA_OPT_OUT_REQUEST";
    
    // Privacy Regulation Jurisdictions
    private static final String GDPR_JURISDICTION = "EU";
    private static final String CCPA_JURISDICTION = "CA_US";
    private static final String LGPD_JURISDICTION = "BR";
    private static final String PIPEDA_JURISDICTION = "CA";
    private static final String PDPA_JURISDICTION = "SG";
    
    // Processing Deadlines (in days)
    private static final int GDPR_RESPONSE_DEADLINE = 30;
    private static final int CCPA_RESPONSE_DEADLINE = 45;
    private static final int URGENT_REQUEST_DEADLINE = 72; // hours for data breach requests
    
    // Validation Patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[1-9]?[0-9]{7,15}$");
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^(GDPR|CCPA|LGPD|PIPEDA|PDPA)-\\d{4}-\\d{8}$");
    
    // Service Dependencies
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final DataSubjectRequestService dataSubjectRequestService;
    private final ConsentManagementService consentManagementService;
    private final DataAnonymizationService dataAnonymizationService;
    private final GDPRComplianceService gdprComplianceService;
    // CRITICAL P0 FIX: Added idempotency service to prevent duplicate GDPR request processing
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    // Configuration Properties
    @Value("${gdpr.privacy.batch-size:50}")
    private int batchSize;
    
    @Value("${gdpr.privacy.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${gdpr.privacy.circuit-breaker.failure-rate:50}")
    private float circuitBreakerFailureRate;
    
    @Value("${gdpr.privacy.circuit-breaker.wait-duration:30}")
    private long circuitBreakerWaitDuration;
    
    @Value("${gdpr.privacy.verification.enabled:true}")
    private boolean identityVerificationEnabled;
    
    @Value("${gdpr.privacy.automated-processing.enabled:true}")
    private boolean automatedProcessingEnabled;
    
    @Value("${gdpr.privacy.cross-border-transfer.enabled:true}")
    private boolean crossBorderTransferEnabled;
    
    @Value("${gdpr.privacy.data-retention.default-years:7}")
    private int defaultDataRetentionYears;
    
    @Value("${gdpr.privacy.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${gdpr.privacy.anonymization.enabled:true}")
    private boolean anonymizationEnabled;
    
    // Thread Pools
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(6);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(8);
    private final ExecutorService urgentRequestExecutor = Executors.newFixedThreadPool(3);
    
    // Metrics
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter gdprRequestsCounter;
    private Counter ccpaRequestsCounter;
    private Counter lgpdRequestsCounter;
    private Counter pipedaRequestsCounter;
    private Counter pdpaRequestsCounter;
    private Counter accessRequestsCounter;
    private Counter rectificationRequestsCounter;
    private Counter erasureRequestsCounter;
    private Counter restrictionRequestsCounter;
    private Counter portabilityRequestsCounter;
    private Counter objectRequestsCounter;
    private Counter consentWithdrawalCounter;
    private Counter identityVerificationFailuresCounter;
    private Counter dataBreachNotificationsCounter;
    private Counter crossBorderTransfersCounter;
    private Counter automatedDecisionRequestsCounter;
    private Counter urgentRequestsCounter;
    private Counter deadlineViolationsCounter;
    private Counter complianceViolationsCounter;
    
    private Timer messageProcessingTimer;
    private Timer requestValidationTimer;
    private Timer identityVerificationTimer;
    private Timer dataRetrievalTimer;
    private Timer dataAnonymizationTimer;
    private Timer dataErasureTimer;
    private Timer consentProcessingTimer;
    private Timer complianceCheckTimer;
    
    // Atomic Counters
    private final AtomicLong activeRequests = new AtomicLong(0);
    private final AtomicLong pendingVerifications = new AtomicLong(0);
    private final AtomicLong urgentRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final AtomicLong dataBreachCases = new AtomicLong(0);
    
    // Concurrent Data Structures
    private final ConcurrentHashMap<String, PrivacyRequest> activePrivacyRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataSubjectRequest> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsentRecord> activeConsents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestAuditLog> auditTrail = new ConcurrentHashMap<>();
    
    // Processing Queues
    private final PriorityBlockingQueue<PrivacyRequest> urgentRequestQueue = 
        new PriorityBlockingQueue<>(200, Comparator.comparing(PrivacyRequest::getPriority).reversed());
    private final BlockingQueue<DataSubjectRequest> requestQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ConsentRecord> consentQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<RequestAuditLog> auditQueue = new LinkedBlockingQueue<>();

    public DataPrivacyRequestEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            DataSubjectRequestService dataSubjectRequestService,
            ConsentManagementService consentManagementService,
            DataAnonymizationService dataAnonymizationService,
            GDPRComplianceService gdprComplianceService,
            com.waqiti.common.idempotency.IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.dataSubjectRequestService = dataSubjectRequestService;
        this.consentManagementService = consentManagementService;
        this.dataAnonymizationService = dataAnonymizationService;
        this.gdprComplianceService = gdprComplianceService;
        this.idempotencyService = idempotencyService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics();
        startUrgentRequestProcessor();
        startRequestProcessor();
        startConsentProcessor();
        startAuditProcessor();
        startDeadlineMonitor();
        startComplianceMonitor();
        logger.info("DataPrivacyRequestEventConsumer initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        urgentRequestExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
            if (!urgentRequestExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                urgentRequestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            urgentRequestExecutor.shutdownNow();
        }
        logger.info("DataPrivacyRequestEventConsumer cleanup completed");
    }

    /**
     * Step 1: Initialize comprehensive metrics for data privacy requests
     */
    private void initializeMetrics() {
        // Message Processing Metrics
        messagesProcessedCounter = Counter.builder("data_privacy_messages_processed_total")
            .description("Total data privacy messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("data_privacy_messages_failed_total")
            .description("Total data privacy messages failed")
            .register(meterRegistry);
        
        // Regulation-specific Metrics
        gdprRequestsCounter = Counter.builder("data_privacy_gdpr_requests_total")
            .description("Total GDPR data subject requests")
            .register(meterRegistry);
            
        ccpaRequestsCounter = Counter.builder("data_privacy_ccpa_requests_total")
            .description("Total CCPA data subject requests")
            .register(meterRegistry);
            
        lgpdRequestsCounter = Counter.builder("data_privacy_lgpd_requests_total")
            .description("Total LGPD data subject requests")
            .register(meterRegistry);
            
        pipedaRequestsCounter = Counter.builder("data_privacy_pipeda_requests_total")
            .description("Total PIPEDA data subject requests")
            .register(meterRegistry);
            
        pdpaRequestsCounter = Counter.builder("data_privacy_pdpa_requests_total")
            .description("Total PDPA data subject requests")
            .register(meterRegistry);
        
        // Request Type Metrics
        accessRequestsCounter = Counter.builder("data_privacy_access_requests_total")
            .description("Total data access requests")
            .register(meterRegistry);
            
        rectificationRequestsCounter = Counter.builder("data_privacy_rectification_requests_total")
            .description("Total data rectification requests")
            .register(meterRegistry);
            
        erasureRequestsCounter = Counter.builder("data_privacy_erasure_requests_total")
            .description("Total data erasure requests")
            .register(meterRegistry);
            
        restrictionRequestsCounter = Counter.builder("data_privacy_restriction_requests_total")
            .description("Total data restriction requests")
            .register(meterRegistry);
            
        portabilityRequestsCounter = Counter.builder("data_privacy_portability_requests_total")
            .description("Total data portability requests")
            .register(meterRegistry);
            
        objectRequestsCounter = Counter.builder("data_privacy_object_requests_total")
            .description("Total data processing objection requests")
            .register(meterRegistry);
            
        consentWithdrawalCounter = Counter.builder("data_privacy_consent_withdrawal_total")
            .description("Total consent withdrawal requests")
            .register(meterRegistry);
        
        // Compliance Metrics
        identityVerificationFailuresCounter = Counter.builder("data_privacy_identity_verification_failures_total")
            .description("Total identity verification failures")
            .register(meterRegistry);
            
        dataBreachNotificationsCounter = Counter.builder("data_privacy_breach_notifications_total")
            .description("Total data breach notifications")
            .register(meterRegistry);
            
        crossBorderTransfersCounter = Counter.builder("data_privacy_cross_border_transfers_total")
            .description("Total cross-border data transfers")
            .register(meterRegistry);
            
        automatedDecisionRequestsCounter = Counter.builder("data_privacy_automated_decision_requests_total")
            .description("Total automated decision-making requests")
            .register(meterRegistry);
            
        urgentRequestsCounter = Counter.builder("data_privacy_urgent_requests_total")
            .description("Total urgent privacy requests")
            .register(meterRegistry);
            
        deadlineViolationsCounter = Counter.builder("data_privacy_deadline_violations_total")
            .description("Total privacy request deadline violations")
            .register(meterRegistry);
            
        complianceViolationsCounter = Counter.builder("data_privacy_compliance_violations_total")
            .description("Total privacy compliance violations")
            .register(meterRegistry);
        
        // Timer Metrics
        messageProcessingTimer = Timer.builder("data_privacy_message_processing_duration")
            .description("Data privacy message processing duration")
            .register(meterRegistry);
            
        requestValidationTimer = Timer.builder("data_privacy_request_validation_duration")
            .description("Data privacy request validation duration")
            .register(meterRegistry);
            
        identityVerificationTimer = Timer.builder("data_privacy_identity_verification_duration")
            .description("Identity verification duration")
            .register(meterRegistry);
            
        dataRetrievalTimer = Timer.builder("data_privacy_data_retrieval_duration")
            .description("Data retrieval processing duration")
            .register(meterRegistry);
            
        dataAnonymizationTimer = Timer.builder("data_privacy_data_anonymization_duration")
            .description("Data anonymization processing duration")
            .register(meterRegistry);
            
        dataErasureTimer = Timer.builder("data_privacy_data_erasure_duration")
            .description("Data erasure processing duration")
            .register(meterRegistry);
            
        consentProcessingTimer = Timer.builder("data_privacy_consent_processing_duration")
            .description("Consent processing duration")
            .register(meterRegistry);
            
        complianceCheckTimer = Timer.builder("data_privacy_compliance_check_duration")
            .description("Privacy compliance check duration")
            .register(meterRegistry);
        
        // Gauge Metrics
        Gauge.builder("data_privacy_active_requests")
            .description("Number of active privacy requests")
            .register(meterRegistry, this, value -> activeRequests.get());
            
        Gauge.builder("data_privacy_pending_verifications")
            .description("Number of pending identity verifications")
            .register(meterRegistry, this, value -> pendingVerifications.get());
            
        Gauge.builder("data_privacy_urgent_requests")
            .description("Number of urgent privacy requests")
            .register(meterRegistry, this, value -> urgentRequests.get());
            
        Gauge.builder("data_privacy_completed_requests")
            .description("Number of completed privacy requests")
            .register(meterRegistry, this, value -> completedRequests.get());
            
        Gauge.builder("data_privacy_rejected_requests")
            .description("Number of rejected privacy requests")
            .register(meterRegistry, this, value -> rejectedRequests.get());
            
        Gauge.builder("data_privacy_breach_cases")
            .description("Number of active data breach cases")
            .register(meterRegistry, this, value -> dataBreachCases.get());
    }

    /**
     * Step 2: Main Kafka event processing with comprehensive validation and security
     */
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "data-privacy-circuit-breaker", fallbackMethod = "fallbackProcessing")
    @Retry(name = "data-privacy-retry")
    public void processDataPrivacyRequestEvent(@Payload String message,
                                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                             @Header(KafkaHeaders.OFFSET) long offset,
                                             @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                             Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = null;
        String idempotencyKey = null;
        java.util.UUID operationId = java.util.UUID.randomUUID();

        try {
            // Step 1: Parse message to extract request ID for idempotency BEFORE processing
            JsonNode messageNode = objectMapper.readTree(message);
            requestId = messageNode.path("requestId").asText();

            if (requestId == null || requestId.isEmpty()) {
                requestId = java.util.UUID.randomUUID().toString();
                logger.warn("Message missing requestId, generated new one: {}", requestId);
            }

            // Step 2: CRITICAL - Idempotency check to prevent duplicate GDPR request processing
            // This prevents: duplicate data exports, multiple erasure attempts, duplicate audit logs
            idempotencyKey = "gdpr-privacy-request:" + requestId;
            java.time.Duration ttl = java.time.Duration.ofDays(30); // 30 days for GDPR compliance retention

            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                logger.warn("⚠️ DUPLICATE DETECTED - GDPR privacy request already processed: requestId={}, topic={}, partition={}, offset={}",
                    requestId, topic, partition, offset);

                // Record duplicate metric for monitoring
                messagesFailedCounter.increment();
                metricsService.recordCustomMetric("gdpr_duplicate_request_prevented", 1.0,
                    java.util.Map.of("requestId", requestId, "eventType", messageNode.path("eventType").asText()));

                // Acknowledge to prevent infinite retries
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Setup distributed tracing and logging context
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            MDC.put("service", "data-privacy");
            MDC.put("compliance_context", "GDPR_CCPA");
            MDC.put("idempotencyKey", idempotencyKey);
            MDC.put("operationId", operationId.toString());

            logger.info("Processing data privacy request event: requestId={}, topic={}, partition={}, offset={}",
                requestId, topic, partition, offset);

            // Step 4: Pre-processing validation and security checks
            if (!validateMessageStructure(message)) {
                logger.error("Invalid message structure detected: requestId={}", requestId);
                idempotencyService.failOperation(idempotencyKey, operationId, "Invalid message structure");
                dlqService.sendToDlq(DLQ_TOPIC, message, "Invalid message structure", requestId);
                acknowledgment.acknowledge();
                return;
            }

            // Step 5: Validate event type
            String eventType = messageNode.path("eventType").asText();

            if (!isValidEventType(eventType)) {
                logger.error("Invalid event type: {}, requestId={}", eventType, requestId);
                idempotencyService.failOperation(idempotencyKey, operationId, "Invalid event type: " + eventType);
                dlqService.sendToDlq(DLQ_TOPIC, message, "Invalid event type: " + eventType, requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 6: Extract and validate sensitive data with encryption
            Map<String, Object> sensitiveData = extractSensitiveData(messageNode);
            if (encryptionEnabled && !encryptSensitiveData(sensitiveData, requestId)) {
                logger.error("Failed to encrypt sensitive data");
                dlqService.sendToDlq(DLQ_TOPIC, message, "Encryption failure", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 6: Process event based on type with comprehensive business logic
            boolean processed = processEventByType(eventType, messageNode, requestId, sensitiveData);

            if (processed) {
                // Step 7: Create audit trail entry
                createAuditTrailEntry(eventType, messageNode, requestId, "SUCCESS");

                // Step 8: CRITICAL - Mark idempotency operation as COMPLETE before acknowledging
                java.util.Map<String, Object> result = java.util.Map.of(
                    "requestId", requestId,
                    "eventType", eventType,
                    "status", "SUCCESS",
                    "processedAt", java.time.Instant.now(),
                    "partition", partition,
                    "offset", offset
                );
                idempotencyService.completeOperation(idempotencyKey, operationId, result, java.time.Duration.ofDays(30));

                // Step 9: Update metrics and monitoring
                messagesProcessedCounter.increment();
                metricsService.recordCustomMetric("data_privacy_processed", 1.0,
                    java.util.Map.of("eventType", eventType, "requestId", requestId));

                // Step 10: Acknowledge successful processing AFTER idempotency marked complete
                acknowledgment.acknowledge();
                logger.info("✅ Successfully processed data privacy event: eventType={}, requestId={}, idempotencyKey={}",
                    eventType, requestId, idempotencyKey);
            } else {
                throw new RuntimeException("Failed to process data privacy request event: " + eventType);
            }

        } catch (Exception e) {
            // Step 11: Comprehensive error handling and recovery
            logger.error("❌ Error processing data privacy request event: requestId={}, error={}",
                requestId, e.getMessage(), e);
            messagesFailedCounter.increment();
            complianceViolationsCounter.increment();

            // CRITICAL: Mark idempotent operation as FAILED
            if (idempotencyKey != null && operationId != null) {
                idempotencyService.failOperation(idempotencyKey, operationId,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            // Create audit trail for failure
            createAuditTrailEntry("ERROR", null, requestId != null ? requestId : "UNKNOWN", "FAILURE: " + e.getMessage());

            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId != null ? requestId : "UNKNOWN");
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("🚨 CRITICAL: Failed to send message to DLQ - MESSAGE MAY BE LOST! requestId={}",
                    requestId, dlqException);
            }
        } finally {
            // Step 12: Cleanup and final monitoring
            sample.stop(messageProcessingTimer);
            MDC.clear();
        }
    }

    /**
     * Process events based on type with comprehensive business logic
     */
    private boolean processEventByType(String eventType, JsonNode messageNode, String requestId, 
                                     Map<String, Object> sensitiveData) {
        try {
            switch (eventType) {
                case "PROCESS_ACCESS_REQUEST":
                    return processAccessRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_RECTIFICATION_REQUEST":
                    return processRectificationRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_ERASURE_REQUEST":
                    return processErasureRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_RESTRICTION_REQUEST":
                    return processRestrictionRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_PORTABILITY_REQUEST":
                    return processPortabilityRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_OBJECTION_REQUEST":
                    return processObjectionRequest(messageNode, requestId, sensitiveData);
                case "PROCESS_CONSENT_WITHDRAWAL":
                    return processConsentWithdrawal(messageNode, requestId, sensitiveData);
                case "VALIDATE_IDENTITY":
                    return validateIdentity(messageNode, requestId, sensitiveData);
                case "NOTIFY_DATA_BREACH":
                    return notifyDataBreach(messageNode, requestId, sensitiveData);
                case "PROCESS_CROSS_BORDER_TRANSFER":
                    return processCrossBorderTransfer(messageNode, requestId, sensitiveData);
                case "VALIDATE_CONSENT":
                    return validateConsent(messageNode, requestId, sensitiveData);
                case "ANONYMIZE_PERSONAL_DATA":
                    return anonymizePersonalData(messageNode, requestId, sensitiveData);
                case "GENERATE_PRIVACY_REPORT":
                    return generatePrivacyReport(messageNode, requestId, sensitiveData);
                case "AUDIT_DATA_PROCESSING":
                    return auditDataProcessing(messageNode, requestId, sensitiveData);
                case "UPDATE_REQUEST_STATUS":
                    return updateRequestStatus(messageNode, requestId, sensitiveData);
                default:
                    logger.warn("Unknown data privacy event type: {}", eventType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("Error processing event type: {}", eventType, e);
            return false;
        }
    }

    /**
     * Process data access request (GDPR Article 15, CCPA access right)
     */
    private boolean processAccessRequest(JsonNode messageNode, String requestId, 
                                       Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            activeRequests.incrementAndGet();
            
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String requestType = messageNode.path("requestType").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            String email = messageNode.path("email").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            String preferredFormat = messageNode.path("preferredFormat").asText();
            boolean urgentRequest = messageNode.path("urgentRequest").asBoolean();
            
            // Validate request data
            if (!validateRequestData(privacyRequestId, dataSubjectId, email, jurisdiction)) {
                logger.error("Invalid access request data: requestId={}", privacyRequestId);
                rejectedRequests.incrementAndGet();
                return false;
            }
            
            // Create privacy request
            PrivacyRequest privacyRequest = PrivacyRequest.builder()
                .requestId(privacyRequestId)
                .dataSubjectId(dataSubjectId)
                .requestType(requestType)
                .jurisdiction(jurisdiction)
                .email(email)
                .dataCategories(extractStringList(dataCategories))
                .preferredFormat(preferredFormat)
                .priority(urgentRequest ? 100 : calculateRequestPriority(requestType, jurisdiction))
                .status("RECEIVED")
                .deadline(calculateDeadline(jurisdiction, urgentRequest))
                .submittedAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            // Handle urgent requests
            if (urgentRequest || isUrgentRequest(privacyRequest)) {
                urgentRequestQueue.offer(privacyRequest);
                urgentRequests.incrementAndGet();
                urgentRequestsCounter.increment();
            } else {
                activePrivacyRequests.put(privacyRequestId, privacyRequest);
            }
            
            // Perform identity verification if enabled
            if (identityVerificationEnabled) {
                boolean identityVerified = verifyDataSubjectIdentity(privacyRequest, sensitiveData);
                if (!identityVerified) {
                    privacyRequest.setStatus("IDENTITY_VERIFICATION_FAILED");
                    identityVerificationFailuresCounter.increment();
                    rejectedRequests.incrementAndGet();
                    return false;
                }
                privacyRequest.setIdentityVerified(true);
            }
            
            // Store request
            DataSubjectRequest dataSubjectRequest = convertToDataSubjectRequest(privacyRequest);
            requestQueue.offer(dataSubjectRequest);
            
            // Update jurisdiction-specific counters
            incrementJurisdictionCounter(jurisdiction);
            accessRequestsCounter.increment();
            
            logger.info("Processed access request: requestId={}, subject={}, jurisdiction={}, urgent={}", 
                privacyRequestId, dataSubjectId, jurisdiction, urgentRequest);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing access request", e);
            return false;
        } finally {
            sample.stop(dataRetrievalTimer);
            activeRequests.decrementAndGet();
        }
    }

    /**
     * Process data rectification request (GDPR Article 16)
     */
    private boolean processRectificationRequest(JsonNode messageNode, String requestId, 
                                              Map<String, Object> sensitiveData) {
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            JsonNode incorrectData = messageNode.path("incorrectData");
            JsonNode correctedData = messageNode.path("correctedData");
            String justification = messageNode.path("justification").asText();
            
            // Validate rectification data
            if (incorrectData == null || correctedData == null || justification.isEmpty()) {
                logger.error("Invalid rectification request data: requestId={}", privacyRequestId);
                rejectedRequests.incrementAndGet();
                return false;
            }
            
            DataRectificationRequest rectificationRequest = DataRectificationRequest.builder()
                .requestId(privacyRequestId)
                .dataSubjectId(dataSubjectId)
                .jurisdiction(jurisdiction)
                .incorrectData(incorrectData.toString())
                .correctedData(correctedData.toString())
                .justification(justification)
                .status("RECEIVED")
                .deadline(calculateDeadline(jurisdiction, false))
                .submittedAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            // Process rectification
            boolean rectificationProcessed = dataSubjectRequestService.processRectificationRequest(rectificationRequest);
            
            if (rectificationProcessed) {
                rectificationRequestsCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                logger.info("Processed rectification request: requestId={}, subject={}, jurisdiction={}", 
                    privacyRequestId, dataSubjectId, jurisdiction);
            }
            
            return rectificationProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing rectification request", e);
            return false;
        }
    }

    /**
     * Process data erasure request (GDPR Article 17, CCPA deletion right)
     */
    private boolean processErasureRequest(JsonNode messageNode, String requestId, 
                                        Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            String erasureReason = messageNode.path("erasureReason").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            boolean forceErasure = messageNode.path("forceErasure").asBoolean();
            JsonNode legalBasis = messageNode.path("legalBasis");
            
            // Validate erasure grounds
            if (!validateErasureGrounds(erasureReason, jurisdiction)) {
                logger.error("Invalid erasure grounds: requestId={}, reason={}", 
                    privacyRequestId, erasureReason);
                rejectedRequests.incrementAndGet();
                return false;
            }
            
            RightToBeF'gottenRequest erasureRequest = RightToBeForgettenRequest.builder()
                .requestId(privacyRequestId)
                .dataSubjectId(dataSubjectId)
                .jurisdiction(jurisdiction)
                .erasureReason(erasureReason)
                .dataCategories(extractStringList(dataCategories))
                .forceErasure(forceErasure)
                .legalBasis(legalBasis.toString())
                .status("RECEIVED")
                .deadline(calculateDeadline(jurisdiction, false))
                .submittedAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            // Check for legal obligations preventing erasure
            boolean canErase = gdprComplianceService.validateErasureCompliance(erasureRequest);
            
            if (!canErase && !forceErasure) {
                erasureRequest.setStatus("REJECTED_LEGAL_OBLIGATION");
                rejectedRequests.incrementAndGet();
                logger.warn("Erasure request rejected due to legal obligations: requestId={}", 
                    privacyRequestId);
                return false;
            }
            
            // Process erasure
            boolean erasureProcessed = dataSubjectRequestService.processErasureRequest(erasureRequest);
            
            if (erasureProcessed) {
                erasureRequestsCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                // If anonymization is enabled, anonymize instead of hard delete
                if (anonymizationEnabled && !forceErasure) {
                    dataAnonymizationService.anonymizePersonalData(dataSubjectId, 
                        extractStringList(dataCategories));
                }
                
                logger.info("Processed erasure request: requestId={}, subject={}, jurisdiction={}, forced={}", 
                    privacyRequestId, dataSubjectId, jurisdiction, forceErasure);
            }
            
            return erasureProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing erasure request", e);
            return false;
        } finally {
            sample.stop(dataErasureTimer);
        }
    }

    /**
     * Process data restriction request (GDPR Article 18)
     */
    private boolean processRestrictionRequest(JsonNode messageNode, String requestId, 
                                            Map<String, Object> sensitiveData) {
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            String restrictionReason = messageNode.path("restrictionReason").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            JsonNode processingActivities = messageNode.path("processingActivities");
            
            // Validate restriction grounds
            if (!validateRestrictionGrounds(restrictionReason)) {
                logger.error("Invalid restriction grounds: requestId={}, reason={}", 
                    privacyRequestId, restrictionReason);
                rejectedRequests.incrementAndGet();
                return false;
            }
            
            // Process restriction
            boolean restrictionProcessed = dataSubjectRequestService.processRestrictionRequest(
                privacyRequestId, dataSubjectId, restrictionReason, 
                extractStringList(dataCategories), extractStringList(processingActivities));
            
            if (restrictionProcessed) {
                restrictionRequestsCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                logger.info("Processed restriction request: requestId={}, subject={}, reason={}", 
                    privacyRequestId, dataSubjectId, restrictionReason);
            }
            
            return restrictionProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing restriction request", e);
            return false;
        }
    }

    /**
     * Process data portability request (GDPR Article 20)
     */
    private boolean processPortabilityRequest(JsonNode messageNode, String requestId, 
                                            Map<String, Object> sensitiveData) {
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            String exportFormat = messageNode.path("exportFormat").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            String targetController = messageNode.path("targetController").asText();
            boolean directTransfer = messageNode.path("directTransfer").asBoolean();
            
            DataPortabilityRequest portabilityRequest = DataPortabilityRequest.builder()
                .requestId(privacyRequestId)
                .dataSubjectId(dataSubjectId)
                .jurisdiction(jurisdiction)
                .exportFormat(exportFormat)
                .dataCategories(extractStringList(dataCategories))
                .targetController(targetController)
                .directTransfer(directTransfer)
                .status("RECEIVED")
                .deadline(calculateDeadline(jurisdiction, false))
                .submittedAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            // Process portability
            boolean portabilityProcessed = dataSubjectRequestService.processPortabilityRequest(portabilityRequest);
            
            if (portabilityProcessed) {
                portabilityRequestsCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                if (directTransfer && !targetController.isEmpty()) {
                    crossBorderTransfersCounter.increment();
                }
                
                logger.info("Processed portability request: requestId={}, subject={}, format={}, directTransfer={}", 
                    privacyRequestId, dataSubjectId, exportFormat, directTransfer);
            }
            
            return portabilityProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing portability request", e);
            return false;
        }
    }

    /**
     * Process objection request (GDPR Article 21)
     */
    private boolean processObjectionRequest(JsonNode messageNode, String requestId, 
                                          Map<String, Object> sensitiveData) {
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            String objectionReason = messageNode.path("objectionReason").asText();
            JsonNode processingActivities = messageNode.path("processingActivities");
            boolean includesDirectMarketing = messageNode.path("includesDirectMarketing").asBoolean();
            boolean includesAutomatedDecisionMaking = messageNode.path("includesAutomatedDecisionMaking").asBoolean();
            
            // Process objection
            boolean objectionProcessed = dataSubjectRequestService.processObjectionRequest(
                privacyRequestId, dataSubjectId, objectionReason, 
                extractStringList(processingActivities), includesDirectMarketing, includesAutomatedDecisionMaking);
            
            if (objectionProcessed) {
                objectRequestsCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                if (includesAutomatedDecisionMaking) {
                    automatedDecisionRequestsCounter.increment();
                }
                
                logger.info("Processed objection request: requestId={}, subject={}, reason={}, marketing={}, automated={}", 
                    privacyRequestId, dataSubjectId, objectionReason, includesDirectMarketing, includesAutomatedDecisionMaking);
            }
            
            return objectionProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing objection request", e);
            return false;
        }
    }

    /**
     * Process consent withdrawal request
     */
    private boolean processConsentWithdrawal(JsonNode messageNode, String requestId, 
                                           Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String consentId = messageNode.path("consentId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            String jurisdiction = messageNode.path("jurisdiction").asText();
            JsonNode processingPurposes = messageNode.path("processingPurposes");
            String withdrawalReason = messageNode.path("withdrawalReason").asText();
            boolean stopAllProcessing = messageNode.path("stopAllProcessing").asBoolean();
            
            ConsentWithdrawalRequest withdrawalRequest = ConsentWithdrawalRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .consentId(consentId)
                .dataSubjectId(dataSubjectId)
                .jurisdiction(jurisdiction)
                .processingPurposes(extractStringList(processingPurposes))
                .withdrawalReason(withdrawalReason)
                .stopAllProcessing(stopAllProcessing)
                .withdrawnAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            // Process consent withdrawal
            boolean withdrawalProcessed = consentManagementService.processConsentWithdrawal(withdrawalRequest);
            
            if (withdrawalProcessed) {
                consentWithdrawalCounter.increment();
                incrementJurisdictionCounter(jurisdiction);
                
                // Update consent record
                ConsentRecord consentRecord = ConsentRecord.builder()
                    .consentId(consentId)
                    .dataSubjectId(dataSubjectId)
                    .status("WITHDRAWN")
                    .withdrawnAt(LocalDateTime.now())
                    .withdrawalReason(withdrawalReason)
                    .build();
                
                consentQueue.offer(consentRecord);
                
                logger.info("Processed consent withdrawal: consentId={}, subject={}, stopAll={}", 
                    consentId, dataSubjectId, stopAllProcessing);
            }
            
            return withdrawalProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing consent withdrawal", e);
            return false;
        } finally {
            sample.stop(consentProcessingTimer);
        }
    }

    /**
     * Validate data subject identity
     */
    private boolean validateIdentity(JsonNode messageNode, String requestId, 
                                   Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            pendingVerifications.incrementAndGet();
            
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            JsonNode identityDocuments = messageNode.path("identityDocuments");
            String verificationType = messageNode.path("verificationType").asText();
            JsonNode biometricData = messageNode.path("biometricData");
            
            // Perform identity verification
            boolean identityVerified = gdprComplianceService.verifyDataSubjectIdentity(
                dataSubjectId, identityDocuments, verificationType, biometricData);
            
            if (!identityVerified) {
                identityVerificationFailuresCounter.increment();
                logger.warn("Identity verification failed: requestId={}, subject={}, type={}", 
                    privacyRequestId, dataSubjectId, verificationType);
            }
            
            // Update request status
            PrivacyRequest privacyRequest = activePrivacyRequests.get(privacyRequestId);
            if (privacyRequest != null) {
                privacyRequest.setIdentityVerified(identityVerified);
                privacyRequest.setIdentityVerificationMethod(verificationType);
                privacyRequest.setIdentityVerifiedAt(LocalDateTime.now());
            }
            
            logger.info("Identity verification completed: requestId={}, subject={}, verified={}", 
                privacyRequestId, dataSubjectId, identityVerified);
            
            return identityVerified;
            
        } catch (Exception e) {
            logger.error("Error validating identity", e);
            return false;
        } finally {
            sample.stop(identityVerificationTimer);
            pendingVerifications.decrementAndGet();
        }
    }

    /**
     * Notify data breach to data subjects and supervisory authorities
     */
    private boolean notifyDataBreach(JsonNode messageNode, String requestId, 
                                   Map<String, Object> sensitiveData) {
        try {
            dataBreachCases.incrementAndGet();
            
            String breachId = messageNode.path("breachId").asText();
            String breachType = messageNode.path("breachType").asText();
            String severity = messageNode.path("severity").asText();
            JsonNode affectedDataSubjects = messageNode.path("affectedDataSubjects");
            JsonNode dataCategories = messageNode.path("dataCategories");
            String breachDescription = messageNode.path("breachDescription").asText();
            LocalDateTime breachOccurred = LocalDateTime.parse(
                messageNode.path("breachOccurred").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            JsonNode supervisoryAuthorities = messageNode.path("supervisoryAuthorities");
            
            // Process data breach notification
            boolean notificationSent = gdprComplianceService.processDataBreachNotification(
                breachId, breachType, severity, extractStringList(affectedDataSubjects),
                extractStringList(dataCategories), breachDescription, breachOccurred,
                extractStringList(supervisoryAuthorities));
            
            if (notificationSent) {
                dataBreachNotificationsCounter.increment();
                
                // Create urgent privacy requests for affected data subjects if high severity
                if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                    createUrgentPrivacyRequests(extractStringList(affectedDataSubjects), 
                        breachId, requestId);
                }
                
                logger.error("DATA BREACH NOTIFICATION: breachId={}, type={}, severity={}, affectedCount={}", 
                    breachId, breachType, severity, affectedDataSubjects.size());
            }
            
            return notificationSent;
            
        } catch (Exception e) {
            logger.error("Error notifying data breach", e);
            return false;
        }
    }

    /**
     * Process cross-border data transfer with adequacy decision validation
     */
    private boolean processCrossBorderTransfer(JsonNode messageNode, String requestId, 
                                             Map<String, Object> sensitiveData) {
        try {
            if (!crossBorderTransferEnabled) {
                logger.warn("Cross-border transfer processing is disabled");
                return false;
            }
            
            String transferId = messageNode.path("transferId").asText();
            String sourceCountry = messageNode.path("sourceCountry").asText();
            String destinationCountry = messageNode.path("destinationCountry").asText();
            String legalBasis = messageNode.path("legalBasis").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            JsonNode dataSubjects = messageNode.path("dataSubjects");
            String safeguards = messageNode.path("safeguards").asText();
            
            // Validate adequacy decision or appropriate safeguards
            boolean transferCompliant = gdprComplianceService.validateCrossBorderTransfer(
                sourceCountry, destinationCountry, legalBasis, safeguards);
            
            if (!transferCompliant) {
                logger.error("Cross-border transfer not compliant: transferId={}, source={}, dest={}, basis={}", 
                    transferId, sourceCountry, destinationCountry, legalBasis);
                complianceViolationsCounter.increment();
                return false;
            }
            
            // Process transfer
            boolean transferProcessed = gdprComplianceService.processCrossBorderTransfer(
                transferId, sourceCountry, destinationCountry, legalBasis,
                extractStringList(dataCategories), extractStringList(dataSubjects), safeguards);
            
            if (transferProcessed) {
                crossBorderTransfersCounter.increment();
                
                logger.info("Processed cross-border transfer: transferId={}, source={}, dest={}, basis={}", 
                    transferId, sourceCountry, destinationCountry, legalBasis);
            }
            
            return transferProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing cross-border transfer", e);
            return false;
        }
    }

    /**
     * Validate consent with comprehensive checks
     */
    private boolean validateConsent(JsonNode messageNode, String requestId, 
                                  Map<String, Object> sensitiveData) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            JsonNode processingPurposes = messageNode.path("processingPurposes");
            String jurisdiction = messageNode.path("jurisdiction").asText();
            LocalDateTime consentGiven = LocalDateTime.parse(
                messageNode.path("consentGiven").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // Validate consent compliance
            boolean consentValid = consentManagementService.validateConsent(
                consentId, dataSubjectId, extractStringList(processingPurposes), 
                jurisdiction, consentGiven);
            
            if (!consentValid) {
                logger.warn("Consent validation failed: consentId={}, subject={}, jurisdiction={}", 
                    consentId, dataSubjectId, jurisdiction);
                complianceViolationsCounter.increment();
            }
            
            return consentValid;
            
        } catch (Exception e) {
            logger.error("Error validating consent", e);
            return false;
        }
    }

    /**
     * Anonymize personal data with comprehensive techniques
     */
    private boolean anonymizePersonalData(JsonNode messageNode, String requestId, 
                                        Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            if (!anonymizationEnabled) {
                logger.warn("Data anonymization is disabled");
                return false;
            }
            
            String dataSubjectId = messageNode.path("dataSubjectId").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            String anonymizationTechnique = messageNode.path("anonymizationTechnique").asText();
            boolean preserveStatisticalUtility = messageNode.path("preserveStatisticalUtility").asBoolean();
            JsonNode retentionRequirements = messageNode.path("retentionRequirements");
            
            // Process anonymization
            boolean anonymizationSuccessful = dataAnonymizationService.anonymizePersonalData(
                dataSubjectId, extractStringList(dataCategories), anonymizationTechnique,
                preserveStatisticalUtility, retentionRequirements);
            
            if (anonymizationSuccessful) {
                logger.info("Anonymized personal data: subject={}, categories={}, technique={}, preserveUtility={}", 
                    dataSubjectId, dataCategories.size(), anonymizationTechnique, preserveStatisticalUtility);
            }
            
            return anonymizationSuccessful;
            
        } catch (Exception e) {
            logger.error("Error anonymizing personal data", e);
            return false;
        } finally {
            sample.stop(dataAnonymizationTimer);
        }
    }

    /**
     * Generate comprehensive privacy report
     */
    private boolean generatePrivacyReport(JsonNode messageNode, String requestId, 
                                        Map<String, Object> sensitiveData) {
        try {
            String reportType = messageNode.path("reportType").asText();
            String period = messageNode.path("period").asText();
            JsonNode jurisdictions = messageNode.path("jurisdictions");
            boolean includeMetrics = messageNode.path("includeMetrics").asBoolean();
            boolean includeViolations = messageNode.path("includeViolations").asBoolean();
            
            boolean reportGenerated = gdprComplianceService.generatePrivacyReport(
                reportType, period, extractStringList(jurisdictions), 
                includeMetrics, includeViolations);
            
            logger.info("Generated privacy report: type={}, period={}, jurisdictions={}, metrics={}, violations={}", 
                reportType, period, jurisdictions.size(), includeMetrics, includeViolations);
            
            return reportGenerated;
            
        } catch (Exception e) {
            logger.error("Error generating privacy report", e);
            return false;
        }
    }

    /**
     * Audit data processing activities
     */
    private boolean auditDataProcessing(JsonNode messageNode, String requestId, 
                                      Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String auditId = messageNode.path("auditId").asText();
            String processingActivityId = messageNode.path("processingActivityId").asText();
            JsonNode dataCategories = messageNode.path("dataCategories");
            JsonNode processingPurposes = messageNode.path("processingPurposes");
            String legalBasis = messageNode.path("legalBasis").asText();
            String auditType = messageNode.path("auditType").asText();
            
            // Perform processing audit
            boolean auditCompleted = gdprComplianceService.auditDataProcessing(
                auditId, processingActivityId, extractStringList(dataCategories),
                extractStringList(processingPurposes), legalBasis, auditType);
            
            if (auditCompleted) {
                RequestAuditLog auditLog = RequestAuditLog.builder()
                    .auditId(auditId)
                    .processingActivityId(processingActivityId)
                    .auditType(auditType)
                    .auditResult("COMPLETED")
                    .auditedAt(LocalDateTime.now())
                    .systemRequestId(requestId)
                    .build();
                
                auditQueue.offer(auditLog);
                
                logger.info("Audited data processing: auditId={}, activityId={}, type={}", 
                    auditId, processingActivityId, auditType);
            }
            
            return auditCompleted;
            
        } catch (Exception e) {
            logger.error("Error auditing data processing", e);
            return false;
        } finally {
            sample.stop(complianceCheckTimer);
        }
    }

    /**
     * Update privacy request status
     */
    private boolean updateRequestStatus(JsonNode messageNode, String requestId, 
                                      Map<String, Object> sensitiveData) {
        try {
            String privacyRequestId = messageNode.path("privacyRequestId").asText();
            String newStatus = messageNode.path("newStatus").asText();
            String statusReason = messageNode.path("statusReason").asText();
            JsonNode responseData = messageNode.path("responseData");
            String completedBy = messageNode.path("completedBy").asText();
            
            PrivacyRequest privacyRequest = activePrivacyRequests.get(privacyRequestId);
            if (privacyRequest == null) {
                logger.error("Privacy request not found: requestId={}", privacyRequestId);
                return false;
            }
            
            // Update request status
            privacyRequest.setStatus(newStatus);
            privacyRequest.setStatusReason(statusReason);
            privacyRequest.setCompletedBy(completedBy);
            privacyRequest.setUpdatedAt(LocalDateTime.now());
            
            if (responseData != null && !responseData.isNull()) {
                privacyRequest.setResponseData(responseData.toString());
            }
            
            // Handle completed requests
            if ("COMPLETED".equals(newStatus)) {
                privacyRequest.setCompletedAt(LocalDateTime.now());
                activePrivacyRequests.remove(privacyRequestId);
                completedRequests.incrementAndGet();
                
                // Calculate processing time
                Duration processingTime = Duration.between(
                    privacyRequest.getSubmittedAt(), privacyRequest.getCompletedAt());
                
                // Check for deadline violations
                if (LocalDateTime.now().isAfter(privacyRequest.getDeadline())) {
                    deadlineViolationsCounter.increment();
                    logger.warn("Privacy request deadline violation: requestId={}, deadline={}", 
                        privacyRequestId, privacyRequest.getDeadline());
                }
                
                logger.info("Completed privacy request: requestId={}, status={}, processingTime={}hours", 
                    privacyRequestId, newStatus, processingTime.toHours());
                
            } else if ("REJECTED".equals(newStatus)) {
                activePrivacyRequests.remove(privacyRequestId);
                rejectedRequests.incrementAndGet();
                
                logger.info("Rejected privacy request: requestId={}, reason={}", 
                    privacyRequestId, statusReason);
            }
            
            // Update in database
            DataSubjectRequest dataSubjectRequest = convertToDataSubjectRequest(privacyRequest);
            boolean updated = dataSubjectRequestService.updateRequestStatus(dataSubjectRequest);
            
            return updated;
            
        } catch (Exception e) {
            logger.error("Error updating request status", e);
            return false;
        }
    }

    // Background processors and utility methods

    private void startUrgentRequestProcessor() {
        urgentRequestExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PrivacyRequest urgentRequest = urgentRequestQueue.take();
                    
                    try {
                        // Process urgent request immediately
                        DataSubjectRequest dataSubjectRequest = convertToDataSubjectRequest(urgentRequest);
                        
                        boolean processed = dataSubjectRequestService.processUrgentRequest(dataSubjectRequest);
                        
                        if (processed) {
                            urgentRequest.setStatus("PROCESSED");
                            urgentRequest.setProcessedAt(LocalDateTime.now());
                            
                            completedRequests.incrementAndGet();
                            
                            logger.info("Processed urgent privacy request: requestId={}, type={}", 
                                urgentRequest.getRequestId(), urgentRequest.getRequestType());
                        }
                        
                        urgentRequests.decrementAndGet();
                        
                    } catch (Exception e) {
                        logger.error("Error processing urgent request: {}", urgentRequest.getRequestId(), e);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in urgent request processor", e);
                }
            }
        });
    }

    private void startRequestProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<DataSubjectRequest> requests = new ArrayList<>();
                requestQueue.drainTo(requests, batchSize / 2);
                
                if (!requests.isEmpty()) {
                    batchExecutor.submit(() -> processDataSubjectRequests(requests));
                }
            } catch (Exception e) {
                logger.error("Error in request processor", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void startConsentProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<ConsentRecord> consents = new ArrayList<>();
                consentQueue.drainTo(consents, batchSize / 4);
                
                if (!consents.isEmpty()) {
                    batchExecutor.submit(() -> processConsentRecords(consents));
                }
            } catch (Exception e) {
                logger.error("Error in consent processor", e);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private void startAuditProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<RequestAuditLog> auditLogs = new ArrayList<>();
                auditQueue.drainTo(auditLogs, batchSize);
                
                if (!auditLogs.isEmpty()) {
                    batchExecutor.submit(() -> processAuditLogs(auditLogs));
                }
            } catch (Exception e) {
                logger.error("Error in audit processor", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void startDeadlineMonitor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                checkRequestDeadlines();
            } catch (Exception e) {
                logger.error("Error in deadline monitor", e);
            }
        }, 1, 6, TimeUnit.HOURS);
    }

    private void startComplianceMonitor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                performComplianceChecks();
            } catch (Exception e) {
                logger.error("Error in compliance monitor", e);
            }
        }, 1, 12, TimeUnit.HOURS);
    }

    // Utility and validation methods

    private boolean validateMessageStructure(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            return node.has("eventType") && node.has("timestamp") && node.has("data");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidEventType(String eventType) {
        return Set.of("PROCESS_ACCESS_REQUEST", "PROCESS_RECTIFICATION_REQUEST", 
                     "PROCESS_ERASURE_REQUEST", "PROCESS_RESTRICTION_REQUEST",
                     "PROCESS_PORTABILITY_REQUEST", "PROCESS_OBJECTION_REQUEST",
                     "PROCESS_CONSENT_WITHDRAWAL", "VALIDATE_IDENTITY", "NOTIFY_DATA_BREACH",
                     "PROCESS_CROSS_BORDER_TRANSFER", "VALIDATE_CONSENT", "ANONYMIZE_PERSONAL_DATA",
                     "GENERATE_PRIVACY_REPORT", "AUDIT_DATA_PROCESSING", "UPDATE_REQUEST_STATUS")
                .contains(eventType);
    }

    private Map<String, Object> extractSensitiveData(JsonNode messageNode) {
        Map<String, Object> sensitiveData = new HashMap<>();
        
        if (messageNode.has("email")) {
            sensitiveData.put("email", messageNode.path("email").asText());
        }
        if (messageNode.has("identityDocuments")) {
            sensitiveData.put("identityDocuments", messageNode.path("identityDocuments"));
        }
        if (messageNode.has("biometricData")) {
            sensitiveData.put("biometricData", messageNode.path("biometricData"));
        }
        
        return sensitiveData;
    }

    private boolean encryptSensitiveData(Map<String, Object> sensitiveData, String requestId) {
        try {
            // Implementation would use HSM or encryption service
            return true;
        } catch (Exception e) {
            logger.error("Encryption failed for requestId: {}", requestId, e);
            return false;
        }
    }

    private void createAuditTrailEntry(String eventType, JsonNode messageNode, String requestId, String result) {
        try {
            RequestAuditLog auditLog = RequestAuditLog.builder()
                .auditId(UUID.randomUUID().toString())
                .activityType(eventType)
                .userId("SYSTEM")
                .activityTime(LocalDateTime.now())
                .result(result)
                .systemRequestId(requestId)
                .createdAt(LocalDateTime.now())
                .build();
            
            auditQueue.offer(auditLog);
        } catch (Exception e) {
            logger.error("Failed to create audit trail entry", e);
        }
    }

    private boolean validateRequestData(String requestId, String dataSubjectId, String email, String jurisdiction) {
        return REQUEST_ID_PATTERN.matcher(requestId).matches() &&
               !dataSubjectId.isEmpty() &&
               EMAIL_PATTERN.matcher(email).matches() &&
               isValidJurisdiction(jurisdiction);
    }

    private boolean isValidJurisdiction(String jurisdiction) {
        return Set.of(GDPR_JURISDICTION, CCPA_JURISDICTION, LGPD_JURISDICTION, 
                     PIPEDA_JURISDICTION, PDPA_JURISDICTION).contains(jurisdiction);
    }

    private int calculateRequestPriority(String requestType, String jurisdiction) {
        int basePriority = 50;
        
        // Adjust based on request type
        if (RIGHT_TO_ERASURE.equals(requestType) || CCPA_DELETION_REQUEST.equals(requestType)) {
            basePriority += 20;
        } else if (RIGHT_OF_ACCESS.equals(requestType) || CCPA_ACCESS_REQUEST.equals(requestType)) {
            basePriority += 10;
        }
        
        // Adjust based on jurisdiction
        if (GDPR_JURISDICTION.equals(jurisdiction)) {
            basePriority += 15;
        } else if (CCPA_JURISDICTION.equals(jurisdiction)) {
            basePriority += 10;
        }
        
        return Math.min(basePriority, 100);
    }

    private LocalDateTime calculateDeadline(String jurisdiction, boolean urgent) {
        if (urgent) {
            return LocalDateTime.now().plusHours(URGENT_REQUEST_DEADLINE);
        }
        
        int deadlineDays = switch (jurisdiction) {
            case GDPR_JURISDICTION -> GDPR_RESPONSE_DEADLINE;
            case CCPA_JURISDICTION -> CCPA_RESPONSE_DEADLINE;
            default -> GDPR_RESPONSE_DEADLINE; // Default to GDPR deadline
        };
        
        return LocalDateTime.now().plusDays(deadlineDays);
    }

    private boolean isUrgentRequest(PrivacyRequest request) {
        return request.getPriority() >= 80 ||
               Set.of(RIGHT_TO_ERASURE, CCPA_DELETION_REQUEST).contains(request.getRequestType()) ||
               request.getRequestId().contains("BREACH");
    }

    private boolean verifyDataSubjectIdentity(PrivacyRequest request, Map<String, Object> sensitiveData) {
        // Implementation would perform comprehensive identity verification
        return gdprComplianceService.verifyDataSubjectIdentity(
            request.getDataSubjectId(), 
            (JsonNode) sensitiveData.get("identityDocuments"),
            "STANDARD",
            (JsonNode) sensitiveData.get("biometricData"));
    }

    private DataSubjectRequest convertToDataSubjectRequest(PrivacyRequest privacyRequest) {
        return DataSubjectRequest.builder()
            .requestId(privacyRequest.getRequestId())
            .dataSubjectId(privacyRequest.getDataSubjectId())
            .requestType(privacyRequest.getRequestType())
            .status(privacyRequest.getStatus())
            .submittedAt(privacyRequest.getSubmittedAt())
            .deadline(privacyRequest.getDeadline())
            .build();
    }

    private void incrementJurisdictionCounter(String jurisdiction) {
        switch (jurisdiction) {
            case GDPR_JURISDICTION:
                gdprRequestsCounter.increment();
                break;
            case CCPA_JURISDICTION:
                ccpaRequestsCounter.increment();
                break;
            case LGPD_JURISDICTION:
                lgpdRequestsCounter.increment();
                break;
            case PIPEDA_JURISDICTION:
                pipedaRequestsCounter.increment();
                break;
            case PDPA_JURISDICTION:
                pdpaRequestsCounter.increment();
                break;
        }
    }

    private boolean validateErasureGrounds(String reason, String jurisdiction) {
        Set<String> validGdprGrounds = Set.of(
            "CONSENT_WITHDRAWN", "NO_LONGER_NECESSARY", "UNLAWFULLY_PROCESSED",
            "LEGAL_OBLIGATION", "CHILD_CONSENT");
        Set<String> validCcpaGrounds = Set.of(
            "CONSUMER_REQUEST", "SALE_OPT_OUT", "BUSINESS_PURPOSE_ENDED");
        
        return (GDPR_JURISDICTION.equals(jurisdiction) && validGdprGrounds.contains(reason)) ||
               (CCPA_JURISDICTION.equals(jurisdiction) && validCcpaGrounds.contains(reason));
    }

    private boolean validateRestrictionGrounds(String reason) {
        return Set.of("ACCURACY_CONTESTED", "UNLAWFUL_PROCESSING", "NO_LONGER_NEEDED", 
                     "OBJECTION_PENDING").contains(reason);
    }

    private void createUrgentPrivacyRequests(List<String> affectedDataSubjects, String breachId, String requestId) {
        for (String dataSubjectId : affectedDataSubjects) {
            PrivacyRequest urgentRequest = PrivacyRequest.builder()
                .requestId("BREACH-" + breachId + "-" + dataSubjectId)
                .dataSubjectId(dataSubjectId)
                .requestType("BREACH_NOTIFICATION_RESPONSE")
                .priority(100)
                .status("URGENT")
                .deadline(LocalDateTime.now().plusHours(URGENT_REQUEST_DEADLINE))
                .submittedAt(LocalDateTime.now())
                .systemRequestId(requestId)
                .build();
            
            urgentRequestQueue.offer(urgentRequest);
            urgentRequests.incrementAndGet();
        }
    }

    private void processDataSubjectRequests(List<DataSubjectRequest> requests) {
        for (DataSubjectRequest request : requests) {
            try {
                boolean processed = dataSubjectRequestService.processDataSubjectRequest(request);
                
                if (processed) {
                    logger.info("Processed data subject request: requestId={}, type={}", 
                        request.getRequestId(), request.getRequestType());
                } else {
                    rejectedRequests.incrementAndGet();
                    logger.warn("Failed to process data subject request: requestId={}", 
                        request.getRequestId());
                }
                
            } catch (Exception e) {
                logger.error("Error processing data subject request: {}", request.getRequestId(), e);
            }
        }
    }

    private void processConsentRecords(List<ConsentRecord> consents) {
        for (ConsentRecord consent : consents) {
            try {
                consentManagementService.updateConsentRecord(consent);
                
                logger.debug("Updated consent record: consentId={}, status={}", 
                    consent.getConsentId(), consent.getStatus());
                
            } catch (Exception e) {
                logger.error("Error processing consent record: {}", consent.getConsentId(), e);
            }
        }
    }

    private void processAuditLogs(List<RequestAuditLog> auditLogs) {
        for (RequestAuditLog auditLog : auditLogs) {
            try {
                gdprComplianceService.saveAuditLog(auditLog);
                
                logger.debug("Saved audit log: auditId={}, activityType={}", 
                    auditLog.getAuditId(), auditLog.getActivityType());
                
            } catch (Exception e) {
                logger.error("Error processing audit log: {}", auditLog.getAuditId(), e);
            }
        }
    }

    private void checkRequestDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningThreshold = now.plusDays(3);
        
        activePrivacyRequests.values().stream()
            .filter(request -> request.getDeadline().isBefore(warningThreshold))
            .forEach(request -> {
                logger.warn("Privacy request deadline approaching: requestId={}, deadline={}", 
                    request.getRequestId(), request.getDeadline());
                
                if (request.getDeadline().isBefore(now)) {
                    deadlineViolationsCounter.increment();
                    logger.error("Privacy request deadline violation: requestId={}, deadline={}", 
                        request.getRequestId(), request.getDeadline());
                }
            });
    }

    private void performComplianceChecks() {
        // Perform regular compliance validation checks
        logger.debug("Performing scheduled privacy compliance checks");
        
        // Check for expired consents
        consentManagementService.checkExpiredConsents();
        
        // Validate data retention compliance
        gdprComplianceService.validateDataRetentionCompliance();
        
        // Check cross-border transfer adequacy decisions
        if (crossBorderTransferEnabled) {
            gdprComplianceService.validateCrossBorderTransferCompliance();
        }
    }

    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }

    /**
     * Circuit breaker fallback method
     */
    public void fallbackProcessing(String message, String topic, int partition, long offset, 
                                 long timestamp, Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker activated for data privacy requests, sending to DLQ", ex);
        
        try {
            dlqService.sendToDlq(DLQ_TOPIC, message, "Circuit breaker activated: " + ex.getMessage(), 
                UUID.randomUUID().toString());
            acknowledgment.acknowledge();
        } catch (Exception dlqException) {
            logger.error("Failed to send message to DLQ during circuit breaker fallback", dlqException);
        }
    }
}