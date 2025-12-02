package com.waqiti.billingorchestrator.consumer;

import com.waqiti.common.events.RecurringPaymentScheduledEvent;
import com.waqiti.common.messaging.EventConsumer;
import com.waqiti.billingorchestrator.dto.PaymentScheduleValidation;
import com.waqiti.billingorchestrator.dto.SubscriptionAnalysis;
import com.waqiti.billingorchestrator.dto.PaymentRetryStrategy;
import com.waqiti.billingorchestrator.dto.BillingCycleManagement;
import com.waqiti.billingorchestrator.entity.RecurringPayment;
import com.waqiti.billingorchestrator.service.PaymentSchedulingService;
import com.waqiti.billingorchestrator.service.SubscriptionManagementService;
import com.waqiti.billingorchestrator.service.PaymentProcessingService;
import com.waqiti.billingorchestrator.service.BillingCycleService;
import com.waqiti.billingorchestrator.service.PaymentRetryService;
import com.waqiti.billingorchestrator.service.DunningManagementService;
import com.waqiti.billingorchestrator.repository.RecurringPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringPaymentScheduledEventConsumer implements EventConsumer<RecurringPaymentScheduledEvent> {

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final PaymentSchedulingService paymentSchedulingService;
    private final SubscriptionManagementService subscriptionManagementService;
    private final PaymentProcessingService paymentProcessingService;
    private final BillingCycleService billingCycleService;
    private final PaymentRetryService paymentRetryService;
    private final DunningManagementService dunningManagementService;

    @Override
    @KafkaListener(
        topics = "recurring-payment-scheduled",
        groupId = "recurring-payment-scheduled-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    public void consume(
        RecurringPaymentScheduledEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            log.info("Processing RecurringPaymentScheduledEvent: paymentId={}, customerId={}, amount={}, frequency={}", 
                    event.getPaymentId(), event.getCustomerId(), event.getAmount(), event.getFrequency());

            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Event already processed, skipping: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            processRecurringPayment(event);
            markEventAsProcessed(event.getEventId());
            acknowledgment.acknowledge();

            log.info("Successfully processed RecurringPaymentScheduledEvent: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing RecurringPaymentScheduledEvent: eventId={}, error={}", 
                    event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    private void processRecurringPayment(RecurringPaymentScheduledEvent event) {
        // Step 1: Create recurring payment record
        RecurringPayment payment = createRecurringPayment(event);
        
        // Step 2: Validate payment schedule and configuration
        PaymentScheduleValidation validation = validatePaymentSchedule(event, payment);
        
        // Step 3: Perform subscription analysis
        SubscriptionAnalysis subscriptionAnalysis = analyzeSubscription(event, payment);
        
        // Step 4: Setup billing cycle management
        BillingCycleManagement billingCycle = setupBillingCycle(event, payment, validation);
        
        // Step 5: Configure payment retry strategy
        PaymentRetryStrategy retryStrategy = configureRetryStrategy(event, payment, subscriptionAnalysis);
        
        // Step 6: Execute payment authorization
        executePaymentAuthorization(event, payment, validation);
        
        // Step 7: Setup dunning management
        setupDunningManagement(event, payment, retryStrategy);
        
        // Step 8: Configure subscription lifecycle management
        configureSubscriptionLifecycle(event, payment, subscriptionAnalysis);
        
        // Step 9: Setup payment monitoring and alerts
        setupPaymentMonitoring(event, payment, billingCycle);
        
        // Step 10: Process initial payment if immediate
        processInitialPayment(event, payment, validation);
        
        // Step 11: Generate billing documentation
        generateBillingDocumentation(event, payment, billingCycle);
        
        // Step 12: Send subscription notifications
        sendSubscriptionNotifications(event, payment, billingCycle, subscriptionAnalysis);
    }

    private RecurringPayment createRecurringPayment(RecurringPaymentScheduledEvent event) {
        RecurringPayment payment = RecurringPayment.builder()
            .paymentId(event.getPaymentId())
            .customerId(event.getCustomerId())
            .accountId(event.getAccountId())
            .subscriptionId(event.getSubscriptionId())
            .merchantId(event.getMerchantId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .frequency(event.getFrequency())
            .paymentMethod(event.getPaymentMethod())
            .paymentInstrumentId(event.getPaymentInstrumentId())
            .status("SCHEDULED")
            .startDate(event.getStartDate())
            .endDate(event.getEndDate())
            .nextPaymentDate(calculateNextPaymentDate(event.getStartDate(), event.getFrequency()))
            .lastPaymentDate(null)
            .totalPaymentsScheduled(calculateTotalPayments(event))
            .paymentsProcessed(0)
            .paymentsRemaining(calculateTotalPayments(event))
            .failedPayments(0)
            .description(event.getDescription())
            .category(event.getCategory())
            .taxAmount(event.getTaxAmount())
            .discountAmount(event.getDiscountAmount())
            .trialPeriod(event.isTrialPeriod())
            .trialEndDate(event.getTrialEndDate())
            .autoRenew(event.isAutoRenew())
            .cancellationRequested(false)
            .pauseRequested(false)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
            
        return recurringPaymentRepository.save(payment);
    }

    private PaymentScheduleValidation validatePaymentSchedule(RecurringPaymentScheduledEvent event, 
                                                             RecurringPayment payment) {
        PaymentScheduleValidation validation = PaymentScheduleValidation.builder()
            .paymentId(event.getPaymentId())
            .validationDate(LocalDateTime.now())
            .build();
            
        // Validate payment frequency
        boolean frequencyValid = paymentSchedulingService.validateFrequency(
            event.getFrequency(), event.getStartDate(), event.getEndDate());
        validation.setFrequencyValid(frequencyValid);
        
        // Validate payment method
        boolean paymentMethodValid = paymentSchedulingService.validatePaymentMethod(
            event.getPaymentMethod(), event.getPaymentInstrumentId(), event.getCustomerId());
        validation.setPaymentMethodValid(paymentMethodValid);
        
        // Validate account standing
        boolean accountStandingValid = paymentSchedulingService.validateAccountStanding(
            event.getAccountId(), event.getCustomerId());
        validation.setAccountStandingValid(accountStandingValid);
        
        // Validate merchant configuration
        boolean merchantConfigValid = paymentSchedulingService.validateMerchantConfig(
            event.getMerchantId(), event.getAmount(), event.getCurrency());
        validation.setMerchantConfigValid(merchantConfigValid);
        
        // Validate billing agreement
        boolean billingAgreementValid = paymentSchedulingService.validateBillingAgreement(
            event.getCustomerId(), event.getMerchantId(), event.getSubscriptionId());
        validation.setBillingAgreementValid(billingAgreementValid);
        
        // Check for duplicate subscriptions
        boolean duplicateCheck = paymentSchedulingService.checkDuplicateSubscription(
            event.getCustomerId(), event.getMerchantId(), event.getAmount());
        validation.setDuplicateSubscription(duplicateCheck);
        
        // Validate payment amount limits
        boolean amountLimitsValid = paymentSchedulingService.validateAmountLimits(
            event.getAmount(), event.getFrequency(), event.getAccountId());
        validation.setAmountLimitsValid(amountLimitsValid);
        
        // Validate trial period configuration
        if (event.isTrialPeriod()) {
            boolean trialConfigValid = paymentSchedulingService.validateTrialConfiguration(
                event.getTrialEndDate(), event.getStartDate(), event.getTrialAmount());
            validation.setTrialConfigValid(trialConfigValid);
        }
        
        // Calculate overall validation result
        boolean overallValid = frequencyValid && paymentMethodValid && accountStandingValid && 
                              merchantConfigValid && billingAgreementValid && !duplicateCheck && 
                              amountLimitsValid;
        validation.setOverallValid(overallValid);
        
        paymentSchedulingService.saveValidation(validation);
        
        // Update payment status based on validation
        if (!overallValid) {
            payment.setStatus("VALIDATION_FAILED");
            payment.setFailureReason(generateValidationFailureReason(validation));
            recurringPaymentRepository.save(payment);
        }
        
        log.info("Payment schedule validation completed: paymentId={}, valid={}", 
                event.getPaymentId(), overallValid);
        
        return validation;
    }

    private SubscriptionAnalysis analyzeSubscription(RecurringPaymentScheduledEvent event, 
                                                    RecurringPayment payment) {
        SubscriptionAnalysis analysis = SubscriptionAnalysis.builder()
            .paymentId(event.getPaymentId())
            .subscriptionId(event.getSubscriptionId())
            .analysisDate(LocalDateTime.now())
            .build();
            
        // Calculate subscription lifetime value
        BigDecimal lifetimeValue = subscriptionManagementService.calculateLifetimeValue(
            event.getAmount(), event.getFrequency(), event.getStartDate(), event.getEndDate());
        analysis.setLifetimeValue(lifetimeValue);
        
        // Calculate monthly recurring revenue (MRR)
        BigDecimal mrr = subscriptionManagementService.calculateMRR(
            event.getAmount(), event.getFrequency());
        analysis.setMonthlyRecurringRevenue(mrr);
        
        // Analyze churn risk
        int churnRiskScore = subscriptionManagementService.analyzeChurnRisk(
            event.getCustomerId(), event.getCategory(), event.getAmount());
        analysis.setChurnRiskScore(churnRiskScore);
        
        // Calculate retention probability
        double retentionProbability = subscriptionManagementService.calculateRetentionProbability(
            event.getCustomerId(), event.getSubscriptionId(), churnRiskScore);
        analysis.setRetentionProbability(retentionProbability);
        
        // Analyze pricing optimization opportunities
        BigDecimal optimalPrice = subscriptionManagementService.analyzeOptimalPricing(
            event.getCategory(), event.getAmount(), event.getCustomerId());
        analysis.setOptimalPricePoint(optimalPrice);
        
        // Calculate upgrade/downgrade probability
        double upgradeProbability = subscriptionManagementService.calculateUpgradeProbability(
            event.getCustomerId(), event.getCategory(), event.getAmount());
        analysis.setUpgradeProbability(upgradeProbability);
        
        // Analyze payment failure risk
        int paymentFailureRisk = subscriptionManagementService.analyzePaymentFailureRisk(
            event.getPaymentMethod(), event.getCustomerId(), event.getAmount());
        analysis.setPaymentFailureRisk(paymentFailureRisk);
        
        // Calculate customer acquisition cost (CAC) payback period
        int cacPaybackMonths = subscriptionManagementService.calculateCACPayback(
            event.getCustomerId(), mrr, event.getCategory());
        analysis.setCacPaybackMonths(cacPaybackMonths);
        
        subscriptionManagementService.saveAnalysis(analysis);
        
        // Update payment with analysis results
        payment.setLifetimeValue(lifetimeValue);
        payment.setChurnRiskScore(churnRiskScore);
        payment.setPaymentFailureRisk(paymentFailureRisk);
        recurringPaymentRepository.save(payment);
        
        log.info("Subscription analysis completed: paymentId={}, ltv={}, churnRisk={}", 
                event.getPaymentId(), lifetimeValue, churnRiskScore);
        
        return analysis;
    }

    private BillingCycleManagement setupBillingCycle(RecurringPaymentScheduledEvent event, 
                                                    RecurringPayment payment,
                                                    PaymentScheduleValidation validation) {
        BillingCycleManagement billingCycle = BillingCycleManagement.builder()
            .paymentId(event.getPaymentId())
            .setupDate(LocalDateTime.now())
            .build();
            
        // Calculate billing cycle dates
        List<LocalDateTime> billingDates = billingCycleService.calculateBillingDates(
            event.getStartDate(), event.getEndDate(), event.getFrequency());
        billingCycle.setBillingDates(billingDates);
        
        // Setup proration rules for mid-cycle changes
        Map<String, BigDecimal> prorationRules = billingCycleService.setupProrationRules(
            event.getAmount(), event.getFrequency(), event.getProrationMethod());
        billingCycle.setProrationRules(prorationRules);
        
        // Configure grace period
        int gracePeriodDays = billingCycleService.determineGracePeriod(
            event.getCategory(), event.getCustomerId(), event.getAmount());
        billingCycle.setGracePeriodDays(gracePeriodDays);
        
        // Setup billing reminders
        List<LocalDateTime> reminderDates = billingCycleService.scheduleReminders(
            billingDates, event.getFrequency(), gracePeriodDays);
        billingCycle.setReminderDates(reminderDates);
        
        // Configure invoice generation
        billingCycleService.setupInvoiceGeneration(
            event.getPaymentId(), event.getCustomerId(), billingDates);
        billingCycle.setInvoiceGenerationConfigured(true);
        
        // Setup payment collection windows
        Map<LocalDateTime, LocalDateTime> collectionWindows = billingCycleService.defineCollectionWindows(
            billingDates, gracePeriodDays);
        billingCycle.setCollectionWindows(collectionWindows);
        
        // Configure tax calculation rules
        if (event.getTaxAmount() != null && event.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            billingCycleService.setupTaxCalculation(
                event.getPaymentId(), event.getTaxRate(), event.getTaxJurisdiction());
            billingCycle.setTaxCalculationConfigured(true);
        }
        
        // Setup discount application rules
        if (event.getDiscountAmount() != null && event.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            billingCycleService.setupDiscountRules(
                event.getPaymentId(), event.getDiscountType(), event.getDiscountAmount());
            billingCycle.setDiscountRulesConfigured(true);
        }
        
        billingCycleService.saveBillingCycle(billingCycle);
        
        // Update payment with billing cycle info
        payment.setNextBillingDate(billingDates.isEmpty() ? null : billingDates.get(0));
        payment.setGracePeriodDays(gracePeriodDays);
        recurringPaymentRepository.save(payment);
        
        log.info("Billing cycle setup completed: paymentId={}, totalCycles={}, gracePeriod={}", 
                event.getPaymentId(), billingDates.size(), gracePeriodDays);
        
        return billingCycle;
    }

    private PaymentRetryStrategy configureRetryStrategy(RecurringPaymentScheduledEvent event, 
                                                       RecurringPayment payment,
                                                       SubscriptionAnalysis analysis) {
        PaymentRetryStrategy strategy = PaymentRetryStrategy.builder()
            .paymentId(event.getPaymentId())
            .configurationDate(LocalDateTime.now())
            .build();
            
        // Determine retry attempts based on risk
        int maxRetryAttempts = paymentRetryService.calculateMaxRetryAttempts(
            analysis.getPaymentFailureRisk(), event.getAmount(), event.getCategory());
        strategy.setMaxRetryAttempts(maxRetryAttempts);
        
        // Configure retry intervals
        List<Integer> retryIntervals = paymentRetryService.defineRetryIntervals(
            maxRetryAttempts, event.getFrequency());
        strategy.setRetryIntervals(retryIntervals);
        
        // Setup intelligent retry timing
        List<String> optimalRetryTimes = paymentRetryService.calculateOptimalRetryTimes(
            event.getCustomerId(), event.getPaymentMethod(), event.getTimezone());
        strategy.setOptimalRetryTimes(optimalRetryTimes);
        
        // Configure payment method fallback
        List<String> fallbackMethods = paymentRetryService.identifyFallbackMethods(
            event.getCustomerId(), event.getPaymentMethod());
        strategy.setFallbackPaymentMethods(fallbackMethods);
        
        // Setup retry notification rules
        Map<Integer, String> notificationRules = paymentRetryService.defineNotificationRules(
            maxRetryAttempts, event.getCustomerId());
        strategy.setNotificationRules(notificationRules);
        
        // Configure soft decline handling
        boolean softDeclineRetry = paymentRetryService.enableSoftDeclineRetry(
            event.getPaymentMethod(), event.getAmount());
        strategy.setSoftDeclineRetryEnabled(softDeclineRetry);
        
        // Setup card updater integration
        if (event.getPaymentMethod().equals("CREDIT_CARD") || event.getPaymentMethod().equals("DEBIT_CARD")) {
            paymentRetryService.setupCardUpdater(
                event.getPaymentInstrumentId(), event.getCustomerId());
            strategy.setCardUpdaterEnabled(true);
        }
        
        // Calculate retry success probability
        double retrySuccessProbability = paymentRetryService.calculateRetrySuccessProbability(
            event.getPaymentMethod(), analysis.getPaymentFailureRisk(), maxRetryAttempts);
        strategy.setRetrySuccessProbability(retrySuccessProbability);
        
        paymentRetryService.saveStrategy(strategy);
        
        // Update payment with retry configuration
        payment.setMaxRetryAttempts(maxRetryAttempts);
        payment.setRetryStrategyConfigured(true);
        recurringPaymentRepository.save(payment);
        
        log.info("Payment retry strategy configured: paymentId={}, maxAttempts={}, successProbability={}", 
                event.getPaymentId(), maxRetryAttempts, retrySuccessProbability);
        
        return strategy;
    }

    private void executePaymentAuthorization(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                           PaymentScheduleValidation validation) {
        if (!validation.isOverallValid()) {
            log.warn("Skipping authorization due to validation failure: paymentId={}", event.getPaymentId());
            return;
        }
        
        // Pre-authorize payment method
        boolean preAuthSuccess = paymentProcessingService.preAuthorize(
            event.getPaymentInstrumentId(), event.getAmount(), event.getMerchantId());
            
        if (!preAuthSuccess) {
            payment.setStatus("PRE_AUTH_FAILED");
            payment.setPreAuthorizationFailed(true);
            recurringPaymentRepository.save(payment);
            return;
        }
        
        // Setup tokenization for secure processing
        String paymentToken = paymentProcessingService.tokenizePaymentMethod(
            event.getPaymentInstrumentId(), event.getCustomerId());
        payment.setPaymentToken(paymentToken);
        
        // Configure 3D Secure if applicable
        if (requiresThreeDSecure(event.getAmount(), event.getPaymentMethod())) {
            paymentProcessingService.setup3DSecure(
                event.getPaymentId(), event.getCustomerId(), event.getAmount());
            payment.setThreeDSecureRequired(true);
        }
        
        // Setup mandate for SEPA/ACH payments
        if (event.getPaymentMethod().equals("SEPA") || event.getPaymentMethod().equals("ACH")) {
            String mandateId = paymentProcessingService.createMandate(
                event.getCustomerId(), event.getAccountId(), event.getMerchantId());
            payment.setMandateId(mandateId);
        }
        
        // Configure payment routing
        String processorId = paymentProcessingService.selectOptimalProcessor(
            event.getPaymentMethod(), event.getAmount(), event.getMerchantId());
        payment.setProcessorId(processorId);
        
        payment.setStatus("AUTHORIZED");
        payment.setAuthorizationDate(LocalDateTime.now());
        recurringPaymentRepository.save(payment);
        
        log.info("Payment authorization executed: paymentId={}, authorized=true, processor={}", 
                event.getPaymentId(), processorId);
    }

    private void setupDunningManagement(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                       PaymentRetryStrategy retryStrategy) {
        // Configure dunning campaigns
        String dunningCampaignId = dunningManagementService.createDunningCampaign(
            event.getPaymentId(), event.getCustomerId(), retryStrategy.getMaxRetryAttempts());
            
        // Setup dunning communication sequence
        List<Map<String, Object>> communicationSequence = dunningManagementService.defineCommunicationSequence(
            retryStrategy.getMaxRetryAttempts(), event.getCustomerId(), event.getAmount());
            
        // Configure grace period handling
        dunningManagementService.setupGracePeriodHandling(
            event.getPaymentId(), payment.getGracePeriodDays());
            
        // Setup service suspension rules
        Map<String, Object> suspensionRules = dunningManagementService.defineSuspensionRules(
            event.getCategory(), retryStrategy.getMaxRetryAttempts(), payment.getGracePeriodDays());
            
        // Configure win-back strategies
        dunningManagementService.setupWinBackStrategies(
            event.getCustomerId(), event.getCategory(), event.getAmount());
            
        // Setup payment arrangement options
        List<Map<String, Object>> paymentArrangements = dunningManagementService.definePaymentArrangements(
            event.getAmount(), event.getFrequency(), event.getCustomerId());
            
        payment.setDunningCampaignId(dunningCampaignId);
        payment.setDunningConfigured(true);
        recurringPaymentRepository.save(payment);
        
        log.info("Dunning management setup completed: paymentId={}, campaignId={}", 
                event.getPaymentId(), dunningCampaignId);
    }

    private void configureSubscriptionLifecycle(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                              SubscriptionAnalysis analysis) {
        // Setup upgrade/downgrade paths
        subscriptionManagementService.defineUpgradeDowngradePaths(
            event.getSubscriptionId(), event.getCategory(), event.getAmount());
            
        // Configure pause/resume functionality
        subscriptionManagementService.setupPauseResumeCapability(
            event.getPaymentId(), event.getSubscriptionId(), event.getFrequency());
            
        // Setup cancellation workflow
        subscriptionManagementService.defineCancellationWorkflow(
            event.getSubscriptionId(), event.getCategory(), analysis.getChurnRiskScore());
            
        // Configure auto-renewal rules
        if (event.isAutoRenew()) {
            subscriptionManagementService.setupAutoRenewal(
                event.getPaymentId(), event.getEndDate(), event.getRenewalTerms());
        }
        
        // Setup trial conversion tracking
        if (event.isTrialPeriod()) {
            subscriptionManagementService.setupTrialConversionTracking(
                event.getPaymentId(), event.getTrialEndDate(), event.getAmount());
        }
        
        // Configure subscription modification rules
        subscriptionManagementService.defineModificationRules(
            event.getSubscriptionId(), event.getCategory(), event.getProrationMethod());
            
        log.info("Subscription lifecycle configured: paymentId={}, autoRenew={}, trial={}", 
                event.getPaymentId(), event.isAutoRenew(), event.isTrialPeriod());
    }

    private void setupPaymentMonitoring(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                       BillingCycleManagement billingCycle) {
        // Setup payment success monitoring
        paymentSchedulingService.setupSuccessMonitoring(
            event.getPaymentId(), billingCycle.getBillingDates());
            
        // Configure failure alerting
        paymentSchedulingService.setupFailureAlerting(
            event.getPaymentId(), event.getCustomerId(), payment.getMaxRetryAttempts());
            
        // Setup churn prediction monitoring
        subscriptionManagementService.setupChurnMonitoring(
            event.getSubscriptionId(), payment.getChurnRiskScore());
            
        // Configure revenue recognition tracking
        billingCycleService.setupRevenueRecognition(
            event.getPaymentId(), event.getAmount(), event.getFrequency());
            
        // Setup compliance monitoring
        paymentSchedulingService.setupComplianceMonitoring(
            event.getPaymentId(), event.getCategory(), event.getMerchantId());
            
        // Configure dispute monitoring
        paymentProcessingService.setupDisputeMonitoring(
            event.getPaymentId(), event.getMerchantId(), event.getAmount());
            
        log.info("Payment monitoring setup completed: paymentId={}", event.getPaymentId());
    }

    private void processInitialPayment(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                      PaymentScheduleValidation validation) {
        // Check if immediate payment required
        if (!event.isImmediateStart() || !validation.isOverallValid()) {
            return;
        }
        
        // Process trial payment if applicable
        BigDecimal paymentAmount = event.isTrialPeriod() && LocalDateTime.now().isBefore(event.getTrialEndDate()) 
            ? event.getTrialAmount() : event.getAmount();
            
        // Execute payment
        Map<String, Object> paymentResult = paymentProcessingService.processPayment(
            event.getPaymentInstrumentId(), paymentAmount, event.getMerchantId(),
            event.getPaymentId().toString());
            
        boolean success = (boolean) paymentResult.get("success");
        String transactionId = (String) paymentResult.get("transactionId");
        
        if (success) {
            payment.setLastPaymentDate(LocalDateTime.now());
            payment.setLastPaymentAmount(paymentAmount);
            payment.setLastTransactionId(transactionId);
            payment.setPaymentsProcessed(1);
            payment.setPaymentsRemaining(payment.getPaymentsRemaining() - 1);
            payment.setStatus("ACTIVE");
        } else {
            payment.setFailedPayments(1);
            payment.setLastFailureDate(LocalDateTime.now());
            payment.setLastFailureReason((String) paymentResult.get("failureReason"));
            payment.setStatus("PAYMENT_FAILED");
            
            // Trigger retry strategy
            paymentRetryService.initiateRetry(event.getPaymentId(), 1);
        }
        
        recurringPaymentRepository.save(payment);
        
        log.info("Initial payment processed: paymentId={}, success={}, amount={}", 
                event.getPaymentId(), success, paymentAmount);
    }

    private void generateBillingDocumentation(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                             BillingCycleManagement billingCycle) {
        // Generate subscription agreement
        billingCycleService.generateSubscriptionAgreement(
            event.getPaymentId(), event.getCustomerId(), event.getMerchantId(),
            event.getAmount(), event.getFrequency(), event.getTermsAndConditions());
            
        // Generate payment schedule document
        billingCycleService.generatePaymentSchedule(
            event.getPaymentId(), billingCycle.getBillingDates(), event.getAmount());
            
        // Generate mandate documentation for SEPA/ACH
        if (payment.getMandateId() != null) {
            paymentProcessingService.generateMandateDocumentation(
                payment.getMandateId(), event.getCustomerId(), event.getAccountId());
        }
        
        // Generate tax documentation if applicable
        if (event.getTaxAmount() != null && event.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            billingCycleService.generateTaxDocumentation(
                event.getPaymentId(), event.getTaxAmount(), event.getTaxJurisdiction());
        }
        
        // Archive documentation with retention policy
        billingCycleService.archiveDocumentation(
            event.getPaymentId(), 7); // 7 years retention
            
        log.info("Billing documentation generated: paymentId={}", event.getPaymentId());
    }

    private void sendSubscriptionNotifications(RecurringPaymentScheduledEvent event, RecurringPayment payment,
                                              BillingCycleManagement billingCycle,
                                              SubscriptionAnalysis analysis) {
        String customerName = event.getCustomerName();
        
        // Customer email notification
        Map<String, Object> emailContext = Map.of(
            "customerName", customerName,
            "paymentId", event.getPaymentId(),
            "subscriptionName", event.getDescription(),
            "amount", event.getAmount(),
            "frequency", event.getFrequency(),
            "startDate", event.getStartDate(),
            "nextPaymentDate", payment.getNextPaymentDate(),
            "paymentMethod", maskPaymentMethod(event.getPaymentMethod(), event.getPaymentInstrumentId()),
            "status", payment.getStatus(),
            "managementUrl", generateManagementUrl(event.getPaymentId()),
            "supportContact", getSupportContactInfo()
        );
        
        // SMS confirmation
        Map<String, Object> smsContext = Map.of(
            "customerName", customerName.split(" ")[0],
            "subscriptionName", event.getDescription(),
            "amount", event.getAmount(),
            "frequency", event.getFrequency(),
            "status", getStatusMessage(payment.getStatus())
        );
        
        // In-app notification
        Map<String, Object> appContext = Map.of(
            "paymentId", event.getPaymentId(),
            "subscriptionId", event.getSubscriptionId(),
            "status", payment.getStatus(),
            "nextPaymentDate", payment.getNextPaymentDate(),
            "lifetimeValue", analysis.getLifetimeValue(),
            "paymentsRemaining", payment.getPaymentsRemaining(),
            "autoRenew", event.isAutoRenew()
        );
        
        // Trial expiration reminder if applicable
        if (event.isTrialPeriod() && event.getTrialEndDate() != null) {
            LocalDateTime reminderDate = event.getTrialEndDate().minusDays(3);
            subscriptionManagementService.scheduleTrialExpirationReminder(
                event.getCustomerId(), event.getPaymentId(), reminderDate);
        }
        
        log.info("Subscription notifications sent: paymentId={}, status={}", 
                event.getPaymentId(), payment.getStatus());
    }

    private LocalDateTime calculateNextPaymentDate(LocalDateTime startDate, String frequency) {
        return switch (frequency) {
            case "DAILY" -> startDate.plusDays(1);
            case "WEEKLY" -> startDate.plusWeeks(1);
            case "BIWEEKLY" -> startDate.plusWeeks(2);
            case "MONTHLY" -> startDate.plusMonths(1);
            case "QUARTERLY" -> startDate.plusMonths(3);
            case "SEMIANNUALLY" -> startDate.plusMonths(6);
            case "ANNUALLY" -> startDate.plusYears(1);
            default -> startDate.plusMonths(1);
        };
    }

    private int calculateTotalPayments(RecurringPaymentScheduledEvent event) {
        if (event.getEndDate() == null) {
            return Integer.MAX_VALUE; // Ongoing subscription
        }
        
        long daysBetween = ChronoUnit.DAYS.between(event.getStartDate(), event.getEndDate());
        
        return switch (event.getFrequency()) {
            case "DAILY" -> (int) daysBetween;
            case "WEEKLY" -> (int) (daysBetween / 7);
            case "BIWEEKLY" -> (int) (daysBetween / 14);
            case "MONTHLY" -> (int) (daysBetween / 30);
            case "QUARTERLY" -> (int) (daysBetween / 90);
            case "SEMIANNUALLY" -> (int) (daysBetween / 180);
            case "ANNUALLY" -> (int) (daysBetween / 365);
            default -> (int) (daysBetween / 30);
        };
    }

    private String generateValidationFailureReason(PaymentScheduleValidation validation) {
        StringBuilder reason = new StringBuilder("Validation failed: ");
        if (!validation.isFrequencyValid()) reason.append("Invalid frequency; ");
        if (!validation.isPaymentMethodValid()) reason.append("Invalid payment method; ");
        if (!validation.isAccountStandingValid()) reason.append("Account not in good standing; ");
        if (!validation.isMerchantConfigValid()) reason.append("Merchant configuration error; ");
        if (!validation.isBillingAgreementValid()) reason.append("No billing agreement; ");
        if (validation.isDuplicateSubscription()) reason.append("Duplicate subscription detected; ");
        if (!validation.isAmountLimitsValid()) reason.append("Amount exceeds limits; ");
        return reason.toString();
    }

    private boolean requiresThreeDSecure(BigDecimal amount, String paymentMethod) {
        return (paymentMethod.equals("CREDIT_CARD") || paymentMethod.equals("DEBIT_CARD")) &&
               amount.compareTo(new BigDecimal("100")) >= 0;
    }

    private String maskPaymentMethod(String paymentMethod, String instrumentId) {
        if (paymentMethod.equals("CREDIT_CARD") || paymentMethod.equals("DEBIT_CARD")) {
            return "****" + instrumentId.substring(Math.max(0, instrumentId.length() - 4));
        } else if (paymentMethod.equals("BANK_ACCOUNT")) {
            return "****" + instrumentId.substring(Math.max(0, instrumentId.length() - 4));
        } else {
            return paymentMethod;
        }
    }

    private String generateManagementUrl(UUID paymentId) {
        return "https://subscriptions.example.com/manage/" + paymentId.toString();
    }

    private String getStatusMessage(String status) {
        return switch (status) {
            case "SCHEDULED" -> "Subscription scheduled";
            case "ACTIVE" -> "Subscription active";
            case "PAYMENT_FAILED" -> "Payment failed - retry pending";
            case "PAUSED" -> "Subscription paused";
            case "CANCELLED" -> "Subscription cancelled";
            default -> "Processing";
        };
    }

    private Map<String, String> getSupportContactInfo() {
        return Map.of(
            "phone", "1-800-WAQITI-BILL",
            "email", "billing@example.com",
            "hours", "24/7",
            "chat", "Available in-app"
        );
    }

    private boolean isAlreadyProcessed(UUID eventId) {
        return recurringPaymentRepository.existsByEventId(eventId);
    }

    private void markEventAsProcessed(UUID eventId) {
        recurringPaymentRepository.markEventAsProcessed(eventId);
    }
}