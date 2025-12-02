package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.KafkaRetryHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.monitoring.model.MonitoringEvent;
import com.waqiti.monitoring.service.AlertingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class UserExperienceMetricsConsumer extends BaseKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(UserExperienceMetricsConsumer.class);
    private static final String CONSUMER_GROUP_ID = "user-experience-metrics-group";
    private static final String DLQ_TOPIC = "user-experience-metrics-dlq";
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final KafkaRetryHandler retryHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.ux.page-load-threshold-ms:3000}")
    private long pageLoadThresholdMs;
    
    @Value("${monitoring.ux.interaction-delay-threshold-ms:100}")
    private long interactionDelayThresholdMs;
    
    @Value("${monitoring.ux.error-rate-threshold:0.05}")
    private double errorRateThreshold;
    
    @Value("${monitoring.ux.bounce-rate-threshold:0.4}")
    private double bounceRateThreshold;
    
    @Value("${monitoring.ux.session-duration-threshold-seconds:120}")
    private long sessionDurationThresholdSeconds;
    
    @Value("${monitoring.ux.rage-click-threshold:3}")
    private int rageClickThreshold;
    
    @Value("${monitoring.ux.abandonment-rate-threshold:0.3}")
    private double abandonmentRateThreshold;
    
    @Value("${monitoring.ux.satisfaction-score-threshold:7}")
    private double satisfactionScoreThreshold;
    
    @Value("${monitoring.ux.accessibility-score-threshold:0.8}")
    private double accessibilityScoreThreshold;
    
    @Value("${monitoring.ux.analysis-window-minutes:15}")
    private int analysisWindowMinutes;
    
    @Value("${monitoring.ux.heatmap-enabled:true}")
    private boolean heatmapEnabled;
    
    @Value("${monitoring.ux.session-replay-enabled:true}")
    private boolean sessionReplayEnabled;
    
    private final Map<String, PageLoadMetrics> pageLoadMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, InteractionMetrics> interactionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, NavigationMetrics> navigationMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ErrorMetrics> errorMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, SessionMetrics> sessionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, EngagementMetrics> engagementMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, FormMetrics> formMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ClickstreamData> clickstreamMap = new ConcurrentHashMap<>();
    private final Map<String, UserJourney> userJourneyMap = new ConcurrentHashMap<>();
    private final Map<String, FrustrationSignals> frustrationMap = new ConcurrentHashMap<>();
    private final Map<String, AccessibilityMetrics> accessibilityMap = new ConcurrentHashMap<>();
    private final Map<String, DeviceMetrics> deviceMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, FeedbackMetrics> feedbackMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, SearchMetrics> searchMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, UxScorecard> uxScorecardMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Counter slowPageLoadCounter;
    private Counter rageClickCounter;
    private Counter abandonmentCounter;
    private Gauge satisfactionGauge;
    private Gauge bounceRateGauge;
    private Gauge engagementGauge;
    private Timer processingTimer;
    private Timer pageLoadTimer;
    private Timer interactionTimer;

    public UserExperienceMetricsConsumer(
            MetricsService metricsService,
            AlertingService alertingService,
            KafkaRetryHandler retryHandler,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.alertingService = alertingService;
        this.retryHandler = retryHandler;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        startScheduledTasks();
        initializeUxTracking();
        logger.info("UserExperienceMetricsConsumer initialized with page load threshold: {}ms", 
                    pageLoadThresholdMs);
    }
    
    private void initializeMetrics() {
        processedEventsCounter = Counter.builder("ux.metrics.processed")
            .description("Total UX metrics events processed")
            .register(meterRegistry);
            
        errorCounter = Counter.builder("ux.metrics.errors")
            .description("Total UX metrics errors")
            .register(meterRegistry);
            
        dlqCounter = Counter.builder("ux.metrics.dlq")
            .description("Total messages sent to DLQ")
            .register(meterRegistry);
            
        slowPageLoadCounter = Counter.builder("ux.slow_page_loads")
            .description("Total slow page loads")
            .register(meterRegistry);
            
        rageClickCounter = Counter.builder("ux.rage_clicks")
            .description("Total rage click events")
            .register(meterRegistry);
            
        abandonmentCounter = Counter.builder("ux.abandonments")
            .description("Total abandonment events")
            .register(meterRegistry);
            
        satisfactionGauge = Gauge.builder("ux.satisfaction_score", this, 
            consumer -> calculateOverallSatisfaction())
            .description("Overall user satisfaction score")
            .register(meterRegistry);
            
        bounceRateGauge = Gauge.builder("ux.bounce_rate", this,
            consumer -> calculateBounceRate())
            .description("Overall bounce rate")
            .register(meterRegistry);
            
        engagementGauge = Gauge.builder("ux.engagement_score", this,
            consumer -> calculateEngagementScore())
            .description("Overall engagement score")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("ux.metrics.processing.time")
            .description("UX metrics processing time")
            .register(meterRegistry);
            
        pageLoadTimer = Timer.builder("ux.page_load.time")
            .description("Page load time")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .register(meterRegistry);
            
        interactionTimer = Timer.builder("ux.interaction.time")
            .description("User interaction response time")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .build();
            
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig)
            .circuitBreaker("ux-metrics", circuitBreakerConfig);
            
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
            
        retry = RetryRegistry.of(retryConfig).retry("ux-metrics", retryConfig);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> logger.warn("Circuit breaker state transition: {}", event));
    }
    
    private void startScheduledTasks() {
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeUserExperience, 
            0, analysisWindowMinutes, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::detectFrustrationPatterns, 
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::calculateUxScores, 
            0, 10, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeUserJourneys, 
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateHeatmaps, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processSessionReplays, 
            0, 15, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateUxReports, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupOldData, 
            0, 24, TimeUnit.HOURS
        );
    }
    
    private void initializeUxTracking() {
        List<String> criticalPages = Arrays.asList(
            "login", "checkout", "payment", "dashboard", "profile", "transfer"
        );
        
        criticalPages.forEach(page -> {
            PageLoadMetrics metrics = new PageLoadMetrics(page);
            pageLoadMetricsMap.put(page, metrics);
        });
        
        UxScorecard scorecard = new UxScorecard();
        uxScorecardMap.put("overall", scorecard);
    }
    
    @KafkaListener(
        topics = "user-experience-metrics-events",
        groupId = CONSUMER_GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
        Acknowledgment acknowledgment
    ) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            processUxMetricsEvent(message, timestamp);
            acknowledgment.acknowledge();
            processedEventsCounter.increment();
            
        } catch (Exception e) {
            handleProcessingError(message, e, acknowledgment);
            
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private void processUxMetricsEvent(String message, long timestamp) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("type").asText();
        String eventId = event.path("eventId").asText();
        
        logger.debug("Processing UX metrics event: {} - {}", eventType, eventId);
        
        Callable<Void> processTask = () -> {
            switch (eventType) {
                case "PAGE_LOAD":
                    handlePageLoad(event, timestamp);
                    break;
                case "USER_INTERACTION":
                    handleUserInteraction(event, timestamp);
                    break;
                case "NAVIGATION_EVENT":
                    handleNavigationEvent(event, timestamp);
                    break;
                case "CLIENT_ERROR":
                    handleClientError(event, timestamp);
                    break;
                case "SESSION_DATA":
                    handleSessionData(event, timestamp);
                    break;
                case "ENGAGEMENT_METRIC":
                    handleEngagementMetric(event, timestamp);
                    break;
                case "FORM_INTERACTION":
                    handleFormInteraction(event, timestamp);
                    break;
                case "CLICKSTREAM_DATA":
                    handleClickstreamData(event, timestamp);
                    break;
                case "USER_JOURNEY_STEP":
                    handleUserJourneyStep(event, timestamp);
                    break;
                case "FRUSTRATION_SIGNAL":
                    handleFrustrationSignal(event, timestamp);
                    break;
                case "ACCESSIBILITY_ISSUE":
                    handleAccessibilityIssue(event, timestamp);
                    break;
                case "DEVICE_METRICS":
                    handleDeviceMetrics(event, timestamp);
                    break;
                case "USER_FEEDBACK":
                    handleUserFeedback(event, timestamp);
                    break;
                case "SEARCH_INTERACTION":
                    handleSearchInteraction(event, timestamp);
                    break;
                case "SCROLL_BEHAVIOR":
                    handleScrollBehavior(event, timestamp);
                    break;
                default:
                    logger.warn("Unknown UX metrics event type: {}", eventType);
            }
            return null;
        };
        
        Retry.decorateCallable(retry, processTask).call();
    }
    
    private void handlePageLoad(JsonNode event, long timestamp) {
        String pageId = event.path("pageId").asText();
        String pageName = event.path("pageName").asText();
        long loadTime = event.path("loadTime").asLong();
        long domContentLoaded = event.path("domContentLoaded").asLong();
        long firstPaint = event.path("firstPaint").asLong();
        long firstContentfulPaint = event.path("firstContentfulPaint").asLong();
        long largestContentfulPaint = event.path("largestContentfulPaint").asLong();
        long timeToInteractive = event.path("timeToInteractive").asLong();
        long totalBlockingTime = event.path("totalBlockingTime").asLong();
        double cumulativeLayoutShift = event.path("cumulativeLayoutShift").asDouble();
        String userId = event.path("userId").asText();
        String sessionId = event.path("sessionId").asText();
        
        PageLoadMetrics metrics = pageLoadMetricsMap.computeIfAbsent(
            pageId, k -> new PageLoadMetrics(pageName)
        );
        
        metrics.recordPageLoad(
            loadTime, domContentLoaded, firstPaint, firstContentfulPaint,
            largestContentfulPaint, timeToInteractive, totalBlockingTime,
            cumulativeLayoutShift, timestamp
        );
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("ux.page_load.detailed")
            .tag("page", pageName)
            .register(meterRegistry));
        
        if (loadTime > pageLoadThresholdMs) {
            slowPageLoadCounter.increment();
            
            alertingService.sendAlert(
                "SLOW_PAGE_LOAD",
                "Medium",
                String.format("Slow page load for %s: %dms (threshold: %dms)",
                    pageName, loadTime, pageLoadThresholdMs),
                Map.of(
                    "page", pageName,
                    "loadTime", String.valueOf(loadTime),
                    "lcp", String.valueOf(largestContentfulPaint),
                    "tti", String.valueOf(timeToInteractive),
                    "userId", userId
                )
            );
        }
        
        double performanceScore = calculatePerformanceScore(
            loadTime, largestContentfulPaint, totalBlockingTime, cumulativeLayoutShift
        );
        
        metrics.setPerformanceScore(performanceScore);
        
        metricsService.recordMetric("ux.page_load_time", loadTime,
            Map.of("page", pageName));
        
        metricsService.recordMetric("ux.core_web_vitals.lcp", largestContentfulPaint,
            Map.of("page", pageName));
        
        metricsService.recordMetric("ux.core_web_vitals.fid", timeToInteractive,
            Map.of("page", pageName));
        
        metricsService.recordMetric("ux.core_web_vitals.cls", cumulativeLayoutShift,
            Map.of("page", pageName));
    }
    
    private void handleUserInteraction(JsonNode event, long timestamp) {
        String interactionType = event.path("interactionType").asText();
        String elementId = event.path("elementId").asText();
        String elementType = event.path("elementType").asText();
        long responseTime = event.path("responseTime").asLong();
        boolean successful = event.path("successful").asBoolean();
        String pageContext = event.path("pageContext").asText();
        String userId = event.path("userId").asText();
        JsonNode metadata = event.path("metadata");
        
        InteractionMetrics metrics = interactionMetricsMap.computeIfAbsent(
            interactionType, k -> new InteractionMetrics(interactionType)
        );
        
        metrics.recordInteraction(
            elementId, elementType, responseTime, successful, pageContext, timestamp
        );
        
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("ux.interaction.response")
            .tag("type", interactionType)
            .tag("element", elementType)
            .register(meterRegistry));
        
        if (responseTime > interactionDelayThresholdMs) {
            metrics.incrementSlowInteractions();
            
            if (metrics.getSlowInteractionRate() > 0.2) {
                alertingService.sendAlert(
                    "HIGH_INTERACTION_DELAY",
                    "Medium",
                    String.format("High interaction delay for %s: %dms",
                        interactionType, responseTime),
                    Map.of(
                        "interaction", interactionType,
                        "element", elementId,
                        "responseTime", String.valueOf(responseTime),
                        "page", pageContext
                    )
                );
            }
        }
        
        if (!successful) {
            metrics.incrementFailedInteractions();
        }
        
        metricsService.recordMetric("ux.interaction_time", responseTime,
            Map.of("type", interactionType, "success", String.valueOf(successful)));
    }
    
    private void handleNavigationEvent(JsonNode event, long timestamp) {
        String fromPage = event.path("fromPage").asText();
        String toPage = event.path("toPage").asText();
        String navigationType = event.path("navigationType").asText();
        long transitionTime = event.path("transitionTime").asLong();
        String userId = event.path("userId").asText();
        String sessionId = event.path("sessionId").asText();
        boolean isBackNavigation = event.path("isBackNavigation").asBoolean();
        
        NavigationMetrics metrics = navigationMetricsMap.computeIfAbsent(
            sessionId, k -> new NavigationMetrics(sessionId)
        );
        
        metrics.recordNavigation(
            fromPage, toPage, navigationType, transitionTime, isBackNavigation, timestamp
        );
        
        if (isBackNavigation) {
            metrics.incrementBackNavigations();
            
            if (metrics.getBackNavigationRate() > 0.3) {
                detectNavigationIssue(sessionId, metrics);
            }
        }
        
        UserJourney journey = userJourneyMap.computeIfAbsent(
            sessionId, k -> new UserJourney(sessionId, userId)
        );
        
        journey.addStep(toPage, timestamp);
        
        metricsService.recordMetric("ux.navigation_time", transitionTime,
            Map.of("from", fromPage, "to", toPage, "type", navigationType));
    }
    
    private void handleClientError(JsonNode event, long timestamp) {
        String errorType = event.path("errorType").asText();
        String errorMessage = event.path("errorMessage").asText();
        String pageContext = event.path("pageContext").asText();
        String stackTrace = event.path("stackTrace").asText("");
        String userId = event.path("userId").asText();
        String browser = event.path("browser").asText();
        int errorCount = event.path("errorCount").asInt(1);
        
        ErrorMetrics metrics = errorMetricsMap.computeIfAbsent(
            pageContext, k -> new ErrorMetrics(pageContext)
        );
        
        metrics.recordError(errorType, errorMessage, browser, errorCount, timestamp);
        
        if (metrics.getErrorRate() > errorRateThreshold) {
            alertingService.sendAlert(
                "HIGH_CLIENT_ERROR_RATE",
                "High",
                String.format("High client error rate on %s: %.2f%%",
                    pageContext, metrics.getErrorRate() * 100),
                Map.of(
                    "page", pageContext,
                    "errorType", errorType,
                    "message", errorMessage,
                    "errorRate", String.valueOf(metrics.getErrorRate()),
                    "browser", browser
                )
            );
        }
        
        metricsService.recordMetric("ux.client_errors", errorCount,
            Map.of("type", errorType, "page", pageContext, "browser", browser));
    }
    
    private void handleSessionData(JsonNode event, long timestamp) {
        String sessionId = event.path("sessionId").asText();
        String userId = event.path("userId").asText();
        long sessionDuration = event.path("sessionDuration").asLong();
        int pageViews = event.path("pageViews").asInt();
        int interactions = event.path("interactions").asInt();
        boolean bounced = event.path("bounced").asBoolean();
        String entryPage = event.path("entryPage").asText();
        String exitPage = event.path("exitPage").asText();
        String deviceType = event.path("deviceType").asText();
        
        SessionMetrics metrics = sessionMetricsMap.computeIfAbsent(
            sessionId, k -> new SessionMetrics(sessionId, userId)
        );
        
        metrics.updateSession(
            sessionDuration, pageViews, interactions, bounced,
            entryPage, exitPage, deviceType, timestamp
        );
        
        if (bounced) {
            metrics.markBounced();
            
            NavigationMetrics navMetrics = navigationMetricsMap.get(sessionId);
            if (navMetrics != null) {
                navMetrics.setBounced(true);
            }
        }
        
        if (sessionDuration < sessionDurationThresholdSeconds && pageViews > 1) {
            detectQuickExit(sessionId, metrics);
        }
        
        double engagementScore = calculateSessionEngagement(
            sessionDuration, pageViews, interactions
        );
        metrics.setEngagementScore(engagementScore);
        
        metricsService.recordMetric("ux.session_duration", sessionDuration,
            Map.of("device", deviceType, "bounced", String.valueOf(bounced)));
        
        metricsService.recordMetric("ux.page_views_per_session", pageViews,
            Map.of("device", deviceType));
    }
    
    private void handleEngagementMetric(JsonNode event, long timestamp) {
        String metricType = event.path("metricType").asText();
        String pageContext = event.path("pageContext").asText();
        double value = event.path("value").asDouble();
        String userId = event.path("userId").asText();
        String sessionId = event.path("sessionId").asText();
        JsonNode details = event.path("details");
        
        EngagementMetrics metrics = engagementMetricsMap.computeIfAbsent(
            pageContext, k -> new EngagementMetrics(pageContext)
        );
        
        switch (metricType) {
            case "SCROLL_DEPTH":
                double scrollDepth = value;
                metrics.recordScrollDepth(scrollDepth, timestamp);
                break;
            case "TIME_ON_PAGE":
                long timeOnPage = (long) value;
                metrics.recordTimeOnPage(timeOnPage, timestamp);
                break;
            case "VIDEO_WATCH_TIME":
                long watchTime = (long) value;
                String videoId = details.path("videoId").asText();
                metrics.recordVideoEngagement(videoId, watchTime, timestamp);
                break;
            case "CONTENT_INTERACTION":
                String contentType = details.path("contentType").asText();
                metrics.recordContentInteraction(contentType, timestamp);
                break;
        }
        
        metricsService.recordMetric("ux.engagement." + metricType.toLowerCase(), value,
            Map.of("page", pageContext));
    }
    
    private void handleFormInteraction(JsonNode event, long timestamp) {
        String formId = event.path("formId").asText();
        String formName = event.path("formName").asText();
        String interactionType = event.path("interactionType").asText();
        String fieldName = event.path("fieldName").asText("");
        long timeSpent = event.path("timeSpent").asLong();
        boolean completed = event.path("completed").asBoolean();
        boolean hasErrors = event.path("hasErrors").asBoolean();
        int attemptCount = event.path("attemptCount").asInt(1);
        String abandonReason = event.path("abandonReason").asText("");
        
        FormMetrics metrics = formMetricsMap.computeIfAbsent(
            formId, k -> new FormMetrics(formId, formName)
        );
        
        switch (interactionType) {
            case "START":
                metrics.recordStart(timestamp);
                break;
            case "FIELD_INTERACTION":
                metrics.recordFieldInteraction(fieldName, timeSpent, timestamp);
                break;
            case "SUBMIT":
                metrics.recordSubmit(completed, hasErrors, attemptCount, timestamp);
                break;
            case "ABANDON":
                metrics.recordAbandon(fieldName, abandonReason, timestamp);
                abandonmentCounter.increment();
                break;
        }
        
        if (metrics.getAbandonmentRate() > abandonmentRateThreshold) {
            alertingService.sendAlert(
                "HIGH_FORM_ABANDONMENT",
                "High",
                String.format("High form abandonment rate for %s: %.2f%%",
                    formName, metrics.getAbandonmentRate() * 100),
                Map.of(
                    "formId", formId,
                    "formName", formName,
                    "abandonmentRate", String.valueOf(metrics.getAbandonmentRate()),
                    "problemField", metrics.getMostProblematicField()
                )
            );
        }
        
        metricsService.recordMetric("ux.form_completion_rate", 
            completed ? 1.0 : 0.0,
            Map.of("form", formName));
    }
    
    private void handleClickstreamData(JsonNode event, long timestamp) {
        String sessionId = event.path("sessionId").asText();
        String userId = event.path("userId").asText();
        String elementPath = event.path("elementPath").asText();
        int x = event.path("x").asInt();
        int y = event.path("y").asInt();
        String pageContext = event.path("pageContext").asText();
        String clickType = event.path("clickType").asText();
        
        ClickstreamData clickstream = clickstreamMap.computeIfAbsent(
            sessionId, k -> new ClickstreamData(sessionId, userId)
        );
        
        clickstream.addClick(elementPath, x, y, pageContext, clickType, timestamp);
        
        if (heatmapEnabled) {
            updateHeatmap(pageContext, x, y);
        }
        
        if ("RAGE".equals(clickType)) {
            rageClickCounter.increment();
            handleRageClick(sessionId, pageContext, elementPath);
        }
        
        metricsService.recordMetric("ux.clicks", 1.0,
            Map.of("page", pageContext, "type", clickType));
    }
    
    private void handleUserJourneyStep(JsonNode event, long timestamp) {
        String journeyId = event.path("journeyId").asText();
        String userId = event.path("userId").asText();
        String stepName = event.path("stepName").asText();
        int stepNumber = event.path("stepNumber").asInt();
        String outcome = event.path("outcome").asText();
        long stepDuration = event.path("stepDuration").asLong();
        JsonNode metadata = event.path("metadata");
        
        UserJourney journey = userJourneyMap.computeIfAbsent(
            journeyId, k -> new UserJourney(journeyId, userId)
        );
        
        journey.recordStep(stepName, stepNumber, outcome, stepDuration, timestamp);
        
        if ("ABANDONED".equals(outcome)) {
            String abandonPoint = stepName;
            journey.setAbandoned(true, abandonPoint);
            
            alertingService.sendAlert(
                "JOURNEY_ABANDONED",
                "Medium",
                String.format("User journey abandoned at step: %s", abandonPoint),
                Map.of(
                    "journeyId", journeyId,
                    "userId", userId,
                    "abandonPoint", abandonPoint,
                    "stepNumber", String.valueOf(stepNumber)
                )
            );
        }
        
        if ("COMPLETED".equals(outcome)) {
            journey.setCompleted(true);
            double conversionTime = journey.getTotalDuration();
            
            metricsService.recordMetric("ux.journey_completion_time", conversionTime,
                Map.of("journey", journey.getJourneyType()));
        }
        
        metricsService.recordMetric("ux.journey_step_duration", stepDuration,
            Map.of("step", stepName, "outcome", outcome));
    }
    
    private void handleFrustrationSignal(JsonNode event, long timestamp) {
        String signalType = event.path("signalType").asText();
        String userId = event.path("userId").asText();
        String sessionId = event.path("sessionId").asText();
        String pageContext = event.path("pageContext").asText();
        String elementId = event.path("elementId").asText("");
        int intensity = event.path("intensity").asInt(1);
        JsonNode context = event.path("context");
        
        FrustrationSignals frustration = frustrationMap.computeIfAbsent(
            sessionId, k -> new FrustrationSignals(sessionId, userId)
        );
        
        frustration.addSignal(signalType, pageContext, elementId, intensity, timestamp);
        
        switch (signalType) {
            case "RAGE_CLICK":
                frustration.incrementRageClicks();
                break;
            case "DEAD_CLICK":
                frustration.incrementDeadClicks();
                break;
            case "RAPID_SCROLLING":
                frustration.incrementRapidScrolling();
                break;
            case "FORM_STRUGGLE":
                frustration.incrementFormStruggles();
                break;
            case "ERROR_LOOP":
                frustration.incrementErrorLoops();
                break;
        }
        
        if (frustration.getFrustrationScore() > 5) {
            alertingService.sendAlert(
                "HIGH_USER_FRUSTRATION",
                "High",
                String.format("High user frustration detected for session %s on %s",
                    sessionId, pageContext),
                Map.of(
                    "sessionId", sessionId,
                    "userId", userId,
                    "page", pageContext,
                    "frustrationScore", String.valueOf(frustration.getFrustrationScore()),
                    "primarySignal", signalType
                )
            );
        }
        
        metricsService.recordMetric("ux.frustration_signal", intensity,
            Map.of("type", signalType, "page", pageContext));
    }
    
    private void handleAccessibilityIssue(JsonNode event, long timestamp) {
        String issueType = event.path("issueType").asText();
        String severity = event.path("severity").asText();
        String element = event.path("element").asText();
        String pageContext = event.path("pageContext").asText();
        String wcagCriteria = event.path("wcagCriteria").asText("");
        String description = event.path("description").asText();
        int occurrences = event.path("occurrences").asInt(1);
        
        AccessibilityMetrics metrics = accessibilityMap.computeIfAbsent(
            pageContext, k -> new AccessibilityMetrics(pageContext)
        );
        
        metrics.recordIssue(issueType, severity, element, wcagCriteria, occurrences, timestamp);
        
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            alertingService.sendAlert(
                "ACCESSIBILITY_ISSUE",
                "High",
                String.format("Critical accessibility issue on %s: %s",
                    pageContext, description),
                Map.of(
                    "page", pageContext,
                    "issueType", issueType,
                    "element", element,
                    "wcagCriteria", wcagCriteria,
                    "severity", severity
                )
            );
        }
        
        double accessibilityScore = metrics.calculateAccessibilityScore();
        if (accessibilityScore < accessibilityScoreThreshold) {
            metrics.setNeedsImprovement(true);
        }
        
        metricsService.recordMetric("ux.accessibility_issues", occurrences,
            Map.of("type", issueType, "severity", severity, "page", pageContext));
    }
    
    private void handleDeviceMetrics(JsonNode event, long timestamp) {
        String deviceType = event.path("deviceType").asText();
        String browser = event.path("browser").asText();
        String os = event.path("os").asText();
        int screenWidth = event.path("screenWidth").asInt();
        int screenHeight = event.path("screenHeight").asInt();
        int viewportWidth = event.path("viewportWidth").asInt();
        int viewportHeight = event.path("viewportHeight").asInt();
        double devicePixelRatio = event.path("devicePixelRatio").asDouble();
        String connectionType = event.path("connectionType").asText("");
        double connectionSpeed = event.path("connectionSpeed").asDouble();
        
        String deviceKey = deviceType + "_" + browser + "_" + os;
        DeviceMetrics metrics = deviceMetricsMap.computeIfAbsent(
            deviceKey, k -> new DeviceMetrics(deviceType, browser, os)
        );
        
        metrics.updateMetrics(
            screenWidth, screenHeight, viewportWidth, viewportHeight,
            devicePixelRatio, connectionType, connectionSpeed, timestamp
        );
        
        if (connectionSpeed > 0 && connectionSpeed < 1.0) {
            metrics.markSlowConnection();
        }
        
        metricsService.recordMetric("ux.device_usage", 1.0,
            Map.of("device", deviceType, "browser", browser, "os", os));
    }
    
    private void handleUserFeedback(JsonNode event, long timestamp) {
        String feedbackType = event.path("feedbackType").asText();
        String userId = event.path("userId").asText();
        String pageContext = event.path("pageContext").asText();
        int rating = event.path("rating").asInt();
        String comment = event.path("comment").asText("");
        String category = event.path("category").asText("");
        String sentiment = event.path("sentiment").asText("NEUTRAL");
        
        FeedbackMetrics metrics = feedbackMetricsMap.computeIfAbsent(
            pageContext, k -> new FeedbackMetrics(pageContext)
        );
        
        metrics.recordFeedback(feedbackType, rating, sentiment, category, timestamp);
        
        if (!comment.isEmpty()) {
            metrics.addComment(userId, comment, rating);
        }
        
        if (rating < satisfactionScoreThreshold) {
            alertingService.sendAlert(
                "LOW_USER_SATISFACTION",
                "Medium",
                String.format("Low user satisfaction on %s: rating %d/10",
                    pageContext, rating),
                Map.of(
                    "page", pageContext,
                    "userId", userId,
                    "rating", String.valueOf(rating),
                    "category", category,
                    "sentiment", sentiment
                )
            );
        }
        
        metricsService.recordMetric("ux.user_rating", rating,
            Map.of("page", pageContext, "type", feedbackType, "sentiment", sentiment));
    }
    
    private void handleSearchInteraction(JsonNode event, long timestamp) {
        String searchQuery = event.path("searchQuery").asText();
        int resultsCount = event.path("resultsCount").asInt();
        boolean hasResults = event.path("hasResults").asBoolean();
        String selectedResult = event.path("selectedResult").asText("");
        int selectedPosition = event.path("selectedPosition").asInt(-1);
        long searchDuration = event.path("searchDuration").asLong();
        boolean refined = event.path("refined").asBoolean();
        String userId = event.path("userId").asText();
        
        SearchMetrics metrics = searchMetricsMap.computeIfAbsent(
            "search", k -> new SearchMetrics()
        );
        
        metrics.recordSearch(
            searchQuery, resultsCount, hasResults, selectedResult,
            selectedPosition, searchDuration, refined, timestamp
        );
        
        if (!hasResults) {
            metrics.incrementNoResults();
            
            if (metrics.getNoResultsRate() > 0.2) {
                alertingService.sendAlert(
                    "HIGH_SEARCH_NO_RESULTS",
                    "Medium",
                    String.format("High no-results rate in search: %.2f%%",
                        metrics.getNoResultsRate() * 100),
                    Map.of(
                        "recentQuery", searchQuery,
                        "noResultsRate", String.valueOf(metrics.getNoResultsRate())
                    )
                );
            }
        }
        
        if (selectedPosition > 10) {
            metrics.incrementDeepClicks();
        }
        
        metricsService.recordMetric("ux.search_duration", searchDuration,
            Map.of("hasResults", String.valueOf(hasResults), "refined", String.valueOf(refined)));
    }
    
    private void handleScrollBehavior(JsonNode event, long timestamp) {
        String pageContext = event.path("pageContext").asText();
        double scrollDepth = event.path("scrollDepth").asDouble();
        long timeToScroll = event.path("timeToScroll").asLong();
        String scrollPattern = event.path("scrollPattern").asText();
        int scrollVelocity = event.path("scrollVelocity").asInt();
        String userId = event.path("userId").asText();
        String sessionId = event.path("sessionId").asText();
        
        EngagementMetrics metrics = engagementMetricsMap.computeIfAbsent(
            pageContext, k -> new EngagementMetrics(pageContext)
        );
        
        metrics.recordScrollBehavior(scrollDepth, timeToScroll, scrollPattern, scrollVelocity, timestamp);
        
        if ("RAPID".equals(scrollPattern) || scrollVelocity > 1000) {
            FrustrationSignals frustration = frustrationMap.get(sessionId);
            if (frustration != null) {
                frustration.incrementRapidScrolling();
            }
        }
        
        metricsService.recordMetric("ux.scroll_depth", scrollDepth,
            Map.of("page", pageContext, "pattern", scrollPattern));
    }
    
    private void analyzeUserExperience() {
        try {
            Map<String, UxAnalysis> analyses = new HashMap<>();
            
            pageLoadMetricsMap.forEach((pageId, metrics) -> {
                UxAnalysis analysis = analyzePagePerformance(metrics);
                analyses.put(pageId, analysis);
            });
            
            sessionMetricsMap.values().stream()
                .filter(session -> session.isRecent(analysisWindowMinutes))
                .forEach(session -> {
                    analyzeSessionQuality(session);
                });
            
            double overallScore = calculateOverallUxScore();
            UxScorecard scorecard = uxScorecardMap.get("overall");
            if (scorecard != null) {
                scorecard.updateScore(overallScore, System.currentTimeMillis());
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing user experience", e);
        }
    }
    
    private void detectFrustrationPatterns() {
        try {
            frustrationMap.forEach((sessionId, frustration) -> {
                if (frustration.hasHighFrustration()) {
                    SessionMetrics session = sessionMetricsMap.get(sessionId);
                    if (session != null) {
                        analyzeFrustrationCause(frustration, session);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error detecting frustration patterns", e);
        }
    }
    
    private void calculateUxScores() {
        try {
            UxScorecard scorecard = uxScorecardMap.get("overall");
            if (scorecard != null) {
                scorecard.setPerformanceScore(calculatePerformanceScore());
                scorecard.setUsabilityScore(calculateUsabilityScore());
                scorecard.setAccessibilityScore(calculateAccessibilityScore());
                scorecard.setSatisfactionScore(calculateSatisfactionScore());
                scorecard.setEngagementScore(calculateEngagementScore());
                
                double overall = scorecard.calculateOverallScore();
                
                metricsService.recordMetric("ux.overall_score", overall, Map.of());
            }
        } catch (Exception e) {
            logger.error("Error calculating UX scores", e);
        }
    }
    
    private void analyzeUserJourneys() {
        try {
            userJourneyMap.forEach((journeyId, journey) -> {
                if (journey.isComplete() || journey.isAbandoned()) {
                    JourneyAnalysis analysis = analyzeJourney(journey);
                    
                    if (analysis.hasIssues()) {
                        reportJourneyIssues(journey, analysis);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Error analyzing user journeys", e);
        }
    }
    
    private void generateHeatmaps() {
        if (!heatmapEnabled) {
            return;
        }
        
        try {
            Map<String, HeatmapData> heatmaps = new HashMap<>();
            
            clickstreamMap.values().forEach(clickstream -> {
                clickstream.getClicksByPage().forEach((page, clicks) -> {
                    HeatmapData heatmap = heatmaps.computeIfAbsent(
                        page, k -> new HeatmapData(page)
                    );
                    
                    clicks.forEach(click -> {
                        heatmap.addDataPoint(click.x, click.y, 1);
                    });
                });
            });
            
            heatmaps.forEach((page, heatmap) -> {
                heatmap.normalize();
                logger.info("Generated heatmap for page: {} with {} data points",
                    page, heatmap.getTotalPoints());
            });
            
        } catch (Exception e) {
            logger.error("Error generating heatmaps", e);
        }
    }
    
    private void processSessionReplays() {
        if (!sessionReplayEnabled) {
            return;
        }
        
        try {
            sessionMetricsMap.values().stream()
                .filter(session -> session.hasSignificantEvents())
                .forEach(session -> {
                    String sessionId = session.getSessionId();
                    ClickstreamData clickstream = clickstreamMap.get(sessionId);
                    NavigationMetrics navigation = navigationMetricsMap.get(sessionId);
                    
                    if (clickstream != null && navigation != null) {
                        SessionReplay replay = createSessionReplay(
                            session, clickstream, navigation
                        );
                        
                        if (replay.isInteresting()) {
                            logger.info("Processed interesting session replay: {}", sessionId);
                        }
                    }
                });
        } catch (Exception e) {
            logger.error("Error processing session replays", e);
        }
    }
    
    private void generateUxReports() {
        try {
            Map<String, Object> report = new HashMap<>();
            
            report.put("overallScore", calculateOverallUxScore());
            report.put("averagePageLoad", calculateAveragePageLoad());
            report.put("bounceRate", calculateBounceRate());
            report.put("errorRate", calculateErrorRate());
            report.put("satisfactionScore", calculateOverallSatisfaction());
            report.put("topFrustrations", getTopFrustrations());
            report.put("slowestPages", getSlowestPages());
            report.put("timestamp", System.currentTimeMillis());
            
            logger.info("UX report generated: {}", report);
            
            metricsService.recordMetric("ux.report_generated", 1.0, Map.of());
            
        } catch (Exception e) {
            logger.error("Error generating UX reports", e);
        }
    }
    
    private void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
            
            pageLoadMetricsMap.values().forEach(metrics -> 
                metrics.cleanupOldData(cutoffTime)
            );
            
            sessionMetricsMap.entrySet().removeIf(entry -> 
                entry.getValue().getLastUpdateTime() < cutoffTime
            );
            
            clickstreamMap.entrySet().removeIf(entry ->
                entry.getValue().getLastClickTime() < cutoffTime
            );
            
            logger.info("Cleaned up old UX metrics data");
            
        } catch (Exception e) {
            logger.error("Error cleaning up old data", e);
        }
    }
    
    private double calculatePerformanceScore(long loadTime, long lcp, long tbt, double cls) {
        double loadScore = Math.max(0, 1 - (loadTime / 10000.0));
        double lcpScore = Math.max(0, 1 - (lcp / 4000.0));
        double tbtScore = Math.max(0, 1 - (tbt / 600.0));
        double clsScore = Math.max(0, 1 - (cls / 0.25));
        
        return (loadScore * 0.25 + lcpScore * 0.35 + tbtScore * 0.25 + clsScore * 0.15);
    }
    
    private double calculateSessionEngagement(long duration, int pageViews, int interactions) {
        double durationScore = Math.min(1, duration / 600.0);
        double pageViewScore = Math.min(1, pageViews / 10.0);
        double interactionScore = Math.min(1, interactions / 20.0);
        
        return (durationScore * 0.3 + pageViewScore * 0.3 + interactionScore * 0.4);
    }
    
    private void detectNavigationIssue(String sessionId, NavigationMetrics metrics) {
        alertingService.sendAlert(
            "NAVIGATION_CONFUSION",
            "Medium",
            String.format("User navigation confusion detected in session %s", sessionId),
            Map.of(
                "sessionId", sessionId,
                "backNavRate", String.valueOf(metrics.getBackNavigationRate()),
                "totalNavigations", String.valueOf(metrics.getTotalNavigations())
            )
        );
    }
    
    private void detectQuickExit(String sessionId, SessionMetrics metrics) {
        alertingService.sendAlert(
            "QUICK_SESSION_EXIT",
            "Low",
            String.format("Quick session exit detected: %s", sessionId),
            Map.of(
                "sessionId", sessionId,
                "duration", String.valueOf(metrics.getSessionDuration()),
                "pageViews", String.valueOf(metrics.getPageViews()),
                "exitPage", metrics.getExitPage()
            )
        );
    }
    
    private void updateHeatmap(String page, int x, int y) {
        logger.debug("Updating heatmap for page {} at ({}, {})", page, x, y);
    }
    
    private void handleRageClick(String sessionId, String page, String element) {
        alertingService.sendAlert(
            "RAGE_CLICK_DETECTED",
            "Medium",
            String.format("Rage click detected on %s", page),
            Map.of(
                "sessionId", sessionId,
                "page", page,
                "element", element
            )
        );
    }
    
    private UxAnalysis analyzePagePerformance(PageLoadMetrics metrics) {
        return new UxAnalysis(
            metrics.getAverageLoadTime(),
            metrics.getPerformanceScore(),
            metrics.getSlowLoadRate()
        );
    }
    
    private void analyzeSessionQuality(SessionMetrics session) {
        double qualityScore = session.getEngagementScore() * 
            (1 - (session.isBounced() ? 0.5 : 0));
        
        session.setQualityScore(qualityScore);
    }
    
    private void analyzeFrustrationCause(FrustrationSignals frustration, SessionMetrics session) {
        String primaryCause = frustration.getPrimaryCause();
        
        logger.info("Frustration analysis for session {}: primary cause {}",
            session.getSessionId(), primaryCause);
    }
    
    private JourneyAnalysis analyzeJourney(UserJourney journey) {
        return new JourneyAnalysis(
            journey.isAbandoned(),
            journey.getAbandonPoint(),
            journey.getTotalDuration(),
            journey.getStepCount()
        );
    }
    
    private void reportJourneyIssues(UserJourney journey, JourneyAnalysis analysis) {
        if (analysis.hasIssues()) {
            logger.warn("Journey issues detected for {}: {}",
                journey.getJourneyId(), analysis.getIssueDescription());
        }
    }
    
    private SessionReplay createSessionReplay(SessionMetrics session, 
                                             ClickstreamData clickstream,
                                             NavigationMetrics navigation) {
        return new SessionReplay(
            session.getSessionId(),
            session.getUserId(),
            clickstream.getAllClicks(),
            navigation.getAllNavigations()
        );
    }
    
    private double calculateOverallUxScore() {
        double performance = calculatePerformanceScore();
        double usability = calculateUsabilityScore();
        double satisfaction = calculateOverallSatisfaction() / 10;
        
        return (performance * 0.3 + usability * 0.3 + satisfaction * 0.4);
    }
    
    private double calculatePerformanceScore() {
        if (pageLoadMetricsMap.isEmpty()) return 0.5;
        
        return pageLoadMetricsMap.values().stream()
            .mapToDouble(PageLoadMetrics::getPerformanceScore)
            .average().orElse(0.5);
    }
    
    private double calculateUsabilityScore() {
        double errorRate = calculateErrorRate();
        double bounceRate = calculateBounceRate();
        double frustrationRate = calculateFrustrationRate();
        
        return Math.max(0, 1 - (errorRate + bounceRate + frustrationRate) / 3);
    }
    
    private double calculateAccessibilityScore() {
        if (accessibilityMap.isEmpty()) return 1.0;
        
        return accessibilityMap.values().stream()
            .mapToDouble(AccessibilityMetrics::calculateAccessibilityScore)
            .average().orElse(1.0);
    }
    
    private double calculateSatisfactionScore() {
        if (feedbackMetricsMap.isEmpty()) return satisfactionScoreThreshold;
        
        return feedbackMetricsMap.values().stream()
            .mapToDouble(FeedbackMetrics::getAverageRating)
            .average().orElse(satisfactionScoreThreshold);
    }
    
    private double calculateOverallSatisfaction() {
        return calculateSatisfactionScore();
    }
    
    private double calculateBounceRate() {
        if (sessionMetricsMap.isEmpty()) return 0.0;
        
        long bounced = sessionMetricsMap.values().stream()
            .filter(SessionMetrics::isBounced)
            .count();
        
        return (double) bounced / sessionMetricsMap.size();
    }
    
    private double calculateEngagementScore() {
        if (sessionMetricsMap.isEmpty()) return 0.0;
        
        return sessionMetricsMap.values().stream()
            .mapToDouble(SessionMetrics::getEngagementScore)
            .average().orElse(0.0);
    }
    
    private double calculateAveragePageLoad() {
        if (pageLoadMetricsMap.isEmpty()) return 0.0;
        
        return pageLoadMetricsMap.values().stream()
            .mapToDouble(PageLoadMetrics::getAverageLoadTime)
            .average().orElse(0.0);
    }
    
    private double calculateErrorRate() {
        if (errorMetricsMap.isEmpty()) return 0.0;
        
        return errorMetricsMap.values().stream()
            .mapToDouble(ErrorMetrics::getErrorRate)
            .average().orElse(0.0);
    }
    
    private double calculateFrustrationRate() {
        if (frustrationMap.isEmpty()) return 0.0;
        
        long frustrated = frustrationMap.values().stream()
            .filter(FrustrationSignals::hasHighFrustration)
            .count();
        
        return (double) frustrated / frustrationMap.size();
    }
    
    private List<String> getTopFrustrations() {
        Map<String, Integer> frustrationCounts = new HashMap<>();
        
        frustrationMap.values().forEach(frustration -> {
            String cause = frustration.getPrimaryCause();
            frustrationCounts.merge(cause, 1, Integer::sum);
        });
        
        return frustrationCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private List<String> getSlowestPages() {
        return pageLoadMetricsMap.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(
                e2.getValue().getAverageLoadTime(),
                e1.getValue().getAverageLoadTime()
            ))
            .limit(5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private void handleProcessingError(String message, Exception e, Acknowledgment acknowledgment) {
        errorCounter.increment();
        logger.error("Error processing UX metrics event", e);
        
        try {
            if (retryHandler.shouldRetry(message, e)) {
                retryHandler.scheduleRetry(message, acknowledgment);
            } else {
                sendToDlq(message, e);
                acknowledgment.acknowledge();
                dlqCounter.increment();
            }
        } catch (Exception retryError) {
            logger.error("Error handling processing failure", retryError);
            acknowledgment.acknowledge();
        }
    }
    
    private void sendToDlq(String message, Exception error) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalMessage", message);
        dlqMessage.put("error", error.getMessage());
        dlqMessage.put("timestamp", System.currentTimeMillis());
        dlqMessage.put("consumer", "UserExperienceMetricsConsumer");
        
        try {
            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);
            logger.info("Sending message to DLQ: {}", dlqPayload);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            logger.info("Shutting down UserExperienceMetricsConsumer");
            
            scheduledExecutor.shutdown();
            executorService.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("UserExperienceMetricsConsumer shutdown complete");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }
    
    private static class PageLoadMetrics {
        private final String pageName;
        private final List<Long> loadTimes = new CopyOnWriteArrayList<>();
        private final List<Long> lcpValues = new CopyOnWriteArrayList<>();
        private final List<Double> clsValues = new CopyOnWriteArrayList<>();
        private volatile double performanceScore = 0.5;
        private final AtomicInteger slowLoads = new AtomicInteger(0);
        private final AtomicInteger totalLoads = new AtomicInteger(0);
        
        public PageLoadMetrics(String pageName) {
            this.pageName = pageName;
        }
        
        public void recordPageLoad(long loadTime, long domContentLoaded, long firstPaint,
                                  long firstContentfulPaint, long largestContentfulPaint,
                                  long timeToInteractive, long totalBlockingTime,
                                  double cumulativeLayoutShift, long timestamp) {
            loadTimes.add(loadTime);
            lcpValues.add(largestContentfulPaint);
            clsValues.add(cumulativeLayoutShift);
            
            if (loadTimes.size() > 1000) {
                loadTimes.remove(0);
                lcpValues.remove(0);
                clsValues.remove(0);
            }
            
            totalLoads.incrementAndGet();
            if (loadTime > 3000) {
                slowLoads.incrementAndGet();
            }
        }
        
        public void setPerformanceScore(double score) {
            this.performanceScore = score;
        }
        
        public void cleanupOldData(long cutoffTime) {
            if (loadTimes.size() > 100) {
                loadTimes.subList(0, loadTimes.size() - 100).clear();
                lcpValues.subList(0, lcpValues.size() - 100).clear();
                clsValues.subList(0, clsValues.size() - 100).clear();
            }
        }
        
        public double getAverageLoadTime() {
            if (loadTimes.isEmpty()) return 0.0;
            return loadTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
        
        public double getPerformanceScore() { return performanceScore; }
        
        public double getSlowLoadRate() {
            int total = totalLoads.get();
            if (total == 0) return 0.0;
            return (double) slowLoads.get() / total;
        }
    }
    
    private static class InteractionMetrics {
        private final String interactionType;
        private final AtomicInteger totalInteractions = new AtomicInteger(0);
        private final AtomicInteger slowInteractions = new AtomicInteger(0);
        private final AtomicInteger failedInteractions = new AtomicInteger(0);
        private final List<Long> responseTimes = new CopyOnWriteArrayList<>();
        
        public InteractionMetrics(String interactionType) {
            this.interactionType = interactionType;
        }
        
        public void recordInteraction(String elementId, String elementType,
                                     long responseTime, boolean successful,
                                     String pageContext, long timestamp) {
            totalInteractions.incrementAndGet();
            responseTimes.add(responseTime);
            
            if (responseTimes.size() > 1000) {
                responseTimes.remove(0);
            }
        }
        
        public void incrementSlowInteractions() {
            slowInteractions.incrementAndGet();
        }
        
        public void incrementFailedInteractions() {
            failedInteractions.incrementAndGet();
        }
        
        public double getSlowInteractionRate() {
            int total = totalInteractions.get();
            if (total == 0) return 0.0;
            return (double) slowInteractions.get() / total;
        }
    }
    
    private static class NavigationMetrics {
        private final String sessionId;
        private final List<Navigation> navigations = new CopyOnWriteArrayList<>();
        private final AtomicInteger backNavigations = new AtomicInteger(0);
        private volatile boolean bounced = false;
        
        public NavigationMetrics(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void recordNavigation(String from, String to, String type,
                                    long transitionTime, boolean isBack, long timestamp) {
            navigations.add(new Navigation(from, to, type, transitionTime, timestamp));
            
            if (isBack) {
                backNavigations.incrementAndGet();
            }
        }
        
        public void incrementBackNavigations() {
            backNavigations.incrementAndGet();
        }
        
        public void setBounced(boolean bounced) {
            this.bounced = bounced;
        }
        
        public double getBackNavigationRate() {
            if (navigations.isEmpty()) return 0.0;
            return (double) backNavigations.get() / navigations.size();
        }
        
        public int getTotalNavigations() { return navigations.size(); }
        public List<Navigation> getAllNavigations() { return new ArrayList<>(navigations); }
        
        private static class Navigation {
            final String from;
            final String to;
            final String type;
            final long transitionTime;
            final long timestamp;
            
            Navigation(String from, String to, String type, long transitionTime, long timestamp) {
                this.from = from;
                this.to = to;
                this.type = type;
                this.transitionTime = transitionTime;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class ErrorMetrics {
        private final String pageContext;
        private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
        private final AtomicInteger totalErrors = new AtomicInteger(0);
        private final AtomicInteger totalPageViews = new AtomicInteger(0);
        
        public ErrorMetrics(String pageContext) {
            this.pageContext = pageContext;
        }
        
        public void recordError(String errorType, String message, String browser,
                               int count, long timestamp) {
            errorCounts.merge(errorType, count, Integer::sum);
            totalErrors.addAndGet(count);
        }
        
        public double getErrorRate() {
            int views = Math.max(1, totalPageViews.get());
            return (double) totalErrors.get() / views;
        }
    }
    
    private static class SessionMetrics {
        private final String sessionId;
        private final String userId;
        private volatile long sessionDuration = 0;
        private volatile int pageViews = 0;
        private volatile int interactions = 0;
        private volatile boolean bounced = false;
        private volatile String entryPage = "";
        private volatile String exitPage = "";
        private volatile String deviceType = "";
        private volatile double engagementScore = 0.0;
        private volatile double qualityScore = 0.0;
        private volatile long lastUpdateTime = 0;
        
        public SessionMetrics(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }
        
        public void updateSession(long duration, int pageViews, int interactions,
                                 boolean bounced, String entry, String exit,
                                 String device, long timestamp) {
            this.sessionDuration = duration;
            this.pageViews = pageViews;
            this.interactions = interactions;
            this.bounced = bounced;
            this.entryPage = entry;
            this.exitPage = exit;
            this.deviceType = device;
            this.lastUpdateTime = timestamp;
        }
        
        public void markBounced() {
            this.bounced = true;
        }
        
        public void setEngagementScore(double score) {
            this.engagementScore = score;
        }
        
        public void setQualityScore(double score) {
            this.qualityScore = score;
        }
        
        public boolean isRecent(int windowMinutes) {
            long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(windowMinutes);
            return lastUpdateTime > cutoff;
        }
        
        public boolean hasSignificantEvents() {
            return pageViews > 5 || interactions > 10 || sessionDuration > 300;
        }
        
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public long getSessionDuration() { return sessionDuration; }
        public int getPageViews() { return pageViews; }
        public boolean isBounced() { return bounced; }
        public String getExitPage() { return exitPage; }
        public double getEngagementScore() { return engagementScore; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }
    
    private static class EngagementMetrics {
        private final String pageContext;
        private final List<Double> scrollDepths = new CopyOnWriteArrayList<>();
        private final List<Long> timeOnPage = new CopyOnWriteArrayList<>();
        private final Map<String, Long> videoWatchTimes = new ConcurrentHashMap<>();
        private final Map<String, Integer> contentInteractions = new ConcurrentHashMap<>();
        
        public EngagementMetrics(String pageContext) {
            this.pageContext = pageContext;
        }
        
        public void recordScrollDepth(double depth, long timestamp) {
            scrollDepths.add(depth);
            if (scrollDepths.size() > 1000) {
                scrollDepths.remove(0);
            }
        }
        
        public void recordTimeOnPage(long time, long timestamp) {
            timeOnPage.add(time);
            if (timeOnPage.size() > 1000) {
                timeOnPage.remove(0);
            }
        }
        
        public void recordVideoEngagement(String videoId, long watchTime, long timestamp) {
            videoWatchTimes.merge(videoId, watchTime, Long::sum);
        }
        
        public void recordContentInteraction(String contentType, long timestamp) {
            contentInteractions.merge(contentType, 1, Integer::sum);
        }
        
        public void recordScrollBehavior(double depth, long time, String pattern,
                                        int velocity, long timestamp) {
            recordScrollDepth(depth, timestamp);
        }
    }
    
    private static class FormMetrics {
        private final String formId;
        private final String formName;
        private final AtomicInteger starts = new AtomicInteger(0);
        private final AtomicInteger completions = new AtomicInteger(0);
        private final AtomicInteger abandonments = new AtomicInteger(0);
        private final Map<String, Integer> fieldAbandonments = new ConcurrentHashMap<>();
        private final Map<String, Long> fieldInteractionTimes = new ConcurrentHashMap<>();
        
        public FormMetrics(String formId, String formName) {
            this.formId = formId;
            this.formName = formName;
        }
        
        public void recordStart(long timestamp) {
            starts.incrementAndGet();
        }
        
        public void recordFieldInteraction(String field, long timeSpent, long timestamp) {
            fieldInteractionTimes.merge(field, timeSpent, Long::sum);
        }
        
        public void recordSubmit(boolean completed, boolean hasErrors, int attempts, long timestamp) {
            if (completed) {
                completions.incrementAndGet();
            }
        }
        
        public void recordAbandon(String field, String reason, long timestamp) {
            abandonments.incrementAndGet();
            if (!field.isEmpty()) {
                fieldAbandonments.merge(field, 1, Integer::sum);
            }
        }
        
        public double getAbandonmentRate() {
            int total = starts.get();
            if (total == 0) return 0.0;
            return (double) abandonments.get() / total;
        }
        
        public String getMostProblematicField() {
            return fieldAbandonments.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        }
    }
    
    private static class ClickstreamData {
        private final String sessionId;
        private final String userId;
        private final List<Click> clicks = new CopyOnWriteArrayList<>();
        private volatile long lastClickTime = 0;
        
        public ClickstreamData(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }
        
        public void addClick(String element, int x, int y, String page,
                           String type, long timestamp) {
            clicks.add(new Click(element, x, y, page, type, timestamp));
            lastClickTime = timestamp;
            
            if (clicks.size() > 10000) {
                clicks.subList(0, 1000).clear();
            }
        }
        
        public Map<String, List<Click>> getClicksByPage() {
            return clicks.stream().collect(Collectors.groupingBy(c -> c.page));
        }
        
        public List<Click> getAllClicks() { return new ArrayList<>(clicks); }
        public long getLastClickTime() { return lastClickTime; }
        
        private static class Click {
            final String element;
            final int x;
            final int y;
            final String page;
            final String type;
            final long timestamp;
            
            Click(String element, int x, int y, String page, String type, long timestamp) {
                this.element = element;
                this.x = x;
                this.y = y;
                this.page = page;
                this.type = type;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class UserJourney {
        private final String journeyId;
        private final String userId;
        private final List<JourneyStep> steps = new CopyOnWriteArrayList<>();
        private volatile boolean completed = false;
        private volatile boolean abandoned = false;
        private volatile String abandonPoint = "";
        private volatile String journeyType = "";
        
        public UserJourney(String journeyId, String userId) {
            this.journeyId = journeyId;
            this.userId = userId;
        }
        
        public void addStep(String page, long timestamp) {
            steps.add(new JourneyStep(page, steps.size() + 1, "", 0, timestamp));
        }
        
        public void recordStep(String name, int number, String outcome,
                             long duration, long timestamp) {
            steps.add(new JourneyStep(name, number, outcome, duration, timestamp));
        }
        
        public void setAbandoned(boolean abandoned, String point) {
            this.abandoned = abandoned;
            this.abandonPoint = point;
        }
        
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
        
        public double getTotalDuration() {
            if (steps.size() < 2) return 0;
            return steps.get(steps.size() - 1).timestamp - steps.get(0).timestamp;
        }
        
        public String getJourneyId() { return journeyId; }
        public String getJourneyType() { return journeyType; }
        public boolean isComplete() { return completed; }
        public boolean isAbandoned() { return abandoned; }
        public String getAbandonPoint() { return abandonPoint; }
        public int getStepCount() { return steps.size(); }
        
        private static class JourneyStep {
            final String name;
            final int number;
            final String outcome;
            final long duration;
            final long timestamp;
            
            JourneyStep(String name, int number, String outcome, long duration, long timestamp) {
                this.name = name;
                this.number = number;
                this.outcome = outcome;
                this.duration = duration;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class FrustrationSignals {
        private final String sessionId;
        private final String userId;
        private final AtomicInteger rageClicks = new AtomicInteger(0);
        private final AtomicInteger deadClicks = new AtomicInteger(0);
        private final AtomicInteger rapidScrolling = new AtomicInteger(0);
        private final AtomicInteger formStruggles = new AtomicInteger(0);
        private final AtomicInteger errorLoops = new AtomicInteger(0);
        private final List<Signal> signals = new CopyOnWriteArrayList<>();
        
        public FrustrationSignals(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }
        
        public void addSignal(String type, String page, String element,
                            int intensity, long timestamp) {
            signals.add(new Signal(type, page, element, intensity, timestamp));
        }
        
        public void incrementRageClicks() { rageClicks.incrementAndGet(); }
        public void incrementDeadClicks() { deadClicks.incrementAndGet(); }
        public void incrementRapidScrolling() { rapidScrolling.incrementAndGet(); }
        public void incrementFormStruggles() { formStruggles.incrementAndGet(); }
        public void incrementErrorLoops() { errorLoops.incrementAndGet(); }
        
        public int getFrustrationScore() {
            return rageClicks.get() * 3 + deadClicks.get() * 2 + 
                   rapidScrolling.get() + formStruggles.get() * 2 + 
                   errorLoops.get() * 3;
        }
        
        public boolean hasHighFrustration() {
            return getFrustrationScore() > 5;
        }
        
        public String getPrimaryCause() {
            int max = Math.max(rageClicks.get(), 
                      Math.max(deadClicks.get(),
                      Math.max(rapidScrolling.get(),
                      Math.max(formStruggles.get(), errorLoops.get()))));
            
            if (max == rageClicks.get()) return "RAGE_CLICKS";
            if (max == deadClicks.get()) return "DEAD_CLICKS";
            if (max == rapidScrolling.get()) return "RAPID_SCROLLING";
            if (max == formStruggles.get()) return "FORM_STRUGGLES";
            return "ERROR_LOOPS";
        }
        
        private static class Signal {
            final String type;
            final String page;
            final String element;
            final int intensity;
            final long timestamp;
            
            Signal(String type, String page, String element, int intensity, long timestamp) {
                this.type = type;
                this.page = page;
                this.element = element;
                this.intensity = intensity;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class AccessibilityMetrics {
        private final String pageContext;
        private final Map<String, List<Issue>> issues = new ConcurrentHashMap<>();
        private volatile boolean needsImprovement = false;
        
        public AccessibilityMetrics(String pageContext) {
            this.pageContext = pageContext;
        }
        
        public void recordIssue(String type, String severity, String element,
                               String wcag, int occurrences, long timestamp) {
            Issue issue = new Issue(type, severity, element, wcag, occurrences, timestamp);
            issues.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(issue);
        }
        
        public void setNeedsImprovement(boolean needs) {
            this.needsImprovement = needs;
        }
        
        public double calculateAccessibilityScore() {
            if (issues.isEmpty()) return 1.0;
            
            int criticalCount = 0;
            int highCount = 0;
            int totalCount = 0;
            
            for (List<Issue> issueList : issues.values()) {
                for (Issue issue : issueList) {
                    totalCount += issue.occurrences;
                    if ("CRITICAL".equals(issue.severity)) {
                        criticalCount += issue.occurrences;
                    } else if ("HIGH".equals(issue.severity)) {
                        highCount += issue.occurrences;
                    }
                }
            }
            
            double penalty = (criticalCount * 0.1 + highCount * 0.05);
            return Math.max(0, 1 - penalty);
        }
        
        private static class Issue {
            final String type;
            final String severity;
            final String element;
            final String wcag;
            final int occurrences;
            final long timestamp;
            
            Issue(String type, String severity, String element, String wcag,
                 int occurrences, long timestamp) {
                this.type = type;
                this.severity = severity;
                this.element = element;
                this.wcag = wcag;
                this.occurrences = occurrences;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class DeviceMetrics {
        private final String deviceType;
        private final String browser;
        private final String os;
        private final List<DeviceData> dataPoints = new CopyOnWriteArrayList<>();
        private final AtomicInteger slowConnectionCount = new AtomicInteger(0);
        
        public DeviceMetrics(String deviceType, String browser, String os) {
            this.deviceType = deviceType;
            this.browser = browser;
            this.os = os;
        }
        
        public void updateMetrics(int screenWidth, int screenHeight,
                                 int viewportWidth, int viewportHeight,
                                 double pixelRatio, String connectionType,
                                 double connectionSpeed, long timestamp) {
            dataPoints.add(new DeviceData(
                screenWidth, screenHeight, viewportWidth, viewportHeight,
                pixelRatio, connectionType, connectionSpeed, timestamp
            ));
            
            if (dataPoints.size() > 1000) {
                dataPoints.remove(0);
            }
        }
        
        public void markSlowConnection() {
            slowConnectionCount.incrementAndGet();
        }
        
        private static class DeviceData {
            final int screenWidth;
            final int screenHeight;
            final int viewportWidth;
            final int viewportHeight;
            final double pixelRatio;
            final String connectionType;
            final double connectionSpeed;
            final long timestamp;
            
            DeviceData(int screenWidth, int screenHeight, int viewportWidth,
                      int viewportHeight, double pixelRatio, String connectionType,
                      double connectionSpeed, long timestamp) {
                this.screenWidth = screenWidth;
                this.screenHeight = screenHeight;
                this.viewportWidth = viewportWidth;
                this.viewportHeight = viewportHeight;
                this.pixelRatio = pixelRatio;
                this.connectionType = connectionType;
                this.connectionSpeed = connectionSpeed;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class FeedbackMetrics {
        private final String pageContext;
        private final List<Feedback> feedbackList = new CopyOnWriteArrayList<>();
        private final Map<String, List<String>> comments = new ConcurrentHashMap<>();
        private volatile double averageRating = 0.0;
        
        public FeedbackMetrics(String pageContext) {
            this.pageContext = pageContext;
        }
        
        public void recordFeedback(String type, int rating, String sentiment,
                                  String category, long timestamp) {
            feedbackList.add(new Feedback(type, rating, sentiment, category, timestamp));
            calculateAverageRating();
        }
        
        public void addComment(String userId, String comment, int rating) {
            comments.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                    .add(comment);
        }
        
        private void calculateAverageRating() {
            if (feedbackList.isEmpty()) {
                averageRating = 0.0;
                return;
            }
            
            averageRating = feedbackList.stream()
                .mapToInt(f -> f.rating)
                .average().orElse(0.0);
        }
        
        public double getAverageRating() { return averageRating; }
        
        private static class Feedback {
            final String type;
            final int rating;
            final String sentiment;
            final String category;
            final long timestamp;
            
            Feedback(String type, int rating, String sentiment, String category, long timestamp) {
                this.type = type;
                this.rating = rating;
                this.sentiment = sentiment;
                this.category = category;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class SearchMetrics {
        private final AtomicInteger totalSearches = new AtomicInteger(0);
        private final AtomicInteger noResultsCount = new AtomicInteger(0);
        private final AtomicInteger deepClicks = new AtomicInteger(0);
        private final List<SearchData> searches = new CopyOnWriteArrayList<>();
        
        public void recordSearch(String query, int results, boolean hasResults,
                                String selected, int position, long duration,
                                boolean refined, long timestamp) {
            searches.add(new SearchData(
                query, results, hasResults, selected, position, duration, refined, timestamp
            ));
            
            totalSearches.incrementAndGet();
            
            if (searches.size() > 1000) {
                searches.remove(0);
            }
        }
        
        public void incrementNoResults() {
            noResultsCount.incrementAndGet();
        }
        
        public void incrementDeepClicks() {
            deepClicks.incrementAndGet();
        }
        
        public double getNoResultsRate() {
            int total = totalSearches.get();
            if (total == 0) return 0.0;
            return (double) noResultsCount.get() / total;
        }
        
        private static class SearchData {
            final String query;
            final int results;
            final boolean hasResults;
            final String selected;
            final int position;
            final long duration;
            final boolean refined;
            final long timestamp;
            
            SearchData(String query, int results, boolean hasResults, String selected,
                      int position, long duration, boolean refined, long timestamp) {
                this.query = query;
                this.results = results;
                this.hasResults = hasResults;
                this.selected = selected;
                this.position = position;
                this.duration = duration;
                this.refined = refined;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class UxScorecard {
        private volatile double performanceScore = 0.5;
        private volatile double usabilityScore = 0.5;
        private volatile double accessibilityScore = 0.5;
        private volatile double satisfactionScore = 0.5;
        private volatile double engagementScore = 0.5;
        private volatile double overallScore = 0.5;
        private volatile long lastUpdateTime = 0;
        
        public void updateScore(double score, long timestamp) {
            this.overallScore = score;
            this.lastUpdateTime = timestamp;
        }
        
        public void setPerformanceScore(double score) { this.performanceScore = score; }
        public void setUsabilityScore(double score) { this.usabilityScore = score; }
        public void setAccessibilityScore(double score) { this.accessibilityScore = score; }
        public void setSatisfactionScore(double score) { this.satisfactionScore = score; }
        public void setEngagementScore(double score) { this.engagementScore = score; }
        
        public double calculateOverallScore() {
            return (performanceScore * 0.25 + usabilityScore * 0.2 + 
                   accessibilityScore * 0.15 + satisfactionScore * 0.25 + 
                   engagementScore * 0.15);
        }
    }
    
    private static class UxAnalysis {
        private final double avgLoadTime;
        private final double performanceScore;
        private final double slowLoadRate;
        
        public UxAnalysis(double avgLoadTime, double performanceScore, double slowLoadRate) {
            this.avgLoadTime = avgLoadTime;
            this.performanceScore = performanceScore;
            this.slowLoadRate = slowLoadRate;
        }
    }
    
    private static class JourneyAnalysis {
        private final boolean abandoned;
        private final String abandonPoint;
        private final double duration;
        private final int stepCount;
        
        public JourneyAnalysis(boolean abandoned, String abandonPoint,
                              double duration, int stepCount) {
            this.abandoned = abandoned;
            this.abandonPoint = abandonPoint;
            this.duration = duration;
            this.stepCount = stepCount;
        }
        
        public boolean hasIssues() {
            return abandoned || duration > 600000 || stepCount > 20;
        }
        
        public String getIssueDescription() {
            if (abandoned) return "Journey abandoned at " + abandonPoint;
            if (duration > 600000) return "Journey took too long";
            if (stepCount > 20) return "Journey has too many steps";
            return "No issues";
        }
    }
    
    private static class SessionReplay {
        private final String sessionId;
        private final String userId;
        private final List<ClickstreamData.Click> clicks;
        private final List<NavigationMetrics.Navigation> navigations;
        
        public SessionReplay(String sessionId, String userId,
                           List<ClickstreamData.Click> clicks,
                           List<NavigationMetrics.Navigation> navigations) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.clicks = clicks;
            this.navigations = navigations;
        }
        
        public boolean isInteresting() {
            return clicks.size() > 50 || navigations.size() > 10 ||
                   clicks.stream().anyMatch(c -> "RAGE".equals(c.type));
        }
    }
    
    private static class HeatmapData {
        private final String page;
        private final Map<String, Integer> dataPoints = new ConcurrentHashMap<>();
        private volatile int totalPoints = 0;
        
        public HeatmapData(String page) {
            this.page = page;
        }
        
        public void addDataPoint(int x, int y, int weight) {
            String key = x + "," + y;
            dataPoints.merge(key, weight, Integer::sum);
            totalPoints += weight;
        }
        
        public void normalize() {
            if (totalPoints > 0) {
                dataPoints.replaceAll((k, v) -> (v * 100) / totalPoints);
            }
        }
        
        public int getTotalPoints() { return totalPoints; }
    }
}