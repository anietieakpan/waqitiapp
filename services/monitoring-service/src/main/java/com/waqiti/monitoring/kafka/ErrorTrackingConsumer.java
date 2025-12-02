package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.model.ErrorOccurrence;
import com.waqiti.monitoring.model.ErrorPattern;
import com.waqiti.monitoring.model.ErrorCategory;
import com.waqiti.monitoring.model.ErrorSeverity;
import com.waqiti.monitoring.service.ErrorAnalysisService;
import com.waqiti.monitoring.service.ErrorCategorisationService;
import com.waqiti.monitoring.service.RootCauseAnalysisService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.SystemException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
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
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ErrorTrackingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ErrorTrackingConsumer.class);
    private static final String CONSUMER_NAME = "error-tracking-consumer";
    private static final String DLQ_TOPIC = "error-tracking-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final ErrorAnalysisService errorAnalysisService;
    private final ErrorCategorisationService errorCategorisationService;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.error-tracking.enabled:true}")
    private boolean consumerEnabled;

    @Value("${error.tracking.analysis-window-minutes:15}")
    private int analysisWindowMinutes;

    @Value("${error.tracking.pattern-detection.enabled:true}")
    private boolean patternDetectionEnabled;

    @Value("${error.tracking.auto-categorization.enabled:true}")
    private boolean autoCategorisationEnabled;

    @Value("${error.tracking.root-cause-analysis.enabled:true}")
    private boolean rootCauseAnalysisEnabled;

    @Value("${error.tracking.error-rate-threshold:0.05}")
    private double errorRateThreshold;

    @Value("${error.tracking.burst-threshold:10}")
    private int burstThreshold;

    @Value("${error.tracking.similarity-threshold:0.8}")
    private double similarityThreshold;

    @Value("${error.tracking.retention-days:30}")
    private int retentionDays;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter errorOccurrenceCounter;
    private Gauge errorRateGauge;
    private DistributionSummary errorSeverityDistribution;

    private final ConcurrentHashMap<String, ErrorOccurrence> recentErrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ErrorOccurrence>> errorBursts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ErrorPattern> detectedPatterns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalErrorsTracked = new AtomicLong(0);
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService errorProcessingExecutor;

    public ErrorTrackingConsumer(ObjectMapper objectMapper,
                                 ErrorAnalysisService errorAnalysisService,
                                 ErrorCategorisationService errorCategorisationService,
                                 RootCauseAnalysisService rootCauseAnalysisService,
                                 AlertingService alertingService,
                                 MetricsService metricsService,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.errorAnalysisService = errorAnalysisService;
        this.errorCategorisationService = errorCategorisationService;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("error_tracking_processed_total")
                .description("Total processed error tracking events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("error_tracking_errors_total")
                .description("Total error tracking processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("error_tracking_dlq_total")
                .description("Total error tracking events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("error_tracking_processing_duration")
                .description("Error tracking processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorOccurrenceCounter = Counter.builder("tracked_errors_total")
                .description("Total errors tracked")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorRateGauge = Gauge.builder("system_error_rate", this::calculateCurrentErrorRate)
                .description("Current system error rate")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorSeverityDistribution = DistributionSummary.builder("error_severity_distribution")
                .description("Distribution of error severities")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        scheduledExecutor.scheduleWithFixedDelay(this::performErrorAnalysis, 
                analysisWindowMinutes, analysisWindowMinutes, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::detectErrorPatterns, 
                5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::performRootCauseAnalysis, 
                30, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupOldErrors, 
                1, 1, TimeUnit.HOURS);

        this.errorProcessingExecutor = Executors.newFixedThreadPool(6);

        logger.info("ErrorTrackingConsumer initialized with analysis window: {} minutes", 
                   analysisWindowMinutes);
    }

    @KafkaListener(
        topics = "${kafka.topics.error-tracking:error-tracking}",
        groupId = "${kafka.consumer.group-id:monitoring-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "error-tracking-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "error-tracking-retry")
    public void processErrorTracking(
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
                logger.warn("Error tracking consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.debug("Processing error tracking message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidErrorMessage(messageNode)) {
                logger.error("Invalid error message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String eventType = messageNode.get("eventType").asText();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    switch (eventType) {
                        case "APPLICATION_ERROR":
                            handleApplicationError(messageNode, correlationId, traceId);
                            break;
                        case "SYSTEM_ERROR":
                            handleSystemError(messageNode, correlationId, traceId);
                            break;
                        case "DATABASE_ERROR":
                            handleDatabaseError(messageNode, correlationId, traceId);
                            break;
                        case "NETWORK_ERROR":
                            handleNetworkError(messageNode, correlationId, traceId);
                            break;
                        case "VALIDATION_ERROR":
                            handleValidationError(messageNode, correlationId, traceId);
                            break;
                        case "AUTHENTICATION_ERROR":
                            handleAuthenticationError(messageNode, correlationId, traceId);
                            break;
                        case "AUTHORIZATION_ERROR":
                            handleAuthorizationError(messageNode, correlationId, traceId);
                            break;
                        case "EXTERNAL_SERVICE_ERROR":
                            handleExternalServiceError(messageNode, correlationId, traceId);
                            break;
                        case "TIMEOUT_ERROR":
                            handleTimeoutError(messageNode, correlationId, traceId);
                            break;
                        case "RESOURCE_ERROR":
                            handleResourceError(messageNode, correlationId, traceId);
                            break;
                        case "CONFIGURATION_ERROR":
                            handleConfigurationError(messageNode, correlationId, traceId);
                            break;
                        case "BUSINESS_LOGIC_ERROR":
                            handleBusinessLogicError(messageNode, correlationId, traceId);
                            break;
                        case "INTEGRATION_ERROR":
                            handleIntegrationError(messageNode, correlationId, traceId);
                            break;
                        case "SECURITY_ERROR":
                            handleSecurityError(messageNode, correlationId, traceId);
                            break;
                        case "PERFORMANCE_ERROR":
                            handlePerformanceError(messageNode, correlationId, traceId);
                            break;
                        default:
                            logger.warn("Unknown error event type: {}", eventType);
                    }
                } catch (Exception e) {
                    logger.error("Error processing error tracking event: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, errorProcessingExecutor).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            totalErrorsTracked.incrementAndGet();
            processedCounter.increment();
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse error message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing error tracking: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    @Transactional
    private void handleApplicationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String serviceName = messageNode.get("serviceName").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            String exceptionType = messageNode.get("exceptionType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(messageNode.get("timestamp").asText());
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(serviceName);
            occurrence.setErrorType("APPLICATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setExceptionType(exceptionType);
            occurrence.setTimestamp(timestamp);
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            if (messageNode.has("stackTrace")) {
                occurrence.setStackTrace(messageNode.get("stackTrace").asText());
                analyzeStackTrace(occurrence);
            }
            
            if (messageNode.has("context")) {
                Map<String, Object> context = objectMapper.convertValue(
                    messageNode.get("context"), Map.class);
                occurrence.setContext(context);
            }
            
            if (messageNode.has("userId")) {
                occurrence.setUserId(messageNode.get("userId").asText());
            }
            
            if (messageNode.has("requestId")) {
                occurrence.setRequestId(messageNode.get("requestId").asText());
            }
            
            ErrorSeverity severity = determineSeverity(exceptionType, errorMessage);
            occurrence.setSeverity(severity);
            
            if (autoCategorisationEnabled) {
                ErrorCategory category = errorCategorisationService.categorizeError(occurrence);
                occurrence.setCategory(category);
            }
            
            processErrorOccurrence(occurrence);
            
            recordSeverityMetric(severity);
            errorOccurrenceCounter.increment();
            
            checkForErrorBurst(serviceName, occurrence);
            
            if (severity == ErrorSeverity.CRITICAL || severity == ErrorSeverity.HIGH) {
                createErrorAlert(occurrence);
            }
            
            logger.warn("Application error tracked: service={}, type={}, severity={}, message={}", 
                       serviceName, exceptionType, severity, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling application error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process application error", e);
        }
    }

    @Transactional
    private void handleSystemError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String component = messageNode.get("component").asText();
            String errorCode = messageNode.get("errorCode").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(component);
            occurrence.setErrorType("SYSTEM_ERROR");
            occurrence.setErrorCode(errorCode);
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.HIGH);
            
            if (messageNode.has("systemMetrics")) {
                JsonNode metrics = messageNode.get("systemMetrics");
                Map<String, Object> systemMetrics = new HashMap<>();
                metrics.fields().forEachRemaining(entry -> {
                    systemMetrics.put(entry.getKey(), entry.getValue().asText());
                });
                occurrence.setSystemMetrics(systemMetrics);
                
                analyzeSystemMetrics(occurrence, systemMetrics);
            }
            
            if (messageNode.has("affectedServices")) {
                JsonNode services = messageNode.get("affectedServices");
                List<String> affectedServices = new ArrayList<>();
                services.forEach(service -> affectedServices.add(service.asText()));
                occurrence.setAffectedServices(affectedServices);
            }
            
            processErrorOccurrence(occurrence);
            
            evaluateSystemImpact(occurrence);
            
            createSystemErrorAlert(occurrence);
            
            logger.error("System error tracked: component={}, code={}, message={}", 
                        component, errorCode, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling system error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process system error", e);
        }
    }

    @Transactional
    private void handleDatabaseError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String database = messageNode.get("database").asText();
            String operation = messageNode.get("operation").asText();
            String sqlState = messageNode.get("sqlState").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(database);
            occurrence.setErrorType("DATABASE_ERROR");
            occurrence.setErrorCode(sqlState);
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> dbContext = new HashMap<>();
            dbContext.put("operation", operation);
            dbContext.put("sqlState", sqlState);
            
            if (messageNode.has("query")) {
                String query = messageNode.get("query").asText();
                dbContext.put("query", query);
                
                analyzeProblematicQuery(query, sqlState);
            }
            
            if (messageNode.has("executionTimeMs")) {
                long executionTime = messageNode.get("executionTimeMs").asLong();
                dbContext.put("executionTimeMs", executionTime);
                
                if (executionTime > 10000) {
                    occurrence.setSeverity(ErrorSeverity.HIGH);
                } else {
                    occurrence.setSeverity(ErrorSeverity.MEDIUM);
                }
            }
            
            occurrence.setContext(dbContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeDatabaseErrorPattern(database, sqlState, operation);
            
            if (isConnectionPoolError(sqlState)) {
                handleConnectionPoolIssue(database, occurrence);
            }
            
            logger.warn("Database error tracked: db={}, operation={}, sqlState={}, message={}", 
                       database, operation, sqlState, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling database error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process database error", e);
        }
    }

    @Transactional
    private void handleNetworkError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String source = messageNode.get("source").asText();
            String target = messageNode.get("target").asText();
            String errorType = messageNode.get("networkErrorType").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(source);
            occurrence.setErrorType("NETWORK_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> networkContext = new HashMap<>();
            networkContext.put("source", source);
            networkContext.put("target", target);
            networkContext.put("networkErrorType", errorType);
            
            if (messageNode.has("latencyMs")) {
                networkContext.put("latencyMs", messageNode.get("latencyMs").asLong());
            }
            
            if (messageNode.has("retryCount")) {
                networkContext.put("retryCount", messageNode.get("retryCount").asInt());
            }
            
            occurrence.setContext(networkContext);
            
            ErrorSeverity severity = classifyNetworkError(errorType);
            occurrence.setSeverity(severity);
            
            processErrorOccurrence(occurrence);
            
            analyzeNetworkConnectivity(source, target, errorType);
            
            if ("CONNECTION_TIMEOUT".equals(errorType) || "READ_TIMEOUT".equals(errorType)) {
                handleTimeoutPattern(source, target, occurrence);
            }
            
            logger.warn("Network error tracked: {} -> {}, type={}, message={}", 
                       source, target, errorType, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling network error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process network error", e);
        }
    }

    @Transactional
    private void handleValidationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String field = messageNode.get("field").asText();
            String validationType = messageNode.get("validationType").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("VALIDATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.LOW);
            
            Map<String, Object> validationContext = new HashMap<>();
            validationContext.put("field", field);
            validationContext.put("validationType", validationType);
            
            if (messageNode.has("value")) {
                validationContext.put("rejectedValue", messageNode.get("value").asText());
            }
            
            if (messageNode.has("constraints")) {
                JsonNode constraints = messageNode.get("constraints");
                Map<String, String> constraintMap = new HashMap<>();
                constraints.fields().forEachRemaining(entry -> {
                    constraintMap.put(entry.getKey(), entry.getValue().asText());
                });
                validationContext.put("constraints", constraintMap);
            }
            
            occurrence.setContext(validationContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeValidationTrends(field, validationType);
            
            logger.debug("Validation error tracked: field={}, type={}, message={}", 
                        field, validationType, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling validation error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process validation error", e);
        }
    }

    @Transactional
    private void handleAuthenticationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String authMethod = messageNode.get("authMethod").asText();
            String username = messageNode.get("username").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            String ipAddress = messageNode.get("ipAddress").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("AUTHENTICATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.MEDIUM);
            
            Map<String, Object> authContext = new HashMap<>();
            authContext.put("authMethod", authMethod);
            authContext.put("username", username);
            authContext.put("ipAddress", ipAddress);
            
            if (messageNode.has("userAgent")) {
                authContext.put("userAgent", messageNode.get("userAgent").asText());
            }
            
            occurrence.setContext(authContext);
            
            processErrorOccurrence(occurrence);
            
            detectAuthenticationAttacks(username, ipAddress, occurrence);
            
            checkForBruteForcePattern(ipAddress, username);
            
            logger.warn("Authentication error tracked: method={}, user={}, ip={}, message={}", 
                       authMethod, username, ipAddress, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling authentication error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process authentication error", e);
        }
    }

    @Transactional
    private void handleAuthorizationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String userId = messageNode.get("userId").asText();
            String resource = messageNode.get("resource").asText();
            String action = messageNode.get("action").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("AUTHORIZATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.MEDIUM);
            occurrence.setUserId(userId);
            
            Map<String, Object> authzContext = new HashMap<>();
            authzContext.put("resource", resource);
            authzContext.put("action", action);
            
            if (messageNode.has("requiredPermissions")) {
                JsonNode permissions = messageNode.get("requiredPermissions");
                List<String> requiredPerms = new ArrayList<>();
                permissions.forEach(perm -> requiredPerms.add(perm.asText()));
                authzContext.put("requiredPermissions", requiredPerms);
            }
            
            occurrence.setContext(authzContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeAccessPatterns(userId, resource, action);
            
            detectPrivilegeEscalationAttempts(userId, resource, action);
            
            logger.warn("Authorization error tracked: user={}, resource={}, action={}, message={}", 
                       userId, resource, action, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling authorization error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process authorization error", e);
        }
    }

    @Transactional
    private void handleExternalServiceError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String serviceName = messageNode.get("serviceName").asText();
            String endpoint = messageNode.get("endpoint").asText();
            int statusCode = messageNode.get("statusCode").asInt();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(serviceName);
            occurrence.setErrorType("EXTERNAL_SERVICE_ERROR");
            occurrence.setErrorCode(String.valueOf(statusCode));
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> serviceContext = new HashMap<>();
            serviceContext.put("endpoint", endpoint);
            serviceContext.put("statusCode", statusCode);
            
            if (messageNode.has("responseTimeMs")) {
                serviceContext.put("responseTimeMs", messageNode.get("responseTimeMs").asLong());
            }
            
            if (messageNode.has("retryCount")) {
                serviceContext.put("retryCount", messageNode.get("retryCount").asInt());
            }
            
            occurrence.setContext(serviceContext);
            
            ErrorSeverity severity = classifyHttpError(statusCode);
            occurrence.setSeverity(severity);
            
            processErrorOccurrence(occurrence);
            
            trackServiceReliability(serviceName, endpoint, statusCode);
            
            if (statusCode >= 500) {
                handleDownstreamServiceFailure(serviceName, endpoint, occurrence);
            }
            
            logger.warn("External service error tracked: service={}, endpoint={}, status={}, message={}", 
                       serviceName, endpoint, statusCode, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling external service error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process external service error", e);
        }
    }

    @Transactional
    private void handleTimeoutError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String operation = messageNode.get("operation").asText();
            long timeoutMs = messageNode.get("timeoutMs").asLong();
            long elapsedMs = messageNode.get("elapsedMs").asLong();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("TIMEOUT_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> timeoutContext = new HashMap<>();
            timeoutContext.put("operation", operation);
            timeoutContext.put("timeoutMs", timeoutMs);
            timeoutContext.put("elapsedMs", elapsedMs);
            
            if (messageNode.has("targetService")) {
                String targetService = messageNode.get("targetService").asText();
                timeoutContext.put("targetService", targetService);
                occurrence.setServiceName(targetService);
            }
            
            occurrence.setContext(timeoutContext);
            
            ErrorSeverity severity = classifyTimeout(elapsedMs, timeoutMs);
            occurrence.setSeverity(severity);
            
            processErrorOccurrence(occurrence);
            
            analyzeTimeoutTrends(operation, timeoutMs, elapsedMs);
            
            if (elapsedMs > timeoutMs * 2) {
                createPerformanceDegradationAlert(operation, elapsedMs);
            }
            
            logger.warn("Timeout error tracked: operation={}, timeout={}ms, elapsed={}ms", 
                       operation, timeoutMs, elapsedMs);
            
        } catch (Exception e) {
            logger.error("Error handling timeout error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process timeout error", e);
        }
    }

    @Transactional
    private void handleResourceError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String resourceType = messageNode.get("resourceType").asText();
            String resourceId = messageNode.get("resourceId").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("RESOURCE_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.HIGH);
            
            Map<String, Object> resourceContext = new HashMap<>();
            resourceContext.put("resourceType", resourceType);
            resourceContext.put("resourceId", resourceId);
            
            if (messageNode.has("utilization")) {
                double utilization = messageNode.get("utilization").asDouble();
                resourceContext.put("utilization", utilization);
                
                if (utilization > 0.9) {
                    occurrence.setSeverity(ErrorSeverity.CRITICAL);
                }
            }
            
            if (messageNode.has("threshold")) {
                resourceContext.put("threshold", messageNode.get("threshold").asDouble());
            }
            
            occurrence.setContext(resourceContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeResourceExhaustion(resourceType, resourceId);
            
            createResourceErrorAlert(resourceType, resourceId, occurrence);
            
            logger.error("Resource error tracked: type={}, id={}, message={}", 
                        resourceType, resourceId, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling resource error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process resource error", e);
        }
    }

    @Transactional
    private void handleConfigurationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String configKey = messageNode.get("configKey").asText();
            String expectedType = messageNode.get("expectedType").asText();
            String actualValue = messageNode.get("actualValue").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("CONFIGURATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.HIGH);
            
            Map<String, Object> configContext = new HashMap<>();
            configContext.put("configKey", configKey);
            configContext.put("expectedType", expectedType);
            configContext.put("actualValue", actualValue);
            
            if (messageNode.has("configFile")) {
                configContext.put("configFile", messageNode.get("configFile").asText());
            }
            
            occurrence.setContext(configContext);
            
            processErrorOccurrence(occurrence);
            
            validateRelatedConfigurations(configKey);
            
            createConfigurationErrorAlert(configKey, occurrence);
            
            logger.error("Configuration error tracked: key={}, expected={}, actual={}, message={}", 
                        configKey, expectedType, actualValue, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling configuration error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process configuration error", e);
        }
    }

    @Transactional
    private void handleBusinessLogicError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String businessRule = messageNode.get("businessRule").asText();
            String violationType = messageNode.get("violationType").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("BUSINESS_LOGIC_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.MEDIUM);
            
            Map<String, Object> businessContext = new HashMap<>();
            businessContext.put("businessRule", businessRule);
            businessContext.put("violationType", violationType);
            
            if (messageNode.has("entityId")) {
                businessContext.put("entityId", messageNode.get("entityId").asText());
            }
            
            if (messageNode.has("ruleset")) {
                businessContext.put("ruleset", messageNode.get("ruleset").asText());
            }
            
            occurrence.setContext(businessContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeBusinessRuleViolations(businessRule, violationType);
            
            logger.warn("Business logic error tracked: rule={}, violation={}, message={}", 
                       businessRule, violationType, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling business logic error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process business logic error", e);
        }
    }

    @Transactional
    private void handleIntegrationError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String integrationName = messageNode.get("integrationName").asText();
            String operation = messageNode.get("operation").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setServiceName(integrationName);
            occurrence.setErrorType("INTEGRATION_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            occurrence.setSeverity(ErrorSeverity.HIGH);
            
            Map<String, Object> integrationContext = new HashMap<>();
            integrationContext.put("operation", operation);
            
            if (messageNode.has("dataFormat")) {
                integrationContext.put("dataFormat", messageNode.get("dataFormat").asText());
            }
            
            if (messageNode.has("transformationStep")) {
                integrationContext.put("transformationStep", messageNode.get("transformationStep").asText());
            }
            
            occurrence.setContext(integrationContext);
            
            processErrorOccurrence(occurrence);
            
            analyzeIntegrationReliability(integrationName, operation);
            
            createIntegrationErrorAlert(integrationName, operation, occurrence);
            
            logger.error("Integration error tracked: integration={}, operation={}, message={}", 
                        integrationName, operation, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling integration error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process integration error", e);
        }
    }

    @Transactional
    private void handleSecurityError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String securityEvent = messageNode.get("securityEvent").asText();
            String threatLevel = messageNode.get("threatLevel").asText();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("SECURITY_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> securityContext = new HashMap<>();
            securityContext.put("securityEvent", securityEvent);
            securityContext.put("threatLevel", threatLevel);
            
            if (messageNode.has("attackVector")) {
                securityContext.put("attackVector", messageNode.get("attackVector").asText());
            }
            
            if (messageNode.has("sourceIp")) {
                securityContext.put("sourceIp", messageNode.get("sourceIp").asText());
            }
            
            occurrence.setContext(securityContext);
            
            ErrorSeverity severity = classifySecurityThreat(threatLevel);
            occurrence.setSeverity(severity);
            
            processErrorOccurrence(occurrence);
            
            analyzeSecurityThreats(securityEvent, threatLevel);
            
            createSecurityErrorAlert(securityEvent, threatLevel, occurrence);
            
            logger.error("Security error tracked: event={}, threat={}, message={}", 
                        securityEvent, threatLevel, errorMessage);
            
        } catch (Exception e) {
            logger.error("Error handling security error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process security error", e);
        }
    }

    @Transactional
    private void handlePerformanceError(JsonNode messageNode, String correlationId, String traceId) {
        try {
            String errorId = UUID.randomUUID().toString();
            String operation = messageNode.get("operation").asText();
            long executionTimeMs = messageNode.get("executionTimeMs").asLong();
            long thresholdMs = messageNode.get("thresholdMs").asLong();
            String errorMessage = messageNode.get("errorMessage").asText();
            
            ErrorOccurrence occurrence = new ErrorOccurrence();
            occurrence.setErrorId(errorId);
            occurrence.setErrorType("PERFORMANCE_ERROR");
            occurrence.setErrorMessage(errorMessage);
            occurrence.setTimestamp(LocalDateTime.now());
            occurrence.setCorrelationId(correlationId);
            occurrence.setTraceId(traceId);
            
            Map<String, Object> perfContext = new HashMap<>();
            perfContext.put("operation", operation);
            perfContext.put("executionTimeMs", executionTimeMs);
            perfContext.put("thresholdMs", thresholdMs);
            perfContext.put("performanceRatio", (double) executionTimeMs / thresholdMs);
            
            if (messageNode.has("resourceUsage")) {
                JsonNode resources = messageNode.get("resourceUsage");
                Map<String, Object> resourceUsage = new HashMap<>();
                resources.fields().forEachRemaining(entry -> {
                    resourceUsage.put(entry.getKey(), entry.getValue().asDouble());
                });
                perfContext.put("resourceUsage", resourceUsage);
            }
            
            occurrence.setContext(perfContext);
            
            ErrorSeverity severity = classifyPerformanceIssue(executionTimeMs, thresholdMs);
            occurrence.setSeverity(severity);
            
            processErrorOccurrence(occurrence);
            
            analyzePerformanceDegradation(operation, executionTimeMs, thresholdMs);
            
            logger.warn("Performance error tracked: operation={}, time={}ms, threshold={}ms", 
                       operation, executionTimeMs, thresholdMs);
            
        } catch (Exception e) {
            logger.error("Error handling performance error: {}", e.getMessage(), e);
            throw new SystemException("Failed to process performance error", e);
        }
    }

    private void processErrorOccurrence(ErrorOccurrence occurrence) {
        String errorKey = generateErrorKey(occurrence);
        recentErrors.put(errorKey, occurrence);
        
        incrementErrorCount(occurrence.getErrorType());
        
        errorAnalysisService.recordError(occurrence);
        
        if (patternDetectionEnabled) {
            checkForSimilarErrors(occurrence);
        }
        
        updateErrorMetrics(occurrence);
    }

    private void performErrorAnalysis() {
        try {
            logger.debug("Performing error analysis for {} recent errors", recentErrors.size());
            
            Map<String, List<ErrorOccurrence>> groupedErrors = groupErrorsByType();
            
            for (Map.Entry<String, List<ErrorOccurrence>> entry : groupedErrors.entrySet()) {
                String errorType = entry.getKey();
                List<ErrorOccurrence> errors = entry.getValue();
                
                if (errors.size() >= burstThreshold) {
                    createErrorBurstAlert(errorType, errors);
                }
                
                analyzeErrorFrequency(errorType, errors);
            }
            
            calculateErrorRates();
            
        } catch (Exception e) {
            logger.error("Error performing error analysis: {}", e.getMessage(), e);
        }
    }

    private void detectErrorPatterns() {
        if (!patternDetectionEnabled) {
            return;
        }
        
        try {
            logger.debug("Detecting error patterns");
            
            List<ErrorPattern> patterns = errorAnalysisService.detectPatterns(
                new ArrayList<>(recentErrors.values()));
            
            for (ErrorPattern pattern : patterns) {
                detectedPatterns.put(pattern.getPatternId(), pattern);
                
                if (pattern.getConfidence() > 0.8) {
                    createPatternAlert(pattern);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error detecting patterns: {}", e.getMessage(), e);
        }
    }

    private void performRootCauseAnalysis() {
        if (!rootCauseAnalysisEnabled) {
            return;
        }
        
        try {
            logger.info("Performing root cause analysis");
            
            for (ErrorPattern pattern : detectedPatterns.values()) {
                Map<String, Object> rootCause = rootCauseAnalysisService.analyzePattern(pattern);
                
                if (rootCause != null) {
                    createRootCauseReport(pattern, rootCause);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error performing root cause analysis: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldErrors() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            
            recentErrors.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp().isBefore(cutoff));
            
            errorBursts.entrySet().removeIf(entry ->
                entry.getValue().stream().allMatch(error -> 
                    error.getTimestamp().isBefore(cutoff)));
            
            logger.debug("Cleaned up old errors before {}", cutoff);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old errors: {}", e.getMessage(), e);
        }
    }

    // Helper methods
    private boolean isValidErrorMessage(JsonNode messageNode) {
        return messageNode != null &&
               messageNode.has("eventType") && 
               StringUtils.hasText(messageNode.get("eventType").asText()) &&
               messageNode.has("errorMessage");
    }

    private String generateErrorKey(ErrorOccurrence occurrence) {
        return occurrence.getErrorType() + ":" + 
               occurrence.getServiceName() + ":" + 
               occurrence.getErrorMessage().hashCode();
    }

    private ErrorSeverity determineSeverity(String exceptionType, String errorMessage) {
        if (exceptionType.contains("OutOfMemory") || exceptionType.contains("StackOverflow")) {
            return ErrorSeverity.CRITICAL;
        }
        if (exceptionType.contains("Runtime") || exceptionType.contains("SQL")) {
            return ErrorSeverity.HIGH;
        }
        if (exceptionType.contains("Validation") || exceptionType.contains("IllegalArgument")) {
            return ErrorSeverity.LOW;
        }
        return ErrorSeverity.MEDIUM;
    }

    private ErrorSeverity classifyNetworkError(String errorType) {
        switch (errorType) {
            case "CONNECTION_REFUSED":
            case "HOST_UNREACHABLE":
                return ErrorSeverity.HIGH;
            case "CONNECTION_TIMEOUT":
            case "READ_TIMEOUT":
                return ErrorSeverity.MEDIUM;
            default:
                return ErrorSeverity.LOW;
        }
    }

    private ErrorSeverity classifyHttpError(int statusCode) {
        if (statusCode >= 500) return ErrorSeverity.HIGH;
        if (statusCode >= 400) return ErrorSeverity.MEDIUM;
        return ErrorSeverity.LOW;
    }

    private ErrorSeverity classifyTimeout(long elapsed, long timeout) {
        double ratio = (double) elapsed / timeout;
        if (ratio > 3) return ErrorSeverity.HIGH;
        if (ratio > 2) return ErrorSeverity.MEDIUM;
        return ErrorSeverity.LOW;
    }

    private ErrorSeverity classifySecurityThreat(String threatLevel) {
        switch (threatLevel.toUpperCase()) {
            case "CRITICAL": return ErrorSeverity.CRITICAL;
            case "HIGH": return ErrorSeverity.HIGH;
            case "MEDIUM": return ErrorSeverity.MEDIUM;
            default: return ErrorSeverity.LOW;
        }
    }

    private ErrorSeverity classifyPerformanceIssue(long actual, long threshold) {
        double ratio = (double) actual / threshold;
        if (ratio > 5) return ErrorSeverity.CRITICAL;
        if (ratio > 3) return ErrorSeverity.HIGH;
        if (ratio > 2) return ErrorSeverity.MEDIUM;
        return ErrorSeverity.LOW;
    }

    private void recordSeverityMetric(ErrorSeverity severity) {
        errorSeverityDistribution.record(severity.ordinal());
    }

    private void incrementErrorCount(String errorType) {
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
    }

    private double calculateCurrentErrorRate() {
        long totalErrors = errorCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
        
        long totalRequests = totalErrorsTracked.get() * 10; // Estimate
        
        return totalRequests > 0 ? (double) totalErrors / totalRequests : 0.0;
    }

    private void updateErrorMetrics(ErrorOccurrence occurrence) {
        metricsService.recordError(occurrence.getErrorType(), occurrence.getSeverity());
    }

    private Map<String, List<ErrorOccurrence>> groupErrorsByType() {
        return recentErrors.values().stream()
            .collect(Collectors.groupingBy(ErrorOccurrence::getErrorType));
    }

    private void checkForErrorBurst(String serviceName, ErrorOccurrence occurrence) {
        String key = serviceName + ":" + occurrence.getErrorType();
        errorBursts.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(occurrence);
        
        List<ErrorOccurrence> burst = errorBursts.get(key);
        if (burst.size() >= burstThreshold) {
            createErrorBurstAlert(serviceName, burst);
        }
    }

    private void checkForSimilarErrors(ErrorOccurrence occurrence) {
        for (ErrorOccurrence existing : recentErrors.values()) {
            double similarity = calculateSimilarity(occurrence, existing);
            if (similarity > similarityThreshold) {
                linkSimilarErrors(occurrence, existing);
            }
        }
    }

    private double calculateSimilarity(ErrorOccurrence error1, ErrorOccurrence error2) {
        return errorAnalysisService.calculateSimilarity(error1, error2);
    }

    private void linkSimilarErrors(ErrorOccurrence error1, ErrorOccurrence error2) {
        errorAnalysisService.linkSimilarErrors(error1, error2);
    }

    private void analyzeErrorFrequency(String errorType, List<ErrorOccurrence> errors) {
        errorAnalysisService.analyzeFrequency(errorType, errors);
    }

    private void calculateErrorRates() {
        errorAnalysisService.calculateErrorRates(errorCounts);
    }

    private void createErrorAlert(ErrorOccurrence occurrence) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "ERROR_OCCURRENCE");
        alert.put("errorId", occurrence.getErrorId());
        alert.put("errorType", occurrence.getErrorType());
        alert.put("severity", occurrence.getSeverity());
        alert.put("serviceName", occurrence.getServiceName());
        alert.put("message", occurrence.getErrorMessage());
        alert.put("timestamp", occurrence.getTimestamp().toString());
        
        alertingService.createAlert(alert);
    }

    private void createErrorBurstAlert(String errorType, List<ErrorOccurrence> errors) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "ERROR_BURST");
        alert.put("errorType", errorType);
        alert.put("errorCount", errors.size());
        alert.put("timeWindow", analysisWindowMinutes + " minutes");
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("error-burst-alerts", alert);
    }

    private void createPatternAlert(ErrorPattern pattern) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "ERROR_PATTERN");
        alert.put("patternId", pattern.getPatternId());
        alert.put("pattern", pattern.getPattern());
        alert.put("confidence", pattern.getConfidence());
        alert.put("occurrences", pattern.getOccurrences());
        alert.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("error-pattern-alerts", alert);
    }

    private void createRootCauseReport(ErrorPattern pattern, Map<String, Object> rootCause) {
        Map<String, Object> report = new HashMap<>();
        report.put("patternId", pattern.getPatternId());
        report.put("rootCause", rootCause);
        report.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send("root-cause-reports", report);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing error tracking message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for error tracking consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    // Additional helper method stubs (to keep implementation size manageable)
    private void analyzeStackTrace(ErrorOccurrence occurrence) { /* Implementation */ }
    private void evaluateSystemImpact(ErrorOccurrence occurrence) { /* Implementation */ }
    private void createSystemErrorAlert(ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeProblematicQuery(String query, String sqlState) { /* Implementation */ }
    private void analyzeDatabaseErrorPattern(String database, String sqlState, String operation) { /* Implementation */ }
    private boolean isConnectionPoolError(String sqlState) { return sqlState.startsWith("08"); }
    private void handleConnectionPoolIssue(String database, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeNetworkConnectivity(String source, String target, String errorType) { /* Implementation */ }
    private void handleTimeoutPattern(String source, String target, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeValidationTrends(String field, String validationType) { /* Implementation */ }
    private void detectAuthenticationAttacks(String username, String ipAddress, ErrorOccurrence occurrence) { /* Implementation */ }
    private void checkForBruteForcePattern(String ipAddress, String username) { /* Implementation */ }
    private void analyzeAccessPatterns(String userId, String resource, String action) { /* Implementation */ }
    private void detectPrivilegeEscalationAttempts(String userId, String resource, String action) { /* Implementation */ }
    private void trackServiceReliability(String serviceName, String endpoint, int statusCode) { /* Implementation */ }
    private void handleDownstreamServiceFailure(String serviceName, String endpoint, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeTimeoutTrends(String operation, long timeoutMs, long elapsedMs) { /* Implementation */ }
    private void createPerformanceDegradationAlert(String operation, long elapsedMs) { /* Implementation */ }
    private void analyzeResourceExhaustion(String resourceType, String resourceId) { /* Implementation */ }
    private void createResourceErrorAlert(String resourceType, String resourceId, ErrorOccurrence occurrence) { /* Implementation */ }
    private void validateRelatedConfigurations(String configKey) { /* Implementation */ }
    private void createConfigurationErrorAlert(String configKey, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeBusinessRuleViolations(String businessRule, String violationType) { /* Implementation */ }
    private void analyzeIntegrationReliability(String integrationName, String operation) { /* Implementation */ }
    private void createIntegrationErrorAlert(String integrationName, String operation, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzeSecurityThreats(String securityEvent, String threatLevel) { /* Implementation */ }
    private void createSecurityErrorAlert(String securityEvent, String threatLevel, ErrorOccurrence occurrence) { /* Implementation */ }
    private void analyzePerformanceDegradation(String operation, long executionTimeMs, long thresholdMs) { /* Implementation */ }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down ErrorTrackingConsumer");
        
        performErrorAnalysis();
        
        scheduledExecutor.shutdown();
        errorProcessingExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!errorProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                errorProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
            scheduledExecutor.shutdownNow();
            errorProcessingExecutor.shutdownNow();
        }
        
        logger.info("ErrorTrackingConsumer shutdown complete. Total errors tracked: {}", 
                   totalErrorsTracked.get());
    }
}