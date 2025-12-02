package com.waqiti.payment.service;

import com.waqiti.payment.domain.InstantTransfer;
import com.waqiti.payment.dto.InstantTransferRequest;
import com.waqiti.payment.exception.ValidationException;
import com.waqiti.payment.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Production-grade validation service for instant transfers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstantTransferValidationService {

    private final UserServiceClient userServiceClient;

    @Value("${instant.transfer.min.amount:0.01}")
    private BigDecimal minTransferAmount;

    @Value("${instant.transfer.max.amount:10000.00}")
    private BigDecimal maxTransferAmount;

    @Value("${instant.transfer.max.memo.length:200}")
    private int maxMemoLength;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?1?[2-9]\\d{2}[2-9]\\d{2}\\d{4}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Validate instant transfer request
     */
    public void validateTransferRequest(InstantTransferRequest request) {
        log.debug("Validating instant transfer request: {}", request.getTransferId());

        validateBasicFields(request);
        validateAmount(request.getAmount());
        validateParticipants(request);
        validateMemo(request.getMemo());
        validateRecipientIdentifier(request.getRecipientIdentifier(), request.getRecipientIdentifierType());
    }

    /**
     * Validate instant transfer entity
     */
    public void validateTransfer(InstantTransfer transfer) {
        log.debug("Validating instant transfer entity: {}", transfer.getId());

        if (transfer.getSenderId() == null) {
            throw new ValidationException("Sender ID is required");
        }

        if (transfer.getRecipientId() == null) {
            throw new ValidationException("Recipient ID is required");
        }

        if (transfer.getAmount() == null) {
            throw new ValidationException("Transfer amount is required");
        }

        validateAmount(transfer.getAmount().getAmount());

        if (transfer.getCurrency() == null || transfer.getCurrency().isEmpty()) {
            throw new ValidationException("Currency is required");
        }

        validateCurrency(transfer.getCurrency());
    }

    /**
     * Validate that users are eligible for instant transfers
     */
    public void validateUserEligibility(String senderId, String recipientId) {
        log.debug("Validating user eligibility: sender={}, recipient={}", senderId, recipientId);

        // Validate sender eligibility
        if (!isUserEligibleForInstantTransfers(senderId)) {
            throw new ValidationException("Sender is not eligible for instant transfers");
        }

        // Validate recipient eligibility
        if (!isUserEligibleForInstantTransfers(recipientId)) {
            throw new ValidationException("Recipient is not eligible for instant transfers");
        }

        // Validate not self-transfer
        if (senderId.equals(recipientId)) {
            throw new ValidationException("Cannot transfer to yourself");
        }
    }

    /**
     * Validate transfer limits
     */
    public void validateTransferLimits(String userId, BigDecimal amount, String timeWindow) {
        log.debug("Validating transfer limits for user: {}, amount: {}", userId, amount);

        // This would integrate with the InstantTransferLimitService
        // For now, basic validation
        if (amount.compareTo(maxTransferAmount) > 0) {
            throw new ValidationException("Transfer amount exceeds maximum limit of " + maxTransferAmount);
        }
    }

    /**
     * Validate business rules for instant transfers
     */
    public void validateBusinessRules(InstantTransfer transfer) {
        log.debug("Validating business rules for transfer: {}", transfer.getId());

        // Validate network availability
        validateNetworkAvailability(transfer.getNetworkType());

        // Validate time restrictions (e.g., no transfers during maintenance windows)
        validateTimeRestrictions();

        // Validate currency support
        validateCurrencySupport(transfer.getCurrency(), transfer.getNetworkType());
    }

    private void validateBasicFields(InstantTransferRequest request) {
        if (request == null) {
            throw new ValidationException("Transfer request is required");
        }

        if (request.getSenderId() == null || request.getSenderId().isEmpty()) {
            throw new ValidationException("Sender ID is required");
        }

        if (request.getRecipientId() == null || request.getRecipientId().isEmpty()) {
            throw new ValidationException("Recipient ID is required");
        }

        if (request.getAmount() == null) {
            throw new ValidationException("Transfer amount is required");
        }

        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new ValidationException("Currency is required");
        }

        if (request.getNetworkType() == null || request.getNetworkType().isEmpty()) {
            throw new ValidationException("Network type is required");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("Amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }

        if (amount.compareTo(minTransferAmount) < 0) {
            throw new ValidationException("Amount is below minimum transfer limit of " + minTransferAmount);
        }

        if (amount.compareTo(maxTransferAmount) > 0) {
            throw new ValidationException("Amount exceeds maximum transfer limit of " + maxTransferAmount);
        }

        // Validate decimal places (most currencies support 2 decimal places)
        if (amount.scale() > 2) {
            throw new ValidationException("Amount cannot have more than 2 decimal places");
        }
    }

    private void validateParticipants(InstantTransferRequest request) {
        if (request.getSenderId().equals(request.getRecipientId())) {
            throw new ValidationException("Cannot transfer to yourself");
        }

        // Validate user IDs format (assuming UUID format)
        if (!isValidUuid(request.getSenderId())) {
            throw new ValidationException("Invalid sender ID format");
        }

        if (!isValidUuid(request.getRecipientId())) {
            throw new ValidationException("Invalid recipient ID format");
        }
    }

    private void validateMemo(String memo) {
        if (memo != null && memo.length() > maxMemoLength) {
            throw new ValidationException("Memo exceeds maximum length of " + maxMemoLength + " characters");
        }

        if (memo != null && containsInappropriateContent(memo)) {
            throw new ValidationException("Memo contains inappropriate content");
        }
    }

    private void validateRecipientIdentifier(String identifier, String identifierType) {
        if (identifier == null || identifier.isEmpty()) {
            throw new ValidationException("Recipient identifier is required");
        }

        switch (identifierType.toUpperCase()) {
            case "EMAIL":
                if (!EMAIL_PATTERN.matcher(identifier).matches()) {
                    throw new ValidationException("Invalid email format");
                }
                break;
            case "PHONE":
                if (!PHONE_PATTERN.matcher(identifier).matches()) {
                    throw new ValidationException("Invalid phone number format");
                }
                break;
            case "USER_ID":
                if (!isValidUuid(identifier)) {
                    throw new ValidationException("Invalid user ID format");
                }
                break;
            default:
                throw new ValidationException("Unsupported recipient identifier type: " + identifierType);
        }
    }

    private void validateCurrency(String currency) {
        // Validate ISO 4217 currency code format
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new ValidationException("Invalid currency format. Must be 3-letter ISO code");
        }

        // List of supported currencies for instant transfers
        String[] supportedCurrencies = {"USD", "EUR", "GBP", "CAD", "AUD"};
        boolean isSupported = false;
        for (String supportedCurrency : supportedCurrencies) {
            if (currency.equals(supportedCurrency)) {
                isSupported = true;
                break;
            }
        }

        if (!isSupported) {
            throw new ValidationException("Currency " + currency + " is not supported for instant transfers");
        }
    }

    private void validateNetworkAvailability(String networkType) {
        // Check if the payment network is available
        switch (networkType.toUpperCase()) {
            case "FEDNOW":
                if (!isFedNowAvailable()) {
                    throw new ValidationException("FedNow network is currently unavailable");
                }
                break;
            case "RTP":
                if (!isRTPAvailable()) {
                    throw new ValidationException("RTP network is currently unavailable");
                }
                break;
            case "ZELLE":
                if (!isZelleAvailable()) {
                    throw new ValidationException("Zelle network is currently unavailable");
                }
                break;
            default:
                throw new ValidationException("Unsupported network type: " + networkType);
        }
    }

    private void validateTimeRestrictions() {
        // Check for maintenance windows or time-based restrictions
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.DayOfWeek dayOfWeek = java.time.LocalDate.now().getDayOfWeek();

        // Example: Block transfers during maintenance window (2 AM - 4 AM on Sundays)
        if (dayOfWeek == java.time.DayOfWeek.SUNDAY &&
            now.isAfter(java.time.LocalTime.of(2, 0)) &&
            now.isBefore(java.time.LocalTime.of(4, 0))) {
            throw new ValidationException("Instant transfers are unavailable during system maintenance");
        }
    }

    private void validateCurrencySupport(String currency, String networkType) {
        // Some networks may not support all currencies
        if ("ZELLE".equals(networkType) && !"USD".equals(currency)) {
            throw new ValidationException("Zelle network only supports USD");
        }

        if ("FEDNOW".equals(networkType) && !"USD".equals(currency)) {
            throw new ValidationException("FedNow network only supports USD");
        }
    }

    private boolean isUserEligibleForInstantTransfers(String userId) {
        try {
            // This would call the user service to check eligibility
            return userServiceClient.isEligibleForInstantTransfers(userId);
        } catch (Exception e) {
            log.warn("Failed to check user eligibility for instant transfers: {}", userId, e);
            // Fail safe - assume not eligible if we can't verify
            return false;
        }
    }

    private boolean isValidUuid(String uuid) {
        try {
            java.util.UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean containsInappropriateContent(String text) {
        // Basic content filtering - could be enhanced with ML-based content moderation
        String[] inappropriateWords = {"fraud", "scam", "illegal", "drugs"};
        String lowerText = text.toLowerCase();
        
        for (String word : inappropriateWords) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        
        return false;
    }

    // Network availability checks - these would typically call external services
    private boolean isFedNowAvailable() {
        // Check FedNow service status
        return true; // Placeholder
    }

    private boolean isRTPAvailable() {
        // Check RTP network status
        return true; // Placeholder
    }

    private boolean isZelleAvailable() {
        // Check Zelle network status
        return true; // Placeholder
    }

    /**
     * Get validation configuration for monitoring
     */
    public ValidationConfiguration getValidationConfiguration() {
        return ValidationConfiguration.builder()
            .minTransferAmount(minTransferAmount)
            .maxTransferAmount(maxTransferAmount)
            .maxMemoLength(maxMemoLength)
            .supportedCurrencies(new String[]{"USD", "EUR", "GBP", "CAD", "AUD"})
            .supportedNetworks(new String[]{"FEDNOW", "RTP", "ZELLE"})
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ValidationConfiguration {
        private BigDecimal minTransferAmount;
        private BigDecimal maxTransferAmount;
        private int maxMemoLength;
        private String[] supportedCurrencies;
        private String[] supportedNetworks;
    }
}