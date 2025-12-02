package com.waqiti.virtualcard.provider.impl;

import com.waqiti.virtualcard.provider.CardProvider;
import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.domain.enums.CardStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of CardProvider for demonstration/testing
 * In production, this would integrate with actual card providers like Marqeta, Galileo, etc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCardProvider implements CardProvider {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Value("${card-provider.api-url:https://api.example-provider.com}")
    private String apiUrl;
    
    @Value("${card-provider.api-key:demo-key}")
    private String apiKey;
    
    @Value("${card-provider.environment:sandbox}")
    private String environment;
    
    @Override
    public CardProviderOrderResponse submitCardOrder(CardProviderOrderRequest request) {
        log.info("Submitting card order to provider: {}", request.getOrderId());
        
        try {
            // Simulate API call to card provider
            simulateApiDelay();
            
            // Generate provider response
            return CardProviderOrderResponse.builder()
                .success(true)
                .providerOrderId(generateProviderOrderId())
                .providerCardId(generateProviderCardId())
                .lastFourDigits(generateLastFourDigits())
                .expiryMonth(12)
                .expiryYear(java.time.LocalDate.now().getYear() + 3)
                .estimatedProductionDays(5)
                .status("SUBMITTED")
                .message("Card order submitted successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to submit card order to provider", e);
            return CardProviderOrderResponse.builder()
                .success(false)
                .errorCode("PROVIDER_ERROR")
                .errorMessage("Failed to submit order to card provider: " + e.getMessage())
                .build();
        }
    }
    
    @Override
    public CardProviderOrderResponse submitReplacementOrder(CardProviderReplacementRequest request) {
        log.info("Submitting card replacement order to provider: {}", request.getOrderId());
        
        try {
            simulateApiDelay();
            
            return CardProviderOrderResponse.builder()
                .success(true)
                .providerOrderId(generateProviderOrderId())
                .providerCardId(generateProviderCardId())
                .lastFourDigits(generateLastFourDigits())
                .expiryMonth(12)
                .expiryYear(java.time.LocalDate.now().getYear() + 3)
                .estimatedProductionDays(request.isRushDelivery() ? 2 : 3)
                .status("SUBMITTED")
                .message("Replacement card order submitted successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to submit replacement order to provider", e);
            return CardProviderOrderResponse.builder()
                .success(false)
                .errorCode("PROVIDER_ERROR")
                .errorMessage("Failed to submit replacement order: " + e.getMessage())
                .build();
        }
    }
    
    @Override
    public CardProductionStatus getProductionStatus(String providerCardId) {
        log.debug("Getting production status for card: {}", providerCardId);
        
        try {
            simulateApiDelay();
            
            // Simulate production progression
            CardStatus status = simulateProductionStatus(providerCardId);
            
            return CardProductionStatus.builder()
                .providerCardId(providerCardId)
                .status(status)
                .statusMessage(getStatusMessage(status))
                .estimatedShipDate(Instant.now().plusSeconds(86400 * 2)) // 2 days from now
                .trackingNumber(status == CardStatus.SHIPPED ? generateTrackingNumber() : null)
                .carrier(status == CardStatus.SHIPPED ? "FedEx" : null)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get production status from provider", e);
            return CardProductionStatus.builder()
                .providerCardId(providerCardId)
                .status(CardStatus.ORDERED)
                .statusMessage("Unable to retrieve status")
                .hasError(true)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    @Override
    public boolean verifyActivation(String providerCardId, String activationCode, String pin) {
        log.info("Verifying activation for card: {}", providerCardId);
        
        try {
            simulateApiDelay();
            
            // Simulate activation verification
            // In demo mode, accept any 4-8 digit code and 4-6 digit PIN
            boolean codeValid = activationCode != null && 
                activationCode.matches("^[A-Z0-9]{4,8}$");
            boolean pinValid = pin != null && pin.matches("^\\d{4,6}$");
            
            return codeValid && pinValid;
            
        } catch (Exception e) {
            log.error("Failed to verify activation with provider", e);
            return false;
        }
    }
    
    @Override
    public void activatePhysicalCard(String providerCardId) {
        log.info("Activating physical card with provider: {}", providerCardId);
        
        try {
            simulateApiDelay();
            // Simulate activation API call
            log.info("Card {} activated successfully with provider", providerCardId);
            
        } catch (Exception e) {
            log.error("Failed to activate card with provider", e);
            throw new RuntimeException("Failed to activate card with provider", e);
        }
    }
    
    @Override
    public void blockCard(String providerCardId, String reason) {
        log.info("Blocking card {} with provider, reason: {}", providerCardId, reason);
        
        try {
            simulateApiDelay();
            // Simulate blocking API call
            log.info("Card {} blocked successfully with provider", providerCardId);
            
        } catch (Exception e) {
            log.error("Failed to block card with provider", e);
            throw new RuntimeException("Failed to block card with provider", e);
        }
    }
    
    @Override
    public void updateCardBalance(String providerCardId, BigDecimal balance) {
        log.debug("Updating card balance for {}: {}", providerCardId, balance);
        
        try {
            simulateApiDelay();
            // Simulate balance update API call
            log.debug("Balance updated successfully for card {}", providerCardId);
            
        } catch (Exception e) {
            log.error("Failed to update card balance with provider", e);
            throw new RuntimeException("Failed to update card balance", e);
        }
    }
    
    @Override
    public CardProviderDetails getCardDetails(String providerCardId) {
        log.debug("Getting card details from provider: {}", providerCardId);
        
        try {
            simulateApiDelay();
            
            return CardProviderDetails.builder()
                .providerCardId(providerCardId)
                .status("ACTIVE")
                .balance(BigDecimal.valueOf(1000.00))
                .availableBalance(BigDecimal.valueOf(1000.00))
                .currency("USD")
                .lastFourDigits("1234")
                .expiryMonth(12)
                .expiryYear(2027)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get card details from provider", e);
            throw new RuntimeException("Failed to get card details", e);
        }
    }
    
    @Override
    public void updateCardLimits(String providerCardId, CardLimitsUpdate limits) {
        log.info("Updating card limits for {}", providerCardId);
        
        try {
            simulateApiDelay();
            // Simulate limits update API call
            log.info("Card limits updated successfully for {}", providerCardId);
            
        } catch (Exception e) {
            log.error("Failed to update card limits with provider", e);
            throw new RuntimeException("Failed to update card limits", e);
        }
    }
    
    @Override
    public void updateCardControls(String providerCardId, CardControlsUpdate controls) {
        log.info("Updating card controls for {}", providerCardId);
        
        try {
            simulateApiDelay();
            // Simulate controls update API call
            log.info("Card controls updated successfully for {}", providerCardId);
            
        } catch (Exception e) {
            log.error("Failed to update card controls with provider", e);
            throw new RuntimeException("Failed to update card controls", e);
        }
    }
    
    @Override
    public List<ProviderCardDesign> getAvailableDesigns() {
        log.debug("Getting available card designs from provider");
        
        try {
            simulateApiDelay();
            
            return List.of(
                ProviderCardDesign.builder()
                    .designId("standard-blue")
                    .name("Standard Blue")
                    .category("Standard")
                    .isPremium(false)
                    .fee(BigDecimal.ZERO)
                    .available(true)
                    .build(),
                ProviderCardDesign.builder()
                    .designId("premium-black")
                    .name("Premium Black")
                    .category("Premium")
                    .isPremium(true)
                    .fee(BigDecimal.valueOf(10.00))
                    .available(true)
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to get available designs from provider", e);
            return List.of();
        }
    }
    
    @Override
    public CardProductionCapabilities getProductionCapabilities() {
        return CardProductionCapabilities.builder()
            .supportsPhysicalCards(true)
            .supportsVirtualCards(true)
            .supportsCustomDesigns(true)
            .supportsEmbossing(true)
            .supportsRushOrders(true)
            .minProductionDays(2)
            .standardProductionDays(5)
            .maxCustomTextLength(20)
            .supportedCardTypes(List.of("DEBIT", "PREPAID"))
            .supportedBrands(List.of("VISA", "MASTERCARD"))
            .build();
    }
    
    @Override
    public CardOrderValidationResult validateCardOrder(CardProviderOrderRequest request) {
        // Basic validation
        boolean valid = request != null && 
                       request.getUserId() != null &&
                       request.getOrderId() != null;
        
        return CardOrderValidationResult.builder()
            .valid(valid)
            .errorMessage(valid ? null : "Invalid order request")
            .build();
    }
    
    @Override
    public int getEstimatedProductionDays(String cardType, boolean rushOrder) {
        return rushOrder ? 2 : 5;
    }
    
    @Override
    public boolean cancelCardOrder(String providerOrderId) {
        log.info("Cancelling card order: {}", providerOrderId);
        
        try {
            simulateApiDelay();
            return true; // Simulate successful cancellation
            
        } catch (Exception e) {
            log.error("Failed to cancel card order", e);
            return false;
        }
    }
    
    @Override
    public List<ProviderTransaction> getTransactionHistory(String providerCardId,
                                                          Instant fromDate,
                                                          Instant toDate) {
        log.debug("Getting transaction history for card: {}", providerCardId);

        try {
            simulateApiDelay();
            // Return empty list for demo
            return List.of();

        } catch (Exception e) {
            log.error("Failed to get transaction history from provider", e);
            return List.of();
        }
    }

    @Override
    public String getDynamicCvv(String providerCardId) {
        log.info("Retrieving dynamic CVV for card: {} (PCI DSS compliant - not stored)", providerCardId);

        try {
            simulateApiDelay();

            // PCI DSS COMPLIANT: Generate or retrieve dynamic CVV from provider
            // In production, this would call the actual card provider's API
            // Some providers support dynamic CVV that changes periodically
            // Others generate CVV on-demand for each request

            // Simulate provider API call to get dynamic CVV
            String dynamicCvv = generateDynamicCvv(providerCardId);

            log.info("Dynamic CVV retrieved successfully for card: {}", providerCardId);

            return dynamicCvv;

        } catch (Exception e) {
            log.error("Failed to retrieve dynamic CVV from provider for card: {}", providerCardId, e);
            throw new RuntimeException("Failed to retrieve card security code. Please try again.", e);
        }
    }

    @Override
    public CardProviderResponse createCard(CardProviderRequest request) {
        log.info("Creating card with provider for user: {}", request.getUserId());

        try {
            simulateApiDelay();

            // Generate card number (PAN)
            String cardNumber = generateCardNumber(request.getType());

            return CardProviderResponse.builder()
                .providerId(generateProviderCardId())
                .cardNumber(cardNumber)
                .lastFourDigits(cardNumber.substring(cardNumber.length() - 4))
                .expiryMonth(12)
                .expiryYear(java.time.LocalDate.now().getYear() + 3)
                .cvv(null) // CVV never returned in create response (PCI DSS compliant)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Failed to create card with provider", e);
            throw new RuntimeException("Failed to create card with provider", e);
        }
    }

    @Override
    public void updateCardStatus(String providerCardId, CardStatus status) {
        log.info("Updating card status for {}: {}", providerCardId, status);

        try {
            simulateApiDelay();
            log.info("Card status updated successfully for {}", providerCardId);

        } catch (Exception e) {
            log.error("Failed to update card status with provider", e);
            throw new RuntimeException("Failed to update card status", e);
        }
    }

    @Override
    public void approveTransaction(String transactionId) {
        log.info("Approving transaction: {}", transactionId);

        try {
            simulateApiDelay();
            log.info("Transaction approved: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to approve transaction", e);
            throw new RuntimeException("Failed to approve transaction", e);
        }
    }

    @Override
    public void declineTransaction(String transactionId, String reason) {
        log.info("Declining transaction {}: {}", transactionId, reason);

        try {
            simulateApiDelay();
            log.info("Transaction declined: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to decline transaction", e);
            throw new RuntimeException("Failed to decline transaction", e);
        }
    }

    @Override
    public void closeCard(String providerCardId) {
        log.info("Closing card with provider: {}", providerCardId);

        try {
            simulateApiDelay();
            log.info("Card closed successfully: {}", providerCardId);

        } catch (Exception e) {
            log.error("Failed to close card with provider", e);
            throw new RuntimeException("Failed to close card", e);
        }
    }

    @Override
    public void expireCard(String providerCardId) {
        log.info("Expiring card with provider: {}", providerCardId);

        try {
            simulateApiDelay();
            log.info("Card expired successfully: {}", providerCardId);

        } catch (Exception e) {
            log.error("Failed to expire card with provider", e);
            throw new RuntimeException("Failed to expire card", e);
        }
    }

    // Helper methods for simulation
    
    private void simulateApiDelay() {
        try {
            Thread.sleep(100 + (long)(SECURE_RANDOM.nextInt(200))); // 100-300ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private String generateProviderOrderId() {
        return "ORD-" + System.currentTimeMillis();
    }
    
    private String generateProviderCardId() {
        return "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private String generateLastFourDigits() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }
    
    private String generateTrackingNumber() {
        return "1Z" + String.format("%016d", Math.abs(SECURE_RANDOM.nextLong()));
    }
    
    private CardStatus simulateProductionStatus(String providerCardId) {
        // Simulate progression based on card ID hash
        int hash = Math.abs(providerCardId.hashCode()) % 100;
        
        if (hash < 20) return CardStatus.ORDERED;
        if (hash < 40) return CardStatus.IN_PRODUCTION;
        if (hash < 70) return CardStatus.SHIPPED;
        return CardStatus.DELIVERED;
    }
    
    private String getStatusMessage(CardStatus status) {
        switch (status) {
            case ORDERED: return "Order received and queued for production";
            case IN_PRODUCTION: return "Card is being manufactured";
            case SHIPPED: return "Card has been shipped";
            case DELIVERED: return "Card has been delivered";
            default: return "Status unknown";
        }
    }

    /**
     * Generate dynamic CVV (PCI DSS compliant - simulated for demo)
     * In production, this would retrieve from the actual card provider
     */
    private String generateDynamicCvv(String providerCardId) {
        // Simulate dynamic CVV generation based on card ID and current time
        // Real implementation would call provider API to get actual dynamic CVV
        int cvvValue = (providerCardId.hashCode() + (int)(System.currentTimeMillis() / 60000)) % 1000;
        return String.format("%03d", Math.abs(cvvValue));
    }

    /**
     * Generate card number (PAN) - Luhn algorithm compliant
     */
    private String generateCardNumber(String cardType) {
        // Generate test card number (using test BIN ranges)
        // Real implementation would get this from provider
        StringBuilder pan = new StringBuilder();

        // Add BIN (first 6 digits) - using test BIN
        pan.append("400000"); // Visa test BIN

        // Add random middle digits
        for (int i = 0; i < 9; i++) {
            pan.append(SECURE_RANDOM.nextInt(10));
        }

        // Calculate and add Luhn check digit
        int checkDigit = calculateLuhnCheckDigit(pan.toString());
        pan.append(checkDigit);

        return pan.toString();
    }

    /**
     * Calculate Luhn check digit for card number validation
     */
    private int calculateLuhnCheckDigit(String partialPan) {
        int sum = 0;
        boolean alternate = true;

        for (int i = partialPan.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(partialPan.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }
}