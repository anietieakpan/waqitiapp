package com.waqiti.merchant.service;

import com.waqiti.merchant.domain.*;
import com.waqiti.merchant.dto.*;
import com.waqiti.merchant.repository.*;
import com.waqiti.merchant.provider.PaymentProcessorProvider;
import com.waqiti.merchant.provider.PosProvider;
import com.waqiti.merchant.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.MerchantPaymentEvent;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.security.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Merchant Payment Service - Handles business payments, QR codes, and POS integration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantPaymentService {

    private final MerchantRepository merchantRepository;
    private final MerchantPaymentRepository paymentRepository;
    private final QrCodeRepository qrCodeRepository;
    private final PosTerminalRepository posRepository;
    private final SettlementRepository settlementRepository;
    private final MerchantAnalyticsRepository analyticsRepository;
    private final PaymentProcessorProvider processorProvider;
    private final PosProvider posProvider;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    
    @Value("${merchant.min-payment-amount:0.01}")
    private BigDecimal minPaymentAmount;
    
    @Value("${merchant.max-payment-amount:10000.00}")
    private BigDecimal maxPaymentAmount;
    
    @Value("${merchant.settlement-fee-rate:0.029}")
    private BigDecimal settlementFeeRate;
    
    @Value("${merchant.qr-code-expiry-minutes:30}")
    private int qrCodeExpiryMinutes;

    /**
     * Register merchant account
     */
    @Transactional
    public MerchantAccountDto registerMerchant(RegisterMerchantRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        // Check if merchant already exists
        if (merchantRepository.existsByUserId(userId)) {
            throw new MerchantAlreadyExistsException("Merchant account already exists");
        }
        
        // Validate business information
        validateBusinessInformation(request);
        
        // Create merchant account
        MerchantAccount merchant = MerchantAccount.builder()
            .userId(userId)
            .businessName(request.getBusinessName())
            .businessType(request.getBusinessType())
            .industry(request.getIndustry())
            .mcc(request.getMcc())
            .businessAddress(request.getBusinessAddress())
            .contactInfo(request.getContactInfo())
            .taxId(request.getTaxId())
            .businessLicense(request.getBusinessLicense())
            .website(request.getWebsite())
            .description(request.getDescription())
            .status(MerchantStatus.PENDING_VERIFICATION)
            .settings(MerchantSettings.builder()
                .acceptCashPayments(true)
                .acceptCardPayments(true)
                .acceptOnlinePayments(true)
                .settlementSchedule(SettlementSchedule.DAILY)
                .webhookUrl(request.getWebhookUrl())
                .notificationsEnabled(true)
                .build())
            .balance(BigDecimal.ZERO)
            .totalVolume(BigDecimal.ZERO)
            .totalTransactions(0L)
            .registeredAt(Instant.now())
            .build();
        
        merchant = merchantRepository.save(merchant);
        
        // Generate API credentials
        generateApiCredentials(merchant);
        
        // Start verification process
        initiateBusinessVerification(merchant);
        
        // Create default QR code
        createDefaultQrCode(merchant);
        
        // Publish event
        eventPublisher.publish(MerchantPaymentEvent.merchantRegistered(merchant));
        
        log.info("Registered merchant account {} for user {}", merchant.getId(), userId);
        
        return toMerchantDto(merchant);
    }

    /**
     * Process merchant payment
     */
    @Transactional
    public MerchantPaymentDto processPayment(ProcessMerchantPaymentRequest request) {
        // Get merchant
        MerchantAccount merchant = getMerchantById(request.getMerchantId());
        
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new MerchantNotActiveException("Merchant account is not active");
        }
        
        // Validate payment
        validatePayment(request, merchant);
        
        // Check for duplicate payment
        if (isDuplicatePayment(request.getIdempotencyKey(), merchant.getId())) {
            return getExistingPayment(request.getIdempotencyKey());
        }
        
        // Create payment record
        MerchantPayment payment = MerchantPayment.builder()
            .merchantId(merchant.getId())
            .customerId(request.getCustomerId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentMethod(request.getPaymentMethod())
            .description(request.getDescription())
            .orderId(request.getOrderId())
            .idempotencyKey(request.getIdempotencyKey())
            .status(PaymentStatus.PENDING)
            .channel(request.getChannel())
            .terminalId(request.getTerminalId())
            .qrCodeId(request.getQrCodeId())
            .metadata(request.getMetadata())
            .build();
        
        payment = paymentRepository.save(payment);
        
        try {
            // Fraud check
            FraudCheckResult fraudResult = fraudDetectionService.checkMerchantPayment(
                payment, merchant
            );
            
            if (fraudResult.isBlocked()) {
                payment.setStatus(PaymentStatus.DECLINED);
                payment.setFailureReason("Fraud check failed: " + fraudResult.getReason());
                payment.setProcessedAt(Instant.now());
                paymentRepository.save(payment);
                
                // Alert merchant
                notificationService.sendFraudAlert(merchant.getUserId(), payment);
                
                return toPaymentDto(payment);
            }
            
            // Process payment based on method
            PaymentResult result = processPaymentByMethod(payment, request);
            
            if (result.isSuccessful()) {
                // Successful payment
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setTransactionId(result.getTransactionId());
                payment.setProcessedAt(Instant.now());
                
                // Calculate merchant fee
                BigDecimal merchantFee = calculateMerchantFee(payment.getAmount(), merchant);
                BigDecimal netAmount = payment.getAmount().subtract(merchantFee);
                
                payment.setMerchantFee(merchantFee);
                payment.setNetAmount(netAmount);
                
                // Update merchant balance
                merchant.setBalance(merchant.getBalance().add(netAmount));
                merchant.setTotalVolume(merchant.getTotalVolume().add(payment.getAmount()));
                merchant.setTotalTransactions(merchant.getTotalTransactions() + 1);
                merchant.setLastPaymentAt(Instant.now());
                merchantRepository.save(merchant);
                
                // Send confirmations
                notificationService.sendPaymentConfirmation(merchant, payment);
                
                if (request.getCustomerEmail() != null) {
                    notificationService.sendCustomerReceipt(request.getCustomerEmail(), payment);
                }
                
                // Webhook notification
                sendWebhookNotification(merchant, payment);
                
                // Update analytics
                updateMerchantAnalytics(merchant, payment);
                
            } else {
                // Failed payment
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(result.getErrorMessage());
                payment.setProcessedAt(Instant.now());
                
                // Notify merchant
                notificationService.sendPaymentFailureNotification(merchant, payment);
            }
            
            paymentRepository.save(payment);
            
            // Publish event
            eventPublisher.publish(MerchantPaymentEvent.paymentProcessed(merchant, payment));
            
            log.info("Processed merchant payment {} for merchant {} - status: {}", 
                payment.getId(), merchant.getId(), payment.getStatus());
            
            return toPaymentDto(payment);
            
        } catch (Exception e) {
            log.error("Failed to process merchant payment {}", payment.getId(), e);
            
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("System error: " + e.getMessage());
            payment.setProcessedAt(Instant.now());
            paymentRepository.save(payment);
            
            throw new PaymentProcessingException("Failed to process payment", e);
        }
    }

    /**
     * Generate QR code for payment
     */
    @Transactional
    public QrCodeDto generateQrCode(String merchantId, GenerateQrCodeRequest request) {
        MerchantAccount merchant = getMerchantById(merchantId);
        
        // Validate request
        validateQrCodeRequest(request);
        
        // Create QR code
        QrPaymentCode qrCode = QrPaymentCode.builder()
            .merchantId(merchantId)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .description(request.getDescription())
            .orderId(request.getOrderId())
            .type(request.getType())
            .status(QrCodeStatus.ACTIVE)
            .expiresAt(request.getType() == QrCodeType.DYNAMIC ? 
                Instant.now().plus(Duration.ofMinutes(qrCodeExpiryMinutes)) : null)
            .usageLimit(request.getUsageLimit())
            .usageCount(0)
            .metadata(request.getMetadata())
            .build();
        
        // Generate QR data
        String qrData = generateQrData(qrCode);
        qrCode.setQrData(qrData);
        
        qrCode = qrCodeRepository.save(qrCode);
        
        log.info("Generated QR code {} for merchant {}", qrCode.getId(), merchantId);
        
        return toQrCodeDto(qrCode);
    }

    /**
     * Process QR code payment
     */
    @Transactional
    public MerchantPaymentDto processQrPayment(String qrCodeId, ProcessQrPaymentRequest request) {
        String customerId = securityContext.getCurrentUserId();
        
        // Get and validate QR code
        QrPaymentCode qrCode = getQrCodeById(qrCodeId);
        validateQrCodeForPayment(qrCode);
        
        MerchantAccount merchant = getMerchantById(qrCode.getMerchantId());
        
        // Determine payment amount
        BigDecimal paymentAmount = qrCode.getAmount() != null ? 
            qrCode.getAmount() : request.getAmount();
        
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Payment amount required");
        }
        
        // Create payment request
        ProcessMerchantPaymentRequest paymentRequest = ProcessMerchantPaymentRequest.builder()
            .merchantId(merchant.getId())
            .customerId(customerId)
            .amount(paymentAmount)
            .currency(qrCode.getCurrency())
            .paymentMethod(request.getPaymentMethod())
            .description(qrCode.getDescription())
            .orderId(qrCode.getOrderId())
            .channel(PaymentChannel.QR_CODE)
            .qrCodeId(qrCodeId)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        // Process payment
        MerchantPaymentDto payment = processPayment(paymentRequest);
        
        // Update QR code usage
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            qrCode.setUsageCount(qrCode.getUsageCount() + 1);
            qrCode.setLastUsedAt(Instant.now());
            
            // Check if QR code should be deactivated
            if (qrCode.getUsageLimit() != null && 
                qrCode.getUsageCount() >= qrCode.getUsageLimit()) {
                qrCode.setStatus(QrCodeStatus.EXPIRED);
            }
            
            qrCodeRepository.save(qrCode);
        }
        
        return payment;
    }

    /**
     * Create POS terminal
     */
    @Transactional
    public PosTerminalDto createPosTerminal(String merchantId, CreatePosTerminalRequest request) {
        MerchantAccount merchant = getMerchantById(merchantId);
        
        // Validate merchant owns this request
        if (!merchant.getUserId().equals(securityContext.getCurrentUserId())) {
            throw new UnauthorizedException("Not authorized to create POS terminal");
        }
        
        // Create terminal
        PosTerminal terminal = PosTerminal.builder()
            .merchantId(merchantId)
            .name(request.getName())
            .location(request.getLocation())
            .type(request.getType())
            .serialNumber(generateTerminalSerial())
            .status(TerminalStatus.PENDING_ACTIVATION)
            .settings(TerminalSettings.builder()
                .acceptCash(request.isAcceptCash())
                .acceptCards(request.isAcceptCards())
                .acceptContactless(request.isAcceptContactless())
                .receiptPrinting(request.isReceiptPrinting())
                .tippingEnabled(request.isTippingEnabled())
                .build())
            .createdAt(Instant.now())
            .build();
        
        terminal = posRepository.save(terminal);
        
        // Generate API key for terminal
        String apiKey = generateTerminalApiKey(terminal);
        terminal.setApiKey(apiKey);
        posRepository.save(terminal);
        
        log.info("Created POS terminal {} for merchant {}", terminal.getId(), merchantId);
        
        return toPosTerminalDto(terminal);
    }

    /**
     * Get merchant analytics
     */
    @Transactional(readOnly = true)
    public MerchantAnalyticsDto getMerchantAnalytics(String merchantId, AnalyticsTimeframe timeframe) {
        MerchantAccount merchant = getMerchantById(merchantId);
        
        // Validate merchant owns this request
        if (!merchant.getUserId().equals(securityContext.getCurrentUserId())) {
            throw new UnauthorizedException("Not authorized to view analytics");
        }
        
        Instant startDate = calculateStartDate(timeframe);
        
        // Get payment statistics
        PaymentStatistics stats = paymentRepository.getPaymentStatistics(merchantId, startDate);
        
        // Get payment trends
        List<DailyPaymentSummary> dailyTrends = paymentRepository.getDailyTrends(merchantId, startDate);
        
        // Get payment methods breakdown
        List<PaymentMethodSummary> methodBreakdown = paymentRepository.getPaymentMethodBreakdown(merchantId, startDate);
        
        // Get top customers
        List<CustomerSummary> topCustomers = paymentRepository.getTopCustomers(merchantId, startDate, 10);
        
        // Calculate key metrics
        BigDecimal averageTransaction = stats.getTotalTransactions() > 0 ?
            stats.getTotalAmount().divide(BigDecimal.valueOf(stats.getTotalTransactions()), 2, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        return MerchantAnalyticsDto.builder()
            .merchantId(merchantId)
            .timeframe(timeframe)
            .totalAmount(stats.getTotalAmount())
            .totalTransactions(stats.getTotalTransactions())
            .successfulTransactions(stats.getSuccessfulTransactions())
            .failedTransactions(stats.getFailedTransactions())
            .averageTransaction(averageTransaction)
            .totalFees(stats.getTotalFees())
            .netAmount(stats.getNetAmount())
            .dailyTrends(dailyTrends)
            .paymentMethodBreakdown(methodBreakdown)
            .topCustomers(topCustomers)
            .build();
    }

    /**
     * Scheduled settlement process
     */
    @Scheduled(cron = "0 0 1 * * ?") // Daily at 1 AM
    @Transactional
    public void processSettlements() {
        log.info("Starting daily settlement process");
        
        List<MerchantAccount> merchants = merchantRepository.findByStatusAndSettlementSchedule(
            MerchantStatus.ACTIVE, SettlementSchedule.DAILY
        );
        
        LocalDate settlementDate = LocalDate.now().minusDays(1);
        
        for (MerchantAccount merchant : merchants) {
            try {
                processSettlement(merchant, settlementDate);
            } catch (Exception e) {
                log.error("Failed to process settlement for merchant {}", merchant.getId(), e);
            }
        }
        
        log.info("Completed settlement process for {} merchants", merchants.size());
    }

    private PaymentResult processPaymentByMethod(MerchantPayment payment, 
                                               ProcessMerchantPaymentRequest request) {
        switch (request.getPaymentMethod()) {
            case WALLET:
                return processWalletPayment(payment);
            case BANK_TRANSFER:
                return processBankTransferPayment(payment);
            case CARD:
                return processCardPayment(payment, request);
            case CASH:
                return processCashPayment(payment);
            default:
                throw new UnsupportedPaymentMethodException("Unsupported payment method");
        }
    }

    private PaymentResult processWalletPayment(MerchantPayment payment) {
        try {
            // Process through wallet service
            PaymentRequest walletRequest = PaymentRequest.builder()
                .senderId(payment.getCustomerId())
                .recipientId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .metadata(Map.of(
                    "merchantPaymentId", payment.getId(),
                    "orderId", payment.getOrderId() != null ? payment.getOrderId() : ""
                ))
                .build();
            
            PaymentResult result = paymentService.processPayment(walletRequest);
            
            return PaymentResult.builder()
                .successful(result.isSuccessful())
                .transactionId(result.getPaymentId())
                .errorMessage(result.getErrorMessage())
                .build();
            
        } catch (Exception e) {
            return PaymentResult.builder()
                .successful(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private PaymentResult processBankTransferPayment(MerchantPayment payment) {
        // Implementation for bank transfer processing
        return PaymentResult.builder()
            .successful(true)
            .transactionId("BT" + System.currentTimeMillis())
            .build();
    }

    private PaymentResult processCardPayment(MerchantPayment payment, 
                                           ProcessMerchantPaymentRequest request) {
        // Process through payment processor
        return processorProvider.processCardPayment(
            CardPaymentRequest.builder()
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .cardToken(request.getCardToken())
                .merchantId(payment.getMerchantId())
                .build()
        );
    }

    private PaymentResult processCashPayment(MerchantPayment payment) {
        // Cash payments are marked as successful immediately
        return PaymentResult.builder()
            .successful(true)
            .transactionId("CASH" + System.currentTimeMillis())
            .build();
    }

    private void processSettlement(MerchantAccount merchant, LocalDate settlementDate) {
        // Get unsettled payments
        List<MerchantPayment> unsettledPayments = paymentRepository
            .findUnsettledPayments(merchant.getId(), settlementDate);
        
        if (unsettledPayments.isEmpty()) {
            return;
        }
        
        // Calculate settlement amount
        BigDecimal totalAmount = unsettledPayments.stream()
            .map(MerchantPayment::getNetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Create settlement record
        Settlement settlement = Settlement.builder()
            .merchantId(merchant.getId())
            .settlementDate(settlementDate)
            .amount(totalAmount)
            .currency("USD") // Default currency
            .paymentCount(unsettledPayments.size())
            .status(SettlementStatus.PENDING)
            .paymentIds(unsettledPayments.stream()
                .map(MerchantPayment::getId)
                .collect(Collectors.toList()))
            .build();
        
        settlement = settlementRepository.save(settlement);
        
        try {
            // Transfer funds to merchant wallet
            walletService.credit(
                merchant.getUserId(),
                totalAmount,
                settlement.getCurrency(),
                "Daily settlement",
                Map.of("settlementId", settlement.getId())
            );
            
            // Update settlement status
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setSettledAt(Instant.now());
            settlementRepository.save(settlement);
            
            // Mark payments as settled
            paymentRepository.markPaymentsAsSettled(
                unsettledPayments.stream().map(MerchantPayment::getId).collect(Collectors.toList()),
                settlement.getId()
            );
            
            // Send notification
            notificationService.sendSettlementNotification(merchant, settlement);
            
            log.info("Processed settlement {} for merchant {} - amount: {}", 
                settlement.getId(), merchant.getId(), totalAmount);
            
        } catch (Exception e) {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(e.getMessage());
            settlementRepository.save(settlement);
            
            log.error("Settlement failed for merchant {}", merchant.getId(), e);
        }
    }

    private BigDecimal calculateMerchantFee(BigDecimal amount, MerchantAccount merchant) {
        // Calculate processing fee (2.9% + $0.30)
        BigDecimal percentageFee = amount.multiply(settlementFeeRate);
        BigDecimal fixedFee = BigDecimal.valueOf(0.30);
        return percentageFee.add(fixedFee).setScale(2, RoundingMode.UP);
    }

    private String generateQrData(QrPaymentCode qrCode) {
        // Generate QR code data in a standard format
        Map<String, Object> qrData = Map.of(
            "merchantId", qrCode.getMerchantId(),
            "qrCodeId", qrCode.getId(),
            "amount", qrCode.getAmount() != null ? qrCode.getAmount().toString() : "",
            "currency", qrCode.getCurrency(),
            "description", qrCode.getDescription() != null ? qrCode.getDescription() : "",
            "timestamp", Instant.now().getEpochSecond()
        );
        
        // In a real implementation, this would be encoded as a QR code
        return Base64.getEncoder().encodeToString(qrData.toString().getBytes());
    }

    // Additional helper methods and validations would be implemented here...
    private void validateBusinessInformation(RegisterMerchantRequest request) {
        if (request.getBusinessName() == null || request.getBusinessName().trim().isEmpty()) {
            throw new InvalidMerchantDataException("Business name is required");
        }
        
        if (request.getBusinessType() == null) {
            throw new InvalidMerchantDataException("Business type is required");
        }
        
        if (request.getIndustry() == null || request.getIndustry().trim().isEmpty()) {
            throw new InvalidMerchantDataException("Industry is required");
        }
        
        if (request.getTaxId() == null || request.getTaxId().trim().isEmpty()) {
            throw new InvalidMerchantDataException("Tax ID is required");
        }
        
        if (request.getBusinessAddress() == null) {
            throw new InvalidMerchantDataException("Business address is required");
        }
        
        if (request.getContactInfo() == null || request.getContactInfo().getEmail() == null) {
            throw new InvalidMerchantDataException("Contact information with email is required");
        }
        
        // Validate email format
        String email = request.getContactInfo().getEmail();
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new InvalidMerchantDataException("Invalid email format");
        }
    }
    private void generateApiCredentials(MerchantAccount merchant) {
        // Generate API key and secret using secure cryptographic generator
        ApiKeyGenerator keyGenerator = new ApiKeyGenerator();
        String apiKey = keyGenerator.generateApiKey();
        String apiSecret = keyGenerator.generateApiSecret();
        
        // Store API key and hashed secret
        merchant.setApiKey(apiKey);
        merchant.setApiSecretHash(hashApiSecret(apiSecret)); // Store hash, not plain text
        
        log.info("Generated secure API credentials for merchant {}", merchant.getId());
        
        // Send credentials to merchant via secure channel
        notificationService.sendApiCredentials(merchant.getUserId(), apiKey, apiSecret);
    }
    private void initiateBusinessVerification(MerchantAccount merchant) {
        // Create verification record
        BusinessVerification verification = BusinessVerification.builder()
            .merchantId(merchant.getId())
            .businessName(merchant.getBusinessName())
            .taxId(merchant.getTaxId())
            .businessLicense(merchant.getBusinessLicense())
            .status(VerificationStatus.PENDING)
            .submittedAt(Instant.now())
            .build();
        
        // In production, this would integrate with KYB providers
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate verification process
                Thread.sleep(5000);
                
                // Auto-approve for demo purposes
                verification.setStatus(VerificationStatus.APPROVED);
                verification.setReviewedAt(Instant.now());
                verification.setReviewNotes("Auto-approved for demo");
                
                // Update merchant status
                merchant.setStatus(MerchantStatus.ACTIVE);
                merchantRepository.save(merchant);
                
                // Notify merchant
                notificationService.sendVerificationComplete(merchant.getUserId(), true);
                
                log.info("Business verification completed for merchant {}", merchant.getId());
                
            } catch (Exception e) {
                log.error("Verification process failed for merchant {}", merchant.getId(), e);
                verification.setStatus(VerificationStatus.REJECTED);
                verification.setReviewedAt(Instant.now());
                verification.setReviewNotes("Verification failed: " + e.getMessage());
                
                notificationService.sendVerificationComplete(merchant.getUserId(), false);
            }
        });
        
        log.info("Initiated business verification for merchant {}", merchant.getId());
    }
    private void createDefaultQrCode(MerchantAccount merchant) {
        try {
            // Create default static QR code for the merchant
            QrPaymentCode defaultQrCode = QrPaymentCode.builder()
                .merchantId(merchant.getId())
                .amount(null) // Static QR allows any amount
                .currency("USD")
                .description("Payment to " + merchant.getBusinessName())
                .type(QrCodeType.STATIC)
                .status(QrCodeStatus.ACTIVE)
                .expiresAt(null) // Static QR doesn't expire
                .usageLimit(null) // No usage limit
                .usageCount(0)
                .metadata(Map.of(
                    "isDefault", "true",
                    "createdBy", "system"
                ))
                .build();
            
            // Generate QR data
            String qrData = generateQrData(defaultQrCode);
            defaultQrCode.setQrData(qrData);
            
            qrCodeRepository.save(defaultQrCode);
            
            log.info("Created default QR code {} for merchant {}", defaultQrCode.getId(), merchant.getId());
            
        } catch (Exception e) {
            log.error("Failed to create default QR code for merchant {}", merchant.getId(), e);
        }
    }
    private void validatePayment(ProcessMerchantPaymentRequest request, MerchantAccount merchant) {
        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Payment amount must be positive");
        }
        
        if (request.getAmount().compareTo(minPaymentAmount) < 0) {
            throw new InvalidPaymentException("Payment amount below minimum: " + minPaymentAmount);
        }
        
        if (request.getAmount().compareTo(maxPaymentAmount) > 0) {
            throw new InvalidPaymentException("Payment amount exceeds maximum: " + maxPaymentAmount);
        }
        
        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new InvalidPaymentException("Currency is required");
        }
        
        // Validate payment method
        if (request.getPaymentMethod() == null) {
            throw new InvalidPaymentException("Payment method is required");
        }
        
        // Check if merchant accepts this payment method
        MerchantSettings settings = merchant.getSettings();
        switch (request.getPaymentMethod()) {
            case CASH:
                if (!settings.isAcceptCashPayments()) {
                    throw new PaymentMethodNotSupportedException("Merchant doesn't accept cash payments");
                }
                break;
            case CARD:
                if (!settings.isAcceptCardPayments()) {
                    throw new PaymentMethodNotSupportedException("Merchant doesn't accept card payments");
                }
                break;
            case WALLET:
            case BANK_TRANSFER:
                if (!settings.isAcceptOnlinePayments()) {
                    throw new PaymentMethodNotSupportedException("Merchant doesn't accept online payments");
                }
                break;
        }
        
        // Validate customer ID
        if (request.getCustomerId() == null || request.getCustomerId().trim().isEmpty()) {
            throw new InvalidPaymentException("Customer ID is required");
        }
        
        // Validate idempotency key format
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().trim().isEmpty()) {
            throw new InvalidPaymentException("Idempotency key is required");
        }
        
        if (request.getIdempotencyKey().length() > 255) {
            throw new InvalidPaymentException("Idempotency key too long");
        }
    }
    private boolean isDuplicatePayment(String idempotencyKey, String merchantId) {
        return paymentRepository.existsByIdempotencyKeyAndMerchantId(idempotencyKey, merchantId);
    }
    
    private MerchantPaymentDto getExistingPayment(String idempotencyKey) {
        MerchantPayment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found for idempotency key"));
        return toPaymentDto(existingPayment);
    }
    
    private String hashApiSecret(String apiSecret) {
        // In production, use BCrypt or similar secure hashing
        return "hashed_" + apiSecret.hashCode();
    }
    
    private String generateWebhookSignature(WebhookPayload payload, String secretHash) {
        // In production, use HMAC-SHA256 with the actual secret
        return "sha256=" + payload.toString().hashCode();
    }
    private void sendWebhookNotification(MerchantAccount merchant, MerchantPayment payment) {
        if (merchant.getSettings().getWebhookUrl() == null || merchant.getSettings().getWebhookUrl().trim().isEmpty()) {
            return; // No webhook configured
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                // Create webhook payload
                WebhookPayload payload = WebhookPayload.builder()
                    .event("payment.completed")
                    .timestamp(Instant.now())
                    .data(WebhookPaymentData.builder()
                        .paymentId(payment.getId())
                        .merchantId(payment.getMerchantId())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .status(payment.getStatus().toString())
                        .paymentMethod(payment.getPaymentMethod().toString())
                        .orderId(payment.getOrderId())
                        .customerEmailed(payment.getCustomerId())
                        .netAmount(payment.getNetAmount())
                        .merchantFee(payment.getMerchantFee())
                        .processedAt(payment.getProcessedAt())
                        .build())
                    .build();
                
                // Send webhook with signature
                String signature = generateWebhookSignature(payload, merchant.getApiSecretHash());
                
                // In production, this would use a proper HTTP client with retry logic
                WebhookClient.send(
                    merchant.getSettings().getWebhookUrl(),
                    payload,
                    Map.of(
                        "X-Waqiti-Signature", signature,
                        "X-Waqiti-Event", "payment.completed",
                        "X-Waqiti-Timestamp", String.valueOf(Instant.now().getEpochSecond())
                    )
                );
                
                log.info("Sent webhook notification for payment {} to merchant {}", 
                    payment.getId(), merchant.getId());
                
            } catch (Exception e) {
                log.error("Failed to send webhook notification for payment {} to merchant {}", 
                    payment.getId(), merchant.getId(), e);
            }
        });
    }
    private void updateMerchantAnalytics(MerchantAccount merchant, MerchantPayment payment) {
        try {
            LocalDate paymentDate = payment.getProcessedAt().atZone(ZoneOffset.UTC).toLocalDate();
            
            // Update daily analytics
            MerchantDailyAnalytics dailyAnalytics = analyticsRepository
                .findByMerchantIdAndDate(merchant.getId(), paymentDate)
                .orElse(MerchantDailyAnalytics.builder()
                    .merchantId(merchant.getId())
                    .date(paymentDate)
                    .totalAmount(BigDecimal.ZERO)
                    .totalTransactions(0L)
                    .successfulTransactions(0L)
                    .failedTransactions(0L)
                    .totalFees(BigDecimal.ZERO)
                    .averageTransaction(BigDecimal.ZERO)
                    .build());
            
            // Update metrics
            dailyAnalytics.setTotalAmount(dailyAnalytics.getTotalAmount().add(payment.getAmount()));
            dailyAnalytics.setTotalTransactions(dailyAnalytics.getTotalTransactions() + 1);
            
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                dailyAnalytics.setSuccessfulTransactions(dailyAnalytics.getSuccessfulTransactions() + 1);
            } else {
                dailyAnalytics.setFailedTransactions(dailyAnalytics.getFailedTransactions() + 1);
            }
            
            if (payment.getMerchantFee() != null) {
                dailyAnalytics.setTotalFees(dailyAnalytics.getTotalFees().add(payment.getMerchantFee()));
            }
            
            // Calculate average transaction
            if (dailyAnalytics.getTotalTransactions() > 0) {
                dailyAnalytics.setAverageTransaction(
                    dailyAnalytics.getTotalAmount().divide(
                        BigDecimal.valueOf(dailyAnalytics.getTotalTransactions()),
                        2,
                        RoundingMode.HALF_UP
                    )
                );
            }
            
            // Update payment method breakdown
            Map<String, Long> paymentMethodCounts = dailyAnalytics.getPaymentMethodCounts();
            if (paymentMethodCounts == null) {
                paymentMethodCounts = new HashMap<>();
            }
            
            String methodKey = payment.getPaymentMethod().toString();
            paymentMethodCounts.put(methodKey, paymentMethodCounts.getOrDefault(methodKey, 0L) + 1);
            dailyAnalytics.setPaymentMethodCounts(paymentMethodCounts);
            
            analyticsRepository.save(dailyAnalytics);
            
            log.debug("Updated analytics for merchant {} on date {}", merchant.getId(), paymentDate);
            
        } catch (Exception e) {
            log.error("Failed to update analytics for merchant {} payment {}", 
                merchant.getId(), payment.getId(), e);
        }
    }
    private void validateQrCodeRequest(GenerateQrCodeRequest request) {
        if (request.getType() == null) {
            throw new InvalidQrCodeException("QR code type is required");
        }
        
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new InvalidQrCodeException("Currency is required");
        }
        
        // Validate amount for static QR codes
        if (request.getType() == QrCodeType.STATIC && request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidQrCodeException("QR code amount must be positive");
            }
            
            if (request.getAmount().compareTo(maxPaymentAmount) > 0) {
                throw new InvalidQrCodeException("QR code amount exceeds maximum: " + maxPaymentAmount);
            }
        }
        
        // Validate dynamic QR code requirements
        if (request.getType() == QrCodeType.DYNAMIC) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidQrCodeException("Dynamic QR codes must have a positive amount");
            }
        }
        
        // Validate usage limit
        if (request.getUsageLimit() != null && request.getUsageLimit() <= 0) {
            throw new InvalidQrCodeException("Usage limit must be positive");
        }
        
        // Validate description length
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new InvalidQrCodeException("Description too long (max 500 characters)");
        }
        
        // Validate order ID format
        if (request.getOrderId() != null && request.getOrderId().length() > 100) {
            throw new InvalidQrCodeException("Order ID too long (max 100 characters)");
        }
    }
    private void validateQrCodeForPayment(QrPaymentCode qrCode) {
        // Check QR code status
        if (qrCode.getStatus() != QrCodeStatus.ACTIVE) {
            throw new QrCodeNotActiveException("QR code is not active");
        }
        
        // Check expiration for dynamic QR codes
        if (qrCode.getExpiresAt() != null && Instant.now().isAfter(qrCode.getExpiresAt())) {
            qrCode.setStatus(QrCodeStatus.EXPIRED);
            qrCodeRepository.save(qrCode);
            throw new QrCodeExpiredException("QR code has expired");
        }
        
        // Check usage limit
        if (qrCode.getUsageLimit() != null && qrCode.getUsageCount() >= qrCode.getUsageLimit()) {
            qrCode.setStatus(QrCodeStatus.EXPIRED);
            qrCodeRepository.save(qrCode);
            throw new QrCodeExpiredException("QR code usage limit exceeded");
        }
        
        // Validate merchant is still active
        MerchantAccount merchant = getMerchantById(qrCode.getMerchantId());
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new MerchantNotActiveException("Merchant account is not active");
        }
    }
    private String generateTerminalSerial() { return "POS" + System.currentTimeMillis(); }
    private String generateTerminalApiKey(PosTerminal terminal) { return UUID.randomUUID().toString(); }
    private Instant calculateStartDate(AnalyticsTimeframe timeframe) { return Instant.now().minus(Duration.ofDays(30)); }

    // DTO conversion methods
    private MerchantAccountDto toMerchantDto(MerchantAccount merchant) {
        return MerchantAccountDto.builder()
            .id(merchant.getId())
            .businessName(merchant.getBusinessName())
            .businessType(merchant.getBusinessType())
            .industry(merchant.getIndustry())
            .status(merchant.getStatus())
            .balance(merchant.getBalance())
            .totalVolume(merchant.getTotalVolume())
            .totalTransactions(merchant.getTotalTransactions())
            .registeredAt(merchant.getRegisteredAt())
            .build();
    }

    private MerchantPaymentDto toPaymentDto(MerchantPayment payment) {
        return MerchantPaymentDto.builder()
            .id(payment.getId())
            .merchantId(payment.getMerchantId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .status(payment.getStatus())
            .description(payment.getDescription())
            .orderId(payment.getOrderId())
            .merchantFee(payment.getMerchantFee())
            .netAmount(payment.getNetAmount())
            .processedAt(payment.getProcessedAt())
            .build();
    }

    private QrCodeDto toQrCodeDto(QrPaymentCode qrCode) {
        return QrCodeDto.builder()
            .id(qrCode.getId())
            .merchantId(qrCode.getMerchantId())
            .qrData(qrCode.getQrData())
            .amount(qrCode.getAmount())
            .currency(qrCode.getCurrency())
            .description(qrCode.getDescription())
            .type(qrCode.getType())
            .status(qrCode.getStatus())
            .expiresAt(qrCode.getExpiresAt())
            .usageCount(qrCode.getUsageCount())
            .usageLimit(qrCode.getUsageLimit())
            .build();
    }

    private PosTerminalDto toPosTerminalDto(PosTerminal terminal) {
        return PosTerminalDto.builder()
            .id(terminal.getId())
            .merchantId(terminal.getMerchantId())
            .name(terminal.getName())
            .location(terminal.getLocation())
            .type(terminal.getType())
            .serialNumber(terminal.getSerialNumber())
            .status(terminal.getStatus())
            .settings(terminal.getSettings())
            .createdAt(terminal.getCreatedAt())
            .build();
    }

    private MerchantAccount getMerchantById(String merchantId) {
        return merchantRepository.findById(merchantId)
            .orElseThrow(() -> new MerchantNotFoundException("Merchant not found"));
    }

    private QrPaymentCode getQrCodeById(String qrCodeId) {
        return qrCodeRepository.findById(qrCodeId)
            .orElseThrow(() -> new QrCodeNotFoundException("QR code not found"));
    }
}