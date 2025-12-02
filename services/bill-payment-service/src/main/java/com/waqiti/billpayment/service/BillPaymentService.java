package com.waqiti.billpayment.service;

import com.waqiti.billpayment.domain.*;
import com.waqiti.billpayment.dto.*;
import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.mapper.*;
import com.waqiti.billpayment.repository.*;
import com.waqiti.billpayment.provider.BillerIntegrationProvider;
import com.waqiti.billpayment.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.BillPaymentEvent;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced Bill Payment Service - Modern bill pay with AI insights and automation
 * 
 * Features:
 * - Smart bill categorization and organization
 * - Auto-pay with intelligent scheduling
 * - Bill negotiation and optimization
 * - Cashback and rewards for bill payments
 * - Bill splitting with friends/family
 * - Due date tracking and reminders
 * - Receipt and document management
 * - Bill analytics and spending insights
 * - Utility provider integrations
 * - Credit building through bill history
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BillPaymentService {

    private final BillRepository billRepository;
    private final BillPaymentRepository paymentRepository;
    private final BillerRepository billerRepository;
    private final BillerConnectionRepository billerConnectionRepository;
    private final AutoPayConfigRepository autoPayRepository;
    private final BillSharingRepository sharingRepository;
    private final BillInsightRepository insightRepository;
    private final BillNegotiationRepository negotiationRepository;

    private final BillerIntegrationProvider billerProvider;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final EncryptionService encryptionService;

    // Mappers
    private final BillerMapper billerMapper;
    private final BillMapper billMapper;
    private final BillPaymentMapper billPaymentMapper;
    private final BillAccountMapper billAccountMapper;
    private final AutoPayConfigMapper autoPayConfigMapper;
    private final BillSharingMapper billSharingMapper;

    // Processing service
    private final BillPaymentProcessingService billPaymentProcessingService;
    
    @Value("${bill.payment.max-amount:10000.00}")
    private BigDecimal maxBillPaymentAmount;
    
    @Value("${bill.cashback.enabled:true}")
    private boolean billCashbackEnabled;
    
    @Value("${bill.negotiation.enabled:true}")
    private boolean billNegotiationEnabled;
    
    @Value("${bill.ai-insights.enabled:true}")
    private boolean aiInsightsEnabled;
    
    /**
     * Connect to a biller and import bills automatically
     */
    public BillerConnectionDto connectToBiller(UUID userId, ConnectBillerRequest request) {
        log.info("Connecting user {} to biller: {}", userId, request.getBillerName());
        
        try {
            // Validate biller exists
            Biller biller = billerRepository.findByName(request.getBillerName())
                .orElseThrow(() -> new BillerNotFoundException("Biller not found: " + request.getBillerName()));
            
            // Check if already connected
            if (billerRepository.existsConnection(userId, biller.getId())) {
                throw new BusinessException("Already connected to this biller");
            }
            
            // Establish connection with biller
            BillerConnectionResult connectionResult = billerProvider.establishConnection(
                biller.getId(),
                request.getCredentials(),
                userId
            );
            
            // Create biller connection
            BillerConnection connection = BillerConnection.builder()
                .userId(userId)
                .billerId(biller.getId())
                .accountNumber(encryptionService.encrypt(request.getAccountNumber()))
                .connectionId(connectionResult.getConnectionId())
                .status(BillerConnectionStatus.ACTIVE)
                .lastSyncedAt(LocalDateTime.now())
                .autoImportEnabled(request.isAutoImportEnabled())
                .createdAt(LocalDateTime.now())
                .build();
            
            connection = billerRepository.saveConnection(connection);
            
            // Import initial bills
            List<Bill> importedBills = importBillsFromBiller(connection);
            
            // Send welcome notification
            notificationService.sendBillerConnectedNotification(userId, biller, importedBills.size());
            
            log.info("Connected to biller {} and imported {} bills for user {}", 
                    biller.getName(), importedBills.size(), userId);
            
            return BillerConnectionDto.builder()
                .id(connection.getId())
                .billerName(biller.getName())
                .billerLogo(biller.getLogoUrl())
                .accountNumber(maskAccountNumber(request.getAccountNumber()))
                .status(connection.getStatus())
                .billsImported(importedBills.size())
                .autoImportEnabled(connection.isAutoImportEnabled())
                .connectedAt(connection.getCreatedAt())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to connect to biller for user: {}", userId, e);
            throw new BusinessException("Failed to connect to biller: " + e.getMessage());
        }
    }
    
    /**
     * Get user's bills organized by status and due date
     */
    public BillDashboardDto getBillDashboard(UUID userId) {
        log.debug("Getting bill dashboard for user: {}", userId);
        
        try {
            List<Bill> allBills = billRepository.findByUserIdOrderByDueDateAsc(userId);
            
            LocalDate today = LocalDate.now();
            LocalDate nextWeek = today.plusDays(7);
            LocalDate nextMonth = today.plusDays(30);
            
            // Categorize bills
            List<BillDto> overdueBills = allBills.stream()
                .filter(bill -> bill.getDueDate().isBefore(today) && !bill.isPaid())
                .map(this::toBillDto)
                .collect(Collectors.toList());
            
            List<BillDto> dueSoon = allBills.stream()
                .filter(bill -> !bill.getDueDate().isBefore(today) && 
                              bill.getDueDate().isBefore(nextWeek) && !bill.isPaid())
                .map(this::toBillDto)
                .collect(Collectors.toList());
            
            List<BillDto> upcoming = allBills.stream()
                .filter(bill -> bill.getDueDate().isAfter(nextWeek) && 
                              bill.getDueDate().isBefore(nextMonth) && !bill.isPaid())
                .map(this::toBillDto)
                .collect(Collectors.toList());
            
            // Calculate totals
            BigDecimal totalOverdue = overdueBills.stream()
                .map(BillDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalDueSoon = dueSoon.stream()
                .map(BillDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal monthlyTotal = allBills.stream()
                .filter(bill -> bill.getDueDate().getMonth() == today.getMonth() && 
                              bill.getDueDate().getYear() == today.getYear())
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Get bill insights
            List<BillInsightDto> insights = getBillInsights(userId);
            
            // Get auto-pay summary
            AutoPaySummaryDto autoPaySummary = getAutoPaySummary(userId);
            
            return BillDashboardDto.builder()
                .overdueBills(overdueBills)
                .dueSoonBills(dueSoon)
                .upcomingBills(upcoming)
                .totalOverdue(totalOverdue)
                .totalDueSoon(totalDueSoon)
                .monthlyTotal(monthlyTotal)
                .billInsights(insights)
                .autoPaySummary(autoPaySummary)
                .nextDueDate(dueSoon.isEmpty() ? null : dueSoon.get(0).getDueDate())
                .totalBillsCount(allBills.size())
                .unpaidBillsCount(overdueBills.size() + dueSoon.size() + upcoming.size())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get bill dashboard for user: {}", userId, e);
            throw new BusinessException("Failed to load bill dashboard");
        }
    }
    
    /**
     * Pay a bill with multiple payment options
     */
    public BillPaymentDto payBill(UUID userId, PayBillRequest request) {
        log.info("Processing bill payment for user: {}, bill: {}, amount: {}", 
                userId, request.getBillId(), request.getAmount());
        
        try {
            // Get the bill
            Bill bill = billRepository.findByIdAndUserId(request.getBillId(), userId)
                .orElseThrow(() -> new BillNotFoundException("Bill not found"));
            
            // Validate payment amount
            validatePaymentAmount(bill, request.getAmount());
            
            // Check wallet balance
            if (!hasEnoughBalance(userId, request.getAmount(), request.getPaymentMethod())) {
                throw new InsufficientFundsException("Insufficient funds for bill payment");
            }
            
            // Create payment record
            BillPayment payment = BillPayment.builder()
                .userId(userId)
                .billId(request.getBillId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentDate(request.getScheduledDate() != null ? 
                    request.getScheduledDate() : LocalDate.now())
                .isScheduled(request.getScheduledDate() != null)
                .status(BillPaymentStatus.PENDING)
                .confirmationNumber(generateConfirmationNumber())
                .memo(request.getMemo())
                .createdAt(LocalDateTime.now())
                .build();
            
            payment = paymentRepository.save(payment);
            
            // Process payment based on type
            if (request.getScheduledDate() != null && request.getScheduledDate().isAfter(LocalDate.now())) {
                // Schedule payment for future
                schedulePayment(payment);
            } else {
                // Process immediately
                payment = processImmediatePayment(payment, bill);
            }
            
            // Apply cashback if eligible
            if (billCashbackEnabled && isCashbackEligible(bill, payment)) {
                applyCashback(userId, payment, bill);
            }
            
            // Update bill status
            if (payment.getStatus() == BillPaymentStatus.COMPLETED) {
                updateBillAfterPayment(bill, payment);
            }
            
            // Send confirmation
            notificationService.sendBillPaymentConfirmation(userId, payment, bill);
            
            // Publish event
            eventPublisher.publish(BillPaymentEvent.paymentProcessed(payment, bill));
            
            // Generate insights
            if (aiInsightsEnabled) {
                generatePaymentInsights(userId, payment, bill);
            }
            
            log.info("Bill payment processed successfully: {}", payment.getId());
            
            return toBillPaymentDto(payment, bill);
            
        } catch (Exception e) {
            log.error("Failed to process bill payment for user: {}", userId, e);
            throw new BusinessException("Failed to process bill payment: " + e.getMessage());
        }
    }
    
    /**
     * Set up automatic bill pay
     */
    public AutoPayConfigDto setupAutoPay(UUID userId, SetupAutoPayRequest request) {
        log.info("Setting up auto-pay for user: {}, bill: {}", userId, request.getBillId());
        
        try {
            Bill bill = billRepository.findByIdAndUserId(request.getBillId(), userId)
                .orElseThrow(() -> new BillNotFoundException("Bill not found"));
            
            // Check if auto-pay already exists
            Optional<AutoPayConfig> existing = autoPayRepository.findByUserIdAndBillId(userId, request.getBillId());
            if (existing.isPresent() && existing.get().isEnabled()) {
                throw new BusinessException("Auto-pay already enabled for this bill");
            }
            
            // Validate auto-pay settings
            validateAutoPayRequest(request);
            
            // Create auto-pay configuration
            AutoPayConfig config = AutoPayConfig.builder()
                .userId(userId)
                .billId(request.getBillId())
                .paymentMethod(request.getPaymentMethod())
                .maxAmount(request.getMaxAmount())
                .paymentTiming(request.getPaymentTiming()) // e.g., 3 days before due date
                .enableSmartScheduling(request.isEnableSmartScheduling())
                .onlyPayExactAmount(request.isOnlyPayExactAmount())
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
            
            config = autoPayRepository.save(config);
            
            // Schedule next auto-payment
            scheduleNextAutoPayment(config, bill);
            
            // Send confirmation
            notificationService.sendAutoPaySetupNotification(userId, config, bill);
            
            log.info("Auto-pay setup completed for bill: {}", request.getBillId());
            
            return toAutoPayConfigDto(config, bill);
            
        } catch (Exception e) {
            log.error("Failed to setup auto-pay for user: {}", userId, e);
            throw new BusinessException("Failed to setup auto-pay: " + e.getMessage());
        }
    }
    
    /**
     * Split a bill with friends or family
     */
    public BillSharingDto shareBill(UUID userId, ShareBillRequest request) {
        log.info("User {} sharing bill {} with {} people", 
                userId, request.getBillId(), request.getParticipants().size());
        
        try {
            Bill bill = billRepository.findByIdAndUserId(request.getBillId(), userId)
                .orElseThrow(() -> new BillNotFoundException("Bill not found"));
            
            // Validate sharing request
            validateSharingRequest(request, bill);
            
            // Create bill sharing
            BillSharing sharing = BillSharing.builder()
                .billId(request.getBillId())
                .organizerUserId(userId)
                .totalAmount(bill.getAmount())
                .splitType(request.getSplitType())
                .customSplits(request.getCustomSplits())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .status(BillSharingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
            
            sharing = sharingRepository.save(sharing);
            
            // Create participant entries
            List<BillSharingParticipant> participants = createSharingParticipants(
                sharing.getId(), request.getParticipants(), request.getSplitType(), bill.getAmount());
            
            // Send invitations
            for (BillSharingParticipant participant : participants) {
                notificationService.sendBillSharingInvitation(participant, sharing, bill);
            }
            
            log.info("Bill sharing created: {} with {} participants", sharing.getId(), participants.size());
            
            return toBillSharingDto(sharing, participants, bill);
            
        } catch (Exception e) {
            log.error("Failed to share bill for user: {}", userId, e);
            throw new BusinessException("Failed to share bill: " + e.getMessage());
        }
    }
    
    /**
     * Get bill payment history with analytics
     */
    public Page<BillPaymentDto> getBillPaymentHistory(UUID userId, Pageable pageable) {
        log.debug("Getting bill payment history for user: {}", userId);
        
        Page<BillPayment> payments = paymentRepository.findByUserIdOrderByPaymentDateDesc(userId, pageable);
        
        return payments.map(payment -> {
            Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
            return toBillPaymentDto(payment, bill);
        });
    }
    
    /**
     * Get spending insights and analytics
     */
    public BillSpendingInsightsDto getBillSpendingInsights(UUID userId, int months) {
        log.debug("Getting bill spending insights for user: {} for {} months", userId, months);
        
        try {
            LocalDate startDate = LocalDate.now().minusMonths(months);
            List<BillPayment> payments = paymentRepository.findByUserIdAndPaymentDateAfter(userId, startDate);
            
            // Calculate total spending
            BigDecimal totalSpent = payments.stream()
                .filter(p -> p.getStatus() == BillPaymentStatus.COMPLETED)
                .map(BillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Average monthly spending
            BigDecimal avgMonthlySpending = totalSpent.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            
            // Category breakdown
            Map<String, BigDecimal> categorySpending = calculateCategorySpending(payments);
            
            // Trends
            List<MonthlySpendingDto> monthlyTrends = calculateMonthlyTrends(payments, months);
            
            // Bill optimization opportunities
            List<BillOptimizationDto> optimizations = identifyOptimizationOpportunities(userId);
            
            // Upcoming bills forecast
            BigDecimal upcomingBillsAmount = calculateUpcomingBillsAmount(userId);
            
            return BillSpendingInsightsDto.builder()
                .totalSpent(totalSpent)
                .avgMonthlySpending(avgMonthlySpending)
                .categoryBreakdown(categorySpending)
                .monthlyTrends(monthlyTrends)
                .optimizationOpportunities(optimizations)
                .upcomingBillsAmount(upcomingBillsAmount)
                .paymentCount(payments.size())
                .mostExpensiveCategory(findMostExpensiveCategory(categorySpending))
                .savingsOpportunity(calculateSavingsOpportunity(optimizations))
                .onTimePaymentRate(calculateOnTimePaymentRate(payments))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get spending insights for user: {}", userId, e);
            throw new BusinessException("Failed to load spending insights");
        }
    }
    
    /**
     * Negotiate bills with AI-powered optimization
     */
    @Async
    public CompletableFuture<BillNegotiationDto> negotiateBill(UUID userId, UUID billId) {
        log.info("Starting bill negotiation for user: {}, bill: {}", userId, billId);
        
        try {
            if (!billNegotiationEnabled) {
                throw new BusinessException("Bill negotiation feature is not available");
            }
            
            Bill bill = billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new BillNotFoundException("Bill not found"));
            
            // Check if negotiation is possible for this biller
            if (!billerProvider.supportsNegotiation(bill.getBillerId())) {
                throw new BusinessException("Negotiation not available for this biller");
            }
            
            // Create negotiation record
            BillNegotiation negotiation = BillNegotiation.builder()
                .userId(userId)
                .billId(billId)
                .originalAmount(bill.getAmount())
                .status(BillNegotiationStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
            
            negotiation = negotiationRepository.save(negotiation);
            
            // Analyze bill history and user profile
            NegotiationContext context = analyzeNegotiationContext(userId, bill);
            
            // Initiate negotiation with biller
            NegotiationResult result = billerProvider.initiateBillNegotiation(
                bill.getBillerId(), 
                bill.getAccountNumber(),
                context
            );
            
            // Update negotiation with result
            negotiation.setNegotiatedAmount(result.getOfferedAmount());
            negotiation.setSavingsAmount(bill.getAmount().subtract(result.getOfferedAmount()));
            negotiation.setStatus(result.isSuccessful() ? 
                BillNegotiationStatus.SUCCESS : BillNegotiationStatus.FAILED);
            negotiation.setCompletedAt(LocalDateTime.now());
            negotiation.setNegotiationDetails(result.getDetails());
            
            negotiationRepository.save(negotiation);
            
            // Send notification
            notificationService.sendBillNegotiationResult(userId, negotiation, bill);
            
            log.info("Bill negotiation completed: {} - Savings: ${}", 
                    negotiation.getId(), negotiation.getSavingsAmount());
            
            return CompletableFuture.completedFuture(toBillNegotiationDto(negotiation, bill));
            
        } catch (Exception e) {
            log.error("Failed to negotiate bill", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Scheduled job to process auto-payments
     */
    @Scheduled(cron = "0 0 9 * * ?") // Daily at 9 AM
    @Transactional
    public void processAutoPayments() {
        log.info("Processing scheduled auto-payments");
        
        LocalDate today = LocalDate.now();
        LocalDate upcoming = today.plusDays(3); // Process bills due in next 3 days
        
        List<AutoPayConfig> configs = autoPayRepository.findEnabledConfigsForProcessing(today, upcoming);
        
        int processed = 0;
        int failed = 0;
        
        for (AutoPayConfig config : configs) {
            try {
                processAutoPayment(config);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process auto-payment for config: {}", config.getId(), e);
                failed++;
            }
        }
        
        log.info("Auto-payment processing completed - Processed: {}, Failed: {}", processed, failed);
    }
    
    /**
     * Sync bills from connected billers
     */
    @Scheduled(cron = "0 0 6 * * ?") // Daily at 6 AM
    @Transactional
    public void syncBillsFromBillers() {
        log.info("Syncing bills from connected billers");
        
        List<BillerConnection> connections = billerRepository.findActiveConnections();
        
        int totalImported = 0;
        
        for (BillerConnection connection : connections) {
            try {
                List<Bill> importedBills = importBillsFromBiller(connection);
                totalImported += importedBills.size();
                
                connection.setLastSyncedAt(LocalDateTime.now());
                billerRepository.saveConnection(connection);
                
            } catch (Exception e) {
                log.error("Failed to sync bills for connection: {}", connection.getId(), e);
            }
        }
        
        log.info("Bill sync completed - Total bills imported: {}", totalImported);
    }
    
    // Helper methods
    
    private List<Bill> importBillsFromBiller(BillerConnection connection) {
        try {
            List<BillData> billsData = billerProvider.fetchBills(connection.getConnectionId());
            List<Bill> bills = new ArrayList<>();
            
            for (BillData billData : billsData) {
                // Check if bill already exists
                if (!billRepository.existsByAccountNumberAndBillNumber(
                        billData.getAccountNumber(), billData.getBillNumber())) {
                    
                    Biller biller = billerRepository.findById(connection.getBillerId()).orElse(null);
                    
                    Bill bill = Bill.builder()
                        .userId(connection.getUserId())
                        .billerId(connection.getBillerId())
                        .billerName(biller != null ? biller.getName() : "Unknown")
                        .accountNumber(encryptionService.encrypt(billData.getAccountNumber()))
                        .billNumber(billData.getBillNumber())
                        .amount(billData.getAmount())
                        .dueDate(billData.getDueDate())
                        .issueDate(billData.getIssueDate())
                        .category(billData.getCategory())
                        .description(billData.getDescription())
                        .isPaid(false)
                        .isRecurring(billData.isRecurring())
                        .minimumPayment(billData.getMinimumPayment())
                        .createdAt(LocalDateTime.now())
                        .build();
                    
                    bills.add(billRepository.save(bill));
                }
            }
            
            return bills;
            
        } catch (Exception e) {
            log.error("Failed to import bills from connection: {}", connection.getId(), e);
            return Collections.emptyList();
        }
    }
    
    private void validatePaymentAmount(Bill bill, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be positive");
        }
        
        if (amount.compareTo(maxBillPaymentAmount) > 0) {
            throw new BusinessException("Payment amount exceeds maximum limit");
        }
        
        if (bill.getMinimumPayment() != null && amount.compareTo(bill.getMinimumPayment()) < 0) {
            throw new BusinessException("Payment amount is below minimum payment required");
        }
    }
    
    private boolean hasEnoughBalance(UUID userId, BigDecimal amount, String paymentMethod) {
        try {
            BigDecimal balance = walletService.getBalance(userId, "USD");
            return balance.compareTo(amount) >= 0;
        } catch (Exception e) {
            log.warn("Failed to check balance for user: {}", userId, e);
            return false;
        }
    }
    
    private BillPayment processImmediatePayment(BillPayment payment, Bill bill) {
        try {
            // Process payment through wallet service
            walletService.debit(
                payment.getUserId(),
                payment.getAmount(),
                "USD",
                "Bill payment: " + bill.getBillerName(),
                Map.of(
                    "billId", payment.getBillId().toString(),
                    "paymentId", payment.getId().toString(),
                    "billerName", bill.getBillerName()
                )
            );
            
            // Submit payment to biller if supported
            if (billerProvider.supportsDirectPayment(bill.getBillerId())) {
                PaymentSubmissionResult result = billerProvider.submitPayment(
                    bill.getBillerId(),
                    bill.getAccountNumber(),
                    payment.getAmount(),
                    payment.getConfirmationNumber()
                );
                
                payment.setBillerConfirmation(result.getBillerConfirmationNumber());
            }
            
            payment.setStatus(BillPaymentStatus.COMPLETED);
            payment.setProcessedAt(LocalDateTime.now());
            
            return paymentRepository.save(payment);
            
        } catch (Exception e) {
            log.error("Failed to process immediate payment: {}", payment.getId(), e);
            payment.setStatus(BillPaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            return paymentRepository.save(payment);
        }
    }
    
    private void schedulePayment(BillPayment payment) {
        // This would integrate with a job scheduler like Quartz
        payment.setStatus(BillPaymentStatus.SCHEDULED);
        paymentRepository.save(payment);
        
        log.info("Payment scheduled for: {}", payment.getPaymentDate());
    }
    
    private void updateBillAfterPayment(Bill bill, BillPayment payment) {
        bill.setPaid(true);
        bill.setPaidAmount(payment.getAmount());
        bill.setPaidDate(payment.getPaymentDate());
        bill.setLastUpdated(LocalDateTime.now());
        
        billRepository.save(bill);
    }
    
    private boolean isCashbackEligible(Bill bill, BillPayment payment) {
        // Define cashback eligibility rules
        return bill.getCategory().equals("UTILITIES") || 
               bill.getCategory().equals("TELECOM") ||
               payment.getAmount().compareTo(new BigDecimal("50")) >= 0;
    }
    
    private void applyCashback(UUID userId, BillPayment payment, Bill bill) {
        try {
            BigDecimal cashbackRate = getCashbackRate(bill.getCategory());
            BigDecimal cashbackAmount = payment.getAmount()
                .multiply(cashbackRate)
                .setScale(2, RoundingMode.HALF_UP);
            
            if (cashbackAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Credit cashback to wallet
                walletService.credit(
                    userId,
                    cashbackAmount,
                    "USD",
                    "Bill payment cashback: " + bill.getBillerName(),
                    Map.of(
                        "billPaymentId", payment.getId().toString(),
                        "cashbackRate", cashbackRate.toString()
                    )
                );
                
                payment.setCashbackAmount(cashbackAmount);
                paymentRepository.save(payment);
                
                // Send cashback notification
                notificationService.sendBillPaymentCashbackNotification(userId, payment, bill);
                
                log.info("Applied ${} cashback for bill payment: {}", cashbackAmount, payment.getId());
            }
            
        } catch (Exception e) {
            log.error("Failed to apply cashback for payment: {}", payment.getId(), e);
        }
    }
    
    private BigDecimal getCashbackRate(String category) {
        // Define category-specific cashback rates
        switch (category) {
            case "UTILITIES":
                return new BigDecimal("0.02"); // 2%
            case "TELECOM":
                return new BigDecimal("0.015"); // 1.5%
            case "INSURANCE":
                return new BigDecimal("0.01"); // 1%
            default:
                return new BigDecimal("0.005"); // 0.5%
        }
    }
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    private String generateConfirmationNumber() {
        return "BP" + System.currentTimeMillis() + 
               String.format("%04d", secureRandom.nextInt(10000));
    }
    
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
    
    // DTO conversion methods
    
    private BillDto toBillDto(Bill bill) {
        return BillDto.builder()
            .id(bill.getId())
            .billerName(bill.getBillerName())
            .accountNumber(maskAccountNumber(encryptionService.decrypt(bill.getAccountNumber())))
            .amount(bill.getAmount())
            .dueDate(bill.getDueDate())
            .category(bill.getCategory())
            .description(bill.getDescription())
            .isPaid(bill.isPaid())
            .isRecurring(bill.isRecurring())
            .minimumPayment(bill.getMinimumPayment())
            .daysPastDue(bill.isPaid() ? 0 : (int) ChronoUnit.DAYS.between(bill.getDueDate(), LocalDate.now()))
            .build();
    }
    
    private BillPaymentDto toBillPaymentDto(BillPayment payment, Bill bill) {
        return BillPaymentDto.builder()
            .id(payment.getId())
            .billId(payment.getBillId())
            .billerName(bill != null ? bill.getBillerName() : "Unknown")
            .amount(payment.getAmount())
            .paymentMethod(payment.getPaymentMethod())
            .paymentDate(payment.getPaymentDate())
            .status(payment.getStatus())
            .confirmationNumber(payment.getConfirmationNumber())
            .billerConfirmation(payment.getBillerConfirmation())
            .cashbackAmount(payment.getCashbackAmount())
            .memo(payment.getMemo())
            .isScheduled(payment.isScheduled())
            .createdAt(payment.getCreatedAt())
            .processedAt(payment.getProcessedAt())
            .build();
    }
    
    // Additional helper methods for auto-pay, insights, etc. would be implemented here...
    
    private void processAutoPayment(AutoPayConfig config) {
        // Implementation for processing auto-payments
        log.debug("Processing auto-payment for config: {}", config.getId());
    }
    
    private List<BillInsightDto> getBillInsights(UUID userId) {
        try {
            log.debug("Generating AI-powered bill insights for user: {}", userId);
            
            List<BillInsightDto> insights = new ArrayList<>();
            
            // Get user's bills and payment history
            List<Bill> userBills = billRepository.findByUserId(userId);
            List<BillPayment> recentPayments = paymentRepository.findByUserIdAndPaymentDateAfter(
                userId, LocalDateTime.now().minusMonths(6));
            
            if (userBills.isEmpty()) {
                return insights; // No bills to analyze
            }
            
            // 1. Overdue Bill Analysis
            insights.addAll(generateOverdueBillInsights(userId, userBills));
            
            // 2. Spending Pattern Analysis
            insights.addAll(generateSpendingPatternInsights(userId, recentPayments));
            
            // 3. Auto-Pay Recommendations
            insights.addAll(generateAutoPayRecommendations(userId, userBills, recentPayments));
            
            // 4. Bill Optimization Insights
            insights.addAll(generateBillOptimizationInsights(userId, userBills));
            
            // 5. Payment Timing Insights
            insights.addAll(generatePaymentTimingInsights(userId, recentPayments));
            
            // 6. Budget Impact Analysis
            insights.addAll(generateBudgetImpactInsights(userId, userBills, recentPayments));
            
            // 7. Seasonal Spending Insights
            insights.addAll(generateSeasonalSpendingInsights(userId, recentPayments));
            
            // 8. Bill Sharing Opportunities
            insights.addAll(generateBillSharingInsights(userId, userBills));
            
            // Sort insights by priority and recency
            insights.sort((a, b) -> {
                int priorityCompare = getPriorityWeight(a.getPriority()) - getPriorityWeight(b.getPriority());
                if (priorityCompare != 0) return priorityCompare;
                return b.getGeneratedAt().compareTo(a.getGeneratedAt());
            });
            
            // Limit to most relevant insights
            List<BillInsightDto> limitedInsights = insights.stream().limit(12).collect(Collectors.toList());
            
            log.info("Generated {} bill insights for user: {}", limitedInsights.size(), userId);
            return limitedInsights;
            
        } catch (Exception e) {
            log.error("Failed to generate bill insights for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    private List<BillInsightDto> generateOverdueBillInsights(UUID userId, List<Bill> bills) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        List<Bill> overdueBills = bills.stream()
            .filter(bill -> !bill.isPaid() && bill.getDueDate().isBefore(LocalDate.now()))
            .collect(Collectors.toList());
        
        if (!overdueBills.isEmpty()) {
            BigDecimal totalOverdue = overdueBills.stream()
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BillInsightDto overdueInsight = BillInsightDto.builder()
                .type("OVERDUE_BILLS")
                .priority("HIGH")
                .title("Overdue Bills Need Attention")
                .description(String.format("You have %d overdue bills totaling $%.2f. " +
                    "Consider paying these immediately to avoid late fees.",
                    overdueBills.size(), totalOverdue))
                .actionText("Pay Now")
                .impactAmount(totalOverdue)
                .generatedAt(LocalDateTime.now())
                .metadata(Map.of(
                    "overdueBillCount", overdueBills.size(),
                    "totalAmount", totalOverdue,
                    "oldestBillDays", calculateDaysOverdue(overdueBills.get(0))
                ))
                .build();
            
            insights.add(overdueInsight);
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateSpendingPatternInsights(UUID userId, List<BillPayment> payments) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        if (payments.size() < 3) return insights; // Not enough data
        
        // Analyze spending trends
        Map<String, BigDecimal> monthlySpending = payments.stream()
            .collect(Collectors.groupingBy(
                payment -> payment.getPaymentDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.reducing(BigDecimal.ZERO, BillPayment::getAmount, BigDecimal::add)
            ));
        
        List<BigDecimal> amounts = new ArrayList<>(monthlySpending.values());
        amounts.sort(Collections.reverseOrder());
        
        if (amounts.size() >= 3) {
            BigDecimal avgSpending = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
            
            BigDecimal lastMonthSpending = amounts.get(0);
            
            if (lastMonthSpending.compareTo(avgSpending.multiply(BigDecimal.valueOf(1.2))) > 0) {
                BillInsightDto spendingInsight = BillInsightDto.builder()
                    .type("SPENDING_SPIKE")
                    .priority("MEDIUM")
                    .title("Bill Spending Increased")
                    .description(String.format("Your bill spending increased by %.1f%% last month. " +
                        "Consider reviewing your bills for any changes.",
                        lastMonthSpending.divide(avgSpending, 4, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))))
                    .actionText("Review Bills")
                    .impactAmount(lastMonthSpending.subtract(avgSpending))
                    .generatedAt(LocalDateTime.now())
                    .build();
                
                insights.add(spendingInsight);
            }
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateAutoPayRecommendations(UUID userId, List<Bill> bills, List<BillPayment> payments) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Find bills without autopay that are paid consistently
        List<Bill> candidatesForAutoPay = bills.stream()
            .filter(bill -> !bill.isAutoPay() && bill.isRecurring())
            .filter(bill -> {
                long consistentPayments = payments.stream()
                    .filter(payment -> payment.getBillId().equals(bill.getId()))
                    .filter(payment -> payment.getStatus() == BillPaymentStatus.COMPLETED)
                    .count();
                return consistentPayments >= 3; // Paid at least 3 times
            })
            .collect(Collectors.toList());
        
        if (!candidatesForAutoPay.isEmpty()) {
            BigDecimal potentialSavings = candidatesForAutoPay.stream()
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BillInsightDto autoPayInsight = BillInsightDto.builder()
                .type("AUTOPAY_RECOMMENDATION")
                .priority("MEDIUM")
                .title("Enable Auto-Pay for Consistent Bills")
                .description(String.format("You have %d bills that could benefit from auto-pay. " +
                    "This would save you time and prevent late payments.",
                    candidatesForAutoPay.size()))
                .actionText("Set Up Auto-Pay")
                .impactAmount(potentialSavings)
                .generatedAt(LocalDateTime.now())
                .metadata(Map.of(
                    "eligibleBillCount", candidatesForAutoPay.size(),
                    "monthlyAmount", potentialSavings
                ))
                .build();
            
            insights.add(autoPayInsight);
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateBillOptimizationInsights(UUID userId, List<Bill> bills) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Analyze utility bills for optimization opportunities
        List<Bill> utilityBills = bills.stream()
            .filter(bill -> Arrays.asList("UTILITIES", "INTERNET", "PHONE", "INSURANCE")
                .contains(bill.getCategory()))
            .collect(Collectors.toList());
        
        for (Bill bill : utilityBills) {
            // Simulate optimization analysis
            if (bill.getAmount().compareTo(BigDecimal.valueOf(100)) > 0) {
                BigDecimal potentialSavings = bill.getAmount().multiply(BigDecimal.valueOf(0.15));
                
                BillInsightDto optimizationInsight = BillInsightDto.builder()
                    .type("BILL_OPTIMIZATION")
                    .priority("LOW")
                    .title("Potential Savings Available")
                    .description(String.format("You could save up to $%.2f monthly on your %s bill. " +
                        "Consider reviewing your plan or shopping for alternatives.",
                        potentialSavings, bill.getCategory().toLowerCase()))
                    .actionText("Review Options")
                    .impactAmount(potentialSavings.multiply(BigDecimal.valueOf(12)))
                    .generatedAt(LocalDateTime.now())
                    .metadata(Map.of(
                        "billId", bill.getId().toString(),
                        "category", bill.getCategory(),
                        "currentAmount", bill.getAmount(),
                        "potentialMonthlySavings", potentialSavings
                    ))
                    .build();
                
                insights.add(optimizationInsight);
            }
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generatePaymentTimingInsights(UUID userId, List<BillPayment> payments) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Analyze payment timing patterns
        Map<Integer, Long> paymentsByDayOfMonth = payments.stream()
            .filter(payment -> payment.getPaymentDate() != null)
            .collect(Collectors.groupingBy(
                payment -> payment.getPaymentDate().getDayOfMonth(),
                Collectors.counting()
            ));
        
        if (paymentsByDayOfMonth.size() > 0) {
            OptionalInt peakDay = paymentsByDayOfMonth.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(OptionalInt::of)
                .orElse(OptionalInt.empty());
            
            if (peakDay.isPresent() && paymentsByDayOfMonth.get(peakDay.getAsInt()) > 3) {
                BillInsightDto timingInsight = BillInsightDto.builder()
                    .type("PAYMENT_TIMING")
                    .priority("LOW")
                    .title("Optimize Payment Timing")
                    .description(String.format("Most of your bills are paid around the %s of the month. " +
                        "Consider spreading payments to improve cash flow.",
                        getOrdinal(peakDay.getAsInt())))
                    .actionText("Adjust Due Dates")
                    .generatedAt(LocalDateTime.now())
                    .build();
                
                insights.add(timingInsight);
            }
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateBudgetImpactInsights(UUID userId, List<Bill> bills, List<BillPayment> payments) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Calculate monthly bill impact
        BigDecimal monthlyBillAmount = bills.stream()
            .filter(Bill::isRecurring)
            .map(Bill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (monthlyBillAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Get user's income/spending data if available
            BigDecimal estimatedIncome = getEstimatedUserIncome(userId);
            
            if (estimatedIncome != null && estimatedIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal billPercentage = monthlyBillAmount
                    .divide(estimatedIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                if (billPercentage.compareTo(BigDecimal.valueOf(40)) > 0) {
                    BillInsightDto budgetInsight = BillInsightDto.builder()
                        .type("BUDGET_IMPACT")
                        .priority("HIGH")
                        .title("High Bill-to-Income Ratio")
                        .description(String.format("Your bills represent %.1f%% of your estimated income. " +
                            "Consider reviewing for cost reduction opportunities.",
                            billPercentage))
                        .actionText("Review Budget")
                        .impactAmount(monthlyBillAmount)
                        .generatedAt(LocalDateTime.now())
                        .build();
                    
                    insights.add(budgetInsight);
                }
            }
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateSeasonalSpendingInsights(UUID userId, List<BillPayment> payments) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Analyze seasonal patterns
        Map<String, BigDecimal> seasonalSpending = payments.stream()
            .collect(Collectors.groupingBy(
                payment -> getSeason(payment.getPaymentDate()),
                Collectors.reducing(BigDecimal.ZERO, BillPayment::getAmount, BigDecimal::add)
            ));
        
        if (seasonalSpending.size() >= 2) {
            String highestSeason = seasonalSpending.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
            
            BigDecimal highestAmount = seasonalSpending.get(highestSeason);
            BigDecimal avgAmount = seasonalSpending.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(seasonalSpending.size()), 2, RoundingMode.HALF_UP);
            
            if (highestAmount.compareTo(avgAmount.multiply(BigDecimal.valueOf(1.3))) > 0) {
                BillInsightDto seasonalInsight = BillInsightDto.builder()
                    .type("SEASONAL_SPENDING")
                    .priority("LOW")
                    .title("Seasonal Spending Pattern Detected")
                    .description(String.format("Your %s spending is %.1f%% higher than average. " +
                        "Plan ahead for seasonal bill increases.",
                        highestSeason.toLowerCase(),
                        highestAmount.divide(avgAmount, 4, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))))
                    .actionText("Plan Budget")
                    .generatedAt(LocalDateTime.now())
                    .build();
                
                insights.add(seasonalInsight);
            }
        }
        
        return insights;
    }
    
    private List<BillInsightDto> generateBillSharingInsights(UUID userId, List<Bill> bills) {
        List<BillInsightDto> insights = new ArrayList<>();
        
        // Find bills that could be shared (utilities, rent, etc.)
        List<Bill> shareableBills = bills.stream()
            .filter(bill -> Arrays.asList("RENT", "UTILITIES", "INTERNET", "STREAMING")
                .contains(bill.getCategory()))
            .filter(bill -> bill.getAmount().compareTo(BigDecimal.valueOf(50)) > 0)
            .collect(Collectors.toList());
        
        if (!shareableBills.isEmpty()) {
            BigDecimal potentialSavings = shareableBills.stream()
                .map(bill -> bill.getAmount().multiply(BigDecimal.valueOf(0.5)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BillInsightDto sharingInsight = BillInsightDto.builder()
                .type("BILL_SHARING")
                .priority("LOW")
                .title("Bill Sharing Opportunities")
                .description(String.format("You could save up to $%.2f monthly by sharing %d bills " +
                    "with roommates or family members.",
                    potentialSavings, shareableBills.size()))
                .actionText("Share Bills")
                .impactAmount(potentialSavings.multiply(BigDecimal.valueOf(12)))
                .generatedAt(LocalDateTime.now())
                .build();
            
            insights.add(sharingInsight);
        }
        
        return insights;
    }
    
    // Helper methods
    private int getPriorityWeight(String priority) {
        switch (priority.toUpperCase()) {
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            case "LOW": return 3;
            default: return 4;
        }
    }
    
    private long calculateDaysOverdue(Bill bill) {
        return ChronoUnit.DAYS.between(bill.getDueDate(), LocalDate.now());
    }
    
    private String getOrdinal(int day) {
        if (day >= 11 && day <= 13) return day + "th";
        switch (day % 10) {
            case 1: return day + "st";
            case 2: return day + "nd";
            case 3: return day + "rd";
            default: return day + "th";
        }
    }
    
    private BigDecimal getEstimatedUserIncome(UUID userId) {
        try {
            // This would typically call user-service or analytics-service
            return walletService.getAverageMonthlyInflow(userId);
        } catch (Exception e) {
            log.debug("Could not get estimated income for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    private String getSeason(LocalDateTime date) {
        int month = date.getMonthValue();
        if (month >= 12 || month <= 2) return "WINTER";
        if (month >= 3 && month <= 5) return "SPRING";
        if (month >= 6 && month <= 8) return "SUMMER";
        return "FALL";
    }
    
    private AutoPaySummaryDto getAutoPaySummary(String userId) {
        log.debug("Getting auto-pay summary for user: {}", userId);

        try {
            // Get all enabled auto-pay configurations for user
            List<AutoPayConfig> enabledConfigs = autoPayConfigRepository.findByUserIdAndIsEnabled(userId, true);

            if (enabledConfigs.isEmpty()) {
                return AutoPaySummaryDto.builder()
                        .totalAutoPayBills(0)
                        .monthlyAutoPayAmount(BigDecimal.ZERO)
                        .nextAutoPayDate(null)
                        .build();
            }

            // Calculate total monthly auto-pay amount (estimate)
            BigDecimal monthlyTotal = BigDecimal.ZERO;
            LocalDate nextPaymentDate = null;

            for (AutoPayConfig config : enabledConfigs) {
                // Get associated bill
                Bill bill = billRepository.findById(config.getBillId()).orElse(null);
                if (bill == null) {
                    continue;
                }

                // Calculate amount for this auto-pay
                BigDecimal paymentAmount = calculateAutoPayAmount(config, bill);
                if (paymentAmount != null) {
                    monthlyTotal = monthlyTotal.add(paymentAmount);
                }

                // Track earliest next payment date
                if (config.getNextScheduledDate() != null) {
                    if (nextPaymentDate == null || config.getNextScheduledDate().isBefore(nextPaymentDate)) {
                        nextPaymentDate = config.getNextScheduledDate();
                    }
                }
            }

            return AutoPaySummaryDto.builder()
                    .totalAutoPayBills(enabledConfigs.size())
                    .monthlyAutoPayAmount(monthlyTotal)
                    .nextAutoPayDate(nextPaymentDate)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get auto-pay summary for user: {}", userId, e);
            // Return empty summary on error
            return AutoPaySummaryDto.builder()
                    .totalAutoPayBills(0)
                    .monthlyAutoPayAmount(BigDecimal.ZERO)
                    .nextAutoPayDate(null)
                    .build();
        }
    }

    private BigDecimal calculateAutoPayAmount(AutoPayConfig config, Bill bill) {
        if (bill.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        switch (config.getAmountType().toUpperCase()) {
            case "FULL_BALANCE":
                return bill.getAmount();

            case "MINIMUM_DUE":
                return bill.getMinimumAmountDue() != null ? bill.getMinimumAmountDue() : bill.getAmount();

            case "FIXED_AMOUNT":
                BigDecimal fixedAmount = config.getFixedAmount();
                // Don't pay more than bill amount
                if (fixedAmount != null && fixedAmount.compareTo(bill.getAmount()) > 0) {
                    return bill.getAmount();
                }
                return fixedAmount != null ? fixedAmount : BigDecimal.ZERO;

            default:
                return bill.getAmount();
        }
    }
    
    private void validateAutoPayRequest(SetupAutoPayRequest request) {
        log.debug("Validating auto-pay request for bill: {}", request.getBillId());

        // Validate payment method
        if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required for auto-pay");
        }

        // Validate payment method is one of allowed values
        List<String> allowedMethods = Arrays.asList("WALLET", "CARD", "BANK_ACCOUNT");
        if (!allowedMethods.contains(request.getPaymentMethod().toUpperCase())) {
            throw new IllegalArgumentException("Invalid payment method. Allowed: " + String.join(", ", allowedMethods));
        }

        // Validate max amount if provided
        if (request.getMaxAmount() != null) {
            if (request.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Max amount must be positive");
            }

            // Max amount should be reasonable (not more than 1 million)
            if (request.getMaxAmount().compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("Max amount exceeds allowed limit");
            }
        }

        // Validate amount type
        if (request.getAmountType() == null || request.getAmountType().trim().isEmpty()) {
            throw new IllegalArgumentException("Amount type is required");
        }

        List<String> allowedAmountTypes = Arrays.asList("FULL_BALANCE", "MINIMUM_DUE", "FIXED_AMOUNT");
        if (!allowedAmountTypes.contains(request.getAmountType().toUpperCase())) {
            throw new IllegalArgumentException("Invalid amount type. Allowed: " + String.join(", ", allowedAmountTypes));
        }

        // If FIXED_AMOUNT, fixed amount must be provided
        if ("FIXED_AMOUNT".equalsIgnoreCase(request.getAmountType())) {
            if (request.getFixedAmount() == null || request.getFixedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Fixed amount is required and must be positive when amount type is FIXED_AMOUNT");
            }
        }

        // Validate payment timing
        if (request.getPaymentTiming() == null || request.getPaymentTiming().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment timing is required");
        }

        List<String> allowedTimings = Arrays.asList("ON_DUE_DATE", "BEFORE_DUE_DATE", "AFTER_DUE_DATE");
        if (!allowedTimings.contains(request.getPaymentTiming().toUpperCase())) {
            throw new IllegalArgumentException("Invalid payment timing. Allowed: " + String.join(", ", allowedTimings));
        }

        // Validate days before due if timing is BEFORE_DUE_DATE
        if ("BEFORE_DUE_DATE".equalsIgnoreCase(request.getPaymentTiming())) {
            if (request.getDaysBeforeDue() == null || request.getDaysBeforeDue() <= 0) {
                throw new IllegalArgumentException("Days before due must be positive when timing is BEFORE_DUE_DATE");
            }

            if (request.getDaysBeforeDue() > 30) {
                throw new IllegalArgumentException("Days before due cannot exceed 30");
            }
        }

        log.debug("Auto-pay request validation successful");
    }
    
    private void scheduleNextAutoPayment(AutoPayConfig config, Bill bill) {
        log.debug("Scheduling next auto-payment for config: {}, bill: {}", config.getId(), bill.getId());

        try {
            if (!config.isEnabled()) {
                log.debug("Auto-pay config is disabled, skipping scheduling");
                return;
            }

            if (bill.getDueDate() == null) {
                log.warn("Bill has no due date, cannot schedule auto-payment");
                return;
            }

            LocalDate nextPaymentDate;

            // Calculate next payment date based on timing configuration
            switch (config.getPaymentTiming().toUpperCase()) {
                case "ON_DUE_DATE":
                    nextPaymentDate = bill.getDueDate();
                    break;

                case "BEFORE_DUE_DATE":
                    int daysBefore = config.getDaysBeforeDue() != null ? config.getDaysBeforeDue() : 3;
                    nextPaymentDate = bill.getDueDate().minusDays(daysBefore);
                    break;

                case "AFTER_DUE_DATE":
                    // This would be unusual but support it
                    int daysAfter = config.getDaysBeforeDue() != null ? config.getDaysBeforeDue() : 0;
                    nextPaymentDate = bill.getDueDate().plusDays(daysAfter);
                    break;

                default:
                    log.warn("Unknown payment timing: {}, defaulting to ON_DUE_DATE", config.getPaymentTiming());
                    nextPaymentDate = bill.getDueDate();
            }

            // Don't schedule if date is in the past
            if (nextPaymentDate.isBefore(LocalDate.now())) {
                log.warn("Calculated next payment date is in the past: {}, using today", nextPaymentDate);
                nextPaymentDate = LocalDate.now();
            }

            config.setNextScheduledDate(nextPaymentDate);
            autoPayConfigRepository.save(config);

            log.info("Next auto-payment scheduled for: {} (bill due: {})",
                    nextPaymentDate, bill.getDueDate());

        } catch (Exception e) {
            log.error("Failed to schedule next auto-payment for config: {}", config.getId(), e);
            // Don't propagate exception - scheduling failure shouldn't break auto-pay setup
        }
    }
    
    private AutoPayConfigDto toAutoPayConfigDto(AutoPayConfig config, Bill bill) {
        return AutoPayConfigDto.builder()
            .id(config.getId())
            .billId(config.getBillId())
            .billerName(bill.getBillerName())
            .paymentMethod(config.getPaymentMethod())
            .maxAmount(config.getMaxAmount())
            .enabled(config.isEnabled())
            .build();
    }
    
    private void validateSharingRequest(ShareBillRequest request, Bill bill) {
        log.debug("Validating bill sharing request for bill: {}", bill.getId());

        // Validate bill is eligible for sharing
        if (bill.getStatus() == null || !bill.getStatus().equalsIgnoreCase("UNPAID")) {
            throw new IllegalStateException("Only unpaid bills can be shared");
        }

        // Validate bill amount
        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Bill has invalid amount");
        }

        // Validate split type
        if (request.getSplitType() == null || request.getSplitType().trim().isEmpty()) {
            throw new IllegalArgumentException("Split type is required");
        }

        List<String> allowedSplitTypes = Arrays.asList("EQUAL", "PERCENTAGE", "CUSTOM");
        if (!allowedSplitTypes.contains(request.getSplitType().toUpperCase())) {
            throw new IllegalArgumentException("Invalid split type. Allowed: " + String.join(", ", allowedSplitTypes));
        }

        // Validate participants
        if (request.getParticipants() == null || request.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }

        if (request.getParticipants().size() > 50) {
            throw new IllegalArgumentException("Maximum 50 participants allowed");
        }

        // Validate participant details
        for (ShareBillParticipant participant : request.getParticipants()) {
            if (participant.getEmail() == null || participant.getEmail().trim().isEmpty()) {
                if (participant.getUserId() == null || participant.getUserId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Each participant must have either email or userId");
                }
            }

            // Validate email format if provided
            if (participant.getEmail() != null && !participant.getEmail().trim().isEmpty()) {
                String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
                if (!participant.getEmail().matches(emailRegex)) {
                    throw new IllegalArgumentException("Invalid email format: " + participant.getEmail());
                }
            }
        }

        // Additional validation based on split type
        switch (request.getSplitType().toUpperCase()) {
            case "PERCENTAGE":
                validatePercentageSplit(request.getParticipants());
                break;

            case "CUSTOM":
                validateCustomSplit(request.getParticipants(), bill.getAmount());
                break;

            case "EQUAL":
                // No additional validation needed for equal split
                break;
        }

        // Validate due date if provided
        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }

        log.debug("Bill sharing request validation successful");
    }

    private void validatePercentageSplit(List<ShareBillParticipant> participants) {
        BigDecimal totalPercentage = BigDecimal.ZERO;

        for (ShareBillParticipant participant : participants) {
            if (participant.getPercentage() == null || participant.getPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Percentage must be provided and positive for each participant in PERCENTAGE split");
            }

            if (participant.getPercentage().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Individual percentage cannot exceed 100%");
            }

            totalPercentage = totalPercentage.add(participant.getPercentage());
        }

        // Allow small rounding differences (0.01%)
        if (totalPercentage.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException("Total percentage must equal 100% (currently: " + totalPercentage + "%)");
        }
    }

    private void validateCustomSplit(List<ShareBillParticipant> participants, BigDecimal totalAmount) {
        BigDecimal totalCustomAmount = BigDecimal.ZERO;

        for (ShareBillParticipant participant : participants) {
            if (participant.getAmount() == null || participant.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Custom amount must be provided and positive for each participant in CUSTOM split");
            }

            totalCustomAmount = totalCustomAmount.add(participant.getAmount());
        }

        // Allow small rounding differences (0.01)
        if (totalCustomAmount.subtract(totalAmount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException(
                    String.format("Sum of custom amounts (%.2f) must equal bill amount (%.2f)",
                            totalCustomAmount, totalAmount));
        }
    }
    
    private List<BillSharingParticipant> createSharingParticipants(UUID sharingId, 
            List<ShareBillParticipant> participants, String splitType, BigDecimal totalAmount) {
        try {
            log.debug("Creating sharing participants for sharing: {}, splitType: {}, totalAmount: {}", 
                sharingId, splitType, totalAmount);
            
            if (participants == null || participants.isEmpty()) {
                log.warn("No participants provided for bill sharing: {}", sharingId);
                return Collections.emptyList();
            }
            
            List<BillSharingParticipant> sharingParticipants = new ArrayList<>();
            
            switch (splitType.toUpperCase()) {
                case "EQUAL":
                    sharingParticipants = createEqualSplitParticipants(sharingId, participants, totalAmount);
                    break;
                    
                case "PERCENTAGE":
                    sharingParticipants = createPercentageSplitParticipants(sharingId, participants, totalAmount);
                    break;
                    
                case "CUSTOM":
                    sharingParticipants = createCustomSplitParticipants(sharingId, participants, totalAmount);
                    break;
                    
                case "BY_INCOME":
                    sharingParticipants = createIncomeBasedSplitParticipants(sharingId, participants, totalAmount);
                    break;
                    
                default:
                    log.warn("Unknown split type: {}, defaulting to equal split", splitType);
                    sharingParticipants = createEqualSplitParticipants(sharingId, participants, totalAmount);
                    break;
            }
            
            // Validate total amounts match
            validateParticipantAmounts(sharingParticipants, totalAmount);
            
            // Save participants to database
            List<BillSharingParticipant> savedParticipants = new ArrayList<>();
            for (BillSharingParticipant participant : sharingParticipants) {
                BillSharingParticipant saved = sharingParticipantRepository.save(participant);
                savedParticipants.add(saved);
            }
            
            log.info("Created {} sharing participants for sharing: {}", savedParticipants.size(), sharingId);
            return savedParticipants;
            
        } catch (Exception e) {
            log.error("Failed to create sharing participants for sharing: {}", sharingId, e);
            return Collections.emptyList();
        }
    }
    
    private List<BillSharingParticipant> createEqualSplitParticipants(UUID sharingId, 
            List<ShareBillParticipant> participants, BigDecimal totalAmount) {
        
        List<BillSharingParticipant> sharingParticipants = new ArrayList<>();
        
        BigDecimal equalShare = totalAmount.divide(
            BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
        
        // Handle rounding by adjusting the last participant
        BigDecimal runningTotal = BigDecimal.ZERO;
        
        for (int i = 0; i < participants.size(); i++) {
            ShareBillParticipant participant = participants.get(i);
            BigDecimal participantAmount;
            
            if (i == participants.size() - 1) {
                // Last participant gets the remaining amount to handle rounding
                participantAmount = totalAmount.subtract(runningTotal);
            } else {
                participantAmount = equalShare;
                runningTotal = runningTotal.add(equalShare);
            }
            
            BillSharingParticipant sharingParticipant = BillSharingParticipant.builder()
                .sharingId(sharingId)
                .participantId(participant.getUserId())
                .participantEmail(participant.getEmail())
                .participantName(participant.getName())
                .owedAmount(participantAmount)
                .paidAmount(BigDecimal.ZERO)
                .status("PENDING")
                .splitPercentage(equalShare.divide(totalAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)))
                .invitedAt(LocalDateTime.now())
                .paymentDueDate(LocalDate.now().plusDays(7)) // 7 days to pay
                .remindersSent(0)
                .notes("Equal split: $" + participantAmount)
                .build();
            
            sharingParticipants.add(sharingParticipant);
        }
        
        return sharingParticipants;
    }
    
    private List<BillSharingParticipant> createPercentageSplitParticipants(UUID sharingId, 
            List<ShareBillParticipant> participants, BigDecimal totalAmount) {
        
        List<BillSharingParticipant> sharingParticipants = new ArrayList<>();
        
        // Validate percentages sum to 100
        BigDecimal totalPercentage = participants.stream()
            .map(p -> p.getPercentage() != null ? p.getPercentage() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessException("Percentages must sum to 100%. Current total: " + totalPercentage + "%");
        }
        
        for (ShareBillParticipant participant : participants) {
            BigDecimal percentage = participant.getPercentage();
            BigDecimal participantAmount = totalAmount
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            BillSharingParticipant sharingParticipant = BillSharingParticipant.builder()
                .sharingId(sharingId)
                .participantId(participant.getUserId())
                .participantEmail(participant.getEmail())
                .participantName(participant.getName())
                .owedAmount(participantAmount)
                .paidAmount(BigDecimal.ZERO)
                .status("PENDING")
                .splitPercentage(percentage)
                .invitedAt(LocalDateTime.now())
                .paymentDueDate(LocalDate.now().plusDays(7))
                .remindersSent(0)
                .notes("Percentage split: " + percentage + "% = $" + participantAmount)
                .build();
            
            sharingParticipants.add(sharingParticipant);
        }
        
        return sharingParticipants;
    }
    
    private List<BillSharingParticipant> createCustomSplitParticipants(UUID sharingId, 
            List<ShareBillParticipant> participants, BigDecimal totalAmount) {
        
        List<BillSharingParticipant> sharingParticipants = new ArrayList<>();
        
        // Validate custom amounts sum to total
        BigDecimal totalCustomAmounts = participants.stream()
            .map(p -> p.getCustomAmount() != null ? p.getCustomAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalCustomAmounts.compareTo(totalAmount) != 0) {
            throw new BusinessException("Custom amounts must sum to total bill amount. " +
                "Expected: $" + totalAmount + ", Got: $" + totalCustomAmounts);
        }
        
        for (ShareBillParticipant participant : participants) {
            BigDecimal customAmount = participant.getCustomAmount();
            BigDecimal percentage = customAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            BillSharingParticipant sharingParticipant = BillSharingParticipant.builder()
                .sharingId(sharingId)
                .participantId(participant.getUserId())
                .participantEmail(participant.getEmail())
                .participantName(participant.getName())
                .owedAmount(customAmount)
                .paidAmount(BigDecimal.ZERO)
                .status("PENDING")
                .splitPercentage(percentage)
                .invitedAt(LocalDateTime.now())
                .paymentDueDate(LocalDate.now().plusDays(7))
                .remindersSent(0)
                .notes("Custom amount: $" + customAmount)
                .build();
            
            sharingParticipants.add(sharingParticipant);
        }
        
        return sharingParticipants;
    }
    
    private List<BillSharingParticipant> createIncomeBasedSplitParticipants(UUID sharingId, 
            List<ShareBillParticipant> participants, BigDecimal totalAmount) {
        
        List<BillSharingParticipant> sharingParticipants = new ArrayList<>();
        
        // Get income data for each participant
        Map<UUID, BigDecimal> participantIncomes = new HashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        
        for (ShareBillParticipant participant : participants) {
            BigDecimal income = getParticipantIncome(participant.getUserId());
            if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
                // Default income if not available
                income = BigDecimal.valueOf(50000); // Default annual income
                log.warn("Using default income for participant: {}", participant.getUserId());
            }
            
            participantIncomes.put(participant.getUserId(), income);
            totalIncome = totalIncome.add(income);
        }
        
        // Calculate amounts based on income ratio
        BigDecimal runningTotal = BigDecimal.ZERO;
        
        for (int i = 0; i < participants.size(); i++) {
            ShareBillParticipant participant = participants.get(i);
            BigDecimal participantIncome = participantIncomes.get(participant.getUserId());
            
            BigDecimal participantAmount;
            BigDecimal percentage;
            
            if (i == participants.size() - 1) {
                // Last participant gets remaining amount to handle rounding
                participantAmount = totalAmount.subtract(runningTotal);
                percentage = participantAmount.divide(totalAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            } else {
                percentage = participantIncome.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                participantAmount = totalAmount.multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                runningTotal = runningTotal.add(participantAmount);
            }
            
            BillSharingParticipant sharingParticipant = BillSharingParticipant.builder()
                .sharingId(sharingId)
                .participantId(participant.getUserId())
                .participantEmail(participant.getEmail())
                .participantName(participant.getName())
                .owedAmount(participantAmount)
                .paidAmount(BigDecimal.ZERO)
                .status("PENDING")
                .splitPercentage(percentage)
                .invitedAt(LocalDateTime.now())
                .paymentDueDate(LocalDate.now().plusDays(7))
                .remindersSent(0)
                .notes("Income-based split: " + percentage.setScale(1, RoundingMode.HALF_UP) + 
                       "% = $" + participantAmount)
                .build();
            
            sharingParticipants.add(sharingParticipant);
        }
        
        return sharingParticipants;
    }
    
    private void validateParticipantAmounts(List<BillSharingParticipant> participants, BigDecimal expectedTotal) {
        BigDecimal actualTotal = participants.stream()
            .map(BillSharingParticipant::getOwedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal difference = expectedTotal.subtract(actualTotal).abs();
        
        // Allow for small rounding differences (up to 1 cent)
        if (difference.compareTo(BigDecimal.valueOf(0.01)) > 0) {
            throw new BusinessException("Participant amounts don't sum to expected total. " +
                "Expected: $" + expectedTotal + ", Actual: $" + actualTotal);
        }
    }
    
    private BigDecimal getParticipantIncome(UUID participantId) {
        try {
            // In production, this would call user-service or analytics-service
            return userService.getEstimatedAnnualIncome(participantId);
        } catch (Exception e) {
            log.debug("Could not get income for participant {}: {}", participantId, e.getMessage());
            return null;
        }
    }
    
    private BillSharingDto toBillSharingDto(BillSharing sharing, 
            List<BillSharingParticipant> participants, Bill bill) {
        // Convert to DTO
        return BillSharingDto.builder()
            .id(sharing.getId())
            .billId(sharing.getBillId())
            .totalAmount(sharing.getTotalAmount())
            .splitType(sharing.getSplitType())
            .status(sharing.getStatus())
            .build();
    }
    
    private void generatePaymentInsights(UUID userId, BillPayment payment, Bill bill) {
        // Generate AI insights after payment
    }
    
    private Map<String, BigDecimal> calculateCategorySpending(List<BillPayment> payments) {
        try {
            log.debug("Calculating category spending from {} payments", payments.size());
            
            if (payments == null || payments.isEmpty()) {
                return Collections.emptyMap();
            }
            
            // Group payments by bill category and sum amounts
            Map<String, BigDecimal> categorySpending = new LinkedHashMap<>();
            
            for (BillPayment payment : payments) {
                try {
                    // Get bill details to determine category
                    Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
                    
                    String category;
                    if (bill != null && bill.getCategory() != null && !bill.getCategory().trim().isEmpty()) {
                        category = normalizeCategory(bill.getCategory());
                    } else {
                        // Try to infer category from biller name or description
                        category = inferCategoryFromPayment(payment, bill);
                    }
                    
                    // Add to category total
                    categorySpending.merge(category, payment.getAmount(), BigDecimal::add);
                    
                } catch (Exception e) {
                    log.warn("Error processing payment {} for category spending: {}", payment.getId(), e.getMessage());
                    // Add to "OTHER" category if processing fails
                    categorySpending.merge("OTHER", payment.getAmount(), BigDecimal::add);
                }
            }
            
            // Sort by spending amount (descending)
            Map<String, BigDecimal> sortedCategorySpending = categorySpending.entrySet()
                .stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
            
            // Add percentage information for insights
            BigDecimal totalSpending = sortedCategorySpending.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Map<String, BigDecimal> categorySpendingWithInsights = new LinkedHashMap<>();
            
            for (Map.Entry<String, BigDecimal> entry : sortedCategorySpending.entrySet()) {
                String category = entry.getKey();
                BigDecimal amount = entry.getValue();
                
                // Store both absolute amount and percentage for UI
                categorySpendingWithInsights.put(category, amount);
                
                if (totalSpending.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal percentage = amount.divide(totalSpending, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    
                    // Store percentage as metadata (could be used by UI)
                    categorySpendingWithInsights.put(category + "_PERCENTAGE", percentage);
                }
            }
            
            log.debug("Calculated spending across {} categories, total: ${}",
                sortedCategorySpending.size(), totalSpending);
            
            return sortedCategorySpending; // Return main data without metadata for now
            
        } catch (Exception e) {
            log.error("Failed to calculate category spending", e);
            return Collections.emptyMap();
        }
    }
    
    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "OTHER";
        }
        
        String normalizedCategory = category.trim().toUpperCase();
        
        // Map similar categories together
        switch (normalizedCategory) {
            case "ELECTRIC":
            case "ELECTRICITY":
            case "GAS":
            case "WATER":
            case "SEWER":
            case "GARBAGE":
            case "WASTE":
                return "UTILITIES";
                
            case "RENT":
            case "MORTGAGE":
            case "HOUSING":
                return "HOUSING";
                
            case "PHONE":
            case "MOBILE":
            case "CELL":
            case "WIRELESS":
                return "PHONE";
                
            case "INTERNET":
            case "CABLE":
            case "TV":
            case "BROADBAND":
                return "INTERNET_TV";
                
            case "NETFLIX":
            case "HULU":
            case "SPOTIFY":
            case "STREAMING":
            case "SUBSCRIPTION":
                return "SUBSCRIPTIONS";
                
            case "CAR":
            case "AUTO":
            case "VEHICLE":
            case "INSURANCE":
                return "INSURANCE";
                
            case "CREDIT_CARD":
            case "LOAN":
            case "DEBT":
                return "DEBT_PAYMENTS";
                
            case "MEDICAL":
            case "HEALTH":
            case "DENTAL":
            case "HEALTHCARE":
                return "HEALTHCARE";
                
            case "EDUCATION":
            case "SCHOOL":
            case "TUITION":
                return "EDUCATION";
                
            case "GYM":
            case "FITNESS":
            case "MEMBERSHIP":
                return "FITNESS";
                
            default:
                return normalizedCategory;
        }
    }
    
    private String inferCategoryFromPayment(BillPayment payment, Bill bill) {
        // Try to infer category from biller name, description, or amount patterns
        
        String billerName = "";
        String description = payment.getDescription() != null ? payment.getDescription().toLowerCase() : "";
        
        if (bill != null && bill.getBillerName() != null) {
            billerName = bill.getBillerName().toLowerCase();
        }
        
        String combinedText = (billerName + " " + description).toLowerCase();
        
        // Electric utilities
        if (combinedText.matches(".*(electric|power|edison|pge|utility|energy).*")) {
            return "UTILITIES";
        }
        
        // Rent/Housing
        if (combinedText.matches(".*(rent|mortgage|housing|property|landlord).*")) {
            return "HOUSING";
        }
        
        // Internet/Cable
        if (combinedText.matches(".*(internet|cable|comcast|verizon|att|broadband|wifi).*")) {
            return "INTERNET_TV";
        }
        
        // Phone
        if (combinedText.matches(".*(phone|mobile|cell|wireless|tmobile|sprint|verizon).*")) {
            return "PHONE";
        }
        
        // Streaming/Subscriptions
        if (combinedText.matches(".*(netflix|hulu|spotify|amazon|subscription|streaming).*")) {
            return "SUBSCRIPTIONS";
        }
        
        // Insurance
        if (combinedText.matches(".*(insurance|geico|progressive|state farm|allstate).*")) {
            return "INSURANCE";
        }
        
        // Credit Cards/Loans
        if (combinedText.matches(".*(credit|loan|debt|payment|bank|visa|mastercard).*")) {
            return "DEBT_PAYMENTS";
        }
        
        // Medical/Healthcare
        if (combinedText.matches(".*(medical|health|doctor|dental|hospital|healthcare).*")) {
            return "HEALTHCARE";
        }
        
        // Education
        if (combinedText.matches(".*(school|tuition|education|student|university|college).*")) {
            return "EDUCATION";
        }
        
        // Fitness
        if (combinedText.matches(".*(gym|fitness|workout|health club|sports).*")) {
            return "FITNESS";
        }
        
        // Water/Gas utilities
        if (combinedText.matches(".*(water|gas|sewer|waste|garbage|sanitation).*")) {
            return "UTILITIES";
        }
        
        // Transportation
        if (combinedText.matches(".*(uber|lyft|taxi|transit|metro|bus|parking).*")) {
            return "TRANSPORTATION";
        }
        
        // Default to OTHER if no pattern matches
        return "OTHER";
    }
    
    private List<MonthlySpendingDto> calculateMonthlyTrends(List<BillPayment> payments, int months) {
        try {
            log.debug("Calculating monthly spending trends from {} payments for {} months", payments.size(), months);
            
            if (payments == null || payments.isEmpty()) {
                return generateEmptyMonthlyTrends(months);
            }
            
            // Group payments by year-month
            Map<String, List<BillPayment>> paymentsByMonth = payments.stream()
                .filter(payment -> payment.getPaymentDate() != null)
                .collect(Collectors.groupingBy(
                    payment -> payment.getPaymentDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));
            
            List<MonthlySpendingDto> monthlyTrends = new ArrayList<>();
            LocalDate currentDate = LocalDate.now().withDayOfMonth(1); // First day of current month
            
            // Generate trends for requested number of months (going backwards)
            for (int i = months - 1; i >= 0; i--) {
                LocalDate monthDate = currentDate.minusMonths(i);
                String monthKey = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                
                List<BillPayment> monthPayments = paymentsByMonth.getOrDefault(monthKey, Collections.emptyList());
                
                MonthlySpendingDto monthlySpending = calculateMonthlySpendingDetails(monthDate, monthPayments);
                monthlyTrends.add(monthlySpending);
            }
            
            // Calculate trends and insights
            addTrendAnalysisToMonthlyData(monthlyTrends);
            
            log.debug("Calculated monthly trends for {} months with data", monthlyTrends.size());
            return monthlyTrends;
            
        } catch (Exception e) {
            log.error("Failed to calculate monthly spending trends", e);
            return generateEmptyMonthlyTrends(months);
        }
    }
    
    private MonthlySpendingDto calculateMonthlySpendingDetails(LocalDate month, List<BillPayment> payments) {
        String monthName = month.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        String monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        if (payments.isEmpty()) {
            return MonthlySpendingDto.builder()
                .month(monthName)
                .monthKey(monthKey)
                .totalSpent(BigDecimal.ZERO)
                .paymentCount(0)
                .avgPaymentAmount(BigDecimal.ZERO)
                .categoryBreakdown(Collections.emptyMap())
                .topCategory("NONE")
                .isCurrentMonth(month.equals(LocalDate.now().withDayOfMonth(1)))
                .build();
        }
        
        // Calculate basic metrics
        BigDecimal totalSpent = payments.stream()
            .map(BillPayment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int paymentCount = payments.size();
        
        BigDecimal avgPaymentAmount = paymentCount > 0 ? 
            totalSpent.divide(BigDecimal.valueOf(paymentCount), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        // Calculate category breakdown for this month
        Map<String, BigDecimal> categoryBreakdown = calculateCategorySpending(payments);
        
        String topCategory = categoryBreakdown.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("OTHER");
        
        // Calculate additional insights
        Map<String, Object> insights = calculateMonthlyInsights(payments, totalSpent);
        
        return MonthlySpendingDto.builder()
            .month(monthName)
            .monthKey(monthKey)
            .totalSpent(totalSpent)
            .paymentCount(paymentCount)
            .avgPaymentAmount(avgPaymentAmount)
            .categoryBreakdown(categoryBreakdown)
            .topCategory(topCategory)
            .isCurrentMonth(month.equals(LocalDate.now().withDayOfMonth(1)))
            .insights(insights)
            .build();
    }
    
    private Map<String, Object> calculateMonthlyInsights(List<BillPayment> payments, BigDecimal totalSpent) {
        Map<String, Object> insights = new HashMap<>();
        
        // Calculate on-time vs late payments
        long onTimePayments = payments.stream()
            .filter(payment -> {
                try {
                    Bill bill = billRepository.findById(payment.getBillId()).orElse(null);
                    return bill != null && payment.getPaymentDate().toLocalDate().isBefore(bill.getDueDate().plusDays(1));
                } catch (Exception e) {
                    return true; // Assume on-time if we can't determine
                }
            })
            .count();
        
        long latePayments = payments.size() - onTimePayments;
        
        insights.put("onTimePayments", onTimePayments);
        insights.put("latePayments", latePayments);
        insights.put("onTimePercentage", payments.size() > 0 ? 
            (double) onTimePayments / payments.size() * 100 : 100.0);
        
        // Find largest payment
        Optional<BillPayment> largestPayment = payments.stream()
            .max(Comparator.comparing(BillPayment::getAmount));
        
        if (largestPayment.isPresent()) {
            insights.put("largestPaymentAmount", largestPayment.get().getAmount());
            try {
                Bill bill = billRepository.findById(largestPayment.get().getBillId()).orElse(null);
                insights.put("largestPaymentBiller", bill != null ? bill.getBillerName() : "Unknown");
            } catch (Exception e) {
                insights.put("largestPaymentBiller", "Unknown");
            }
        }
        
        // Calculate payment method distribution
        Map<String, Long> paymentMethods = payments.stream()
            .collect(Collectors.groupingBy(
                payment -> payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "UNKNOWN",
                Collectors.counting()
            ));
        
        insights.put("paymentMethods", paymentMethods);
        
        // Calculate average days between payments
        if (payments.size() > 1) {
            List<LocalDateTime> paymentDates = payments.stream()
                .map(BillPayment::getPaymentDate)
                .sorted()
                .collect(Collectors.toList());
            
            double avgDaysBetween = 0;
            for (int i = 1; i < paymentDates.size(); i++) {
                avgDaysBetween += ChronoUnit.DAYS.between(paymentDates.get(i-1), paymentDates.get(i));
            }
            avgDaysBetween = avgDaysBetween / (paymentDates.size() - 1);
            
            insights.put("avgDaysBetweenPayments", Math.round(avgDaysBetween * 10.0) / 10.0);
        }
        
        return insights;
    }
    
    private void addTrendAnalysisToMonthlyData(List<MonthlySpendingDto> monthlyTrends) {
        if (monthlyTrends.size() < 2) {
            return; // Need at least 2 months for trend analysis
        }
        
        for (int i = 1; i < monthlyTrends.size(); i++) {
            MonthlySpendingDto currentMonth = monthlyTrends.get(i);
            MonthlySpendingDto previousMonth = monthlyTrends.get(i - 1);
            
            // Calculate month-over-month change
            BigDecimal currentTotal = currentMonth.getTotalSpent();
            BigDecimal previousTotal = previousMonth.getTotalSpent();
            
            if (previousTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal changeAmount = currentTotal.subtract(previousTotal);
                BigDecimal changePercent = changeAmount
                    .divide(previousTotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                Map<String, Object> trends = new HashMap<>();
                trends.put("changeAmount", changeAmount);
                trends.put("changePercent", changePercent);
                trends.put("trend", changeAmount.compareTo(BigDecimal.ZERO) > 0 ? "INCREASE" : 
                                 changeAmount.compareTo(BigDecimal.ZERO) < 0 ? "DECREASE" : "STABLE");
                
                // Add trend significance
                if (changePercent.abs().compareTo(BigDecimal.valueOf(20)) > 0) {
                    trends.put("significance", "HIGH");
                } else if (changePercent.abs().compareTo(BigDecimal.valueOf(10)) > 0) {
                    trends.put("significance", "MEDIUM");
                } else {
                    trends.put("significance", "LOW");
                }
                
                currentMonth.setTrends(trends);
            }
            
            // Calculate rolling averages
            if (i >= 2) { // Need at least 3 months for 3-month average
                BigDecimal threeMonthAvg = monthlyTrends.subList(i - 2, i + 1).stream()
                    .map(MonthlySpendingDto::getTotalSpent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                
                Map<String, Object> trends = currentMonth.getTrends();
                if (trends == null) {
                    trends = new HashMap<>();
                    currentMonth.setTrends(trends);
                }
                trends.put("threeMonthAvg", threeMonthAvg);
            }
        }
        
        // Add seasonal analysis
        addSeasonalAnalysis(monthlyTrends);
    }
    
    private void addSeasonalAnalysis(List<MonthlySpendingDto> monthlyTrends) {
        // Group by season and calculate averages
        Map<String, List<MonthlySpendingDto>> seasonalData = monthlyTrends.stream()
            .collect(Collectors.groupingBy(month -> {
                LocalDate date = LocalDate.parse(month.getMonthKey() + "-01");
                return getSeason(date.atStartOfDay());
            }));
        
        Map<String, BigDecimal> seasonalAverages = new HashMap<>();
        for (Map.Entry<String, List<MonthlySpendingDto>> entry : seasonalData.entrySet()) {
            BigDecimal seasonalAvg = entry.getValue().stream()
                .map(MonthlySpendingDto::getTotalSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(entry.getValue().size()), 2, RoundingMode.HALF_UP);
            
            seasonalAverages.put(entry.getKey(), seasonalAvg);
        }
        
        // Add seasonal context to each month
        for (MonthlySpendingDto month : monthlyTrends) {
            LocalDate date = LocalDate.parse(month.getMonthKey() + "-01");
            String season = getSeason(date.atStartOfDay());
            BigDecimal seasonalAvg = seasonalAverages.get(season);
            
            if (seasonalAvg != null) {
                Map<String, Object> trends = month.getTrends();
                if (trends == null) {
                    trends = new HashMap<>();
                    month.setTrends(trends);
                }
                
                trends.put("season", season);
                trends.put("seasonalAvg", seasonalAvg);
                
                if (month.getTotalSpent().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal seasonalVariance = month.getTotalSpent()
                        .subtract(seasonalAvg)
                        .divide(seasonalAvg, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    
                    trends.put("seasonalVariance", seasonalVariance);
                }
            }
        }
    }
    
    private List<MonthlySpendingDto> generateEmptyMonthlyTrends(int months) {
        List<MonthlySpendingDto> emptyTrends = new ArrayList<>();
        LocalDate currentDate = LocalDate.now().withDayOfMonth(1);
        
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthDate = currentDate.minusMonths(i);
            String monthName = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            String monthKey = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            MonthlySpendingDto emptyMonth = MonthlySpendingDto.builder()
                .month(monthName)
                .monthKey(monthKey)
                .totalSpent(BigDecimal.ZERO)
                .paymentCount(0)
                .avgPaymentAmount(BigDecimal.ZERO)
                .categoryBreakdown(Collections.emptyMap())
                .topCategory("NONE")
                .isCurrentMonth(monthDate.equals(LocalDate.now().withDayOfMonth(1)))
                .build();
            
            emptyTrends.add(emptyMonth);
        }
        
        return emptyTrends;
    }
    
    private List<BillOptimizationDto> identifyOptimizationOpportunities(UUID userId) {
        // Identify bill optimization opportunities
        return Collections.emptyList(); // Placeholder
    }
    
    private BigDecimal calculateUpcomingBillsAmount(UUID userId) {
        // Calculate upcoming bills amount
        return BigDecimal.ZERO; // Placeholder
    }
    
    private String findMostExpensiveCategory(Map<String, BigDecimal> categorySpending) {
        return categorySpending.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");
    }
    
    private BigDecimal calculateSavingsOpportunity(List<BillOptimizationDto> optimizations) {
        return optimizations.stream()
            .map(BillOptimizationDto::getPotentialSavings)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateOnTimePaymentRate(List<BillPayment> payments) {
        // Calculate on-time payment rate
        return new BigDecimal("95.5"); // Placeholder
    }
    
    private NegotiationContext analyzeNegotiationContext(UUID userId, Bill bill) {
        // Analyze context for bill negotiation
        return NegotiationContext.builder()
            .userLoyaltyYears(2)
            .paymentHistory(PaymentHistoryRating.EXCELLENT)
            .currentMarketRates(bill.getAmount().multiply(new BigDecimal("0.9")))
            .build();
    }
    
    private BillNegotiationDto toBillNegotiationDto(BillNegotiation negotiation, Bill bill) {
        return BillNegotiationDto.builder()
            .id(negotiation.getId())
            .billId(negotiation.getBillId())
            .billerName(bill.getBillerName())
            .originalAmount(negotiation.getOriginalAmount())
            .negotiatedAmount(negotiation.getNegotiatedAmount())
            .savingsAmount(negotiation.getSavingsAmount())
            .status(negotiation.getStatus())
            .startedAt(negotiation.getStartedAt())
            .completedAt(negotiation.getCompletedAt())
            .build();
    }

    // ============== Missing Service Methods for Controller Endpoints ==============

    /**
     * Get all billers with optional filtering
     */
    @Transactional(readOnly = true)
    public Page<BillerResponse> getAllBillers(String category, String country, Pageable pageable) {
        log.debug("Fetching billers - category: {}, country: {}", category, country);

        Page<Biller> billers;
        if (category != null && country != null) {
            billers = billerRepository.findByCategoryAndCountry(category, country, pageable);
        } else if (category != null) {
            billers = billerRepository.findByCategory(category, pageable);
        } else if (country != null) {
            billers = billerRepository.findByCountry(country, pageable);
        } else {
            billers = billerRepository.findAll(pageable);
        }

        return billers.map(billerMapper::toResponse);
    }

    /**
     * Get biller details by ID
     */
    @Transactional(readOnly = true)
    public BillerResponse getBillerDetails(String billerId) {
        log.debug("Fetching biller details for ID: {}", billerId);

        UUID billerUuid = UUID.fromString(billerId);
        Biller biller = billerRepository.findById(billerUuid)
                .orElseThrow(() -> new BillerNotFoundException("Biller not found with ID: " + billerId));

        return billerMapper.toResponse(biller);
    }

    /**
     * Search billers by query
     */
    @Transactional(readOnly = true)
    public List<BillerResponse> searchBillers(String query, int limit) {
        log.debug("Searching billers with query: {} (limit: {})", query, limit);

        List<Biller> billers = billerRepository.searchByNameOrCategory(query, limit);
        return billerMapper.toResponseList(billers);
    }

    /**
     * Add a bill account for a user
     */
    public BillAccountResponse addBillAccount(String userId, AddBillAccountRequest request) {
        log.info("Adding bill account for user: {} - biller: {}", userId, request.getBillerId());

        // Validate biller exists
        Biller biller = billerRepository.findById(request.getBillerId())
                .orElseThrow(() -> new BillerNotFoundException("Biller not found with ID: " + request.getBillerId()));

        // Check if account already exists
        Optional<BillerConnection> existing = billerConnectionRepository
                .findByUserIdAndBillerIdAndAccountNumber(userId, request.getBillerId(), request.getAccountNumber());

        if (existing.isPresent()) {
            throw new DuplicatePaymentException("Bill account already exists for this biller and account number");
        }

        // Create new account
        BillerConnection billerConnection = billAccountMapper.toEntity(request);
        billerConnection.setUserId(userId);
        billerConnection.setBiller(biller);
        billerConnection.setCreatedAt(LocalDateTime.now());
        billerConnection.setUpdatedAt(LocalDateTime.now());

        // If set as default, unset other defaults for this user
        if (Boolean.TRUE.equals(request.getSetAsDefault())) {
            billerConnectionRepository.unsetDefaultsForUser(userId);
        }

        BillerConnection saved = billerConnectionRepository.save(billerConnection);

        log.info("Bill account created successfully: {}", saved.getId());
        return billAccountMapper.toResponse(saved);
    }

    /**
     * Get all bill accounts for a user
     */
    @Transactional(readOnly = true)
    public List<BillAccountResponse> getUserBillAccounts(String userId) {
        log.debug("Fetching bill accounts for user: {}", userId);

        List<BillerConnection> connections = billerConnectionRepository.findByUserIdAndIsActive(userId, true);
        return billAccountMapper.toResponseList(connections);
    }

    /**
     * Update a bill account
     */
    public BillAccountResponse updateBillAccount(String userId, UUID accountId, UpdateBillAccountRequest request) {
        log.info("Updating bill account {} for user: {}", accountId, userId);

        BillerConnection connection = billerConnectionRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BillNotFoundException("Bill account not found or does not belong to user"));

        // Update fields
        if (request.getAccountName() != null) {
            connection.setAccountName(request.getAccountName());
        }
        if (request.getNotes() != null) {
            connection.setNotes(request.getNotes());
        }
        if (request.getIsActive() != null) {
            connection.setIsActive(request.getIsActive());
        }
        if (Boolean.TRUE.equals(request.getSetAsDefault())) {
            billerConnectionRepository.unsetDefaultsForUser(userId);
            connection.setIsDefault(true);
        }

        connection.setUpdatedAt(LocalDateTime.now());
        BillerConnection updated = billerConnectionRepository.save(connection);

        log.info("Bill account updated successfully: {}", accountId);
        return billAccountMapper.toResponse(updated);
    }

    /**
     * Delete a bill account (soft delete)
     */
    public void deleteBillAccount(String userId, UUID accountId) {
        log.info("Deleting bill account {} for user: {}", accountId, userId);

        BillerConnection connection = billerConnectionRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BillNotFoundException("Bill account not found or does not belong to user"));

        // Soft delete
        connection.setIsActive(false);
        connection.setDeletedAt(LocalDateTime.now());
        connection.setDeletedBy(userId);
        connection.setUpdatedAt(LocalDateTime.now());

        billerConnectionRepository.save(connection);

        log.info("Bill account deleted successfully: {}", accountId);
    }

    /**
     * Inquire about a bill from a biller
     */
    public BillInquiryResponse inquireBill(String userId, BillInquiryRequest request) {
        log.info("Bill inquiry for user: {} - biller: {}, account: {}",
                userId, request.getBillerId(), maskAccountNumber(request.getAccountNumber()));

        // Validate biller exists
        Biller biller = billerRepository.findById(request.getBillerId())
                .orElseThrow(() -> new BillerNotFoundException("Biller not found with ID: " + request.getBillerId()));

        // Query biller for bill information
        BillInquiryResult result = billerProvider.inquireBill(
                biller.getBillerCode(),
                request.getAccountNumber(),
                request.getAccountName()
        );

        if (!result.isSuccess()) {
            throw new BillerIntegrationException("Bill inquiry failed: " + result.getErrorMessage());
        }

        // Create or update bill in database
        Bill bill = billRepository.findByBillerIdAndAccountNumber(request.getBillerId(), request.getAccountNumber())
                .orElse(new Bill());

        bill.setUserId(userId);
        bill.setBiller(biller);
        bill.setAccountNumber(request.getAccountNumber());
        bill.setAccountName(request.getAccountName() != null ? request.getAccountName() : result.getAccountName());
        bill.setAmount(result.getBillAmount());
        bill.setMinimumDue(result.getMinimumDue());
        bill.setCurrency(result.getCurrency());
        bill.setDueDate(result.getDueDate());
        bill.setIssueDate(result.getIssueDate());
        bill.setBillPeriod(result.getBillPeriod());
        bill.setStatus("UNPAID");
        bill.setBillerReferenceNumber(result.getBillerReferenceNumber());
        bill.setUpdatedAt(LocalDateTime.now());

        if (bill.getId() == null) {
            bill.setCreatedAt(LocalDateTime.now());
        }

        Bill saved = billRepository.save(bill);

        // Convert to response DTO
        return billMapper.toInquiryResponse(saved);
    }

    /**
     * Validate a bill payment before execution
     */
    public BillValidationResponse validateBillPayment(String userId, BillValidationRequest request) {
        log.debug("Validating bill payment for user: {}", userId);

        List<String> validationMessages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;
        String errorMessage = null;

        try {
            // Validate biller exists
            Biller biller = billerRepository.findById(request.getBillerId())
                    .orElseThrow(() -> new BillerNotFoundException("Biller not found"));

            // Validate amount
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                isValid = false;
                validationMessages.add("Amount must be greater than zero");
            }

            if (request.getAmount().compareTo(maxBillPaymentAmount) > 0) {
                isValid = false;
                validationMessages.add("Amount exceeds maximum limit of " + maxBillPaymentAmount);
            }

            // Check wallet balance
            BigDecimal walletBalance = walletService.getBalance(userId, request.getCurrency());
            if (walletBalance.compareTo(request.getAmount()) < 0) {
                isValid = false;
                validationMessages.add("Insufficient wallet balance");
            }

            // Calculate fees
            BigDecimal fee = calculatePaymentFee(request.getAmount(), biller.getCategory());
            BigDecimal totalAmount = request.getAmount().add(fee);

            // Add warnings
            if (fee.compareTo(BigDecimal.ZERO) > 0) {
                warnings.add("A fee of " + fee + " " + request.getCurrency() + " will be charged");
            }

            return BillValidationResponse.builder()
                    .isValid(isValid)
                    .status(isValid ? "VALID" : "INVALID")
                    .totalFee(fee)
                    .totalAmount(totalAmount)
                    .currency(request.getCurrency())
                    .validationMessages(validationMessages)
                    .warnings(warnings)
                    .build();

        } catch (Exception e) {
            log.error("Bill validation failed", e);
            return BillValidationResponse.builder()
                    .isValid(false)
                    .status("ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Calculate payment fee based on amount and category
     */
    private BigDecimal calculatePaymentFee(BigDecimal amount, String category) {
        // Simple fee calculation - can be enhanced with configurable fee schedules
        BigDecimal feePercentage = new BigDecimal("0.01"); // 1% default fee

        // Category-specific fees
        if ("UTILITIES".equalsIgnoreCase(category)) {
            feePercentage = new BigDecimal("0.005"); // 0.5% for utilities
        } else if ("CREDIT_CARD".equalsIgnoreCase(category)) {
            feePercentage = new BigDecimal("0.015"); // 1.5% for credit cards
        }

        return amount.multiply(feePercentage).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Pay a bill immediately using the existing bill-payment-processing-service logic
     */
    public BillPaymentResponse payBill(String userId, PayBillRequest request) {
        log.info("Processing immediate bill payment for user: {} - bill: {}", userId, request.getBillId());

        // Delegate to BillPaymentProcessingService for the actual payment processing
        BillPayment payment = billPaymentProcessingService.initiatePayment(
                userId,
                request.getBillId(),
                request.getAmount(),
                request.getPaymentMethod(),
                request.getNotes(),
                request.getIdempotencyKey()
        );

        return billPaymentMapper.toResponse(payment);
    }

    /**
     * Pay a bill instantly without prior inquiry
     */
    public BillPaymentResponse payBillInstant(String userId, PayBillInstantRequest request) {
        log.info("Processing instant bill payment for user: {} - biller: {}", userId, request.getBillerId());

        // First, inquire the bill
        BillInquiryRequest inquiryRequest = BillInquiryRequest.builder()
                .billerId(request.getBillerId())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .build();

        BillInquiryResponse inquiry = inquireBill(userId, inquiryRequest);

        // Then pay the bill
        PayBillRequest payRequest = PayBillRequest.builder()
                .billId(inquiry.getBillId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .notes(request.getNotes())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        return payBill(userId, payRequest);
    }

    /**
     * Schedule a bill payment for a future date
     */
    public ScheduledPaymentResponse scheduleBillPayment(String userId, ScheduleBillPaymentRequest request) {
        log.info("Scheduling bill payment for user: {} - bill: {}, date: {}",
                userId, request.getBillId(), request.getScheduledDate());

        // Validate bill exists
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new BillNotFoundException("Bill not found with ID: " + request.getBillId()));

        // Validate bill belongs to user
        if (!bill.getUserId().equals(userId)) {
            throw new BillNotFoundException("Bill not found or does not belong to user");
        }

        // Create scheduled payment
        BillPayment payment = new BillPayment();
        payment.setUserId(userId);
        payment.setBill(bill);
        payment.setAmount(request.getAmount());
        payment.setCurrency(bill.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus("SCHEDULED");
        payment.setScheduledDate(request.getScheduledDate());
        payment.setNotes(request.getNotes());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        BillPayment saved = paymentRepository.save(payment);

        log.info("Bill payment scheduled successfully: {}", saved.getId());
        return billPaymentMapper.toScheduledResponse(saved);
    }

    /**
     * Setup recurring bill payment
     */
    public RecurringPaymentResponse setupRecurringPayment(String userId, SetupRecurringPaymentRequest request) {
        log.info("Setting up recurring payment for user: {} - bill: {}", userId, request.getBillId());

        // Validate bill exists
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new BillNotFoundException("Bill not found with ID: " + request.getBillId()));

        // Validate bill belongs to user
        if (!bill.getUserId().equals(userId)) {
            throw new BillNotFoundException("Bill not found or does not belong to user");
        }

        // Create recurring payment config (similar to auto-pay)
        AutoPayConfig config = new AutoPayConfig();
        config.setUserId(userId);
        config.setBill(bill);
        config.setPaymentMethod(request.getPaymentMethod());
        config.setAmountType("FIXED_AMOUNT");
        config.setFixedAmount(request.getAmount());
        config.setPaymentTiming("ON_DUE_DATE");
        config.setNextScheduledDate(request.getStartDate());
        config.setIsEnabled(true);
        config.setSuccessfulPayments(0);
        config.setFailedPayments(0);
        config.setNotes(request.getNotes());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        AutoPayConfig saved = autoPayRepository.save(config);

        log.info("Recurring payment setup successfully: {}", saved.getId());

        return RecurringPaymentResponse.builder()
                .recurringPaymentId(saved.getId())
                .billId(bill.getId())
                .billerName(bill.getBiller().getName())
                .accountNumber(bill.getAccountNumber())
                .frequency(request.getFrequency())
                .amount(request.getAmount())
                .currency(bill.getCurrency())
                .status("ACTIVE")
                .paymentMethod(request.getPaymentMethod())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .nextPaymentDate(request.getStartDate())
                .successfulPayments(0)
                .failedPayments(0)
                .createdAt(saved.getCreatedAt())
                .notes(saved.getNotes())
                .isActive(true)
                .build();
    }

    /**
     * Get payment history for a user
     */
    @Transactional(readOnly = true)
    public Page<BillPaymentResponse> getPaymentHistory(
            String userId,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            Pageable pageable) {

        log.debug("Fetching payment history for user: {} - from: {}, to: {}, status: {}",
                userId, fromDate, toDate, status);

        Page<BillPayment> payments;

        if (fromDate != null && toDate != null && status != null) {
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(23, 59, 59);
            payments = paymentRepository.findByUserIdAndCreatedAtBetweenAndStatus(
                    userId, fromDateTime, toDateTime, status, pageable);
        } else if (fromDate != null && toDate != null) {
            LocalDateTime fromDateTime = fromDate.atStartOfDay();
            LocalDateTime toDateTime = toDate.atTime(23, 59, 59);
            payments = paymentRepository.findByUserIdAndCreatedAtBetween(
                    userId, fromDateTime, toDateTime, pageable);
        } else if (status != null) {
            payments = paymentRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            payments = paymentRepository.findByUserId(userId, pageable);
        }

        return payments.map(billPaymentMapper::toResponse);
    }

    /**
     * Get payment details by ID
     */
    @Transactional(readOnly = true)
    public BillPaymentResponse getPaymentDetails(String userId, UUID paymentId) {
        log.debug("Fetching payment details for user: {} - payment: {}", userId, paymentId);

        BillPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        // Validate payment belongs to user
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException("Payment not found or does not belong to user");
        }

        return billPaymentMapper.toResponse(payment);
    }

    /**
     * Get payment status by ID
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String userId, UUID paymentId) {
        log.debug("Fetching payment status for user: {} - payment: {}", userId, paymentId);

        BillPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        // Validate payment belongs to user
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException("Payment not found or does not belong to user");
        }

        PaymentStatusResponse response = billPaymentMapper.toStatusResponse(payment);

        // Add cancellation eligibility
        response.setCanCancel(canCancelPayment(payment));
        response.setCanRetry(canRetryPayment(payment));

        return response;
    }

    /**
     * Cancel a scheduled or pending payment
     */
    public void cancelPayment(String userId, UUID paymentId, CancelPaymentRequest request) {
        log.info("Cancelling payment for user: {} - payment: {}", userId, paymentId);

        BillPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        // Validate payment belongs to user
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException("Payment not found or does not belong to user");
        }

        // Validate payment can be cancelled
        if (!canCancelPayment(payment)) {
            throw new InvalidPaymentStateException(
                    "Payment cannot be cancelled. Current status: " + payment.getStatus());
        }

        // Cancel payment
        payment.setStatus("CANCELLED");
        payment.setFailureReason(request.getReason());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepository.save(payment);

        log.info("Payment cancelled successfully: {}", paymentId);
    }

    /**
     * Generate payment receipt
     */
    @Transactional(readOnly = true)
    public byte[] generateReceipt(String userId, UUID paymentId, String format) {
        log.info("Generating receipt for user: {} - payment: {}, format: {}", userId, paymentId, format);

        BillPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        // Validate payment belongs to user
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException("Payment not found or does not belong to user");
        }

        // Validate payment is completed
        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new InvalidPaymentStateException("Receipt can only be generated for completed payments");
        }

        // Generate receipt based on format
        if ("PDF".equalsIgnoreCase(format)) {
            return generatePdfReceipt(payment);
        } else if ("JSON".equalsIgnoreCase(format)) {
            return generateJsonReceipt(payment);
        } else {
            throw new IllegalArgumentException("Unsupported receipt format: " + format);
        }
    }

    /**
     * Get auto-pay settings for a user
     */
    @Transactional(readOnly = true)
    public List<AutoPayResponse> getAutoPaySettings(String userId) {
        log.debug("Fetching auto-pay settings for user: {}", userId);

        List<AutoPayConfig> configs = autoPayRepository.findByUserId(userId);
        return autoPayConfigMapper.toResponseList(configs);
    }

    /**
     * Cancel/delete auto-pay configuration
     */
    public void cancelAutoPay(String userId, UUID autoPayId) {
        log.info("Cancelling auto-pay for user: {} - autoPayId: {}", userId, autoPayId);

        AutoPayConfig config = autoPayRepository.findById(autoPayId)
                .orElseThrow(() -> new AutoPayConfigNotFoundException("Auto-pay configuration not found"));

        // Validate config belongs to user
        if (!config.getUserId().equals(userId)) {
            throw new AutoPayConfigNotFoundException("Auto-pay configuration not found or does not belong to user");
        }

        // Disable auto-pay
        config.setIsEnabled(false);
        config.setUpdatedAt(LocalDateTime.now());

        autoPayRepository.save(config);

        log.info("Auto-pay cancelled successfully: {}", autoPayId);
    }

    /**
     * Generate summary report for bill payments
     */
    @Transactional(readOnly = true)
    public BillPaymentSummaryReport generateSummaryReport(String userId, LocalDate fromDate, LocalDate toDate) {
        log.info("Generating summary report for user: {} - from: {}, to: {}", userId, fromDate, toDate);

        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(23, 59, 59);

        List<BillPayment> payments = paymentRepository.findByUserIdAndCreatedAtBetween(
                userId, fromDateTime, toDateTime);

        // Calculate statistics
        int totalPayments = payments.size();
        int successfulPayments = (int) payments.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .count();
        int failedPayments = (int) payments.stream()
                .filter(p -> "FAILED".equals(p.getStatus()))
                .count();
        int pendingPayments = (int) payments.stream()
                .filter(p -> "PENDING".equals(p.getStatus()) || "SCHEDULED".equals(p.getStatus()))
                .count();

        BigDecimal totalAmountPaid = payments.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(BillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFees = payments.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(p -> p.getFee() != null ? p.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averagePaymentAmount = totalPayments > 0
                ? totalAmountPaid.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Get currency from first payment or default to USD
        String currency = payments.isEmpty() ? "USD" : payments.get(0).getCurrency();

        return BillPaymentSummaryReport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .userId(userId)
                .totalPayments(totalPayments)
                .successfulPayments(successfulPayments)
                .failedPayments(failedPayments)
                .pendingPayments(pendingPayments)
                .totalAmountPaid(totalAmountPaid)
                .totalFees(totalFees)
                .currency(currency)
                .averagePaymentAmount(averagePaymentAmount)
                .build();
    }

    /**
     * Get spending analytics for a user
     */
    @Transactional(readOnly = true)
    public SpendingAnalytics getSpendingAnalytics(String userId, String period) {
        log.info("Generating spending analytics for user: {} - period: {}", userId, period);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate;

        switch (period.toUpperCase()) {
            case "WEEKLY":
                startDate = now.minusWeeks(1);
                break;
            case "MONTHLY":
                startDate = now.minusMonths(1);
                break;
            case "QUARTERLY":
                startDate = now.minusMonths(3);
                break;
            case "YEARLY":
                startDate = now.minusYears(1);
                break;
            default:
                startDate = now.minusMonths(1);
        }

        List<BillPayment> currentPeriodPayments = paymentRepository
                .findByUserIdAndCreatedAtBetweenAndStatus(userId, startDate, now, "COMPLETED");

        BigDecimal currentPeriodSpending = currentPeriodPayments.stream()
                .map(BillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get previous period for comparison
        LocalDateTime previousStartDate = startDate.minus(ChronoUnit.DAYS.between(startDate, now), ChronoUnit.DAYS);
        List<BillPayment> previousPeriodPayments = paymentRepository
                .findByUserIdAndCreatedAtBetweenAndStatus(userId, previousStartDate, startDate, "COMPLETED");

        BigDecimal previousPeriodSpending = previousPeriodPayments.stream()
                .map(BillPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal percentageChange = calculatePercentageChange(previousPeriodSpending, currentPeriodSpending);

        String currency = currentPeriodPayments.isEmpty() ? "USD" : currentPeriodPayments.get(0).getCurrency();

        return SpendingAnalytics.builder()
                .period(period)
                .currentPeriodSpending(currentPeriodSpending)
                .previousPeriodSpending(previousPeriodSpending)
                .percentageChange(percentageChange)
                .currency(currency)
                .build();
    }

    // ============== Helper Methods ==============

    private boolean canCancelPayment(BillPayment payment) {
        return "SCHEDULED".equals(payment.getStatus()) || "PENDING".equals(payment.getStatus());
    }

    private boolean canRetryPayment(BillPayment payment) {
        return "FAILED".equals(payment.getStatus());
    }

    private byte[] generatePdfReceipt(BillPayment payment) {
        // TODO: Implement PDF generation using a library like iText or Apache PDFBox
        log.warn("PDF receipt generation not yet implemented");
        return new byte[0];
    }

    private byte[] generateJsonReceipt(BillPayment payment) {
        // Simple JSON receipt generation
        BillPaymentResponse response = billPaymentMapper.toResponse(payment);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(response);
        } catch (Exception e) {
            log.error("Failed to generate JSON receipt", e);
            return new byte[0];
        }
    }

    private BigDecimal calculatePercentageChange(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return newValue.subtract(oldValue)
                .divide(oldValue, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}