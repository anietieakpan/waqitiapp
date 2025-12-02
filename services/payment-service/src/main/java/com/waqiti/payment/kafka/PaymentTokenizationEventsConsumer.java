package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentTokenizationEvent;
import com.waqiti.common.events.TokenLifecycleEvent;
import com.waqiti.common.events.PCIComplianceEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentToken;
import com.waqiti.payment.domain.TokenStatus;
import com.waqiti.payment.domain.TokenType;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentTokenRepository;
import com.waqiti.payment.service.TokenizationService;
import com.waqiti.payment.service.TokenVaultService;
import com.waqiti.payment.service.PCIComplianceService;
import com.waqiti.payment.service.TokenSecurityService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.TokenizationException;
import com.waqiti.payment.metrics.TokenizationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL Consumer for Payment Tokenization Events
 * 
 * Handles all payment tokenization operations including:
 * - Card tokenization and detokenization
 * - Token vault management and lifecycle
 * - PCI DSS compliance and data protection
 * - Token provisioning and deprovisioning
 * - Network tokenization (Apple Pay, Google Pay)
 * - Token security and fraud prevention
 * - Token expiration and renewal
 * - Cross-platform token synchronization
 * 
 * This is CRITICAL for PCI compliance and security.
 * Proper tokenization reduces PCI scope by 80-90%
 * and protects sensitive payment data.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentTokenizationEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentTokenRepository tokenRepository;
    private final TokenizationService tokenizationService;
    private final TokenVaultService vaultService;
    private final PCIComplianceService pciComplianceService;
    private final TokenSecurityService tokenSecurityService;
    private final TokenizationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final EncryptionService encryptionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Token configuration constants
    private static final Duration TOKEN_DEFAULT_EXPIRY = Duration.ofDays(365); // 1 year
    private static final Duration NETWORK_TOKEN_EXPIRY = Duration.ofDays(1095); // 3 years
    private static final Duration TEMP_TOKEN_EXPIRY = Duration.ofMinutes(15);
    
    // Token security parameters
    private static final int TOKEN_LENGTH = 16;
    private static final String TOKEN_PREFIX = "tok_";
    private static final String NETWORK_TOKEN_PREFIX = "ntok_";
    private static final String TEMP_TOKEN_PREFIX = "tmp_";
    
    // PCI compliance requirements
    private static final int MAX_FAILED_DETOKENIZATION_ATTEMPTS = 3;
    private static final Duration TOKEN_ROTATION_WINDOW = Duration.ofDays(90);
    private static final int MAX_CONCURRENT_TOKENIZATIONS = 100;
    
    /**
     * Primary handler for payment tokenization events
     * Processes all tokenization operations with PCI compliance
     */
    @KafkaListener(
        topics = "payment-tokenization-events",
        groupId = "payment-tokenization-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 24000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentTokenizationEvent(
            @Payload PaymentTokenizationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("token-%s-p%d-o%d", 
            event.getPaymentId() != null ? event.getPaymentId() : event.getTokenId(), 
            partition, offset);
        
        log.info("Processing tokenization event: paymentId={}, tokenId={}, action={}, correlation={}",
            event.getPaymentId(), event.getTokenId(), event.getTokenAction(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(
                event.getPaymentId() != null ? event.getPaymentId() : event.getTokenId(), 
                "TOKEN_MANAGEMENT");
            validateTokenizationEvent(event);
            
            // PCI compliance check
            if (!pciComplianceService.isTokenizationCompliant(event)) {
                log.error("Tokenization event fails PCI compliance: {}", event);
                handlePCIComplianceViolation(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on token action
            switch (event.getTokenAction()) {
                case CREATE_TOKEN:
                    processCreateToken(event, correlationId);
                    break;
                case DETOKENIZE:
                    processDetokenize(event, correlationId);
                    break;
                case UPDATE_TOKEN:
                    processUpdateToken(event, correlationId);
                    break;
                case REVOKE_TOKEN:
                    processRevokeToken(event, correlationId);
                    break;
                case ROTATE_TOKEN:
                    processRotateToken(event, correlationId);
                    break;
                case PROVISION_NETWORK_TOKEN:
                    processProvisionNetworkToken(event, correlationId);
                    break;
                case SYNC_TOKEN:
                    processSyncToken(event, correlationId);
                    break;
                case VALIDATE_TOKEN:
                    processValidateToken(event, correlationId);
                    break;
                case EXPIRE_TOKEN:
                    processExpireToken(event, correlationId);
                    break;
                default:
                    log.warn("Unknown tokenization action: {}", event.getTokenAction());
                    break;
            }
            
            // Audit the tokenization operation
            auditService.logFinancialEvent(
                "TOKENIZATION_EVENT_PROCESSED",
                event.getPaymentId() != null ? event.getPaymentId() : event.getTokenId(),
                Map.of(
                    "tokenAction", event.getTokenAction(),
                    "tokenType", event.getTokenType() != null ? event.getTokenType() : "UNKNOWN",
                    "tokenId", event.getTokenId() != null ? event.getTokenId() : "NEW",
                    "customerId", event.getCustomerId() != null ? event.getCustomerId() : "UNKNOWN",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process tokenization event: paymentId={}, tokenId={}, error={}",
                event.getPaymentId(), event.getTokenId(), e.getMessage(), e);
            
            handleTokenizationEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Processes token creation requests
     */
    private void processCreateToken(PaymentTokenizationEvent event, String correlationId) {
        log.info("Processing token creation: paymentId={}, type={}, customerId={}",
            event.getPaymentId(), event.getTokenType(), event.getCustomerId());
        
        try {
            // Validate request
            if (event.getCardData() == null || event.getCardData().isEmpty()) {
                throw new TokenizationException("Card data is required for tokenization");
            }
            
            // Check rate limiting for tokenization requests
            if (!tokenSecurityService.checkTokenizationRateLimit(event.getCustomerId())) {
                log.warn("Tokenization rate limit exceeded for customer: {}", event.getCustomerId());
                throw new TokenizationException("Rate limit exceeded for tokenization requests");
            }
            
            // Generate token based on type
            PaymentToken token = createPaymentToken(event, correlationId);
            
            // Encrypt and store card data in vault
            String vaultReference = vaultService.storeCardData(
                event.getCardData(),
                token.getId(),
                correlationId
            );
            
            token.setVaultReference(vaultReference);
            token.setCreatedAt(LocalDateTime.now());
            token.setLastUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
            
            // Update payment if applicable
            if (event.getPaymentId() != null) {
                Payment payment = getPaymentById(event.getPaymentId());
                payment.setTokenId(token.getId());
                payment.setTokenized(true);
                paymentRepository.save(payment);
            }
            
            // Publish token lifecycle event
            publishTokenLifecycleEvent(token, "TOKEN_CREATED", correlationId);
            
            // Update metrics
            metricsService.recordTokenCreated(token.getTokenType(), token.getCustomerId());
            
            // Schedule automatic expiration if needed
            if (token.getExpiresAt() != null) {
                scheduleTokenExpiration(token, correlationId);
            }
            
            log.info("Token created successfully: tokenId={}, type={}, expiresAt={}",
                token.getId(), token.getTokenType(), token.getExpiresAt());
            
        } catch (Exception e) {
            log.error("Failed to create token: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            throw new TokenizationException("Token creation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates payment token based on event data
     */
    private PaymentToken createPaymentToken(PaymentTokenizationEvent event, String correlationId) {
        String tokenValue = generateTokenValue(event.getTokenType());
        TokenType tokenType = TokenType.valueOf(event.getTokenType());
        
        return PaymentToken.builder()
            .id(UUID.randomUUID().toString())
            .tokenValue(tokenValue)
            .tokenType(tokenType)
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .paymentId(event.getPaymentId())
            .status(TokenStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(calculateTokenExpiration(tokenType))
            .lastFourDigits(event.getLastFourDigits())
            .cardBrand(event.getCardBrand())
            .expiryMonth(event.getExpiryMonth())
            .expiryYear(event.getExpiryYear())
            .correlationId(correlationId)
            .securityLevel(determineSecurityLevel(event))
            .usageCount(0)
            .maxUsageCount(getMaxUsageCount(tokenType))
            .build();
    }
    
    /**
     * Generates secure token value
     */
    private String generateTokenValue(String tokenType) {
        String prefix = getTokenPrefix(tokenType);
        String randomPart = tokenSecurityService.generateSecureRandomToken(TOKEN_LENGTH - prefix.length());
        return prefix + randomPart;
    }
    
    /**
     * Processes detokenization requests
     */
    private void processDetokenize(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing detokenization: tokenId={}, customerId={}, purpose={}",
            token.getId(), token.getCustomerId(), event.getDetokenizationPurpose());
        
        try {
            // Validate detokenization request
            validateDetokenizationRequest(token, event);
            
            // Check security permissions
            if (!tokenSecurityService.hasDetokenizationPermission(
                event.getRequesterId(), token.getId())) {
                throw new TokenizationException("Insufficient permissions for detokenization");
            }
            
            // Retrieve card data from vault
            Map<String, Object> cardData = vaultService.retrieveCardData(
                token.getVaultReference(),
                event.getDetokenizationPurpose(),
                correlationId
            );
            
            // Update token usage
            token.setUsageCount(token.getUsageCount() + 1);
            token.setLastUsedAt(LocalDateTime.now());
            token.setLastDetokenizedAt(LocalDateTime.now());
            tokenRepository.save(token);
            
            // Publish detokenization result (without sensitive data in event)
            publishDetokenizationResult(token, event, correlationId);
            
            // Update metrics
            metricsService.recordDetokenization(token.getTokenType(), event.getDetokenizationPurpose());
            
            // Check if token should be rotated after usage
            if (shouldRotateAfterUsage(token)) {
                scheduleTokenRotation(token, correlationId);
            }
            
            log.info("Detokenization completed: tokenId={}, usageCount={}",
                token.getId(), token.getUsageCount());
            
        } catch (Exception e) {
            // Record failed detokenization attempt
            recordFailedDetokenization(token, event, e.getMessage());
            
            log.error("Failed to detokenize token: tokenId={}, error={}",
                token.getId(), e.getMessage(), e);
            throw new TokenizationException("Detokenization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes token updates
     */
    private void processUpdateToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing token update: tokenId={}, updateType={}", 
            token.getId(), event.getUpdateType());
        
        boolean updated = false;
        
        // Update expiry information if provided
        if (event.getExpiryMonth() != null && event.getExpiryYear() != null) {
            token.setExpiryMonth(event.getExpiryMonth());
            token.setExpiryYear(event.getExpiryYear());
            updated = true;
        }
        
        // Update status if provided
        if (event.getNewStatus() != null) {
            TokenStatus oldStatus = token.getStatus();
            token.setStatus(TokenStatus.valueOf(event.getNewStatus()));
            
            if (oldStatus != token.getStatus()) {
                publishTokenStatusChange(token, oldStatus, correlationId);
                updated = true;
            }
        }
        
        // Update security level if needed
        if (event.getNewSecurityLevel() != null) {
            token.setSecurityLevel(event.getNewSecurityLevel());
            updated = true;
        }
        
        if (updated) {
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepository.save(token);
            
            publishTokenLifecycleEvent(token, "TOKEN_UPDATED", correlationId);
            metricsService.recordTokenUpdated(token.getTokenType());
            
            log.info("Token updated successfully: tokenId={}", token.getId());
        } else {
            log.debug("No updates required for token: {}", token.getId());
        }
    }
    
    /**
     * Processes token revocation
     */
    private void processRevokeToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing token revocation: tokenId={}, reason={}", 
            token.getId(), event.getRevocationReason());
        
        // Update token status
        token.setStatus(TokenStatus.REVOKED);
        token.setRevokedAt(LocalDateTime.now());
        token.setRevocationReason(event.getRevocationReason());
        token.setRevokedBy(event.getRequesterId());
        tokenRepository.save(token);
        
        // Remove from vault if required
        if (event.isRemoveFromVault()) {
            try {
                vaultService.removeCardData(token.getVaultReference(), correlationId);
                token.setVaultReference(null);
                tokenRepository.save(token);
            } catch (Exception e) {
                log.warn("Failed to remove card data from vault: tokenId={}, error={}",
                    token.getId(), e.getMessage());
            }
        }
        
        // Invalidate any network tokens
        if (token.getNetworkTokenId() != null) {
            invalidateNetworkToken(token.getNetworkTokenId(), correlationId);
        }
        
        // Publish revocation event
        publishTokenLifecycleEvent(token, "TOKEN_REVOKED", correlationId);
        
        // Update metrics
        metricsService.recordTokenRevoked(token.getTokenType(), event.getRevocationReason());
        
        // Send notification if needed
        if (event.isNotifyCustomer()) {
            sendTokenRevocationNotification(token, correlationId);
        }
        
        log.info("Token revoked successfully: tokenId={}, reason={}", 
            token.getId(), event.getRevocationReason());
    }
    
    /**
     * Processes token rotation
     */
    private void processRotateToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken oldToken = getTokenById(event.getTokenId());
        
        log.info("Processing token rotation: oldTokenId={}, reason={}", 
            oldToken.getId(), event.getRotationReason());
        
        try {
            // Create new token with same card data
            PaymentToken newToken = createRotatedToken(oldToken, correlationId);
            tokenRepository.save(newToken);
            
            // Update old token status
            oldToken.setStatus(TokenStatus.ROTATED);
            oldToken.setRotatedAt(LocalDateTime.now());
            oldToken.setReplacementTokenId(newToken.getId());
            tokenRepository.save(oldToken);
            
            // Update payments to use new token
            updatePaymentsWithNewToken(oldToken.getId(), newToken.getId());
            
            // Publish rotation events
            publishTokenLifecycleEvent(oldToken, "TOKEN_ROTATED", correlationId);
            publishTokenLifecycleEvent(newToken, "TOKEN_CREATED", correlationId);
            
            // Update metrics
            metricsService.recordTokenRotated(oldToken.getTokenType());
            
            // Schedule old token cleanup
            scheduleTokenCleanup(oldToken, Duration.ofDays(30), correlationId);
            
            log.info("Token rotation completed: oldToken={}, newToken={}", 
                oldToken.getId(), newToken.getId());
            
        } catch (Exception e) {
            log.error("Failed to rotate token: tokenId={}, error={}",
                oldToken.getId(), e.getMessage(), e);
            throw new TokenizationException("Token rotation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes network token provisioning
     */
    private void processProvisionNetworkToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing network token provisioning: tokenId={}, network={}", 
            token.getId(), event.getNetworkProvider());
        
        try {
            // Request network token from provider
            String networkTokenId = tokenizationService.provisionNetworkToken(
                token.getId(),
                event.getNetworkProvider(),
                event.getDeviceInfo(),
                correlationId
            );
            
            // Update token with network token information
            token.setNetworkTokenId(networkTokenId);
            token.setNetworkProvider(event.getNetworkProvider());
            token.setNetworkTokenStatus("ACTIVE");
            token.setNetworkTokenProvisionedAt(LocalDateTime.now());
            tokenRepository.save(token);
            
            // Publish network token event
            publishNetworkTokenEvent(token, "NETWORK_TOKEN_PROVISIONED", correlationId);
            
            // Update metrics
            metricsService.recordNetworkTokenProvisioned(event.getNetworkProvider());
            
            log.info("Network token provisioned: tokenId={}, networkTokenId={}", 
                token.getId(), networkTokenId);
            
        } catch (Exception e) {
            log.error("Failed to provision network token: tokenId={}, error={}",
                token.getId(), e.getMessage(), e);
            throw new TokenizationException("Network token provisioning failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes token synchronization across platforms
     */
    private void processSyncToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing token sync: tokenId={}, platform={}", 
            token.getId(), event.getSyncPlatform());
        
        try {
            // Sync token status and metadata across platforms
            tokenizationService.syncTokenAcrossPlatforms(
                token.getId(),
                event.getSyncPlatform(),
                event.getSyncData(),
                correlationId
            );
            
            token.setLastSyncAt(LocalDateTime.now());
            token.setSyncPlatforms(addPlatformToSyncList(token.getSyncPlatforms(), event.getSyncPlatform()));
            tokenRepository.save(token);
            
            publishTokenLifecycleEvent(token, "TOKEN_SYNCED", correlationId);
            metricsService.recordTokenSynced(event.getSyncPlatform());
            
            log.info("Token synced successfully: tokenId={}, platform={}", 
                token.getId(), event.getSyncPlatform());
            
        } catch (Exception e) {
            log.error("Failed to sync token: tokenId={}, platform={}, error={}",
                token.getId(), event.getSyncPlatform(), e.getMessage(), e);
        }
    }
    
    /**
     * Processes token validation requests
     */
    private void processValidateToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing token validation: tokenId={}", token.getId());
        
        boolean isValid = validateTokenStatus(token) && 
                         validateTokenExpiry(token) && 
                         validateTokenUsage(token);
        
        // Update token validation timestamp
        token.setLastValidatedAt(LocalDateTime.now());
        if (!isValid) {
            token.setValidationFailureReason(getValidationFailureReason(token));
        }
        tokenRepository.save(token);
        
        // Publish validation result
        publishTokenValidationResult(token, isValid, correlationId);
        
        // Update metrics
        metricsService.recordTokenValidation(token.getTokenType(), isValid);
        
        log.info("Token validation completed: tokenId={}, valid={}", token.getId(), isValid);
    }
    
    /**
     * Processes token expiration
     */
    private void processExpireToken(PaymentTokenizationEvent event, String correlationId) {
        PaymentToken token = getTokenById(event.getTokenId());
        
        log.info("Processing token expiration: tokenId={}", token.getId());
        
        // Update token status
        token.setStatus(TokenStatus.EXPIRED);
        token.setExpiredAt(LocalDateTime.now());
        tokenRepository.save(token);
        
        // Publish expiration event
        publishTokenLifecycleEvent(token, "TOKEN_EXPIRED", correlationId);
        
        // Update metrics
        metricsService.recordTokenExpired(token.getTokenType());
        
        // Schedule cleanup if needed
        if (event.isCleanupAfterExpiration()) {
            scheduleTokenCleanup(token, Duration.ofDays(7), correlationId);
        }
        
        log.info("Token expired: tokenId={}", token.getId());
    }
    
    /**
     * Scheduled task to check for expiring tokens
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void checkExpiringTokens() {
        log.debug("Checking for expiring tokens...");
        
        LocalDateTime expirationThreshold = LocalDateTime.now().plusDays(7); // 7 days ahead
        List<PaymentToken> expiringTokens = tokenRepository.findTokensExpiringBefore(expirationThreshold);
        
        for (PaymentToken token : expiringTokens) {
            try {
                if (shouldAutoRenewToken(token)) {
                    // Auto-renew token
                    PaymentTokenizationEvent renewalEvent = PaymentTokenizationEvent.builder()
                        .tokenId(token.getId())
                        .tokenAction("ROTATE_TOKEN")
                        .rotationReason("AUTO_RENEWAL_BEFORE_EXPIRY")
                        .timestamp(Instant.now())
                        .build();
                    
                    kafkaTemplate.send("payment-tokenization-events", renewalEvent);
                    
                } else {
                    // Notify customer of upcoming expiration
                    sendTokenExpirationNotification(token);
                }
                
            } catch (Exception e) {
                log.error("Failed to process expiring token: tokenId={}, error={}",
                    token.getId(), e.getMessage(), e);
            }
        }
        
        if (!expiringTokens.isEmpty()) {
            log.info("Processed {} expiring tokens", expiringTokens.size());
        }
    }
    
    /**
     * Utility methods for tokenization processing
     */
    private LocalDateTime calculateTokenExpiration(TokenType tokenType) {
        switch (tokenType) {
            case NETWORK_TOKEN:
                return LocalDateTime.now().plus(NETWORK_TOKEN_EXPIRY);
            case TEMPORARY:
                return LocalDateTime.now().plus(TEMP_TOKEN_EXPIRY);
            case SINGLE_USE:
                return LocalDateTime.now().plusDays(1); // Single use tokens expire in 1 day
            default:
                return LocalDateTime.now().plus(TOKEN_DEFAULT_EXPIRY);
        }
    }
    
    private String getTokenPrefix(String tokenType) {
        switch (TokenType.valueOf(tokenType)) {
            case NETWORK_TOKEN:
                return NETWORK_TOKEN_PREFIX;
            case TEMPORARY:
                return TEMP_TOKEN_PREFIX;
            default:
                return TOKEN_PREFIX;
        }
    }
    
    private String determineSecurityLevel(PaymentTokenizationEvent event) {
        if (event.getSecurityLevel() != null) {
            return event.getSecurityLevel();
        }
        
        // Determine based on token type and context
        if ("NETWORK_TOKEN".equals(event.getTokenType())) {
            return "HIGH";
        } else if ("TEMPORARY".equals(event.getTokenType())) {
            return "MEDIUM";
        } else {
            return "STANDARD";
        }
    }
    
    private int getMaxUsageCount(TokenType tokenType) {
        switch (tokenType) {
            case SINGLE_USE:
                return 1;
            case TEMPORARY:
                return 10;
            default:
                return -1; // Unlimited
        }
    }
    
    private PaymentToken createRotatedToken(PaymentToken oldToken, String correlationId) {
        return PaymentToken.builder()
            .id(UUID.randomUUID().toString())
            .tokenValue(generateTokenValue(oldToken.getTokenType().toString()))
            .tokenType(oldToken.getTokenType())
            .customerId(oldToken.getCustomerId())
            .merchantId(oldToken.getMerchantId())
            .status(TokenStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(calculateTokenExpiration(oldToken.getTokenType()))
            .lastFourDigits(oldToken.getLastFourDigits())
            .cardBrand(oldToken.getCardBrand())
            .expiryMonth(oldToken.getExpiryMonth())
            .expiryYear(oldToken.getExpiryYear())
            .vaultReference(oldToken.getVaultReference()) // Reuse vault reference
            .correlationId(correlationId)
            .securityLevel(oldToken.getSecurityLevel())
            .usageCount(0)
            .maxUsageCount(oldToken.getMaxUsageCount())
            .predecessorTokenId(oldToken.getId())
            .build();
    }
    
    /**
     * Validation and utility methods
     */
    private void validateTokenizationEvent(PaymentTokenizationEvent event) {
        if (event.getTokenAction() == null || event.getTokenAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Token action is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
        
        // Additional validation based on action
        switch (event.getTokenAction()) {
            case "CREATE_TOKEN":
                if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Customer ID is required for token creation");
                }
                break;
            case "DETOKENIZE":
            case "UPDATE_TOKEN":
            case "REVOKE_TOKEN":
            case "ROTATE_TOKEN":
                if (event.getTokenId() == null || event.getTokenId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Token ID is required for this action");
                }
                break;
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found: " + paymentId));
    }
    
    private PaymentToken getTokenById(String tokenId) {
        return tokenRepository.findById(tokenId)
            .orElseThrow(() -> new TokenizationException(
                "Token not found: " + tokenId));
    }
    
    private void validateDetokenizationRequest(PaymentToken token, PaymentTokenizationEvent event) {
        if (token.getStatus() != TokenStatus.ACTIVE) {
            throw new TokenizationException("Token is not active: " + token.getStatus());
        }
        
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenizationException("Token has expired");
        }
        
        if (token.getMaxUsageCount() > 0 && token.getUsageCount() >= token.getMaxUsageCount()) {
            throw new TokenizationException("Token usage limit exceeded");
        }
    }
    
    private boolean shouldRotateAfterUsage(PaymentToken token) {
        // Rotate single-use tokens after first use
        if (token.getTokenType() == TokenType.SINGLE_USE && token.getUsageCount() >= 1) {
            return true;
        }
        
        // Rotate if last rotation was more than 90 days ago
        if (token.getLastRotatedAt() != null && 
            token.getLastRotatedAt().isBefore(LocalDateTime.now().minus(TOKEN_ROTATION_WINDOW))) {
            return true;
        }
        
        return false;
    }
    
    private boolean shouldAutoRenewToken(PaymentToken token) {
        // Auto-renew for active recurring payment tokens
        return token.getStatus() == TokenStatus.ACTIVE &&
               tokenRepository.hasActiveRecurringPayments(token.getId()) &&
               token.getCustomerId() != null;
    }
    
    private boolean validateTokenStatus(PaymentToken token) {
        return token.getStatus() == TokenStatus.ACTIVE;
    }
    
    private boolean validateTokenExpiry(PaymentToken token) {
        return token.getExpiresAt() == null || token.getExpiresAt().isAfter(LocalDateTime.now());
    }
    
    private boolean validateTokenUsage(PaymentToken token) {
        return token.getMaxUsageCount() <= 0 || token.getUsageCount() < token.getMaxUsageCount();
    }
    
    private String getValidationFailureReason(PaymentToken token) {
        if (!validateTokenStatus(token)) {
            return "Token status is " + token.getStatus();
        }
        if (!validateTokenExpiry(token)) {
            return "Token has expired";
        }
        if (!validateTokenUsage(token)) {
            return "Token usage limit exceeded";
        }
        return "Unknown validation failure";
    }
    
    private String addPlatformToSyncList(String existingPlatforms, String newPlatform) {
        if (existingPlatforms == null || existingPlatforms.trim().isEmpty()) {
            return newPlatform;
        }
        
        Set<String> platforms = new HashSet<>(Arrays.asList(existingPlatforms.split(",")));
        platforms.add(newPlatform);
        return String.join(",", platforms);
    }
    
    /**
     * Event publishing methods
     */
    private void publishTokenLifecycleEvent(PaymentToken token, String eventType, String correlationId) {
        TokenLifecycleEvent lifecycleEvent = TokenLifecycleEvent.builder()
            .tokenId(token.getId())
            .eventType(eventType)
            .tokenType(token.getTokenType().toString())
            .customerId(token.getCustomerId())
            .status(token.getStatus().toString())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("token-lifecycle-events", lifecycleEvent);
    }
    
    private void publishTokenStatusChange(PaymentToken token, TokenStatus oldStatus, String correlationId) {
        Map<String, Object> statusChangeEvent = Map.of(
            "tokenId", token.getId(),
            "oldStatus", oldStatus.toString(),
            "newStatus", token.getStatus().toString(),
            "customerId", token.getCustomerId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("token-status-change-events", statusChangeEvent);
    }
    
    private void publishDetokenizationResult(PaymentToken token, PaymentTokenizationEvent event, 
            String correlationId) {
        
        Map<String, Object> resultEvent = Map.of(
            "tokenId", token.getId(),
            "success", true,
            "purpose", event.getDetokenizationPurpose(),
            "requesterId", event.getRequesterId(),
            "usageCount", token.getUsageCount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("detokenization-result-events", resultEvent);
    }
    
    private void publishNetworkTokenEvent(PaymentToken token, String eventType, String correlationId) {
        Map<String, Object> networkTokenEvent = Map.of(
            "tokenId", token.getId(),
            "networkTokenId", token.getNetworkTokenId(),
            "networkProvider", token.getNetworkProvider(),
            "eventType", eventType,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("network-token-events", networkTokenEvent);
    }
    
    private void publishTokenValidationResult(PaymentToken token, boolean isValid, String correlationId) {
        Map<String, Object> validationEvent = Map.of(
            "tokenId", token.getId(),
            "valid", isValid,
            "failureReason", isValid ? "" : token.getValidationFailureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("token-validation-events", validationEvent);
    }
    
    /**
     * Additional utility methods
     */
    private void recordFailedDetokenization(PaymentToken token, PaymentTokenizationEvent event, 
            String errorMessage) {
        
        token.setFailedDetokenizationAttempts(
            token.getFailedDetokenizationAttempts() != null ? 
            token.getFailedDetokenizationAttempts() + 1 : 1);
        
        if (token.getFailedDetokenizationAttempts() >= MAX_FAILED_DETOKENIZATION_ATTEMPTS) {
            token.setStatus(TokenStatus.SUSPENDED);
            token.setSuspendedAt(LocalDateTime.now());
            token.setSuspensionReason("Too many failed detokenization attempts");
        }
        
        tokenRepository.save(token);
        metricsService.recordDetokenizationFailure(token.getTokenType(), errorMessage);
    }
    
    private void scheduleTokenExpiration(PaymentToken token, String correlationId) {
        // Implementation would schedule expiration check
        log.debug("Scheduled token expiration: tokenId={}, expiresAt={}", 
            token.getId(), token.getExpiresAt());
    }
    
    private void scheduleTokenRotation(PaymentToken token, String correlationId) {
        CompletableFuture.runAsync(() -> {
            try {
                PaymentTokenizationEvent rotationEvent = PaymentTokenizationEvent.builder()
                    .tokenId(token.getId())
                    .tokenAction("ROTATE_TOKEN")
                    .rotationReason("SCHEDULED_ROTATION")
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-tokenization-events", rotationEvent);
                
            } catch (Exception e) {
                log.error("Failed to schedule token rotation: tokenId={}, error={}",
                    token.getId(), e.getMessage());
            }
        });
    }
    
    private void scheduleTokenCleanup(PaymentToken token, Duration delay, String correlationId) {
        // Implementation would schedule cleanup task
        log.debug("Scheduled token cleanup: tokenId={}, delay={}", token.getId(), delay);
    }
    
    private void updatePaymentsWithNewToken(String oldTokenId, String newTokenId) {
        List<Payment> payments = paymentRepository.findByTokenId(oldTokenId);
        for (Payment payment : payments) {
            payment.setTokenId(newTokenId);
        }
        paymentRepository.saveAll(payments);
    }
    
    private void invalidateNetworkToken(String networkTokenId, String correlationId) {
        try {
            tokenizationService.invalidateNetworkToken(networkTokenId, correlationId);
        } catch (Exception e) {
            log.error("Failed to invalidate network token: networkTokenId={}, error={}",
                networkTokenId, e.getMessage());
        }
    }
    
    private void sendTokenRevocationNotification(PaymentToken token, String correlationId) {
        if (token.getCustomerId() != null) {
            notificationService.sendCustomerNotification(
                token.getCustomerId(),
                "Payment Method Removed",
                "A saved payment method has been removed from your account for security reasons.",
                NotificationService.Priority.MEDIUM
            );
        }
    }
    
    private void sendTokenExpirationNotification(PaymentToken token) {
        if (token.getCustomerId() != null) {
            notificationService.sendCustomerNotification(
                token.getCustomerId(),
                "Payment Method Expiring Soon",
                String.format("Your saved payment method ending in %s will expire soon. " +
                    "Please update your payment information.", token.getLastFourDigits()),
                NotificationService.Priority.MEDIUM
            );
        }
    }
    
    private void handlePCIComplianceViolation(PaymentTokenizationEvent event, String correlationId) {
        PCIComplianceEvent complianceEvent = PCIComplianceEvent.builder()
            .eventType("PCI_VIOLATION")
            .violationType("TOKENIZATION_NON_COMPLIANT")
            .description("Tokenization event failed PCI compliance check")
            .severity("HIGH")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("pci-compliance-events", complianceEvent);
        
        notificationService.sendComplianceAlert(
            "PCI Compliance Violation",
            "Tokenization event failed PCI compliance validation",
            NotificationService.Priority.CRITICAL
        );
    }
    
    private void handleTokenizationEventError(PaymentTokenizationEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-tokenization-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Tokenization Event Processing Failed",
            String.format("Failed to process tokenization event: %s",
                error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementTokenizationEventError(event.getTokenAction());
    }
}