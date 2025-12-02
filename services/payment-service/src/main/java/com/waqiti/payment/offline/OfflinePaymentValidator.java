package com.waqiti.payment.offline;

import com.waqiti.payment.offline.dto.OfflinePaymentRequest;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Validator for offline payment requests
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfflinePaymentValidator {
    
    private final UserServiceClient userServiceClient;
    private final UnifiedWalletServiceClient walletServiceClient;
    
    private static final long MAX_TIME_DRIFT_MINUTES = 10;
    
    public void validateOfflinePayment(OfflinePaymentRequest request, String senderId) {
        // Validate sender is not same as recipient
        if (senderId.equals(request.getRecipientId())) {
            throw new IllegalArgumentException("Cannot send payment to yourself");
        }
        
        // Validate client timestamp is reasonable (prevent replay attacks)
        validateClientTimestamp(request.getClientTimestamp());
        
        // Validate offline PIN if provided
        if (request.getOfflinePin() != null && !isValidOfflinePin(request.getOfflinePin())) {
            throw new IllegalArgumentException("Invalid offline PIN format");
        }
        
        // Note: We cannot validate user existence or wallet balance offline
        // These will be validated during sync
        log.debug("Offline payment validation passed for sender: {}", senderId);
    }
    
    private void validateClientTimestamp(LocalDateTime clientTimestamp) {
        LocalDateTime now = LocalDateTime.now();
        long minutesDifference = Math.abs(ChronoUnit.MINUTES.between(clientTimestamp, now));
        
        if (minutesDifference > MAX_TIME_DRIFT_MINUTES) {
            throw new IllegalArgumentException("Client timestamp is too far from server time");
        }
    }
    
    private boolean isValidOfflinePin(String pin) {
        // Validate PIN format (e.g., 4-6 digits)
        return pin != null && pin.matches("\\d{4,6}");
    }
    
    /**
     * Validate during sync (when online)
     */
    public void validateForSync(String senderId, String recipientId) {
        // Validate users exist
        try {
            userServiceClient.getUserById(senderId);
            userServiceClient.getUserById(recipientId);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid sender or recipient", e);
        }
        
        // Note: Wallet balance will be checked during actual payment processing
    }
}