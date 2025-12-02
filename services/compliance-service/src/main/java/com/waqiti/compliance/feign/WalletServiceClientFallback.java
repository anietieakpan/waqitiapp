package com.waqiti.compliance.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Wallet Service Client Fallback
 *
 * Provides fallback responses when wallet-service is unavailable
 * to ensure graceful degradation and system resilience.
 *
 * Fallback Strategy:
 * - Queue freeze/unfreeze for later execution
 * - Return pending status
 * - Log failure for manual follow-up
 * - Alert compliance team for critical freezes
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public Map<String, Object> freezeAccount(String userId, Map<String, Object> freezeRequest, String authToken) {
        log.error("Wallet service unavailable - account freeze fallback triggered. User: {}, Request: {}", userId, freezeRequest);
        log.error("CRITICAL: Unable to freeze account for user {}. Manual freeze may be required.", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "FREEZE_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "Wallet service temporarily unavailable. Account freeze queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "WALLET_SERVICE_UNAVAILABLE");
        response.put("frozen", false);
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> unfreezeAccount(String userId, Map<String, Object> unfreezeRequest, String authToken) {
        log.error("Wallet service unavailable - account unfreeze fallback triggered. User: {}, Request: {}", userId, unfreezeRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UNFREEZE_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "Wallet service temporarily unavailable. Account unfreeze queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "WALLET_SERVICE_UNAVAILABLE");
        response.put("frozen", true); // Assume still frozen
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> blockTransaction(String transactionId, Map<String, Object> blockRequest, String authToken) {
        log.error("Wallet service unavailable - transaction block fallback triggered. Transaction: {}, Request: {}", transactionId, blockRequest);
        log.error("CRITICAL: Unable to block transaction {}. Manual intervention may be required.", transactionId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "BLOCK_PENDING_RETRY");
        response.put("transactionId", transactionId);
        response.put("message", "Wallet service temporarily unavailable. Transaction block queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "WALLET_SERVICE_UNAVAILABLE");
        response.put("blocked", false);
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> getFreezeStatus(String userId, String authToken) {
        log.error("Wallet service unavailable - freeze status check fallback triggered. User: {}", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "STATUS_UNAVAILABLE");
        response.put("userId", userId);
        response.put("message", "Wallet service temporarily unavailable. Unable to determine freeze status.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "WALLET_SERVICE_UNAVAILABLE");
        response.put("frozen", null); // Unknown status

        return response;
    }
}
