package com.waqiti.card.service;

import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardProduct;
import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.enums.CardType;
import com.waqiti.card.exception.CardIssuanceException;
import com.waqiti.card.repository.CardProductRepository;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CardIssuanceService - Card issuance and provisioning
 *
 * Handles:
 * - Physical card issuance
 * - Virtual card generation
 * - Card number generation and encryption
 * - Initial card setup
 * - Shipping coordination
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardIssuanceService {

    private final CardRepository cardRepository;
    private final CardProductRepository cardProductRepository;
    private final CardSecurityService cardSecurityService;
    private final CardActivationService cardActivationService;
    private final CardAuditService cardAuditService;
    private final CardMetricsService cardMetricsService;

    /**
     * Issue a new physical card
     *
     * @param userId User ID
     * @param accountId Account ID
     * @param productId Card product ID
     * @param cardType Card type
     * @param cardBrand Card brand
     * @param cardHolderName Cardholder name
     * @param creditLimit Credit limit
     * @return Newly issued card
     */
    @Transactional
    public Card issuePhysicalCard(UUID userId, UUID accountId, String productId,
                                  CardType cardType, CardBrand cardBrand,
                                  String cardHolderName, BigDecimal creditLimit) {
        log.info("Issuing physical card for user: {} - Type: {}, Brand: {}", userId, cardType, cardBrand);

        // Validate product
        CardProduct product = cardProductRepository.findByProductIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CardIssuanceException("Card product not found: " + productId));

        // Generate card number
        String cardNumber = generateCardNumber(cardBrand);

        // Encrypt card number
        String encryptedCardNumber = cardSecurityService.encryptCardNumber(cardNumber);
        String lastFour = cardSecurityService.extractLastFour(cardNumber);

        // Generate card ID
        String cardId = "CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Create card
        Card card = Card.builder()
                .cardId(cardId)
                .userId(userId)
                .accountId(accountId)
                .cardNumberEncrypted(encryptedCardNumber)
                .cardNumberLastFour(lastFour)
                .cardType(cardType)
                .cardBrand(cardBrand)
                .cardStatus(CardStatus.PENDING_ACTIVATION)
                .productId(productId)
                .cardProduct(product)
                .cardHolderName(cardHolderName)
                .expiryDate(LocalDate.now().plusYears(3))
                .issueDate(LocalDate.now())
                .creditLimit(creditLimit)
                .availableCredit(creditLimit)
                .outstandingBalance(BigDecimal.ZERO)
                .currency("USD")
                .isVirtual(false)
                .isContactless(true)
                .build();

        card = cardRepository.save(card);

        // Generate activation code
        String activationCode = cardActivationService.generateAndStoreActivationCode(
                UUID.fromString(card.getCardId()));

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(card.getCardId()),
                cardType.name(), java.util.Map.of("productId", productId, "physical", true));

        // Metrics
        cardMetricsService.recordCardCreation(cardType.name());

        log.info("Physical card issued successfully: {} - Activation code: {}", cardId, activationCode);
        return card;
    }

    /**
     * Issue virtual card (instant)
     *
     * @param userId User ID
     * @param accountId Account ID
     * @param cardType Card type
     * @param cardBrand Card brand
     * @param creditLimit Credit limit
     * @return Virtual card (already active)
     */
    @Transactional
    public Card issueVirtualCard(UUID userId, UUID accountId, CardType cardType,
                                 CardBrand cardBrand, BigDecimal creditLimit) {
        log.info("Issuing virtual card for user: {} - Type: {}, Brand: {}", userId, cardType, cardBrand);

        // Generate card number
        String cardNumber = generateCardNumber(cardBrand);
        String encryptedCardNumber = cardSecurityService.encryptCardNumber(cardNumber);
        String lastFour = cardSecurityService.extractLastFour(cardNumber);

        String cardId = "VRT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Card card = Card.builder()
                .cardId(cardId)
                .userId(userId)
                .accountId(accountId)
                .cardNumberEncrypted(encryptedCardNumber)
                .cardNumberLastFour(lastFour)
                .cardType(cardType)
                .cardBrand(cardBrand)
                .cardStatus(CardStatus.ACTIVE) // Virtual cards are immediately active
                .expiryDate(LocalDate.now().plusYears(2)) // Shorter expiry for virtual
                .issueDate(LocalDate.now())
                .activatedAt(java.time.LocalDateTime.now())
                .creditLimit(creditLimit)
                .availableCredit(creditLimit)
                .outstandingBalance(BigDecimal.ZERO)
                .currency("USD")
                .isVirtual(true)
                .isContactless(false)
                .build();

        card = cardRepository.save(card);

        // Audit
        cardAuditService.logCardCreation(userId, UUID.fromString(card.getCardId()),
                cardType.name(), java.util.Map.of("virtual", true));

        // Metrics
        cardMetricsService.recordCardCreation("VIRTUAL_" + cardType.name());

        log.info("Virtual card issued and activated: {}", cardId);
        return card;
    }

    private String generateCardNumber(CardBrand cardBrand) {
        String bin;
        int length;

        switch (cardBrand) {
            case VISA:
                bin = "426684";
                length = 16;
                break;
            case MASTERCARD:
                bin = "542418";
                length = 16;
                break;
            case AMEX:
                bin = "374442";
                length = 15;
                break;
            default:
                bin = "400000";
                length = 16;
        }

        StringBuilder cardNumber = new StringBuilder(bin);
        for (int i = 0; i < length - bin.length() - 1; i++) {
            cardNumber.append((int) (Math.random() * 10));
        }

        // Calculate Luhn check digit
        int checkDigit = calculateLuhnCheckDigit(cardNumber.toString());
        cardNumber.append(checkDigit);

        return cardNumber.toString();
    }

    private int calculateLuhnCheckDigit(String baseNumber) {
        int sum = 0;
        boolean alternate = true;

        for (int i = baseNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(baseNumber.charAt(i));
            if (alternate) {
                digit *= 2;
                if (digit > 9) digit = (digit % 10) + 1;
            }
            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }
}
