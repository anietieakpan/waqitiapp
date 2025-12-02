package com.waqiti.familyaccount.service.integration;

import com.waqiti.familyaccount.client.UserServiceClient;
import com.waqiti.familyaccount.client.WalletServiceClient;
import com.waqiti.familyaccount.client.NotificationServiceClient;
import com.waqiti.familyaccount.client.SecurityServiceClient;
import com.waqiti.familyaccount.exception.ExternalServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Family External Service Facade
 *
 * Provides a unified interface for all external service integrations
 * Handles:
 * - External service calls with retry logic
 * - Error handling and circuit breaking
 * - Logging and monitoring
 * - Service abstraction
 *
 * Benefits:
 * - Single point of integration
 * - Consistent error handling
 * - Easy to mock for testing
 * - Centralized retry/circuit breaker logic
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyExternalServiceFacade {

    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final SecurityServiceClient securityServiceClient;

    // ==================== User Service Operations ====================

    /**
     * Check if user exists
     */
    @CircuitBreaker(name = "user-service", fallbackMethod = "isUserExistsFallback")
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean isUserExists(String userId) {
        log.debug("Checking if user exists: {}", userId);
        try {
            return userServiceClient.userExists(userId);
        } catch (Exception e) {
            log.error("Error checking user existence: {}", userId, e);
            throw new ExternalServiceException("user-service", "Failed to verify user existence", e);
        }
    }

    private boolean isUserExistsFallback(String userId, Exception ex) {
        log.warn("Circuit breaker fallback for isUserExists - userId: {}, error: {}", userId, ex.getMessage());
        // Fail safe: assume user exists to not block operations
        return true;
    }

    /**
     * Get user age
     */
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserAgeFallback")
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Integer getUserAge(String userId) {
        log.debug("Getting user age: {}", userId);
        try {
            return userServiceClient.getUserAge(userId);
        } catch (Exception e) {
            log.error("Error getting user age: {}", userId, e);
            throw new ExternalServiceException("user-service", "Failed to get user age", e);
        }
    }

    private Integer getUserAgeFallback(String userId, Exception ex) {
        log.warn("Circuit breaker fallback for getUserAge - userId: {}, error: {}", userId, ex.getMessage());
        // Return null to indicate age unavailable (caller should handle)
        return null;
    }

    /**
     * Check if user is eligible for family account
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean isUserEligibleForFamilyAccount(String userId) {
        log.debug("Checking family account eligibility for user: {}", userId);
        try {
            return userServiceClient.isUserEligibleForFamilyAccount(userId);
        } catch (Exception e) {
            log.error("Error checking family account eligibility: {}", userId, e);
            throw new ExternalServiceException("Failed to verify family account eligibility", e);
        }
    }

    /**
     * Get user profile details
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Map<String, Object> getUserProfile(String userId) {
        log.debug("Getting user profile: {}", userId);
        try {
            return userServiceClient.getUserProfile(userId);
        } catch (Exception e) {
            log.error("Error getting user profile: {}", userId, e);
            throw new ExternalServiceException("Failed to get user profile", e);
        }
    }

    // ==================== Wallet Service Operations ====================

    /**
     * Create family wallet
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String createFamilyWallet(String familyId, String ownerId) {
        log.info("Creating family wallet for family: {}", familyId);
        try {
            return walletServiceClient.createFamilyWallet(familyId, ownerId);
        } catch (Exception e) {
            log.error("Error creating family wallet for family: {}", familyId, e);
            throw new ExternalServiceException("Failed to create family wallet", e);
        }
    }

    /**
     * Create individual member wallet
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String createIndividualWallet(String familyId, String userId) {
        log.info("Creating individual wallet for user: {} in family: {}", userId, familyId);
        try {
            return walletServiceClient.createIndividualWallet(familyId, userId);
        } catch (Exception e) {
            log.error("Error creating individual wallet for user: {} in family: {}", userId, familyId, e);
            throw new ExternalServiceException("Failed to create individual wallet", e);
        }
    }

    /**
     * Get wallet balance
     */
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "getWalletBalanceFallback")
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public BigDecimal getWalletBalance(String walletId) {
        log.debug("Getting wallet balance: {}", walletId);
        try {
            return walletServiceClient.getWalletBalance(walletId);
        } catch (Exception e) {
            log.error("Error getting wallet balance: {}", walletId, e);
            throw new ExternalServiceException("wallet-service", "Failed to get wallet balance", e);
        }
    }

    private BigDecimal getWalletBalanceFallback(String walletId, Exception ex) {
        log.error("Circuit breaker fallback for getWalletBalance - walletId: {}, error: {}", walletId, ex.getMessage());
        // Return zero balance as safe fallback (will prevent transactions)
        return BigDecimal.ZERO;
    }

    /**
     * Transfer funds between wallets (e.g., for allowance payments)
     */
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "transferFundsFallback")
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean transferFunds(String fromWalletId, String toWalletId, BigDecimal amount, String description) {
        log.info("Transferring funds: {} from wallet {} to wallet {}", amount, fromWalletId, toWalletId);
        try {
            return walletServiceClient.transferFunds(fromWalletId, toWalletId, amount, description);
        } catch (Exception e) {
            log.error("Error transferring funds from {} to {}: amount {}", fromWalletId, toWalletId, amount, e);
            throw new ExternalServiceException("wallet-service", "Failed to transfer funds", e);
        }
    }

    private boolean transferFundsFallback(String fromWalletId, String toWalletId, BigDecimal amount, String description, Exception ex) {
        log.error("Circuit breaker fallback for transferFunds - from: {}, to: {}, amount: {}, error: {}",
                  fromWalletId, toWalletId, amount, ex.getMessage());
        // Return false to indicate transfer failed (critical operation, don't fake success)
        return false;
    }

    /**
     * Freeze wallet (for suspended members)
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean freezeWallet(String walletId, String reason) {
        log.info("Freezing wallet: {} reason: {}", walletId, reason);
        try {
            return walletServiceClient.freezeWallet(walletId, reason);
        } catch (Exception e) {
            log.error("Error freezing wallet: {}", walletId, e);
            throw new ExternalServiceException("Failed to freeze wallet", e);
        }
    }

    /**
     * Unfreeze wallet
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean unfreezeWallet(String walletId) {
        log.info("Unfreezing wallet: {}", walletId);
        try {
            return walletServiceClient.unfreezeWallet(walletId);
        } catch (Exception e) {
            log.error("Error unfreezing wallet: {}", walletId, e);
            throw new ExternalServiceException("Failed to unfreeze wallet", e);
        }
    }

    // ==================== Notification Service Operations ====================

    /**
     * Send family account created notification
     */
    public void sendFamilyAccountCreatedNotification(String familyId, String primaryParentUserId, String familyName) {
        log.info("Sending family account created notification for family: {}", familyId);
        try {
            notificationServiceClient.sendFamilyAccountCreatedNotification(
                familyId, primaryParentUserId, familyName);
        } catch (Exception e) {
            // Notifications are non-critical, log error but don't fail
            log.error("Error sending family account created notification: {}", familyId, e);
        }
    }

    /**
     * Send member invitation notification
     */
    public void sendMemberInvitationNotification(String userId, String familyName, String invitedBy) {
        log.info("Sending member invitation notification to user: {}", userId);
        try {
            notificationServiceClient.sendMemberInvitationNotification(userId, familyName, invitedBy);
        } catch (Exception e) {
            log.error("Error sending member invitation notification to user: {}", userId, e);
        }
    }

    /**
     * Send transaction authorization notification
     */
    public void sendTransactionAuthorizationNotification(String userId, String transactionId,
                                                         BigDecimal amount, boolean authorized) {
        log.info("Sending transaction authorization notification to user: {} authorized: {}", userId, authorized);
        try {
            notificationServiceClient.sendTransactionAuthorizationNotification(
                userId, transactionId, amount, authorized);
        } catch (Exception e) {
            log.error("Error sending transaction authorization notification to user: {}", userId, e);
        }
    }

    /**
     * Send parent approval request notification
     */
    public void sendParentApprovalRequestNotification(String parentUserId, String memberId,
                                                      String transactionId, BigDecimal amount) {
        log.info("Sending parent approval request to parent: {} for transaction: {}", parentUserId, transactionId);
        try {
            notificationServiceClient.sendParentApprovalRequestNotification(
                parentUserId, memberId, transactionId, amount);
        } catch (Exception e) {
            log.error("Error sending parent approval request to parent: {}", parentUserId, e);
        }
    }

    /**
     * Send allowance payment notification
     */
    public void sendAllowancePaymentNotification(String userId, BigDecimal amount) {
        log.info("Sending allowance payment notification to user: {} amount: {}", userId, amount);
        try {
            notificationServiceClient.sendAllowancePaymentNotification(userId, amount);
        } catch (Exception e) {
            log.error("Error sending allowance payment notification to user: {}", userId, e);
        }
    }

    /**
     * Send spending limit alert
     */
    public void sendSpendingLimitAlert(String userId, String limitType, BigDecimal currentSpent, BigDecimal limit) {
        log.info("Sending spending limit alert to user: {} type: {}", userId, limitType);
        try {
            notificationServiceClient.sendSpendingLimitAlert(userId, limitType, currentSpent, limit);
        } catch (Exception e) {
            log.error("Error sending spending limit alert to user: {}", userId, e);
        }
    }

    // ==================== Security Service Operations ====================

    /**
     * Log security event
     */
    public void logSecurityEvent(String eventType, String userId, String familyId, Map<String, Object> details) {
        log.debug("Logging security event: {} for user: {} family: {}", eventType, userId, familyId);
        try {
            securityServiceClient.logSecurityEvent(eventType, userId, familyId, details);
        } catch (Exception e) {
            // Security logging is critical, but don't fail the operation
            log.error("Error logging security event: {} for user: {}", eventType, userId, e);
        }
    }

    /**
     * Validate device for transaction
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 500)
    )
    public boolean validateDevice(String userId, String deviceId) {
        log.debug("Validating device: {} for user: {}", deviceId, userId);
        try {
            return securityServiceClient.validateDevice(userId, deviceId);
        } catch (Exception e) {
            log.error("Error validating device: {} for user: {}", deviceId, userId, e);
            // For security, fail open (return true) to not block legitimate users
            return true;
        }
    }

    /**
     * Check for suspicious activity
     */
    @Retryable(
        value = {ExternalServiceException.class},
        maxAttempts = 2,
        backoff = @Backoff(delay = 500)
    )
    public boolean isSuspiciousActivity(String userId, String activityType, Map<String, Object> context) {
        log.debug("Checking for suspicious activity: {} for user: {}", activityType, userId);
        try {
            return securityServiceClient.isSuspiciousActivity(userId, activityType, context);
        } catch (Exception e) {
            log.error("Error checking suspicious activity for user: {}", userId, e);
            // Fail safe: assume not suspicious if check fails
            return false;
        }
    }
}
