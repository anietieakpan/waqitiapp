package com.waqiti.card.service;

import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardProduct;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.exception.CardNotFoundException;
import com.waqiti.card.exception.InvalidCardStateException;
import com.waqiti.card.repository.CardProductRepository;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardReplacementService - Production-grade card replacement workflow
 *
 * Handles card replacement for:
 * - Lost or stolen cards
 * - Damaged cards
 * - Expired cards
 * - Compromised cards
 * - Card upgrades
 *
 * Key Features:
 * - Automatic balance and limit transfer to new card
 * - Old card deactivation and cancellation
 * - Comprehensive audit logging
 * - Replacement reason tracking
 * - New card number generation with encryption
 * - Notification to user
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardReplacementService {

    private final CardRepository cardRepository;
    private final CardProductRepository cardProductRepository;
    private final CardSecurityService cardSecurityService;
    private final CardNotificationService cardNotificationService;
    private final CardAuditService cardAuditService;

    /**
     * Replace a lost or stolen card
     *
     * @param oldCardId Old card ID
     * @param userId User ID
     * @param reason Replacement reason (LOST or STOLEN)
     * @param email User email
     * @param phoneNumber User phone
     * @return New replacement card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card replaceLostOrStolenCard(UUID oldCardId, UUID userId, String reason,
                                        String email, String phoneNumber) {
        log.info("Replacing lost/stolen card: {} for user: {} - Reason: {}", oldCardId, userId, reason);

        Card oldCard = cardRepository.findByCardIdAndNotDeleted(oldCardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + oldCardId));

        // Validate ownership
        validateCardOwnership(oldCard, userId);

        // Block old card immediately (security measure)
        oldCard.setCardStatus(CardStatus.BLOCKED);
        oldCard.setBlockedReason("Card reported as " + reason);
        oldCard.setBlockedAt(LocalDateTime.now());
        cardRepository.save(oldCard);

        // Audit old card blocking
        cardAuditService.logCardBlocked(userId, oldCardId, "Card reported as " + reason, false);

        // Create new replacement card
        Card newCard = createReplacementCard(oldCard, reason);

        // Transfer balances and limits
        transferCardDetails(oldCard, newCard);

        // Save new card
        newCard = cardRepository.save(newCard);

        // Cancel old card
        oldCard.setCardStatus(CardStatus.CANCELLED);
        oldCard.setCancelledAt(LocalDateTime.now());
        cardRepository.save(oldCard);

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(newCard.getCardId()),
                newCard.getCardType().name(),
                java.util.Map.of("replacementFor", oldCardId.toString(), "reason", reason));

        // Notify user
        cardNotificationService.sendCardReplacementNotification(userId, oldCard, newCard, email, phoneNumber);

        log.info("Card replacement successful - Old: {}, New: {}", oldCardId, newCard.getCardId());
        return newCard;
    }

    /**
     * Replace a damaged card
     *
     * @param oldCardId Old card ID
     * @param userId User ID
     * @param email User email
     * @param phoneNumber User phone
     * @return New replacement card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card replaceDamagedCard(UUID oldCardId, UUID userId, String email, String phoneNumber) {
        log.info("Replacing damaged card: {} for user: {}", oldCardId, userId);

        Card oldCard = cardRepository.findByCardIdAndNotDeleted(oldCardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + oldCardId));

        // Validate ownership
        validateCardOwnership(oldCard, userId);

        // Create new replacement card
        Card newCard = createReplacementCard(oldCard, "DAMAGED");

        // Transfer balances and limits (keep same card number for damaged replacement)
        transferCardDetails(oldCard, newCard);

        // Save new card
        newCard = cardRepository.save(newCard);

        // Deactivate old card
        oldCard.setCardStatus(CardStatus.CANCELLED);
        oldCard.setCancelledAt(LocalDateTime.now());
        cardRepository.save(oldCard);

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(newCard.getCardId()),
                newCard.getCardType().name(),
                java.util.Map.of("replacementFor", oldCardId.toString(), "reason", "DAMAGED"));

        // Notify
        cardNotificationService.sendCardReplacementNotification(userId, oldCard, newCard, email, phoneNumber);

        log.info("Damaged card replaced successfully - Old: {}, New: {}", oldCardId, newCard.getCardId());
        return newCard;
    }

    /**
     * Replace an expired card
     *
     * @param oldCardId Old card ID
     * @param userId User ID
     * @param email User email
     * @param phoneNumber User phone
     * @return New replacement card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card replaceExpiredCard(UUID oldCardId, UUID userId, String email, String phoneNumber) {
        log.info("Replacing expired card: {} for user: {}", oldCardId, userId);

        Card oldCard = cardRepository.findByCardIdAndNotDeleted(oldCardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + oldCardId));

        // Validate ownership
        validateCardOwnership(oldCard, userId);

        // Verify card is actually expired
        if (!oldCard.isExpired()) {
            throw new InvalidCardStateException("Card is not expired. Cannot replace.");
        }

        // Create new replacement card with new expiry date
        Card newCard = createReplacementCard(oldCard, "EXPIRED");

        // Transfer balances and limits
        transferCardDetails(oldCard, newCard);

        // Save new card
        newCard = cardRepository.save(newCard);

        // Mark old card as expired
        oldCard.setCardStatus(CardStatus.EXPIRED);
        cardRepository.save(oldCard);

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(newCard.getCardId()),
                newCard.getCardType().name(),
                java.util.Map.of("replacementFor", oldCardId.toString(), "reason", "EXPIRED"));

        // Notify
        cardNotificationService.sendCardReplacementNotification(userId, oldCard, newCard, email, phoneNumber);

        log.info("Expired card replaced successfully - Old: {}, New: {}", oldCardId, newCard.getCardId());
        return newCard;
    }

    /**
     * Replace a compromised card (fraud detection)
     *
     * @param oldCardId Old card ID
     * @param userId User ID
     * @param compromiseReason Reason for compromise
     * @param email User email
     * @param phoneNumber User phone
     * @return New replacement card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card replaceCompromisedCard(UUID oldCardId, UUID userId, String compromiseReason,
                                       String email, String phoneNumber) {
        log.warn("Replacing compromised card: {} for user: {} - Reason: {}",
                oldCardId, userId, compromiseReason);

        Card oldCard = cardRepository.findByCardIdAndNotDeleted(oldCardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + oldCardId));

        // Validate ownership
        validateCardOwnership(oldCard, userId);

        // Block old card immediately
        oldCard.setCardStatus(CardStatus.BLOCKED);
        oldCard.setBlockedReason("Card compromised: " + compromiseReason);
        oldCard.setBlockedAt(LocalDateTime.now());
        cardRepository.save(oldCard);

        // Audit security event
        cardAuditService.logCardBlocked(userId, oldCardId,
                "Card compromised: " + compromiseReason, true);

        // Create new replacement card with new card number
        Card newCard = createReplacementCard(oldCard, "COMPROMISED");

        // Transfer balances only (not limits - may need review)
        newCard.setOutstandingBalance(oldCard.getOutstandingBalance());
        newCard.setAvailableCredit(oldCard.getAvailableCredit());

        // Save new card
        newCard = cardRepository.save(newCard);

        // Cancel old card
        oldCard.setCardStatus(CardStatus.CANCELLED);
        oldCard.setCancelledAt(LocalDateTime.now());
        cardRepository.save(oldCard);

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(newCard.getCardId()),
                newCard.getCardType().name(),
                java.util.Map.of("replacementFor", oldCardId.toString(),
                        "reason", "COMPROMISED",
                        "compromiseReason", compromiseReason));

        // Send urgent notification
        cardNotificationService.sendCardReplacementNotification(userId, oldCard, newCard, email, phoneNumber);
        cardNotificationService.sendFraudAlertNotification(userId, oldCard, null,
                new BigDecimal("100.0"), email, phoneNumber);

        log.warn("Compromised card replaced successfully - Old: {}, New: {}", oldCardId, newCard.getCardId());
        return newCard;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private void validateCardOwnership(Card card, UUID userId) {
        if (!card.getUserId().equals(userId)) {
            log.warn("User {} attempted to replace card {} owned by {}",
                    userId, card.getCardId(), card.getUserId());
            throw new InvalidCardStateException("Card does not belong to user");
        }
    }

    private Card createReplacementCard(Card oldCard, String replacementReason) {
        // Generate new card number
        String newCardNumber = generateNewCardNumber(oldCard.getCardBrand().name());

        // Encrypt new card number
        String encryptedCardNumber = cardSecurityService.encryptCardNumber(newCardNumber);

        // Extract last 4 digits
        String lastFour = cardSecurityService.extractLastFour(newCardNumber);

        // Generate new card ID
        String newCardId = "CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Generate new expiry date (3 years from now)
        LocalDate newExpiryDate = LocalDate.now().plusYears(3);

        // Create new card
        Card newCard = Card.builder()
                .cardId(newCardId)
                .userId(oldCard.getUserId())
                .accountId(oldCard.getAccountId())
                .cardNumberEncrypted(encryptedCardNumber)
                .cardNumberLastFour(lastFour)
                .cardType(oldCard.getCardType())
                .cardBrand(oldCard.getCardBrand())
                .cardStatus(CardStatus.PENDING_ACTIVATION) // Needs activation
                .productId(oldCard.getProductId())
                .cardProduct(oldCard.getCardProduct())
                .cardHolderName(oldCard.getCardHolderName())
                .expiryDate(newExpiryDate)
                .issueDate(LocalDate.now())
                .currency(oldCard.getCurrency())
                .isVirtual(oldCard.getIsVirtual())
                .isContactless(oldCard.getIsContactless())
                .build();

        log.info("Created replacement card: {} for old card: {} - Reason: {}",
                newCardId, oldCard.getCardId(), replacementReason);

        return newCard;
    }

    private void transferCardDetails(Card oldCard, Card newCard) {
        // Transfer credit limit
        if (oldCard.getCreditLimit() != null) {
            newCard.setCreditLimit(oldCard.getCreditLimit());
        }

        // Transfer outstanding balance
        if (oldCard.getOutstandingBalance() != null) {
            newCard.setOutstandingBalance(oldCard.getOutstandingBalance());
        }

        // Calculate available credit
        if (newCard.getCreditLimit() != null && newCard.getOutstandingBalance() != null) {
            BigDecimal availableCredit = newCard.getCreditLimit()
                    .subtract(newCard.getOutstandingBalance());
            newCard.setAvailableCredit(availableCredit);
        }

        // Transfer billing info
        newCard.setBillingAddress(oldCard.getBillingAddress());
        newCard.setBillingCity(oldCard.getBillingCity());
        newCard.setBillingState(oldCard.getBillingState());
        newCard.setBillingPostalCode(oldCard.getBillingPostalCode());
        newCard.setBillingCountry(oldCard.getBillingCountry());

        log.debug("Transferred card details from old card to new card");
    }

    private String generateNewCardNumber(String cardBrand) {
        // Generate new card number based on card brand
        // In production: Integrate with card number generation service or BIN provider

        String bin; // Bank Identification Number (first 6 digits)

        switch (cardBrand.toUpperCase()) {
            case "VISA":
                bin = "426684"; // Example Visa BIN
                break;
            case "MASTERCARD":
                bin = "542418"; // Example Mastercard BIN
                break;
            case "AMEX":
                bin = "374442"; // Example Amex BIN (15 digits total)
                break;
            default:
                bin = "400000"; // Default BIN
        }

        // Generate middle digits (random)
        int middleLength = (cardBrand.equalsIgnoreCase("AMEX") ? 15 : 16) - bin.length() - 1;
        StringBuilder cardNumber = new StringBuilder(bin);

        for (int i = 0; i < middleLength; i++) {
            cardNumber.append((int) (Math.random() * 10));
        }

        // Calculate Luhn check digit
        String baseNumber = cardNumber.toString();
        int checkDigit = calculateLuhnCheckDigit(baseNumber);
        cardNumber.append(checkDigit);

        String generatedNumber = cardNumber.toString();

        // Validate using Luhn algorithm
        if (!cardSecurityService.validateCardNumberLuhn(generatedNumber)) {
            log.error("Generated invalid card number - Luhn check failed");
            throw new RuntimeException("Card number generation failed");
        }

        log.debug("Generated new card number with BIN: {}", bin);
        return generatedNumber;
    }

    private int calculateLuhnCheckDigit(String baseNumber) {
        int sum = 0;
        boolean alternate = true; // Start with alternate since we're calculating check digit

        for (int i = baseNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(baseNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit;
    }
}
