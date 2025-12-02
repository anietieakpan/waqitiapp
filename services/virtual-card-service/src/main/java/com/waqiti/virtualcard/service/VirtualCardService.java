package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.domain.*;
import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.repository.*;
import com.waqiti.virtualcard.provider.CardProvider;
import com.waqiti.virtualcard.security.CardEncryptionService;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.VirtualCardEvent;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.exception.*;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Virtual Card Service - Manages virtual debit/credit cards
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualCardService {

    private final VirtualCardRepository cardRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardLimitRepository limitRepository;
    private final CardControlRepository controlRepository;
    private final CardProvider cardProvider;
    private final CardEncryptionService encryptionService;
    private final FraudDetectionService fraudDetectionService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final KYCClientService kycClientService;
    private final PhysicalCardService physicalCardService;
    private final MfaVerificationService mfaVerificationService;
    private final AuditService auditService;

    @Value("${virtual-card.max-cards-per-user:5}")
    private int maxCardsPerUser;
    
    @Value("${virtual-card.default-expiry-years:3}")
    private int defaultExpiryYears;

    /**
     * Create a new virtual card
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "VIRTUAL_CARD")
    public VirtualCardDto createVirtualCard(CreateCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        // Validate request
        validateCreateCardRequest(request, userId);
        
        // If physical card requested, delegate to PhysicalCardService
        if (request.getType() == CardType.PHYSICAL) {
            return createPhysicalCard(request, userId);
        }
        
        // Check card limit
        long activeCards = cardRepository.countByUserIdAndStatusIn(
            userId, List.of(CardStatus.ACTIVE, CardStatus.FROZEN)
        );
        if (activeCards >= maxCardsPerUser) {
            throw new CardLimitExceededException("Maximum card limit reached");
        }
        
        // Check wallet balance for funding
        BigDecimal walletBalance = walletService.getBalance(userId, request.getCurrency());
        if (request.getInitialBalance() != null && 
            request.getInitialBalance().compareTo(walletBalance) > 0) {
            throw new InsufficientFundsException("Insufficient wallet balance");
        }
        
        try {
            // Generate card with provider
            CardProviderResponse providerResponse = cardProvider.createCard(
                CardProviderRequest.builder()
                    .userId(userId)
                    .type(request.getType())
                    .currency(request.getCurrency())
                    .cardholderName(request.getCardholderName())
                    .billingAddress(request.getBillingAddress())
                    .build()
            );
            
            // Create card entity
            VirtualCard card = VirtualCard.builder()
                .userId(userId)
                .providerId(providerResponse.getProviderId())
                .type(request.getType())
                .brand(CardBrand.VISA) // Default brand
                .status(CardStatus.ACTIVE)
                .nickname(request.getNickname())
                .currency(request.getCurrency())
                .balance(request.getInitialBalance() != null ? 
                    request.getInitialBalance() : BigDecimal.ZERO)
                .cardholderName(request.getCardholderName())
                .lastFourDigits(providerResponse.getLastFourDigits())
                .expiryMonth(providerResponse.getExpiryMonth())
                .expiryYear(providerResponse.getExpiryYear())
                .billingAddress(encryptAddress(request.getBillingAddress()))
                .createdAt(Instant.now())
                .activatedAt(Instant.now())
                .build();
            
            // PCI DSS COMPLIANT: Encrypt only PAN, NEVER store CVV per Requirement 3.2.2
            // CVV is dynamically retrieved from provider when needed for authorization
            card.setEncryptedCardNumber(encryptionService.encrypt(
                providerResponse.getCardNumber()
            ));
            // CVV REMOVED: No longer stored in database per PCI DSS 3.2.2
            // Dynamic CVV is retrieved from provider API when user requests card details

            card = cardRepository.save(card);
            
            // Set default limits
            setDefaultLimits(card.getId());
            
            // Set default controls
            setDefaultControls(card.getId());
            
            // Fund card if initial balance provided
            if (request.getInitialBalance() != null && 
                request.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
                fundCard(card.getId(), request.getInitialBalance());
            }
            
            // Send notification
            notificationService.sendCardCreatedNotification(userId, card);
            
            // Publish event
            eventPublisher.publish(VirtualCardEvent.cardCreated(card));
            
            log.info("Created virtual card {} for user {}", card.getId(), userId);
            
            return toDto(card, true); // Include sensitive data for initial response
            
        } catch (Exception e) {
            log.error("Failed to create virtual card for user {}", userId, e);
            throw new CardCreationException("Failed to create virtual card", e);
        }
    }

    /**
     * Get card details
     */
    @Transactional(readOnly = true)
    public VirtualCardDto getCardDetails(String cardId, boolean includeSensitive) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        // Log access for security
        log.info("Card {} accessed by user {} with sensitive={}", 
            cardId, userId, includeSensitive);
        
        return toDto(card, includeSensitive);
    }

    /**
     * Get card number and CVV (requires additional authentication)
     *
     * PCI DSS COMPLIANT IMPLEMENTATION:
     * - CVV is retrieved dynamically from card provider (never stored)
     * - Requires device-based 2FA/MFA verification
     * - All access is logged for audit trail
     * - User receives security notification
     * - CVV is transmitted over TLS and immediately discarded after display
     */
    @Transactional(readOnly = true)
    public CardDetailsDto getCardSecrets(String cardId, String authenticationToken) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);

        // Verify device-based 2FA/MFA authentication
        if (!verifyAdditionalAuth(userId, authenticationToken)) {
            // Log failed authentication attempt
            log.warn("Failed card secrets access attempt for card {} by user {} - MFA verification failed",
                cardId, userId);
            throw new SecurityException("Multi-factor authentication required to view card details");
        }

        try {
            // Decrypt PAN from database
            String cardNumber = encryptionService.decrypt(card.getEncryptedCardNumber());

            // PCI DSS COMPLIANT: Retrieve dynamic CVV from provider (never from database)
            // CVV is generated on-demand by the card network/provider
            String dynamicCvv = cardProvider.getDynamicCvv(card.getProviderId());

            // Log sensitive access for security audit
            log.warn("SECURITY AUDIT: Sensitive card data accessed - cardId={}, userId={}, timestamp={}",
                cardId, userId, Instant.now());

            // Send real-time security notification to user
            notificationService.sendSecurityAlert(userId,
                "Your virtual card details were accessed on " + Instant.now());

            // Record access in audit log
            auditService.logSensitiveDataAccess(
                userId,
                cardId,
                "CARD_SECRETS_VIEWED",
                Map.of(
                    "authMethod", "DEVICE_MFA",
                    "ipAddress", securityContext.getClientIpAddress(),
                    "userAgent", securityContext.getUserAgent()
                )
            );

            return CardDetailsDto.builder()
                .cardNumber(cardNumber)
                .cvv(dynamicCvv)  // Dynamic CVV from provider, never stored
                .expiryMonth(card.getExpiryMonth())
                .expiryYear(card.getExpiryYear())
                .cardholderName(card.getCardholderName())
                .build();

        } catch (Exception e) {
            log.error("Failed to retrieve card secrets for card {} - userId={}", cardId, userId, e);
            // Send alert for failed sensitive data access
            notificationService.sendSecurityAlert(userId,
                "Failed attempt to access your card details");
            throw new CardSecretsRetrievalException("Unable to retrieve card details. Please try again.", e);
        }
    }

    /**
     * Freeze/Unfreeze card
     */
    @Transactional
    public VirtualCardDto toggleCardFreeze(String cardId, boolean freeze) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        if (freeze && card.getStatus() == CardStatus.FROZEN) {
            throw new IllegalStateException("Card is already frozen");
        }
        
        if (!freeze && card.getStatus() != CardStatus.FROZEN) {
            throw new IllegalStateException("Card is not frozen");
        }
        
        // Update card status
        CardStatus previousStatus = card.getStatus();
        card.setStatus(freeze ? CardStatus.FROZEN : CardStatus.ACTIVE);
        card.setLastStatusChange(Instant.now());
        
        // Update with provider
        cardProvider.updateCardStatus(card.getProviderId(), card.getStatus());
        
        card = cardRepository.save(card);
        
        // Send notification
        notificationService.sendCardStatusNotification(userId, card, freeze);
        
        // Publish event
        eventPublisher.publish(VirtualCardEvent.cardStatusChanged(card, previousStatus));
        
        log.info("Card {} {} by user {}", cardId, freeze ? "frozen" : "unfrozen", userId);
        
        return toDto(card, false);
    }

    /**
     * Update card limits
     */
    @Transactional
    public CardLimitsDto updateCardLimits(String cardId, UpdateLimitsRequest request) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        validateLimits(request);
        
        CardLimits limits = limitRepository.findByCardId(cardId)
            .orElseGet(() -> CardLimits.builder().cardId(cardId).build());
        
        // Update limits
        if (request.getDailyLimit() != null) {
            limits.setDailyLimit(request.getDailyLimit());
        }
        if (request.getWeeklyLimit() != null) {
            limits.setWeeklyLimit(request.getWeeklyLimit());
        }
        if (request.getMonthlyLimit() != null) {
            limits.setMonthlyLimit(request.getMonthlyLimit());
        }
        if (request.getTransactionLimit() != null) {
            limits.setTransactionLimit(request.getTransactionLimit());
        }
        if (request.getAtmLimit() != null) {
            limits.setAtmLimit(request.getAtmLimit());
        }
        if (request.getOnlineLimit() != null) {
            limits.setOnlineLimit(request.getOnlineLimit());
        }
        
        limits = limitRepository.save(limits);
        
        // Update with provider
        cardProvider.updateCardLimits(card.getProviderId(), limits);
        
        log.info("Updated limits for card {} by user {}", cardId, userId);
        
        return toLimitsDto(limits);
    }

    /**
     * Update card controls
     */
    @Transactional
    public CardControlsDto updateCardControls(String cardId, UpdateControlsRequest request) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        CardControls controls = controlRepository.findByCardId(cardId)
            .orElseGet(() -> CardControls.builder().cardId(cardId).build());
        
        // Update controls
        if (request.getOnlineTransactions() != null) {
            controls.setOnlineTransactions(request.getOnlineTransactions());
        }
        if (request.getInternationalTransactions() != null) {
            controls.setInternationalTransactions(request.getInternationalTransactions());
        }
        if (request.getAtmWithdrawals() != null) {
            controls.setAtmWithdrawals(request.getAtmWithdrawals());
        }
        if (request.getContactlessPayments() != null) {
            controls.setContactlessPayments(request.getContactlessPayments());
        }
        if (request.getAllowedMerchantCategories() != null) {
            controls.setAllowedMerchantCategories(request.getAllowedMerchantCategories());
        }
        if (request.getBlockedMerchantCategories() != null) {
            controls.setBlockedMerchantCategories(request.getBlockedMerchantCategories());
        }
        if (request.getAllowedCountries() != null) {
            controls.setAllowedCountries(request.getAllowedCountries());
        }
        if (request.getBlockedCountries() != null) {
            controls.setBlockedCountries(request.getBlockedCountries());
        }
        
        controls = controlRepository.save(controls);
        
        // Update with provider
        cardProvider.updateCardControls(card.getProviderId(), controls);
        
        log.info("Updated controls for card {} by user {}", cardId, userId);
        
        return toControlsDto(controls);
    }

    /**
     * Fund virtual card from wallet
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.INTERMEDIATE, action = "CARD_FUNDING")
    public CardTransactionDto fundCard(String cardId, BigDecimal amount) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new IllegalStateException("Card is not active");
        }
        
        // Check wallet balance
        BigDecimal walletBalance = walletService.getBalance(userId, card.getCurrency());
        if (amount.compareTo(walletBalance) > 0) {
            throw new InsufficientFundsException("Insufficient wallet balance");
        }
        
        // Process funding
        try {
            // Deduct from wallet
            walletService.debit(userId, amount, card.getCurrency(), 
                "Virtual card funding", Map.of("cardId", cardId));
            
            // Update card balance
            card.setBalance(card.getBalance().add(amount));
            card = cardRepository.save(card);
            
            // Record transaction
            CardTransaction transaction = CardTransaction.builder()
                .cardId(cardId)
                .type(TransactionType.FUNDING)
                .amount(amount)
                .currency(card.getCurrency())
                .description("Card funding from wallet")
                .status(TransactionStatus.COMPLETED)
                .balanceAfter(card.getBalance())
                .processedAt(Instant.now())
                .build();
            
            transaction = transactionRepository.save(transaction);
            
            // Notify provider
            cardProvider.updateCardBalance(card.getProviderId(), card.getBalance());
            
            // Send notification
            notificationService.sendCardFundedNotification(userId, card, amount);
            
            log.info("Funded card {} with {} for user {}", cardId, amount, userId);
            
            return toTransactionDto(transaction);
            
        } catch (Exception e) {
            log.error("Failed to fund card {} for user {}", cardId, userId, e);
            throw new CardFundingException("Failed to fund card", e);
        }
    }

    /**
     * Withdraw from virtual card to wallet
     */
    @Transactional
    public CardTransactionDto withdrawFromCard(String cardId, BigDecimal amount) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new IllegalStateException("Card is not active");
        }
        
        if (amount.compareTo(card.getBalance()) > 0) {
            throw new InsufficientFundsException("Insufficient card balance");
        }
        
        try {
            // Update card balance
            card.setBalance(card.getBalance().subtract(amount));
            card = cardRepository.save(card);
            
            // Credit to wallet
            walletService.credit(userId, amount, card.getCurrency(), 
                "Virtual card withdrawal", Map.of("cardId", cardId));
            
            // Record transaction
            CardTransaction transaction = CardTransaction.builder()
                .cardId(cardId)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .currency(card.getCurrency())
                .description("Withdrawal to wallet")
                .status(TransactionStatus.COMPLETED)
                .balanceAfter(card.getBalance())
                .processedAt(Instant.now())
                .build();
            
            transaction = transactionRepository.save(transaction);
            
            // Notify provider
            cardProvider.updateCardBalance(card.getProviderId(), card.getBalance());
            
            // Send notification
            notificationService.sendCardWithdrawalNotification(userId, card, amount);
            
            log.info("Withdrew {} from card {} for user {}", amount, cardId, userId);
            
            return toTransactionDto(transaction);
            
        } catch (Exception e) {
            log.error("Failed to withdraw from card {} for user {}", cardId, userId, e);
            throw new CardWithdrawalException("Failed to withdraw from card", e);
        }
    }

    /**
     * Process card transaction (called by provider webhook)
     */
    @Transactional
    public void processCardTransaction(CardTransactionWebhook webhook) {
        VirtualCard card = cardRepository.findByProviderId(webhook.getCardProviderId())
            .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        // Fraud check
        FraudCheckResult fraudResult = fraudDetectionService.checkCardTransaction(
            card, webhook
        );
        
        if (fraudResult.isBlocked()) {
            // Decline transaction
            cardProvider.declineTransaction(webhook.getTransactionId(), 
                fraudResult.getReason());
            
            // Record declined transaction
            recordDeclinedTransaction(card, webhook, fraudResult.getReason());
            
            // Alert user
            notificationService.sendFraudAlert(card.getUserId(), card, webhook);
            
            return;
        }
        
        // Check limits
        if (!checkTransactionLimits(card, webhook)) {
            cardProvider.declineTransaction(webhook.getTransactionId(), 
                "Transaction limit exceeded");
            recordDeclinedTransaction(card, webhook, "Limit exceeded");
            return;
        }
        
        // Check balance
        if (webhook.getAmount().compareTo(card.getBalance()) > 0) {
            cardProvider.declineTransaction(webhook.getTransactionId(), 
                "Insufficient balance");
            recordDeclinedTransaction(card, webhook, "Insufficient balance");
            return;
        }
        
        // Process transaction
        try {
            // Update card balance
            card.setBalance(card.getBalance().subtract(webhook.getAmount()));
            card.setLastUsedAt(Instant.now());
            card = cardRepository.save(card);
            
            // Record transaction
            CardTransaction transaction = CardTransaction.builder()
                .cardId(card.getId())
                .providerTransactionId(webhook.getTransactionId())
                .type(TransactionType.PURCHASE)
                .amount(webhook.getAmount())
                .currency(webhook.getCurrency())
                .merchantName(webhook.getMerchantName())
                .merchantCategory(webhook.getMerchantCategory())
                .merchantCountry(webhook.getMerchantCountry())
                .description(webhook.getDescription())
                .status(TransactionStatus.COMPLETED)
                .balanceAfter(card.getBalance())
                .processedAt(Instant.now())
                .metadata(webhook.getMetadata())
                .build();
            
            transaction = transactionRepository.save(transaction);
            
            // Approve with provider
            cardProvider.approveTransaction(webhook.getTransactionId());
            
            // Send notification
            notificationService.sendTransactionNotification(
                card.getUserId(), card, transaction
            );
            
            // Update spending analytics
            updateSpendingAnalytics(card, transaction);
            
            log.info("Processed transaction {} for card {}", 
                webhook.getTransactionId(), card.getId());
            
        } catch (Exception e) {
            log.error("Failed to process transaction {} for card {}", 
                webhook.getTransactionId(), card.getId(), e);
            
            // Rollback and decline
            cardProvider.declineTransaction(webhook.getTransactionId(), 
                "Processing error");
            throw e;
        }
    }

    /**
     * Get card transactions
     */
    @Transactional(readOnly = true)
    public Page<CardTransactionDto> getCardTransactions(String cardId, 
                                                       TransactionFilter filter,
                                                       Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        Page<CardTransaction> transactions = transactionRepository.findByCardIdAndFilters(
            cardId, filter, pageable
        );
        
        return transactions.map(this::toTransactionDto);
    }

    /**
     * Get spending analytics
     */
    @Transactional(readOnly = true)
    public SpendingAnalyticsDto getSpendingAnalytics(String cardId, 
                                                    AnalyticsTimeframe timeframe) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        Instant startDate = calculateStartDate(timeframe);
        
        // Get spending by category
        List<CategorySpending> categorySpending = transactionRepository
            .getSpendingByCategory(cardId, startDate);
        
        // Get spending trend
        List<DailySpending> dailySpending = transactionRepository
            .getDailySpending(cardId, startDate);
        
        // Get top merchants
        List<MerchantSpending> topMerchants = transactionRepository
            .getTopMerchants(cardId, startDate, 10);
        
        // Calculate totals
        BigDecimal totalSpent = categorySpending.stream()
            .map(CategorySpending::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long transactionCount = transactionRepository.countByCardIdAndProcessedAtAfter(
            cardId, startDate
        );
        
        return SpendingAnalyticsDto.builder()
            .cardId(cardId)
            .timeframe(timeframe)
            .totalSpent(totalSpent)
            .transactionCount(transactionCount)
            .averageTransaction(transactionCount > 0 ? 
                totalSpent.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .categorySpending(categorySpending)
            .dailySpending(dailySpending)
            .topMerchants(topMerchants)
            .build();
    }

    /**
     * Delete/Close virtual card
     */
    @Transactional
    public void deleteCard(String cardId) {
        String userId = securityContext.getCurrentUserId();
        VirtualCard card = getCardByIdAndUser(cardId, userId);
        
        // Check if card has balance
        if (card.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                "Cannot delete card with remaining balance. Please withdraw funds first."
            );
        }
        
        // Close with provider
        cardProvider.closeCard(card.getProviderId());
        
        // Update status
        card.setStatus(CardStatus.CLOSED);
        card.setClosedAt(Instant.now());
        cardRepository.save(card);
        
        // Clear sensitive data after grace period
        scheduleDataDeletion(card.getId());
        
        // Send notification
        notificationService.sendCardClosedNotification(userId, card);
        
        // Publish event
        eventPublisher.publish(VirtualCardEvent.cardClosed(card));
        
        log.info("Closed card {} for user {}", cardId, userId);
    }

    /**
     * Create physical card by delegating to PhysicalCardService
     */
    private VirtualCardDto createPhysicalCard(CreateCardRequest request, String userId) {
        try {
            // Convert CreateCardRequest to OrderCardRequest for PhysicalCardService
            OrderCardRequest orderRequest = OrderCardRequest.builder()
                .brand(request.getBrand() != null ? request.getBrand() : CardBrand.VISA)
                .currency(request.getCurrency())
                .design(request.getDesign())
                .personalization(request.getPersonalization())
                .shippingAddress(request.getShippingAddress())
                .shippingMethod(request.getShippingMethod() != null ? 
                    request.getShippingMethod() : ShippingMethod.STANDARD)
                .build();
            
            // Order physical card through PhysicalCardService
            CardOrderDto cardOrder = physicalCardService.orderPhysicalCard(orderRequest);
            
            // Get the physical card details
            PhysicalCardDto physicalCard = physicalCardService.getPhysicalCard(cardOrder.getCardId());
            
            // Convert PhysicalCardDto to VirtualCardDto for consistent API response
            return VirtualCardDto.builder()
                .id(physicalCard.getId())
                .type(physicalCard.getType())
                .brand(physicalCard.getBrand())
                .status(physicalCard.getStatus())
                .currency(physicalCard.getCurrency())
                .balance(physicalCard.getBalance())
                .lastFourDigits(physicalCard.getLastFourDigits())
                .expiryMonth(physicalCard.getExpiryMonth())
                .expiryYear(physicalCard.getExpiryYear())
                .cardholderName(request.getCardholderName())
                .createdAt(physicalCard.getOrderedAt())
                .estimatedDelivery(physicalCard.getEstimatedDelivery())
                .isPhysical(true)
                .orderId(physicalCard.getOrderId())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create physical card for user {}", userId, e);
            throw new CardCreationException("Failed to create physical card", e);
        }
    }

    // Scheduled job to expire cards
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void expireCards() {
        YearMonth currentMonth = YearMonth.now();
        
        List<VirtualCard> expiredCards = cardRepository.findByExpiryMonthAndExpiryYearAndStatus(
            currentMonth.getMonthValue(),
            currentMonth.getYear(),
            CardStatus.ACTIVE
        );
        
        for (VirtualCard card : expiredCards) {
            try {
                card.setStatus(CardStatus.EXPIRED);
                card.setExpiredAt(Instant.now());
                cardRepository.save(card);
                
                // Notify provider
                cardProvider.expireCard(card.getProviderId());
                
                // Notify user
                notificationService.sendCardExpiredNotification(card.getUserId(), card);
                
                log.info("Expired card {} for user {}", card.getId(), card.getUserId());
                
            } catch (Exception e) {
                log.error("Failed to expire card {}", card.getId(), e);
            }
        }
    }

    private void validateCreateCardRequest(CreateCardRequest request, String userId) {
        // Validate card type - physical cards are now supported via PhysicalCardService
        if (request.getType() == CardType.PHYSICAL) {
            // Validate required fields for physical cards
            if (request.getShippingAddress() == null) {
                throw new IllegalArgumentException("Shipping address is required for physical cards");
            }
            
            // Validate shipping address completeness
            ShippingAddress address = request.getShippingAddress();
            if (address.getLine1() == null || address.getLine1().trim().isEmpty() ||
                address.getCity() == null || address.getCity().trim().isEmpty() ||
                address.getState() == null || address.getState().trim().isEmpty() ||
                address.getPostalCode() == null || address.getPostalCode().trim().isEmpty() ||
                address.getCountry() == null || address.getCountry().trim().isEmpty()) {
                throw new IllegalArgumentException("Complete shipping address is required for physical cards");
            }
            
            // Validate shipping method
            if (request.getShippingMethod() == null) {
                request.setShippingMethod(ShippingMethod.STANDARD); // Default to standard
            }
        }
        
        // Validate currency
        if (!isSupportedCurrency(request.getCurrency())) {
            throw new IllegalArgumentException("Unsupported currency: " + request.getCurrency());
        }
        
        // Validate cardholder name
        if (request.getCardholderName() == null || request.getCardholderName().trim().isEmpty()) {
            throw new IllegalArgumentException("Cardholder name is required");
        }
        
        // KYC verification is now handled by @RequireKYCVerification annotation
    }

    private boolean isSupportedCurrency(String currency) {
        return Set.of("USD", "EUR", "GBP", "CAD", "AUD").contains(currency);
    }

    private boolean isUserVerified(String userId) {
        // Use KYC client service to check verification status
        return kycClientService.isUserAdvancedVerified(userId);
    }

    /**
     * Verify additional authentication (2FA/MFA) for sensitive operations
     *
     * This method validates device-based multi-factor authentication including:
     * - Time-based One-Time Password (TOTP)
     * - Biometric authentication (fingerprint/face ID)
     * - Device token verification
     *
     * @param userId User identifier
     * @param authToken MFA token from user's device
     * @return true if MFA verification succeeds, false otherwise
     */
    private boolean verifyAdditionalAuth(String userId, String authToken) {
        try {
            // Validate MFA token format
            if (authToken == null || authToken.trim().isEmpty()) {
                log.warn("MFA verification failed: Empty auth token for user {}", userId);
                return false;
            }

            // Verify MFA token with MFA service
            MfaVerificationResult result = mfaVerificationService.verifyToken(
                userId,
                authToken,
                MfaType.DEVICE_BIOMETRIC
            );

            if (!result.isValid()) {
                log.warn("MFA verification failed for user {}: {}", userId, result.getFailureReason());
                // Record failed MFA attempt for security monitoring
                auditService.logFailedMfaAttempt(userId, result.getFailureReason());
                return false;
            }

            // Verify device trust status
            if (!result.isDeviceTrusted()) {
                log.warn("MFA verification failed: Untrusted device for user {}", userId);
                auditService.logUntrustedDeviceAttempt(userId, result.getDeviceId());
                return false;
            }

            // Successful MFA verification
            log.info("MFA verification successful for user {} with device {}", userId, result.getDeviceId());
            auditService.logSuccessfulMfaVerification(userId, result.getDeviceId());

            return true;

        } catch (Exception e) {
            log.error("MFA verification error for user {}", userId, e);
            // Fail closed on errors
            return false;
        }
    }

    private void setDefaultLimits(String cardId) {
        CardLimits limits = CardLimits.builder()
            .cardId(cardId)
            .dailyLimit(new BigDecimal("1000"))
            .weeklyLimit(new BigDecimal("5000"))
            .monthlyLimit(new BigDecimal("10000"))
            .transactionLimit(new BigDecimal("500"))
            .atmLimit(new BigDecimal("500"))
            .onlineLimit(new BigDecimal("1000"))
            .build();
        
        limitRepository.save(limits);
    }

    private void setDefaultControls(String cardId) {
        CardControls controls = CardControls.builder()
            .cardId(cardId)
            .onlineTransactions(true)
            .internationalTransactions(false)
            .atmWithdrawals(true)
            .contactlessPayments(true)
            .allowedMerchantCategories(new HashSet<>())
            .blockedMerchantCategories(Set.of("7995")) // Gambling
            .allowedCountries(new HashSet<>())
            .blockedCountries(new HashSet<>())
            .build();
        
        controlRepository.save(controls);
    }

    private void validateLimits(UpdateLimitsRequest request) {
        // Validate limits are positive
        if (request.getDailyLimit() != null && 
            request.getDailyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Daily limit must be positive");
        }
        
        // Validate limit hierarchy
        if (request.getDailyLimit() != null && request.getWeeklyLimit() != null &&
            request.getDailyLimit().multiply(BigDecimal.valueOf(7))
                .compareTo(request.getWeeklyLimit()) > 0) {
            throw new IllegalArgumentException("Weekly limit must be at least 7x daily limit");
        }
    }

    private boolean checkTransactionLimits(VirtualCard card, CardTransactionWebhook webhook) {
        CardLimits limits = limitRepository.findByCardId(card.getId()).orElse(null);
        if (limits == null) {
            return true; // No limits set
        }
        
        // Check transaction limit
        if (limits.getTransactionLimit() != null && 
            webhook.getAmount().compareTo(limits.getTransactionLimit()) > 0) {
            return false;
        }
        
        // Check daily limit
        if (limits.getDailyLimit() != null) {
            BigDecimal dailySpent = transactionRepository.getTotalSpentInPeriod(
                card.getId(), Instant.now().minus(Duration.ofDays(1))
            );
            if (dailySpent.add(webhook.getAmount()).compareTo(limits.getDailyLimit()) > 0) {
                return false;
            }
        }
        
        // Check merchant-specific limits
        if (webhook.getMerchantCategory() != null) {
            CardControls controls = controlRepository.findByCardId(card.getId()).orElse(null);
            if (controls != null && controls.getBlockedMerchantCategories() != null &&
                controls.getBlockedMerchantCategories().contains(webhook.getMerchantCategory())) {
                return false;
            }
        }
        
        return true;
    }

    private void recordDeclinedTransaction(VirtualCard card, 
                                         CardTransactionWebhook webhook, 
                                         String reason) {
        CardTransaction transaction = CardTransaction.builder()
            .cardId(card.getId())
            .providerTransactionId(webhook.getTransactionId())
            .type(TransactionType.PURCHASE)
            .amount(webhook.getAmount())
            .currency(webhook.getCurrency())
            .merchantName(webhook.getMerchantName())
            .merchantCategory(webhook.getMerchantCategory())
            .merchantCountry(webhook.getMerchantCountry())
            .description(webhook.getDescription())
            .status(TransactionStatus.DECLINED)
            .declineReason(reason)
            .processedAt(Instant.now())
            .metadata(webhook.getMetadata())
            .build();
        
        transactionRepository.save(transaction);
        
        // Send notification
        notificationService.sendTransactionDeclinedNotification(
            card.getUserId(), card, transaction
        );
    }

    private void updateSpendingAnalytics(VirtualCard card, CardTransaction transaction) {
        // Update spending analytics in background
        CompletableFuture.runAsync(() -> {
            try {
                // Update category totals
                // Update merchant frequency
                // Update spending patterns
                // This would integrate with analytics service
            } catch (Exception e) {
                log.error("Failed to update spending analytics for card {}", card.getId(), e);
            }
        });
    }

    private void scheduleDataDeletion(String cardId) {
        // Schedule deletion of sensitive data after 30 days
        // This would integrate with a job scheduler
    }

    private BillingAddress encryptAddress(BillingAddress address) {
        if (address == null) {
            return null;
        }
        
        return BillingAddress.builder()
            .line1(encryptionService.encrypt(address.getLine1()))
            .line2(address.getLine2() != null ? 
                encryptionService.encrypt(address.getLine2()) : null)
            .city(encryptionService.encrypt(address.getCity()))
            .state(encryptionService.encrypt(address.getState()))
            .postalCode(encryptionService.encrypt(address.getPostalCode()))
            .country(address.getCountry())
            .build();
    }

    private Instant calculateStartDate(AnalyticsTimeframe timeframe) {
        Instant now = Instant.now();
        switch (timeframe) {
            case DAILY:
                return now.minus(Duration.ofDays(1));
            case WEEKLY:
                return now.minus(Duration.ofDays(7));
            case MONTHLY:
                return now.minus(Duration.ofDays(30));
            case YEARLY:
                return now.minus(Duration.ofDays(365));
            default:
                return now.minus(Duration.ofDays(30));
        }
    }

    private VirtualCard getCardByIdAndUser(String cardId, String userId) {
        return cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new CardNotFoundException("Card not found"));
    }

    // DTO conversion methods
    private VirtualCardDto toDto(VirtualCard card, boolean includeSensitive) {
        VirtualCardDto.VirtualCardDtoBuilder builder = VirtualCardDto.builder()
            .id(card.getId())
            .type(card.getType())
            .brand(card.getBrand())
            .status(card.getStatus())
            .nickname(card.getNickname())
            .currency(card.getCurrency())
            .balance(card.getBalance())
            .lastFourDigits(card.getLastFourDigits())
            .expiryMonth(card.getExpiryMonth())
            .expiryYear(card.getExpiryYear())
            .cardholderName(card.getCardholderName())
            .createdAt(card.getCreatedAt())
            .lastUsedAt(card.getLastUsedAt());
        
        if (includeSensitive) {
            // Only include in secure contexts
            builder.billingAddress(decryptAddress(card.getBillingAddress()));
        }
        
        return builder.build();
    }

    private CardTransactionDto toTransactionDto(CardTransaction transaction) {
        return CardTransactionDto.builder()
            .id(transaction.getId())
            .type(transaction.getType())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .merchantName(transaction.getMerchantName())
            .merchantCategory(transaction.getMerchantCategory())
            .description(transaction.getDescription())
            .status(transaction.getStatus())
            .declineReason(transaction.getDeclineReason())
            .balanceAfter(transaction.getBalanceAfter())
            .processedAt(transaction.getProcessedAt())
            .build();
    }

    private CardLimitsDto toLimitsDto(CardLimits limits) {
        return CardLimitsDto.builder()
            .dailyLimit(limits.getDailyLimit())
            .weeklyLimit(limits.getWeeklyLimit())
            .monthlyLimit(limits.getMonthlyLimit())
            .transactionLimit(limits.getTransactionLimit())
            .atmLimit(limits.getAtmLimit())
            .onlineLimit(limits.getOnlineLimit())
            .build();
    }

    private CardControlsDto toControlsDto(CardControls controls) {
        return CardControlsDto.builder()
            .onlineTransactions(controls.getOnlineTransactions())
            .internationalTransactions(controls.getInternationalTransactions())
            .atmWithdrawals(controls.getAtmWithdrawals())
            .contactlessPayments(controls.getContactlessPayments())
            .allowedMerchantCategories(controls.getAllowedMerchantCategories())
            .blockedMerchantCategories(controls.getBlockedMerchantCategories())
            .allowedCountries(controls.getAllowedCountries())
            .blockedCountries(controls.getBlockedCountries())
            .build();
    }

    private BillingAddress decryptAddress(BillingAddress encrypted) {
        if (encrypted == null) {
            return null;
        }
        
        return BillingAddress.builder()
            .line1(encryptionService.decrypt(encrypted.getLine1()))
            .line2(encrypted.getLine2() != null ? 
                encryptionService.decrypt(encrypted.getLine2()) : null)
            .city(encryptionService.decrypt(encrypted.getCity()))
            .state(encryptionService.decrypt(encrypted.getState()))
            .postalCode(encryptionService.decrypt(encrypted.getPostalCode()))
            .country(encrypted.getCountry())
            .build();
    }
}