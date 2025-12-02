package com.waqiti.payment.service;

import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.dto.TransferRequest;
import com.waqiti.payment.client.dto.TransferResponse;
import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.PaymentLinkRepository;
import com.waqiti.payment.repository.PaymentLinkTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing payment links - shareable URLs for collecting payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLinkService {
    
    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentLinkTransactionRepository transactionRepository;
    private final UnifiedWalletServiceClient walletClient;
    private final UserServiceClient userClient;
    private final EventPublisher eventPublisher;
    private final KYCClientService kycClientService;
    private final MeterRegistry meterRegistry;
    
    private static final String PAYMENT_LINK_EVENTS_TOPIC = "payment-link-events";
    private static final int LINK_ID_LENGTH = 8;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("50000");
    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    
    private final SecureRandom random = new SecureRandom();
    
    /**
     * Create a new payment link
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "CREATE_PAYMENT_LINK")
    public PaymentLinkResponse createPaymentLink(UUID creatorId, CreatePaymentLinkRequest request) {
        Timer.Sample timer = Timer.start(meterRegistry);
        log.info("Creating payment link for user {}: {}", creatorId, request.getTitle());
        
        try {
            // Validate creator exists
            UserResponse creator = validateUserExists(creatorId);
            
            // Enhanced KYC check for high-value payment links
            if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                if (!kycClientService.canUserMakeHighValueTransfer(creatorId.toString())) {
                    throw new BusinessException("Enhanced KYC verification required for high-value payment links");
                }
            }
            
            // Validate request data
            validateCreateRequest(request);
            
            // Generate unique link ID
            String linkId = request.getCustomLinkId() != null ? 
                           request.getCustomLinkId() : generateUniqueLinkId();
            
            // Ensure link ID is unique
            if (paymentLinkRepository.existsByLinkId(linkId)) {
                if (request.getCustomLinkId() != null) {
                    throw new BusinessException("Custom link ID already exists: " + linkId);
                }
                linkId = generateUniqueLinkId(); // Try again with generated ID
            }
            
            // Create payment link entity
            PaymentLink paymentLink = PaymentLink.builder()
                    .linkId(linkId)
                    .creatorId(creatorId)
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .minAmount(request.getMinAmount())
                    .maxAmount(request.getMaxAmount())
                    .linkType(request.getLinkType())
                    .status(PaymentLink.PaymentLinkStatus.ACTIVE)
                    .expiresAt(request.getExpiresAt())
                    .maxUses(request.getMaxUses())
                    .requiresNote(request.getRequiresNote())
                    .customMessage(request.getCustomMessage())
                    .metadata(request.getMetadata())
                    .build();
            
            paymentLink = paymentLinkRepository.save(paymentLink);
            
            // Publish creation event
            publishPaymentLinkEvent(paymentLink, "CREATED");
            
            // Track metrics
            meterRegistry.counter("payment.links.created", 
                                "type", request.getLinkType().toString()).increment();
            
            timer.stop(Timer.builder("payment.link.create.time")
                    .description("Time to create payment link")
                    .tags("status", "success")
                    .register(meterRegistry));
            
            log.info("Payment link created successfully: {} -> {}", paymentLink.getId(), linkId);
            
            // Convert to response and enrich with user info
            PaymentLinkResponse response = PaymentLinkResponse.fromDomain(paymentLink);
            response.setCreatorName(creator.getDisplayName());
            response.setShortUrl(generateShortUrl(linkId));
            response.setQrCode(generateQRCodeUrl(linkId));
            
            return response;
            
        } catch (Exception e) {
            timer.stop(Timer.builder("payment.link.create.time")
                    .description("Time to create payment link")
                    .tags("status", "error")
                    .register(meterRegistry));
            
            meterRegistry.counter("payment.links.errors", "type", e.getClass().getSimpleName()).increment();
            log.error("Error creating payment link for user {}", creatorId, e);
            throw e;
        }
    }
    
    /**
     * Get payment link by link ID
     */
    @Cacheable(value = "paymentLinks", key = "#linkId")
    public PaymentLinkResponse getPaymentLink(String linkId) {
        log.debug("Getting payment link: {}", linkId);
        
        PaymentLink paymentLink = paymentLinkRepository.findByLinkId(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + linkId));
        
        PaymentLinkResponse response = PaymentLinkResponse.fromDomain(paymentLink);
        
        // Enrich with creator info
        try {
            UserResponse creator = userClient.getUser(paymentLink.getCreatorId());
            response.setCreatorName(creator.getDisplayName());
        } catch (Exception e) {
            log.warn("Failed to get creator info for payment link {}", linkId, e);
        }
        
        // Add computed fields
        response.setShortUrl(generateShortUrl(linkId));
        response.setQrCode(generateQRCodeUrl(linkId));
        
        return response;
    }
    
    /**
     * Process payment through a payment link
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "PAYMENT_LINK_PAYMENT", 
                           dynamicUserId = "request.payerId")
    public PaymentLinkTransactionResponse processPaymentLink(ProcessPaymentLinkRequest request) {
        Timer.Sample timer = Timer.start(meterRegistry);
        log.info("Processing payment link payment: {} for amount {}", request.getLinkId(), request.getAmount());
        
        try {
            // Get payment link
            PaymentLink paymentLink = paymentLinkRepository.findByLinkId(request.getLinkId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + request.getLinkId()));
            
            // Validate payment link can accept payments
            validatePaymentLinkForPayment(paymentLink, request);
            
            // Create transaction record
            String transactionId = generateTransactionId();
            PaymentLinkTransaction transaction = PaymentLinkTransaction.builder()
                    .paymentLink(paymentLink)
                    .transactionId(transactionId)
                    .payerId(request.getPayerId())
                    .payerEmail(request.getPayerEmail())
                    .payerName(request.getPayerName())
                    .amount(request.getAmount())
                    .currency(paymentLink.getCurrency())
                    .paymentNote(request.getPaymentNote())
                    .status(PaymentLinkTransaction.TransactionStatus.PENDING)
                    .paymentMethod(request.getPaymentMethod())
                    .metadata(request.getMetadata())
                    .ipAddress(request.getIpAddress())
                    .userAgent(request.getUserAgent())
                    .build();
            
            transaction = transactionRepository.save(transaction);
            
            try {
                // Process payment through wallet service
                TransferResponse transferResult = processPayment(paymentLink, transaction, request);
                
                // Update transaction with success
                transaction.markCompleted();
                transaction.setPaymentProvider("INTERNAL", transferResult.getReference(), transferResult.getId().toString());
                
                // Update payment link statistics
                paymentLink.incrementUsage();
                paymentLink.addToTotalCollected(request.getAmount());
                
                paymentLinkRepository.save(paymentLink);
                transaction = transactionRepository.save(transaction);
                
                // Clear cache
                evictPaymentLinkCache(request.getLinkId());
                
                // Publish success event
                publishTransactionEvent(transaction, paymentLink, "COMPLETED");
                
                // Track metrics
                meterRegistry.counter("payment.link.payments.completed",
                                    "type", paymentLink.getLinkType().toString()).increment();
                
                timer.stop(Timer.builder("payment.link.payment.time")
                        .description("Time to process payment link payment")
                        .tags("status", "success")
                        .register(meterRegistry));
                
                log.info("Payment link payment completed: {} -> {}", request.getLinkId(), transactionId);
                
                return enrichTransactionResponse(PaymentLinkTransactionResponse.fromDomain(transaction));
                
            } catch (Exception paymentException) {
                // Update transaction with failure
                transaction.markFailed(paymentException.getMessage());
                transaction = transactionRepository.save(transaction);
                
                // Publish failure event
                publishTransactionEvent(transaction, paymentLink, "FAILED");
                
                // Track failure metrics
                meterRegistry.counter("payment.link.payments.failed",
                                    "reason", paymentException.getClass().getSimpleName()).increment();
                
                timer.stop(Timer.builder("payment.link.payment.time")
                        .description("Time to process payment link payment")
                        .tags("status", "error")
                        .register(meterRegistry));
                
                log.error("Payment link payment failed: {} -> {}", request.getLinkId(), transactionId, paymentException);
                
                // Return the failed transaction response
                return enrichTransactionResponse(PaymentLinkTransactionResponse.fromDomain(transaction));
            }
            
        } catch (Exception e) {
            timer.stop(Timer.builder("payment.link.payment.time")
                    .description("Time to process payment link payment")
                    .tags("status", "error")
                    .register(meterRegistry));
            
            meterRegistry.counter("payment.link.errors", "type", e.getClass().getSimpleName()).increment();
            log.error("Error processing payment link payment: {}", request.getLinkId(), e);
            throw e;
        }
    }
    
    /**
     * Get payment links created by a user
     */
    @Cacheable(value = "userPaymentLinks", key = "#creatorId + '_' + #pageable.pageNumber")
    public Page<PaymentLinkResponse> getUserPaymentLinks(UUID creatorId, Pageable pageable) {
        log.debug("Getting payment links for user: {}", creatorId);
        
        Page<PaymentLink> links = paymentLinkRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, pageable);
        
        return links.map(link -> {
            PaymentLinkResponse response = PaymentLinkResponse.fromDomain(link);
            response.setShortUrl(generateShortUrl(link.getLinkId()));
            response.setQrCode(generateQRCodeUrl(link.getLinkId()));
            return response;
        });
    }
    
    /**
     * Get transactions for a payment link
     */
    public Page<PaymentLinkTransactionResponse> getPaymentLinkTransactions(UUID creatorId, UUID paymentLinkId, Pageable pageable) {
        log.debug("Getting transactions for payment link: {}", paymentLinkId);
        
        // Verify ownership
        PaymentLink paymentLink = paymentLinkRepository.findById(paymentLinkId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + paymentLinkId));
        
        if (!paymentLink.getCreatorId().equals(creatorId)) {
            throw new BusinessException("Access denied: You are not the owner of this payment link");
        }
        
        Page<PaymentLinkTransaction> transactions = transactionRepository
                .findByPaymentLinkIdOrderByCreatedAtDesc(paymentLinkId, pageable);
        
        return transactions.map(transaction -> 
                enrichTransactionResponse(PaymentLinkTransactionResponse.fromDomain(transaction)));
    }
    
    /**
     * Update payment link
     */
    @Transactional
    @CacheEvict(value = "paymentLinks", key = "#paymentLinkId")
    public PaymentLinkResponse updatePaymentLink(UUID creatorId, UUID paymentLinkId, UpdatePaymentLinkRequest request) {
        log.info("Updating payment link: {} by user {}", paymentLinkId, creatorId);
        
        PaymentLink paymentLink = paymentLinkRepository.findById(paymentLinkId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + paymentLinkId));
        
        // Verify ownership
        if (!paymentLink.getCreatorId().equals(creatorId)) {
            throw new BusinessException("Access denied: You are not the owner of this payment link");
        }
        
        // Update fields
        if (request.getTitle() != null) paymentLink.setTitle(request.getTitle());
        if (request.getDescription() != null) paymentLink.setDescription(request.getDescription());
        if (request.getCustomMessage() != null) paymentLink.setCustomMessage(request.getCustomMessage());
        if (request.getExpiresAt() != null) paymentLink.setExpiresAt(request.getExpiresAt());
        if (request.getMaxUses() != null) paymentLink.setMaxUses(request.getMaxUses());
        if (request.getRequiresNote() != null) paymentLink.setRequiresNote(request.getRequiresNote());
        
        paymentLink = paymentLinkRepository.save(paymentLink);
        
        // Publish update event
        publishPaymentLinkEvent(paymentLink, "UPDATED");
        
        log.info("Payment link updated successfully: {}", paymentLinkId);
        
        return PaymentLinkResponse.fromDomain(paymentLink);
    }
    
    /**
     * Deactivate payment link
     */
    @Transactional
    @CacheEvict(value = "paymentLinks", key = "#paymentLinkId")
    public void deactivatePaymentLink(UUID creatorId, UUID paymentLinkId) {
        log.info("Deactivating payment link: {} by user {}", paymentLinkId, creatorId);
        
        PaymentLink paymentLink = paymentLinkRepository.findById(paymentLinkId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + paymentLinkId));
        
        // Verify ownership
        if (!paymentLink.getCreatorId().equals(creatorId)) {
            throw new BusinessException("Access denied: You are not the owner of this payment link");
        }
        
        paymentLink.deactivate();
        paymentLinkRepository.save(paymentLink);
        
        // Publish deactivation event
        publishPaymentLinkEvent(paymentLink, "DEACTIVATED");
        
        meterRegistry.counter("payment.links.deactivated").increment();
        
        log.info("Payment link deactivated successfully: {}", paymentLinkId);
    }
    
    /**
     * Scheduled task to clean up expired payment links
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupExpiredPaymentLinks() {
        log.info("Cleaning up expired payment links");
        
        try {
            // Mark expired links as expired
            int expiredCount = paymentLinkRepository.markExpiredLinksAsExpired(LocalDateTime.now());
            
            // Mark completed links (reached max uses)
            int completedCount = paymentLinkRepository.markCompletedLinks();
            
            log.info("Cleanup completed: {} expired, {} completed", expiredCount, completedCount);
            
            meterRegistry.counter("payment.links.expired").increment(expiredCount);
            meterRegistry.counter("payment.links.completed").increment(completedCount);
            
        } catch (Exception e) {
            log.error("Error during payment link cleanup", e);
            meterRegistry.counter("payment.link.cleanup.errors").increment();
        }
    }
    
    // Private helper methods
    
    private void validateCreateRequest(CreatePaymentLinkRequest request) {
        if (!request.hasValidAmountRange()) {
            throw new BusinessException("Invalid amount range: minimum amount cannot be greater than maximum");
        }
        
        if (!request.hasValidFixedAmount()) {
            throw new BusinessException("Cannot specify both fixed amount and amount range");
        }
        
        if (request.getAmount() != null && request.getAmount().compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new BusinessException("Amount exceeds maximum allowed: " + MAX_TRANSACTION_AMOUNT);
        }
        
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException("Expiration time must be at least 5 minutes in the future");
        }
    }
    
    private void validatePaymentLinkForPayment(PaymentLink paymentLink, ProcessPaymentLinkRequest request) {
        if (!paymentLink.canAcceptPayment()) {
            String reason = paymentLink.isExpired() ? "expired" : 
                           paymentLink.hasReachedMaxUses() ? "reached maximum uses" : "not active";
            throw new BusinessException("Payment link cannot accept payments: " + reason);
        }
        
        if (!paymentLink.isAmountValid(request.getAmount())) {
            throw new BusinessException("Payment amount is not valid for this payment link");
        }
        
        if (paymentLink.getRequiresNote() && (request.getPaymentNote() == null || request.getPaymentNote().trim().isEmpty())) {
            throw new BusinessException("Payment note is required for this payment link");
        }
        
        // Check for duplicate payments from same IP/email within short time window
        if (request.getIpAddress() != null) {
            long recentCount = transactionRepository.countRecentTransactionsByIp(
                    request.getIpAddress(), LocalDateTime.now().minusMinutes(5));
            if (recentCount >= 3) {
                throw new BusinessException("Too many payment attempts from this location. Please try again later.");
            }
        }
    }
    
    private TransferResponse processPayment(PaymentLink paymentLink, PaymentLinkTransaction transaction, ProcessPaymentLinkRequest request) {
        // Get or create default wallet for the payment link creator
        UUID recipientWalletId = getOrCreateDefaultWallet(paymentLink.getCreatorId(), paymentLink.getCurrency());
        
        // Create transfer request
        TransferRequest transferRequest = TransferRequest.builder()
                .sourceWalletId(request.getPayerId() != null ? 
                               getOrCreateDefaultWallet(request.getPayerId(), paymentLink.getCurrency()) : null)
                .targetWalletId(recipientWalletId)
                .amount(request.getAmount())
                .description("Payment via link: " + paymentLink.getTitle())
                .build();
        
        return walletClient.transfer(transferRequest);
    }
    
    private UUID getOrCreateDefaultWallet(UUID userId, String currency) {
        try {
            return walletClient.getUserWallets(userId).stream()
                    .filter(wallet -> currency.equals(wallet.getCurrency()))
                    .map(wallet -> wallet.getId())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("No wallet found for currency: " + currency));
        } catch (Exception e) {
            log.error("Error getting wallet for user {}", userId, e);
            throw new BusinessException("Unable to process payment: wallet service unavailable");
        }
    }
    
    private UserResponse validateUserExists(UUID userId) {
        try {
            UserResponse user = userClient.getUser(userId);
            if (user == null) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }
            return user;
        } catch (Exception e) {
            log.error("Error validating user {}", userId, e);
            throw new BusinessException("Unable to validate user");
        }
    }
    
    private String generateUniqueLinkId() {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            StringBuilder linkId = new StringBuilder(LINK_ID_LENGTH);
            for (int i = 0; i < LINK_ID_LENGTH; i++) {
                linkId.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
            }
            String generated = linkId.toString();
            
            if (!paymentLinkRepository.existsByLinkId(generated)) {
                return generated;
            }
        }
        
        throw new BusinessException("Unable to generate unique link ID after " + MAX_RETRY_ATTEMPTS + " attempts");
    }
    
    private String generateTransactionId() {
        return "PLT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    
    private String generateShortUrl(String linkId) {
        return "https://waqiti.app/p/" + linkId;
    }
    
    private String generateQRCodeUrl(String linkId) {
        return "https://api.waqiti.app/qr/payment-link/" + linkId;
    }
    
    @CacheEvict(value = "paymentLinks", key = "#linkId")
    private void evictPaymentLinkCache(String linkId) {
        // Cache eviction handled by annotation
    }
    
    private PaymentLinkTransactionResponse enrichTransactionResponse(PaymentLinkTransactionResponse response) {
        // Enrich with payer display name if user ID is available
        if (response.getPayerId() != null) {
            try {
                UserResponse payer = userClient.getUser(response.getPayerId());
                response.setPayerDisplayName(payer.getDisplayName());
            } catch (Exception e) {
                log.warn("Failed to get payer info for transaction {}", response.getTransactionId(), e);
            }
        }
        
        return response;
    }
    
    private void publishPaymentLinkEvent(PaymentLink paymentLink, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "PAYMENT_LINK_" + eventType,
                    "paymentLinkId", paymentLink.getId().toString(),
                    "linkId", paymentLink.getLinkId(),
                    "creatorId", paymentLink.getCreatorId().toString(),
                    "title", paymentLink.getTitle(),
                    "linkType", paymentLink.getLinkType().toString(),
                    "status", paymentLink.getStatus().toString(),
                    "timestamp", LocalDateTime.now()
            );
            
            eventPublisher.publishEvent(
                    PAYMENT_LINK_EVENTS_TOPIC,
                    paymentLink.getId().toString(),
                    "PAYMENT_LINK_" + eventType,
                    event
            );
            
        } catch (Exception e) {
            log.error("Failed to publish payment link event", e);
        }
    }
    
    private void publishTransactionEvent(PaymentLinkTransaction transaction, PaymentLink paymentLink, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "PAYMENT_LINK_TRANSACTION_" + eventType,
                    "transactionId", transaction.getTransactionId(),
                    "paymentLinkId", paymentLink.getId().toString(),
                    "linkId", paymentLink.getLinkId(),
                    "amount", transaction.getAmount(),
                    "currency", transaction.getCurrency(),
                    "status", transaction.getStatus().toString(),
                    "timestamp", LocalDateTime.now()
            );
            
            eventPublisher.publishEvent(
                    PAYMENT_LINK_EVENTS_TOPIC,
                    transaction.getTransactionId(),
                    "PAYMENT_LINK_TRANSACTION_" + eventType,
                    event
            );
            
        } catch (Exception e) {
            log.error("Failed to publish transaction event", e);
        }
    }
}

