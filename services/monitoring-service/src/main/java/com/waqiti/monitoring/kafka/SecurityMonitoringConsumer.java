package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.entity.SecurityEvent;
import com.waqiti.monitoring.repository.SecurityEventRepository;
import com.waqiti.monitoring.service.*;
import com.waqiti.monitoring.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
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
@RequiredArgsConstructor
public class SecurityMonitoringConsumer {

    private static final String TOPIC = "security-monitoring";
    private static final String GROUP_ID = "monitoring-security-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final int FAILED_LOGIN_THRESHOLD = 5;
    private static final int ACCESS_VIOLATION_THRESHOLD = 3;
    private static final double THREAT_SCORE_THRESHOLD = 0.75;
    private static final int VULNERABILITY_CRITICAL_THRESHOLD = 5;
    private static final int ENCRYPTION_FAILURE_THRESHOLD = 10;
    private static final int CERTIFICATE_EXPIRY_WARNING_DAYS = 30;
    private static final int INTRUSION_ATTEMPT_THRESHOLD = 3;
    private static final double AUDIT_ANOMALY_THRESHOLD = 0.80;
    private static final int PERMISSION_ESCALATION_THRESHOLD = 2;
    private static final int DATA_EXFILTRATION_THRESHOLD_MB = 100;
    private static final int MALWARE_DETECTION_THRESHOLD = 1;
    private static final double DDOS_TRAFFIC_THRESHOLD = 1000;
    private static final int SESSION_HIJACK_THRESHOLD = 2;
    private static final double COMPLIANCE_VIOLATION_THRESHOLD = 0.95;
    private static final int API_ABUSE_THRESHOLD = 100;
    private static final int ANALYSIS_WINDOW_MINUTES = 15;
    
    private final SecurityEventRepository eventRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ThreatIntelligenceService threatService;
    private final IncidentResponseService incidentService;
    private final VulnerabilityManagementService vulnerabilityService;
    private final SecurityOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, SecurityMonitoringState> securityStates = new ConcurrentHashMap<>();
    private final Map<String, AuthenticationTracker> authTrackers = new ConcurrentHashMap<>();
    private final Map<String, AccessControlMonitor> accessMonitors = new ConcurrentHashMap<>();
    private final Map<String, ThreatDetector> threatDetectors = new ConcurrentHashMap<>();
    private final Map<String, VulnerabilityScanner> vulnScanners = new ConcurrentHashMap<>();
    private final Map<String, EncryptionMonitor> encryptionMonitors = new ConcurrentHashMap<>();
    private final Map<String, IntrusionDetector> intrusionDetectors = new ConcurrentHashMap<>();
    private final Map<String, AuditAnalyzer> auditAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, ComplianceChecker> complianceCheckers = new ConcurrentHashMap<>();
    private final Map<String, NetworkSecurityMonitor> networkMonitors = new ConcurrentHashMap<>();
    private final Map<String, DataProtectionMonitor> dataMonitors = new ConcurrentHashMap<>();
    private final Map<String, SecurityIncident> activeIncidents = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<SecurityEventData> eventQueue = new LinkedBlockingQueue<>(10000);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter threatCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge threatLevelGauge;
    private Gauge vulnerabilityGauge;
    private Gauge complianceScoreGauge;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        initializeMonitors();
        establishBaselines();
        loadThreatIntelligence();
        log.info("SecurityMonitoringConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("security.monitoring.events.processed");
        errorCounter = meterRegistry.counter("security.monitoring.events.errors");
        threatCounter = meterRegistry.counter("security.monitoring.threats.detected");
        processingTimer = meterRegistry.timer("security.monitoring.processing.time");
        queueSizeGauge = meterRegistry.gauge("security.monitoring.queue.size", eventQueue, Queue::size);
        
        threatLevelGauge = meterRegistry.gauge("security.monitoring.threat.level", 
            threatDetectors, detectors -> calculateAverageThreatLevel(detectors));
        vulnerabilityGauge = meterRegistry.gauge("security.monitoring.vulnerability.count",
            vulnScanners, scanners -> calculateTotalVulnerabilities(scanners));
        complianceScoreGauge = meterRegistry.gauge("security.monitoring.compliance.score",
            complianceCheckers, checkers -> calculateAverageComplianceScore(checkers));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::analyzeSecurityPosture, 1, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::detectThreats, 30, 30, TimeUnit.SECONDS);
        scheduledExecutor.scheduleAtFixedRate(this::scanVulnerabilities, 5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::performComplianceChecks, 10, 10, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
    }
    
    private void initializeMonitors() {
        Arrays.asList("web", "api", "database", "network", "system").forEach(component -> {
            authTrackers.put(component, new AuthenticationTracker(component));
            accessMonitors.put(component, new AccessControlMonitor(component));
            threatDetectors.put(component, new ThreatDetector(component));
            vulnScanners.put(component, new VulnerabilityScanner(component));
            encryptionMonitors.put(component, new EncryptionMonitor(component));
            intrusionDetectors.put(component, new IntrusionDetector(component));
            auditAnalyzers.put(component, new AuditAnalyzer(component));
            complianceCheckers.put(component, new ComplianceChecker(component));
            networkMonitors.put(component, new NetworkSecurityMonitor(component));
            dataMonitors.put(component, new DataProtectionMonitor(component));
            securityStates.put(component, new SecurityMonitoringState(component));
        });
    }
    
    private void establishBaselines() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        eventRepository.findByTimestampAfter(oneWeekAgo)
            .forEach(event -> {
                String component = event.getComponent();
                SecurityMonitoringState state = securityStates.get(component);
                if (state != null) {
                    state.updateBaseline(event);
                }
            });
        log.info("Established security baselines for {} components", securityStates.size());
    }
    
    private void loadThreatIntelligence() {
        try {
            threatService.loadLatestThreatFeeds();
            threatService.updateIndicatorsOfCompromise();
            log.info("Loaded latest threat intelligence feeds");
        } catch (Exception e) {
            log.error("Error loading threat intelligence: {}", e.getMessage(), e);
        }
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "securityMonitoring", fallbackMethod = "handleMessageFallback")
    @Retry(name = "securityMonitoring", fallbackMethod = "handleMessageFallback")
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
            log.debug("Processing security monitoring event from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing security monitoring event: {}", e.getMessage(), e);
            errorCounter.increment();
            handleProcessingError(record, e, acknowledgment);
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "AUTHENTICATION_EVENT":
                    processAuthenticationEvent(eventData);
                    break;
                case "ACCESS_VIOLATION":
                    processAccessViolation(eventData);
                    break;
                case "THREAT_DETECTED":
                    processThreatDetected(eventData);
                    break;
                case "VULNERABILITY_SCAN":
                    processVulnerabilityScan(eventData);
                    break;
                case "ENCRYPTION_STATUS":
                    processEncryptionStatus(eventData);
                    break;
                case "CERTIFICATE_STATUS":
                    processCertificateStatus(eventData);
                    break;
                case "INTRUSION_ATTEMPT":
                    processIntrusionAttempt(eventData);
                    break;
                case "AUDIT_LOG":
                    processAuditLog(eventData);
                    break;
                case "PERMISSION_CHANGE":
                    processPermissionChange(eventData);
                    break;
                case "DATA_EXFILTRATION":
                    processDataExfiltration(eventData);
                    break;
                case "MALWARE_DETECTION":
                    processMalwareDetection(eventData);
                    break;
                case "DDOS_ATTACK":
                    processDdosAttack(eventData);
                    break;
                case "SESSION_ANOMALY":
                    processSessionAnomaly(eventData);
                    break;
                case "COMPLIANCE_CHECK":
                    processComplianceCheck(eventData);
                    break;
                case "API_SECURITY":
                    processApiSecurity(eventData);
                    break;
                default:
                    log.warn("Unknown security monitoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processAuthenticationEvent(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String userId = eventData.get("userId").asText();
        String authMethod = eventData.get("authMethod").asText();
        boolean success = eventData.get("success").asBoolean();
        String ipAddress = eventData.get("ipAddress").asText();
        String userAgent = eventData.get("userAgent").asText("");
        String failureReason = eventData.get("failureReason").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        AuthenticationTracker tracker = authTrackers.get(component);
        if (tracker != null) {
            tracker.recordAuthentication(userId, authMethod, success, ipAddress, 
                                        userAgent, failureReason, timestamp);
            
            if (!success) {
                int failedAttempts = tracker.getFailedAttempts(userId, ipAddress);
                if (failedAttempts >= FAILED_LOGIN_THRESHOLD) {
                    String message = String.format("Multiple failed login attempts for user %s from IP %s: %d attempts", 
                        userId, ipAddress, failedAttempts);
                    
                    SecurityIncident incident = createSecurityIncident("BRUTE_FORCE_ATTEMPT", "HIGH", message,
                        Map.of("userId", userId, "ipAddress", ipAddress, "attempts", failedAttempts));
                    
                    orchestrationService.respondToIncident(incident);
                }
                
                detectCredentialStuffing(tracker, userId, ipAddress, userAgent);
            } else {
                detectAnomalousLogin(tracker, userId, ipAddress, userAgent);
            }
        }
        
        updateSecurityState(component, state -> {
            state.recordAuthenticationEvent(userId, success);
            if (!success) {
                state.incrementFailedLogins();
            }
        });
        
        metricsService.recordAuthenticationEvent(component, userId, authMethod, success);
        
        SecurityEvent event = SecurityEvent.builder()
            .component(component)
            .eventType("AUTHENTICATION")
            .userId(userId)
            .ipAddress(ipAddress)
            .success(success)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        eventRepository.save(event);
    }
    
    private void processAccessViolation(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String userId = eventData.get("userId").asText();
        String resource = eventData.get("resource").asText();
        String action = eventData.get("action").asText();
        String violationType = eventData.get("violationType").asText();
        String ipAddress = eventData.get("ipAddress").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        AccessControlMonitor monitor = accessMonitors.get(component);
        if (monitor != null) {
            monitor.recordViolation(userId, resource, action, violationType, ipAddress, timestamp);
            
            int violationCount = monitor.getViolationCount(userId);
            if (violationCount >= ACCESS_VIOLATION_THRESHOLD) {
                String message = String.format("Multiple access violations by user %s: %d violations", 
                    userId, violationCount);
                
                SecurityIncident incident = createSecurityIncident("ACCESS_VIOLATION", "MEDIUM", message,
                    Map.of("userId", userId, "resource", resource, "violationCount", violationCount));
                
                orchestrationService.respondToIncident(incident);
                
                investigatePrivilegeEscalation(monitor, userId, resource);
            }
        }
        
        updateSecurityState(component, state -> {
            state.recordAccessViolation(userId, resource, violationType);
        });
        
        metricsService.recordAccessViolation(component, userId, resource, violationType);
    }
    
    private void processThreatDetected(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String threatType = eventData.get("threatType").asText();
        String threatName = eventData.get("threatName").asText();
        double threatScore = eventData.get("threatScore").asDouble();
        String source = eventData.get("source").asText();
        String target = eventData.get("target").asText();
        JsonNode indicators = eventData.get("indicators");
        long timestamp = eventData.get("timestamp").asLong();
        
        ThreatDetector detector = threatDetectors.get(component);
        if (detector != null) {
            detector.recordThreat(threatType, threatName, threatScore, source, target, indicators, timestamp);
            
            if (threatScore >= THREAT_SCORE_THRESHOLD) {
                String message = String.format("High severity threat detected: %s (%s) with score %.2f", 
                    threatName, threatType, threatScore);
                
                SecurityIncident incident = createSecurityIncident("THREAT_DETECTED", "CRITICAL", message,
                    Map.of("threatType", threatType, "threatName", threatName, 
                           "threatScore", threatScore, "source", source, "target", target));
                
                orchestrationService.respondToIncident(incident);
                threatService.analyzeIndicators(indicators);
                implementContainment(component, threatType, source);
            }
            
            correlateWithThreatIntel(detector, threatName, indicators);
        }
        
        threatCounter.increment();
        
        updateSecurityState(component, state -> {
            state.recordThreat(threatType, threatScore);
            state.updateThreatLevel(threatScore);
        });
        
        metricsService.recordThreatDetection(component, threatType, threatScore);
    }
    
    private void processVulnerabilityScan(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String scanType = eventData.get("scanType").asText();
        int criticalCount = eventData.get("criticalCount").asInt();
        int highCount = eventData.get("highCount").asInt();
        int mediumCount = eventData.get("mediumCount").asInt();
        int lowCount = eventData.get("lowCount").asInt();
        JsonNode vulnerabilities = eventData.get("vulnerabilities");
        long timestamp = eventData.get("timestamp").asLong();
        
        VulnerabilityScanner scanner = vulnScanners.get(component);
        if (scanner != null) {
            scanner.recordScanResults(scanType, criticalCount, highCount, mediumCount, 
                                     lowCount, vulnerabilities, timestamp);
            
            if (criticalCount >= VULNERABILITY_CRITICAL_THRESHOLD) {
                String message = String.format("Critical vulnerabilities found in %s: %d critical, %d high", 
                    component, criticalCount, highCount);
                
                alertService.createAlert("CRITICAL_VULNERABILITIES", "CRITICAL", message,
                    Map.of("component", component, "criticalCount", criticalCount, 
                           "highCount", highCount));
                
                prioritizeRemediation(scanner, component, vulnerabilities);
            }
            
            generatePatchingPlan(component, vulnerabilities);
        }
        
        vulnerabilityService.processVulnerabilities(component, vulnerabilities);
        
        updateSecurityState(component, state -> {
            state.updateVulnerabilityCounts(criticalCount, highCount, mediumCount, lowCount);
        });
        
        metricsService.recordVulnerabilityScan(component, criticalCount, highCount, mediumCount, lowCount);
    }
    
    private void processEncryptionStatus(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String encryptionType = eventData.get("encryptionType").asText();
        String algorithm = eventData.get("algorithm").asText();
        String status = eventData.get("status").asText();
        int failureCount = eventData.get("failureCount").asInt();
        String failureReason = eventData.get("failureReason").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        EncryptionMonitor monitor = encryptionMonitors.get(component);
        if (monitor != null) {
            monitor.recordEncryptionStatus(encryptionType, algorithm, status, 
                                          failureCount, failureReason, timestamp);
            
            if (failureCount >= ENCRYPTION_FAILURE_THRESHOLD) {
                String message = String.format("Encryption failures in %s: %d failures for %s", 
                    component, failureCount, encryptionType);
                
                alertService.createAlert("ENCRYPTION_FAILURES", "HIGH", message,
                    Map.of("component", component, "encryptionType", encryptionType, 
                           "failureCount", failureCount, "reason", failureReason));
                
                investigateEncryptionIssue(component, encryptionType, failureReason);
            }
            
            if ("WEAK".equals(status) || isWeakAlgorithm(algorithm)) {
                recommendStrongerEncryption(component, encryptionType, algorithm);
            }
        }
        
        updateSecurityState(component, state -> {
            state.updateEncryptionStatus(encryptionType, status, failureCount);
        });
        
        metricsService.recordEncryptionStatus(component, encryptionType, status, failureCount > 0);
    }
    
    private void processCertificateStatus(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String certificateName = eventData.get("certificateName").asText();
        String status = eventData.get("status").asText();
        long expiryTime = eventData.get("expiryTime").asLong();
        String issuer = eventData.get("issuer").asText();
        boolean isValid = eventData.get("isValid").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        long daysUntilExpiry = Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(expiryTime)
        ).toDays();
        
        if (daysUntilExpiry <= CERTIFICATE_EXPIRY_WARNING_DAYS && daysUntilExpiry > 0) {
            String message = String.format("Certificate %s expires in %d days", 
                certificateName, daysUntilExpiry);
            
            alertService.createAlert("CERTIFICATE_EXPIRY_WARNING", "WARNING", message,
                Map.of("component", component, "certificateName", certificateName, 
                       "daysUntilExpiry", daysUntilExpiry));
            
            orchestrationService.scheduleCertificateRenewal(component, certificateName);
        } else if (daysUntilExpiry <= 0) {
            handleExpiredCertificate(component, certificateName);
        }
        
        if (!isValid) {
            handleInvalidCertificate(component, certificateName, status);
        }
        
        updateSecurityState(component, state -> {
            state.updateCertificateStatus(certificateName, status, daysUntilExpiry);
        });
        
        metricsService.recordCertificateStatus(component, certificateName, isValid, daysUntilExpiry);
    }
    
    private void processIntrusionAttempt(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String attackType = eventData.get("attackType").asText();
        String sourceIp = eventData.get("sourceIp").asText();
        String targetIp = eventData.get("targetIp").asText();
        int severity = eventData.get("severity").asInt();
        JsonNode attackSignature = eventData.get("attackSignature");
        String action = eventData.get("action").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        IntrusionDetector detector = intrusionDetectors.get(component);
        if (detector != null) {
            detector.recordIntrusionAttempt(attackType, sourceIp, targetIp, severity, 
                                           attackSignature, action, timestamp);
            
            int attemptCount = detector.getAttemptCount(sourceIp, attackType);
            if (attemptCount >= INTRUSION_ATTEMPT_THRESHOLD) {
                String message = String.format("Multiple intrusion attempts from %s: %d %s attempts", 
                    sourceIp, attemptCount, attackType);
                
                SecurityIncident incident = createSecurityIncident("INTRUSION_DETECTED", "CRITICAL", message,
                    Map.of("attackType", attackType, "sourceIp", sourceIp, 
                           "targetIp", targetIp, "attemptCount", attemptCount));
                
                orchestrationService.respondToIncident(incident);
                implementNetworkBlocking(sourceIp, attackType);
            }
            
            correlateAttackPattern(detector, attackType, attackSignature);
        }
        
        updateSecurityState(component, state -> {
            state.recordIntrusionAttempt(attackType, sourceIp, severity);
        });
        
        metricsService.recordIntrusionAttempt(component, attackType, sourceIp, severity);
    }
    
    private void processAuditLog(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String userId = eventData.get("userId").asText();
        String action = eventData.get("action").asText();
        String resource = eventData.get("resource").asText();
        boolean suspicious = eventData.get("suspicious").asBoolean();
        JsonNode metadata = eventData.get("metadata");
        long timestamp = eventData.get("timestamp").asLong();
        
        AuditAnalyzer analyzer = auditAnalyzers.get(component);
        if (analyzer != null) {
            analyzer.analyzeAuditLog(userId, action, resource, suspicious, metadata, timestamp);
            
            double anomalyScore = analyzer.getAnomalyScore(userId);
            if (anomalyScore >= AUDIT_ANOMALY_THRESHOLD) {
                String message = String.format("Audit anomaly detected for user %s: score %.2f", 
                    userId, anomalyScore);
                
                alertService.createAlert("AUDIT_ANOMALY", "MEDIUM", message,
                    Map.of("component", component, "userId", userId, 
                           "anomalyScore", anomalyScore));
                
                investigateUserBehavior(analyzer, userId, action, resource);
            }
            
            if (suspicious) {
                handleSuspiciousActivity(component, userId, action, resource, metadata);
            }
        }
        
        updateSecurityState(component, state -> {
            state.recordAuditEvent(userId, action, suspicious);
        });
        
        metricsService.recordAuditLog(component, userId, action, suspicious);
    }
    
    private void processPermissionChange(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String userId = eventData.get("userId").asText();
        String targetUser = eventData.get("targetUser").asText();
        String permissionType = eventData.get("permissionType").asText();
        String oldValue = eventData.get("oldValue").asText();
        String newValue = eventData.get("newValue").asText();
        boolean isEscalation = eventData.get("isEscalation").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (isEscalation) {
            AccessControlMonitor monitor = accessMonitors.get(component);
            if (monitor != null) {
                int escalationCount = monitor.getEscalationCount(targetUser);
                if (escalationCount >= PERMISSION_ESCALATION_THRESHOLD) {
                    String message = String.format("Multiple permission escalations for user %s: %d changes", 
                        targetUser, escalationCount);
                    
                    alertService.createAlert("PRIVILEGE_ESCALATION", "HIGH", message,
                        Map.of("component", component, "targetUser", targetUser, 
                               "escalationCount", escalationCount));
                    
                    reviewPermissionChange(component, userId, targetUser, permissionType, newValue);
                }
            }
        }
        
        validatePermissionChange(component, userId, targetUser, permissionType, oldValue, newValue);
        
        updateSecurityState(component, state -> {
            state.recordPermissionChange(targetUser, permissionType, isEscalation);
        });
        
        metricsService.recordPermissionChange(component, targetUser, permissionType, isEscalation);
    }
    
    private void processDataExfiltration(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String userId = eventData.get("userId").asText();
        String dataType = eventData.get("dataType").asText();
        double dataSizeMB = eventData.get("dataSizeMB").asDouble();
        String destination = eventData.get("destination").asText();
        String protocol = eventData.get("protocol").asText();
        boolean suspicious = eventData.get("suspicious").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        DataProtectionMonitor monitor = dataMonitors.get(component);
        if (monitor != null) {
            monitor.recordDataTransfer(userId, dataType, dataSizeMB, destination, 
                                      protocol, suspicious, timestamp);
            
            if (dataSizeMB > DATA_EXFILTRATION_THRESHOLD_MB || suspicious) {
                String message = String.format("Potential data exfiltration by user %s: %.2f MB of %s data to %s", 
                    userId, dataSizeMB, dataType, destination);
                
                SecurityIncident incident = createSecurityIncident("DATA_EXFILTRATION", "CRITICAL", message,
                    Map.of("userId", userId, "dataType", dataType, 
                           "dataSizeMB", dataSizeMB, "destination", destination));
                
                orchestrationService.respondToIncident(incident);
                blockDataTransfer(component, userId, destination);
            }
            
            analyzeDataMovementPattern(monitor, userId, dataType, destination);
        }
        
        updateSecurityState(component, state -> {
            state.recordDataExfiltration(userId, dataSizeMB, suspicious);
        });
        
        metricsService.recordDataExfiltration(component, userId, dataType, dataSizeMB, suspicious);
    }
    
    private void processMalwareDetection(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String malwareType = eventData.get("malwareType").asText();
        String malwareName = eventData.get("malwareName").asText();
        String filePath = eventData.get("filePath").asText();
        String fileHash = eventData.get("fileHash").asText();
        String action = eventData.get("action").asText();
        boolean quarantined = eventData.get("quarantined").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (!quarantined) {
            String message = String.format("Malware detected but not quarantined: %s (%s) at %s", 
                malwareName, malwareType, filePath);
            
            SecurityIncident incident = createSecurityIncident("MALWARE_ACTIVE", "CRITICAL", message,
                Map.of("malwareType", malwareType, "malwareName", malwareName, 
                       "filePath", filePath, "fileHash", fileHash));
            
            orchestrationService.respondToIncident(incident);
            orchestrationService.quarantineFile(component, filePath, fileHash);
        }
        
        threatService.updateMalwareSignatures(malwareName, fileHash);
        scanRelatedSystems(component, malwareType, fileHash);
        
        updateSecurityState(component, state -> {
            state.recordMalwareDetection(malwareType, malwareName, quarantined);
        });
        
        metricsService.recordMalwareDetection(component, malwareType, quarantined);
    }
    
    private void processDdosAttack(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String attackVector = eventData.get("attackVector").asText();
        double requestRate = eventData.get("requestRate").asDouble();
        int uniqueSources = eventData.get("uniqueSources").asInt();
        String targetEndpoint = eventData.get("targetEndpoint").asText();
        boolean mitigated = eventData.get("mitigated").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        NetworkSecurityMonitor monitor = networkMonitors.get(component);
        if (monitor != null) {
            monitor.recordDdosAttack(attackVector, requestRate, uniqueSources, 
                                    targetEndpoint, mitigated, timestamp);
            
            if (requestRate > DDOS_TRAFFIC_THRESHOLD && !mitigated) {
                String message = String.format("Active DDoS attack on %s: %.0f requests/sec from %d sources", 
                    targetEndpoint, requestRate, uniqueSources);
                
                SecurityIncident incident = createSecurityIncident("DDOS_ATTACK", "CRITICAL", message,
                    Map.of("attackVector", attackVector, "requestRate", requestRate, 
                           "uniqueSources", uniqueSources, "targetEndpoint", targetEndpoint));
                
                orchestrationService.respondToIncident(incident);
                implementDdosMitigation(component, attackVector, targetEndpoint);
            }
        }
        
        updateSecurityState(component, state -> {
            state.recordDdosAttack(attackVector, requestRate, mitigated);
        });
        
        metricsService.recordDdosAttack(component, attackVector, requestRate, mitigated);
    }
    
    private void processSessionAnomaly(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String sessionId = eventData.get("sessionId").asText();
        String userId = eventData.get("userId").asText();
        String anomalyType = eventData.get("anomalyType").asText();
        JsonNode indicators = eventData.get("indicators");
        double riskScore = eventData.get("riskScore").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        if ("SESSION_HIJACK".equals(anomalyType)) {
            AuthenticationTracker tracker = authTrackers.get(component);
            if (tracker != null) {
                int hijackAttempts = tracker.getSessionHijackAttempts(userId);
                if (hijackAttempts >= SESSION_HIJACK_THRESHOLD) {
                    String message = String.format("Potential session hijacking for user %s: %d suspicious activities", 
                        userId, hijackAttempts);
                    
                    SecurityIncident incident = createSecurityIncident("SESSION_HIJACK", "HIGH", message,
                        Map.of("sessionId", sessionId, "userId", userId, 
                               "hijackAttempts", hijackAttempts));
                    
                    orchestrationService.respondToIncident(incident);
                    terminateSession(component, sessionId, userId);
                }
            }
        }
        
        analyzeSessionBehavior(component, sessionId, userId, indicators);
        
        updateSecurityState(component, state -> {
            state.recordSessionAnomaly(userId, anomalyType, riskScore);
        });
        
        metricsService.recordSessionAnomaly(component, userId, anomalyType, riskScore);
    }
    
    private void processComplianceCheck(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String standard = eventData.get("standard").asText();
        double complianceScore = eventData.get("complianceScore").asDouble();
        JsonNode violations = eventData.get("violations");
        JsonNode recommendations = eventData.get("recommendations");
        long timestamp = eventData.get("timestamp").asLong();
        
        ComplianceChecker checker = complianceCheckers.get(component);
        if (checker != null) {
            checker.recordComplianceCheck(standard, complianceScore, violations, 
                                         recommendations, timestamp);
            
            if (complianceScore < COMPLIANCE_VIOLATION_THRESHOLD) {
                String message = String.format("Compliance violation for %s standard in %s: score %.2f%%", 
                    standard, component, complianceScore * 100);
                
                alertService.createAlert("COMPLIANCE_VIOLATION", "HIGH", message,
                    Map.of("component", component, "standard", standard, 
                           "complianceScore", complianceScore));
                
                generateRemediationPlan(component, standard, violations, recommendations);
            }
        }
        
        updateSecurityState(component, state -> {
            state.updateComplianceScore(standard, complianceScore);
        });
        
        metricsService.recordComplianceCheck(component, standard, complianceScore);
    }
    
    private void processApiSecurity(JsonNode eventData) {
        String component = eventData.get("component").asText();
        String apiEndpoint = eventData.get("apiEndpoint").asText();
        String securityIssue = eventData.get("securityIssue").asText();
        String clientId = eventData.get("clientId").asText();
        int requestCount = eventData.get("requestCount").asInt();
        boolean blocked = eventData.get("blocked").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (requestCount > API_ABUSE_THRESHOLD) {
            String message = String.format("API abuse detected for %s: %d requests from client %s", 
                apiEndpoint, requestCount, clientId);
            
            alertService.createAlert("API_ABUSE", "MEDIUM", message,
                Map.of("component", component, "apiEndpoint", apiEndpoint, 
                       "clientId", clientId, "requestCount", requestCount));
            
            if (!blocked) {
                implementRateLimiting(component, apiEndpoint, clientId);
            }
        }
        
        validateApiSecurity(component, apiEndpoint, securityIssue);
        
        updateSecurityState(component, state -> {
            state.recordApiSecurityEvent(apiEndpoint, securityIssue, blocked);
        });
        
        metricsService.recordApiSecurity(component, apiEndpoint, securityIssue, blocked);
    }
    
    private void updateSecurityState(String component, java.util.function.Consumer<SecurityMonitoringState> updater) {
        securityStates.computeIfAbsent(component, k -> new SecurityMonitoringState(component))
                      .update(updater);
    }
    
    private SecurityIncident createSecurityIncident(String type, String severity, String description, Map<String, Object> details) {
        String incidentId = UUID.randomUUID().toString();
        SecurityIncident incident = new SecurityIncident(incidentId, type, severity, description, details);
        activeIncidents.put(incidentId, incident);
        
        alertService.createAlert(type, severity, description, details);
        incidentService.createIncident(incident);
        
        return incident;
    }
    
    private void detectCredentialStuffing(AuthenticationTracker tracker, String userId, 
                                         String ipAddress, String userAgent) {
        if (tracker.isPotentialCredentialStuffing(userId, ipAddress, userAgent)) {
            SecurityIncident incident = createSecurityIncident("CREDENTIAL_STUFFING", "HIGH",
                String.format("Potential credential stuffing attack for user %s", userId),
                Map.of("userId", userId, "ipAddress", ipAddress));
            
            orchestrationService.respondToIncident(incident);
        }
    }
    
    private void detectAnomalousLogin(AuthenticationTracker tracker, String userId, 
                                     String ipAddress, String userAgent) {
        if (tracker.isAnomalousLogin(userId, ipAddress, userAgent)) {
            notificationService.notifyUser(userId, "Unusual login detected from " + ipAddress);
            incidentService.investigateLogin(userId, ipAddress, userAgent);
        }
    }
    
    private void investigatePrivilegeEscalation(AccessControlMonitor monitor, String userId, String resource) {
        Map<String, Object> escalationPattern = monitor.getEscalationPattern(userId);
        if (!escalationPattern.isEmpty()) {
            incidentService.investigatePrivilegeEscalation(userId, resource, escalationPattern);
        }
    }
    
    private void correlateWithThreatIntel(ThreatDetector detector, String threatName, JsonNode indicators) {
        List<String> matchedIndicators = threatService.correlateIndicators(indicators);
        if (!matchedIndicators.isEmpty()) {
            detector.updateThreatIntelligence(threatName, matchedIndicators);
            orchestrationService.handleKnownThreat(threatName, matchedIndicators);
        }
    }
    
    private void implementContainment(String component, String threatType, String source) {
        orchestrationService.containThreat(component, threatType, source);
        networkMonitors.get(component).blockSource(source);
    }
    
    private void prioritizeRemediation(VulnerabilityScanner scanner, String component, JsonNode vulnerabilities) {
        List<Map<String, Object>> prioritizedVulns = scanner.prioritizeByRisk(vulnerabilities);
        vulnerabilityService.createRemediationPlan(component, prioritizedVulns);
    }
    
    private void generatePatchingPlan(String component, JsonNode vulnerabilities) {
        vulnerabilityService.generatePatchingSchedule(component, vulnerabilities);
    }
    
    private boolean isWeakAlgorithm(String algorithm) {
        return Arrays.asList("MD5", "SHA1", "DES", "RC4").contains(algorithm.toUpperCase());
    }
    
    private void investigateEncryptionIssue(String component, String encryptionType, String failureReason) {
        incidentService.investigateEncryptionFailure(component, encryptionType, failureReason);
    }
    
    private void recommendStrongerEncryption(String component, String encryptionType, String currentAlgorithm) {
        String recommendation = orchestrationService.getEncryptionRecommendation(encryptionType, currentAlgorithm);
        notificationService.notifySecurityTeam(component, "Weak encryption detected: " + recommendation);
    }
    
    private void handleExpiredCertificate(String component, String certificateName) {
        SecurityIncident incident = createSecurityIncident("CERTIFICATE_EXPIRED", "CRITICAL",
            String.format("Certificate %s has expired", certificateName),
            Map.of("component", component, "certificateName", certificateName));
        
        orchestrationService.respondToIncident(incident);
        orchestrationService.disableCertificate(component, certificateName);
    }
    
    private void handleInvalidCertificate(String component, String certificateName, String status) {
        incidentService.investigateCertificateIssue(component, certificateName, status);
    }
    
    private void implementNetworkBlocking(String sourceIp, String attackType) {
        networkMonitors.values().forEach(monitor -> monitor.blockSource(sourceIp));
        orchestrationService.updateFirewallRules(sourceIp, attackType);
    }
    
    private void correlateAttackPattern(IntrusionDetector detector, String attackType, JsonNode signature) {
        List<String> similarAttacks = detector.findSimilarPatterns(attackType, signature);
        if (similarAttacks.size() > 3) {
            orchestrationService.handleCoordinatedAttack(attackType, similarAttacks);
        }
    }
    
    private void investigateUserBehavior(AuditAnalyzer analyzer, String userId, 
                                        String action, String resource) {
        Map<String, Object> behaviorProfile = analyzer.getUserBehaviorProfile(userId);
        incidentService.investigateAnomalousActivity(userId, action, resource, behaviorProfile);
    }
    
    private void handleSuspiciousActivity(String component, String userId, String action, 
                                         String resource, JsonNode metadata) {
        incidentService.handleSuspiciousActivity(component, userId, action, resource, metadata);
    }
    
    private void reviewPermissionChange(String component, String userId, String targetUser, 
                                       String permissionType, String newValue) {
        orchestrationService.reviewPermissionChange(component, userId, targetUser, permissionType, newValue);
    }
    
    private void validatePermissionChange(String component, String userId, String targetUser, 
                                         String permissionType, String oldValue, String newValue) {
        if (!orchestrationService.isPermissionChangeValid(userId, targetUser, permissionType, newValue)) {
            orchestrationService.revertPermissionChange(component, targetUser, permissionType, oldValue);
        }
    }
    
    private void blockDataTransfer(String component, String userId, String destination) {
        dataMonitors.get(component).blockTransfer(userId, destination);
        orchestrationService.preventDataExfiltration(userId, destination);
    }
    
    private void analyzeDataMovementPattern(DataProtectionMonitor monitor, String userId, 
                                           String dataType, String destination) {
        Map<String, Object> pattern = monitor.getDataMovementPattern(userId);
        if (monitor.isAnomalousPattern(pattern)) {
            incidentService.investigateDataMovement(userId, dataType, destination, pattern);
        }
    }
    
    private void scanRelatedSystems(String component, String malwareType, String fileHash) {
        orchestrationService.initiateSystemScan(component, malwareType, fileHash);
    }
    
    private void implementDdosMitigation(String component, String attackVector, String targetEndpoint) {
        networkMonitors.get(component).enableDdosProtection(attackVector, targetEndpoint);
        orchestrationService.activateDdosMitigation(attackVector, targetEndpoint);
    }
    
    private void terminateSession(String component, String sessionId, String userId) {
        authTrackers.get(component).terminateSession(sessionId);
        notificationService.notifyUser(userId, "Your session was terminated due to security concerns");
    }
    
    private void analyzeSessionBehavior(String component, String sessionId, String userId, JsonNode indicators) {
        authTrackers.get(component).analyzeSession(sessionId, userId, indicators);
    }
    
    private void generateRemediationPlan(String component, String standard, 
                                        JsonNode violations, JsonNode recommendations) {
        Map<String, Object> plan = orchestrationService.createComplianceRemediationPlan(
            component, standard, violations, recommendations);
        incidentService.trackRemediationProgress(component, standard, plan);
    }
    
    private void implementRateLimiting(String component, String apiEndpoint, String clientId) {
        networkMonitors.get(component).setRateLimit(apiEndpoint, clientId, 100);
    }
    
    private void validateApiSecurity(String component, String apiEndpoint, String securityIssue) {
        orchestrationService.validateApiSecurity(component, apiEndpoint, securityIssue);
    }
    
    @Scheduled(fixedDelay = 60000)
    private void analyzeSecurityPosture() {
        try {
            Map<String, Double> componentScores = new HashMap<>();
            
            securityStates.forEach((component, state) -> {
                double score = calculateSecurityScore(state);
                componentScores.put(component, score);
                
                if (score < 0.6) {
                    alertService.createAlert("LOW_SECURITY_SCORE", "WARNING",
                        String.format("Low security score for %s: %.2f", component, score),
                        Map.of("component", component, "score", score));
                }
            });
            
            double overallScore = componentScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            generateSecurityReport(overallScore, componentScores);
            
        } catch (Exception e) {
            log.error("Error analyzing security posture: {}", e.getMessage(), e);
        }
    }
    
    private double calculateSecurityScore(SecurityMonitoringState state) {
        double authScore = 1.0 - (state.getFailedLoginRate() / 100.0);
        double threatScore = 1.0 - state.getThreatLevel();
        double vulnScore = 1.0 - (state.getCriticalVulnerabilities() / 10.0);
        double complianceScore = state.getAverageComplianceScore();
        
        return (authScore + threatScore + vulnScore + complianceScore) / 4.0;
    }
    
    private void generateSecurityReport(double overallScore, Map<String, Double> componentScores) {
        Map<String, Object> report = new HashMap<>();
        report.put("overallScore", overallScore);
        report.put("componentScores", componentScores);
        report.put("timestamp", Instant.now().toEpochMilli());
        report.put("activeIncidents", activeIncidents.size());
        
        metricsService.recordSecurityReport(report);
        notificationService.sendSecurityReport(report);
    }
    
    @Scheduled(fixedDelay = 30000)
    private void detectThreats() {
        try {
            threatDetectors.forEach((component, detector) -> {
                List<Map<String, Object>> threats = detector.detectActiveThreats();
                threats.forEach(threat -> {
                    double score = (double) threat.get("score");
                    if (score > THREAT_SCORE_THRESHOLD) {
                        handleDetectedThreat(component, threat);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error detecting threats: {}", e.getMessage(), e);
        }
    }
    
    private void handleDetectedThreat(String component, Map<String, Object> threat) {
        String threatType = (String) threat.get("type");
        double score = (double) threat.get("score");
        
        SecurityIncident incident = createSecurityIncident("ACTIVE_THREAT", "HIGH",
            String.format("Active threat detected in %s: %s (score: %.2f)", component, threatType, score),
            threat);
        
        orchestrationService.respondToIncident(incident);
    }
    
    @Scheduled(fixedDelay = 300000)
    private void scanVulnerabilities() {
        try {
            vulnScanners.forEach((component, scanner) -> {
                scanner.performScan();
                Map<String, Integer> results = scanner.getLatestScanResults();
                
                int critical = results.getOrDefault("critical", 0);
                if (critical > 0) {
                    vulnerabilityService.handleCriticalVulnerabilities(component, critical);
                }
            });
        } catch (Exception e) {
            log.error("Error scanning vulnerabilities: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 600000)
    private void performComplianceChecks() {
        try {
            complianceCheckers.forEach((component, checker) -> {
                Map<String, Double> scores = checker.performComplianceCheck();
                scores.forEach((standard, score) -> {
                    if (score < COMPLIANCE_VIOLATION_THRESHOLD) {
                        handleComplianceViolation(component, standard, score);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error performing compliance checks: {}", e.getMessage(), e);
        }
    }
    
    private void handleComplianceViolation(String component, String standard, double score) {
        Map<String, Object> violation = Map.of(
            "component", component,
            "standard", standard,
            "score", score,
            "timestamp", Instant.now().toEpochMilli()
        );
        
        incidentService.handleComplianceViolation(violation);
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            int deleted = eventRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old security events", deleted);
            
            activeIncidents.entrySet().removeIf(entry -> 
                entry.getValue().isOlderThan(cutoff));
            
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process security monitoring event after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            Map<String, Object> errorContext = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "error", error.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
            );
            
            notificationService.notifyError("SECURITY_MONITORING_PROCESSING_ERROR", errorContext);
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
        log.error("Fallback triggered for security monitoring event processing", ex);
        errorCounter.increment();
    }
    
    private double calculateAverageThreatLevel(Map<String, ThreatDetector> detectors) {
        return detectors.values().stream()
            .mapToDouble(ThreatDetector::getCurrentThreatLevel)
            .average()
            .orElse(0.0);
    }
    
    private double calculateTotalVulnerabilities(Map<String, VulnerabilityScanner> scanners) {
        return scanners.values().stream()
            .mapToDouble(scanner -> scanner.getTotalVulnerabilities())
            .sum();
    }
    
    private double calculateAverageComplianceScore(Map<String, ComplianceChecker> checkers) {
        return checkers.values().stream()
            .mapToDouble(ComplianceChecker::getOverallComplianceScore)
            .average()
            .orElse(0.0);
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down SecurityMonitoringConsumer...");
            scheduledExecutor.shutdown();
            analysisExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
            
            log.info("SecurityMonitoringConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}