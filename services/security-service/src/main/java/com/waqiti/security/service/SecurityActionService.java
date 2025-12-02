package com.waqiti.security.service;

import com.waqiti.common.events.FraudAlertEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Security Action Service Interface
 * 
 * Handles immediate security actions in response to fraud alerts and security events.
 * This service is critical for preventing financial losses from fraud.
 */
public interface SecurityActionService {
    
    /**
     * Immediately freeze an account due to critical fraud
     * 
     * @param userId User ID to freeze
     * @param reason Reason for freezing
     * @param fraudScore Fraud score that triggered the action
     * @return Action ID for tracking
     */
    String freezeAccountImmediately(UUID userId, String reason, Double fraudScore);
    
    /**
     * Block a specific transaction
     * 
     * @param transactionId Transaction to block
     * @param blockReason Reason for blocking
     * @param fraudIndicators List of fraud indicators
     * @return Block action ID
     */
    String blockTransaction(UUID transactionId, String blockReason, List<String> fraudIndicators);
    
    /**
     * Enable enhanced monitoring for an account
     * 
     * @param userId User ID to monitor
     * @param reason Reason for enhanced monitoring
     * @param monitoringUntil When to stop enhanced monitoring
     * @return Monitoring action ID
     */
    String enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil);
    
    /**
     * Apply temporary transaction limits
     * 
     * @param userId User ID to limit
     * @param limits Recommended limits
     * @return Limit action ID
     */
    String applyTemporaryTransactionLimits(UUID userId, FraudAlertEvent.TransactionLimits limits);
    
    /**
     * Require additional verification for future transactions
     * 
     * @param userId User ID requiring verification
     * @param reason Reason for additional verification
     * @param requiredUntil When to stop requiring additional verification
     * @return Verification requirement ID
     */
    String requireAdditionalVerification(UUID userId, String reason, LocalDateTime requiredUntil);
    
    /**
     * Unfreeze an account (for resolution/false positive)
     * 
     * @param userId User ID to unfreeze
     * @param reason Reason for unfreezing
     * @param resolvedBy Who resolved the fraud alert
     * @return Unfreeze action ID
     */
    String unfreezeAccount(UUID userId, String reason, String resolvedBy);
    
    /**
     * Block transaction with ML-specific reason and metadata
     * 
     * @param transactionId Transaction to block
     * @param reason ML-generated reason
     * @param modelName ML model name
     * @param fraudScore ML fraud score
     * @param confidence ML confidence level
     * @return Block action ID
     */
    String blockTransactionWithMLReason(String transactionId, String reason, String modelName, 
                                       Double fraudScore, Double confidence);
    
    /**
     * Freeze account based on ML fraud detection
     * 
     * @param userId User ID to freeze
     * @param reason ML-generated reason
     * @param fraudScore ML fraud score
     * @return Freeze action ID
     */
    String freezeAccountFromML(String userId, String reason, Double fraudScore);
    
    /**
     * Request additional authentication based on ML analysis
     *
     * @param userId User ID requiring auth
     * @param transactionId Related transaction
     * @param reason ML-generated reason
     * @return Auth request ID
     */
    String requestAdditionalAuthFromML(String userId, String transactionId, String reason);

    // Methods for AuthenticationEventConsumer

    void flagInvalidAuthMethod(String userId, String authMethod);

    void handleMFAFailure(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void handleBiometricFailure(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void escalateBehavioralAnomaly(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void lockUserAccount(String userId, String reason);

    void flagPotentialAccountTakeover(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    com.waqiti.security.entity.SecurityEvent createSecurityEvent(com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void updateSecurityMetrics(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void generateHighRiskAlert(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void notifySecurityTeam(String userId, com.waqiti.security.entity.AuthenticationAttempt authAttempt);

    void handleProcessingError(com.waqiti.security.entity.AuthenticationAttempt authAttempt, Exception e);
}