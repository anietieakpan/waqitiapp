package com.waqiti.virtualcard.provider;

import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.domain.enums.CardStatus;

import java.math.BigDecimal;

/**
 * Interface for card provider integration (e.g., Marqeta, Galileo, etc.)
 */
public interface CardProvider {
    
    /**
     * Submit a card order to the provider
     */
    CardProviderOrderResponse submitCardOrder(CardProviderOrderRequest request);
    
    /**
     * Submit a card replacement order
     */
    CardProviderOrderResponse submitReplacementOrder(CardProviderReplacementRequest request);
    
    /**
     * Get production status of a card
     */
    CardProductionStatus getProductionStatus(String providerCardId);
    
    /**
     * Verify card activation code and PIN
     */
    boolean verifyActivation(String providerCardId, String activationCode, String pin);
    
    /**
     * Activate a physical card with the provider
     */
    void activatePhysicalCard(String providerCardId);
    
    /**
     * Block a card with the provider
     */
    void blockCard(String providerCardId, String reason);
    
    /**
     * Update card balance with the provider
     */
    void updateCardBalance(String providerCardId, BigDecimal balance);
    
    /**
     * Get card details from provider
     */
    CardProviderDetails getCardDetails(String providerCardId);
    
    /**
     * Update card limits with the provider
     */
    void updateCardLimits(String providerCardId, CardLimitsUpdate limits);
    
    /**
     * Update card controls with the provider
     */
    void updateCardControls(String providerCardId, CardControlsUpdate controls);
    
    /**
     * Get available card designs from provider
     */
    java.util.List<ProviderCardDesign> getAvailableDesigns();
    
    /**
     * Get card production capabilities (e.g., supported features)
     */
    CardProductionCapabilities getProductionCapabilities();
    
    /**
     * Validate card order before submission
     */
    CardOrderValidationResult validateCardOrder(CardProviderOrderRequest request);
    
    /**
     * Get estimated production time for card type
     */
    int getEstimatedProductionDays(String cardType, boolean rushOrder);
    
    /**
     * Cancel a card order (if possible)
     */
    boolean cancelCardOrder(String providerOrderId);
    
    /**
     * Get provider transaction history for a card
     */
    java.util.List<ProviderTransaction> getTransactionHistory(String providerCardId,
                                                               java.time.Instant fromDate,
                                                               java.time.Instant toDate);

    /**
     * Get dynamic CVV for a card (PCI DSS compliant - CVV never stored)
     *
     * This method retrieves a dynamically generated CVV from the card provider.
     * The CVV is generated on-demand and is never persisted in our system.
     *
     * @param providerCardId Provider's card identifier
     * @return Dynamic CVV code (3 or 4 digits)
     * @throws CardProviderException if CVV retrieval fails
     */
    String getDynamicCvv(String providerCardId);

    /**
     * Create a new virtual card
     *
     * @param request Card creation request
     * @return Card provider response with card details
     */
    CardProviderResponse createCard(CardProviderRequest request);

    /**
     * Update card status with provider
     *
     * @param providerCardId Provider's card identifier
     * @param status New card status
     */
    void updateCardStatus(String providerCardId, CardStatus status);

    /**
     * Approve a transaction
     *
     * @param transactionId Transaction identifier
     */
    void approveTransaction(String transactionId);

    /**
     * Decline a transaction
     *
     * @param transactionId Transaction identifier
     * @param reason Decline reason
     */
    void declineTransaction(String transactionId, String reason);

    /**
     * Close a card with the provider
     *
     * @param providerCardId Provider's card identifier
     */
    void closeCard(String providerCardId);

    /**
     * Expire a card
     *
     * @param providerCardId Provider's card identifier
     */
    void expireCard(String providerCardId);
}