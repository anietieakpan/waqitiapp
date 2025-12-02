package com.waqiti.merchant.service;

import com.waqiti.merchant.domain.*;
import com.waqiti.merchant.repository.MerchantRepository;
import com.waqiti.merchant.repository.StoreRepository;
import com.waqiti.merchant.repository.MerchantAnalyticsRepository;
import com.waqiti.merchant.dto.*;
import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced merchant management service providing comprehensive multi-store management,
 * analytics, onboarding, and merchant operations.
 *
 * Features:
 * - Multi-store merchant management
 * - Advanced merchant analytics and reporting
 * - Automated onboarding and KYB verification
 * - Store performance optimization
 * - Merchant risk assessment and monitoring
 * - Dynamic fee management
 * - Inventory and catalog management
 * - Sales forecasting and insights
 * - Compliance and regulatory reporting
 * - API rate limiting and access control
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedMerchantManagementService {

    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final MerchantAnalyticsRepository analyticsRepository;
    private final KYBVerificationService kybVerificationService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final InventoryService inventoryService;
    private final MetricsCollector metricsCollector;
    
    // Configuration constants
    private static final BigDecimal DEFAULT_PROCESSING_FEE = new BigDecimal("0.029"); // 2.9%
    private static final BigDecimal PREMIUM_PROCESSING_FEE = new BigDecimal("0.024"); // 2.4%
    private static final int MAX_STORES_PER_MERCHANT = 50;
    private static final long SETTLEMENT_DELAY_HOURS = 24;

    /**
     * Creates a new merchant account with comprehensive onboarding.
     *
     * @param request merchant creation request
     * @return created merchant
     */
    @Transactional
    public Merchant createMerchant(CreateMerchantRequest request) {
        log.info("Creating merchant account for business: {}", request.getBusinessName());
        
        // Validate business information
        validateBusinessInformation(request);
        
        // Perform initial risk assessment
        RiskAssessment riskAssessment = assessMerchantRisk(request);
        
        Merchant merchant = Merchant.builder()
                .businessName(request.getBusinessName())
                .businessType(request.getBusinessType())
                .businessRegistrationNumber(request.getBusinessRegistrationNumber())
                .taxIdentifier(request.getTaxIdentifier())
                .website(request.getWebsite())
                .industry(request.getIndustry())
                .businessAddress(request.getBusinessAddress())
                .contactDetails(request.getContactDetails())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .tier(MerchantTier.STANDARD)
                .riskLevel(riskAssessment.getLevel())
                .riskScore(riskAssessment.getScore())
                .onboardingStage(OnboardingStage.BASIC_INFO_COLLECTED)
                .processingSettings(createDefaultProcessingSettings())
                .complianceSettings(createDefaultComplianceSettings())
                .apiSettings(createDefaultApiSettings())
                .createdAt(Instant.now())
                .build();
        
        Merchant saved = merchantRepository.save(merchant);
        
        // Initialize default store
        if (request.isCreateDefaultStore()) {
            createDefaultStore(saved, request);
        }
        
        // Start KYB verification process
        initiateKYBVerification(saved);
        
        // Create merchant analytics profile
        createMerchantAnalyticsProfile(saved);
        
        metricsCollector.incrementCounter("merchant.accounts.created");
        log.info("Created merchant account: {} for business: {}", saved.getId(), saved.getBusinessName());
        
        return saved;
    }
    
    /**
     * Creates a new store for a merchant.
     *
     * @param merchantId merchant ID
     * @param request store creation request
     * @return created store
     */
    @Transactional
    public Store createStore(String merchantId, CreateStoreRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        validateStoreCreation(merchant, request);
        
        log.info("Creating store: {} for merchant: {}", request.getName(), merchantId);
        
        Store store = Store.builder()
                .merchant(merchant)
                .name(request.getName())
                .description(request.getDescription())
                .storeType(request.getStoreType())
                .address(request.getAddress())
                .contactInfo(request.getContactInfo())
                .operatingHours(request.getOperatingHours())
                .timezone(request.getTimezone())
                .currency(request.getCurrency())
                .status(StoreStatus.ACTIVE)
                .settings(createDefaultStoreSettings(request))
                .branding(request.getBranding())
                .paymentMethods(request.getSupportedPaymentMethods())
                .categories(request.getCategories())
                .createdAt(Instant.now())
                .build();
        
        Store saved = storeRepository.save(store);
        
        // Initialize store inventory if provided
        if (request.getInitialInventory() != null && !request.getInitialInventory().isEmpty()) {
            inventoryService.initializeStoreInventory(saved.getId(), request.getInitialInventory());
        }
        
        // Setup store analytics
        setupStoreAnalytics(saved);
        
        // Update merchant statistics
        updateMerchantStatistics(merchant);
        
        metricsCollector.incrementCounter("merchant.stores.created");
        log.info("Created store: {} with ID: {}", saved.getName(), saved.getId());
        
        return saved;
    }
    
    /**
     * Updates merchant verification status and processes KYB results.
     *
     * @param merchantId merchant ID
     * @param kybResult KYB verification result
     * @return updated merchant
     */
    @Transactional
    public Merchant updateMerchantVerification(String merchantId, KYBVerificationResult kybResult) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        log.info("Processing KYB verification result for merchant: {} - Status: {}", 
                merchantId, kybResult.getStatus());
        
        merchant.setKybStatus(kybResult.getStatus());
        merchant.setKybCompletedAt(Instant.now());
        merchant.setKybProvider(kybResult.getProvider());
        merchant.setKybReferenceId(kybResult.getReferenceId());
        
        if (kybResult.getStatus() == KYBStatus.APPROVED) {
            merchant.setStatus(MerchantStatus.ACTIVE);
            merchant.setOnboardingStage(OnboardingStage.COMPLETED);
            
            // Activate all pending stores
            activatePendingStores(merchant);
            
            // Setup payment processing
            setupPaymentProcessing(merchant);
            
            // Send welcome notification
            notificationService.sendMerchantWelcomeNotification(merchant);
            
        } else if (kybResult.getStatus() == KYBStatus.REJECTED) {
            merchant.setStatus(MerchantStatus.SUSPENDED);
            merchant.setRejectionReason(kybResult.getRejectionReason());
            
            // Notify of rejection and next steps
            notificationService.sendKYBRejectionNotification(merchant, kybResult);
        }
        
        Merchant updated = merchantRepository.save(merchant);
        
        metricsCollector.incrementCounter("merchant.kyb.processed");
        
        return updated;
    }
    
    /**
     * Gets comprehensive merchant analytics and insights.
     *
     * @param merchantId merchant ID
     * @param period analysis period
     * @return merchant analytics
     */
    public MerchantAnalytics getMerchantAnalytics(String merchantId, AnalyticsPeriod period) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        Instant endDate = Instant.now();
        Instant startDate = calculateStartDate(endDate, period);
        
        // Get transaction metrics
        TransactionMetrics transactionMetrics = analyticsRepository
                .getTransactionMetrics(merchantId, startDate, endDate);
        
        // Get revenue metrics
        RevenueMetrics revenueMetrics = analyticsRepository
                .getRevenueMetrics(merchantId, startDate, endDate);
        
        // Get store performance
        List<StorePerformance> storePerformances = analyticsRepository
                .getStorePerformances(merchantId, startDate, endDate);
        
        // Get customer metrics
        CustomerMetrics customerMetrics = analyticsRepository
                .getCustomerMetrics(merchantId, startDate, endDate);
        
        // Get payment method analysis
        List<PaymentMethodStats> paymentMethodStats = analyticsRepository
                .getPaymentMethodStats(merchantId, startDate, endDate);
        
        // Calculate growth trends
        GrowthTrends growthTrends = calculateGrowthTrends(merchantId, startDate, endDate);
        
        return MerchantAnalytics.builder()
                .merchantId(merchantId)
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .transactionMetrics(transactionMetrics)
                .revenueMetrics(revenueMetrics)
                .storePerformances(storePerformances)
                .customerMetrics(customerMetrics)
                .paymentMethodStats(paymentMethodStats)
                .growthTrends(growthTrends)
                .generatedAt(Instant.now())
                .build();
    }
    
    /**
     * Optimizes merchant processing fees based on performance.
     *
     * @param merchantId merchant ID
     * @return fee optimization result
     */
    @Transactional
    public FeeOptimizationResult optimizeMerchantFees(String merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        log.info("Optimizing processing fees for merchant: {}", merchantId);
        
        // Get merchant performance metrics
        MerchantPerformanceMetrics performance = calculatePerformanceMetrics(merchant);
        
        // Determine optimal fee structure
        FeeStructure currentFees = merchant.getProcessingSettings().getFeeStructure();
        FeeStructure optimizedFees = calculateOptimizedFees(performance, currentFees);
        
        FeeOptimizationResult result = FeeOptimizationResult.builder()
                .merchantId(merchantId)
                .currentFees(currentFees)
                .optimizedFees(optimizedFees)
                .estimatedSavings(calculateEstimatedSavings(performance, currentFees, optimizedFees))
                .effectiveDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .reason(determineFeeOptimizationReason(performance))
                .build();
        
        // Apply optimized fees if beneficial
        if (result.getEstimatedSavings().compareTo(BigDecimal.ZERO) > 0) {
            merchant.getProcessingSettings().setFeeStructure(optimizedFees);
            merchant.getProcessingSettings().setLastFeeOptimization(Instant.now());
            merchantRepository.save(merchant);
            
            // Notify merchant of fee optimization
            notificationService.sendFeeOptimizationNotification(merchant, result);
        }
        
        metricsCollector.incrementCounter("merchant.fees.optimized");
        
        return result;
    }
    
    /**
     * Generates sales forecast for merchant stores.
     *
     * @param merchantId merchant ID
     * @param forecastPeriod forecast period
     * @return sales forecast
     */
    public SalesForecast generateSalesForecast(String merchantId, ForecastPeriod forecastPeriod) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        log.info("Generating sales forecast for merchant: {} for period: {}", merchantId, forecastPeriod);
        
        // Get historical sales data
        List<HistoricalSalesData> historicalData = analyticsRepository
                .getHistoricalSalesData(merchantId, calculateHistoricalPeriod(forecastPeriod));
        
        // Apply forecasting algorithms
        SalesForecast forecast = applySalesForecastingAlgorithms(historicalData, forecastPeriod);
        
        // Get store-specific forecasts
        List<Store> stores = storeRepository.findByMerchantId(merchantId);
        Map<String, StoreForecast> storeForecasts = new HashMap<>();
        
        for (Store store : stores) {
            StoreForecast storeForecast = generateStoreForecast(store, forecastPeriod, historicalData);
            storeForecasts.put(store.getId(), storeForecast);
        }
        
        forecast.setStoreForecasts(storeForecasts);
        forecast.setGeneratedAt(Instant.now());
        
        metricsCollector.incrementCounter("merchant.forecasts.generated");
        
        return forecast;
    }
    
    /**
     * Manages store inventory across all merchant locations.
     *
     * @param merchantId merchant ID
     * @param request inventory management request
     * @return inventory management result
     */
    @Transactional
    public InventoryManagementResult manageInventory(String merchantId, InventoryManagementRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        log.info("Managing inventory for merchant: {} - Operation: {}", merchantId, request.getOperation());
        
        List<InventoryOperation> operations = new ArrayList<>();
        
        switch (request.getOperation()) {
            case BULK_UPDATE:
                operations.addAll(processBulkInventoryUpdate(request));
                break;
            case TRANSFER_BETWEEN_STORES:
                operations.addAll(processInventoryTransfer(request));
                break;
            case LOW_STOCK_REORDER:
                operations.addAll(processLowStockReorders(merchantId));
                break;
            case SYNC_ACROSS_STORES:
                operations.addAll(syncInventoryAcrossStores(merchantId));
                break;
        }
        
        // Execute operations
        for (InventoryOperation operation : operations) {
            inventoryService.executeInventoryOperation(operation);
        }
        
        InventoryManagementResult result = InventoryManagementResult.builder()
                .merchantId(merchantId)
                .operation(request.getOperation())
                .operationsExecuted(operations.size())
                .affectedItems(operations.stream().mapToInt(op -> op.getItems().size()).sum())
                .executedAt(Instant.now())
                .success(true)
                .build();
        
        metricsCollector.incrementCounter("merchant.inventory.managed");
        
        return result;
    }
    
    /**
     * Gets comprehensive merchant dashboard data.
     *
     * @param merchantId merchant ID
     * @return dashboard data
     */
    public MerchantDashboard getMerchantDashboard(String merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        
        // Get real-time metrics
        DashboardMetrics metrics = analyticsRepository.getDashboardMetrics(merchantId);
        
        // Get recent transactions
        List<RecentTransaction> recentTransactions = analyticsRepository
                .getRecentTransactions(merchantId, 10);
        
        // Get store summaries
        List<StoreSummary> storeSummaries = storeRepository.findByMerchantId(merchantId)
                .stream()
                .map(this::createStoreSummary)
                .collect(Collectors.toList());
        
        // Get alerts and notifications
        List<MerchantAlert> alerts = getMerchantAlerts(merchantId);
        
        // Get performance trends
        PerformanceTrends trends = calculatePerformanceTrends(merchantId);
        
        return MerchantDashboard.builder()
                .merchantId(merchantId)
                .merchantName(merchant.getBusinessName())
                .metrics(metrics)
                .recentTransactions(recentTransactions)
                .storeSummaries(storeSummaries)
                .alerts(alerts)
                .trends(trends)
                .lastUpdated(Instant.now())
                .build();
    }
    
    /**
     * Processes merchant settlements and payouts.
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void processMerchantSettlements() {
        log.info("Processing merchant settlements");
        
        List<Merchant> eligibleMerchants = merchantRepository.findEligibleForSettlement();
        
        for (Merchant merchant : eligibleMerchants) {
            try {
                processSettlement(merchant);
            } catch (Exception e) {
                log.error("Error processing settlement for merchant: {}", merchant.getId(), e);
                metricsCollector.incrementCounter("merchant.settlement.errors");
            }
        }
    }
    
    /**
     * Monitors merchant risk and compliance.
     */
    @Scheduled(fixedDelay = 21600000) // Every 6 hours
    public void monitorMerchantRisk() {
        log.info("Monitoring merchant risk and compliance");
        
        List<Merchant> activeMerchants = merchantRepository.findByStatus(MerchantStatus.ACTIVE);
        
        for (Merchant merchant : activeMerchants) {
            try {
                assessAndUpdateMerchantRisk(merchant);
            } catch (Exception e) {
                log.error("Error assessing risk for merchant: {}", merchant.getId(), e);
            }
        }
    }
    
    // Private helper methods
    
    private void validateBusinessInformation(CreateMerchantRequest request) {
        if (request.getBusinessName() == null || request.getBusinessName().trim().isEmpty()) {
            throw new IllegalArgumentException("Business name is required");
        }
        
        if (request.getBusinessType() == null) {
            throw new IllegalArgumentException("Business type is required");
        }
        
        if (request.getIndustry() == null) {
            throw new IllegalArgumentException("Industry is required");
        }
    }
    
    private RiskAssessment assessMerchantRisk(CreateMerchantRequest request) {
        return riskAssessmentService.assessMerchantRisk(
            request.getIndustry(),
            request.getBusinessType(),
            request.getEstimatedMonthlyVolume(),
            request.getBusinessAddress().getCountry()
        );
    }
    
    private ProcessingSettings createDefaultProcessingSettings() {
        return ProcessingSettings.builder()
                .feeStructure(createStandardFeeStructure())
                .settlementSchedule(SettlementSchedule.DAILY)
                .holdReserve(new BigDecimal("0.10"))
                .transactionLimits(createDefaultTransactionLimits())
                .build();
    }
    
    private ComplianceSettings createDefaultComplianceSettings() {
        return ComplianceSettings.builder()
                .amlEnabled(true)
                .fraudMonitoring(true)
                .transactionMonitoring(true)
                .sanctionsScreening(true)
                .riskThresholds(createDefaultRiskThresholds())
                .build();
    }
    
    private ApiSettings createDefaultApiSettings() {
        return ApiSettings.builder()
                .rateLimits(createDefaultRateLimits())
                .webhooksEnabled(true)
                .apiVersion("v1")
                .build();
    }
    
    private void initiateKYBVerification(Merchant merchant) {
        CompletableFuture.runAsync(() -> {
            try {
                kybVerificationService.initiateVerification(merchant);
            } catch (Exception e) {
                log.error("Error initiating KYB verification for merchant: {}", merchant.getId(), e);
            }
        });
    }
    
    private void createMerchantAnalyticsProfile(Merchant merchant) {
        MerchantAnalyticsProfile profile = MerchantAnalyticsProfile.builder()
                .merchantId(merchant.getId())
                .industry(merchant.getIndustry())
                .tier(merchant.getTier())
                .createdAt(Instant.now())
                .build();
        
        analyticsRepository.createProfile(profile);
    }
    
    private Store createDefaultStore(Merchant merchant, CreateMerchantRequest request) {
        CreateStoreRequest storeRequest = CreateStoreRequest.builder()
                .name(request.getBusinessName() + " - Main Store")
                .storeType(StoreType.PHYSICAL)
                .address(request.getBusinessAddress())
                .timezone(request.getTimezone())
                .currency(request.getDefaultCurrency())
                .build();
        
        return createStore(merchant.getId(), storeRequest);
    }
    
    private MerchantPerformanceMetrics calculatePerformanceMetrics(Merchant merchant) {
        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(30, ChronoUnit.DAYS);
        
        return analyticsRepository.getPerformanceMetrics(merchant.getId(), startDate, endDate);
    }
    
    private void processSettlement(Merchant merchant) {
        log.info("Processing settlement for merchant: {}", merchant.getId());
        
        // Calculate settlement amount
        SettlementCalculation calculation = calculateSettlementAmount(merchant);
        
        if (calculation.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Process payout
            PayoutResult result = paymentService.processMerchantPayout(
                merchant.getId(),
                calculation.getAmount(),
                merchant.getProcessingSettings().getSettlementAccount()
            );
            
            if (result.isSuccess()) {
                // Record settlement
                recordSuccessfulSettlement(merchant, calculation, result);
                metricsCollector.incrementCounter("merchant.settlements.successful");
            } else {
                log.error("Settlement failed for merchant: {} - {}", merchant.getId(), result.getErrorMessage());
                metricsCollector.incrementCounter("merchant.settlements.failed");
            }
        }
    }
    
    private void assessAndUpdateMerchantRisk(Merchant merchant) {
        RiskAssessment newAssessment = riskAssessmentService.assessMerchant(merchant);
        
        if (hasSignificantRiskChange(merchant.getRiskScore(), newAssessment.getScore())) {
            merchant.setRiskScore(newAssessment.getScore());
            merchant.setRiskLevel(newAssessment.getLevel());
            merchant.setLastRiskAssessment(Instant.now());
            
            merchantRepository.save(merchant);
            
            // Notify if risk level increased significantly
            if (newAssessment.getLevel().ordinal() > merchant.getRiskLevel().ordinal()) {
                notificationService.sendRiskLevelChangeNotification(merchant, newAssessment);
            }
        }
    }
    
    private boolean hasSignificantRiskChange(Double currentScore, Double newScore) {
        if (currentScore == null) return true;
        return Math.abs(currentScore - newScore) > 10.0;
    }
}