package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.*;
import com.waqiti.security.dto.FraudAlertEvent;
import com.waqiti.security.entity.FraudCase;
import com.waqiti.security.entity.FraudAction;
import com.waqiti.security.repository.FraudCaseRepository;
import com.waqiti.security.repository.FraudActionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise-grade Kafka consumer for fraud alert events.
 * Implements complete fraud response orchestration with industrial-strength reliability.
 * 
 * This consumer was identified as CRITICAL in the architecture analysis - fraud events
 * were being produced by multiple services but never consumed, creating a massive security gap.
 */
@Slf4j
@Component
public class FraudAlertConsumer {

    // Core Services
    private final FraudAnalysisService fraudAnalysisService;
    private final AlertManagementService alertManagementService;
    private final IncidentResponseService incidentResponseService;
    private final TransactionService transactionService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final MerchantService merchantService;
    private final RiskScoringService riskScoringService;
    private final MachineLearningService mlService;
    
    // Infrastructure
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Repositories
    private final FraudCaseRepository fraudCaseRepository;
    private final FraudActionRepository fraudActionRepository;
    
    // Metrics
    private final Counter fraudAlertsProcessed;
    private final Counter fraudAlertsBlocked;
    private final Counter fraudAlertsFailed;
    private final Timer fraudProcessingTimer;
    
    // Configuration
    @Value("${fraud.processing.timeout.seconds:30}")
    private int processingTimeoutSeconds;
    
    @Value("${fraud.critical.auto-block:true}")
    private boolean autoblockCritical;
    
    @Value("${fraud.high.manual-review:true}")
    private boolean manualReviewHigh;
    
    @Value("${fraud.velocity.window.minutes:5}")
    private int velocityWindowMinutes;
    
    @Value("${fraud.max.retry.attempts:3}")
    private int maxRetryAttempts;

    public FraudAlertConsumer(
            FraudAnalysisService fraudAnalysisService,
            AlertManagementService alertManagementService,
            IncidentResponseService incidentResponseService,
            TransactionService transactionService,
            UserService userService,
            NotificationService notificationService,
            ComplianceService complianceService,
            MerchantService merchantService,
            RiskScoringService riskScoringService,
            MachineLearningService mlService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            FraudCaseRepository fraudCaseRepository,
            FraudActionRepository fraudActionRepository) {
        
        this.fraudAnalysisService = fraudAnalysisService;
        this.alertManagementService = alertManagementService;
        this.incidentResponseService = incidentResponseService;
        this.transactionService = transactionService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.merchantService = merchantService;
        this.riskScoringService = riskScoringService;
        this.mlService = mlService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.fraudCaseRepository = fraudCaseRepository;
        this.fraudActionRepository = fraudActionRepository;
        
        // Initialize metrics
        this.fraudAlertsProcessed = Counter.builder("fraud.alerts.processed")
            .description("Total fraud alerts processed")
            .register(meterRegistry);
        
        this.fraudAlertsBlocked = Counter.builder("fraud.alerts.blocked")
            .description("Total transactions blocked due to fraud")
            .register(meterRegistry);
        
        this.fraudAlertsFailed = Counter.builder("fraud.alerts.failed")
            .description("Total fraud alert processing failures")
            .register(meterRegistry);
        
        this.fraudProcessingTimer = Timer.builder("fraud.processing.time")
            .description("Fraud alert processing time")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "fraud-alerts",
        groupId = "security-service-fraud-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 60)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleFraudAlert(
            @Valid @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Processing fraud alert: correlationId={}, eventId={}, transactionId={}, " +
                "severity={}, riskScore={}, amount={} {}, source={}, partition={}, offset={}", 
                correlationId, event.getEventId(), event.getTransactionId(), 
                event.getSeverity(), event.getRiskScore(), event.getAmount(), 
                event.getCurrency(), event.getSource(), partition, offset);

        LocalDateTime startTime = LocalDateTime.now();
        FraudCase fraudCase = null;
        
        try {
            // Check for duplicate processing
            if (isDuplicateEvent(event.getEventId())) {
                log.warn("Duplicate fraud alert detected: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Create fraud case for tracking
            fraudCase = createFraudCase(event, correlationId);
            
            // Execute parallel analysis tasks
            CompletableFuture<FraudAnalysisService.AnalysisResult> analysisF = 
                CompletableFuture.supplyAsync(() -> 
                    fraudAnalysisService.analyzeFraudPattern(event));
            
            CompletableFuture<Map<String, Object>> enrichmentF = 
                CompletableFuture.supplyAsync(() -> 
                    enrichEventData(event));
            
            CompletableFuture<List<String>> velocityF = 
                CompletableFuture.supplyAsync(() -> 
                    checkVelocityRules(event));
            
            CompletableFuture<Double> mlScoreF = 
                CompletableFuture.supplyAsync(() -> 
                    mlService.recalculateFraudScore(event));
            
            // Wait for all analysis tasks with timeout
            CompletableFuture.allOf(analysisF, enrichmentF, velocityF, mlScoreF)
                .get(processingTimeoutSeconds, TimeUnit.SECONDS);
            
            FraudAnalysisService.AnalysisResult analysis = analysisF.get();
            Map<String, Object> enrichedData = enrichmentF.get();
            List<String> velocityViolations = velocityF.get();
            Double mlScore = mlScoreF.get();
            
            // Update event with ML score if significantly different
            if (Math.abs(mlScore - event.getRiskScore()) > 0.1) {
                log.info("ML score differs from initial: original={}, ml={}", 
                    event.getRiskScore(), mlScore);
                event.setRiskScore(mlScore);
                // Potentially adjust severity based on new score
                event.setSeverity(calculateSeverity(mlScore, event.getAmount()));
            }
            
            // Add velocity violations to analysis
            if (!velocityViolations.isEmpty()) {
                analysis.addRiskFactor("velocity_violations", velocityViolations);
                log.warn("Velocity violations detected: {}", velocityViolations);
            }
            
            // Process based on severity and business rules
            FraudResponseDecision decision = determineResponse(event, analysis, enrichedData);
            fraudCase.setDecision(decision.getDecision());
            fraudCase.setDecisionReason(decision.getReason());
            
            // Execute response actions
            List<FraudAction> actions = executeResponseActions(event, decision, analysis, correlationId);
            fraudCase.setActions(actions);
            
            // Update case status
            fraudCase.setStatus(FraudCase.Status.PROCESSED);
            fraudCase.setProcessedAt(LocalDateTime.now());
            fraudCase.setProcessingTimeMs(
                Duration.between(startTime, LocalDateTime.now()).toMillis());
            fraudCaseRepository.save(fraudCase);
            
            // Send notifications
            sendNotifications(event, decision, fraudCase);
            
            // Publish processed event for downstream consumers
            publishProcessedEvent(event, decision, fraudCase);
            
            // Update metrics
            fraudAlertsProcessed.increment();
            if (decision.shouldBlock()) {
                fraudAlertsBlocked.increment();
            }
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fraud alert: correlationId={}, eventId={}, " +
                    "decision={}, processingTime={}ms", 
                    correlationId, event.getEventId(), decision.getDecision(),
                    fraudCase.getProcessingTimeMs());

        } catch (Exception e) {
            log.error("Error processing fraud alert: correlationId={}, eventId={}, error={}", 
                    correlationId, event.getEventId(), e.getMessage(), e);
            
            fraudAlertsFailed.increment();
            
            // Update fraud case with error
            if (fraudCase != null) {
                fraudCase.setStatus(FraudCase.Status.ERROR);
                fraudCase.setErrorMessage(e.getMessage());
                fraudCase.setProcessedAt(LocalDateTime.now());
                fraudCaseRepository.save(fraudCase);
            }
            
            // Send to DLQ after max retries
            if (getRetryCount(event) >= maxRetryAttempts) {
                sendToDeadLetterQueue(event, e);
                acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
            } else {
                throw new RuntimeException("Fraud processing failed", e); // Trigger retry
            }
            
        } finally {
            sample.stop(fraudProcessingTimer);
            // Clear any temporary cache entries
            clearTemporaryCache(event.getEventId());
        }
    }

    /**
     * Comprehensive response determination based on multiple factors
     */
    private FraudResponseDecision determineResponse(
            FraudAlertEvent event,
            FraudAnalysisService.AnalysisResult analysis,
            Map<String, Object> enrichedData) {
        
        FraudResponseDecision.Builder decision = FraudResponseDecision.builder();
        
        // Check for financial crime first - highest priority
        if (event.isFinancialCrime()) {
            return decision
                .decision(FraudResponseDecision.Decision.BLOCK_AND_REPORT)
                .shouldBlock(true)
                .shouldReverse(true)
                .shouldNotifyAuthorities(true)
                .requiresCompliance(true)
                .reason("Financial crime detected - AML/Sanctions violation")
                .priority(1)
                .build();
        }
        
        // Critical severity - immediate block
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.CRITICAL) {
            decision.decision(FraudResponseDecision.Decision.BLOCK)
                .shouldBlock(true)
                .shouldReverse(event.getReversible())
                .requiresManualReview(false)
                .reason("Critical fraud severity - automatic block");
                
            if (event.getRiskScore() > 0.95) {
                decision.shouldBlacklistUser(true);
            }
            
            return decision.priority(event.getResponsePriority()).build();
        }
        
        // High severity - depends on configuration and amount
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.HIGH) {
            boolean shouldBlock = event.shouldBlockTransaction() || 
                event.getAmount().compareTo(new BigDecimal("5000")) > 0;
            
            return decision
                .decision(shouldBlock ? 
                    FraudResponseDecision.Decision.BLOCK_AND_REVIEW : 
                    FraudResponseDecision.Decision.REVIEW)
                .shouldBlock(shouldBlock)
                .requiresManualReview(true)
                .requiresTwoFactorAuth(true)
                .reason("High fraud risk - " + (shouldBlock ? "blocked pending review" : "manual review required"))
                .priority(event.getResponsePriority())
                .build();
        }
        
        // Medium severity - enhanced monitoring
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.MEDIUM) {
            boolean requiresAuth = event.getRiskScore() > 0.5 || 
                event.getAmount().compareTo(new BigDecimal("1000")) > 0;
            
            return decision
                .decision(FraudResponseDecision.Decision.MONITOR)
                .shouldBlock(false)
                .requiresManualReview(event.requiresManualReview())
                .requiresTwoFactorAuth(requiresAuth)
                .enhancedMonitoring(true)
                .reason("Medium fraud risk - enhanced monitoring applied")
                .priority(event.getResponsePriority())
                .build();
        }
        
        // Low severity - log and continue
        return decision
            .decision(FraudResponseDecision.Decision.ALLOW)
            .shouldBlock(false)
            .requiresManualReview(false)
            .reason("Low fraud risk - transaction allowed with logging")
            .priority(5)
            .build();
    }

    /**
     * Execute all response actions based on decision
     */
    private List<FraudAction> executeResponseActions(
            FraudAlertEvent event,
            FraudResponseDecision decision,
            FraudAnalysisService.AnalysisResult analysis,
            String correlationId) {
        
        List<FraudAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Block transaction if required
        if (decision.shouldBlock()) {
            try {
                transactionService.blockTransaction(event.getTransactionId(), 
                    decision.getReason(), correlationId);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_TRANSACTION)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Transaction blocked: " + event.getTransactionId())
                    .build());
                    
                log.info("Blocked transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to block transaction: {}", event.getTransactionId(), e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_TRANSACTION)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Reverse transaction if required and possible
        if (decision.shouldReverse() && event.getReversible()) {
            try {
                String reversalId = transactionService.initiateReversal(
                    event.getTransactionId(), "Fraud detected: " + decision.getReason(), correlationId);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.REVERSE_TRANSACTION)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Reversal initiated: " + reversalId)
                    .build());
                    
                log.info("Initiated transaction reversal: original={}, reversal={}", 
                    event.getTransactionId(), reversalId);
            } catch (Exception e) {
                log.error("Failed to reverse transaction: {}", event.getTransactionId(), e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.REVERSE_TRANSACTION)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Block user account if required
        if (decision.shouldBlockUser()) {
            try {
                userService.blockAccount(event.getUserId(), 
                    "Fraud detected: " + decision.getReason(), correlationId);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_USER)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("User account blocked: " + event.getUserId())
                    .build());
                    
                log.warn("Blocked user account: {}", event.getUserId());
            } catch (Exception e) {
                log.error("Failed to block user account: {}", event.getUserId(), e);
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLOCK_USER)
                    .status(FraudAction.Status.FAILED)
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Blacklist user if required
        if (decision.shouldBlacklistUser()) {
            try {
                userService.addToBlacklist(event.getUserId(), event.getDeviceId(), 
                    event.getUserIpAddress(), decision.getReason());
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.BLACKLIST_USER)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("User blacklisted: " + event.getUserId())
                    .build());
                    
                log.warn("Blacklisted user: {}", event.getUserId());
            } catch (Exception e) {
                log.error("Failed to blacklist user: {}", event.getUserId(), e);
            }
        }
        
        // Request two-factor authentication
        if (decision.requiresTwoFactorAuth()) {
            try {
                userService.requestTwoFactorAuth(event.getUserId(), event.getSessionId());
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.REQUEST_2FA)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("2FA requested for user: " + event.getUserId())
                    .build());
            } catch (Exception e) {
                log.error("Failed to request 2FA: {}", event.getUserId(), e);
            }
        }
        
        // Create manual review case
        if (decision.requiresManualReview()) {
            try {
                String caseId = incidentResponseService.createReviewCase(
                    event, analysis, decision, correlationId);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.CREATE_CASE)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Review case created: " + caseId)
                    .build());
                    
                log.info("Created manual review case: {}", caseId);
            } catch (Exception e) {
                log.error("Failed to create review case", e);
            }
        }
        
        // Enable enhanced monitoring
        if (decision.requiresEnhancedMonitoring()) {
            try {
                riskScoringService.enableEnhancedMonitoring(
                    event.getUserId(), Duration.ofDays(30));
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.ENHANCE_MONITORING)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Enhanced monitoring enabled for 30 days")
                    .build());
            } catch (Exception e) {
                log.error("Failed to enable enhanced monitoring", e);
            }
        }
        
        // Notify authorities if required
        if (decision.shouldNotifyAuthorities()) {
            try {
                complianceService.notifyAuthorities(event, decision.getReason());
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.NOTIFY_AUTHORITIES)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("Authorities notified")
                    .build());
                    
                log.warn("Authorities notified for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to notify authorities", e);
            }
        }
        
        // File SAR if required
        if (decision.requiresCompliance() && Boolean.TRUE.equals(event.getSarRequired())) {
            try {
                String sarId = complianceService.fileSAR(event, analysis, correlationId);
                
                actions.add(FraudAction.builder()
                    .actionType(FraudAction.ActionType.FILE_SAR)
                    .status(FraudAction.Status.SUCCESS)
                    .timestamp(now)
                    .details("SAR filed: " + sarId)
                    .build());
                    
                log.info("SAR filed: {}", sarId);
            } catch (Exception e) {
                log.error("Failed to file SAR", e);
            }
        }
        
        return actions;
    }

    /**
     * Send comprehensive notifications based on fraud detection
     */
    private void sendNotifications(FraudAlertEvent event, 
                                  FraudResponseDecision decision,
                                  FraudCase fraudCase) {
        try {
            // Notify user if required
            if (Boolean.TRUE.equals(event.getNotifyUser()) && !decision.shouldBlockUser()) {
                notificationService.notifyUser(
                    event.getUserId(),
                    "Suspicious Activity Detected",
                    buildUserNotificationMessage(event, decision),
                    NotificationService.Priority.HIGH,
                    NotificationService.Channel.PUSH_AND_EMAIL
                );
            }
            
            // Notify merchant if required
            if (Boolean.TRUE.equals(event.getNotifyMerchant()) && event.getMerchantId() != null) {
                merchantService.notifyMerchant(
                    event.getMerchantId(),
                    "Fraud Alert",
                    buildMerchantNotificationMessage(event, decision)
                );
            }
            
            // Notify security team for high-priority cases
            if (decision.getPriority() <= 2) {
                notificationService.notifySecurityTeam(
                    "High Priority Fraud Alert",
                    buildSecurityTeamMessage(event, decision, fraudCase),
                    NotificationService.Priority.CRITICAL
                );
            }
            
            // Notify compliance for financial crimes
            if (event.isFinancialCrime()) {
                complianceService.notifyComplianceTeam(event, fraudCase);
            }
            
        } catch (Exception e) {
            log.error("Failed to send notifications for fraud alert: {}", event.getEventId(), e);
        }
    }

    /**
     * Enrich event data with additional context
     */
    @Cacheable(value = "fraudEnrichment", key = "#event.transactionId")
    private Map<String, Object> enrichEventData(FraudAlertEvent event) {
        Map<String, Object> enrichedData = new HashMap<>();
        
        try {
            // Get user history
            enrichedData.put("userHistory", userService.getUserTransactionHistory(
                event.getUserId(), 30));
            
            // Get merchant risk profile
            if (event.getMerchantId() != null) {
                enrichedData.put("merchantRisk", merchantService.getRiskProfile(
                    event.getMerchantId()));
            }
            
            // Get device history
            if (event.getDeviceId() != null) {
                enrichedData.put("deviceHistory", userService.getDeviceHistory(
                    event.getDeviceId()));
            }
            
            // Get location risk
            if (event.getGeolocation() != null) {
                enrichedData.put("locationRisk", riskScoringService.getLocationRisk(
                    event.getGeolocation()));
            }
            
            // Get related transactions
            enrichedData.put("relatedTransactions", transactionService.getRelatedTransactions(
                event.getTransactionId(), event.getUserId()));
            
        } catch (Exception e) {
            log.error("Error enriching fraud event data", e);
        }
        
        return enrichedData;
    }

    /**
     * Check velocity rules for unusual activity patterns
     */
    private List<String> checkVelocityRules(FraudAlertEvent event) {
        List<String> violations = new ArrayList<>();
        
        try {
            String velocityKey = "velocity:" + event.getUserId();
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
            
            // Get recent transactions from cache
            List<FraudAlertEvent> recentEvents = getRecentEvents(event.getUserId(), windowStart);
            
            // Check transaction count velocity
            if (recentEvents.size() > 5) {
                violations.add("High transaction velocity: " + recentEvents.size() + 
                    " transactions in " + velocityWindowMinutes + " minutes");
            }
            
            // Check amount velocity
            BigDecimal totalAmount = recentEvents.stream()
                .map(FraudAlertEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(event.getAmount());
            
            if (totalAmount.compareTo(new BigDecimal("10000")) > 0) {
                violations.add("High amount velocity: " + totalAmount + " in " + 
                    velocityWindowMinutes + " minutes");
            }
            
            // Check unique merchant velocity
            Set<String> uniqueMerchants = recentEvents.stream()
                .map(FraudAlertEvent::getMerchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            if (uniqueMerchants.size() > 10) {
                violations.add("High merchant diversity: " + uniqueMerchants.size() + 
                    " different merchants");
            }
            
            // Check geographic velocity
            if (event.getGeolocation() != null) {
                for (FraudAlertEvent recent : recentEvents) {
                    if (recent.getGeolocation() != null && 
                        !recent.getGeolocation().equals(event.getGeolocation())) {
                        double distance = calculateDistance(
                            event.getLatitude(), event.getLongitude(),
                            recent.getLatitude(), recent.getLongitude());
                        
                        if (distance > 500) { // More than 500km
                            Duration timeDiff = Duration.between(
                                recent.getTimestamp(), event.getTimestamp());
                            
                            if (timeDiff.toHours() < 1) {
                                violations.add("Impossible travel: " + distance + 
                                    "km in " + timeDiff.toMinutes() + " minutes");
                            }
                        }
                    }
                }
            }
            
            // Store this event for future velocity checks
            cacheEvent(event);
            
        } catch (Exception e) {
            log.error("Error checking velocity rules", e);
        }
        
        return violations;
    }

    /**
     * Create comprehensive fraud case for tracking
     */
    private FraudCase createFraudCase(FraudAlertEvent event, String correlationId) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.setCaseId(UUID.randomUUID().toString());
        fraudCase.setCorrelationId(correlationId);
        fraudCase.setEventId(event.getEventId());
        fraudCase.setTransactionId(event.getTransactionId());
        fraudCase.setUserId(event.getUserId());
        fraudCase.setAmount(event.getAmount());
        fraudCase.setCurrency(event.getCurrency());
        fraudCase.setSeverity(event.getSeverity().toString());
        fraudCase.setRiskScore(event.getRiskScore());
        fraudCase.setFraudType(event.getFraudType().toString());
        fraudCase.setSource(event.getSource());
        fraudCase.setCreatedAt(LocalDateTime.now());
        fraudCase.setStatus(FraudCase.Status.PROCESSING);
        fraudCase.setMetadata(objectMapper.convertValue(event, Map.class));
        
        return fraudCaseRepository.save(fraudCase);
    }

    /**
     * Publish processed fraud event for downstream consumers
     */
    private void publishProcessedEvent(FraudAlertEvent event, 
                                      FraudResponseDecision decision,
                                      FraudCase fraudCase) {
        try {
            Map<String, Object> processedEvent = new HashMap<>();
            processedEvent.put("originalEvent", event);
            processedEvent.put("decision", decision);
            processedEvent.put("caseId", fraudCase.getCaseId());
            processedEvent.put("processedAt", LocalDateTime.now());
            processedEvent.put("processingTimeMs", fraudCase.getProcessingTimeMs());
            
            kafkaTemplate.send("fraud-processed", event.getTransactionId(), processedEvent);
            
            log.debug("Published processed fraud event: transactionId={}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to publish processed fraud event", e);
        }
    }

    /**
     * Send event to dead letter queue for manual processing
     */
    private void sendToDeadLetterQueue(FraudAlertEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("event", event);
            dlqMessage.put("error", error.getMessage());
            dlqMessage.put("stackTrace", Arrays.toString(error.getStackTrace()));
            dlqMessage.put("timestamp", LocalDateTime.now());
            dlqMessage.put("retryCount", getRetryCount(event));
            
            kafkaTemplate.send("fraud-alerts-dlq", event.getEventId(), dlqMessage);
            
            log.info("Sent fraud alert to DLQ: eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: eventId={}", event.getEventId(), e);
        }
    }

    // Helper methods
    
    private boolean isDuplicateEvent(String eventId) {
        String key = "fraud:processed:" + eventId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(key, true, Duration.ofHours(24));
            return false;
        }
        return true;
    }
    
    private int getRetryCount(FraudAlertEvent event) {
        String key = "fraud:retry:" + event.getEventId();
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        return count != null ? count : 0;
    }
    
    private void cacheEvent(FraudAlertEvent event) {
        String key = "fraud:velocity:" + event.getUserId();
        redisTemplate.opsForList().rightPush(key, event);
        redisTemplate.expire(key, Duration.ofMinutes(velocityWindowMinutes * 2));
    }
    
    private List<FraudAlertEvent> getRecentEvents(String userId, LocalDateTime since) {
        String key = "fraud:velocity:" + userId;
        List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
        if (cached == null) return new ArrayList<>();
        
        return cached.stream()
            .map(obj -> objectMapper.convertValue(obj, FraudAlertEvent.class))
            .filter(e -> e.getTimestamp().isAfter(since))
            .collect(Collectors.toList());
    }
    
    private void clearTemporaryCache(String eventId) {
        try {
            redisTemplate.delete("fraud:processing:" + eventId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
    
    private FraudAlertEvent.FraudSeverity calculateSeverity(Double riskScore, BigDecimal amount) {
        if (riskScore > 0.9 || amount.compareTo(new BigDecimal("50000")) > 0) {
            return FraudAlertEvent.FraudSeverity.CRITICAL;
        } else if (riskScore > 0.7 || amount.compareTo(new BigDecimal("10000")) > 0) {
            return FraudAlertEvent.FraudSeverity.HIGH;
        } else if (riskScore > 0.5 || amount.compareTo(new BigDecimal("5000")) > 0) {
            return FraudAlertEvent.FraudSeverity.MEDIUM;
        } else if (riskScore > 0.3) {
            return FraudAlertEvent.FraudSeverity.LOW;
        }
        return FraudAlertEvent.FraudSeverity.INFO;
    }
    
    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0;
        }
        
        final int R = 6371; // Radius of Earth in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    private String buildUserNotificationMessage(FraudAlertEvent event, FraudResponseDecision decision) {
        return String.format(
            "We detected suspicious activity on your account. Transaction of %s %s %s. " +
            "Please verify this was you or contact support immediately.",
            event.getAmount(), event.getCurrency(),
            decision.shouldBlock() ? "was blocked" : "requires verification"
        );
    }
    
    private String buildMerchantNotificationMessage(FraudAlertEvent event, FraudResponseDecision decision) {
        return String.format(
            "Fraud alert for transaction %s: Amount %s %s, Risk Score: %.2f. Decision: %s",
            event.getTransactionId(), event.getAmount(), event.getCurrency(),
            event.getRiskScore(), decision.getDecision()
        );
    }
    
    private String buildSecurityTeamMessage(FraudAlertEvent event, 
                                           FraudResponseDecision decision,
                                           FraudCase fraudCase) {
        return String.format(
            "HIGH PRIORITY FRAUD ALERT\n" +
            "Case ID: %s\n" +
            "Transaction: %s\n" +
            "User: %s\n" +
            "Amount: %s %s\n" +
            "Risk Score: %.2f\n" +
            "Fraud Type: %s\n" +
            "Decision: %s\n" +
            "Actions Taken: %d\n" +
            "Review Required: %s",
            fraudCase.getCaseId(),
            event.getTransactionId(),
            event.getUserId(),
            event.getAmount(), event.getCurrency(),
            event.getRiskScore(),
            event.getFraudType(),
            decision.getDecision(),
            fraudCase.getActions() != null ? fraudCase.getActions().size() : 0,
            decision.requiresManualReview() ? "YES" : "NO"
        );
    }
}