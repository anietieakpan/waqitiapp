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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class BusinessMetricsConsumer extends BaseKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(BusinessMetricsConsumer.class);
    private static final String CONSUMER_GROUP_ID = "business-metrics-group";
    private static final String DLQ_TOPIC = "business-metrics-dlq";
    
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final KafkaRetryHandler retryHandler;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${monitoring.business.kpi-calculation-interval-minutes:5}")
    private int kpiCalculationIntervalMinutes;
    
    @Value("${monitoring.business.revenue-alert-threshold:10000}")
    private BigDecimal revenueAlertThreshold;
    
    @Value("${monitoring.business.conversion-rate-threshold:0.02}")
    private double conversionRateThreshold;
    
    @Value("${monitoring.business.churn-rate-threshold:0.05}")
    private double churnRateThreshold;
    
    @Value("${monitoring.business.growth-rate-threshold:0.1}")
    private double growthRateThreshold;
    
    @Value("${monitoring.business.profit-margin-threshold:0.15}")
    private double profitMarginThreshold;
    
    @Value("${monitoring.business.customer-satisfaction-threshold:4.0}")
    private double customerSatisfactionThreshold;
    
    @Value("${monitoring.business.trend-analysis-window-days:30}")
    private int trendAnalysisWindowDays;
    
    @Value("${monitoring.business.forecast-horizon-days:90}")
    private int forecastHorizonDays;
    
    @Value("${monitoring.business.anomaly-detection-sensitivity:2.5}")
    private double anomalyDetectionSensitivity;
    
    @Value("${monitoring.business.segment-analysis-enabled:true}")
    private boolean segmentAnalysisEnabled;
    
    private final Map<String, TransactionMetrics> transactionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, RevenueMetrics> revenueMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, UserMetrics> userMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ConversionMetrics> conversionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, CustomerMetrics> customerMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ProductMetrics> productMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, MarketingMetrics> marketingMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, OperationalMetrics> operationalMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, FinancialMetrics> financialMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, GrowthMetrics> growthMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, RetentionMetrics> retentionMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, SegmentMetrics> segmentMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, KpiMetrics> kpiMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, ForecastMetrics> forecastMetricsMap = new ConcurrentHashMap<>();
    private final Map<String, BusinessHealthScore> businessHealthMap = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public BusinessMetricsConsumer(MetricsService metricsService,
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
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Counter kpiCalculationCounter;
    private Counter anomalyDetectionCounter;
    private Counter alertCounter;
    private Gauge revenueGauge;
    private Gauge conversionRateGauge;
    private Gauge churnRateGauge;
    private Gauge customerSatisfactionGauge;
    private Timer processingTimer;
    private Timer calculationTimer;
    private Timer forecastTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        startScheduledTasks();
        initializeBusinessMetrics();
        logger.info("BusinessMetricsConsumer initialized with KPI calculation interval: {} minutes", 
                    kpiCalculationIntervalMinutes);
    }
    
    private void initializeMetrics() {
        processedEventsCounter = Counter.builder("business.metrics.processed")
            .description("Total business metrics events processed")
            .register(meterRegistry);
            
        errorCounter = Counter.builder("business.metrics.errors")
            .description("Total business metrics errors")
            .register(meterRegistry);
            
        dlqCounter = Counter.builder("business.metrics.dlq")
            .description("Total messages sent to DLQ")
            .register(meterRegistry);
            
        kpiCalculationCounter = Counter.builder("business.kpi.calculations")
            .description("Total KPI calculations performed")
            .register(meterRegistry);
            
        anomalyDetectionCounter = Counter.builder("business.anomaly.detections")
            .description("Total business anomalies detected")
            .register(meterRegistry);
            
        alertCounter = Counter.builder("business.alerts")
            .description("Total business alerts triggered")
            .register(meterRegistry);
            
        revenueGauge = Gauge.builder("business.revenue.total", this, 
            consumer -> calculateTotalRevenue())
            .description("Total revenue")
            .register(meterRegistry);
            
        conversionRateGauge = Gauge.builder("business.conversion.rate", this,
            consumer -> calculateAverageConversionRate())
            .description("Average conversion rate")
            .register(meterRegistry);
            
        churnRateGauge = Gauge.builder("business.churn.rate", this,
            consumer -> calculateChurnRate())
            .description("Customer churn rate")
            .register(meterRegistry);
            
        customerSatisfactionGauge = Gauge.builder("business.customer.satisfaction", this,
            consumer -> calculateCustomerSatisfaction())
            .description("Average customer satisfaction score")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("business.metrics.processing.time")
            .description("Business metrics processing time")
            .register(meterRegistry);
            
        calculationTimer = Timer.builder("business.kpi.calculation.time")
            .description("KPI calculation time")
            .register(meterRegistry);
            
        forecastTimer = Timer.builder("business.forecast.time")
            .description("Business forecast calculation time")
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
            .circuitBreaker("business-metrics", circuitBreakerConfig);
            
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
            
        retry = RetryRegistry.of(retryConfig).retry("business-metrics", retryConfig);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> logger.warn("Circuit breaker state transition: {}", event));
    }
    
    private void startScheduledTasks() {
        scheduledExecutor.scheduleAtFixedRate(
            this::calculateKpis, 
            0, kpiCalculationIntervalMinutes, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeBusinessTrends, 
            0, 15, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::performSegmentAnalysis, 
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateForecasts, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::calculateBusinessHealth, 
            0, 10, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::detectAnomalies, 
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::generateBusinessReports, 
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupOldData, 
            0, 24, TimeUnit.HOURS
        );
    }
    
    private void initializeBusinessMetrics() {
        List<String> segments = Arrays.asList(
            "retail", "corporate", "premium", "standard", "new", "returning"
        );
        
        segments.forEach(segment -> {
            SegmentMetrics metrics = new SegmentMetrics(segment);
            segmentMetricsMap.put(segment, metrics);
        });
        
        List<String> kpis = Arrays.asList(
            "revenue", "profit", "conversion", "retention", "satisfaction",
            "acquisition", "ltv", "cac", "arpu", "nps"
        );
        
        kpis.forEach(kpi -> {
            KpiMetrics metrics = new KpiMetrics(kpi);
            kpiMetricsMap.put(kpi, metrics);
        });
    }
    
    @KafkaListener(
        topics = "business-metrics-events",
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
            processBusinessMetricsEvent(message, timestamp);
            acknowledgment.acknowledge();
            processedEventsCounter.increment();
            
        } catch (Exception e) {
            handleProcessingError(message, e, acknowledgment);
            
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private void processBusinessMetricsEvent(String message, long timestamp) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("type").asText();
        String eventId = event.path("eventId").asText();
        
        logger.debug("Processing business metrics event: {} - {}", eventType, eventId);
        
        Callable<Void> processTask = () -> {
            switch (eventType) {
                case "TRANSACTION_VOLUME":
                    handleTransactionVolume(event, timestamp);
                    break;
                case "REVENUE_UPDATE":
                    handleRevenueUpdate(event, timestamp);
                    break;
                case "USER_ACTIVITY":
                    handleUserActivity(event, timestamp);
                    break;
                case "CONVERSION_EVENT":
                    handleConversionEvent(event, timestamp);
                    break;
                case "CUSTOMER_LIFECYCLE":
                    handleCustomerLifecycle(event, timestamp);
                    break;
                case "PRODUCT_PERFORMANCE":
                    handleProductPerformance(event, timestamp);
                    break;
                case "MARKETING_METRICS":
                    handleMarketingMetrics(event, timestamp);
                    break;
                case "OPERATIONAL_EFFICIENCY":
                    handleOperationalEfficiency(event, timestamp);
                    break;
                case "FINANCIAL_METRICS":
                    handleFinancialMetrics(event, timestamp);
                    break;
                case "GROWTH_METRICS":
                    handleGrowthMetrics(event, timestamp);
                    break;
                case "RETENTION_METRICS":
                    handleRetentionMetrics(event, timestamp);
                    break;
                case "CUSTOMER_SATISFACTION":
                    handleCustomerSatisfaction(event, timestamp);
                    break;
                case "SEGMENT_PERFORMANCE":
                    handleSegmentPerformance(event, timestamp);
                    break;
                case "KPI_UPDATE":
                    handleKpiUpdate(event, timestamp);
                    break;
                case "BUSINESS_GOAL_TRACKING":
                    handleBusinessGoalTracking(event, timestamp);
                    break;
                default:
                    logger.warn("Unknown business metrics event type: {}", eventType);
            }
            return null;
        };
        
        Retry.decorateCallable(retry, processTask).call();
    }
    
    private void handleTransactionVolume(JsonNode event, long timestamp) {
        String period = event.path("period").asText();
        int transactionCount = event.path("transactionCount").asInt();
        BigDecimal totalAmount = new BigDecimal(event.path("totalAmount").asText("0"));
        BigDecimal averageAmount = new BigDecimal(event.path("averageAmount").asText("0"));
        String currency = event.path("currency").asText("USD");
        JsonNode breakdown = event.path("breakdown");
        
        TransactionMetrics metrics = transactionMetricsMap.computeIfAbsent(
            period, k -> new TransactionMetrics(period)
        );
        
        metrics.updateVolume(transactionCount, totalAmount, averageAmount, currency, timestamp);
        
        if (breakdown != null && !breakdown.isNull()) {
            processTransactionBreakdown(metrics, breakdown);
        }
        
        if (totalAmount.compareTo(revenueAlertThreshold) > 0) {
            alertingService.sendAlert(
                "HIGH_TRANSACTION_VOLUME",
                "Info",
                String.format("High transaction volume detected: %s %s in %s",
                    totalAmount, currency, period),
                Map.of(
                    "period", period,
                    "count", String.valueOf(transactionCount),
                    "total", totalAmount.toString(),
                    "currency", currency
                )
            );
        }
        
        metricsService.recordMetric("business.transaction_volume", transactionCount,
            Map.of("period", period, "currency", currency));
        
        metricsService.recordMetric("business.transaction_value", totalAmount.doubleValue(),
            Map.of("period", period, "currency", currency));
    }
    
    private void handleRevenueUpdate(JsonNode event, long timestamp) {
        String source = event.path("source").asText();
        String period = event.path("period").asText();
        BigDecimal revenue = new BigDecimal(event.path("revenue").asText("0"));
        BigDecimal costs = new BigDecimal(event.path("costs").asText("0"));
        BigDecimal profit = new BigDecimal(event.path("profit").asText("0"));
        String currency = event.path("currency").asText("USD");
        JsonNode breakdown = event.path("breakdown");
        
        RevenueMetrics metrics = revenueMetricsMap.computeIfAbsent(
            source, k -> new RevenueMetrics(source)
        );
        
        metrics.updateRevenue(revenue, costs, profit, period, currency, timestamp);
        
        if (breakdown != null && breakdown.isObject()) {
            breakdown.fieldNames().forEachRemaining(category -> {
                BigDecimal categoryRevenue = new BigDecimal(breakdown.path(category).asText("0"));
                metrics.addCategoryRevenue(category, categoryRevenue);
            });
        }
        
        BigDecimal profitMargin = profit.divide(revenue, 4, RoundingMode.HALF_UP);
        if (profitMargin.doubleValue() < profitMarginThreshold) {
            alertingService.sendAlert(
                "LOW_PROFIT_MARGIN",
                "Medium",
                String.format("Low profit margin for %s: %.2f%%",
                    source, profitMargin.multiply(BigDecimal.valueOf(100)).doubleValue()),
                Map.of(
                    "source", source,
                    "revenue", revenue.toString(),
                    "profit", profit.toString(),
                    "margin", profitMargin.toString()
                )
            );
        }
        
        metricsService.recordMetric("business.revenue", revenue.doubleValue(),
            Map.of("source", source, "period", period, "currency", currency));
        
        metricsService.recordMetric("business.profit", profit.doubleValue(),
            Map.of("source", source, "period", period, "currency", currency));
    }
    
    private void handleUserActivity(JsonNode event, long timestamp) {
        String activityType = event.path("activityType").asText();
        int activeUsers = event.path("activeUsers").asInt();
        int newUsers = event.path("newUsers").asInt();
        int returningUsers = event.path("returningUsers").asInt();
        double engagementRate = event.path("engagementRate").asDouble();
        String period = event.path("period").asText();
        JsonNode sessionData = event.path("sessionData");
        
        UserMetrics metrics = userMetricsMap.computeIfAbsent(
            period, k -> new UserMetrics(period)
        );
        
        metrics.updateActivity(
            activityType, activeUsers, newUsers, returningUsers, engagementRate, timestamp
        );
        
        if (sessionData != null && !sessionData.isNull()) {
            double avgSessionDuration = sessionData.path("avgDuration").asDouble();
            int totalSessions = sessionData.path("totalSessions").asInt();
            metrics.updateSessionMetrics(avgSessionDuration, totalSessions);
        }
        
        double userGrowthRate = (double) newUsers / Math.max(1, activeUsers);
        if (userGrowthRate < 0.01) {
            alertingService.sendAlert(
                "LOW_USER_GROWTH",
                "Medium",
                String.format("Low user growth rate: %.2f%% for %s",
                    userGrowthRate * 100, period),
                Map.of(
                    "period", period,
                    "activeUsers", String.valueOf(activeUsers),
                    "newUsers", String.valueOf(newUsers),
                    "growthRate", String.valueOf(userGrowthRate)
                )
            );
        }
        
        metricsService.recordMetric("business.active_users", activeUsers,
            Map.of("type", activityType, "period", period));
        
        metricsService.recordMetric("business.engagement_rate", engagementRate,
            Map.of("type", activityType, "period", period));
    }
    
    private void handleConversionEvent(JsonNode event, long timestamp) {
        String funnelStage = event.path("funnelStage").asText();
        String sourceChannel = event.path("sourceChannel").asText();
        int visitors = event.path("visitors").asInt();
        int conversions = event.path("conversions").asInt();
        double conversionRate = event.path("conversionRate").asDouble();
        BigDecimal conversionValue = new BigDecimal(event.path("conversionValue").asText("0"));
        String period = event.path("period").asText();
        
        ConversionMetrics metrics = conversionMetricsMap.computeIfAbsent(
            funnelStage, k -> new ConversionMetrics(funnelStage)
        );
        
        metrics.updateConversion(
            sourceChannel, visitors, conversions, conversionRate, conversionValue, timestamp
        );
        
        if (conversionRate < conversionRateThreshold) {
            alertingService.sendAlert(
                "LOW_CONVERSION_RATE",
                "High",
                String.format("Low conversion rate at %s: %.2f%%",
                    funnelStage, conversionRate * 100),
                Map.of(
                    "stage", funnelStage,
                    "channel", sourceChannel,
                    "rate", String.valueOf(conversionRate),
                    "visitors", String.valueOf(visitors),
                    "conversions", String.valueOf(conversions)
                )
            );
        }
        
        metricsService.recordMetric("business.conversion_rate", conversionRate,
            Map.of("stage", funnelStage, "channel", sourceChannel, "period", period));
        
        metricsService.recordMetric("business.conversion_value", conversionValue.doubleValue(),
            Map.of("stage", funnelStage, "channel", sourceChannel, "period", period));
    }
    
    private void handleCustomerLifecycle(JsonNode event, long timestamp) {
        String stage = event.path("stage").asText();
        String customerId = event.path("customerId").asText();
        BigDecimal lifetimeValue = new BigDecimal(event.path("lifetimeValue").asText("0"));
        int purchaseCount = event.path("purchaseCount").asInt();
        double retentionRate = event.path("retentionRate").asDouble();
        double churnProbability = event.path("churnProbability").asDouble();
        String segment = event.path("segment").asText();
        
        CustomerMetrics metrics = customerMetricsMap.computeIfAbsent(
            segment, k -> new CustomerMetrics(segment)
        );
        
        metrics.updateLifecycle(
            stage, lifetimeValue, purchaseCount, retentionRate, churnProbability, timestamp
        );
        
        if (churnProbability > 0.7) {
            alertingService.sendAlert(
                "HIGH_CHURN_RISK",
                "High",
                String.format("High churn risk for customer in %s segment: %.1f%%",
                    segment, churnProbability * 100),
                Map.of(
                    "customerId", customerId,
                    "segment", segment,
                    "churnProbability", String.valueOf(churnProbability),
                    "lifetimeValue", lifetimeValue.toString()
                )
            );
        }
        
        if ("CHURNED".equals(stage)) {
            metrics.incrementChurnCount();
            updateChurnRate(segment);
        }
        
        metricsService.recordMetric("business.customer_ltv", lifetimeValue.doubleValue(),
            Map.of("segment", segment, "stage", stage));
        
        metricsService.recordMetric("business.retention_rate", retentionRate,
            Map.of("segment", segment));
    }
    
    private void handleProductPerformance(JsonNode event, long timestamp) {
        String productId = event.path("productId").asText();
        String productName = event.path("productName").asText();
        int unitsSold = event.path("unitsSold").asInt();
        BigDecimal revenue = new BigDecimal(event.path("revenue").asText("0"));
        BigDecimal profit = new BigDecimal(event.path("profit").asText("0"));
        double marginPercentage = event.path("marginPercentage").asDouble();
        double marketShare = event.path("marketShare").asDouble();
        String category = event.path("category").asText();
        
        ProductMetrics metrics = productMetricsMap.computeIfAbsent(
            productId, k -> new ProductMetrics(productId, productName, category)
        );
        
        metrics.updatePerformance(
            unitsSold, revenue, profit, marginPercentage, marketShare, timestamp
        );
        
        if (marginPercentage < 0.1) {
            alertingService.sendAlert(
                "LOW_PRODUCT_MARGIN",
                "Medium",
                String.format("Low margin for product %s: %.1f%%",
                    productName, marginPercentage * 100),
                Map.of(
                    "productId", productId,
                    "productName", productName,
                    "margin", String.valueOf(marginPercentage),
                    "revenue", revenue.toString()
                )
            );
        }
        
        metricsService.recordMetric("business.product_revenue", revenue.doubleValue(),
            Map.of("product", productName, "category", category));
        
        metricsService.recordMetric("business.product_units", unitsSold,
            Map.of("product", productName, "category", category));
    }
    
    private void handleMarketingMetrics(JsonNode event, long timestamp) {
        String campaign = event.path("campaign").asText();
        String channel = event.path("channel").asText();
        BigDecimal spend = new BigDecimal(event.path("spend").asText("0"));
        int impressions = event.path("impressions").asInt();
        int clicks = event.path("clicks").asInt();
        int conversions = event.path("conversions").asInt();
        BigDecimal revenue = new BigDecimal(event.path("revenue").asText("0"));
        double roi = event.path("roi").asDouble();
        double cac = event.path("customerAcquisitionCost").asDouble();
        
        MarketingMetrics metrics = marketingMetricsMap.computeIfAbsent(
            campaign, k -> new MarketingMetrics(campaign)
        );
        
        metrics.updateCampaignMetrics(
            channel, spend, impressions, clicks, conversions, revenue, roi, cac, timestamp
        );
        
        if (roi < 1.0) {
            alertingService.sendAlert(
                "NEGATIVE_MARKETING_ROI",
                "High",
                String.format("Negative ROI for campaign %s: %.2f",
                    campaign, roi),
                Map.of(
                    "campaign", campaign,
                    "channel", channel,
                    "spend", spend.toString(),
                    "revenue", revenue.toString(),
                    "roi", String.valueOf(roi)
                )
            );
        }
        
        double ctr = impressions > 0 ? (double) clicks / impressions : 0;
        double conversionRate = clicks > 0 ? (double) conversions / clicks : 0;
        
        metrics.updateEngagementMetrics(ctr, conversionRate);
        
        metricsService.recordMetric("business.marketing_roi", roi,
            Map.of("campaign", campaign, "channel", channel));
        
        metricsService.recordMetric("business.customer_acquisition_cost", cac,
            Map.of("campaign", campaign, "channel", channel));
    }
    
    private void handleOperationalEfficiency(JsonNode event, long timestamp) {
        String metric = event.path("metric").asText();
        double value = event.path("value").asDouble();
        String unit = event.path("unit").asText();
        String department = event.path("department").asText();
        double target = event.path("target").asDouble();
        double variance = event.path("variance").asDouble();
        JsonNode breakdown = event.path("breakdown");
        
        OperationalMetrics metrics = operationalMetricsMap.computeIfAbsent(
            department, k -> new OperationalMetrics(department)
        );
        
        metrics.updateEfficiency(metric, value, unit, target, variance, timestamp);
        
        if (breakdown != null && !breakdown.isNull()) {
            breakdown.fieldNames().forEachRemaining(component -> {
                double componentValue = breakdown.path(component).asDouble();
                metrics.addComponentMetric(component, componentValue);
            });
        }
        
        if (Math.abs(variance) > 0.2) {
            String severity = Math.abs(variance) > 0.4 ? "High" : "Medium";
            alertingService.sendAlert(
                "OPERATIONAL_VARIANCE",
                severity,
                String.format("High variance in %s for %s: %.1f%%",
                    metric, department, variance * 100),
                Map.of(
                    "metric", metric,
                    "department", department,
                    "value", String.valueOf(value),
                    "target", String.valueOf(target),
                    "variance", String.valueOf(variance)
                )
            );
        }
        
        metricsService.recordMetric("business.operational_efficiency", value,
            Map.of("metric", metric, "department", department, "unit", unit));
    }
    
    private void handleFinancialMetrics(JsonNode event, long timestamp) {
        String metricType = event.path("metricType").asText();
        String period = event.path("period").asText();
        BigDecimal value = new BigDecimal(event.path("value").asText("0"));
        String currency = event.path("currency").asText("USD");
        double growthRate = event.path("growthRate").asDouble();
        double forecast = event.path("forecast").asDouble();
        JsonNode components = event.path("components");
        
        FinancialMetrics metrics = financialMetricsMap.computeIfAbsent(
            period, k -> new FinancialMetrics(period)
        );
        
        metrics.updateFinancial(metricType, value, currency, growthRate, forecast, timestamp);
        
        if (components != null && components.isObject()) {
            components.fieldNames().forEachRemaining(component -> {
                BigDecimal componentValue = new BigDecimal(components.path(component).asText("0"));
                metrics.addComponent(component, componentValue);
            });
        }
        
        if ("CASH_FLOW".equals(metricType) && value.compareTo(BigDecimal.ZERO) < 0) {
            alertingService.sendAlert(
                "NEGATIVE_CASH_FLOW",
                "Critical",
                String.format("Negative cash flow detected: %s %s",
                    value, currency),
                Map.of(
                    "period", period,
                    "value", value.toString(),
                    "currency", currency
                )
            );
        }
        
        metricsService.recordMetric("business.financial." + metricType.toLowerCase(), 
            value.doubleValue(),
            Map.of("period", period, "currency", currency));
    }
    
    private void handleGrowthMetrics(JsonNode event, long timestamp) {
        String growthType = event.path("growthType").asText();
        String period = event.path("period").asText();
        double currentValue = event.path("currentValue").asDouble();
        double previousValue = event.path("previousValue").asDouble();
        double growthRate = event.path("growthRate").asDouble();
        double targetGrowthRate = event.path("targetGrowthRate").asDouble();
        String segment = event.path("segment").asText("");
        
        GrowthMetrics metrics = growthMetricsMap.computeIfAbsent(
            growthType, k -> new GrowthMetrics(growthType)
        );
        
        metrics.updateGrowth(
            period, currentValue, previousValue, growthRate, targetGrowthRate, segment, timestamp
        );
        
        if (growthRate < growthRateThreshold && growthRate < targetGrowthRate) {
            alertingService.sendAlert(
                "BELOW_GROWTH_TARGET",
                "High",
                String.format("Growth rate for %s below target: %.1f%% vs %.1f%%",
                    growthType, growthRate * 100, targetGrowthRate * 100),
                Map.of(
                    "type", growthType,
                    "period", period,
                    "actual", String.valueOf(growthRate),
                    "target", String.valueOf(targetGrowthRate),
                    "segment", segment
                )
            );
        }
        
        metricsService.recordMetric("business.growth_rate", growthRate,
            Map.of("type", growthType, "period", period, "segment", segment));
    }
    
    private void handleRetentionMetrics(JsonNode event, long timestamp) {
        String cohort = event.path("cohort").asText();
        String period = event.path("period").asText();
        int totalUsers = event.path("totalUsers").asInt();
        int retainedUsers = event.path("retainedUsers").asInt();
        double retentionRate = event.path("retentionRate").asDouble();
        double churnRate = event.path("churnRate").asDouble();
        JsonNode retentionCurve = event.path("retentionCurve");
        
        RetentionMetrics metrics = retentionMetricsMap.computeIfAbsent(
            cohort, k -> new RetentionMetrics(cohort)
        );
        
        metrics.updateRetention(
            period, totalUsers, retainedUsers, retentionRate, churnRate, timestamp
        );
        
        if (retentionCurve != null && retentionCurve.isArray()) {
            List<Double> curve = new ArrayList<>();
            retentionCurve.forEach(point -> curve.add(point.asDouble()));
            metrics.setRetentionCurve(curve);
        }
        
        if (churnRate > churnRateThreshold) {
            alertingService.sendAlert(
                "HIGH_CHURN_RATE",
                "Critical",
                String.format("High churn rate for cohort %s: %.1f%%",
                    cohort, churnRate * 100),
                Map.of(
                    "cohort", cohort,
                    "period", period,
                    "churnRate", String.valueOf(churnRate),
                    "retainedUsers", String.valueOf(retainedUsers),
                    "totalUsers", String.valueOf(totalUsers)
                )
            );
        }
        
        metricsService.recordMetric("business.retention_rate", retentionRate,
            Map.of("cohort", cohort, "period", period));
        
        metricsService.recordMetric("business.churn_rate", churnRate,
            Map.of("cohort", cohort, "period", period));
    }
    
    private void handleCustomerSatisfaction(JsonNode event, long timestamp) {
        String measureType = event.path("measureType").asText();
        double score = event.path("score").asDouble();
        int responseCount = event.path("responseCount").asInt();
        String segment = event.path("segment").asText("");
        JsonNode breakdown = event.path("breakdown");
        JsonNode feedback = event.path("feedback");
        
        CustomerMetrics metrics = customerMetricsMap.computeIfAbsent(
            segment.isEmpty() ? "all" : segment, k -> new CustomerMetrics(k)
        );
        
        metrics.updateSatisfaction(measureType, score, responseCount, timestamp);
        
        if (breakdown != null && breakdown.isObject()) {
            breakdown.fieldNames().forEachRemaining(category -> {
                double categoryScore = breakdown.path(category).asDouble();
                metrics.addCategoryScore(category, categoryScore);
            });
        }
        
        if (feedback != null && feedback.isObject()) {
            int positive = feedback.path("positive").asInt();
            int neutral = feedback.path("neutral").asInt();
            int negative = feedback.path("negative").asInt();
            metrics.updateSentiment(positive, neutral, negative);
        }
        
        if (score < customerSatisfactionThreshold) {
            alertingService.sendAlert(
                "LOW_CUSTOMER_SATISFACTION",
                "High",
                String.format("Low %s score: %.1f (threshold: %.1f)",
                    measureType, score, customerSatisfactionThreshold),
                Map.of(
                    "type", measureType,
                    "score", String.valueOf(score),
                    "responses", String.valueOf(responseCount),
                    "segment", segment
                )
            );
        }
        
        metricsService.recordMetric("business.customer_satisfaction", score,
            Map.of("type", measureType, "segment", segment));
    }
    
    private void handleSegmentPerformance(JsonNode event, long timestamp) {
        String segmentId = event.path("segmentId").asText();
        String segmentName = event.path("segmentName").asText();
        BigDecimal revenue = new BigDecimal(event.path("revenue").asText("0"));
        int customers = event.path("customers").asInt();
        double avgOrderValue = event.path("avgOrderValue").asDouble();
        double conversionRate = event.path("conversionRate").asDouble();
        double retentionRate = event.path("retentionRate").asDouble();
        JsonNode demographics = event.path("demographics");
        
        SegmentMetrics metrics = segmentMetricsMap.computeIfAbsent(
            segmentId, k -> new SegmentMetrics(segmentName)
        );
        
        metrics.updatePerformance(
            revenue, customers, avgOrderValue, conversionRate, retentionRate, timestamp
        );
        
        if (demographics != null && demographics.isObject()) {
            demographics.fieldNames().forEachRemaining(demo -> {
                String value = demographics.path(demo).asText();
                metrics.addDemographic(demo, value);
            });
        }
        
        BigDecimal arpu = customers > 0 ? 
            revenue.divide(BigDecimal.valueOf(customers), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        metrics.setArpu(arpu);
        
        metricsService.recordMetric("business.segment_revenue", revenue.doubleValue(),
            Map.of("segment", segmentName));
        
        metricsService.recordMetric("business.segment_arpu", arpu.doubleValue(),
            Map.of("segment", segmentName));
    }
    
    private void handleKpiUpdate(JsonNode event, long timestamp) {
        String kpiName = event.path("kpiName").asText();
        double value = event.path("value").asDouble();
        double target = event.path("target").asDouble();
        double variance = event.path("variance").asDouble();
        String period = event.path("period").asText();
        String status = event.path("status").asText();
        JsonNode trend = event.path("trend");
        
        KpiMetrics metrics = kpiMetricsMap.computeIfAbsent(
            kpiName, k -> new KpiMetrics(kpiName)
        );
        
        metrics.updateKpi(value, target, variance, period, status, timestamp);
        
        if (trend != null && trend.isArray()) {
            List<Double> trendData = new ArrayList<>();
            trend.forEach(point -> trendData.add(point.asDouble()));
            metrics.setTrendData(trendData);
            
            String trendDirection = analyzeTrend(trendData);
            metrics.setTrendDirection(trendDirection);
        }
        
        if ("RED".equals(status) || Math.abs(variance) > 0.25) {
            alertingService.sendAlert(
                "KPI_ALERT",
                "High",
                String.format("KPI %s variance: %.1f%% from target",
                    kpiName, variance * 100),
                Map.of(
                    "kpi", kpiName,
                    "value", String.valueOf(value),
                    "target", String.valueOf(target),
                    "status", status,
                    "period", period
                )
            );
        }
        
        metricsService.recordMetric("business.kpi." + kpiName.toLowerCase(), value,
            Map.of("period", period, "status", status));
    }
    
    private void handleBusinessGoalTracking(JsonNode event, long timestamp) {
        String goalId = event.path("goalId").asText();
        String goalName = event.path("goalName").asText();
        double progress = event.path("progress").asDouble();
        double target = event.path("target").asDouble();
        String deadline = event.path("deadline").asText();
        String status = event.path("status").asText();
        JsonNode milestones = event.path("milestones");
        
        BusinessHealthScore health = businessHealthMap.computeIfAbsent(
            "overall", k -> new BusinessHealthScore()
        );
        
        health.updateGoalProgress(goalId, goalName, progress, target, deadline, status, timestamp);
        
        if (milestones != null && milestones.isArray()) {
            milestones.forEach(milestone -> {
                String milestoneName = milestone.path("name").asText();
                boolean completed = milestone.path("completed").asBoolean();
                health.addMilestone(goalId, milestoneName, completed);
            });
        }
        
        double completionRate = progress / Math.max(1, target);
        if (completionRate < 0.7 && isDeadlineApproaching(deadline)) {
            alertingService.sendAlert(
                "GOAL_AT_RISK",
                "High",
                String.format("Business goal at risk: %s (%.1f%% complete)",
                    goalName, completionRate * 100),
                Map.of(
                    "goalId", goalId,
                    "goalName", goalName,
                    "progress", String.valueOf(progress),
                    "target", String.valueOf(target),
                    "deadline", deadline
                )
            );
        }
        
        metricsService.recordMetric("business.goal_progress", completionRate,
            Map.of("goal", goalName, "status", status));
    }
    
    private void calculateKpis() {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            kpiMetricsMap.forEach((kpiName, metrics) -> {
                double calculatedValue = calculateKpiValue(kpiName);
                metrics.updateCalculatedValue(calculatedValue, System.currentTimeMillis());
                
                if (metrics.hasSignificantVariance()) {
                    triggerKpiAlert(kpiName, metrics);
                }
            });
            
            kpiCalculationCounter.increment();
            sample.stop(calculationTimer);
            
        } catch (Exception e) {
            logger.error("Error calculating KPIs", e);
        }
    }
    
    private double calculateKpiValue(String kpiName) {
        switch (kpiName) {
            case "revenue":
                return calculateTotalRevenue();
            case "profit":
                return calculateTotalProfit();
            case "conversion":
                return calculateAverageConversionRate();
            case "retention":
                return calculateAverageRetention();
            case "satisfaction":
                return calculateCustomerSatisfaction();
            case "acquisition":
                return calculateCustomerAcquisition();
            case "ltv":
                return calculateAverageLtv();
            case "cac":
                return calculateAverageCac();
            case "arpu":
                return calculateArpu();
            case "nps":
                return calculateNps();
            default:
                return 0.0;
        }
    }
    
    private void analyzeBusinessTrends() {
        try {
            Map<String, TrendAnalysis> trends = new HashMap<>();
            
            revenueMetricsMap.forEach((source, metrics) -> {
                TrendAnalysis trend = analyzeTrend(metrics.getHistoricalRevenue());
                trends.put("revenue_" + source, trend);
            });
            
            userMetricsMap.forEach((period, metrics) -> {
                TrendAnalysis trend = analyzeTrend(metrics.getHistoricalActiveUsers());
                trends.put("users_" + period, trend);
            });
            
            trends.forEach((metric, trend) -> {
                if (trend.isSignificant()) {
                    handleTrendAlert(metric, trend);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error analyzing business trends", e);
        }
    }
    
    private void performSegmentAnalysis() {
        if (!segmentAnalysisEnabled) {
            return;
        }
        
        try {
            segmentMetricsMap.forEach((segmentId, metrics) -> {
                SegmentAnalysis analysis = analyzeSegment(metrics);
                
                if (analysis.requiresAttention()) {
                    alertingService.sendAlert(
                        "SEGMENT_ATTENTION",
                        "Medium",
                        String.format("Segment %s requires attention: %s",
                            metrics.getSegmentName(), analysis.getReason()),
                        Map.of(
                            "segment", metrics.getSegmentName(),
                            "revenue", metrics.getRevenue().toString(),
                            "customers", String.valueOf(metrics.getCustomers()),
                            "reason", analysis.getReason()
                        )
                    );
                }
                
                metricsService.recordMetric("business.segment_health", analysis.getHealthScore(),
                    Map.of("segment", metrics.getSegmentName()));
            });
            
        } catch (Exception e) {
            logger.error("Error performing segment analysis", e);
        }
    }
    
    private void generateForecasts() {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            ForecastMetrics forecast = new ForecastMetrics(forecastHorizonDays);
            
            forecast.setRevenueForecast(forecastRevenue());
            forecast.setUserGrowthForecast(forecastUserGrowth());
            forecast.setChurnForecast(forecastChurn());
            forecast.setConversionForecast(forecastConversion());
            
            forecastMetricsMap.put("current", forecast);
            
            sample.stop(forecastTimer);
            
            metricsService.recordMetric("business.forecast.revenue", forecast.getRevenueForecast(),
                Map.of("horizon", String.valueOf(forecastHorizonDays)));
            
        } catch (Exception e) {
            logger.error("Error generating forecasts", e);
        }
    }
    
    private void calculateBusinessHealth() {
        try {
            BusinessHealthScore health = businessHealthMap.computeIfAbsent(
                "overall", k -> new BusinessHealthScore()
            );
            
            double revenueHealth = calculateRevenueHealth();
            double customerHealth = calculateCustomerHealth();
            double operationalHealth = calculateOperationalHealth();
            double growthHealth = calculateGrowthHealth();
            
            health.updateHealthScores(
                revenueHealth, customerHealth, operationalHealth, growthHealth,
                System.currentTimeMillis()
            );
            
            double overallHealth = health.calculateOverallHealth();
            
            if (overallHealth < 0.6) {
                alertingService.sendAlert(
                    "BUSINESS_HEALTH_WARNING",
                    "Critical",
                    String.format("Low business health score: %.1f%%", overallHealth * 100),
                    Map.of(
                        "revenue", String.valueOf(revenueHealth),
                        "customer", String.valueOf(customerHealth),
                        "operational", String.valueOf(operationalHealth),
                        "growth", String.valueOf(growthHealth)
                    )
                );
            }
            
            metricsService.recordMetric("business.health.overall", overallHealth,
                Map.of());
            
        } catch (Exception e) {
            logger.error("Error calculating business health", e);
        }
    }
    
    private void detectAnomalies() {
        try {
            revenueMetricsMap.forEach((source, metrics) -> {
                List<Double> values = metrics.getHistoricalRevenue();
                if (values.size() >= 10) {
                    AnomalyDetection anomaly = detectAnomaly(values, anomalyDetectionSensitivity);
                    
                    if (anomaly.isAnomaly()) {
                        anomalyDetectionCounter.increment();
                        
                        alertingService.sendAlert(
                            "REVENUE_ANOMALY",
                            "High",
                            String.format("Revenue anomaly detected for %s: %.2f (expected: %.2f)",
                                source, anomaly.getActualValue(), anomaly.getExpectedValue()),
                            Map.of(
                                "source", source,
                                "actual", String.valueOf(anomaly.getActualValue()),
                                "expected", String.valueOf(anomaly.getExpectedValue()),
                                "deviation", String.valueOf(anomaly.getDeviation())
                            )
                        );
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error detecting anomalies", e);
        }
    }
    
    private void generateBusinessReports() {
        try {
            Map<String, Object> report = new HashMap<>();
            
            report.put("revenue", calculateTotalRevenue());
            report.put("profit", calculateTotalProfit());
            report.put("activeUsers", calculateTotalActiveUsers());
            report.put("conversionRate", calculateAverageConversionRate());
            report.put("churnRate", calculateChurnRate());
            report.put("customerSatisfaction", calculateCustomerSatisfaction());
            report.put("topProducts", getTopProducts(5));
            report.put("topSegments", getTopSegments(3));
            report.put("timestamp", System.currentTimeMillis());
            
            logger.info("Business report generated: {}", report);
            
            metricsService.recordMetric("business.report.generated", 1.0,
                Map.of());
            
        } catch (Exception e) {
            logger.error("Error generating business reports", e);
        }
    }
    
    private void cleanupOldData() {
        try {
            long cutoffTime = System.currentTimeMillis() - 
                TimeUnit.DAYS.toMillis(90);
            
            transactionMetricsMap.values().forEach(metrics -> 
                metrics.cleanupOldData(cutoffTime)
            );
            
            revenueMetricsMap.values().forEach(metrics -> 
                metrics.cleanupOldData(cutoffTime)
            );
            
            logger.info("Cleaned up old business metrics data");
            
        } catch (Exception e) {
            logger.error("Error cleaning up old data", e);
        }
    }
    
    private void processTransactionBreakdown(TransactionMetrics metrics, JsonNode breakdown) {
        breakdown.fieldNames().forEachRemaining(field -> {
            JsonNode value = breakdown.path(field);
            if (value.isNumber()) {
                metrics.addBreakdown(field, value.asDouble());
            }
        });
    }
    
    private void updateChurnRate(String segment) {
        CustomerMetrics metrics = customerMetricsMap.get(segment);
        if (metrics != null) {
            double churnRate = metrics.calculateChurnRate();
            
            metricsService.recordMetric("business.churn_rate_updated", churnRate,
                Map.of("segment", segment));
        }
    }
    
    private String analyzeTrend(List<Double> values) {
        if (values.size() < 3) return "INSUFFICIENT_DATA";
        
        double recent = values.subList(Math.max(0, values.size() - 3), values.size())
            .stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double historical = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        double change = (recent - historical) / Math.max(0.01, historical);
        
        if (change > 0.1) return "INCREASING";
        if (change < -0.1) return "DECREASING";
        return "STABLE";
    }
    
    private boolean isDeadlineApproaching(String deadline) {
        try {
            LocalDateTime deadlineDate = LocalDateTime.parse(deadline);
            LocalDateTime now = LocalDateTime.now();
            return deadlineDate.minusDays(7).isBefore(now);
        } catch (Exception e) {
            return false;
        }
    }
    
    private void triggerKpiAlert(String kpiName, KpiMetrics metrics) {
        alertCounter.increment();
        
        alertingService.sendAlert(
            "KPI_VARIANCE",
            "High",
            String.format("KPI %s has significant variance: %.2f%%",
                kpiName, metrics.getVariance() * 100),
            Map.of(
                "kpi", kpiName,
                "current", String.valueOf(metrics.getCurrentValue()),
                "target", String.valueOf(metrics.getTarget()),
                "trend", metrics.getTrendDirection()
            )
        );
    }
    
    private void handleTrendAlert(String metric, TrendAnalysis trend) {
        alertingService.sendAlert(
            "BUSINESS_TREND",
            trend.getSeverity(),
            String.format("Significant trend in %s: %s",
                metric, trend.getDirection()),
            Map.of(
                "metric", metric,
                "direction", trend.getDirection(),
                "strength", String.valueOf(trend.getStrength()),
                "prediction", trend.getPrediction()
            )
        );
    }
    
    private SegmentAnalysis analyzeSegment(SegmentMetrics metrics) {
        double revenuePerCustomer = metrics.getRevenue().doubleValue() / 
            Math.max(1, metrics.getCustomers());
        boolean lowRevenue = revenuePerCustomer < 100;
        boolean lowRetention = metrics.getRetentionRate() < 0.7;
        boolean lowConversion = metrics.getConversionRate() < 0.02;
        
        String reason = lowRevenue ? "Low revenue per customer" :
                       lowRetention ? "Low retention rate" :
                       lowConversion ? "Low conversion rate" : "";
        
        double healthScore = (metrics.getRetentionRate() + metrics.getConversionRate()) / 2;
        
        return new SegmentAnalysis(
            lowRevenue || lowRetention || lowConversion,
            reason,
            healthScore
        );
    }
    
    private double forecastRevenue() {
        List<Double> historicalRevenue = new ArrayList<>();
        revenueMetricsMap.values().forEach(metrics -> 
            historicalRevenue.addAll(metrics.getHistoricalRevenue())
        );
        
        if (historicalRevenue.size() < 10) return 0.0;
        
        double avg = historicalRevenue.stream()
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
        double growthRate = calculateGrowthRate(historicalRevenue);
        
        return avg * Math.pow(1 + growthRate, forecastHorizonDays / 30.0);
    }
    
    private double forecastUserGrowth() {
        List<Integer> historicalUsers = new ArrayList<>();
        userMetricsMap.values().forEach(metrics -> 
            historicalUsers.addAll(metrics.getHistoricalActiveUsers())
        );
        
        if (historicalUsers.size() < 10) return 0.0;
        
        double growthRate = calculateGrowthRate(
            historicalUsers.stream().map(Double::valueOf).collect(Collectors.toList())
        );
        
        return historicalUsers.get(historicalUsers.size() - 1) * 
            Math.pow(1 + growthRate, forecastHorizonDays / 30.0);
    }
    
    private double forecastChurn() {
        List<Double> historicalChurn = new ArrayList<>();
        retentionMetricsMap.values().forEach(metrics -> 
            historicalChurn.add(metrics.getChurnRate())
        );
        
        if (historicalChurn.isEmpty()) return 0.05;
        
        return historicalChurn.stream()
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.05);
    }
    
    private double forecastConversion() {
        List<Double> historicalConversion = new ArrayList<>();
        conversionMetricsMap.values().forEach(metrics -> 
            historicalConversion.add(metrics.getConversionRate())
        );
        
        if (historicalConversion.isEmpty()) return 0.02;
        
        return historicalConversion.stream()
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.02);
    }
    
    private double calculateGrowthRate(List<Double> values) {
        if (values.size() < 2) return 0.0;
        
        double first = values.get(0);
        double last = values.get(values.size() - 1);
        
        if (first == 0) return 0.0;
        
        return (last - first) / first / values.size();
    }
    
    private AnomalyDetection detectAnomaly(List<Double> values, double sensitivity) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(values, mean);
        
        double lastValue = values.get(values.size() - 1);
        double zScore = (lastValue - mean) / Math.max(0.01, stdDev);
        
        boolean isAnomaly = Math.abs(zScore) > sensitivity;
        double deviation = lastValue - mean;
        
        return new AnomalyDetection(isAnomaly, lastValue, mean, deviation, zScore);
    }
    
    private double calculateStandardDeviation(List<Double> values, double mean) {
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiff / values.size());
    }
    
    private double calculateTotalRevenue() {
        return revenueMetricsMap.values().stream()
            .mapToDouble(metrics -> metrics.getRevenue().doubleValue())
            .sum();
    }
    
    private double calculateTotalProfit() {
        return revenueMetricsMap.values().stream()
            .mapToDouble(metrics -> metrics.getProfit().doubleValue())
            .sum();
    }
    
    private double calculateAverageConversionRate() {
        if (conversionMetricsMap.isEmpty()) return 0.0;
        
        return conversionMetricsMap.values().stream()
            .mapToDouble(ConversionMetrics::getConversionRate)
            .average().orElse(0.0);
    }
    
    private double calculateAverageRetention() {
        if (retentionMetricsMap.isEmpty()) return 0.0;
        
        return retentionMetricsMap.values().stream()
            .mapToDouble(RetentionMetrics::getRetentionRate)
            .average().orElse(0.0);
    }
    
    private double calculateCustomerSatisfaction() {
        if (customerMetricsMap.isEmpty()) return 0.0;
        
        return customerMetricsMap.values().stream()
            .mapToDouble(CustomerMetrics::getSatisfactionScore)
            .average().orElse(0.0);
    }
    
    private double calculateCustomerAcquisition() {
        return userMetricsMap.values().stream()
            .mapToInt(UserMetrics::getNewUsers)
            .sum();
    }
    
    private double calculateAverageLtv() {
        if (customerMetricsMap.isEmpty()) return 0.0;
        
        return customerMetricsMap.values().stream()
            .mapToDouble(metrics -> metrics.getLifetimeValue().doubleValue())
            .average().orElse(0.0);
    }
    
    private double calculateAverageCac() {
        if (marketingMetricsMap.isEmpty()) return 0.0;
        
        return marketingMetricsMap.values().stream()
            .mapToDouble(MarketingMetrics::getCustomerAcquisitionCost)
            .average().orElse(0.0);
    }
    
    private double calculateArpu() {
        double revenue = calculateTotalRevenue();
        int totalUsers = calculateTotalActiveUsers();
        
        return totalUsers > 0 ? revenue / totalUsers : 0.0;
    }
    
    private double calculateNps() {
        if (customerMetricsMap.isEmpty()) return 0.0;
        
        int promoters = 0;
        int detractors = 0;
        int total = 0;
        
        for (CustomerMetrics metrics : customerMetricsMap.values()) {
            promoters += metrics.getPromoters();
            detractors += metrics.getDetractors();
            total += metrics.getTotalResponses();
        }
        
        if (total == 0) return 0.0;
        
        return ((double) promoters - detractors) / total * 100;
    }
    
    private int calculateTotalActiveUsers() {
        return userMetricsMap.values().stream()
            .mapToInt(UserMetrics::getActiveUsers)
            .sum();
    }
    
    private double calculateChurnRate() {
        if (retentionMetricsMap.isEmpty()) return 0.0;
        
        return retentionMetricsMap.values().stream()
            .mapToDouble(RetentionMetrics::getChurnRate)
            .average().orElse(0.0);
    }
    
    private double calculateRevenueHealth() {
        double revenue = calculateTotalRevenue();
        double profit = calculateTotalProfit();
        double margin = revenue > 0 ? profit / revenue : 0;
        
        return Math.min(1.0, margin / 0.3);
    }
    
    private double calculateCustomerHealth() {
        double satisfaction = calculateCustomerSatisfaction() / 5.0;
        double retention = calculateAverageRetention();
        double nps = (calculateNps() + 100) / 200;
        
        return (satisfaction + retention + nps) / 3;
    }
    
    private double calculateOperationalHealth() {
        if (operationalMetricsMap.isEmpty()) return 0.5;
        
        double avgEfficiency = operationalMetricsMap.values().stream()
            .mapToDouble(OperationalMetrics::getEfficiencyScore)
            .average().orElse(0.5);
        
        return avgEfficiency;
    }
    
    private double calculateGrowthHealth() {
        if (growthMetricsMap.isEmpty()) return 0.5;
        
        double avgGrowth = growthMetricsMap.values().stream()
            .mapToDouble(GrowthMetrics::getGrowthRate)
            .average().orElse(0.0);
        
        return Math.min(1.0, Math.max(0.0, avgGrowth / 0.2 + 0.5));
    }
    
    private List<String> getTopProducts(int count) {
        return productMetricsMap.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().getRevenue()
                .compareTo(e1.getValue().getRevenue()))
            .limit(count)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    private List<String> getTopSegments(int count) {
        return segmentMetricsMap.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().getRevenue()
                .compareTo(e1.getValue().getRevenue()))
            .limit(count)
            .map(e -> e.getValue().getSegmentName())
            .collect(Collectors.toList());
    }
    
    private void handleProcessingError(String message, Exception e, Acknowledgment acknowledgment) {
        errorCounter.increment();
        logger.error("Error processing business metrics event", e);
        
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
        dlqMessage.put("consumer", "BusinessMetricsConsumer");
        
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
            logger.info("Shutting down BusinessMetricsConsumer");
            
            scheduledExecutor.shutdown();
            executorService.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("BusinessMetricsConsumer shutdown complete");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }
    
    private static class TransactionMetrics {
        private final String period;
        private volatile int transactionCount = 0;
        private volatile BigDecimal totalAmount = BigDecimal.ZERO;
        private volatile BigDecimal averageAmount = BigDecimal.ZERO;
        private volatile String currency = "USD";
        private volatile long lastUpdateTime = 0;
        private final Map<String, Double> breakdown = new ConcurrentHashMap<>();
        
        public TransactionMetrics(String period) {
            this.period = period;
        }
        
        public void updateVolume(int count, BigDecimal total, BigDecimal average,
                                String currency, long timestamp) {
            this.transactionCount = count;
            this.totalAmount = total;
            this.averageAmount = average;
            this.currency = currency;
            this.lastUpdateTime = timestamp;
        }
        
        public void addBreakdown(String category, double value) {
            breakdown.put(category, value);
        }
        
        public void cleanupOldData(long cutoffTime) {
            if (lastUpdateTime < cutoffTime) {
                breakdown.clear();
            }
        }
    }
    
    private static class RevenueMetrics {
        private final String source;
        private volatile BigDecimal revenue = BigDecimal.ZERO;
        private volatile BigDecimal costs = BigDecimal.ZERO;
        private volatile BigDecimal profit = BigDecimal.ZERO;
        private volatile String period = "";
        private volatile String currency = "USD";
        private volatile long lastUpdateTime = 0;
        private final Map<String, BigDecimal> categoryRevenue = new ConcurrentHashMap<>();
        private final List<Double> historicalRevenue = new CopyOnWriteArrayList<>();
        
        public RevenueMetrics(String source) {
            this.source = source;
        }
        
        public void updateRevenue(BigDecimal revenue, BigDecimal costs, BigDecimal profit,
                                String period, String currency, long timestamp) {
            this.revenue = revenue;
            this.costs = costs;
            this.profit = profit;
            this.period = period;
            this.currency = currency;
            this.lastUpdateTime = timestamp;
            
            historicalRevenue.add(revenue.doubleValue());
            if (historicalRevenue.size() > 100) {
                historicalRevenue.remove(0);
            }
        }
        
        public void addCategoryRevenue(String category, BigDecimal revenue) {
            categoryRevenue.put(category, revenue);
        }
        
        public void cleanupOldData(long cutoffTime) {
            if (lastUpdateTime < cutoffTime) {
                categoryRevenue.clear();
            }
        }
        
        public BigDecimal getRevenue() { return revenue; }
        public BigDecimal getProfit() { return profit; }
        public List<Double> getHistoricalRevenue() { return new ArrayList<>(historicalRevenue); }
    }
    
    private static class UserMetrics {
        private final String period;
        private volatile int activeUsers = 0;
        private volatile int newUsers = 0;
        private volatile int returningUsers = 0;
        private volatile double engagementRate = 0.0;
        private volatile double avgSessionDuration = 0.0;
        private volatile int totalSessions = 0;
        private volatile long lastUpdateTime = 0;
        private final List<Integer> historicalActiveUsers = new CopyOnWriteArrayList<>();
        
        public UserMetrics(String period) {
            this.period = period;
        }
        
        public void updateActivity(String type, int active, int newU, int returning,
                                  double engagement, long timestamp) {
            this.activeUsers = active;
            this.newUsers = newU;
            this.returningUsers = returning;
            this.engagementRate = engagement;
            this.lastUpdateTime = timestamp;
            
            historicalActiveUsers.add(active);
            if (historicalActiveUsers.size() > 100) {
                historicalActiveUsers.remove(0);
            }
        }
        
        public void updateSessionMetrics(double avgDuration, int sessions) {
            this.avgSessionDuration = avgDuration;
            this.totalSessions = sessions;
        }
        
        public int getActiveUsers() { return activeUsers; }
        public int getNewUsers() { return newUsers; }
        public List<Integer> getHistoricalActiveUsers() { return new ArrayList<>(historicalActiveUsers); }
    }
    
    private static class ConversionMetrics {
        private final String funnelStage;
        private volatile int visitors = 0;
        private volatile int conversions = 0;
        private volatile double conversionRate = 0.0;
        private volatile BigDecimal conversionValue = BigDecimal.ZERO;
        private final Map<String, Double> channelRates = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime = 0;
        
        public ConversionMetrics(String funnelStage) {
            this.funnelStage = funnelStage;
        }
        
        public void updateConversion(String channel, int visitors, int conversions,
                                    double rate, BigDecimal value, long timestamp) {
            this.visitors = visitors;
            this.conversions = conversions;
            this.conversionRate = rate;
            this.conversionValue = value;
            this.lastUpdateTime = timestamp;
            
            channelRates.put(channel, rate);
        }
        
        public double getConversionRate() { return conversionRate; }
    }
    
    private static class CustomerMetrics {
        private final String segment;
        private volatile BigDecimal lifetimeValue = BigDecimal.ZERO;
        private volatile int purchaseCount = 0;
        private volatile double retentionRate = 0.0;
        private volatile double churnProbability = 0.0;
        private final AtomicInteger churnCount = new AtomicInteger(0);
        private volatile double satisfactionScore = 0.0;
        private volatile int promoters = 0;
        private volatile int detractors = 0;
        private volatile int totalResponses = 0;
        private final Map<String, Double> categoryScores = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime = 0;
        
        public CustomerMetrics(String segment) {
            this.segment = segment;
        }
        
        public void updateLifecycle(String stage, BigDecimal ltv, int purchases,
                                   double retention, double churn, long timestamp) {
            this.lifetimeValue = ltv;
            this.purchaseCount = purchases;
            this.retentionRate = retention;
            this.churnProbability = churn;
            this.lastUpdateTime = timestamp;
        }
        
        public void updateSatisfaction(String type, double score, int responses, long timestamp) {
            this.satisfactionScore = score;
            this.totalResponses = responses;
            this.lastUpdateTime = timestamp;
        }
        
        public void updateSentiment(int positive, int neutral, int negative) {
            this.promoters = positive;
            this.detractors = negative;
        }
        
        public void addCategoryScore(String category, double score) {
            categoryScores.put(category, score);
        }
        
        public void incrementChurnCount() {
            churnCount.incrementAndGet();
        }
        
        public double calculateChurnRate() {
            return churnCount.get() / Math.max(1.0, totalResponses);
        }
        
        public BigDecimal getLifetimeValue() { return lifetimeValue; }
        public double getSatisfactionScore() { return satisfactionScore; }
        public int getPromoters() { return promoters; }
        public int getDetractors() { return detractors; }
        public int getTotalResponses() { return totalResponses; }
    }
    
    private static class ProductMetrics {
        private final String productId;
        private final String productName;
        private final String category;
        private volatile int unitsSold = 0;
        private volatile BigDecimal revenue = BigDecimal.ZERO;
        private volatile BigDecimal profit = BigDecimal.ZERO;
        private volatile double marginPercentage = 0.0;
        private volatile double marketShare = 0.0;
        private volatile long lastUpdateTime = 0;
        
        public ProductMetrics(String productId, String productName, String category) {
            this.productId = productId;
            this.productName = productName;
            this.category = category;
        }
        
        public void updatePerformance(int units, BigDecimal revenue, BigDecimal profit,
                                     double margin, double share, long timestamp) {
            this.unitsSold = units;
            this.revenue = revenue;
            this.profit = profit;
            this.marginPercentage = margin;
            this.marketShare = share;
            this.lastUpdateTime = timestamp;
        }
        
        public BigDecimal getRevenue() { return revenue; }
    }
    
    private static class MarketingMetrics {
        private final String campaign;
        private volatile BigDecimal spend = BigDecimal.ZERO;
        private volatile int impressions = 0;
        private volatile int clicks = 0;
        private volatile int conversions = 0;
        private volatile BigDecimal revenue = BigDecimal.ZERO;
        private volatile double roi = 0.0;
        private volatile double customerAcquisitionCost = 0.0;
        private volatile double clickThroughRate = 0.0;
        private volatile double conversionRate = 0.0;
        private final Map<String, Double> channelPerformance = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime = 0;
        
        public MarketingMetrics(String campaign) {
            this.campaign = campaign;
        }
        
        public void updateCampaignMetrics(String channel, BigDecimal spend, int impressions,
                                         int clicks, int conversions, BigDecimal revenue,
                                         double roi, double cac, long timestamp) {
            this.spend = spend;
            this.impressions = impressions;
            this.clicks = clicks;
            this.conversions = conversions;
            this.revenue = revenue;
            this.roi = roi;
            this.customerAcquisitionCost = cac;
            this.lastUpdateTime = timestamp;
            
            channelPerformance.put(channel, roi);
        }
        
        public void updateEngagementMetrics(double ctr, double convRate) {
            this.clickThroughRate = ctr;
            this.conversionRate = convRate;
        }
        
        public double getCustomerAcquisitionCost() { return customerAcquisitionCost; }
    }
    
    private static class OperationalMetrics {
        private final String department;
        private final Map<String, Double> metrics = new ConcurrentHashMap<>();
        private final Map<String, Double> targets = new ConcurrentHashMap<>();
        private final Map<String, Double> variances = new ConcurrentHashMap<>();
        private final Map<String, Double> components = new ConcurrentHashMap<>();
        private volatile double efficiencyScore = 0.5;
        private volatile long lastUpdateTime = 0;
        
        public OperationalMetrics(String department) {
            this.department = department;
        }
        
        public void updateEfficiency(String metric, double value, String unit,
                                    double target, double variance, long timestamp) {
            metrics.put(metric, value);
            targets.put(metric, target);
            variances.put(metric, variance);
            this.lastUpdateTime = timestamp;
            
            calculateEfficiencyScore();
        }
        
        public void addComponentMetric(String component, double value) {
            components.put(component, value);
        }
        
        private void calculateEfficiencyScore() {
            if (variances.isEmpty()) {
                efficiencyScore = 0.5;
                return;
            }
            
            double avgVariance = variances.values().stream()
                .mapToDouble(Math::abs)
                .average().orElse(0.5);
            
            efficiencyScore = Math.max(0, Math.min(1, 1 - avgVariance));
        }
        
        public double getEfficiencyScore() { return efficiencyScore; }
    }
    
    private static class FinancialMetrics {
        private final String period;
        private final Map<String, BigDecimal> metrics = new ConcurrentHashMap<>();
        private final Map<String, BigDecimal> components = new ConcurrentHashMap<>();
        private volatile String currency = "USD";
        private volatile double growthRate = 0.0;
        private volatile double forecast = 0.0;
        private volatile long lastUpdateTime = 0;
        
        public FinancialMetrics(String period) {
            this.period = period;
        }
        
        public void updateFinancial(String type, BigDecimal value, String currency,
                                   double growth, double forecast, long timestamp) {
            metrics.put(type, value);
            this.currency = currency;
            this.growthRate = growth;
            this.forecast = forecast;
            this.lastUpdateTime = timestamp;
        }
        
        public void addComponent(String component, BigDecimal value) {
            components.put(component, value);
        }
    }
    
    private static class GrowthMetrics {
        private final String growthType;
        private volatile double currentValue = 0.0;
        private volatile double previousValue = 0.0;
        private volatile double growthRate = 0.0;
        private volatile double targetGrowthRate = 0.0;
        private volatile String period = "";
        private volatile String segment = "";
        private volatile long lastUpdateTime = 0;
        
        public GrowthMetrics(String growthType) {
            this.growthType = growthType;
        }
        
        public void updateGrowth(String period, double current, double previous,
                               double rate, double target, String segment, long timestamp) {
            this.period = period;
            this.currentValue = current;
            this.previousValue = previous;
            this.growthRate = rate;
            this.targetGrowthRate = target;
            this.segment = segment;
            this.lastUpdateTime = timestamp;
        }
        
        public double getGrowthRate() { return growthRate; }
    }
    
    private static class RetentionMetrics {
        private final String cohort;
        private volatile int totalUsers = 0;
        private volatile int retainedUsers = 0;
        private volatile double retentionRate = 0.0;
        private volatile double churnRate = 0.0;
        private List<Double> retentionCurve = new ArrayList<>();
        private volatile String period = "";
        private volatile long lastUpdateTime = 0;
        
        public RetentionMetrics(String cohort) {
            this.cohort = cohort;
        }
        
        public void updateRetention(String period, int total, int retained,
                                   double retention, double churn, long timestamp) {
            this.period = period;
            this.totalUsers = total;
            this.retainedUsers = retained;
            this.retentionRate = retention;
            this.churnRate = churn;
            this.lastUpdateTime = timestamp;
        }
        
        public void setRetentionCurve(List<Double> curve) {
            this.retentionCurve = new ArrayList<>(curve);
        }
        
        public double getRetentionRate() { return retentionRate; }
        public double getChurnRate() { return churnRate; }
    }
    
    private static class SegmentMetrics {
        private final String segmentName;
        private volatile BigDecimal revenue = BigDecimal.ZERO;
        private volatile int customers = 0;
        private volatile double avgOrderValue = 0.0;
        private volatile double conversionRate = 0.0;
        private volatile double retentionRate = 0.0;
        private volatile BigDecimal arpu = BigDecimal.ZERO;
        private final Map<String, String> demographics = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime = 0;
        
        public SegmentMetrics(String segmentName) {
            this.segmentName = segmentName;
        }
        
        public void updatePerformance(BigDecimal revenue, int customers, double aov,
                                     double conversion, double retention, long timestamp) {
            this.revenue = revenue;
            this.customers = customers;
            this.avgOrderValue = aov;
            this.conversionRate = conversion;
            this.retentionRate = retention;
            this.lastUpdateTime = timestamp;
        }
        
        public void addDemographic(String key, String value) {
            demographics.put(key, value);
        }
        
        public void setArpu(BigDecimal arpu) { this.arpu = arpu; }
        
        public String getSegmentName() { return segmentName; }
        public BigDecimal getRevenue() { return revenue; }
        public int getCustomers() { return customers; }
        public double getConversionRate() { return conversionRate; }
        public double getRetentionRate() { return retentionRate; }
    }
    
    private static class KpiMetrics {
        private final String kpiName;
        private volatile double currentValue = 0.0;
        private volatile double target = 0.0;
        private volatile double variance = 0.0;
        private volatile double calculatedValue = 0.0;
        private volatile String period = "";
        private volatile String status = "";
        private volatile String trendDirection = "STABLE";
        private List<Double> trendData = new ArrayList<>();
        private volatile long lastUpdateTime = 0;
        
        public KpiMetrics(String kpiName) {
            this.kpiName = kpiName;
        }
        
        public void updateKpi(double value, double target, double variance,
                            String period, String status, long timestamp) {
            this.currentValue = value;
            this.target = target;
            this.variance = variance;
            this.period = period;
            this.status = status;
            this.lastUpdateTime = timestamp;
        }
        
        public void updateCalculatedValue(double value, long timestamp) {
            this.calculatedValue = value;
            this.lastUpdateTime = timestamp;
        }
        
        public void setTrendData(List<Double> data) {
            this.trendData = new ArrayList<>(data);
        }
        
        public void setTrendDirection(String direction) {
            this.trendDirection = direction;
        }
        
        public boolean hasSignificantVariance() {
            return Math.abs(variance) > 0.15;
        }
        
        public double getCurrentValue() { return currentValue; }
        public double getTarget() { return target; }
        public double getVariance() { return variance; }
        public String getTrendDirection() { return trendDirection; }
    }
    
    private static class ForecastMetrics {
        private final int horizonDays;
        private volatile double revenueForecast = 0.0;
        private volatile double userGrowthForecast = 0.0;
        private volatile double churnForecast = 0.0;
        private volatile double conversionForecast = 0.0;
        private final Map<String, Double> segmentForecasts = new ConcurrentHashMap<>();
        private volatile long generatedAt = System.currentTimeMillis();
        
        public ForecastMetrics(int horizonDays) {
            this.horizonDays = horizonDays;
        }
        
        public void setRevenueForecast(double forecast) { this.revenueForecast = forecast; }
        public void setUserGrowthForecast(double forecast) { this.userGrowthForecast = forecast; }
        public void setChurnForecast(double forecast) { this.churnForecast = forecast; }
        public void setConversionForecast(double forecast) { this.conversionForecast = forecast; }
        
        public double getRevenueForecast() { return revenueForecast; }
    }
    
    private static class BusinessHealthScore {
        private volatile double revenueHealth = 0.5;
        private volatile double customerHealth = 0.5;
        private volatile double operationalHealth = 0.5;
        private volatile double growthHealth = 0.5;
        private final Map<String, GoalProgress> goalProgress = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime = 0;
        
        public void updateHealthScores(double revenue, double customer,
                                      double operational, double growth, long timestamp) {
            this.revenueHealth = revenue;
            this.customerHealth = customer;
            this.operationalHealth = operational;
            this.growthHealth = growth;
            this.lastUpdateTime = timestamp;
        }
        
        public void updateGoalProgress(String goalId, String goalName, double progress,
                                      double target, String deadline, String status, long timestamp) {
            GoalProgress goal = new GoalProgress(goalName, progress, target, deadline, status);
            goalProgress.put(goalId, goal);
            this.lastUpdateTime = timestamp;
        }
        
        public void addMilestone(String goalId, String milestone, boolean completed) {
            GoalProgress goal = goalProgress.get(goalId);
            if (goal != null) {
                goal.addMilestone(milestone, completed);
            }
        }
        
        public double calculateOverallHealth() {
            return (revenueHealth + customerHealth + operationalHealth + growthHealth) / 4;
        }
        
        private static class GoalProgress {
            private final String goalName;
            private final double progress;
            private final double target;
            private final String deadline;
            private final String status;
            private final Map<String, Boolean> milestones = new HashMap<>();
            
            public GoalProgress(String goalName, double progress, double target,
                              String deadline, String status) {
                this.goalName = goalName;
                this.progress = progress;
                this.target = target;
                this.deadline = deadline;
                this.status = status;
            }
            
            public void addMilestone(String name, boolean completed) {
                milestones.put(name, completed);
            }
        }
    }
    
    private static class TrendAnalysis {
        private final boolean significant;
        private final String direction;
        private final double strength;
        private final String prediction;
        private final String severity;
        
        public TrendAnalysis(boolean significant, String direction, double strength,
                           String prediction, String severity) {
            this.significant = significant;
            this.direction = direction;
            this.strength = strength;
            this.prediction = prediction;
            this.severity = severity;
        }
        
        public boolean isSignificant() { return significant; }
        public String getDirection() { return direction; }
        public double getStrength() { return strength; }
        public String getPrediction() { return prediction; }
        public String getSeverity() { return severity; }
    }
    
    private static class SegmentAnalysis {
        private final boolean requiresAttention;
        private final String reason;
        private final double healthScore;
        
        public SegmentAnalysis(boolean requiresAttention, String reason, double healthScore) {
            this.requiresAttention = requiresAttention;
            this.reason = reason;
            this.healthScore = healthScore;
        }
        
        public boolean requiresAttention() { return requiresAttention; }
        public String getReason() { return reason; }
        public double getHealthScore() { return healthScore; }
    }
    
    private static class AnomalyDetection {
        private final boolean isAnomaly;
        private final double actualValue;
        private final double expectedValue;
        private final double deviation;
        private final double zScore;
        
        public AnomalyDetection(boolean isAnomaly, double actualValue, double expectedValue,
                               double deviation, double zScore) {
            this.isAnomaly = isAnomaly;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
            this.deviation = deviation;
            this.zScore = zScore;
        }
        
        public boolean isAnomaly() { return isAnomaly; }
        public double getActualValue() { return actualValue; }
        public double getExpectedValue() { return expectedValue; }
        public double getDeviation() { return deviation; }
    }
}