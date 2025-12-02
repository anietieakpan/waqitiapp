package com.waqiti.card.service;

import com.waqiti.card.entity.Card;
import com.waqiti.card.exception.CardNotFoundException;
import com.waqiti.card.exception.InvalidCardStateException;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * LimitManagementService - Card limit operations
 *
 * Handles:
 * - Credit limit changes
 * - Spending limit management
 * - Temporary limit increases
 * - Daily/monthly limit enforcement
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LimitManagementService {

    private final CardRepository cardRepository;
    private final CardNotificationService cardNotificationService;
    private final CardAuditService cardAuditService;

    /**
     * Update credit limit
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param newLimit New credit limit
     * @param email User email
     * @param phoneNumber User phone
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card updateCreditLimit(UUID cardId, UUID userId, BigDecimal newLimit,
                                  String email, String phoneNumber) {
        log.info("Updating credit limit for card: {} - New limit: {}", cardId, newLimit);

        if (newLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidCardStateException("Credit limit cannot be negative");
        }

        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        BigDecimal oldLimit = card.getCreditLimit();

        // Update credit limit
        card.setCreditLimit(newLimit);

        // Recalculate available credit
        BigDecimal outstandingBalance = card.getOutstandingBalance() != null ?
                card.getOutstandingBalance() : BigDecimal.ZERO;
        BigDecimal newAvailableCredit = newLimit.subtract(outstandingBalance);

        card.setAvailableCredit(newAvailableCredit);
        card = cardRepository.save(card);

        // Audit
        cardAuditService.logLimitChange(userId, cardId, oldLimit, newLimit);

        // Notify
        cardNotificationService.sendLimitChangeNotification(userId, card, oldLimit, newLimit,
                email, phoneNumber);

        log.info("Credit limit updated successfully for card: {}", cardId);
        return card;
    }

    /**
     * Increase credit limit
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param increaseAmount Amount to increase
     * @param email User email
     * @param phoneNumber User phone
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card increaseCreditLimit(UUID cardId, UUID userId, BigDecimal increaseAmount,
                                    String email, String phoneNumber) {
        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        BigDecimal currentLimit = card.getCreditLimit() != null ?
                card.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal newLimit = currentLimit.add(increaseAmount);

        return updateCreditLimit(cardId, userId, newLimit, email, phoneNumber);
    }

    /**
     * Decrease credit limit
     *
     * @param cardId Card ID
     * @param userId User ID
     * @param decreaseAmount Amount to decrease
     * @param email User email
     * @param phoneNumber User phone
     * @return Updated card
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Card decreaseCreditLimit(UUID cardId, UUID userId, BigDecimal decreaseAmount,
                                    String email, String phoneNumber) {
        Card card = cardRepository.findByCardIdAndNotDeleted(cardId.toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));

        validateOwnership(card, userId);

        BigDecimal currentLimit = card.getCreditLimit() != null ?
                card.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal newLimit = currentLimit.subtract(decreaseAmount);

        if (newLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidCardStateException("Cannot decrease limit below zero");
        }

        BigDecimal outstandingBalance = card.getOutstandingBalance() != null ?
                card.getOutstandingBalance() : BigDecimal.ZERO;

        if (newLimit.compareTo(outstandingBalance) < 0) {
            throw new InvalidCardStateException(
                    "New limit cannot be less than outstanding balance: " + outstandingBalance);
        }

        return updateCreditLimit(cardId, userId, newLimit, email, phoneNumber);
    }

    private void validateOwnership(Card card, UUID userId) {
        if (!card.getUserId().equals(userId)) {
            log.warn("User {} attempted to modify limits on card {} owned by {}",
                    userId, card.getCardId(), card.getUserId());
            throw new InvalidCardStateException("Card does not belong to user");
        }
    }
}
