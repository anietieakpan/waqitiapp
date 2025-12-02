package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.entity.CostMetrics;
import com.waqiti.monitoring.repository.CostMetricsRepository;
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
public class CostMonitoringConsumer {

    private static final String TOPIC = "cost-monitoring";
    private static final String GROUP_ID = "monitoring-cost-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final double BUDGET_WARNING_THRESHOLD = 0.80;
    private static final double BUDGET_CRITICAL_THRESHOLD = 0.95;
    private static final double COST_SPIKE_THRESHOLD = 1.50;
    private static final double RESOURCE_WASTE_THRESHOLD = 0.30;
    private static final double RESERVED_INSTANCE_UTILIZATION_THRESHOLD = 0.70;
    private static final double SAVINGS_OPPORTUNITY_THRESHOLD = 100.0;
    private static final double BILLING_ANOMALY_THRESHOLD = 0.25;
    private static final int UNUSED_RESOURCE_DAYS_THRESHOLD = 7;
    private static final double COST_EFFICIENCY_THRESHOLD = 0.60;
    private static final double TRANSACTION_COST_THRESHOLD = 0.10;
    private static final double VENDOR_PRICE_INCREASE_THRESHOLD = 0.10;
    private static final double LICENSE_UTILIZATION_THRESHOLD = 0.50;
    private static final double CLOUD_COMMITMENT_THRESHOLD = 0.85;
    private static final double FORECASTED_OVERRUN_THRESHOLD = 1.10;
    private static final int COST_ALLOCATION_ACCURACY_THRESHOLD = 95;
    private static final int ANALYSIS_WINDOW_MINUTES = 60;
    
    private final CostMetricsRepository metricsRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final BudgetManagementService budgetService;
    private final CostOptimizationService optimizationService;
    private final ResourceUtilizationService utilizationService;
    private final FinancialForecastingService forecastingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, CostMonitoringState> costStates = new ConcurrentHashMap<>();
    private final Map<String, CloudCostTracker> cloudTrackers = new ConcurrentHashMap<>();
    private final Map<String, ResourceCostAnalyzer> resourceAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, BudgetMonitor> budgetMonitors = new ConcurrentHashMap<>();
    private final Map<String, BillingAnalyzer> billingAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, SavingsIdentifier> savingsIdentifiers = new ConcurrentHashMap<>();
    private final Map<String, TransactionCostCalculator> transactionCalculators = new ConcurrentHashMap<>();
    private final Map<String, VendorCostTracker> vendorTrackers = new ConcurrentHashMap<>();
    private final Map<String, LicenseManager> licenseManagers = new ConcurrentHashMap<>();
    private final Map<String, CostAllocationEngine> allocationEngines = new ConcurrentHashMap<>();
    private final Map<String, ForecastingModel> forecastingModels = new ConcurrentHashMap<>();
    private final Map<String, CostAnomaly> activeAnomalies = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<CostEventData> eventQueue = new LinkedBlockingQueue<>(10000);
    
    public CostMonitoringConsumer(CostMetricsRepository metricsRepository,
                                AlertService alertService,
                                MetricsService metricsService,
                                NotificationService notificationService,
                                BudgetManagementService budgetService,
                                CostOptimizationService optimizationService,
                                ResourceUtilizationService utilizationService,
                                FinancialForecastingService forecastingService,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.metricsRepository = metricsRepository;
        this.alertService = alertService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.budgetService = budgetService;
        this.optimizationService = optimizationService;
        this.utilizationService = utilizationService;
        this.forecastingService = forecastingService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter anomalyCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge totalCostGauge;
    private Gauge budgetUtilizationGauge;
    private Gauge savingsOpportunityGauge;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        initializeTrackers();
        loadBudgetConfigurations();
        establishBaselines();
        log.info("CostMonitoringConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("cost.monitoring.events.processed");
        errorCounter = meterRegistry.counter("cost.monitoring.events.errors");
        anomalyCounter = meterRegistry.counter("cost.monitoring.anomalies.detected");
        processingTimer = meterRegistry.timer("cost.monitoring.processing.time");
        queueSizeGauge = meterRegistry.gauge("cost.monitoring.queue.size", eventQueue, Queue::size);
        
        totalCostGauge = meterRegistry.gauge("cost.monitoring.total.cost", 
            costStates, states -> calculateTotalCost(states));
        budgetUtilizationGauge = meterRegistry.gauge("cost.monitoring.budget.utilization",
            budgetMonitors, monitors -> calculateBudgetUtilization(monitors));
        savingsOpportunityGauge = meterRegistry.gauge("cost.monitoring.savings.opportunity",
            savingsIdentifiers, identifiers -> calculateTotalSavings(identifiers));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::analyzeCostTrends, 5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::identifySavingsOpportunities, 10, 10, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::forecastCosts, 1, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::generateCostReports, 1, 24, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
    }
    
    private void initializeTrackers() {
        Arrays.asList("AWS", "Azure", "GCP", "Infrastructure", "Application").forEach(category -> {
            cloudTrackers.put(category, new CloudCostTracker(category));
            resourceAnalyzers.put(category, new ResourceCostAnalyzer(category));
            budgetMonitors.put(category, new BudgetMonitor(category));
            billingAnalyzers.put(category, new BillingAnalyzer(category));
            savingsIdentifiers.put(category, new SavingsIdentifier(category));
            transactionCalculators.put(category, new TransactionCostCalculator(category));
            vendorTrackers.put(category, new VendorCostTracker(category));
            licenseManagers.put(category, new LicenseManager(category));
            allocationEngines.put(category, new CostAllocationEngine(category));
            forecastingModels.put(category, new ForecastingModel(category));
            costStates.put(category, new CostMonitoringState(category));
        });
    }
    
    private void loadBudgetConfigurations() {
        try {
            budgetService.loadBudgetConfigurations();
            budgetMonitors.values().forEach(monitor -> 
                monitor.updateBudgets(budgetService.getBudgets()));
            log.info("Loaded budget configurations");
        } catch (Exception e) {
            log.error("Error loading budget configurations: {}", e.getMessage(), e);
        }
    }
    
    private void establishBaselines() {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        metricsRepository.findByTimestampAfter(oneMonthAgo)
            .forEach(metric -> {
                String category = metric.getCategory();
                CostMonitoringState state = costStates.get(category);
                if (state != null) {
                    state.updateBaseline(metric);
                }
            });
        log.info("Established cost baselines for {} categories", costStates.size());
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "costMonitoring", fallbackMethod = "handleMessageFallback")
    @Retry(name = "costMonitoring", fallbackMethod = "handleMessageFallback")
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
            log.debug("Processing cost monitoring event from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing cost monitoring event: {}", e.getMessage(), e);
            errorCounter.increment();
            handleProcessingError(record, e, acknowledgment);
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "CLOUD_USAGE":
                    processCloudUsage(eventData);
                    break;
                case "RESOURCE_COST":
                    processResourceCost(eventData);
                    break;
                case "BUDGET_STATUS":
                    processBudgetStatus(eventData);
                    break;
                case "BILLING_ALERT":
                    processBillingAlert(eventData);
                    break;
                case "COST_ANOMALY":
                    processCostAnomaly(eventData);
                    break;
                case "RESERVED_INSTANCES":
                    processReservedInstances(eventData);
                    break;
                case "SAVINGS_PLAN":
                    processSavingsPlan(eventData);
                    break;
                case "TRANSACTION_COST":
                    processTransactionCost(eventData);
                    break;
                case "VENDOR_INVOICE":
                    processVendorInvoice(eventData);
                    break;
                case "LICENSE_USAGE":
                    processLicenseUsage(eventData);
                    break;
                case "COST_ALLOCATION":
                    processCostAllocation(eventData);
                    break;
                case "RESOURCE_OPTIMIZATION":
                    processResourceOptimization(eventData);
                    break;
                case "FORECAST_UPDATE":
                    processForecastUpdate(eventData);
                    break;
                case "UNUSED_RESOURCES":
                    processUnusedResources(eventData);
                    break;
                case "COMMITMENT_UTILIZATION":
                    processCommitmentUtilization(eventData);
                    break;
                default:
                    log.warn("Unknown cost monitoring event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processCloudUsage(JsonNode eventData) {
        String provider = eventData.get("provider").asText();
        String service = eventData.get("service").asText();
        String region = eventData.get("region").asText();
        double usageAmount = eventData.get("usageAmount").asDouble();
        double costAmount = eventData.get("costAmount").asDouble();
        String usageUnit = eventData.get("usageUnit").asText();
        JsonNode tags = eventData.get("tags");
        long timestamp = eventData.get("timestamp").asLong();
        
        CloudCostTracker tracker = cloudTrackers.get(provider);
        if (tracker != null) {
            tracker.recordUsage(service, region, usageAmount, costAmount, usageUnit, tags, timestamp);
            
            double dailyCost = tracker.getDailyCost(service, region);
            double monthlyProjection = dailyCost * 30;
            
            checkCostSpike(provider, service, dailyCost, tracker.getAverageDailyCost(service));
            
            if (tags != null && tags.has("environment")) {
                allocateCostToEnvironment(provider, tags.get("environment").asText(), costAmount);
            }
        }
        
        updateCostState(provider, state -> {
            state.addCloudCost(service, costAmount);
            state.updateUsage(service, usageAmount);
        });
        
        metricsService.recordCloudUsage(provider, service, region, usageAmount, costAmount);
        
        CostMetrics metrics = CostMetrics.builder()
            .category(provider)
            .service(service)
            .costAmount(costAmount)
            .usageAmount(usageAmount)
            .region(region)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        metricsRepository.save(metrics);
    }
    
    private void processResourceCost(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String resourceId = eventData.get("resourceId").asText();
        String resourceType = eventData.get("resourceType").asText();
        double hourlyCost = eventData.get("hourlyCost").asDouble();
        double utilizationPercentage = eventData.get("utilizationPercentage").asDouble();
        boolean idle = eventData.get("idle").asBoolean();
        JsonNode metadata = eventData.get("metadata");
        long timestamp = eventData.get("timestamp").asLong();
        
        ResourceCostAnalyzer analyzer = resourceAnalyzers.get(category);
        if (analyzer != null) {
            analyzer.analyzeResource(resourceId, resourceType, hourlyCost, 
                                    utilizationPercentage, idle, metadata, timestamp);
            
            if (utilizationPercentage < RESOURCE_WASTE_THRESHOLD && hourlyCost > 0) {
                double wastedCost = hourlyCost * (1 - utilizationPercentage);
                String message = String.format("Underutilized resource %s: %.2f%% utilization, wasting $%.2f/hour", 
                    resourceId, utilizationPercentage * 100, wastedCost);
                
                alertService.createAlert("RESOURCE_WASTE", "WARNING", message,
                    Map.of("category", category, "resourceId", resourceId, 
                           "utilization", utilizationPercentage, "wastedCost", wastedCost));
                
                suggestResourceOptimization(category, resourceId, resourceType, utilizationPercentage);
            }
            
            if (idle) {
                handleIdleResource(category, resourceId, resourceType, hourlyCost);
            }
        }
        
        updateCostState(category, state -> {
            state.updateResourceCost(resourceId, hourlyCost);
            state.recordUtilization(resourceId, utilizationPercentage);
        });
        
        metricsService.recordResourceCost(category, resourceId, resourceType, hourlyCost, utilizationPercentage);
    }
    
    private void processBudgetStatus(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String budgetName = eventData.get("budgetName").asText();
        double budgetAmount = eventData.get("budgetAmount").asDouble();
        double actualSpend = eventData.get("actualSpend").asDouble();
        double forecastedSpend = eventData.get("forecastedSpend").asDouble();
        String period = eventData.get("period").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        BudgetMonitor monitor = budgetMonitors.get(category);
        if (monitor != null) {
            monitor.updateBudgetStatus(budgetName, budgetAmount, actualSpend, 
                                      forecastedSpend, period, timestamp);
            
            double utilizationRate = actualSpend / budgetAmount;
            
            if (utilizationRate >= BUDGET_CRITICAL_THRESHOLD) {
                String message = String.format("Critical budget threshold reached for %s: $%.2f of $%.2f (%.2f%%)", 
                    budgetName, actualSpend, budgetAmount, utilizationRate * 100);
                
                alertService.createAlert("BUDGET_CRITICAL", "CRITICAL", message,
                    Map.of("category", category, "budgetName", budgetName, 
                           "actualSpend", actualSpend, "budgetAmount", budgetAmount));
                
                implementCostControls(category, budgetName, actualSpend, budgetAmount);
            } else if (utilizationRate >= BUDGET_WARNING_THRESHOLD) {
                notifyBudgetWarning(category, budgetName, actualSpend, budgetAmount);
            }
            
            if (forecastedSpend > budgetAmount * FORECASTED_OVERRUN_THRESHOLD) {
                handleForecastedOverrun(category, budgetName, forecastedSpend, budgetAmount);
            }
        }
        
        budgetService.trackBudgetUtilization(budgetName, actualSpend, budgetAmount);
        
        updateCostState(category, state -> {
            state.updateBudgetStatus(budgetName, utilizationRate);
        });
        
        metricsService.recordBudgetStatus(category, budgetName, actualSpend, budgetAmount);
    }
    
    private void processBillingAlert(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String alertType = eventData.get("alertType").asText();
        double amount = eventData.get("amount").asDouble();
        String description = eventData.get("description").asText();
        JsonNode charges = eventData.get("charges");
        long timestamp = eventData.get("timestamp").asLong();
        
        BillingAnalyzer analyzer = billingAnalyzers.get(category);
        if (analyzer != null) {
            analyzer.processBillingAlert(alertType, amount, description, charges, timestamp);
            
            if ("UNEXPECTED_CHARGE".equals(alertType)) {
                investigateUnexpectedCharge(category, amount, description, charges);
            }
            
            double anomalyScore = analyzer.calculateAnomalyScore(amount, charges);
            if (anomalyScore > BILLING_ANOMALY_THRESHOLD) {
                handleBillingAnomaly(category, alertType, amount, anomalyScore);
            }
        }
        
        updateCostState(category, state -> {
            state.recordBillingAlert(alertType, amount);
        });
        
        metricsService.recordBillingAlert(category, alertType, amount);
    }
    
    private void processCostAnomaly(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String anomalyType = eventData.get("anomalyType").asText();
        String service = eventData.get("service").asText();
        double normalCost = eventData.get("normalCost").asDouble();
        double actualCost = eventData.get("actualCost").asDouble();
        double deviation = eventData.get("deviation").asDouble();
        JsonNode rootCause = eventData.get("rootCause");
        long timestamp = eventData.get("timestamp").asLong();
        
        String anomalyId = UUID.randomUUID().toString();
        CostAnomaly anomaly = new CostAnomaly(anomalyId, category, anomalyType, 
                                             service, actualCost, deviation);
        activeAnomalies.put(anomalyId, anomaly);
        
        anomalyCounter.increment();
        
        String message = String.format("Cost anomaly detected in %s/%s: $%.2f (expected: $%.2f, deviation: %.2f%%)", 
            category, service, actualCost, normalCost, deviation * 100);
        
        alertService.createAlert("COST_ANOMALY", "HIGH", message,
            Map.of("category", category, "service", service, 
                   "actualCost", actualCost, "normalCost", normalCost));
        
        investigateAnomaly(category, service, actualCost, normalCost, rootCause);
        
        updateCostState(category, state -> {
            state.recordAnomaly(anomalyType, service, deviation);
        });
        
        metricsService.recordCostAnomaly(category, anomalyType, service, deviation);
    }
    
    private void processReservedInstances(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String instanceType = eventData.get("instanceType").asText();
        int totalInstances = eventData.get("totalInstances").asInt();
        int usedInstances = eventData.get("usedInstances").asInt();
        double monthlyCost = eventData.get("monthlyCost").asDouble();
        double onDemandCost = eventData.get("onDemandCost").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        double utilization = (double) usedInstances / totalInstances;
        
        if (utilization < RESERVED_INSTANCE_UTILIZATION_THRESHOLD) {
            double wastedCost = (totalInstances - usedInstances) * (monthlyCost / totalInstances);
            String message = String.format("Low RI utilization for %s: %.2f%% (%d/%d), wasting $%.2f/month", 
                instanceType, utilization * 100, usedInstances, totalInstances, wastedCost);
            
            alertService.createAlert("LOW_RI_UTILIZATION", "WARNING", message,
                Map.of("category", category, "instanceType", instanceType, 
                       "utilization", utilization, "wastedCost", wastedCost));
            
            optimizeReservedInstances(category, instanceType, totalInstances, usedInstances);
        }
        
        double savings = onDemandCost - monthlyCost;
        if (savings > 0) {
            trackReservedInstanceSavings(category, instanceType, savings);
        }
        
        updateCostState(category, state -> {
            state.updateReservedInstanceUtilization(instanceType, utilization);
        });
        
        metricsService.recordReservedInstances(category, instanceType, utilization);
    }
    
    private void processSavingsPlan(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String planType = eventData.get("planType").asText();
        double commitment = eventData.get("commitment").asDouble();
        double utilizedAmount = eventData.get("utilizedAmount").asDouble();
        double savingsAmount = eventData.get("savingsAmount").asDouble();
        String term = eventData.get("term").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        SavingsIdentifier identifier = savingsIdentifiers.get(category);
        if (identifier != null) {
            identifier.recordSavingsPlan(planType, commitment, utilizedAmount, 
                                        savingsAmount, term, timestamp);
            
            double utilizationRate = utilizedAmount / commitment;
            if (utilizationRate < CLOUD_COMMITMENT_THRESHOLD) {
                String message = String.format("Underutilized savings plan %s: %.2f%% of $%.2f commitment", 
                    planType, utilizationRate * 100, commitment);
                
                alertService.createAlert("SAVINGS_PLAN_UNDERUTILIZED", "INFO", message,
                    Map.of("category", category, "planType", planType, 
                           "utilization", utilizationRate, "commitment", commitment));
            }
            
            if (savingsAmount > SAVINGS_OPPORTUNITY_THRESHOLD) {
                notifySavingsAchieved(category, planType, savingsAmount);
            }
        }
        
        updateCostState(category, state -> {
            state.recordSavingsPlan(planType, savingsAmount);
        });
        
        metricsService.recordSavingsPlan(category, planType, savingsAmount);
    }
    
    private void processTransactionCost(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String transactionType = eventData.get("transactionType").asText();
        int transactionCount = eventData.get("transactionCount").asInt();
        double totalCost = eventData.get("totalCost").asDouble();
        double avgCostPerTransaction = eventData.get("avgCostPerTransaction").asDouble();
        JsonNode breakdown = eventData.get("breakdown");
        long timestamp = eventData.get("timestamp").asLong();
        
        TransactionCostCalculator calculator = transactionCalculators.get(category);
        if (calculator != null) {
            calculator.analyzeTransactionCost(transactionType, transactionCount, 
                                             totalCost, avgCostPerTransaction, breakdown, timestamp);
            
            if (avgCostPerTransaction > TRANSACTION_COST_THRESHOLD) {
                String message = String.format("High transaction cost for %s: $%.4f per transaction", 
                    transactionType, avgCostPerTransaction);
                
                alertService.createAlert("HIGH_TRANSACTION_COST", "INFO", message,
                    Map.of("category", category, "transactionType", transactionType, 
                           "avgCost", avgCostPerTransaction));
                
                optimizeTransactionCost(category, transactionType, breakdown);
            }
            
            projectTransactionCosts(calculator, transactionType, transactionCount);
        }
        
        updateCostState(category, state -> {
            state.updateTransactionCost(transactionType, totalCost, transactionCount);
        });
        
        metricsService.recordTransactionCost(category, transactionType, totalCost, transactionCount);
    }
    
    private void processVendorInvoice(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String vendorName = eventData.get("vendorName").asText();
        String invoiceNumber = eventData.get("invoiceNumber").asText();
        double amount = eventData.get("amount").asDouble();
        String currency = eventData.get("currency").asText();
        long dueDate = eventData.get("dueDate").asLong();
        JsonNode lineItems = eventData.get("lineItems");
        long timestamp = eventData.get("timestamp").asLong();
        
        VendorCostTracker tracker = vendorTrackers.get(category);
        if (tracker != null) {
            tracker.recordInvoice(vendorName, invoiceNumber, amount, currency, 
                                dueDate, lineItems, timestamp);
            
            double priceChange = tracker.calculatePriceChange(vendorName, amount);
            if (priceChange > VENDOR_PRICE_INCREASE_THRESHOLD) {
                String message = String.format("Vendor price increase for %s: %.2f%% increase", 
                    vendorName, priceChange * 100);
                
                alertService.createAlert("VENDOR_PRICE_INCREASE", "WARNING", message,
                    Map.of("category", category, "vendorName", vendorName, 
                           "priceChange", priceChange));
                
                renegotiateContract(category, vendorName, priceChange);
            }
            
            validateInvoice(tracker, vendorName, invoiceNumber, lineItems);
        }
        
        updateCostState(category, state -> {
            state.addVendorCost(vendorName, amount);
        });
        
        metricsService.recordVendorInvoice(category, vendorName, amount);
    }
    
    private void processLicenseUsage(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String licenseName = eventData.get("licenseName").asText();
        int totalLicenses = eventData.get("totalLicenses").asInt();
        int usedLicenses = eventData.get("usedLicenses").asInt();
        double costPerLicense = eventData.get("costPerLicense").asDouble();
        long renewalDate = eventData.get("renewalDate").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        LicenseManager manager = licenseManagers.get(category);
        if (manager != null) {
            manager.trackLicenseUsage(licenseName, totalLicenses, usedLicenses, 
                                     costPerLicense, renewalDate, timestamp);
            
            double utilization = (double) usedLicenses / totalLicenses;
            if (utilization < LICENSE_UTILIZATION_THRESHOLD) {
                int unusedLicenses = totalLicenses - usedLicenses;
                double wastedCost = unusedLicenses * costPerLicense;
                
                String message = String.format("License underutilization for %s: %d unused licenses, $%.2f wasted", 
                    licenseName, unusedLicenses, wastedCost);
                
                alertService.createAlert("LICENSE_UNDERUTILIZATION", "INFO", message,
                    Map.of("category", category, "licenseName", licenseName, 
                           "unusedLicenses", unusedLicenses, "wastedCost", wastedCost));
                
                optimizeLicenseAllocation(category, licenseName, totalLicenses, usedLicenses);
            }
            
            checkLicenseRenewal(manager, licenseName, renewalDate);
        }
        
        updateCostState(category, state -> {
            state.updateLicenseUtilization(licenseName, utilization);
        });
        
        metricsService.recordLicenseUsage(category, licenseName, utilization);
    }
    
    private void processCostAllocation(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String costCenter = eventData.get("costCenter").asText();
        String department = eventData.get("department").asText();
        double allocatedAmount = eventData.get("allocatedAmount").asDouble();
        String allocationMethod = eventData.get("allocationMethod").asText();
        double accuracy = eventData.get("accuracy").asDouble();
        JsonNode tags = eventData.get("tags");
        long timestamp = eventData.get("timestamp").asLong();
        
        CostAllocationEngine engine = allocationEngines.get(category);
        if (engine != null) {
            engine.allocateCost(costCenter, department, allocatedAmount, 
                              allocationMethod, accuracy, tags, timestamp);
            
            if (accuracy < COST_ALLOCATION_ACCURACY_THRESHOLD) {
                String message = String.format("Low cost allocation accuracy for %s: %.2f%%", 
                    costCenter, accuracy);
                
                alertService.createAlert("LOW_ALLOCATION_ACCURACY", "WARNING", message,
                    Map.of("category", category, "costCenter", costCenter, "accuracy", accuracy));
                
                improveAllocationMethod(category, costCenter, allocationMethod);
            }
            
            validateCostAllocation(engine, costCenter, department, allocatedAmount);
        }
        
        updateCostState(category, state -> {
            state.updateCostAllocation(costCenter, department, allocatedAmount);
        });
        
        metricsService.recordCostAllocation(category, costCenter, department, allocatedAmount);
    }
    
    private void processResourceOptimization(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String optimizationType = eventData.get("optimizationType").asText();
        String resourceId = eventData.get("resourceId").asText();
        double currentCost = eventData.get("currentCost").asDouble();
        double optimizedCost = eventData.get("optimizedCost").asDouble();
        double savingsAmount = eventData.get("savingsAmount").asDouble();
        JsonNode recommendations = eventData.get("recommendations");
        long timestamp = eventData.get("timestamp").asLong();
        
        if (savingsAmount > SAVINGS_OPPORTUNITY_THRESHOLD) {
            String message = String.format("Optimization opportunity for %s: Save $%.2f (%.2f%% reduction)", 
                resourceId, savingsAmount, (savingsAmount / currentCost) * 100);
            
            alertService.createAlert("OPTIMIZATION_OPPORTUNITY", "INFO", message,
                Map.of("category", category, "resourceId", resourceId, 
                       "savings", savingsAmount));
            
            implementOptimization(category, optimizationType, resourceId, recommendations);
        }
        
        optimizationService.trackOptimization(category, resourceId, savingsAmount);
        
        updateCostState(category, state -> {
            state.recordOptimizationOpportunity(optimizationType, savingsAmount);
        });
        
        metricsService.recordOptimization(category, optimizationType, savingsAmount);
    }
    
    private void processForecastUpdate(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String forecastPeriod = eventData.get("forecastPeriod").asText();
        double forecastedAmount = eventData.get("forecastedAmount").asDouble();
        double confidence = eventData.get("confidence").asDouble();
        double budgetLimit = eventData.get("budgetLimit").asDouble();
        JsonNode assumptions = eventData.get("assumptions");
        long timestamp = eventData.get("timestamp").asLong();
        
        ForecastingModel model = forecastingModels.get(category);
        if (model != null) {
            model.updateForecast(forecastPeriod, forecastedAmount, confidence, 
                               assumptions, timestamp);
            
            if (forecastedAmount > budgetLimit) {
                double overrun = forecastedAmount - budgetLimit;
                String message = String.format("Forecasted budget overrun for %s: $%.2f over limit", 
                    category, overrun);
                
                alertService.createAlert("FORECAST_OVERRUN", "WARNING", message,
                    Map.of("category", category, "forecastedAmount", forecastedAmount, 
                           "budgetLimit", budgetLimit, "overrun", overrun));
                
                developCostReductionPlan(category, overrun, assumptions);
            }
        }
        
        forecastingService.processForecast(category, forecastPeriod, forecastedAmount, confidence);
        
        updateCostState(category, state -> {
            state.updateForecast(forecastPeriod, forecastedAmount);
        });
        
        metricsService.recordForecast(category, forecastPeriod, forecastedAmount);
    }
    
    private void processUnusedResources(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String resourceId = eventData.get("resourceId").asText();
        String resourceType = eventData.get("resourceType").asText();
        int daysUnused = eventData.get("daysUnused").asInt();
        double monthlyCost = eventData.get("monthlyCost").asDouble();
        String lastUsedDate = eventData.get("lastUsedDate").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (daysUnused >= UNUSED_RESOURCE_DAYS_THRESHOLD) {
            String message = String.format("Unused resource %s (%s) for %d days, costing $%.2f/month", 
                resourceId, resourceType, daysUnused, monthlyCost);
            
            alertService.createAlert("UNUSED_RESOURCE", "WARNING", message,
                Map.of("category", category, "resourceId", resourceId, 
                       "daysUnused", daysUnused, "monthlyCost", monthlyCost));
            
            if (daysUnused > 30) {
                recommendResourceTermination(category, resourceId, resourceType, monthlyCost);
            } else {
                scheduleResourceReview(category, resourceId, resourceType);
            }
        }
        
        utilizationService.trackUnusedResource(resourceId, daysUnused, monthlyCost);
        
        updateCostState(category, state -> {
            state.recordUnusedResource(resourceId, monthlyCost);
        });
        
        metricsService.recordUnusedResource(category, resourceId, daysUnused, monthlyCost);
    }
    
    private void processCommitmentUtilization(JsonNode eventData) {
        String category = eventData.get("category").asText();
        String commitmentType = eventData.get("commitmentType").asText();
        double committedAmount = eventData.get("committedAmount").asDouble();
        double utilizedAmount = eventData.get("utilizedAmount").asDouble();
        double remainingCommitment = eventData.get("remainingCommitment").asDouble();
        long expiryDate = eventData.get("expiryDate").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        double utilizationRate = utilizedAmount / committedAmount;
        
        if (utilizationRate < CLOUD_COMMITMENT_THRESHOLD) {
            String message = String.format("Low commitment utilization for %s: %.2f%% of $%.2f", 
                commitmentType, utilizationRate * 100, committedAmount);
            
            alertService.createAlert("LOW_COMMITMENT_UTILIZATION", "WARNING", message,
                Map.of("category", category, "commitmentType", commitmentType, 
                       "utilization", utilizationRate, "remaining", remainingCommitment));
            
            optimizeCommitmentUsage(category, commitmentType, remainingCommitment);
        }
        
        checkCommitmentExpiry(category, commitmentType, expiryDate);
        
        updateCostState(category, state -> {
            state.updateCommitmentUtilization(commitmentType, utilizationRate);
        });
        
        metricsService.recordCommitmentUtilization(category, commitmentType, utilizationRate);
    }
    
    private void updateCostState(String category, java.util.function.Consumer<CostMonitoringState> updater) {
        costStates.computeIfAbsent(category, k -> new CostMonitoringState(category))
                  .update(updater);
    }
    
    private void checkCostSpike(String provider, String service, double dailyCost, double averageCost) {
        if (dailyCost > averageCost * COST_SPIKE_THRESHOLD) {
            String message = String.format("Cost spike detected for %s/%s: $%.2f (avg: $%.2f)", 
                provider, service, dailyCost, averageCost);
            
            alertService.createAlert("COST_SPIKE", "HIGH", message,
                Map.of("provider", provider, "service", service, 
                       "dailyCost", dailyCost, "averageCost", averageCost));
            
            investigateCostSpike(provider, service, dailyCost);
        }
    }
    
    private void allocateCostToEnvironment(String provider, String environment, double cost) {
        allocationEngines.get(provider).allocateToEnvironment(environment, cost);
    }
    
    private void suggestResourceOptimization(String category, String resourceId, 
                                            String resourceType, double utilization) {
        Map<String, Object> optimization = optimizationService.analyzeResource(
            resourceId, resourceType, utilization);
        
        if (optimization != null && optimization.containsKey("savings")) {
            double savings = (double) optimization.get("savings");
            if (savings > SAVINGS_OPPORTUNITY_THRESHOLD) {
                optimizationService.createOptimizationPlan(category, resourceId, optimization);
            }
        }
    }
    
    private void handleIdleResource(String category, String resourceId, 
                                   String resourceType, double hourlyCost) {
        utilizationService.handleIdleResource(category, resourceId, resourceType, hourlyCost);
    }
    
    private void implementCostControls(String category, String budgetName, 
                                      double actualSpend, double budgetAmount) {
        budgetService.implementControls(category, budgetName, actualSpend, budgetAmount);
    }
    
    private void notifyBudgetWarning(String category, String budgetName, 
                                    double actualSpend, double budgetAmount) {
        notificationService.notifyBudgetWarning(category, budgetName, actualSpend, budgetAmount);
    }
    
    private void handleForecastedOverrun(String category, String budgetName, 
                                        double forecastedSpend, double budgetAmount) {
        forecastingService.handleOverrun(category, budgetName, forecastedSpend, budgetAmount);
    }
    
    private void investigateUnexpectedCharge(String category, double amount, 
                                            String description, JsonNode charges) {
        billingAnalyzers.get(category).investigateCharge(amount, description, charges);
    }
    
    private void handleBillingAnomaly(String category, String alertType, 
                                     double amount, double anomalyScore) {
        CostAnomaly anomaly = new CostAnomaly(UUID.randomUUID().toString(), 
            category, alertType, "billing", amount, anomalyScore);
        activeAnomalies.put(anomaly.getId(), anomaly);
        
        optimizationService.investigateAnomaly(anomaly);
    }
    
    private void investigateAnomaly(String category, String service, double actualCost, 
                                   double normalCost, JsonNode rootCause) {
        optimizationService.analyzeAnomaly(category, service, actualCost, normalCost, rootCause);
    }
    
    private void optimizeReservedInstances(String category, String instanceType, 
                                          int totalInstances, int usedInstances) {
        optimizationService.optimizeReservedInstances(category, instanceType, 
                                                     totalInstances, usedInstances);
    }
    
    private void trackReservedInstanceSavings(String category, String instanceType, double savings) {
        savingsIdentifiers.get(category).trackRISavings(instanceType, savings);
    }
    
    private void notifySavingsAchieved(String category, String planType, double savingsAmount) {
        notificationService.notifySavings(category, planType, savingsAmount);
    }
    
    private void optimizeTransactionCost(String category, String transactionType, JsonNode breakdown) {
        optimizationService.optimizeTransactionCost(category, transactionType, breakdown);
    }
    
    private void projectTransactionCosts(TransactionCostCalculator calculator, 
                                        String transactionType, int transactionCount) {
        double projection = calculator.projectMonthlyCost(transactionType, transactionCount);
        forecastingService.updateTransactionProjection(transactionType, projection);
    }
    
    private void renegotiateContract(String category, String vendorName, double priceChange) {
        vendorTrackers.get(category).initiateRenegotiation(vendorName, priceChange);
    }
    
    private void validateInvoice(VendorCostTracker tracker, String vendorName, 
                                String invoiceNumber, JsonNode lineItems) {
        boolean valid = tracker.validateInvoice(vendorName, invoiceNumber, lineItems);
        if (!valid) {
            alertService.createAlert("INVOICE_VALIDATION_FAILED", "WARNING",
                String.format("Invoice validation failed for %s: %s", vendorName, invoiceNumber),
                Map.of("vendorName", vendorName, "invoiceNumber", invoiceNumber));
        }
    }
    
    private void optimizeLicenseAllocation(String category, String licenseName, 
                                          int totalLicenses, int usedLicenses) {
        licenseManagers.get(category).optimizeAllocation(licenseName, totalLicenses, usedLicenses);
    }
    
    private void checkLicenseRenewal(LicenseManager manager, String licenseName, long renewalDate) {
        long daysUntilRenewal = Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(renewalDate)
        ).toDays();
        
        if (daysUntilRenewal <= 30) {
            manager.prepareRenewal(licenseName, renewalDate);
        }
    }
    
    private void improveAllocationMethod(String category, String costCenter, String method) {
        allocationEngines.get(category).improveMethod(costCenter, method);
    }
    
    private void validateCostAllocation(CostAllocationEngine engine, String costCenter, 
                                       String department, double amount) {
        engine.validateAllocation(costCenter, department, amount);
    }
    
    private void implementOptimization(String category, String optimizationType, 
                                      String resourceId, JsonNode recommendations) {
        optimizationService.implement(category, optimizationType, resourceId, recommendations);
    }
    
    private void developCostReductionPlan(String category, double overrun, JsonNode assumptions) {
        optimizationService.createReductionPlan(category, overrun, assumptions);
    }
    
    private void recommendResourceTermination(String category, String resourceId, 
                                             String resourceType, double monthlyCost) {
        utilizationService.recommendTermination(category, resourceId, resourceType, monthlyCost);
    }
    
    private void scheduleResourceReview(String category, String resourceId, String resourceType) {
        utilizationService.scheduleReview(category, resourceId, resourceType);
    }
    
    private void optimizeCommitmentUsage(String category, String commitmentType, double remaining) {
        optimizationService.optimizeCommitment(category, commitmentType, remaining);
    }
    
    private void checkCommitmentExpiry(String category, String commitmentType, long expiryDate) {
        long daysUntilExpiry = Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(expiryDate)
        ).toDays();
        
        if (daysUntilExpiry <= 30) {
            notificationService.notifyCommitmentExpiry(category, commitmentType, daysUntilExpiry);
        }
    }
    
    private void investigateCostSpike(String provider, String service, double dailyCost) {
        cloudTrackers.get(provider).investigateSpike(service, dailyCost);
    }
    
    @Scheduled(fixedDelay = 300000)
    private void analyzeCostTrends() {
        try {
            costStates.forEach((category, state) -> {
                Map<String, Double> trends = state.analyzeTrends();
                
                trends.forEach((metric, trend) -> {
                    if (Math.abs(trend) > 0.20) {
                        String direction = trend > 0 ? "increase" : "decrease";
                        notificationService.notifyCostTrend(category, metric, trend, direction);
                    }
                });
                
                generateTrendReport(category, trends);
            });
        } catch (Exception e) {
            log.error("Error analyzing cost trends: {}", e.getMessage(), e);
        }
    }
    
    private void generateTrendReport(String category, Map<String, Double> trends) {
        Map<String, Object> report = new HashMap<>();
        report.put("category", category);
        report.put("trends", trends);
        report.put("timestamp", Instant.now().toEpochMilli());
        
        metricsService.recordCostTrends(report);
    }
    
    @Scheduled(fixedDelay = 600000)
    private void identifySavingsOpportunities() {
        try {
            savingsIdentifiers.forEach((category, identifier) -> {
                List<Map<String, Object>> opportunities = identifier.findOpportunities();
                
                double totalSavings = opportunities.stream()
                    .mapToDouble(opp -> (double) opp.get("savings"))
                    .sum();
                
                if (totalSavings > SAVINGS_OPPORTUNITY_THRESHOLD) {
                    optimizationService.prioritizeOpportunities(category, opportunities);
                }
            });
        } catch (Exception e) {
            log.error("Error identifying savings opportunities: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    private void forecastCosts() {
        try {
            forecastingModels.forEach((category, model) -> {
                Map<String, Double> forecast = model.generateForecast();
                forecastingService.processForecast(category, forecast);
                
                double nextMonthForecast = forecast.getOrDefault("nextMonth", 0.0);
                double budget = budgetMonitors.get(category).getMonthlyBudget();
                
                if (nextMonthForecast > budget) {
                    alertService.createAlert("FORECAST_EXCEEDS_BUDGET", "WARNING",
                        String.format("Forecasted cost for %s exceeds budget: $%.2f > $%.2f", 
                            category, nextMonthForecast, budget),
                        Map.of("category", category, "forecast", nextMonthForecast, "budget", budget));
                }
            });
        } catch (Exception e) {
            log.error("Error forecasting costs: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 86400000)
    private void generateCostReports() {
        try {
            Map<String, Object> dailyReport = new HashMap<>();
            
            costStates.forEach((category, state) -> {
                Map<String, Object> categoryReport = new HashMap<>();
                categoryReport.put("totalCost", state.getTotalCost());
                categoryReport.put("budgetUtilization", state.getBudgetUtilization());
                categoryReport.put("savingsIdentified", state.getSavingsIdentified());
                categoryReport.put("anomaliesDetected", state.getAnomalyCount());
                
                dailyReport.put(category, categoryReport);
            });
            
            dailyReport.put("totalCost", calculateTotalCost(costStates));
            dailyReport.put("totalSavings", calculateTotalSavings(savingsIdentifiers));
            dailyReport.put("activeAnomalies", activeAnomalies.size());
            
            notificationService.sendDailyCostReport(dailyReport);
            
        } catch (Exception e) {
            log.error("Error generating cost reports: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            int deleted = metricsRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old cost metrics records", deleted);
            
            activeAnomalies.entrySet().removeIf(entry -> 
                entry.getValue().isOlderThan(cutoff));
            
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process cost monitoring event after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            Map<String, Object> errorContext = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "error", error.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
            );
            
            notificationService.notifyError("COST_MONITORING_PROCESSING_ERROR", errorContext);
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
        log.error("Fallback triggered for cost monitoring event processing", ex);
        errorCounter.increment();
    }
    
    private double calculateTotalCost(Map<String, CostMonitoringState> states) {
        return states.values().stream()
            .mapToDouble(CostMonitoringState::getTotalCost)
            .sum();
    }
    
    private double calculateBudgetUtilization(Map<String, BudgetMonitor> monitors) {
        return monitors.values().stream()
            .mapToDouble(BudgetMonitor::getUtilization)
            .average()
            .orElse(0.0);
    }
    
    private double calculateTotalSavings(Map<String, SavingsIdentifier> identifiers) {
        return identifiers.values().stream()
            .mapToDouble(SavingsIdentifier::getTotalSavings)
            .sum();
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down CostMonitoringConsumer...");
            scheduledExecutor.shutdown();
            analysisExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
            
            log.info("CostMonitoringConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}