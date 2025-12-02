package com.waqiti.merchant.service;

import com.waqiti.merchant.dto.*;
import com.waqiti.merchant.entity.*;
import com.waqiti.merchant.exception.*;
import com.waqiti.merchant.repository.*;
import com.waqiti.common.service.NotificationService;
import com.waqiti.common.service.AuditService;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantPaymentMethodRepository paymentMethodRepository;
    private final MerchantQRCodeRepository qrCodeRepository;
    private final MerchantTransactionRepository transactionRepository;
    private final MerchantPayoutRepository payoutRepository;
    private final MerchantDisputeRepository disputeRepository;
    private final MerchantRefundRepository refundRepository;
    private final MerchantWebhookRepository webhookRepository;
    private final MerchantSettingsRepository settingsRepository;
    
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KYCClientService kycClientService;
    private final QRCodeGenerationService qrCodeGenerationService;
    private final PaymentProcessingService paymentProcessingService;
    private final AnalyticsService analyticsService;
    private final ComplianceService complianceService;

    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "MERCHANT_REGISTRATION")
    public MerchantRegistrationResponse registerMerchant(MerchantRegistrationRequest request) {
        log.info("Registering merchant: {}", request.getBusinessName());

        // Validate business information
        validateBusinessInformation(request);

        // Check if merchant already exists
        if (merchantRepository.existsByBusinessEmailOrBusinessPhone(
                request.getBusinessEmail(), request.getBusinessPhone())) {
            throw new MerchantAlreadyExistsException("Merchant with this email or phone already exists");
        }

        // Create merchant entity
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setUserId(request.getUserId());
        merchant.setBusinessName(request.getBusinessName());
        merchant.setBusinessType(BusinessType.valueOf(request.getBusinessType()));
        merchant.setBusinessCategory(request.getBusinessCategory());
        merchant.setBusinessDescription(request.getBusinessDescription());
        merchant.setBusinessEmail(request.getBusinessEmail());
        merchant.setBusinessPhone(request.getBusinessPhone());
        merchant.setBusinessWebsite(request.getBusinessWebsite());
        merchant.setBusinessAddress(mapToAddress(request.getBusinessAddress()));
        merchant.setTaxId(request.getTaxId());
        merchant.setStatus(MerchantStatus.PENDING_VERIFICATION);
        merchant.setCreatedAt(LocalDateTime.now());
        merchant.setUpdatedAt(LocalDateTime.now());

        // Set default settings
        MerchantSettings settings = createDefaultSettings(merchant);
        merchant.setSettings(settings);

        merchant = merchantRepository.save(merchant);

        // Initialize verification process
        initializeVerification(merchant);

        // Create audit entry
        auditService.logMerchantAction(merchant.getId(), "MERCHANT_REGISTERED", 
                "Merchant registered: " + merchant.getBusinessName());

        // Send welcome notification
        notificationService.sendMerchantWelcomeEmail(merchant);

        // Publish event
        eventPublisher.publishEvent(new MerchantRegisteredEvent(merchant));

        return mapToRegistrationResponse(merchant);
    }

    public MerchantProfileResponse getMerchantProfile(UUID merchantId) {
        Merchant merchant = findMerchantById(merchantId);
        return mapToProfileResponse(merchant);
    }
    
    /**
     * Get multiple merchants by IDs with optimized loading to avoid N+1 queries
     */
    @Transactional(readOnly = true)
    public List<MerchantProfileResponse> getMerchantsByIds(List<UUID> merchantIds) {
        log.info("Getting {} merchants by IDs", merchantIds.size());
        
        // Use optimized query to fetch merchants with related data
        List<Merchant> merchants = merchantRepository.findByIdsWithPaymentMethods(merchantIds);
        
        return merchants.stream()
                .map(this::mapToProfileResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get merchant risk profile - cached for performance
     */
    @Cacheable(value = "merchantRiskProfiles", key = "#merchantId")
    @Transactional(readOnly = true)
    public MerchantRiskProfile getMerchantRiskProfile(UUID merchantId) {
        log.debug("Getting risk profile for merchant: {}", merchantId);
        
        return merchantRepository.getMerchantRiskProfile(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
    }

    public MerchantProfileResponse updateMerchantProfile(UUID merchantId, MerchantUpdateRequest request) {
        Merchant merchant = findMerchantById(merchantId);

        // Update business information
        if (request.getBusinessName() != null) {
            merchant.setBusinessName(request.getBusinessName());
        }
        if (request.getBusinessDescription() != null) {
            merchant.setBusinessDescription(request.getBusinessDescription());
        }
        if (request.getBusinessEmail() != null) {
            merchant.setBusinessEmail(request.getBusinessEmail());
        }
        if (request.getBusinessPhone() != null) {
            merchant.setBusinessPhone(request.getBusinessPhone());
        }
        if (request.getBusinessWebsite() != null) {
            merchant.setBusinessWebsite(request.getBusinessWebsite());
        }
        if (request.getBusinessAddress() != null) {
            merchant.setBusinessAddress(mapToAddress(request.getBusinessAddress()));
        }

        merchant.setUpdatedAt(LocalDateTime.now());
        merchant = merchantRepository.save(merchant);

        // Log audit
        auditService.logMerchantAction(merchantId, "PROFILE_UPDATED", "Merchant profile updated");

        return mapToProfileResponse(merchant);
    }

    public VerificationResponse submitVerification(UUID merchantId, VerificationRequest request) {
        Merchant merchant = findMerchantById(merchantId);

        // Create verification record
        MerchantVerification verification = new MerchantVerification();
        verification.setId(UUID.randomUUID());
        verification.setMerchantId(merchantId);
        verification.setDocumentType(DocumentType.valueOf(request.getDocumentType()));
        verification.setDocumentNumber(request.getDocumentNumber());
        verification.setDocumentUrl(request.getDocumentUrl());
        verification.setAdditionalInfo(request.getAdditionalInfo());
        verification.setStatus(VerificationStatus.PENDING);
        verification.setSubmittedAt(LocalDateTime.now());

        verification = verificationRepository.save(verification);

        // Update merchant status
        merchant.setStatus(MerchantStatus.VERIFICATION_IN_PROGRESS);
        merchantRepository.save(merchant);

        // Submit for compliance review
        complianceService.submitForReview(verification);

        // Send notification
        notificationService.sendVerificationSubmittedNotification(merchant);

        // Log audit
        auditService.logMerchantAction(merchantId, "VERIFICATION_SUBMITTED", 
                "Verification documents submitted");

        return mapToVerificationResponse(verification);
    }

    public VerificationStatusResponse getVerificationStatus(UUID merchantId) {
        Merchant merchant = findMerchantById(merchantId);
        List<MerchantVerification> verifications = verificationRepository.findByMerchantId(merchantId);

        VerificationStatusResponse response = new VerificationStatusResponse();
        response.setMerchantId(merchantId);
        response.setOverallStatus(merchant.getStatus().name());
        response.setVerifications(verifications.stream()
                .map(this::mapToVerificationResponse)
                .collect(Collectors.toList()));
        response.setRequiredDocuments(getRequiredDocuments(merchant));
        response.setNextSteps(getVerificationNextSteps(merchant));

        return response;
    }

    public PaymentMethodResponse addPaymentMethod(UUID merchantId, PaymentMethodRequest request) {
        Merchant merchant = findMerchantById(merchantId);

        // Validate payment method
        validatePaymentMethod(request);

        MerchantPaymentMethod paymentMethod = new MerchantPaymentMethod();
        paymentMethod.setId(UUID.randomUUID());
        paymentMethod.setMerchantId(merchantId);
        paymentMethod.setType(PaymentMethodType.valueOf(request.getType()));
        paymentMethod.setAccountNumber(request.getAccountNumber());
        paymentMethod.setRoutingNumber(request.getRoutingNumber());
        paymentMethod.setBankName(request.getBankName());
        paymentMethod.setAccountHolderName(request.getAccountHolderName());
        paymentMethod.setIsDefault(request.getIsDefault());
        paymentMethod.setIsActive(true);
        paymentMethod.setCreatedAt(LocalDateTime.now());

        // If this is set as default, unset others
        if (request.getIsDefault()) {
            paymentMethodRepository.updateDefaultStatus(merchantId, false);
        }

        paymentMethod = paymentMethodRepository.save(paymentMethod);

        // Log audit
        auditService.logMerchantAction(merchantId, "PAYMENT_METHOD_ADDED", 
                "Payment method added: " + paymentMethod.getType());

        return mapToPaymentMethodResponse(paymentMethod);
    }

    public List<PaymentMethodResponse> getPaymentMethods(UUID merchantId) {
        List<MerchantPaymentMethod> paymentMethods = paymentMethodRepository.findByMerchantIdAndIsActiveTrue(merchantId);
        return paymentMethods.stream()
                .map(this::mapToPaymentMethodResponse)
                .collect(Collectors.toList());
    }

    public void removePaymentMethod(UUID merchantId, UUID paymentMethodId) {
        MerchantPaymentMethod paymentMethod = paymentMethodRepository
                .findByIdAndMerchantId(paymentMethodId, merchantId)
                .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found"));

        paymentMethod.setIsActive(false);
        paymentMethod.setUpdatedAt(LocalDateTime.now());
        paymentMethodRepository.save(paymentMethod);

        // Log audit
        auditService.logMerchantAction(merchantId, "PAYMENT_METHOD_REMOVED", 
                "Payment method removed: " + paymentMethod.getType());
    }

    public QRCodeResponse generateQRCode(UUID merchantId, QRCodeRequest request) {
        Merchant merchant = findMerchantById(merchantId);

        MerchantQRCode qrCode = new MerchantQRCode();
        qrCode.setId(UUID.randomUUID());
        qrCode.setMerchantId(merchantId);
        qrCode.setName(request.getName());
        qrCode.setDescription(request.getDescription());
        qrCode.setAmount(request.getAmount());
        qrCode.setCurrency(request.getCurrency());
        qrCode.setIsActive(true);
        qrCode.setCreatedAt(LocalDateTime.now());

        // Generate QR code image
        String qrCodeData = qrCodeGenerationService.generateMerchantQRCode(merchant, qrCode);
        qrCode.setQrCodeData(qrCodeData);
        qrCode.setQrCodeUrl(qrCodeGenerationService.generateQRCodeImageUrl(qrCodeData));

        qrCode = qrCodeRepository.save(qrCode);

        // Log audit
        auditService.logMerchantAction(merchantId, "QR_CODE_GENERATED", 
                "QR code generated: " + qrCode.getName());

        return mapToQRCodeResponse(qrCode);
    }

    public Page<QRCodeResponse> getQRCodes(UUID merchantId, Pageable pageable) {
        Page<MerchantQRCode> qrCodes = qrCodeRepository.findByMerchantIdAndIsActiveTrue(merchantId, pageable);
        return qrCodes.map(this::mapToQRCodeResponse);
    }

    public Page<MerchantTransactionResponse> getTransactions(UUID merchantId, LocalDate startDate, 
            LocalDate endDate, String status, Pageable pageable) {
        Page<MerchantTransaction> transactions;
        
        if (startDate != null || endDate != null || status != null) {
            transactions = transactionRepository.findByMerchantIdWithFilters(
                    merchantId, startDate, endDate, status, pageable);
        } else {
            transactions = transactionRepository.findByMerchantId(merchantId, pageable);
        }

        return transactions.map(this::mapToTransactionResponse);
    }

    public MerchantAnalyticsResponse getAnalytics(UUID merchantId, LocalDate startDate, 
            LocalDate endDate, String granularity) {
        return analyticsService.generateMerchantAnalytics(merchantId, startDate, endDate, granularity);
    }

    public PayoutResponse requestPayout(UUID merchantId, PayoutRequest request) {
        Merchant merchant = findMerchantById(merchantId);

        // Validate payout request
        validatePayoutRequest(merchant, request);
        
        // Enhanced KYC check for high-value payouts
        if (request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(merchant.getUserId().toString())) {
                throw new MerchantComplianceException(
                    "Enhanced KYC verification required for merchant payouts over $5,000");
            }
        }

        MerchantPayout payout = new MerchantPayout();
        payout.setId(UUID.randomUUID());
        payout.setMerchantId(merchantId);
        payout.setAmount(request.getAmount());
        payout.setCurrency(request.getCurrency());
        payout.setPaymentMethodId(request.getPaymentMethodId());
        payout.setStatus(PayoutStatus.PENDING);
        payout.setRequestedAt(LocalDateTime.now());

        payout = payoutRepository.save(payout);

        // Process payout
        paymentProcessingService.processPayout(payout);

        // Log audit
        auditService.logMerchantAction(merchantId, "PAYOUT_REQUESTED", 
                "Payout requested: " + request.getAmount());

        return mapToPayoutResponse(payout);
    }

    public Page<PayoutResponse> getPayoutHistory(UUID merchantId, Pageable pageable) {
        Page<MerchantPayout> payouts = payoutRepository.findByMerchantId(merchantId, pageable);
        return payouts.map(this::mapToPayoutResponse);
    }

    public MerchantBalanceResponse getBalance(UUID merchantId) {
        Merchant merchant = findMerchantById(merchantId);
        
        BigDecimal totalBalance = transactionRepository.calculateTotalBalance(merchantId);
        BigDecimal pendingBalance = transactionRepository.calculatePendingBalance(merchantId);
        BigDecimal availableBalance = totalBalance.subtract(pendingBalance);

        MerchantBalanceResponse response = new MerchantBalanceResponse();
        response.setMerchantId(merchantId);
        response.setTotalBalance(totalBalance);
        response.setPendingBalance(pendingBalance);
        response.setAvailableBalance(availableBalance);
        response.setCurrency("USD");
        response.setLastUpdated(LocalDateTime.now());

        return response;
    }

    public DisputeResponse createDispute(UUID merchantId, DisputeRequest request) {
        MerchantDispute dispute = new MerchantDispute();
        dispute.setId(UUID.randomUUID());
        dispute.setMerchantId(merchantId);
        dispute.setTransactionId(request.getTransactionId());
        dispute.setReason(request.getReason());
        dispute.setDescription(request.getDescription());
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setCreatedAt(LocalDateTime.now());

        dispute = disputeRepository.save(dispute);

        // Log audit
        auditService.logMerchantAction(merchantId, "DISPUTE_CREATED", 
                "Dispute created for transaction: " + request.getTransactionId());

        return mapToDisputeResponse(dispute);
    }

    public Page<DisputeResponse> getDisputes(UUID merchantId, String status, Pageable pageable) {
        Page<MerchantDispute> disputes;
        
        if (status != null) {
            disputes = disputeRepository.findByMerchantIdAndStatus(
                    merchantId, DisputeStatus.valueOf(status), pageable);
        } else {
            disputes = disputeRepository.findByMerchantId(merchantId, pageable);
        }

        return disputes.map(this::mapToDisputeResponse);
    }

    public RefundResponse processRefund(UUID merchantId, RefundRequest request) {
        MerchantRefund refund = new MerchantRefund();
        refund.setId(UUID.randomUUID());
        refund.setMerchantId(merchantId);
        refund.setTransactionId(request.getTransactionId());
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason());
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setRequestedAt(LocalDateTime.now());

        refund = refundRepository.save(refund);

        // Process refund
        paymentProcessingService.processRefund(refund);

        // Log audit
        auditService.logMerchantAction(merchantId, "REFUND_PROCESSED", 
                "Refund processed: " + request.getAmount());

        return mapToRefundResponse(refund);
    }

    public Page<RefundResponse> getRefundHistory(UUID merchantId, Pageable pageable) {
        Page<MerchantRefund> refunds = refundRepository.findByMerchantId(merchantId, pageable);
        return refunds.map(this::mapToRefundResponse);
    }

    public WebhookEndpointResponse addWebhookEndpoint(UUID merchantId, WebhookEndpointRequest request) {
        MerchantWebhook webhook = new MerchantWebhook();
        webhook.setId(UUID.randomUUID());
        webhook.setMerchantId(merchantId);
        webhook.setUrl(request.getUrl());
        webhook.setEvents(request.getEvents());
        webhook.setSecret(generateWebhookSecret());
        webhook.setIsActive(true);
        webhook.setCreatedAt(LocalDateTime.now());

        webhook = webhookRepository.save(webhook);

        // Log audit
        auditService.logMerchantAction(merchantId, "WEBHOOK_ADDED", 
                "Webhook endpoint added: " + request.getUrl());

        return mapToWebhookResponse(webhook);
    }

    public List<WebhookEndpointResponse> getWebhookEndpoints(UUID merchantId) {
        List<MerchantWebhook> webhooks = webhookRepository.findByMerchantIdAndIsActiveTrue(merchantId);
        return webhooks.stream()
                .map(this::mapToWebhookResponse)
                .collect(Collectors.toList());
    }

    public MerchantSettingsResponse updateSettings(UUID merchantId, MerchantSettingsRequest request) {
        MerchantSettings settings = settingsRepository.findByMerchantId(merchantId)
                .orElse(createDefaultSettings(findMerchantById(merchantId)));

        // Update settings
        if (request.getAutoPayoutEnabled() != null) {
            settings.setAutoPayoutEnabled(request.getAutoPayoutEnabled());
        }
        if (request.getAutoPayoutThreshold() != null) {
            settings.setAutoPayoutThreshold(request.getAutoPayoutThreshold());
        }
        if (request.getNotificationSettings() != null) {
            settings.setNotificationSettings(request.getNotificationSettings());
        }

        settings.setUpdatedAt(LocalDateTime.now());
        settings = settingsRepository.save(settings);

        // Log audit
        auditService.logMerchantAction(merchantId, "SETTINGS_UPDATED", "Merchant settings updated");

        return mapToSettingsResponse(settings);
    }

    public MerchantSettingsResponse getSettings(UUID merchantId) {
        MerchantSettings settings = settingsRepository.findByMerchantId(merchantId)
                .orElse(createDefaultSettings(findMerchantById(merchantId)));

        return mapToSettingsResponse(settings);
    }

    public Page<MerchantSearchResult> searchMerchants(String query, String category, String location,
            BigDecimal latitude, BigDecimal longitude, Integer radiusKm, Pageable pageable) {
        
        Page<Merchant> merchants = merchantRepository.searchMerchants(
                query, category, location, latitude, longitude, radiusKm, pageable);
        
        return merchants.map(this::mapToSearchResult);
    }

    // Helper methods
    private Merchant findMerchantById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
    }

    private void validateBusinessInformation(MerchantRegistrationRequest request) {
        // Implement business validation logic
        if (request.getBusinessName() == null || request.getBusinessName().trim().isEmpty()) {
            throw new InvalidBusinessInfoException("Business name is required");
        }
        // Add more validations as needed
    }

    private void validatePaymentMethod(PaymentMethodRequest request) {
        // Implement payment method validation
        if (request.getAccountNumber() == null || request.getAccountNumber().trim().isEmpty()) {
            throw new InvalidPaymentMethodException("Account number is required");
        }
        // Add more validations
    }

    private void validatePayoutRequest(Merchant merchant, PayoutRequest request) {
        // Validate payout amount and availability
        MerchantBalanceResponse balance = getBalance(merchant.getId());
        if (request.getAmount().compareTo(balance.getAvailableBalance()) > 0) {
            throw new InsufficientBalanceException("Insufficient balance for payout");
        }
    }

    // Mapping methods would be implemented here
    private MerchantRegistrationResponse mapToRegistrationResponse(Merchant merchant) {
        // Implementation
        return new MerchantRegistrationResponse();
    }

    private MerchantProfileResponse mapToProfileResponse(Merchant merchant) {
        // Implementation
        return new MerchantProfileResponse();
    }

    // Additional mapping methods...

    private String generateWebhookSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private MerchantSettings createDefaultSettings(Merchant merchant) {
        MerchantSettings settings = new MerchantSettings();
        settings.setId(UUID.randomUUID());
        settings.setMerchantId(merchant.getId());
        settings.setAutoPayoutEnabled(false);
        settings.setAutoPayoutThreshold(new BigDecimal("1000.00"));
        settings.setCreatedAt(LocalDateTime.now());
        settings.setUpdatedAt(LocalDateTime.now());
        return settings;
    }

    private void initializeVerification(Merchant merchant) {
        // Initialize verification process
    }

    private List<String> getRequiredDocuments(Merchant merchant) {
        // Return list of required documents based on business type
        return List.of("Business License", "Tax ID", "Bank Account Verification");
    }

    private List<String> getVerificationNextSteps(Merchant merchant) {
        // Return next steps in verification process
        return List.of("Submit business license", "Verify bank account");
    }
}