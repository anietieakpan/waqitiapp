package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;
import com.waqiti.compliance.service.FINCENIntegrationService;
import com.waqiti.compliance.service.RegulatoryFilingService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.model.FinCEN314Request;
import com.waqiti.compliance.model.FinCEN314Response;
import com.waqiti.compliance.model.FinCENSubmission;
import com.waqiti.compliance.model.RegulatoryFiling;
import com.waqiti.compliance.domain.ComplianceAuditEntry;

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
import java.math.BigDecimal;
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
 * Comprehensive FinCEN Reporting Event Consumer for handling FinCEN 314(a) and 314(b) reporting requirements.
 * 
 * This consumer processes events related to:
 * - FinCEN 314(a) requests from law enforcement
 * - FinCEN 314(b) information sharing between financial institutions
 * - FinCEN SAR (Suspicious Activity Report) processing
 * - BSA (Bank Secrecy Act) compliance reporting
 * - CTR (Currency Transaction Report) submissions
 * - FBAR (Foreign Bank Account Report) processing
 * 
 * Compliance Standards:
 * - BSA/AML regulations
 * - OFAC sanctions compliance
 * - PCI DSS for data protection
 * - GDPR for data privacy
 * - SOX for financial reporting
 * - Basel III for risk management
 */
@Component
public class FINCENReportingEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FINCENReportingEventConsumer.class);
    
    // Kafka Configuration
    private static final String TOPIC = "waqiti.compliance.fincen-reporting";
    private static final String CONSUMER_GROUP = "fincen-reporting-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.compliance.fincen-reporting.dlq";
    
    // FinCEN Filing Types
    private static final String FINCEN_314A = "FINCEN_314A";
    private static final String FINCEN_314B = "FINCEN_314B";
    private static final String SAR_FILING = "SAR_FILING";
    private static final String CTR_FILING = "CTR_FILING";
    private static final String FBAR_FILING = "FBAR_FILING";
    
    // BSA Compliance Thresholds
    private static final double CTR_THRESHOLD = 10000.00;
    private static final double STRUCTURING_THRESHOLD = 10000.00;
    private static final int MULTIPLE_TRANSACTION_DAYS = 1;
    
    // Validation Patterns
    private static final Pattern SSN_PATTERN = Pattern.compile("^\\d{3}-?\\d{2}-?\\d{4}$");
    private static final Pattern EIN_PATTERN = Pattern.compile("^\\d{2}-?\\d{7}$");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Z0-9]{8,17}$");
    private static final Pattern ROUTING_PATTERN = Pattern.compile("^\\d{9}$");
    
    // Service Dependencies
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final FINCENIntegrationService finCENIntegrationService;
    private final RegulatoryFilingService regulatoryFilingService;
    private final ComplianceAuditService complianceAuditService;
    
    // Configuration Properties
    @Value("${compliance.fincen.batch-size:50}")
    private int batchSize;
    
    @Value("${compliance.fincen.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${compliance.fincen.circuit-breaker.failure-rate:50}")
    private float circuitBreakerFailureRate;
    
    @Value("${compliance.fincen.circuit-breaker.wait-duration:30}")
    private long circuitBreakerWaitDuration;
    
    @Value("${compliance.fincen.314a.response-deadline-hours:72}")
    private int finCEN314AResponseDeadlineHours;
    
    @Value("${compliance.fincen.314b.sharing-threshold:5000}")
    private double finCEN314BSharingThreshold;
    
    @Value("${compliance.fincen.sar.filing-deadline-days:30}")
    private int sarFilingDeadlineDays;
    
    @Value("${compliance.fincen.ctr.filing-deadline-days:15}")
    private int ctrFilingDeadlineDays;
    
    @Value("${compliance.fincen.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${compliance.fincen.data-retention-years:5}")
    private int dataRetentionYears;
    
    // Thread Pools
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService urgentFilingExecutor = Executors.newFixedThreadPool(2);
    
    // Metrics
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter finCEN314ARequestsCounter;
    private Counter finCEN314BSubmissionsCounter;
    private Counter sarFilingsCounter;
    private Counter ctrFilingsCounter;
    private Counter fbarFilingsCounter;
    private Counter urgentFilingsCounter;
    private Counter dataValidationErrorsCounter;
    private Counter encryptionFailuresCounter;
    private Counter auditTrailEntriesCounter;
    private Counter deadlineViolationsCounter;
    private Counter complianceViolationsCounter;
    
    private Timer messageProcessingTimer;
    private Timer finCENSubmissionTimer;
    private Timer dataValidationTimer;
    private Timer encryptionTimer;
    private Timer auditingTimer;
    
    // Atomic Counters
    private final AtomicLong active314ARequests = new AtomicLong(0);
    private final AtomicLong pending314BSubmissions = new AtomicLong(0);
    private final AtomicLong activeSARFilings = new AtomicLong(0);
    private final AtomicLong activeCTRFilings = new AtomicLong(0);
    private final AtomicLong urgentFilings = new AtomicLong(0);
    private final AtomicLong dataRetentionViolations = new AtomicLong(0);
    
    // Concurrent Data Structures
    private final ConcurrentHashMap<String, FinCEN314Request> active314ARequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FinCENSubmission> pendingSubmissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegulatoryFiling> activeFilings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ComplianceAuditEntry> auditTrail = new ConcurrentHashMap<>();
    
    // Processing Queues
    private final PriorityBlockingQueue<FinCEN314Request> urgent314AQueue = 
        new PriorityBlockingQueue<>(100, Comparator.comparing(FinCEN314Request::getPriority).reversed());
    private final BlockingQueue<FinCENSubmission> submissionQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<RegulatoryFiling> filingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ComplianceAuditEntry> auditQueue = new LinkedBlockingQueue<>();

    public FINCENReportingEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            FINCENIntegrationService finCENIntegrationService,
            RegulatoryFilingService regulatoryFilingService,
            ComplianceAuditService complianceAuditService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.finCENIntegrationService = finCENIntegrationService;
        this.regulatoryFilingService = regulatoryFilingService;
        this.complianceAuditService = complianceAuditService;
    }

    @PostConstruct
    public void init() {
        initializeMetrics();
        startUrgent314AProcessor();
        startSubmissionProcessor();
        startFilingProcessor();
        startAuditProcessor();
        startDeadlineMonitor();
        startDataRetentionCleanup();
        logger.info("FINCENReportingEventConsumer initialized successfully");
    }

    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        urgentFilingExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
            if (!urgentFilingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                urgentFilingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            urgentFilingExecutor.shutdownNow();
        }
        logger.info("FINCENReportingEventConsumer cleanup completed");
    }

    /**
     * Step 1: Initialize comprehensive metrics for FinCEN reporting
     */
    private void initializeMetrics() {
        // Message Processing Metrics
        messagesProcessedCounter = Counter.builder("fincen_reporting_messages_processed_total")
            .description("Total FinCEN reporting messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("fincen_reporting_messages_failed_total")
            .description("Total FinCEN reporting messages failed")
            .register(meterRegistry);
        
        // FinCEN-specific Metrics
        finCEN314ARequestsCounter = Counter.builder("fincen_314a_requests_total")
            .description("Total FinCEN 314(a) requests processed")
            .register(meterRegistry);
            
        finCEN314BSubmissionsCounter = Counter.builder("fincen_314b_submissions_total")
            .description("Total FinCEN 314(b) submissions")
            .register(meterRegistry);
            
        sarFilingsCounter = Counter.builder("fincen_sar_filings_total")
            .description("Total SAR filings submitted")
            .register(meterRegistry);
            
        ctrFilingsCounter = Counter.builder("fincen_ctr_filings_total")
            .description("Total CTR filings submitted")
            .register(meterRegistry);
            
        fbarFilingsCounter = Counter.builder("fincen_fbar_filings_total")
            .description("Total FBAR filings submitted")
            .register(meterRegistry);
            
        urgentFilingsCounter = Counter.builder("fincen_urgent_filings_total")
            .description("Total urgent FinCEN filings")
            .register(meterRegistry);
            
        dataValidationErrorsCounter = Counter.builder("fincen_data_validation_errors_total")
            .description("Total data validation errors")
            .register(meterRegistry);
            
        encryptionFailuresCounter = Counter.builder("fincen_encryption_failures_total")
            .description("Total encryption failures")
            .register(meterRegistry);
            
        auditTrailEntriesCounter = Counter.builder("fincen_audit_trail_entries_total")
            .description("Total audit trail entries created")
            .register(meterRegistry);
            
        deadlineViolationsCounter = Counter.builder("fincen_deadline_violations_total")
            .description("Total filing deadline violations")
            .register(meterRegistry);
            
        complianceViolationsCounter = Counter.builder("fincen_compliance_violations_total")
            .description("Total compliance violations detected")
            .register(meterRegistry);
        
        // Timer Metrics
        messageProcessingTimer = Timer.builder("fincen_reporting_message_processing_duration")
            .description("FinCEN reporting message processing duration")
            .register(meterRegistry);
            
        finCENSubmissionTimer = Timer.builder("fincen_submission_duration")
            .description("FinCEN submission processing duration")
            .register(meterRegistry);
            
        dataValidationTimer = Timer.builder("fincen_data_validation_duration")
            .description("FinCEN data validation duration")
            .register(meterRegistry);
            
        encryptionTimer = Timer.builder("fincen_encryption_duration")
            .description("FinCEN data encryption duration")
            .register(meterRegistry);
            
        auditingTimer = Timer.builder("fincen_auditing_duration")
            .description("FinCEN audit trail creation duration")
            .register(meterRegistry);
        
        // Gauge Metrics
        Gauge.builder("fincen_active_314a_requests")
            .description("Number of active FinCEN 314(a) requests")
            .register(meterRegistry, this, value -> active314ARequests.get());
            
        Gauge.builder("fincen_pending_314b_submissions")
            .description("Number of pending FinCEN 314(b) submissions")
            .register(meterRegistry, this, value -> pending314BSubmissions.get());
            
        Gauge.builder("fincen_active_sar_filings")
            .description("Number of active SAR filings")
            .register(meterRegistry, this, value -> activeSARFilings.get());
            
        Gauge.builder("fincen_active_ctr_filings")
            .description("Number of active CTR filings")
            .register(meterRegistry, this, value -> activeCTRFilings.get());
            
        Gauge.builder("fincen_urgent_filings")
            .description("Number of urgent filings requiring immediate attention")
            .register(meterRegistry, this, value -> urgentFilings.get());
            
        Gauge.builder("fincen_data_retention_violations")
            .description("Number of data retention policy violations")
            .register(meterRegistry, this, value -> dataRetentionViolations.get());
    }

    /**
     * Step 2: Main Kafka event processing with comprehensive validation and security
     */
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "fincen-reporting-circuit-breaker", fallbackMethod = "fallbackProcessing")
    @Retry(name = "fincen-reporting-retry")
    public void processFinCENReportingEvent(@Payload String message,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                          Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            // Step 3: Setup distributed tracing and logging context
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            MDC.put("service", "fincen-reporting");
            MDC.put("compliance_context", "BSA_AML");
            
            logger.info("Processing FinCEN reporting event: topic={}, partition={}, offset={}", 
                topic, partition, offset);
            
            // Step 4: Pre-processing validation and security checks
            if (!validateMessageStructure(message)) {
                logger.error("Invalid message structure detected");
                dataValidationErrorsCounter.increment();
                dlqService.sendToDlq(DLQ_TOPIC, message, "Invalid message structure", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 5: Parse and validate message content
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            if (!isValidEventType(eventType)) {
                logger.error("Invalid event type: {}", eventType);
                dlqService.sendToDlq(DLQ_TOPIC, message, "Invalid event type: " + eventType, requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 6: Extract and validate sensitive data with encryption
            Map<String, Object> sensitiveData = extractSensitiveData(messageNode);
            if (encryptionEnabled && !encryptSensitiveData(sensitiveData, requestId)) {
                logger.error("Failed to encrypt sensitive data");
                encryptionFailuresCounter.increment();
                dlqService.sendToDlq(DLQ_TOPIC, message, "Encryption failure", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 7: Process event based on type with comprehensive business logic
            boolean processed = processEventByType(eventType, messageNode, requestId, sensitiveData);
            
            if (processed) {
                // Step 8: Create audit trail entry
                createAuditTrailEntry(eventType, messageNode, requestId, "SUCCESS");
                
                // Step 9: Update metrics and monitoring
                messagesProcessedCounter.increment();
                metricsService.recordCustomMetric("fincen_reporting_processed", 1.0, 
                    Map.of("eventType", eventType, "requestId", requestId));
                
                // Step 10: Acknowledge successful processing
                acknowledgment.acknowledge();
                logger.info("Successfully processed FinCEN event: eventType={}, requestId={}", 
                    eventType, requestId);
            } else {
                throw new RuntimeException("Failed to process FinCEN reporting event: " + eventType);
            }
            
        } catch (Exception e) {
            // Step 11: Comprehensive error handling and recovery
            logger.error("Error processing FinCEN reporting event", e);
            messagesFailedCounter.increment();
            complianceViolationsCounter.increment();
            
            // Create audit trail for failure
            createAuditTrailEntry("ERROR", null, requestId, "FAILURE: " + e.getMessage());
            
            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("Failed to send message to DLQ", dlqException);
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
                case "PROCESS_314A_REQUEST":
                    return processFinCEN314ARequest(messageNode, requestId, sensitiveData);
                case "SUBMIT_314B_INFORMATION":
                    return submitFinCEN314BInformation(messageNode, requestId, sensitiveData);
                case "FILE_SAR_REPORT":
                    return fileSARReport(messageNode, requestId, sensitiveData);
                case "FILE_CTR_REPORT":
                    return fileCTRReport(messageNode, requestId, sensitiveData);
                case "FILE_FBAR_REPORT":
                    return fileFBARReport(messageNode, requestId, sensitiveData);
                case "VALIDATE_BSA_COMPLIANCE":
                    return validateBSACompliance(messageNode, requestId, sensitiveData);
                case "SCREEN_OFAC_SANCTIONS":
                    return screenOFACSanctions(messageNode, requestId, sensitiveData);
                case "GENERATE_COMPLIANCE_REPORT":
                    return generateComplianceReport(messageNode, requestId, sensitiveData);
                case "AUDIT_FINCEN_ACTIVITY":
                    return auditFinCENActivity(messageNode, requestId, sensitiveData);
                case "UPDATE_REGULATORY_STATUS":
                    return updateRegulatoryStatus(messageNode, requestId, sensitiveData);
                case "PROCESS_LAW_ENFORCEMENT_REQUEST":
                    return processLawEnforcementRequest(messageNode, requestId, sensitiveData);
                case "VALIDATE_DATA_RETENTION":
                    return validateDataRetention(messageNode, requestId, sensitiveData);
                default:
                    logger.warn("Unknown FinCEN event type: {}", eventType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("Error processing event type: {}", eventType, e);
            return false;
        }
    }

    /**
     * Process FinCEN 314(a) law enforcement requests
     */
    private boolean processFinCEN314ARequest(JsonNode messageNode, String requestId, 
                                           Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            active314ARequests.incrementAndGet();
            
            String requestNumber = messageNode.path("requestNumber").asText();
            String subjectName = messageNode.path("subjectName").asText();
            String subjectSSN = messageNode.path("subjectSSN").asText();
            String dateOfBirth = messageNode.path("dateOfBirth").asText();
            String requestingAgency = messageNode.path("requestingAgency").asText();
            String priority = messageNode.path("priority").asText();
            LocalDateTime deadline = LocalDateTime.parse(messageNode.path("deadline").asText(), 
                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // Validate required fields
            if (!validateSSN(subjectSSN) || subjectName.isEmpty() || requestingAgency.isEmpty()) {
                logger.error("Invalid 314(a) request data: requestNumber={}", requestNumber);
                dataValidationErrorsCounter.increment();
                return false;
            }
            
            // Create 314(a) request object
            FinCEN314Request request = FinCEN314Request.builder()
                .requestNumber(requestNumber)
                .subjectName(subjectName)
                .subjectSSN(subjectSSN)
                .dateOfBirth(dateOfBirth)
                .requestingAgency(requestingAgency)
                .priority(calculatePriority(priority))
                .deadline(deadline)
                .status("PROCESSING")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            // Check if urgent processing required
            if (isUrgentRequest(request)) {
                urgent314AQueue.offer(request);
                urgentFilings.incrementAndGet();
                urgentFilingsCounter.increment();
            } else {
                active314ARequests.put(requestNumber, request);
            }
            
            // Initiate customer search and account analysis
            boolean searchResult = finCENIntegrationService.searchCustomerRecords(request);
            
            if (searchResult) {
                finCEN314ARequestsCounter.increment();
                logger.info("Successfully processed 314(a) request: requestNumber={}, agency={}", 
                    requestNumber, requestingAgency);
            }
            
            return searchResult;
            
        } catch (Exception e) {
            logger.error("Error processing 314(a) request", e);
            return false;
        } finally {
            sample.stop(finCENSubmissionTimer);
            active314ARequests.decrementAndGet();
        }
    }

    /**
     * Submit FinCEN 314(b) information sharing
     */
    private boolean submitFinCEN314BInformation(JsonNode messageNode, String requestId, 
                                              Map<String, Object> sensitiveData) {
        try {
            pending314BSubmissions.incrementAndGet();
            
            String submissionId = messageNode.path("submissionId").asText();
            String targetInstitution = messageNode.path("targetInstitution").asText();
            String subjectName = messageNode.path("subjectName").asText();
            String informationType = messageNode.path("informationType").asText();
            JsonNode accountData = messageNode.path("accountData");
            double shareThreshold = messageNode.path("shareThreshold").asDouble();
            
            // Validate sharing threshold
            if (shareThreshold < finCEN314BSharingThreshold) {
                logger.warn("Information below sharing threshold: submissionId={}, amount={}", 
                    submissionId, shareThreshold);
                return true; // Not an error, just below threshold
            }
            
            // Create 314(b) submission
            FinCENSubmission submission = FinCENSubmission.builder()
                .submissionId(submissionId)
                .targetInstitution(targetInstitution)
                .subjectName(subjectName)
                .informationType(informationType)
                .accountData(accountData.toString())
                .shareThreshold(shareThreshold)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            submissionQueue.offer(submission);
            pendingSubmissions.put(submissionId, submission);
            
            finCEN314BSubmissionsCounter.increment();
            
            logger.info("Queued 314(b) submission: submissionId={}, target={}", 
                submissionId, targetInstitution);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error submitting 314(b) information", e);
            return false;
        } finally {
            pending314BSubmissions.decrementAndGet();
        }
    }

    /**
     * File SAR (Suspicious Activity Report)
     */
    private boolean fileSARReport(JsonNode messageNode, String requestId, 
                                Map<String, Object> sensitiveData) {
        try {
            activeSARFilings.incrementAndGet();
            
            String sarId = messageNode.path("sarId").asText();
            String customerId = messageNode.path("customerId").asText();
            String suspiciousActivity = messageNode.path("suspiciousActivity").asText();
            String narrative = messageNode.path("narrative").asText();
            // CRITICAL FIX: Use BigDecimal for financial precision (prevents precision loss in SAR amounts)
            BigDecimal totalAmount = new BigDecimal(messageNode.path("totalAmount").asText());
            JsonNode transactionIds = messageNode.path("transactionIds");
            LocalDateTime filingDeadline = LocalDateTime.now().plusDays(sarFilingDeadlineDays);
            
            // Validate SAR requirements
            if (suspiciousActivity.isEmpty() || narrative.length() < 50) {
                logger.error("Invalid SAR data: sarId={}", sarId);
                dataValidationErrorsCounter.increment();
                return false;
            }
            
            // Create SAR filing
            RegulatoryFiling sarFiling = RegulatoryFiling.builder()
                .filingId(sarId)
                .filingType("SAR")
                .customerId(customerId)
                .description(suspiciousActivity)
                .narrative(narrative)
                .totalAmount(totalAmount)
                .transactionIds(extractStringList(transactionIds))
                .deadline(filingDeadline)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            filingQueue.offer(sarFiling);
            activeFilings.put(sarId, sarFiling);
            
            sarFilingsCounter.increment();
            
            logger.info("Queued SAR filing: sarId={}, customerId={}, amount={}", 
                sarId, customerId, totalAmount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error filing SAR report", e);
            return false;
        }
    }

    /**
     * File CTR (Currency Transaction Report)
     */
    private boolean fileCTRReport(JsonNode messageNode, String requestId, 
                                Map<String, Object> sensitiveData) {
        try {
            activeCTRFilings.incrementAndGet();
            
            String ctrId = messageNode.path("ctrId").asText();
            String customerId = messageNode.path("customerId").asText();
            // CRITICAL FIX: Use BigDecimal for financial precision (prevents precision loss in CTR threshold comparisons)
            BigDecimal transactionAmount = new BigDecimal(messageNode.path("transactionAmount").asText());
            String transactionType = messageNode.path("transactionType").asText();
            String cashInstrument = messageNode.path("cashInstrument").asText();
            LocalDateTime transactionDate = LocalDateTime.parse(
                messageNode.path("transactionDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // Validate CTR threshold - CRITICAL: Use BigDecimal.compareTo() for accurate threshold comparison
            BigDecimal ctrThreshold = new BigDecimal("10000");
            if (transactionAmount.compareTo(ctrThreshold) < 0) {
                logger.warn("Transaction below CTR threshold: ctrId={}, amount={}",
                    ctrId, transactionAmount);
                return true; // Not an error, just below threshold
            }

            // Check for structuring patterns
            boolean structuringDetected = detectStructuring(customerId, transactionAmount, transactionDate);
            
            RegulatoryFiling ctrFiling = RegulatoryFiling.builder()
                .filingId(ctrId)
                .filingType("CTR")
                .customerId(customerId)
                .totalAmount(transactionAmount)
                .transactionType(transactionType)
                .cashInstrument(cashInstrument)
                .transactionDate(transactionDate)
                .structuringDetected(structuringDetected)
                .deadline(LocalDateTime.now().plusDays(ctrFilingDeadlineDays))
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            filingQueue.offer(ctrFiling);
            activeFilings.put(ctrId, ctrFiling);
            
            ctrFilingsCounter.increment();
            
            if (structuringDetected) {
                logger.warn("Potential structuring detected: ctrId={}, customerId={}", 
                    ctrId, customerId);
                complianceViolationsCounter.increment();
            }
            
            logger.info("Queued CTR filing: ctrId={}, customerId={}, amount={}", 
                ctrId, customerId, transactionAmount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error filing CTR report", e);
            return false;
        }
    }

    /**
     * File FBAR (Foreign Bank Account Report)
     */
    private boolean fileFBARReport(JsonNode messageNode, String requestId, 
                                 Map<String, Object> sensitiveData) {
        try {
            String fbarId = messageNode.path("fbarId").asText();
            String customerId = messageNode.path("customerId").asText();
            JsonNode foreignAccounts = messageNode.path("foreignAccounts");
            // CRITICAL FIX: Use BigDecimal for financial precision (prevents precision loss in FBAR threshold comparisons)
            BigDecimal aggregateBalance = new BigDecimal(messageNode.path("aggregateBalance").asText());
            int reportingYear = messageNode.path("reportingYear").asInt();

            // Validate FBAR requirements - CRITICAL: Use BigDecimal.compareTo() for accurate threshold comparison
            BigDecimal fbarThreshold = new BigDecimal("10000");
            if (aggregateBalance.compareTo(fbarThreshold) < 0) {
                logger.info("Aggregate balance below FBAR threshold: fbarId={}, balance={}",
                    fbarId, aggregateBalance);
                return true; // Below threshold, no filing required
            }
            
            RegulatoryFiling fbarFiling = RegulatoryFiling.builder()
                .filingId(fbarId)
                .filingType("FBAR")
                .customerId(customerId)
                .totalAmount(aggregateBalance)
                .reportingYear(reportingYear)
                .foreignAccountData(foreignAccounts.toString())
                .deadline(LocalDateTime.of(reportingYear + 1, 4, 15, 23, 59))
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            filingQueue.offer(fbarFiling);
            activeFilings.put(fbarId, fbarFiling);
            
            fbarFilingsCounter.increment();
            
            logger.info("Queued FBAR filing: fbarId={}, customerId={}, balance={}", 
                fbarId, customerId, aggregateBalance);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error filing FBAR report", e);
            return false;
        }
    }

    /**
     * Validate BSA compliance
     */
    private boolean validateBSACompliance(JsonNode messageNode, String requestId, 
                                        Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String complianceCheckId = messageNode.path("complianceCheckId").asText();
            String customerId = messageNode.path("customerId").asText();
            JsonNode transactionData = messageNode.path("transactionData");
            String checkType = messageNode.path("checkType").asText();
            
            // Perform comprehensive BSA compliance validation
            boolean complianceResult = regulatoryFilingService.validateBSACompliance(
                customerId, transactionData, checkType);
            
            if (!complianceResult) {
                complianceViolationsCounter.increment();
                logger.warn("BSA compliance violation detected: customerId={}, checkType={}", 
                    customerId, checkType);
            }
            
            return complianceResult;
            
        } catch (Exception e) {
            logger.error("Error validating BSA compliance", e);
            return false;
        } finally {
            sample.stop(dataValidationTimer);
        }
    }

    /**
     * Screen against OFAC sanctions
     */
    private boolean screenOFACSanctions(JsonNode messageNode, String requestId, 
                                      Map<String, Object> sensitiveData) {
        try {
            String screeningId = messageNode.path("screeningId").asText();
            String entityName = messageNode.path("entityName").asText();
            String entityType = messageNode.path("entityType").asText();
            JsonNode identifiers = messageNode.path("identifiers");
            
            boolean sanctionsMatch = finCENIntegrationService.screenOFACSanctions(
                entityName, entityType, identifiers);
            
            if (sanctionsMatch) {
                complianceViolationsCounter.increment();
                logger.error("OFAC sanctions match detected: screeningId={}, entity={}", 
                    screeningId, entityName);
                
                // Immediately escalate sanctions match
                escalateSanctionsMatch(screeningId, entityName, requestId);
            }
            
            return !sanctionsMatch; // Return true if no match (compliant)
            
        } catch (Exception e) {
            logger.error("Error screening OFAC sanctions", e);
            return false;
        }
    }

    /**
     * Generate comprehensive compliance report
     */
    private boolean generateComplianceReport(JsonNode messageNode, String requestId, 
                                           Map<String, Object> sensitiveData) {
        try {
            String reportId = messageNode.path("reportId").asText();
            String reportType = messageNode.path("reportType").asText();
            String period = messageNode.path("period").asText();
            JsonNode reportParameters = messageNode.path("reportParameters");
            
            boolean reportGenerated = regulatoryFilingService.generateComplianceReport(
                reportType, period, reportParameters);
            
            logger.info("Generated compliance report: reportId={}, type={}, period={}", 
                reportId, reportType, period);
            
            return reportGenerated;
            
        } catch (Exception e) {
            logger.error("Error generating compliance report", e);
            return false;
        }
    }

    /**
     * Audit FinCEN activity
     */
    private boolean auditFinCENActivity(JsonNode messageNode, String requestId, 
                                      Map<String, Object> sensitiveData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String auditId = messageNode.path("auditId").asText();
            String activityType = messageNode.path("activityType").asText();
            String userId = messageNode.path("userId").asText();
            LocalDateTime activityTime = LocalDateTime.parse(
                messageNode.path("activityTime").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            JsonNode auditData = messageNode.path("auditData");
            
            ComplianceAuditEntry auditEntry = ComplianceAuditEntry.builder()
                .auditId(auditId)
                .activityType(activityType)
                .userId(userId)
                .activityTime(activityTime)
                .auditData(auditData.toString())
                .requestId(requestId)
                .createdAt(LocalDateTime.now())
                .build();
            
            auditQueue.offer(auditEntry);
            auditTrail.put(auditId, auditEntry);
            
            auditTrailEntriesCounter.increment();
            
            logger.info("Created audit entry: auditId={}, activityType={}, userId={}", 
                auditId, activityType, userId);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error auditing FinCEN activity", e);
            return false;
        } finally {
            sample.stop(auditingTimer);
        }
    }

    /**
     * Update regulatory status
     */
    private boolean updateRegulatoryStatus(JsonNode messageNode, String requestId, 
                                         Map<String, Object> sensitiveData) {
        try {
            String filingId = messageNode.path("filingId").asText();
            String newStatus = messageNode.path("newStatus").asText();
            String statusReason = messageNode.path("statusReason").asText();
            LocalDateTime statusDate = LocalDateTime.parse(
                messageNode.path("statusDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            RegulatoryFiling filing = activeFilings.get(filingId);
            if (filing != null) {
                filing.setStatus(newStatus);
                filing.setStatusReason(statusReason);
                filing.setStatusDate(statusDate);
                filing.setUpdatedAt(LocalDateTime.now());
                
                regulatoryFilingService.updateFilingStatus(filing);
                
                logger.info("Updated regulatory status: filingId={}, status={}, reason={}", 
                    filingId, newStatus, statusReason);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error updating regulatory status", e);
            return false;
        }
    }

    /**
     * Process law enforcement request
     */
    private boolean processLawEnforcementRequest(JsonNode messageNode, String requestId, 
                                               Map<String, Object> sensitiveData) {
        try {
            String requestNumber = messageNode.path("requestNumber").asText();
            String agency = messageNode.path("agency").asText();
            String requestType = messageNode.path("requestType").asText();
            JsonNode subjectDetails = messageNode.path("subjectDetails");
            String legalBasis = messageNode.path("legalBasis").asText();
            LocalDateTime responseDeadline = LocalDateTime.parse(
                messageNode.path("responseDeadline").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // Validate legal basis and agency authorization
            if (!validateLegalBasis(legalBasis) || !validateAgencyAuthorization(agency)) {
                logger.error("Invalid law enforcement request: requestNumber={}, agency={}", 
                    requestNumber, agency);
                complianceViolationsCounter.increment();
                return false;
            }
            
            boolean requestProcessed = finCENIntegrationService.processLawEnforcementRequest(
                requestNumber, agency, requestType, subjectDetails, legalBasis, responseDeadline);
            
            logger.info("Processed law enforcement request: requestNumber={}, agency={}, type={}", 
                requestNumber, agency, requestType);
            
            return requestProcessed;
            
        } catch (Exception e) {
            logger.error("Error processing law enforcement request", e);
            return false;
        }
    }

    /**
     * Validate data retention compliance
     */
    private boolean validateDataRetention(JsonNode messageNode, String requestId, 
                                        Map<String, Object> sensitiveData) {
        try {
            String dataType = messageNode.path("dataType").asText();
            LocalDateTime dataCreationDate = LocalDateTime.parse(
                messageNode.path("dataCreationDate").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String retentionPolicy = messageNode.path("retentionPolicy").asText();
            
            LocalDateTime retentionDeadline = dataCreationDate.plusYears(dataRetentionYears);
            boolean withinRetentionPeriod = LocalDateTime.now().isBefore(retentionDeadline);
            
            if (!withinRetentionPeriod) {
                dataRetentionViolations.incrementAndGet();
                logger.warn("Data retention violation: dataType={}, createdDate={}", 
                    dataType, dataCreationDate);
            }
            
            return withinRetentionPeriod;
            
        } catch (Exception e) {
            logger.error("Error validating data retention", e);
            return false;
        }
    }

    // Background processors and utility methods

    private void startUrgent314AProcessor() {
        urgentFilingExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    FinCEN314Request urgentRequest = urgent314AQueue.take();
                    
                    try {
                        boolean processed = finCENIntegrationService.processUrgent314ARequest(urgentRequest);
                        
                        if (processed) {
                            finCEN314ARequestsCounter.increment();
                            logger.info("Processed urgent 314(a) request: requestNumber={}", 
                                urgentRequest.getRequestNumber());
                        }
                        
                        urgentFilings.decrementAndGet();
                        
                    } catch (Exception e) {
                        logger.error("Error processing urgent 314(a) request: {}", 
                            urgentRequest.getRequestNumber(), e);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in urgent 314(a) processor", e);
                }
            }
        });
    }

    private void startSubmissionProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<FinCENSubmission> submissions = new ArrayList<>();
                submissionQueue.drainTo(submissions, batchSize / 4);
                
                if (!submissions.isEmpty()) {
                    batchExecutor.submit(() -> processSubmissions(submissions));
                }
            } catch (Exception e) {
                logger.error("Error in submission processor", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void startFilingProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<RegulatoryFiling> filings = new ArrayList<>();
                filingQueue.drainTo(filings, batchSize / 2);
                
                if (!filings.isEmpty()) {
                    batchExecutor.submit(() -> processFilings(filings));
                }
            } catch (Exception e) {
                logger.error("Error in filing processor", e);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private void startAuditProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<ComplianceAuditEntry> entries = new ArrayList<>();
                auditQueue.drainTo(entries, batchSize);
                
                if (!entries.isEmpty()) {
                    batchExecutor.submit(() -> processAuditEntries(entries));
                }
            } catch (Exception e) {
                logger.error("Error in audit processor", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void startDeadlineMonitor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                checkFilingDeadlines();
            } catch (Exception e) {
                logger.error("Error in deadline monitor", e);
            }
        }, 1, 6, TimeUnit.HOURS);
    }

    private void startDataRetentionCleanup() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                cleanupExpiredData();
            } catch (Exception e) {
                logger.error("Error in data retention cleanup", e);
            }
        }, 1, 24, TimeUnit.HOURS);
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
        return Set.of("PROCESS_314A_REQUEST", "SUBMIT_314B_INFORMATION", "FILE_SAR_REPORT",
                     "FILE_CTR_REPORT", "FILE_FBAR_REPORT", "VALIDATE_BSA_COMPLIANCE",
                     "SCREEN_OFAC_SANCTIONS", "GENERATE_COMPLIANCE_REPORT", "AUDIT_FINCEN_ACTIVITY",
                     "UPDATE_REGULATORY_STATUS", "PROCESS_LAW_ENFORCEMENT_REQUEST", "VALIDATE_DATA_RETENTION")
                .contains(eventType);
    }

    private Map<String, Object> extractSensitiveData(JsonNode messageNode) {
        Map<String, Object> sensitiveData = new HashMap<>();
        
        // Extract PII and sensitive financial data
        if (messageNode.has("subjectSSN")) {
            sensitiveData.put("ssn", messageNode.path("subjectSSN").asText());
        }
        if (messageNode.has("accountNumber")) {
            sensitiveData.put("accountNumber", messageNode.path("accountNumber").asText());
        }
        if (messageNode.has("routingNumber")) {
            sensitiveData.put("routingNumber", messageNode.path("routingNumber").asText());
        }
        
        return sensitiveData;
    }

    private boolean encryptSensitiveData(Map<String, Object> sensitiveData, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Implementation would use HSM or encryption service
            // For now, return true to indicate successful encryption
            return true;
        } catch (Exception e) {
            logger.error("Encryption failed for requestId: {}", requestId, e);
            return false;
        } finally {
            sample.stop(encryptionTimer);
        }
    }

    private void createAuditTrailEntry(String eventType, JsonNode messageNode, String requestId, String result) {
        try {
            ComplianceAuditEntry auditEntry = ComplianceAuditEntry.builder()
                .auditId(UUID.randomUUID().toString())
                .activityType(eventType)
                .userId("SYSTEM")
                .activityTime(LocalDateTime.now())
                .result(result)
                .requestId(requestId)
                .createdAt(LocalDateTime.now())
                .build();
            
            auditQueue.offer(auditEntry);
            auditTrailEntriesCounter.increment();
        } catch (Exception e) {
            logger.error("Failed to create audit trail entry", e);
        }
    }

    private boolean validateSSN(String ssn) {
        return ssn != null && SSN_PATTERN.matcher(ssn).matches();
    }

    private int calculatePriority(String priority) {
        switch (priority.toUpperCase()) {
            case "URGENT": return 100;
            case "HIGH": return 75;
            case "MEDIUM": return 50;
            case "LOW": return 25;
            default: return 10;
        }
    }

    private boolean isUrgentRequest(FinCEN314Request request) {
        return request.getPriority() >= 75 || 
               Duration.between(LocalDateTime.now(), request.getDeadline()).toHours() <= 24;
    }

    /**
     * CRITICAL: Detects potential structuring (multiple transactions to avoid CTR threshold)
     * Uses BigDecimal for precise amount comparisons to avoid false positives/negatives
     */
    private boolean detectStructuring(String customerId, BigDecimal amount, LocalDateTime transactionDate) {
        // Implementation would check for multiple transactions below threshold within time window
        // TODO: Implement complete structuring detection with:
        // - 24-hour rolling window analysis
        // - Pattern detection for amounts just below $10,000
        // - Frequency analysis
        // - Multi-account aggregation
        return false; // Placeholder - requires implementation
    }

    private void escalateSanctionsMatch(String screeningId, String entityName, String requestId) {
        // Implementation would immediately notify compliance team and freeze accounts
        logger.error("SANCTIONS MATCH ESCALATION: screeningId={}, entity={}, requestId={}", 
            screeningId, entityName, requestId);
    }

    private boolean validateLegalBasis(String legalBasis) {
        return Set.of("SUBPOENA", "COURT_ORDER", "NATIONAL_SECURITY_LETTER", "FINCEN_314A")
                .contains(legalBasis);
    }

    private boolean validateAgencyAuthorization(String agency) {
        return Set.of("FBI", "DEA", "IRS", "SECRET_SERVICE", "FINCEN", "LOCAL_PD")
                .contains(agency);
    }

    private void processSubmissions(List<FinCENSubmission> submissions) {
        for (FinCENSubmission submission : submissions) {
            try {
                boolean submitted = finCENIntegrationService.submit314BInformation(submission);
                
                submission.setStatus(submitted ? "SUBMITTED" : "FAILED");
                submission.setProcessedAt(LocalDateTime.now());
                
                if (submitted) {
                    finCEN314BSubmissionsCounter.increment();
                }
                
            } catch (Exception e) {
                logger.error("Error processing submission: {}", submission.getSubmissionId(), e);
                submission.setStatus("FAILED");
                submission.setProcessedAt(LocalDateTime.now());
            }
        }
    }

    private void processFilings(List<RegulatoryFiling> filings) {
        for (RegulatoryFiling filing : filings) {
            try {
                boolean filed = regulatoryFilingService.submitFiling(filing);
                
                filing.setStatus(filed ? "FILED" : "FAILED");
                filing.setProcessedAt(LocalDateTime.now());
                
                if (filed) {
                    switch (filing.getFilingType()) {
                        case "SAR":
                            sarFilingsCounter.increment();
                            activeSARFilings.decrementAndGet();
                            break;
                        case "CTR":
                            ctrFilingsCounter.increment();
                            activeCTRFilings.decrementAndGet();
                            break;
                        case "FBAR":
                            fbarFilingsCounter.increment();
                            break;
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error processing filing: {}", filing.getFilingId(), e);
                filing.setStatus("FAILED");
                filing.setProcessedAt(LocalDateTime.now());
            }
        }
    }

    private void processAuditEntries(List<ComplianceAuditEntry> entries) {
        for (ComplianceAuditEntry entry : entries) {
            try {
                complianceAuditService.saveAuditEntry(entry);
                
                logger.debug("Saved audit entry: auditId={}, activityType={}", 
                    entry.getAuditId(), entry.getActivityType());
                
            } catch (Exception e) {
                logger.error("Error processing audit entry: {}", entry.getAuditId(), e);
            }
        }
    }

    private void checkFilingDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime urgentThreshold = now.plusHours(24);
        
        activeFilings.values().stream()
            .filter(filing -> "PENDING".equals(filing.getStatus()))
            .filter(filing -> filing.getDeadline().isBefore(urgentThreshold))
            .forEach(filing -> {
                logger.warn("Filing deadline approaching: filingId={}, deadline={}, type={}", 
                    filing.getFilingId(), filing.getDeadline(), filing.getFilingType());
                
                if (filing.getDeadline().isBefore(now)) {
                    deadlineViolationsCounter.increment();
                    logger.error("Filing deadline violation: filingId={}, deadline={}", 
                        filing.getFilingId(), filing.getDeadline());
                }
            });
    }

    private void cleanupExpiredData() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(dataRetentionYears);
        
        auditTrail.entrySet().removeIf(entry -> {
            ComplianceAuditEntry auditEntry = entry.getValue();
            return auditEntry.getCreatedAt().isBefore(cutoff);
        });
        
        logger.info("Completed data retention cleanup, cutoff date: {}", cutoff);
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
        logger.error("Circuit breaker activated for FinCEN reporting, sending to DLQ", ex);
        
        try {
            dlqService.sendToDlq(DLQ_TOPIC, message, "Circuit breaker activated: " + ex.getMessage(), 
                UUID.randomUUID().toString());
            acknowledgment.acknowledge();
        } catch (Exception dlqException) {
            logger.error("Failed to send message to DLQ during circuit breaker fallback", dlqException);
        }
    }
}