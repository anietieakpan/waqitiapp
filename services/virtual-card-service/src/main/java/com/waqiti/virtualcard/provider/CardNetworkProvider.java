package com.waqiti.virtualcard.provider;

import com.waqiti.virtualcard.dto.CardNetworkRequest;
import com.waqiti.virtualcard.dto.CardNetworkResponse;
import com.waqiti.virtualcard.dto.CardNetworkRegistration;
import com.waqiti.virtualcard.dto.CardNetworkTransactionRequest;

/**
 * Interface for Card Network Providers (Visa, Mastercard, etc.)
 */
public interface CardNetworkProvider {
    
    /**
     * Register a new virtual card with the card network
     */
    CardNetworkRegistration registerCard(CardNetworkRequest request);
    
    /**
     * Update card status in the network
     */
    void updateCardStatus(String networkToken, String status);
    
    /**
     * Deregister a card from the network
     */
    void deregisterCard(String networkToken);
    
    /**
     * Process a transaction through the card network
     */
    CardNetworkResponse processTransaction(CardNetworkTransactionRequest request);
    
    /**
     * Update CVV for a card
     */
    void updateCVV(String networkToken, String newCvv);
    
    /**
     * Update 3D Secure settings
     */
    void update3DSecure(String networkToken, boolean enabled);
    
    /**
     * Check if provider is available
     */
    boolean isAvailable();
    
    /**
     * Get provider name
     */
    String getProviderName();
    
    /**
     * Get supported card networks
     */
    java.util.List<String> getSupportedNetworks();
}