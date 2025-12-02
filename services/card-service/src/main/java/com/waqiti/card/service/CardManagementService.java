package com.waqiti.card.service;

import com.waqiti.card.entity.Card;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.exception.CardNotFoundException;
import com.waqiti.card.exception.InvalidCardStateException;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CardManagementService - Card lifecycle management operations
 *
 * Handles:
 * - Card blocking/unblocking
 * - Card freezing/unfreezing (temporary suspension)
 * - Card cancellation
 * - Card suspension
 * - PIN management
 * - Card status management
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardManagementService {

    private final CardRepository cardRepository;
    private final CardSecurityService cardSecurityService;
    private final CardNotificationService cardNotificationService;
    private final CardAuditService cardAuditService;

    /**
     * Block a card (permanent until unblocked)
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param reason Blocking reason
     * @param email User email
     * @param phoneNumber User phone
     * @return Blocked card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card blockCard(UUID cardId, UUID userId, String reason, String email, String phoneNumber) {
        log.info("Blocking card: {} for user: {} - Reason: {}", cardId, userId, reason);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        // Cannot block already blocked or cancelled cards
        if (card.getCardStatus() == CardStatus.BLOCKED) {
            throw new InvalidCardStateException("Card is already blocked");
        }
        if (card.getCardStatus() == CardStatus.CANCELLED) {
            throw new InvalidCardStateException("Cannot block cancelled card");
        }

        card.setCardStatus(CardStatus.BLOCKED);
        card.setBlockedReason(reason);
        card.setBlockedAt(LocalDateTime.now());
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardBlocked(userId, cardId, reason, false);

        // Notify
        cardNotificationService.sendCardBlockedNotification(userId, card, reason, email, phoneNumber);

        log.info("Card blocked successfully: {}", cardId);
        return card;
    }

    /**
     * Unblock a card
     *
     * @param cardId Card ID
     * @param userId User ID
     * @return Unblocked card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card unblockCard(UUID cardId, UUID userId) {
        log.info("Unblocking card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        if (card.getCardStatus() != CardStatus.BLOCKED) {
            throw new InvalidCardStateException("Card is not blocked");
        }

        card.unblock();
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardActivation(userId, cardId, "Card unblocked");

        log.info("Card unblocked successfully: {}", cardId);
        return card;
    }

    /**
     * Freeze a card (temporary suspension by user)
     *
     * @param cardId Card ID
     * @param userId User ID
     * @return Frozen card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card freezeCard(UUID cardId, UUID userId) {
        log.info("Freezing card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        if (card.getCardStatus() != CardStatus.ACTIVE) {
            throw new InvalidCardStateException(
                    "Only active cards can be frozen. Current status: " + card.getCardStatus());
        }

        card.setCardStatus(CardStatus.SUSPENDED);
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardDeactivation(userId, cardId, "Card frozen by user");

        log.info("Card frozen successfully: {}", cardId);
        return card;
    }

    /**
     * Unfreeze a card
     *
     * @param cardId Card ID
     * @param userId User ID
     * @return Unfrozen card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card unfreezeCard(UUID cardId, UUID userId) {
        log.info("Unfreezing card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        if (card.getCardStatus() != CardStatus.SUSPENDED) {
            throw new InvalidCardStateException("Card is not frozen");
        }

        card.setCardStatus(CardStatus.ACTIVE);
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardActivation(userId, cardId, "Card unfrozen by user");

        log.info("Card unfrozen successfully: {}", cardId);
        return card;
    }

    /**
     * Cancel a card (permanent, cannot be reversed)
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param reason Cancellation reason
     * @param email User email
     * @param phoneNumber User phone
     * @return Cancelled card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card cancelCard(UUID cardId, UUID userId, String reason, String email, String phoneNumber) {
        log.info("Cancelling card: {} for user: {} - Reason: {}", cardId, userId, reason);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        if (card.getCardStatus() == CardStatus.CANCELLED) {
            throw new InvalidCardStateException("Card is already cancelled");
        }

        card.setCardStatus(CardStatus.CANCELLED);
        card.setCancelledAt(LocalDateTime.now());
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardDeactivation(userId, cardId, "Card cancelled: " + reason);

        // Notify
        cardNotificationService.sendCardBlockedNotification(userId, card,
                "Card cancelled: " + reason, email, phoneNumber);

        log.info("Card cancelled successfully: {}", cardId);
        return card;
    }

    /**
     * Change card PIN
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param currentPin Current PIN
     * @param newPin New PIN
     * @param email User email
     * @param phoneNumber User phone
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card changePin(UUID cardId, UUID userId, String currentPin, String newPin,
                          String email, String phoneNumber) {
        log.info("Changing PIN for card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        // Verify card is active
        if (card.getCardStatus() != CardStatus.ACTIVE) {
            throw new InvalidCardStateException("Card must be active to change PIN");
        }

        // Check if PIN is locked
        if (card.isPinLocked()) {
            throw new InvalidCardStateException("PIN is locked until " + card.getPinLockedUntil());
        }

        // Verify current PIN if already set
        if (card.getPinSet() && card.getPinHash() != null) {
            boolean currentPinValid = cardSecurityService.verifyPin(currentPin, card.getPinHash());

            if (!currentPinValid) {
                card.incrementPinAttempts();
                cardRepository.save(card);

                // Audit failed attempt
                cardAuditService.logFailedPinAttempt(userId, cardId, card.getPinAttempts());

                throw new InvalidCardStateException("Invalid current PIN");
            }
        }

        // Hash new PIN
        String newPinHash = cardSecurityService.hashPin(newPin);

        // Update PIN
        card.setPinHash(newPinHash);
        card.setPinSet(true);
        card.resetPinAttempts();
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logPinChange(userId, cardId, true);

        // Notify
        cardNotificationService.sendPinChangeNotification(userId, card, email, phoneNumber);

        log.info("PIN changed successfully for card: {}", cardId);
        return card;
    }

    /**
     * Set PIN for new card (first-time PIN setup)
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param pin PIN
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card setPin(UUID cardId, UUID userId, String pin) {
        log.info("Setting PIN for card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        if (card.getPinSet()) {
            throw new InvalidCardStateException("PIN is already set. Use changePin to update.");
        }

        // Hash PIN
        String pinHash = cardSecurityService.hashPin(pin);

        // Set PIN
        card.setPinHash(pinHash);
        card.setPinSet(true);
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logPinChange(userId, cardId, true);

        log.info("PIN set successfully for card: {}", cardId);
        return card;
    }

    /**
     * Verify PIN (for ATM/POS transactions)
     *
     * @param cardId Card ID
     * @param pin PIN to verify
     * @return true if PIN is correct, false otherwise
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean verifyPin(UUID cardId, String pin) {
        log.debug("Verifying PIN for card: {}", cardId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        if (!card.getPinSet() || card.getPinHash() == null) {
            log.warn("PIN not set for card: {}", cardId);
            return false;
        }

        if (card.isPinLocked()) {
            log.warn("PIN is locked for card: {} until {}", cardId, card.getPinLockedUntil());
            return false;
        }

        boolean verified = cardSecurityService.verifyPin(pin, card.getPinHash());

        if (verified) {
            card.resetPinAttempts();
            cardRepository.save(card);
            log.debug("PIN verified successfully for card: {}", cardId);
        } else {
            card.incrementPinAttempts();
            cardRepository.save(card);

            // Audit failed attempt
            cardAuditService.logFailedPinAttempt(card.getUserId(), cardId, card.getPinAttempts());

            log.warn("Invalid PIN for card: {} - Attempt {}", cardId, card.getPinAttempts());
        }

        return verified;
    }

    /**
     * Reset PIN (requires verification through other means)
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param newPin New PIN
     * @param email User email
     * @param phoneNumber User phone
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card resetPin(UUID cardId, UUID userId, String newPin, String email, String phoneNumber) {
        log.info("Resetting PIN for card: {} for user: {}", cardId, userId);

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        // Hash new PIN
        String newPinHash = cardSecurityService.hashPin(newPin);

        // Reset PIN
        card.setPinHash(newPinHash);
        card.setPinSet(true);
        card.resetPinAttempts();
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logPinChange(userId, cardId, true);

        // Notify
        cardNotificationService.sendPinChangeNotification(userId, card, email, phoneNumber);

        log.info("PIN reset successfully for card: {}", cardId);
        return card;
    }

    /**
     * Get all cards for a user
     *
     * @param userId User ID
     * @return List of user's cards
     */
    public List<Card> getUserCards(UUID userId) {
        return cardRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Get active cards for a user
     *
     * @param userId User ID
     * @return List of active cards
     */
    public List<Card> getActiveCards(UUID userId) {
        return cardRepository.findByUserIdAndCardStatusAndDeletedAtIsNull(userId, CardStatus.ACTIVE);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private void validateOwnership(Card card, UUID userId) {
        if (!card.getUserId().equals(userId)) {
            log.warn("User {} attempted operation on card {} owned by {}",
                    userId, card.getCardId(), card.getUserId());
            throw new InvalidCardStateException("Card does not belong to user");
        }
    }
}
