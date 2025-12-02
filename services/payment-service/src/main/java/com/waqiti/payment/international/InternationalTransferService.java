package com.waqiti.payment.international;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.international.corridor.*;
import com.waqiti.payment.international.model.*;
import com.waqiti.payment.international.provider.*;
import com.waqiti.payment.international.compliance.*;
import com.waqiti.payment.international.repository.InternationalTransferRepository;
import com.waqiti.payment.international.exception.*;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.audit.Auditable;
import com.waqiti.common.encryption.FieldEncryption;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for handling international money transfers across different corridors
 * Supports multiple providers, compliance checking, and regulatory requirements
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class InternationalTransferService {

    private final InternationalTransferRepository transferRepository;
    private final List<TransferCorridor> transferCorridors;
    private final List<TransferProvider> transferProviders;
    private final ComplianceService complianceService;
    private final ExchangeRateService exchangeRateService;
    private final SwiftNetworkService swiftNetworkService;
    private final CorrespondentBankingService correspondentBankingService;
    private final RegulationCheckService regulationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Counter transferCounter;
    private final Counter complianceFailureCounter;
    private final Timer transferProcessingTimer;
    
    public InternationalTransferService(
            InternationalTransferRepository transferRepository,
            List<TransferCorridor> transferCorridors,
            List<TransferProvider> transferProviders,
            ComplianceService complianceService,
            ExchangeRateService exchangeRateService,
            SwiftNetworkService swiftNetworkService,
            CorrespondentBankingService correspondentBankingService,
            RegulationCheckService regulationService,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        
        this.transferRepository = transferRepository;
        this.transferCorridors = transferCorridors;
        this.transferProviders = transferProviders;
        this.complianceService = complianceService;
        this.exchangeRateService = exchangeRateService;
        this.swiftNetworkService = swiftNetworkService;
        this.correspondentBankingService = correspondentBankingService;
        this.regulationService = regulationService;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.transferCounter = Counter.builder("international.transfers.total")
                .description("Total international transfers processed")
                .register(meterRegistry);
                
        this.complianceFailureCounter = Counter.builder("international.transfers.compliance.failures")
                .description("Compliance check failures")
                .register(meterRegistry);
                
        this.transferProcessingTimer = Timer.builder("international.transfers.processing.time")
                .description("International transfer processing time")
                .register(meterRegistry);
    }
    
    /**
     * Initiate an international transfer
     */
    @Transactional
    @PreAuthorize("hasRole('USER') and @userService.canPerformAction(authentication.name, 'INTERNATIONAL_TRANSFER')")
    @RateLimited(key = "international_transfer", limit = 10, window = "1h")
    @Auditable(action = "INITIATE_INTERNATIONAL_TRANSFER")
    public CompletableFuture<InternationalTransferResponse> initiateTransfer(
            @Valid @NotNull InternationalTransferRequest request) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Initiating international transfer from {} to {} for amount {} {}",
                        request.getSenderCountry(), request.getRecipientCountry(), 
                        request.getAmount(), request.getCurrency());
                
                // 1. Validate request
                validateTransferRequest(request);
                
                // 2. Find optimal corridor
                TransferCorridor corridor = findOptimalCorridor(request);
                
                // 3. Perform compliance checks
                ComplianceCheckResult complianceResult = performComplianceChecks(request, corridor);
                if (!complianceResult.isPassed()) {
                    complianceFailureCounter.increment();
                    throw new ComplianceException("Transfer failed compliance checks: " + 
                            complianceResult.getFailureReasons());
                }
                
                // 4. Get exchange rate
                ExchangeRateQuote exchangeRate = getExchangeRate(request, corridor);
                
                // 5. Calculate fees
                FeeStructure fees = calculateFees(request, corridor, exchangeRate);
                
                // 6. Create transfer record
                InternationalTransfer transfer = createTransferRecord(request, corridor, exchangeRate, fees, complianceResult);
                
                // 7. Process through selected provider
                TransferProcessingResult processingResult = processTransfer(transfer, corridor);
                
                // 8. Update transfer status
                transfer.updateProcessingResult(processingResult);
                transferRepository.save(transfer);
                
                // 9. Increment metrics
                transferCounter.increment();
                
                // 10. Build response
                return buildTransferResponse(transfer, processingResult);
                
            } catch (Exception e) {
                log.error("Error initiating international transfer", e);
                throw new InternationalTransferException("Failed to initiate transfer: " + e.getMessage(), e);
            } finally {
                sample.stop(transferProcessingTimer);
            }
        });
    }
    
    /**
     * Get transfer quote without initiating the transfer
     */
    @PreAuthorize("hasRole('USER')")
    @RateLimited(key = "transfer_quote", limit = 50, window = "1h")
    @Cacheable(value = "transfer_quotes", key = "{#request.senderCountry, #request.recipientCountry, #request.currency, #request.targetCurrency, #request.amount}")
    public TransferQuote getTransferQuote(@Valid @NotNull TransferQuoteRequest request) {
        try {
            log.debug("Getting transfer quote from {} to {} for amount {} {}",
                    request.getSenderCountry(), request.getRecipientCountry(), 
                    request.getAmount(), request.getCurrency());
            
            // Find available corridors
            List<TransferCorridor> availableCorridors = findAvailableCorridors(
                    request.getSenderCountry(), request.getRecipientCountry());
            
            if (availableCorridors.isEmpty()) {
                throw new UnsupportedCorridorException("No transfer corridors available for " + 
                        request.getSenderCountry() + " to " + request.getRecipientCountry());
            }
            
            List<TransferOption> options = new ArrayList<>();
            
            for (TransferCorridor corridor : availableCorridors) {
                try {
                    // Get exchange rate
                    ExchangeRateQuote exchangeRate = exchangeRateService.getQuote(
                            request.getCurrency(), 
                            request.getTargetCurrency(),
                            request.getAmount(),
                            corridor.getProviderId()
                    );
                    
                    // Calculate fees
                    FeeStructure fees = corridor.calculateFees(request.getAmount(), request.getCurrency());
                    
                    // Calculate total cost and recipient amount
                    BigDecimal totalCost = request.getAmount().add(fees.getTotalFees());
                    BigDecimal recipientAmount = exchangeRate.convert(request.getAmount())
                            .subtract(fees.getRecipientFees());
                    
                    // Get delivery estimate
                    Duration estimatedDeliveryTime = corridor.getEstimatedDeliveryTime();
                    
                    TransferOption option = TransferOption.builder()
                            .providerId(corridor.getProviderId())
                            .providerName(corridor.getProviderName())
                            .corridorId(corridor.getId())
                            .exchangeRate(exchangeRate.getRate())
                            .fees(fees)
                            .totalCost(totalCost)
                            .recipientAmount(recipientAmount)
                            .estimatedDeliveryTime(estimatedDeliveryTime)
                            .supportedPaymentMethods(corridor.getSupportedPaymentMethods())
                            .supportedDeliveryMethods(corridor.getSupportedDeliveryMethods())
                            .complianceRequirements(corridor.getComplianceRequirements())
                            .build();
                    
                    options.add(option);
                    
                } catch (Exception e) {
                    log.warn("Failed to get quote from corridor {}: {}", corridor.getId(), e.getMessage());
                    // Continue with other corridors
                }
            }
            
            if (options.isEmpty()) {
                throw new NoQuoteAvailableException("No transfer quotes available at this time");
            }
            
            // Sort options by best value (lowest total cost, fastest delivery)
            options.sort((o1, o2) -> {
                // Primary: lowest total cost
                int costComparison = o1.getTotalCost().compareTo(o2.getTotalCost());
                if (costComparison != 0) {
                    return costComparison;
                }
                // Secondary: fastest delivery
                return o1.getEstimatedDeliveryTime().compareTo(o2.getEstimatedDeliveryTime());
            });
            
            return TransferQuote.builder()
                    .quoteId(UUID.randomUUID().toString())
                    .senderCountry(request.getSenderCountry())
                    .recipientCountry(request.getRecipientCountry())
                    .sourceCurrency(request.getCurrency())
                    .targetCurrency(request.getTargetCurrency())
                    .amount(request.getAmount())
                    .options(options)
                    .validUntil(Instant.now().plus(Duration.ofMinutes(15))) // 15-minute quote validity
                    .generatedAt(Instant.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Error getting transfer quote", e);
            throw new TransferQuoteException("Failed to generate transfer quote: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get supported transfer corridors
     */
    @Cacheable(value = "transfer_corridors", unless = "#result.isEmpty()")
    public List<TransferCorridorInfo> getSupportedCorridors() {
        return transferCorridors.stream()
                .filter(TransferCorridor::isActive)
                .map(corridor -> TransferCorridorInfo.builder()
                        .id(corridor.getId())
                        .name(corridor.getName())
                        .senderCountry(corridor.getSenderCountry())
                        .recipientCountry(corridor.getRecipientCountry())
                        .supportedCurrencies(corridor.getSupportedCurrencies())
                        .minAmount(corridor.getMinAmount())
                        .maxAmount(corridor.getMaxAmount())
                        .estimatedDeliveryTime(corridor.getEstimatedDeliveryTime())
                        .supportedPaymentMethods(corridor.getSupportedPaymentMethods())
                        .supportedDeliveryMethods(corridor.getSupportedDeliveryMethods())
                        .requiresCompliance(corridor.requiresComplianceCheck())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Track transfer status
     */
    @PreAuthorize("hasRole('USER') and @transferSecurityService.canAccessTransfer(authentication.name, #transferId)")
    public TransferStatus getTransferStatus(@NotNull String transferId) {
        try {
            InternationalTransfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferId));
            
            // Get real-time status from provider
            String providerTransferId = transfer.getProviderTransferId();
            if (providerTransferId != null) {
                TransferProvider provider = getProviderById(transfer.getProviderId());
                ProviderTransferStatus providerStatus = provider.getTransferStatus(providerTransferId);
                
                // Update local status if different
                if (!transfer.getStatus().equals(providerStatus.getStatus())) {
                    transfer.updateStatus(providerStatus.getStatus(), providerStatus.getStatusMessage());
                    transferRepository.save(transfer);
                }
            }
            
            return TransferStatus.builder()
                    .transferId(transfer.getId())
                    .status(transfer.getStatus())
                    .statusMessage(transfer.getStatusMessage())
                    .createdAt(transfer.getCreatedAt())
                    .updatedAt(transfer.getUpdatedAt())
                    .estimatedDeliveryTime(transfer.getEstimatedDeliveryTime())
                    .actualDeliveryTime(transfer.getActualDeliveryTime())
                    .trackingReference(transfer.getTrackingReference())
                    .providerReference(transfer.getProviderTransferId())
                    .timeline(transfer.getStatusTimeline())
                    .build();
            
        } catch (Exception e) {
            log.error("Error getting transfer status for ID: {}", transferId, e);
            throw new TransferStatusException("Failed to get transfer status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cancel a pending transfer
     */
    @Transactional
    @PreAuthorize("hasRole('USER') and @transferSecurityService.canModifyTransfer(authentication.name, #transferId)")
    @Auditable(action = "CANCEL_INTERNATIONAL_TRANSFER")
    public CancelTransferResponse cancelTransfer(@NotNull String transferId, @NotNull String reason) {
        try {
            InternationalTransfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferId));
            
            if (!transfer.isCancellable()) {
                throw new TransferNotCancellableException("Transfer cannot be cancelled in current status: " + 
                        transfer.getStatus());
            }
            
            // Cancel with provider
            TransferProvider provider = getProviderById(transfer.getProviderId());
            ProviderCancelResult cancelResult = provider.cancelTransfer(transfer.getProviderTransferId(), reason);
            
            if (cancelResult.isSuccessful()) {
                transfer.cancel(reason, cancelResult.getCancellationFee());
                transferRepository.save(transfer);
                
                return CancelTransferResponse.builder()
                        .transferId(transferId)
                        .cancelled(true)
                        .cancellationFee(cancelResult.getCancellationFee())
                        .refundAmount(transfer.getAmount().subtract(cancelResult.getCancellationFee()))
                        .refundEta(cancelResult.getRefundEta())
                        .build();
            } else {
                throw new TransferCancellationException("Provider failed to cancel transfer: " + 
                        cancelResult.getFailureReason());
            }
            
        } catch (Exception e) {
            log.error("Error cancelling transfer: {}", transferId, e);
            throw new TransferCancellationException("Failed to cancel transfer: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get transfer history for a user
     */
    @PreAuthorize("hasRole('USER')")
    public Page<TransferSummary> getTransferHistory(@NotNull String userId, Pageable pageable) {
        try {
            return transferRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                    .map(transfer -> TransferSummary.builder()
                            .transferId(transfer.getId())
                            .amount(transfer.getAmount())
                            .currency(transfer.getCurrency())
                            .recipientAmount(transfer.getRecipientAmount())
                            .recipientCurrency(transfer.getRecipientCurrency())
                            .recipientName(transfer.getRecipientName())
                            .recipientCountry(transfer.getRecipientCountry())
                            .status(transfer.getStatus())
                            .createdAt(transfer.getCreatedAt())
                            .deliveredAt(transfer.getActualDeliveryTime())
                            .providerName(getProviderById(transfer.getProviderId()).getName())
                            .build());
        } catch (Exception e) {
            log.error("Error getting transfer history for user: {}", userId, e);
            throw new TransferHistoryException("Failed to get transfer history: " + e.getMessage(), e);
        }
    }
    
    /**
     * Webhook handler for provider status updates
     */
    @Transactional
    @Auditable(action = "TRANSFER_STATUS_WEBHOOK")
    public void handleProviderWebhook(@NotNull String providerId, @NotNull ProviderWebhookPayload payload) {
        try {
            log.info("Received webhook from provider {} for transfer {}", 
                    providerId, payload.getProviderTransferId());
            
            InternationalTransfer transfer = transferRepository
                    .findByProviderTransferId(payload.getProviderTransferId())
                    .orElseThrow(() -> new TransferNotFoundException("Transfer not found with provider ID: " + 
                            payload.getProviderTransferId()));
            
            // Verify webhook authenticity
            TransferProvider provider = getProviderById(providerId);
            if (!provider.verifyWebhookSignature(payload)) {
                log.warn("Invalid webhook signature from provider {}", providerId);
                return;
            }
            
            // Update transfer status
            transfer.updateStatus(payload.getStatus(), payload.getStatusMessage());
            
            if (payload.getActualDeliveryTime() != null) {
                transfer.setActualDeliveryTime(payload.getActualDeliveryTime());
            }
            
            if (payload.getFailureReason() != null) {
                transfer.setFailureReason(payload.getFailureReason());
            }
            
            transferRepository.save(transfer);
            
            // Send notification to user
            sendStatusUpdateNotification(transfer);
            
            log.info("Updated transfer {} status to {}", transfer.getId(), payload.getStatus());
            
        } catch (Exception e) {
            log.error("Error handling provider webhook", e);
            // Don't throw exception - webhook should always return success
        }
    }
    
    // Private helper methods
    
    private void validateTransferRequest(InternationalTransferRequest request) {
        // Basic validation
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferRequestException("Transfer amount must be positive");
        }
        
        // Currency validation
        if (!isValidCurrency(request.getCurrency())) {
            throw new InvalidTransferRequestException("Unsupported source currency: " + request.getCurrency());
        }
        
        if (!isValidCurrency(request.getTargetCurrency())) {
            throw new InvalidTransferRequestException("Unsupported target currency: " + request.getTargetCurrency());
        }
        
        // Country validation
        if (!isValidCountryCode(request.getSenderCountry())) {
            throw new InvalidTransferRequestException("Invalid sender country: " + request.getSenderCountry());
        }
        
        if (!isValidCountryCode(request.getRecipientCountry())) {
            throw new InvalidTransferRequestException("Invalid recipient country: " + request.getRecipientCountry());
        }
        
        // Recipient validation
        validateRecipientInformation(request.getRecipient());
        
        // Purpose of remittance validation
        if (!isValidPurposeCode(request.getPurposeCode())) {
            throw new InvalidTransferRequestException("Invalid purpose code: " + request.getPurposeCode());
        }
    }
    
    private TransferCorridor findOptimalCorridor(InternationalTransferRequest request) {
        List<TransferCorridor> availableCorridors = findAvailableCorridors(
                request.getSenderCountry(), request.getRecipientCountry());
        
        if (availableCorridors.isEmpty()) {
            throw new UnsupportedCorridorException("No transfer corridors available for " + 
                    request.getSenderCountry() + " to " + request.getRecipientCountry());
        }
        
        // Filter by amount limits
        availableCorridors = availableCorridors.stream()
                .filter(corridor -> corridor.supportsAmount(request.getAmount(), request.getCurrency()))
                .collect(Collectors.toList());
        
        if (availableCorridors.isEmpty()) {
            throw new AmountLimitExceededException("Transfer amount outside supported limits");
        }
        
        // Filter by currency support
        availableCorridors = availableCorridors.stream()
                .filter(corridor -> corridor.supportsCurrencyPair(request.getCurrency(), request.getTargetCurrency()))
                .collect(Collectors.toList());
        
        if (availableCorridors.isEmpty()) {
            throw new UnsupportedCurrencyPairException("Currency pair not supported: " + 
                    request.getCurrency() + " to " + request.getTargetCurrency());
        }
        
        // Select optimal corridor based on cost, speed, and reliability
        return availableCorridors.stream()
                .min((c1, c2) -> compareCorridors(c1, c2, request))
                .orElseThrow(() -> new NoOptimalCorridorException("No optimal corridor found"));
    }
    
    private int compareCorridors(TransferCorridor c1, TransferCorridor c2, InternationalTransferRequest request) {
        // Calculate total cost for each corridor
        FeeStructure fees1 = c1.calculateFees(request.getAmount(), request.getCurrency());
        FeeStructure fees2 = c2.calculateFees(request.getAmount(), request.getCurrency());
        
        // Primary factor: total cost (lower is better)
        int costComparison = fees1.getTotalFees().compareTo(fees2.getTotalFees());
        if (costComparison != 0) {
            return costComparison;
        }
        
        // Secondary factor: delivery time (faster is better)
        int speedComparison = c1.getEstimatedDeliveryTime().compareTo(c2.getEstimatedDeliveryTime());
        if (speedComparison != 0) {
            return speedComparison;
        }
        
        // Tertiary factor: reliability score (higher is better)
        return Double.compare(c2.getReliabilityScore(), c1.getReliabilityScore());
    }
    
    private List<TransferCorridor> findAvailableCorridors(String senderCountry, String recipientCountry) {
        return transferCorridors.stream()
                .filter(corridor -> corridor.isActive())
                .filter(corridor -> corridor.supports(senderCountry, recipientCountry))
                .collect(Collectors.toList());
    }
    
    private ComplianceCheckResult performComplianceChecks(InternationalTransferRequest request, TransferCorridor corridor) {
        ComplianceCheckRequest complianceRequest = ComplianceCheckRequest.builder()
                .userId(request.getUserId())
                .senderCountry(request.getSenderCountry())
                .recipientCountry(request.getRecipientCountry())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .purposeCode(request.getPurposeCode())
                .recipient(request.getRecipient())
                .corridorId(corridor.getId())
                .build();
        
        return complianceService.performChecks(complianceRequest);
    }
    
    private ExchangeRateQuote getExchangeRate(InternationalTransferRequest request, TransferCorridor corridor) {
        return exchangeRateService.getQuote(
                request.getCurrency(),
                request.getTargetCurrency(),
                request.getAmount(),
                corridor.getProviderId()
        );
    }
    
    private FeeStructure calculateFees(InternationalTransferRequest request, TransferCorridor corridor, ExchangeRateQuote exchangeRate) {
        return corridor.calculateFees(request.getAmount(), request.getCurrency());
    }
    
    private InternationalTransfer createTransferRecord(
            InternationalTransferRequest request,
            TransferCorridor corridor,
            ExchangeRateQuote exchangeRate,
            FeeStructure fees,
            ComplianceCheckResult complianceResult) {
        
        return InternationalTransfer.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .corridorId(corridor.getId())
                .providerId(corridor.getProviderId())
                .senderCountry(request.getSenderCountry())
                .recipientCountry(request.getRecipientCountry())
                .currency(request.getCurrency())
                .targetCurrency(request.getTargetCurrency())
                .amount(request.getAmount())
                .exchangeRate(exchangeRate.getRate())
                .recipientAmount(exchangeRate.convert(request.getAmount()).subtract(fees.getRecipientFees()))
                .fees(fees)
                .totalCost(request.getAmount().add(fees.getTotalFees()))
                .purposeCode(request.getPurposeCode())
                .purposeDescription(request.getPurposeDescription())
                .recipient(request.getRecipient())
                .paymentMethod(request.getPaymentMethod())
                .deliveryMethod(request.getDeliveryMethod())
                .estimatedDeliveryTime(Instant.now().plus(corridor.getEstimatedDeliveryTime()))
                .status(InternationalTransferStatus.PENDING)
                .complianceCheckId(complianceResult.getCheckId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(SecurityContext.getCurrentUserId())
                .build();
    }
    
    private TransferProcessingResult processTransfer(InternationalTransfer transfer, TransferCorridor corridor) {
        TransferProvider provider = getProviderById(corridor.getProviderId());
        
        ProviderTransferRequest providerRequest = ProviderTransferRequest.builder()
                .transferId(transfer.getId())
                .senderCountry(transfer.getSenderCountry())
                .recipientCountry(transfer.getRecipientCountry())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .targetCurrency(transfer.getTargetCurrency())
                .recipient(transfer.getRecipient())
                .paymentMethod(transfer.getPaymentMethod())
                .deliveryMethod(transfer.getDeliveryMethod())
                .purposeCode(transfer.getPurposeCode())
                .purposeDescription(transfer.getPurposeDescription())
                .build();
        
        return provider.processTransfer(providerRequest);
    }
    
    private InternationalTransferResponse buildTransferResponse(InternationalTransfer transfer, TransferProcessingResult processingResult) {
        return InternationalTransferResponse.builder()
                .transferId(transfer.getId())
                .status(transfer.getStatus())
                .trackingReference(processingResult.getTrackingReference())
                .providerReference(processingResult.getProviderTransferId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .recipientAmount(transfer.getRecipientAmount())
                .recipientCurrency(transfer.getTargetCurrency())
                .exchangeRate(transfer.getExchangeRate())
                .fees(transfer.getFees())
                .totalCost(transfer.getTotalCost())
                .estimatedDeliveryTime(transfer.getEstimatedDeliveryTime())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
    
    private TransferProvider getProviderById(String providerId) {
        return transferProviders.stream()
                .filter(provider -> provider.getProviderId().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new ProviderNotFoundException("Provider not found: " + providerId));
    }
    
    private void validateRecipientInformation(RecipientInformation recipient) {
        if (recipient == null) {
            throw new InvalidTransferRequestException("Recipient information is required");
        }
        
        if (recipient.getFullName() == null || recipient.getFullName().trim().isEmpty()) {
            throw new InvalidTransferRequestException("Recipient name is required");
        }
        
        // Validate bank details if bank transfer
        if (recipient.getBankDetails() != null) {
            validateBankDetails(recipient.getBankDetails());
        }
        
        // Validate address
        if (recipient.getAddress() == null) {
            throw new InvalidTransferRequestException("Recipient address is required");
        }
    }
    
    private void validateBankDetails(BankDetails bankDetails) {
        if (bankDetails.getBankCode() == null || bankDetails.getBankCode().trim().isEmpty()) {
            throw new InvalidTransferRequestException("Bank code is required");
        }
        
        if (bankDetails.getAccountNumber() == null || bankDetails.getAccountNumber().trim().isEmpty()) {
            throw new InvalidTransferRequestException("Account number is required");
        }
        
        // Validate SWIFT/BIC code if international
        if (bankDetails.getSwiftCode() != null && !isValidSwiftCode(bankDetails.getSwiftCode())) {
            throw new InvalidTransferRequestException("Invalid SWIFT/BIC code: " + bankDetails.getSwiftCode());
        }
    }
    
    private boolean isValidCurrency(String currency) {
        return Currency.getAvailableCurrencies().stream()
                .anyMatch(c -> c.getCurrencyCode().equals(currency));
    }
    
    private boolean isValidCountryCode(String countryCode) {
        return Arrays.stream(Locale.getISOCountries())
                .anyMatch(code -> code.equals(countryCode));
    }
    
    private boolean isValidPurposeCode(String purposeCode) {
        // Validate against standard remittance purpose codes
        return PurposeCodeValidator.isValid(purposeCode);
    }
    
    private boolean isValidSwiftCode(String swiftCode) {
        // SWIFT code format: 4 letters (bank code) + 2 letters (country) + 2 characters (location) + optional 3 characters (branch)
        return swiftCode.matches("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    }
    
    private void sendStatusUpdateNotification(InternationalTransfer transfer) {
        // Send notification to user about status update
        // This would integrate with the notification service
        log.info("Sending status update notification for transfer {} to user {}", 
                transfer.getId(), transfer.getUserId());
    }
}