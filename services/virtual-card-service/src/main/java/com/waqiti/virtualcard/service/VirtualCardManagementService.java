package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.domain.*;
import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.repository.*;
import com.waqiti.virtualcard.provider.CardNetworkProvider;
import com.waqiti.virtualcard.security.CardEncryptionService;
import com.waqiti.virtualcard.validator.CardValidator;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Comprehensive Virtual Card Management Service
 * Handles creation, management, and security of virtual payment cards
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VirtualCardManagementService {

    private final VirtualCardRepository virtualCardRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardLimitRepository cardLimitRepository;
    private final CardControlRepository cardControlRepository;
    private final MerchantRestrictionRepository merchantRestrictionRepository;
    private final CardUsageRepository cardUsageRepository;
    private final CardNetworkProvider cardNetworkProvider;
    private final com.waqiti.virtualcard.provider.CardProvider cardProvider;
    private final CardEncryptionService encryptionService;
    private final CardValidator cardValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${virtual-card.max-cards-per-user:10}")
    private int maxCardsPerUser;
    
    @Value("${virtual-card.default-expiry-months:24}")
    private int defaultExpiryMonths;
    
    @Value("${virtual-card.cvv-rotation-enabled:true}")
    private boolean cvvRotationEnabled;
    
    @Value("${virtual-card.3ds-enabled:true}")
    private boolean threeDSecureEnabled;
    
    private static final String CARD_NUMBER_PREFIX = "4";  // Visa prefix
    private static final int CARD_NUMBER_LENGTH = 16;
    private static final int CVV_LENGTH = 3;
    
    private final Map<String, CardSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * Create a new virtual card
     */
    public VirtualCardResponse createVirtualCard(CreateVirtualCardRequest request) {
        log.info("Creating virtual card for user: {} type: {}", request.getUserId(), request.getCardType());
        
        // Validate request
        validateCreateCardRequest(request);
        
        // Check user's card limit
        int existingCards = virtualCardRepository.countByUserIdAndStatus(
            request.getUserId(), CardStatus.ACTIVE
        );
        
        if (existingCards >= maxCardsPerUser) {
            throw new BusinessException("Maximum number of virtual cards reached");
        }
        
        try {
            // Generate card details
            String cardNumber = generateCardNumber();
            String cvv = generateCVV();
            YearMonth expiry = YearMonth.now().plusMonths(
                request.getValidityMonths() != null ? request.getValidityMonths() : defaultExpiryMonths
            );
            
            // Create virtual card entity
            // PCI DSS COMPLIANT: CVV is NOT stored per Requirement 3.2.2
            // CVV is retrieved dynamically from card provider when needed
            VirtualCard virtualCard = VirtualCard.builder()
                .userId(request.getUserId())
                .cardType(request.getCardType())
                .cardPurpose(request.getCardPurpose())
                .encryptedCardNumber(encryptionService.encryptCardNumber(cardNumber))
                .maskedCardNumber(maskCardNumber(cardNumber))
                // CVV NOT STORED - violates PCI DSS 3.2.2
                .expiryMonth(expiry.getMonthValue())
                .expiryYear(expiry.getYear())
                .cardholderName(request.getCardholderName())
                .billingAddress(request.getBillingAddress())
                .status(CardStatus.ACTIVE)
                .isLocked(false)
                .isPinEnabled(request.isPinEnabled())
                .is3dsEnabled(threeDSecureEnabled)
                .cardNetwork(CardNetwork.VISA)
                .cardColor(request.getCardColor())
                .cardDesign(request.getCardDesign())
                .nickname(request.getNickname())
                .createdAt(LocalDateTime.now())
                .lastUsedAt(null)
                .usageCount(0L)
                .totalSpent(BigDecimal.ZERO)
                .metadata(request.getMetadata())
                .build();
            
            // Set card limits
            if (request.getSpendingLimits() != null) {
                setCardLimits(virtualCard, request.getSpendingLimits());
            } else {
                setDefaultCardLimits(virtualCard);
            }
            
            // Set card controls
            if (request.getCardControls() != null) {
                setCardControls(virtualCard, request.getCardControls());
            }
            
            // Set merchant restrictions
            if (request.getMerchantRestrictions() != null) {
                setMerchantRestrictions(virtualCard, request.getMerchantRestrictions());
            }
            
            // Save card
            virtualCard = virtualCardRepository.save(virtualCard);
            
            // Register with card network
            CardNetworkRegistration registration = cardNetworkProvider.registerCard(
                CardNetworkRequest.builder()
                    .cardNumber(cardNumber)
                    .cvv(cvv)
                    .expiry(expiry.toString())
                    .cardholderName(request.getCardholderName())
                    .billingAddress(request.getBillingAddress())
                    .build()
            );
            
            virtualCard.setNetworkToken(registration.getToken());
            virtualCard.setNetworkStatus(registration.getStatus());
            virtualCardRepository.save(virtualCard);
            
            // Publish card created event
            publishCardEvent(new CardCreatedEvent(
                virtualCard.getId(),
                request.getUserId(),
                request.getCardType(),
                LocalDateTime.now()
            ));
            
            // Log card creation
            logCardActivity(virtualCard.getId(), "CARD_CREATED", "Virtual card created successfully");
            
            return VirtualCardResponse.builder()
                .cardId(virtualCard.getId())
                .cardNumber(maskCardNumber(cardNumber))
                .cvv(request.isShowCvv() ? cvv : null)
                .expiryMonth(expiry.getMonthValue())
                .expiryYear(expiry.getYear())
                .cardholderName(virtualCard.getCardholderName())
                .cardType(virtualCard.getCardType())
                .status(virtualCard.getStatus())
                .cardNetwork(virtualCard.getCardNetwork())
                .nickname(virtualCard.getNickname())
                .createdAt(virtualCard.getCreatedAt())
                .limits(getCardLimits(virtualCard.getId()))
                .controls(getCardControls(virtualCard.getId()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create virtual card", e);
            throw new BusinessException("Failed to create virtual card: " + e.getMessage());
        }
    }
    
    /**
     * Get virtual card details
     */
    @Cacheable(value = "virtualCards", key = "#cardId")
    public VirtualCardDetails getCardDetails(String userId, String cardId, boolean showSensitive) {
        log.debug("Getting card details for card: {} user: {}", cardId, userId);
        
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        VirtualCardDetails.VirtualCardDetailsBuilder details = VirtualCardDetails.builder()
            .cardId(card.getId())
            .cardNumber(card.getMaskedCardNumber())
            .expiryMonth(card.getExpiryMonth())
            .expiryYear(card.getExpiryYear())
            .cardholderName(card.getCardholderName())
            .cardType(card.getCardType())
            .cardPurpose(card.getCardPurpose())
            .status(card.getStatus())
            .isLocked(card.isLocked())
            .cardNetwork(card.getCardNetwork())
            .nickname(card.getNickname())
            .cardColor(card.getCardColor())
            .cardDesign(card.getCardDesign())
            .createdAt(card.getCreatedAt())
            .lastUsedAt(card.getLastUsedAt())
            .usageCount(card.getUsageCount())
            .totalSpent(card.getTotalSpent())
            .limits(getCardLimits(cardId))
            .controls(getCardControls(cardId))
            .merchantRestrictions(getMerchantRestrictions(cardId))
            .recentTransactions(getRecentTransactions(cardId, 10));
        
        if (showSensitive && validateUserAccess(userId, cardId, "VIEW_SENSITIVE")) {
            // Decrypt PAN from database
            details.fullCardNumber(encryptionService.decryptCardNumber(card.getEncryptedCardNumber()));

            // PCI DSS COMPLIANT: Retrieve dynamic CVV from provider (never from database)
            // CVV is generated on-demand and never persisted
            try {
                String dynamicCvv = cardProvider.getDynamicCvv(card.getProviderId());
                details.cvv(dynamicCvv);
            } catch (Exception e) {
                log.error("Failed to retrieve dynamic CVV for card {}", cardId, e);
                // Don't fail the entire request if CVV retrieval fails
                details.cvv(null);
            }
        }
        
        return details.build();
    }
    
    /**
     * Update card spending limits
     */
    @CacheEvict(value = "virtualCards", key = "#cardId")
    public void updateCardLimits(String userId, String cardId, SpendingLimits newLimits) {
        log.info("Updating card limits for card: {}", cardId);
        
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        // Validate new limits
        validateSpendingLimits(newLimits);
        
        // Update limits
        CardLimit cardLimit = cardLimitRepository.findByCardId(cardId)
            .orElse(new CardLimit());
        
        cardLimit.setCardId(cardId);
        cardLimit.setDailyLimit(newLimits.getDailyLimit());
        cardLimit.setWeeklyLimit(newLimits.getWeeklyLimit());
        cardLimit.setMonthlyLimit(newLimits.getMonthlyLimit());
        cardLimit.setTransactionLimit(newLimits.getTransactionLimit());
        cardLimit.setAtmWithdrawalLimit(newLimits.getAtmWithdrawalLimit());
        cardLimit.setOnlineLimit(newLimits.getOnlineLimit());
        cardLimit.setInternationalLimit(newLimits.getInternationalLimit());
        cardLimit.setUpdatedAt(LocalDateTime.now());
        
        cardLimitRepository.save(cardLimit);
        
        // Log activity
        logCardActivity(cardId, "LIMITS_UPDATED", "Card spending limits updated");
        
        // Publish event
        publishCardEvent(new CardLimitsUpdatedEvent(cardId, userId, newLimits, LocalDateTime.now()));
    }
    
    /**
     * Lock/Unlock virtual card
     */
    @CacheEvict(value = "virtualCards", key = "#cardId")
    public void toggleCardLock(String userId, String cardId, boolean lock, String reason) {
        log.info("Toggling card lock for card: {} lock: {}", cardId, lock);
        
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Cannot lock/unlock inactive card");
        }
        
        card.setLocked(lock);
        card.setLockedAt(lock ? LocalDateTime.now() : null);
        card.setLockReason(lock ? reason : null);
        virtualCardRepository.save(card);
        
        // Notify card network
        cardNetworkProvider.updateCardStatus(
            card.getNetworkToken(),
            lock ? "LOCKED" : "ACTIVE"
        );
        
        // Log activity
        logCardActivity(cardId, lock ? "CARD_LOCKED" : "CARD_UNLOCKED", reason);
        
        // Publish event
        publishCardEvent(new CardLockToggleEvent(cardId, userId, lock, reason, LocalDateTime.now()));
    }
    
    /**
     * Terminate virtual card
     */
    @CacheEvict(value = "virtualCards", key = "#cardId")
    public void terminateCard(String userId, String cardId, String reason) {
        log.info("Terminating card: {} for user: {}", cardId, userId);
        
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        if (card.getStatus() == CardStatus.TERMINATED) {
            throw new BusinessException("Card is already terminated");
        }
        
        card.setStatus(CardStatus.TERMINATED);
        card.setTerminatedAt(LocalDateTime.now());
        card.setTerminationReason(reason);
        virtualCardRepository.save(card);
        
        // Deregister from card network
        cardNetworkProvider.deregisterCard(card.getNetworkToken());
        
        // Log activity
        logCardActivity(cardId, "CARD_TERMINATED", reason);
        
        // Publish event
        publishCardEvent(new CardTerminatedEvent(cardId, userId, reason, LocalDateTime.now()));
    }
    
    /**
     * Process card transaction
     */
    @Transactional
    public CardTransactionResponse processTransaction(CardTransactionRequest request) {
        log.debug("Processing transaction for card: {} amount: {}", 
            request.getCardId(), request.getAmount());
        
        VirtualCard card = virtualCardRepository.findById(request.getCardId())
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        // Validate card status
        if (!card.canProcessTransaction()) {
            return CardTransactionResponse.declined("CARD_INACTIVE", "Card is not active for transactions");
        }
        
        // Validate card controls
        CardControlValidation controlValidation = validateCardControls(card, request);
        if (!controlValidation.isValid()) {
            return CardTransactionResponse.declined("CONTROL_VIOLATION", controlValidation.getReason());
        }
        
        // Check spending limits
        SpendingLimitValidation limitValidation = validateSpendingLimits(card, request);
        if (!limitValidation.isValid()) {
            return CardTransactionResponse.declined("LIMIT_EXCEEDED", limitValidation.getReason());
        }
        
        // Check merchant restrictions
        if (!validateMerchantRestrictions(card, request.getMerchant())) {
            return CardTransactionResponse.declined("MERCHANT_RESTRICTED", "Merchant not allowed");
        }
        
        // Process with card network
        try {
            CardNetworkResponse networkResponse = cardNetworkProvider.processTransaction(
                CardNetworkTransactionRequest.builder()
                    .token(card.getNetworkToken())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .merchant(request.getMerchant())
                    .mcc(request.getMerchantCategoryCode())
                    .build()
            );
            
            // Record transaction
            CardTransaction transaction = CardTransaction.builder()
                .cardId(card.getId())
                .transactionId(UUID.randomUUID().toString())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .merchantName(request.getMerchant().getName())
                .merchantCategory(request.getMerchant().getCategory())
                .transactionType(request.getTransactionType())
                .status(networkResponse.isApproved() ? TransactionStatus.APPROVED : TransactionStatus.DECLINED)
                .authorizationCode(networkResponse.getAuthorizationCode())
                .networkResponseCode(networkResponse.getResponseCode())
                .transactionDate(LocalDateTime.now())
                .build();
            
            transactionRepository.save(transaction);
            
            // Update card usage
            if (networkResponse.isApproved()) {
                updateCardUsage(card, request.getAmount());
            }
            
            // Log transaction
            logCardActivity(card.getId(), "TRANSACTION_" + transaction.getStatus(), 
                "Transaction " + transaction.getStatus() + " for " + request.getAmount());
            
            return CardTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .approved(networkResponse.isApproved())
                .authorizationCode(transaction.getAuthorizationCode())
                .responseCode(transaction.getNetworkResponseCode())
                .message(networkResponse.getMessage())
                .balance(getAvailableBalance(card))
                .timestamp(transaction.getTransactionDate())
                .build();
                
        } catch (Exception e) {
            log.error("Transaction processing failed", e);
            return CardTransactionResponse.declined("PROCESSING_ERROR", "Transaction processing failed");
        }
    }
    
    /**
     * Get card transactions
     */
    public List<CardTransaction> getCardTransactions(String userId, String cardId, 
                                                    TransactionFilter filter) {
        // Verify user owns the card
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        return transactionRepository.findByCardIdAndFilters(
            cardId,
            filter.getFromDate(),
            filter.getToDate(),
            filter.getStatus(),
            filter.getMinAmount(),
            filter.getMaxAmount()
        );
    }
    
    /**
     * Rotate CVV for enhanced security
     */
    @CacheEvict(value = "virtualCards", key = "#cardId")
    public String rotateCVV(String userId, String cardId) {
        log.info("Rotating CVV for card: {}", cardId);
        
        if (!cvvRotationEnabled) {
            throw new BusinessException("CVV rotation is not enabled");
        }
        
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));

        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Cannot rotate CVV for inactive card");
        }

        // PCI DSS COMPLIANT: CVV rotation is handled by the card provider
        // We do not store CVV per Requirement 3.2.2
        // The provider generates and manages dynamic CVV

        // Request CVV rotation from card provider
        String newCvv = cardNetworkProvider.rotateCVV(card.getNetworkToken());

        // Update rotation timestamp (but NOT the CVV itself)
        card.setCvvRotatedAt(LocalDateTime.now());
        virtualCardRepository.save(card);

        // Log activity
        logCardActivity(cardId, "CVV_ROTATED", "CVV rotated for security");

        // Return new CVV to user (transmitted over TLS, immediately discarded)
        return newCvv;
    }
    
    /**
     * Create single-use virtual card
     */
    public VirtualCardResponse createSingleUseCard(String userId, BigDecimal amount, 
                                                  String merchantName, int validityMinutes) {
        log.info("Creating single-use card for user: {} amount: {}", userId, amount);
        
        CreateVirtualCardRequest request = CreateVirtualCardRequest.builder()
            .userId(userId)
            .cardType(CardType.SINGLE_USE)
            .cardPurpose("Single transaction for " + merchantName)
            .spendingLimits(SpendingLimits.builder()
                .transactionLimit(amount)
                .dailyLimit(amount)
                .build())
            .cardControls(CardControls.builder()
                .allowedMerchants(Collections.singletonList(merchantName))
                .maxTransactions(1)
                .expiryMinutes(validityMinutes)
                .build())
            .validityMonths(1)
            .build();
        
        return createVirtualCard(request);
    }
    
    /**
     * Get user's virtual cards
     */
    public List<VirtualCardSummary> getUserCards(String userId, boolean includeInactive) {
        List<VirtualCard> cards;
        
        if (includeInactive) {
            cards = virtualCardRepository.findByUserId(userId);
        } else {
            cards = virtualCardRepository.findByUserIdAndStatus(userId, CardStatus.ACTIVE);
        }
        
        return cards.stream()
            .map(this::toCardSummary)
            .collect(Collectors.toList());
    }
    
    /**
     * Enable/Disable 3D Secure
     */
    @CacheEvict(value = "virtualCards", key = "#cardId")
    public void toggle3DSecure(String userId, String cardId, boolean enable) {
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException("Virtual card not found"));
        
        card.set3dsEnabled(enable);
        virtualCardRepository.save(card);
        
        // Update with card network
        cardNetworkProvider.update3DSecure(card.getNetworkToken(), enable);
        
        logCardActivity(cardId, enable ? "3DS_ENABLED" : "3DS_DISABLED", 
            "3D Secure " + (enable ? "enabled" : "disabled"));
    }
    
    // Helper methods
    
    private String generateCardNumber() {
        StringBuilder cardNumber = new StringBuilder(CARD_NUMBER_PREFIX);
        
        // Generate random digits
        for (int i = 1; i < CARD_NUMBER_LENGTH - 1; i++) {
            cardNumber.append(secureRandom.nextInt(10));
        }
        
        // Calculate and append Luhn check digit
        cardNumber.append(calculateLuhnCheckDigit(cardNumber.toString()));
        
        return cardNumber.toString();
    }
    
    private String generateCVV() {
        StringBuilder cvv = new StringBuilder();
        for (int i = 0; i < CVV_LENGTH; i++) {
            cvv.append(secureRandom.nextInt(10));
        }
        return cvv.toString();
    }
    
    private int calculateLuhnCheckDigit(String cardNumber) {
        int sum = 0;
        boolean alternate = true;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (10 - (sum % 10)) % 10;
    }
    
    private String maskCardNumber(String cardNumber) {
        if (cardNumber.length() < 8) {
            return cardNumber;
        }
        return cardNumber.substring(0, 4) + " **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
    
    private void validateCreateCardRequest(CreateVirtualCardRequest request) {
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        
        if (request.getCardType() == null) {
            throw new ValidationException("Card type is required");
        }
        
        if (request.getCardholderName() == null || request.getCardholderName().isEmpty()) {
            throw new ValidationException("Cardholder name is required");
        }
        
        if (request.getSpendingLimits() != null) {
            validateSpendingLimits(request.getSpendingLimits());
        }
    }
    
    private void validateSpendingLimits(SpendingLimits limits) {
        if (limits.getDailyLimit() != null && limits.getDailyLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Daily limit cannot be negative");
        }
        
        if (limits.getMonthlyLimit() != null && limits.getDailyLimit() != null &&
            limits.getMonthlyLimit().compareTo(limits.getDailyLimit()) < 0) {
            throw new ValidationException("Monthly limit cannot be less than daily limit");
        }
    }
    
    private void setCardLimits(VirtualCard card, SpendingLimits limits) {
        CardLimit cardLimit = CardLimit.builder()
            .cardId(card.getId())
            .dailyLimit(limits.getDailyLimit())
            .weeklyLimit(limits.getWeeklyLimit())
            .monthlyLimit(limits.getMonthlyLimit())
            .transactionLimit(limits.getTransactionLimit())
            .atmWithdrawalLimit(limits.getAtmWithdrawalLimit())
            .onlineLimit(limits.getOnlineLimit())
            .internationalLimit(limits.getInternationalLimit())
            .createdAt(LocalDateTime.now())
            .build();
        
        cardLimitRepository.save(cardLimit);
    }
    
    private void setDefaultCardLimits(VirtualCard card) {
        CardLimit cardLimit = CardLimit.builder()
            .cardId(card.getId())
            .dailyLimit(new BigDecimal("1000"))
            .weeklyLimit(new BigDecimal("5000"))
            .monthlyLimit(new BigDecimal("10000"))
            .transactionLimit(new BigDecimal("500"))
            .atmWithdrawalLimit(new BigDecimal("200"))
            .onlineLimit(new BigDecimal("1000"))
            .internationalLimit(new BigDecimal("500"))
            .createdAt(LocalDateTime.now())
            .build();
        
        cardLimitRepository.save(cardLimit);
    }
    
    private void setCardControls(VirtualCard card, CardControls controls) {
        CardControl cardControl = CardControl.builder()
            .cardId(card.getId())
            .allowOnlineTransactions(controls.isAllowOnlineTransactions())
            .allowInternationalTransactions(controls.isAllowInternationalTransactions())
            .allowAtmWithdrawals(controls.isAllowAtmWithdrawals())
            .allowContactlessPayments(controls.isAllowContactlessPayments())
            .allowRecurringPayments(controls.isAllowRecurringPayments())
            .maxTransactions(controls.getMaxTransactions())
            .allowedCountries(controls.getAllowedCountries())
            .blockedCountries(controls.getBlockedCountries())
            .allowedMerchantCategories(controls.getAllowedMerchantCategories())
            .blockedMerchantCategories(controls.getBlockedMerchantCategories())
            .timeRestrictions(controls.getTimeRestrictions())
            .createdAt(LocalDateTime.now())
            .build();
        
        cardControlRepository.save(cardControl);
    }
    
    private void setMerchantRestrictions(VirtualCard card, MerchantRestrictions restrictions) {
        MerchantRestriction merchantRestriction = MerchantRestriction.builder()
            .cardId(card.getId())
            .allowedMerchants(restrictions.getAllowedMerchants())
            .blockedMerchants(restrictions.getBlockedMerchants())
            .allowedCategories(restrictions.getAllowedCategories())
            .blockedCategories(restrictions.getBlockedCategories())
            .createdAt(LocalDateTime.now())
            .build();
        
        merchantRestrictionRepository.save(merchantRestriction);
    }
    
    private void updateCardUsage(VirtualCard card, BigDecimal amount) {
        card.setUsageCount(card.getUsageCount() + 1);
        card.setTotalSpent(card.getTotalSpent().add(amount));
        card.setLastUsedAt(LocalDateTime.now());
        virtualCardRepository.save(card);
    }
    
    private void logCardActivity(String cardId, String action, String details) {
        CardActivity activity = CardActivity.builder()
            .cardId(cardId)
            .action(action)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Save to activity log
        log.info("Card activity: {} - {} - {}", cardId, action, details);
    }
    
    private void publishCardEvent(Object event) {
        try {
            kafkaTemplate.send("virtual-card-events", event);
        } catch (Exception e) {
            log.error("Failed to publish card event", e);
        }
    }
    
    private VirtualCardSummary toCardSummary(VirtualCard card) {
        return VirtualCardSummary.builder()
            .cardId(card.getId())
            .maskedCardNumber(card.getMaskedCardNumber())
            .cardType(card.getCardType())
            .status(card.getStatus())
            .nickname(card.getNickname())
            .expiryMonth(card.getExpiryMonth())
            .expiryYear(card.getExpiryYear())
            .lastUsedAt(card.getLastUsedAt())
            .totalSpent(card.getTotalSpent())
            .isLocked(card.isLocked())
            .build();
    }
}