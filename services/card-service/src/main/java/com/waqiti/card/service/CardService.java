package com.waqiti.card.service;

import com.waqiti.card.dto.*;
import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardProduct;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.repository.CardProductRepository;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CardService - Business logic for card management
 *
 * Handles:
 * - Card creation and lifecycle
 * - Card activation and blocking
 * - Card updates and replacements
 * - Balance and limit management
 * - PIN management
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final CardProductRepository cardProductRepository;
    private final CardSecurityService cardSecurityService;

    /**
     * Create a new card
     */
    @Transactional
    public CardResponse createCard(CardCreateRequest request) {
        log.info("Creating new card for user: {}", request.getUserId());

        // Validate product exists
        CardProduct product = cardProductRepository.findByProductId(request.getProductId())
            .orElseThrow(() -> new RuntimeException("Card product not found: " + request.getProductId()));

        // Validate product is active
        if (!product.isCurrentlyActive()) {
            throw new RuntimeException("Card product is not currently active");
        }

        // Generate card ID
        String cardId = generateCardId();

        // Generate PAN (Primary Account Number) - in production, use secure tokenization
        String cardNumber = generateCardNumber(product);
        String encryptedPAN = encryptCardNumber(cardNumber);
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        String panToken = generatePanToken(cardNumber);

        // Calculate expiry date
        LocalDate expiryDate = product.calculateExpiryDate(LocalDate.now());

        // Determine credit limit
        BigDecimal creditLimit = determineCreditLimit(request.getCreditLimit(), product);

        // Build card entity
        Card card = Card.builder()
            .cardId(cardId)
            .cardNumberEncrypted(encryptedPAN)
            .cardNumberLastFour(lastFour)
            .panToken(panToken)
            .cardType(request.getCardType())
            .cardBrand(request.getCardBrand())
            .cardStatus(CardStatus.PENDING_ACTIVATION)
            .productId(request.getProductId())
            .userId(request.getUserId())
            .accountId(request.getAccountId())
            .issueDate(LocalDate.now())
            .expiryDate(expiryDate)
            .embossedName(request.getEmbossedName())
            .creditLimit(creditLimit)
            .availableCredit(creditLimit)
            .ledgerBalance(BigDecimal.ZERO)
            .outstandingBalance(BigDecimal.ZERO)
            .dailySpendLimit(request.getDailySpendLimit() != null ? request.getDailySpendLimit() : product.getDefaultDailyLimit())
            .monthlySpendLimit(request.getMonthlySpendLimit() != null ? request.getMonthlySpendLimit() : product.getDefaultMonthlyLimit())
            .isVirtual(request.getIsVirtual() != null ? request.getIsVirtual() : false)
            .isContactless(request.getIsContactless() != null ? request.getIsContactless() : product.getContactlessEnabled())
            .isInternationalEnabled(request.getIsInternationalEnabled() != null ? request.getIsInternationalEnabled() : product.getInternationalEnabled())
            .isOnlineEnabled(request.getIsOnlineEnabled() != null ? request.getIsOnlineEnabled() : product.getOnlineTransactionsEnabled())
            .pinSet(false)
            .deliveryAddress(request.getDeliveryAddress())
            .deliveryStatus("PENDING")
            .build();

        card = cardRepository.save(card);

        log.info("Card created successfully: {}", card.getCardId());

        return mapToCardResponse(card);
    }

    /**
     * Get card by ID
     */
    @Transactional(readOnly = true)
    public CardResponse getCardById(UUID cardId) {
        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        return mapToCardResponse(card);
    }

    /**
     * Get card by card ID string
     */
    @Transactional(readOnly = true)
    public CardResponse getCardByCardId(String cardId) {
        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        return mapToCardResponse(card);
    }

    /**
     * Get all cards for a user
     */
    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByUserId(UUID userId) {
        List<Card> cards = cardRepository.findByUserId(userId);
        return cards.stream()
            .map(this::mapToCardResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get active cards for a user
     */
    @Transactional(readOnly = true)
    public List<CardResponse> getActiveCardsByUserId(UUID userId) {
        List<Card> cards = cardRepository.findActiveCardsByUserId(userId);
        return cards.stream()
            .map(this::mapToCardResponse)
            .collect(Collectors.toList());
    }

    /**
     * Activate card
     */
    @Transactional
    public CardResponse activateCard(String cardId, CardActivateRequest request) {
        log.info("Activating card: {}", cardId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        // Validate card is in correct status
        if (card.getCardStatus() != CardStatus.PENDING_ACTIVATION &&
            card.getCardStatus() != CardStatus.PENDING_USER_ACTIVATION) {
            throw new RuntimeException("Card cannot be activated - current status: " + card.getCardStatus());
        }

        // Validate activation code (in production, verify against secure stored code)
        if (!validateActivationCode(card, request.getActivationCode())) {
            throw new RuntimeException("Invalid activation code");
        }

        // Validate CVV (in production, verify against encrypted CVV)
        if (!validateCVV(card, request.getCvv())) {
            throw new RuntimeException("Invalid CVV");
        }

        // Validate last four digits
        if (!card.getCardNumberLastFour().equals(request.getLastFourDigits())) {
            throw new RuntimeException("Invalid card number");
        }

        // Activate card
        card.activate();
        card = cardRepository.save(card);

        log.info("Card activated successfully: {}", cardId);

        return mapToCardResponse(card);
    }

    /**
     * Block card
     */
    @Transactional
    public CardResponse blockCard(String cardId, CardBlockRequest request) {
        log.info("Blocking card: {} - Reason: {}", cardId, request.getReason());

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        // Block card
        card.block(request.getReason());

        // Update status based on reason
        if (Boolean.TRUE.equals(request.getReportFraud())) {
            card.setCardStatus(CardStatus.FRAUD_BLOCKED);
        } else if (Boolean.TRUE.equals(request.getReportLostStolen())) {
            card.setCardStatus(CardStatus.LOST_STOLEN);
        }

        card = cardRepository.save(card);

        log.info("Card blocked successfully: {}", cardId);

        return mapToCardResponse(card);
    }

    /**
     * Unblock card
     */
    @Transactional
    public CardResponse unblockCard(String cardId) {
        log.info("Unblocking card: {}", cardId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        if (!card.isBlocked()) {
            throw new RuntimeException("Card is not currently blocked");
        }

        card.unblock();
        card = cardRepository.save(card);

        log.info("Card unblocked successfully: {}", cardId);

        return mapToCardResponse(card);
    }

    /**
     * Update card details
     */
    @Transactional
    public CardResponse updateCard(String cardId, CardUpdateRequest request) {
        log.info("Updating card: {}", cardId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        // Update fields
        if (request.getEmbossedName() != null) {
            card.setEmbossedName(request.getEmbossedName());
        }
        if (request.getCreditLimit() != null) {
            updateCreditLimit(card, request.getCreditLimit());
        }
        if (request.getDailySpendLimit() != null) {
            card.setDailySpendLimit(request.getDailySpendLimit());
        }
        if (request.getMonthlySpendLimit() != null) {
            card.setMonthlySpendLimit(request.getMonthlySpendLimit());
        }
        if (request.getIsInternationalEnabled() != null) {
            card.setIsInternationalEnabled(request.getIsInternationalEnabled());
        }
        if (request.getIsOnlineEnabled() != null) {
            card.setIsOnlineEnabled(request.getIsOnlineEnabled());
        }
        if (request.getDeliveryAddress() != null) {
            card.setDeliveryAddress(request.getDeliveryAddress());
        }

        card = cardRepository.save(card);

        log.info("Card updated successfully: {}", cardId);

        return mapToCardResponse(card);
    }

    /**
     * Set/change card PIN
     */
    @Transactional
    public void setCardPin(String cardId, CardPinSetRequest request) {
        log.info("Setting PIN for card: {}", cardId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        // Validate new PIN matches confirmation
        if (!request.getNewPin().equals(request.getConfirmPin())) {
            throw new RuntimeException("PIN confirmation does not match");
        }

        // If PIN already set, validate current PIN
        if (Boolean.TRUE.equals(card.getPinSet())) {
            if (request.getCurrentPin() == null) {
                throw new RuntimeException("Current PIN is required");
            }
            if (!validatePin(card, request.getCurrentPin())) {
                card.incrementPinAttempts();
                cardRepository.save(card);
                throw new RuntimeException("Invalid current PIN");
            }
        }

        // Hash and set new PIN
        String pinHash = hashPin(request.getNewPin());
        card.setPinHash(pinHash);
        card.setPinSet(true);
        card.resetPinAttempts();

        cardRepository.save(card);

        log.info("PIN set successfully for card: {}", cardId);
    }

    /**
     * Deduct available credit (for transaction authorization)
     */
    @Transactional
    public void deductCredit(UUID cardId, BigDecimal amount) {
        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        if (!card.hasSufficientCredit(amount)) {
            throw new RuntimeException("Insufficient credit available");
        }

        card.deductCredit(amount);
        card.setLastTransactionDate(LocalDateTime.now());
        cardRepository.save(card);
    }

    /**
     * Restore available credit (for reversal/refund)
     */
    @Transactional
    public void restoreCredit(UUID cardId, BigDecimal amount) {
        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        card.restoreCredit(amount);
        cardRepository.save(card);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private CardResponse mapToCardResponse(Card card) {
        return CardResponse.builder()
            .id(card.getId())
            .cardId(card.getCardId())
            .maskedCardNumber(card.getMaskedCardNumber())
            .cardNumberLastFour(card.getCardNumberLastFour())
            .cardType(card.getCardType())
            .cardBrand(card.getCardBrand())
            .cardStatus(card.getCardStatus())
            .productId(card.getProductId())
            .userId(card.getUserId())
            .accountId(card.getAccountId())
            .issueDate(card.getIssueDate())
            .expiryDate(card.getExpiryDate())
            .embossedName(card.getEmbossedName())
            .creditLimit(card.getCreditLimit())
            .availableCredit(card.getAvailableCredit())
            .outstandingBalance(card.getOutstandingBalance())
            .ledgerBalance(card.getLedgerBalance())
            .paymentDueDate(card.getPaymentDueDate())
            .minimumPayment(card.getMinimumPayment())
            .isContactless(card.getIsContactless())
            .isVirtual(card.getIsVirtual())
            .isInternationalEnabled(card.getIsInternationalEnabled())
            .isOnlineEnabled(card.getIsOnlineEnabled())
            .activatedAt(card.getActivatedAt())
            .isExpired(card.isExpired())
            .isUsable(card.isUsable())
            .isBlocked(card.isBlocked())
            .createdAt(card.getCreatedAt())
            .updatedAt(card.getUpdatedAt())
            .build();
    }

    private String generateCardId() {
        return "CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateCardNumber(CardProduct product) {
        // In production: Use BIN range from product and generate valid card number with Luhn algorithm
        // For now, generate a dummy number
        String bin = product.getBinRangeStart() != null ? product.getBinRangeStart() : "400000";
        String accountNumber = String.format("%010d", System.currentTimeMillis() % 10000000000L);
        return bin + accountNumber;
    }

    private String encryptCardNumber(String cardNumber) {
        return cardSecurityService.encryptCardNumber(cardNumber);
    }

    private String generatePanToken(String cardNumber) {
        return cardSecurityService.generatePanToken(cardNumber);
    }

    private BigDecimal determineCreditLimit(BigDecimal requestedLimit, CardProduct product) {
        if (requestedLimit == null) {
            return product.getDefaultCreditLimit();
        }

        if (!product.isValidCreditLimit(requestedLimit)) {
            throw new RuntimeException("Credit limit outside allowed range");
        }

        return requestedLimit;
    }

    private void updateCreditLimit(Card card, BigDecimal newLimit) {
        // Validate new limit
        CardProduct product = cardProductRepository.findByProductId(card.getProductId())
            .orElseThrow(() -> new RuntimeException("Card product not found"));

        if (!product.isValidCreditLimit(newLimit)) {
            throw new RuntimeException("Credit limit outside allowed range");
        }

        BigDecimal difference = newLimit.subtract(card.getCreditLimit());
        card.setCreditLimit(newLimit);
        card.setAvailableCredit(card.getAvailableCredit().add(difference));
    }

    private boolean validateActivationCode(Card card, String code) {
        if (card.getActivationCodeHash() == null) {
            return false;
        }
        return cardSecurityService.verifyActivationCode(code, card.getActivationCodeHash());
    }

    private boolean validateCVV(Card card, String cvv) {
        if (card.getCvvEncrypted() == null) {
            return false;
        }
        return cardSecurityService.verifyCvv(cvv, card.getCvvEncrypted());
    }

    private boolean validatePin(Card card, String pin) {
        if (card.getPinHash() == null) {
            return false;
        }
        return cardSecurityService.verifyPin(pin, card.getPinHash());
    }

    private String hashPin(String pin) {
        return cardSecurityService.hashPin(pin);
    }
}
