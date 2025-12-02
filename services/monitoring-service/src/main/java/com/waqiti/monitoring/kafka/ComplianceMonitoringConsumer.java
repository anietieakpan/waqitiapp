package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.entity.ComplianceEvent;
import com.waqiti.monitoring.repository.ComplianceEventRepository;
import com.waqiti.monitoring.service.*;
import com.waqiti.monitoring.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ComplianceMonitoringConsumer {

    private static final String TOPIC = "compliance-monitoring";
    private static final String GROUP_ID = "monitoring-compliance-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final double PCI_DSS_THRESHOLD = 0.95;
    private static final double SOC2_THRESHOLD = 0.90;
    private static final double GDPR_THRESHOLD = 0.98;
    private static final double ISO27001_THRESHOLD = 0.92;
    private static final int DATA_RETENTION_VIOLATION_THRESHOLD = 5;
    private static final int PRIVACY_VIOLATION_THRESHOLD = 3;
    private static final int AUDIT_FAILURE_THRESHOLD = 10;
    private static final double POLICY_VIOLATION_THRESHOLD = 0.05;
    private static final int CONSENT_VIOLATION_THRESHOLD = 5;
    private static final int REGULATORY_BREACH_THRESHOLD = 1;
    private static final int DATA_RESIDENCY_VIOLATION_THRESHOLD = 3;
    private static final double CONTROL_EFFECTIVENESS_THRESHOLD = 0.85;
    private static final int REPORTING_DELAY_THRESHOLD_HOURS = 24;
    private static final int TRAINING_COMPLIANCE_THRESHOLD_DAYS = 90;
    private static final double RISK_ASSESSMENT_THRESHOLD = 0.75;
    private static final int ANALYSIS_WINDOW_MINUTES = 30;
    
    private final ComplianceEventRepository eventRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final RegulatoryReportingService reportingService;
    private final ComplianceAssessmentService assessmentService;
    private final PolicyEnforcementService policyService;
    private final AuditManagementService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, ComplianceMonitoringState> complianceStates = new ConcurrentHashMap<>();
    private final Map<String, RegulationTracker> regulationTrackers = new ConcurrentHashMap<>();
    private final Map<String, DataGovernanceMonitor> dataMonitors = new ConcurrentHashMap<>();
    private final Map<String, PrivacyComplianceChecker> privacyCheckers = new ConcurrentHashMap<>();
    private final Map<String, AuditTrailAnalyzer> auditAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, PolicyComplianceMonitor> policyMonitors = new ConcurrentHashMap<>();
    private final Map<String, ConsentManagementTracker> consentTrackers = new ConcurrentHashMap<>();
    private final Map<String, ControlAssessment> controlAssessments = new ConcurrentHashMap<>();
    private final Map<String, ReportingObligationTracker> reportingTrackers = new ConcurrentHashMap<>();
    private final Map<String, TrainingComplianceMonitor> trainingMonitors = new ConcurrentHashMap<>();
    private final Map<String, RiskComplianceAnalyzer> riskAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, ComplianceViolation> activeViolations = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<ComplianceEventData> eventQueue = new LinkedBlockingQueue<>(10000);
    
    public ComplianceMonitoringConsumer(ComplianceEventRepository eventRepository,
                                      AlertService alertService,
                                      MetricsService metricsService,
                                      NotificationService notificationService,
                                      RegulatoryReportingService reportingService,
                                      ComplianceAssessmentService assessmentService,
                                      PolicyEnforcementService policyService,
                                      AuditManagementService auditService,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.alertService = alertService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.reportingService = reportingService;
        this.assessmentService = assessmentService;
        this.policyService = policyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter violationCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge complianceScoreGauge;
    private Gauge violationGauge;
    private Gauge riskLevelGauge;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        initializeMonitors();
        loadComplianceRequirements();
        establishBaselines();
        log.info("ComplianceMonitoringConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("compliance.monitoring.events.processed");
        errorCounter = meterRegistry.counter("compliance.monitoring.events.errors");
        violationCounter = meterRegistry.counter("compliance.monitoring.violations.detected");
        processingTimer = meterRegistry.timer("compliance.monitoring.processing.time");
        queueSizeGauge = meterRegistry.gauge("compliance.monitoring.queue.size", eventQueue, Queue::size);
        
        complianceScoreGauge = meterRegistry.gauge("compliance.monitoring.score", 
            complianceStates, states -> calculateOverallComplianceScore(states));
        violationGauge = meterRegistry.gauge("compliance.monitoring.violations.active",
            activeViolations, violations -> violations.size());
        riskLevelGauge = meterRegistry.gauge("compliance.monitoring.risk.level",
            riskAnalyzers, analyzers -> calculateAverageRiskLevel(analyzers));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::performComplianceAssessment, 5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::checkRegulatoryDeadlines, 1, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::validateControls, 10, 10, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::generateComplianceReports, 1, 24, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
    }
    
    private void initializeMonitors() {
        Arrays.asList("PCI-DSS", "SOC2", "GDPR", "ISO27001", "HIPAA").forEach(regulation -> {
            regulationTrackers.put(regulation, new RegulationTracker(regulation));
            dataMonitors.put(regulation, new DataGovernanceMonitor(regulation));
            privacyCheckers.put(regulation, new PrivacyComplianceChecker(regulation));
            auditAnalyzers.put(regulation, new AuditTrailAnalyzer(regulation));
            policyMonitors.put(regulation, new PolicyComplianceMonitor(regulation));
            consentTrackers.put(regulation, new ConsentManagementTracker(regulation));
            controlAssessments.put(regulation, new ControlAssessment(regulation));
            reportingTrackers.put(regulation, new ReportingObligationTracker(regulation));
            trainingMonitors.put(regulation, new TrainingComplianceMonitor(regulation));
            riskAnalyzers.put(regulation, new RiskComplianceAnalyzer(regulation));
            complianceStates.put(regulation, new ComplianceMonitoringState(regulation));
        });
    }
    
    private void loadComplianceRequirements() {
        try {
            assessmentService.loadRegulatoryRequirements();
            policyService.loadCompliancePolicies();
            log.info("Loaded compliance requirements and policies");
        } catch (Exception e) {
            log.error("Error loading compliance requirements: {}", e.getMessage(), e);
        }
    }
    
    private void establishBaselines() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        eventRepository.findByTimestampAfter(oneMonthAgo)
            .forEach(event -> {
                String regulation = event.getRegulation();
                ComplianceMonitoringState state = complianceStates.get(regulation);
                if (state != null) {
                    state.updateBaseline(event);
                }
            });
        log.info("Established compliance baselines for {} regulations", complianceStates.size());
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "complianceMonitoring", fallbackMethod = "handleMessageFallback")
    @Retry(name = "complianceMonitoring", fallbackMethod = "handleMessageFallback")
    public void consume(
            @Payload ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("traceId", UUID.randomUUID().toString());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Processing compliance monitoring event from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing compliance monitoring event: {}", e.getMessage(), e);
            errorCounter.increment();
            handleProcessingError(record, e, acknowledgment);
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "REGULATION_ASSESSMENT":
                    processRegulationAssessment(eventData);
                    break;
                case "DATA_GOVERNANCE":
                    processDataGovernance(eventData);
                    break;
                case "PRIVACY_COMPLIANCE":
                    processPrivacyCompliance(eventData);
                    break;
                case "AUDIT_TRAIL":
                    processAuditTrail(eventData);
                    break;
                case "POLICY_VIOLATION":
                    processPolicyViolation(eventData);
                    break;
                case "CONSENT_MANAGEMENT":
                    processConsentManagement(eventData);
                    break;
                case "REGULATORY_BREACH":
                    processRegulatoryBreach(eventData);
                    break;
                case "DATA_RETENTION":
                    processDataRetention(eventData);
                    break;
                case "DATA_RESIDENCY":
                    processDataResidency(eventData);
                    break;
                case "CONTROL_ASSESSMENT":
                    processControlAssessment(eventData);
                    break;
                case "REPORTING_OBLIGATION":
                    processReportingObligation(eventData);
                    break;
                case "TRAINING_COMPLIANCE":
                    processTrainingCompliance(eventData);
                    break;
                case "RISK_ASSESSMENT":
                    processRiskAssessment(eventData);
                    break;
                case "CERTIFICATION_STATUS":
                    processCertificationStatus(eventData);
                    break;
                case "COMPLIANCE_CHANGE":
                    processComplianceChange(eventData);
                    break;
                default:
                    log.warn("Unknown compliance monitoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processRegulationAssessment(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String assessmentType = eventData.get("assessmentType").asText();
        double complianceScore = eventData.get("complianceScore").asDouble();
        JsonNode findings = eventData.get("findings");
        JsonNode gaps = eventData.get("gaps");
        int criticalGaps = eventData.get("criticalGaps").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        RegulationTracker tracker = regulationTrackers.get(regulation);
        if (tracker != null) {
            tracker.recordAssessment(assessmentType, complianceScore, findings, gaps, 
                                    criticalGaps, timestamp);
            
            double threshold = getRegulationThreshold(regulation);
            if (complianceScore < threshold) {
                String message = String.format("%s compliance below threshold: %.2f%% (required: %.2f%%)", 
                    regulation, complianceScore * 100, threshold * 100);
                
                ComplianceViolation violation = createComplianceViolation("REGULATION_NONCOMPLIANCE", "HIGH", 
                    message, Map.of("regulation", regulation, "complianceScore", complianceScore, 
                                   "criticalGaps", criticalGaps));
                
                handleComplianceViolation(violation);
                generateRemediationPlan(regulation, gaps, findings);
            }
            
            if (criticalGaps > 0) {
                prioritizeCriticalGaps(regulation, gaps);
            }
        }
        
        assessmentService.processAssessment(regulation, assessmentType, complianceScore, findings);
        
        updateComplianceState(regulation, state -> {
            state.updateComplianceScore(complianceScore);
            state.recordAssessment(assessmentType, criticalGaps);
        });
        
        metricsService.recordRegulationAssessment(regulation, assessmentType, complianceScore);
        
        ComplianceEvent event = ComplianceEvent.builder()
            .regulation(regulation)
            .eventType("ASSESSMENT")
            .complianceScore(complianceScore)
            .criticalIssues(criticalGaps)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        eventRepository.save(event);
    }
    
    private void processDataGovernance(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String dataCategory = eventData.get("dataCategory").asText();
        String governanceIssue = eventData.get("governanceIssue").asText();
        String dataLocation = eventData.get("dataLocation").asText();
        boolean encrypted = eventData.get("encrypted").asBoolean();
        boolean compliant = eventData.get("compliant").asBoolean();
        JsonNode violations = eventData.get("violations");
        long timestamp = eventData.get("timestamp").asLong();
        
        DataGovernanceMonitor monitor = dataMonitors.get(regulation);
        if (monitor != null) {
            monitor.recordGovernanceEvent(dataCategory, governanceIssue, dataLocation, 
                                        encrypted, compliant, violations, timestamp);
            
            if (!compliant) {
                String message = String.format("Data governance violation for %s: %s in %s", 
                    dataCategory, governanceIssue, dataLocation);
                
                alertService.createAlert("DATA_GOVERNANCE_VIOLATION", "HIGH", message,
                    Map.of("regulation", regulation, "dataCategory", dataCategory, 
                           "governanceIssue", governanceIssue, "dataLocation", dataLocation));
                
                enforceDataGovernance(regulation, dataCategory, dataLocation, violations);
            }
            
            if (!encrypted && isSensitiveData(dataCategory)) {
                handleUnencryptedSensitiveData(regulation, dataCategory, dataLocation);
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordDataGovernanceEvent(dataCategory, compliant);
        });
        
        metricsService.recordDataGovernance(regulation, dataCategory, compliant);
    }
    
    private void processPrivacyCompliance(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String privacyRight = eventData.get("privacyRight").asText();
        String userId = eventData.get("userId").asText();
        String requestType = eventData.get("requestType").asText();
        boolean fulfilled = eventData.get("fulfilled").asBoolean();
        long responseTimeHours = eventData.get("responseTimeHours").asLong();
        long maxAllowedHours = eventData.get("maxAllowedHours").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        PrivacyComplianceChecker checker = privacyCheckers.get(regulation);
        if (checker != null) {
            checker.recordPrivacyRequest(privacyRight, userId, requestType, fulfilled, 
                                        responseTimeHours, maxAllowedHours, timestamp);
            
            if (!fulfilled) {
                handlePrivacyViolation(regulation, privacyRight, userId, requestType);
            }
            
            if (responseTimeHours > maxAllowedHours) {
                String message = String.format("Privacy request response time violation: %d hours (max: %d hours)", 
                    responseTimeHours, maxAllowedHours);
                
                alertService.createAlert("PRIVACY_RESPONSE_VIOLATION", "MEDIUM", message,
                    Map.of("regulation", regulation, "privacyRight", privacyRight, 
                           "responseTimeHours", responseTimeHours));
            }
            
            int violationCount = checker.getViolationCount(privacyRight);
            if (violationCount >= PRIVACY_VIOLATION_THRESHOLD) {
                escalatePrivacyIssue(regulation, privacyRight, violationCount);
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordPrivacyCompliance(privacyRight, fulfilled);
        });
        
        metricsService.recordPrivacyCompliance(regulation, privacyRight, fulfilled);
    }
    
    private void processAuditTrail(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String auditType = eventData.get("auditType").asText();
        String component = eventData.get("component").asText();
        boolean complete = eventData.get("complete").asBoolean();
        boolean tampered = eventData.get("tampered").asBoolean();
        int missingRecords = eventData.get("missingRecords").asInt();
        JsonNode auditFindings = eventData.get("auditFindings");
        long timestamp = eventData.get("timestamp").asLong();
        
        AuditTrailAnalyzer analyzer = auditAnalyzers.get(regulation);
        if (analyzer != null) {
            analyzer.analyzeAuditTrail(auditType, component, complete, tampered, 
                                      missingRecords, auditFindings, timestamp);
            
            if (tampered) {
                String message = String.format("Audit trail tampering detected in %s for %s", 
                    component, regulation);
                
                ComplianceViolation violation = createComplianceViolation("AUDIT_TAMPERING", "CRITICAL", 
                    message, Map.of("regulation", regulation, "component", component));
                
                handleComplianceViolation(violation);
                investigateAuditTampering(regulation, component, auditFindings);
            }
            
            if (missingRecords > 0) {
                handleMissingAuditRecords(regulation, component, missingRecords);
            }
            
            int failureCount = analyzer.getAuditFailureCount(component);
            if (failureCount >= AUDIT_FAILURE_THRESHOLD) {
                escalateAuditIssue(regulation, component, failureCount);
            }
        }
        
        auditService.processAuditFindings(regulation, auditType, component, auditFindings);
        
        updateComplianceState(regulation, state -> {
            state.recordAuditEvent(auditType, complete, tampered);
        });
        
        metricsService.recordAuditTrail(regulation, auditType, complete, tampered);
    }
    
    private void processPolicyViolation(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String policyId = eventData.get("policyId").asText();
        String policyName = eventData.get("policyName").asText();
        String violationType = eventData.get("violationType").asText();
        String violator = eventData.get("violator").asText();
        int severity = eventData.get("severity").asInt();
        JsonNode violationDetails = eventData.get("violationDetails");
        long timestamp = eventData.get("timestamp").asLong();
        
        PolicyComplianceMonitor monitor = policyMonitors.get(regulation);
        if (monitor != null) {
            monitor.recordViolation(policyId, policyName, violationType, violator, 
                                  severity, violationDetails, timestamp);
            
            double violationRate = monitor.getViolationRate(policyId);
            if (violationRate > POLICY_VIOLATION_THRESHOLD) {
                String message = String.format("High policy violation rate for %s: %.2f%%", 
                    policyName, violationRate * 100);
                
                alertService.createAlert("HIGH_POLICY_VIOLATION_RATE", "HIGH", message,
                    Map.of("regulation", regulation, "policyId", policyId, 
                           "violationRate", violationRate));
                
                reviewPolicyEffectiveness(regulation, policyId, policyName, violationRate);
            }
            
            if (severity >= 8) {
                handleCriticalPolicyViolation(regulation, policyId, violator, violationDetails);
            }
        }
        
        policyService.enforcePolicy(policyId, violator, violationType, violationDetails);
        
        updateComplianceState(regulation, state -> {
            state.recordPolicyViolation(policyId, severity);
        });
        
        metricsService.recordPolicyViolation(regulation, policyId, violationType, severity);
    }
    
    private void processConsentManagement(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String userId = eventData.get("userId").asText();
        String consentType = eventData.get("consentType").asText();
        String action = eventData.get("action").asText();
        boolean valid = eventData.get("valid").asBoolean();
        boolean documented = eventData.get("documented").asBoolean();
        JsonNode consentDetails = eventData.get("consentDetails");
        long timestamp = eventData.get("timestamp").asLong();
        
        ConsentManagementTracker tracker = consentTrackers.get(regulation);
        if (tracker != null) {
            tracker.recordConsentEvent(userId, consentType, action, valid, 
                                      documented, consentDetails, timestamp);
            
            if (!valid || !documented) {
                String message = String.format("Invalid consent for user %s: %s (%s)", 
                    userId, consentType, action);
                
                alertService.createAlert("CONSENT_VIOLATION", "HIGH", message,
                    Map.of("regulation", regulation, "userId", userId, 
                           "consentType", consentType, "valid", valid));
                
                handleConsentViolation(regulation, userId, consentType, consentDetails);
            }
            
            int violationCount = tracker.getConsentViolationCount(userId);
            if (violationCount >= CONSENT_VIOLATION_THRESHOLD) {
                suspendDataProcessing(regulation, userId, consentType);
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordConsentEvent(consentType, valid && documented);
        });
        
        metricsService.recordConsentManagement(regulation, consentType, action, valid);
    }
    
    private void processRegulatoryBreach(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String breachType = eventData.get("breachType").asText();
        String description = eventData.get("description").asText();
        int affectedRecords = eventData.get("affectedRecords").asInt();
        double financialImpact = eventData.get("financialImpact").asDouble();
        boolean reported = eventData.get("reported").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        String message = String.format("Regulatory breach detected: %s - %s affecting %d records", 
            regulation, breachType, affectedRecords);
        
        ComplianceViolation violation = createComplianceViolation("REGULATORY_BREACH", "CRITICAL", 
            message, Map.of("regulation", regulation, "breachType", breachType, 
                           "affectedRecords", affectedRecords, "financialImpact", financialImpact));
        
        handleComplianceViolation(violation);
        
        if (!reported) {
            initiateBreachNotification(regulation, breachType, description, affectedRecords);
        }
        
        reportingService.createBreachReport(regulation, breachType, description, 
                                           affectedRecords, financialImpact);
        
        updateComplianceState(regulation, state -> {
            state.recordRegulatoryBreach(breachType, affectedRecords);
        });
        
        metricsService.recordRegulatoryBreach(regulation, breachType, affectedRecords);
    }
    
    private void processDataRetention(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String dataType = eventData.get("dataType").asText();
        int retentionDays = eventData.get("retentionDays").asInt();
        int requiredDays = eventData.get("requiredDays").asInt();
        int overdueRecords = eventData.get("overdueRecords").asInt();
        boolean compliant = eventData.get("compliant").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        DataGovernanceMonitor monitor = dataMonitors.get(regulation);
        if (monitor != null) {
            monitor.recordRetentionStatus(dataType, retentionDays, requiredDays, 
                                        overdueRecords, compliant, timestamp);
            
            if (!compliant) {
                String message = String.format("Data retention violation for %s: %d days (required: %d days)", 
                    dataType, retentionDays, requiredDays);
                
                alertService.createAlert("DATA_RETENTION_VIOLATION", "HIGH", message,
                    Map.of("regulation", regulation, "dataType", dataType, 
                           "retentionDays", retentionDays, "overdueRecords", overdueRecords));
                
                if (overdueRecords > DATA_RETENTION_VIOLATION_THRESHOLD) {
                    initiateDataPurge(regulation, dataType, overdueRecords);
                }
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordDataRetention(dataType, compliant);
        });
        
        metricsService.recordDataRetention(regulation, dataType, compliant);
    }
    
    private void processDataResidency(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String dataType = eventData.get("dataType").asText();
        String currentLocation = eventData.get("currentLocation").asText();
        String requiredLocation = eventData.get("requiredLocation").asText();
        boolean compliant = eventData.get("compliant").asBoolean();
        int violatingRecords = eventData.get("violatingRecords").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        DataGovernanceMonitor monitor = dataMonitors.get(regulation);
        if (monitor != null) {
            monitor.recordResidencyStatus(dataType, currentLocation, requiredLocation, 
                                        compliant, violatingRecords, timestamp);
            
            if (!compliant) {
                String message = String.format("Data residency violation: %s data in %s (required: %s)", 
                    dataType, currentLocation, requiredLocation);
                
                alertService.createAlert("DATA_RESIDENCY_VIOLATION", "HIGH", message,
                    Map.of("regulation", regulation, "dataType", dataType, 
                           "currentLocation", currentLocation, "requiredLocation", requiredLocation));
                
                if (violatingRecords >= DATA_RESIDENCY_VIOLATION_THRESHOLD) {
                    initiateDataMigration(regulation, dataType, currentLocation, requiredLocation);
                }
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordDataResidency(dataType, compliant);
        });
        
        metricsService.recordDataResidency(regulation, dataType, compliant);
    }
    
    private void processControlAssessment(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String controlId = eventData.get("controlId").asText();
        String controlName = eventData.get("controlName").asText();
        double effectiveness = eventData.get("effectiveness").asDouble();
        String status = eventData.get("status").asText();
        JsonNode deficiencies = eventData.get("deficiencies");
        long timestamp = eventData.get("timestamp").asLong();
        
        ControlAssessment assessment = controlAssessments.get(regulation);
        if (assessment != null) {
            assessment.recordAssessment(controlId, controlName, effectiveness, 
                                      status, deficiencies, timestamp);
            
            if (effectiveness < CONTROL_EFFECTIVENESS_THRESHOLD) {
                String message = String.format("Control ineffective for %s: %s at %.2f%% effectiveness", 
                    regulation, controlName, effectiveness * 100);
                
                alertService.createAlert("INEFFECTIVE_CONTROL", "MEDIUM", message,
                    Map.of("regulation", regulation, "controlId", controlId, 
                           "effectiveness", effectiveness));
                
                strengthenControl(regulation, controlId, controlName, deficiencies);
            }
            
            if ("FAILED".equals(status)) {
                handleControlFailure(regulation, controlId, controlName);
            }
        }
        
        assessmentService.evaluateControl(regulation, controlId, effectiveness, deficiencies);
        
        updateComplianceState(regulation, state -> {
            state.recordControlAssessment(controlId, effectiveness);
        });
        
        metricsService.recordControlAssessment(regulation, controlId, effectiveness);
    }
    
    private void processReportingObligation(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String reportType = eventData.get("reportType").asText();
        String reportId = eventData.get("reportId").asText();
        long dueDate = eventData.get("dueDate").asLong();
        long submittedDate = eventData.get("submittedDate").asLong();
        boolean submitted = eventData.get("submitted").asBoolean();
        boolean onTime = eventData.get("onTime").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        ReportingObligationTracker tracker = reportingTrackers.get(regulation);
        if (tracker != null) {
            tracker.recordReportingEvent(reportType, reportId, dueDate, submittedDate, 
                                        submitted, onTime, timestamp);
            
            if (!submitted && Instant.now().toEpochMilli() > dueDate) {
                String message = String.format("Overdue regulatory report: %s for %s", 
                    reportType, regulation);
                
                ComplianceViolation violation = createComplianceViolation("REPORTING_VIOLATION", "HIGH", 
                    message, Map.of("regulation", regulation, "reportType", reportType));
                
                handleComplianceViolation(violation);
                expediteReporting(regulation, reportType, reportId);
            }
            
            if (!onTime) {
                long delayHours = Duration.between(
                    Instant.ofEpochMilli(dueDate),
                    Instant.ofEpochMilli(submittedDate)
                ).toHours();
                
                if (delayHours > REPORTING_DELAY_THRESHOLD_HOURS) {
                    handleReportingDelay(regulation, reportType, delayHours);
                }
            }
        }
        
        reportingService.trackReportingObligation(regulation, reportType, submitted, onTime);
        
        updateComplianceState(regulation, state -> {
            state.recordReportingObligation(reportType, submitted && onTime);
        });
        
        metricsService.recordReportingObligation(regulation, reportType, submitted, onTime);
    }
    
    private void processTrainingCompliance(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String trainingType = eventData.get("trainingType").asText();
        String employeeId = eventData.get("employeeId").asText();
        boolean completed = eventData.get("completed").asBoolean();
        int daysSinceLastTraining = eventData.get("daysSinceLastTraining").asInt();
        double scorePercentage = eventData.get("scorePercentage").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        TrainingComplianceMonitor monitor = trainingMonitors.get(regulation);
        if (monitor != null) {
            monitor.recordTrainingEvent(trainingType, employeeId, completed, 
                                      daysSinceLastTraining, scorePercentage, timestamp);
            
            if (!completed && daysSinceLastTraining > TRAINING_COMPLIANCE_THRESHOLD_DAYS) {
                String message = String.format("Training compliance violation for %s: %d days overdue", 
                    employeeId, daysSinceLastTraining - TRAINING_COMPLIANCE_THRESHOLD_DAYS);
                
                alertService.createAlert("TRAINING_OVERDUE", "MEDIUM", message,
                    Map.of("regulation", regulation, "trainingType", trainingType, 
                           "employeeId", employeeId, "daysSinceLastTraining", daysSinceLastTraining));
                
                scheduleTraining(regulation, trainingType, employeeId);
            }
            
            double complianceRate = monitor.getTrainingComplianceRate(trainingType);
            if (complianceRate < 0.90) {
                handleLowTrainingCompliance(regulation, trainingType, complianceRate);
            }
        }
        
        updateComplianceState(regulation, state -> {
            state.recordTrainingCompliance(trainingType, completed);
        });
        
        metricsService.recordTrainingCompliance(regulation, trainingType, completed);
    }
    
    private void processRiskAssessment(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String riskCategory = eventData.get("riskCategory").asText();
        double riskScore = eventData.get("riskScore").asDouble();
        String riskLevel = eventData.get("riskLevel").asText();
        JsonNode riskFactors = eventData.get("riskFactors");
        JsonNode mitigations = eventData.get("mitigations");
        long timestamp = eventData.get("timestamp").asLong();
        
        RiskComplianceAnalyzer analyzer = riskAnalyzers.get(regulation);
        if (analyzer != null) {
            analyzer.assessRisk(riskCategory, riskScore, riskLevel, riskFactors, 
                              mitigations, timestamp);
            
            if (riskScore > RISK_ASSESSMENT_THRESHOLD) {
                String message = String.format("High compliance risk for %s: %s at %.2f", 
                    regulation, riskCategory, riskScore);
                
                alertService.createAlert("HIGH_COMPLIANCE_RISK", "HIGH", message,
                    Map.of("regulation", regulation, "riskCategory", riskCategory, 
                           "riskScore", riskScore, "riskLevel", riskLevel));
                
                implementRiskMitigation(regulation, riskCategory, riskFactors, mitigations);
            }
            
            analyzeRiskTrends(analyzer, regulation, riskCategory);
        }
        
        assessmentService.processRiskAssessment(regulation, riskCategory, riskScore, riskFactors);
        
        updateComplianceState(regulation, state -> {
            state.recordRiskAssessment(riskCategory, riskScore);
        });
        
        metricsService.recordRiskAssessment(regulation, riskCategory, riskScore);
    }
    
    private void processCertificationStatus(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String certificationName = eventData.get("certificationName").asText();
        String status = eventData.get("status").asText();
        long expiryDate = eventData.get("expiryDate").asLong();
        boolean valid = eventData.get("valid").asBoolean();
        JsonNode auditResults = eventData.get("auditResults");
        long timestamp = eventData.get("timestamp").asLong();
        
        long daysUntilExpiry = Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(expiryDate)
        ).toDays();
        
        if (daysUntilExpiry <= 90 && daysUntilExpiry > 0) {
            String message = String.format("Certification %s expires in %d days", 
                certificationName, daysUntilExpiry);
            
            alertService.createAlert("CERTIFICATION_EXPIRY_WARNING", "WARNING", message,
                Map.of("regulation", regulation, "certificationName", certificationName, 
                       "daysUntilExpiry", daysUntilExpiry));
            
            initiateCertificationRenewal(regulation, certificationName);
        } else if (daysUntilExpiry <= 0) {
            handleExpiredCertification(regulation, certificationName);
        }
        
        if (!valid) {
            handleInvalidCertification(regulation, certificationName, auditResults);
        }
        
        updateComplianceState(regulation, state -> {
            state.updateCertificationStatus(certificationName, status, valid);
        });
        
        metricsService.recordCertificationStatus(regulation, certificationName, valid);
    }
    
    private void processComplianceChange(JsonNode eventData) {
        String regulation = eventData.get("regulation").asText();
        String changeType = eventData.get("changeType").asText();
        String description = eventData.get("description").asText();
        long effectiveDate = eventData.get("effectiveDate").asLong();
        JsonNode impactedAreas = eventData.get("impactedAreas");
        JsonNode requiredActions = eventData.get("requiredActions");
        long timestamp = eventData.get("timestamp").asLong();
        
        long daysUntilEffective = Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(effectiveDate)
        ).toDays();
        
        String message = String.format("Compliance change for %s: %s effective in %d days", 
            regulation, changeType, daysUntilEffective);
        
        notificationService.notifyComplianceTeam(message, Map.of(
            "regulation", regulation,
            "changeType", changeType,
            "effectiveDate", effectiveDate
        ));
        
        prepareForComplianceChange(regulation, changeType, impactedAreas, requiredActions);
        
        updateComplianceState(regulation, state -> {
            state.recordComplianceChange(changeType, effectiveDate);
        });
        
        metricsService.recordComplianceChange(regulation, changeType);
    }
    
    private void updateComplianceState(String regulation, java.util.function.Consumer<ComplianceMonitoringState> updater) {
        complianceStates.computeIfAbsent(regulation, k -> new ComplianceMonitoringState(regulation))
                        .update(updater);
    }
    
    private ComplianceViolation createComplianceViolation(String type, String severity, String description, Map<String, Object> details) {
        String violationId = UUID.randomUUID().toString();
        ComplianceViolation violation = new ComplianceViolation(violationId, type, severity, description, details);
        activeViolations.put(violationId, violation);
        
        violationCounter.increment();
        alertService.createAlert(type, severity, description, details);
        
        return violation;
    }
    
    private double getRegulationThreshold(String regulation) {
        switch (regulation) {
            case "PCI-DSS": return PCI_DSS_THRESHOLD;
            case "SOC2": return SOC2_THRESHOLD;
            case "GDPR": return GDPR_THRESHOLD;
            case "ISO27001": return ISO27001_THRESHOLD;
            default: return 0.90;
        }
    }
    
    private void handleComplianceViolation(ComplianceViolation violation) {
        reportingService.reportViolation(violation);
        assessmentService.investigateViolation(violation);
        
        if ("CRITICAL".equals(violation.getSeverity())) {
            escalateViolation(violation);
        }
    }
    
    private void generateRemediationPlan(String regulation, JsonNode gaps, JsonNode findings) {
        Map<String, Object> plan = assessmentService.createRemediationPlan(regulation, gaps, findings);
        policyService.implementRemediationActions(plan);
    }
    
    private void prioritizeCriticalGaps(String regulation, JsonNode gaps) {
        List<Map<String, Object>> prioritizedGaps = assessmentService.prioritizeGaps(regulation, gaps);
        prioritizedGaps.forEach(gap -> policyService.addressCriticalGap(regulation, gap));
    }
    
    private void enforceDataGovernance(String regulation, String dataCategory, String dataLocation, JsonNode violations) {
        policyService.enforceDataGovernance(regulation, dataCategory, dataLocation, violations);
    }
    
    private boolean isSensitiveData(String dataCategory) {
        return Arrays.asList("PII", "PHI", "FINANCIAL", "CREDENTIALS").contains(dataCategory.toUpperCase());
    }
    
    private void handleUnencryptedSensitiveData(String regulation, String dataCategory, String dataLocation) {
        policyService.encryptSensitiveData(regulation, dataCategory, dataLocation);
    }
    
    private void handlePrivacyViolation(String regulation, String privacyRight, String userId, String requestType) {
        ComplianceViolation violation = createComplianceViolation("PRIVACY_VIOLATION", "HIGH",
            String.format("Privacy violation for user %s: %s request not fulfilled", userId, requestType),
            Map.of("regulation", regulation, "privacyRight", privacyRight, "userId", userId));
        
        handleComplianceViolation(violation);
    }
    
    private void escalatePrivacyIssue(String regulation, String privacyRight, int violationCount) {
        reportingService.escalatePrivacyIssue(regulation, privacyRight, violationCount);
    }
    
    private void investigateAuditTampering(String regulation, String component, JsonNode findings) {
        auditService.investigateTampering(regulation, component, findings);
        policyService.strengthenAuditControls(regulation, component);
    }
    
    private void handleMissingAuditRecords(String regulation, String component, int missingRecords) {
        auditService.reconstructAuditTrail(regulation, component, missingRecords);
    }
    
    private void escalateAuditIssue(String regulation, String component, int failureCount) {
        reportingService.escalateAuditFailure(regulation, component, failureCount);
    }
    
    private void reviewPolicyEffectiveness(String regulation, String policyId, String policyName, double violationRate) {
        policyService.reviewPolicy(policyId, policyName, violationRate);
    }
    
    private void handleCriticalPolicyViolation(String regulation, String policyId, String violator, JsonNode details) {
        policyService.handleCriticalViolation(regulation, policyId, violator, details);
    }
    
    private void handleConsentViolation(String regulation, String userId, String consentType, JsonNode details) {
        policyService.suspendProcessing(userId, consentType);
        reportingService.reportConsentViolation(regulation, userId, consentType, details);
    }
    
    private void suspendDataProcessing(String regulation, String userId, String consentType) {
        policyService.suspendUserDataProcessing(regulation, userId, consentType);
    }
    
    private void initiateBreachNotification(String regulation, String breachType, String description, int affectedRecords) {
        reportingService.initiateBreachNotification(regulation, breachType, description, affectedRecords);
    }
    
    private void initiateDataPurge(String regulation, String dataType, int overdueRecords) {
        policyService.purgeOverdueData(regulation, dataType, overdueRecords);
    }
    
    private void initiateDataMigration(String regulation, String dataType, String currentLocation, String requiredLocation) {
        policyService.migrateData(regulation, dataType, currentLocation, requiredLocation);
    }
    
    private void strengthenControl(String regulation, String controlId, String controlName, JsonNode deficiencies) {
        assessmentService.strengthenControl(regulation, controlId, controlName, deficiencies);
    }
    
    private void handleControlFailure(String regulation, String controlId, String controlName) {
        assessmentService.handleControlFailure(regulation, controlId, controlName);
    }
    
    private void expediteReporting(String regulation, String reportType, String reportId) {
        reportingService.expediteReport(regulation, reportType, reportId);
    }
    
    private void handleReportingDelay(String regulation, String reportType, long delayHours) {
        reportingService.handleDelay(regulation, reportType, delayHours);
    }
    
    private void scheduleTraining(String regulation, String trainingType, String employeeId) {
        policyService.scheduleComplianceTraining(regulation, trainingType, employeeId);
    }
    
    private void handleLowTrainingCompliance(String regulation, String trainingType, double complianceRate) {
        policyService.enforceTrainingRequirements(regulation, trainingType, complianceRate);
    }
    
    private void implementRiskMitigation(String regulation, String riskCategory, JsonNode riskFactors, JsonNode mitigations) {
        assessmentService.implementMitigations(regulation, riskCategory, riskFactors, mitigations);
    }
    
    private void analyzeRiskTrends(RiskComplianceAnalyzer analyzer, String regulation, String riskCategory) {
        Map<String, Object> trends = analyzer.analyzeRiskTrends(riskCategory);
        if (analyzer.isRiskIncreasing(trends)) {
            assessmentService.addressIncreasingRisk(regulation, riskCategory, trends);
        }
    }
    
    private void initiateCertificationRenewal(String regulation, String certificationName) {
        assessmentService.initiateCertificationRenewal(regulation, certificationName);
    }
    
    private void handleExpiredCertification(String regulation, String certificationName) {
        ComplianceViolation violation = createComplianceViolation("EXPIRED_CERTIFICATION", "CRITICAL",
            String.format("Certification %s has expired for %s", certificationName, regulation),
            Map.of("regulation", regulation, "certificationName", certificationName));
        
        handleComplianceViolation(violation);
    }
    
    private void handleInvalidCertification(String regulation, String certificationName, JsonNode auditResults) {
        assessmentService.addressCertificationIssues(regulation, certificationName, auditResults);
    }
    
    private void prepareForComplianceChange(String regulation, String changeType, JsonNode impactedAreas, JsonNode requiredActions) {
        assessmentService.prepareForChange(regulation, changeType, impactedAreas, requiredActions);
    }
    
    private void escalateViolation(ComplianceViolation violation) {
        reportingService.escalateToCISO(violation);
        reportingService.notifyLegalTeam(violation);
    }
    
    @Scheduled(fixedDelay = 300000)
    private void performComplianceAssessment() {
        try {
            complianceStates.forEach((regulation, state) -> {
                double score = calculateComplianceScore(state);
                
                if (score < getRegulationThreshold(regulation)) {
                    alertService.createAlert("LOW_COMPLIANCE_SCORE", "HIGH",
                        String.format("Compliance score below threshold for %s: %.2f%%", 
                            regulation, score * 100),
                        Map.of("regulation", regulation, "score", score));
                }
                
                generateComplianceMetrics(regulation, state, score);
            });
        } catch (Exception e) {
            log.error("Error performing compliance assessment: {}", e.getMessage(), e);
        }
    }
    
    private double calculateComplianceScore(ComplianceMonitoringState state) {
        double assessmentScore = state.getComplianceScore();
        double policyScore = 1.0 - state.getPolicyViolationRate();
        double auditScore = state.getAuditCompletionRate();
        double trainingScore = state.getTrainingComplianceRate();
        double controlScore = state.getControlEffectiveness();
        
        return (assessmentScore + policyScore + auditScore + trainingScore + controlScore) / 5.0;
    }
    
    private void generateComplianceMetrics(String regulation, ComplianceMonitoringState state, double score) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("regulation", regulation);
        metrics.put("overallScore", score);
        metrics.put("violations", state.getViolationCount());
        metrics.put("criticalGaps", state.getCriticalGapCount());
        metrics.put("controlEffectiveness", state.getControlEffectiveness());
        
        metricsService.recordComplianceMetrics(metrics);
    }
    
    @Scheduled(fixedDelay = 3600000)
    private void checkRegulatoryDeadlines() {
        try {
            reportingTrackers.forEach((regulation, tracker) -> {
                List<Map<String, Object>> upcomingDeadlines = tracker.getUpcomingDeadlines();
                upcomingDeadlines.forEach(deadline -> {
                    notificationService.notifyUpcomingDeadline(regulation, deadline);
                });
            });
        } catch (Exception e) {
            log.error("Error checking regulatory deadlines: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 600000)
    private void validateControls() {
        try {
            controlAssessments.forEach((regulation, assessment) -> {
                Map<String, Double> controlScores = assessment.validateAllControls();
                controlScores.forEach((controlId, effectiveness) -> {
                    if (effectiveness < CONTROL_EFFECTIVENESS_THRESHOLD) {
                        assessmentService.remediateControl(regulation, controlId, effectiveness);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error validating controls: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 86400000)
    private void generateComplianceReports() {
        try {
            Map<String, Double> regulationScores = new HashMap<>();
            
            complianceStates.forEach((regulation, state) -> {
                double score = calculateComplianceScore(state);
                regulationScores.put(regulation, score);
            });
            
            Map<String, Object> report = reportingService.generateComplianceReport(
                regulationScores, activeViolations);
            
            notificationService.sendComplianceReport(report);
            
        } catch (Exception e) {
            log.error("Error generating compliance reports: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
            int deleted = eventRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old compliance events", deleted);
            
            activeViolations.entrySet().removeIf(entry -> 
                entry.getValue().isOlderThan(cutoff));
            
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process compliance monitoring event after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            Map<String, Object> errorContext = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "error", error.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
            );
            
            notificationService.notifyError("COMPLIANCE_MONITORING_PROCESSING_ERROR", errorContext);
            sendToDeadLetterQueue(record, error);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error handling processing failure: {}", e.getMessage(), e);
        }
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record, Exception error) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalTopic", record.topic(),
                "originalValue", record.value(),
                "errorMessage", error.getMessage(),
                "errorType", error.getClass().getName(),
                "timestamp", Instant.now().toEpochMilli(),
                "retryCount", MAX_RETRY_ATTEMPTS
            );
            
            log.info("Message sent to DLQ: {}", dlqMessage);
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }
    
    public void handleMessageFallback(ConsumerRecord<String, String> record, Exception ex) {
        log.error("Fallback triggered for compliance monitoring event processing", ex);
        errorCounter.increment();
    }
    
    private double calculateOverallComplianceScore(Map<String, ComplianceMonitoringState> states) {
        return states.values().stream()
            .mapToDouble(ComplianceMonitoringState::getComplianceScore)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageRiskLevel(Map<String, RiskComplianceAnalyzer> analyzers) {
        return analyzers.values().stream()
            .mapToDouble(RiskComplianceAnalyzer::getAverageRiskScore)
            .average()
            .orElse(0.0);
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down ComplianceMonitoringConsumer...");
            scheduledExecutor.shutdown();
            analysisExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
            
            log.info("ComplianceMonitoringConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}