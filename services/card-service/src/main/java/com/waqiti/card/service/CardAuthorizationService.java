package com.waqiti.card.service;

import com.waqiti.card.dto.CardAuthorizationRequest;
import com.waqiti.card.dto.CardAuthorizationResponse;
import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardAuthorization;
import com.waqiti.card.enums.AuthorizationStatus;
import com.waqiti.card.enums.DeclineReason;
import com.waqiti.card.repository.CardAuthorizationRepository;
import com.waqiti.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CardAuthorizationService - Business logic for transaction authorization
 *
 * Handles:
 * - Authorization request processing
 * - Fraud and risk checks
 * - Velocity limit checks
 * - Credit limit checks
 * - Authorization approval/decline
 * - Authorization capture
 * - Authorization reversal
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardAuthorizationService {

    private final CardAuthorizationRepository authorizationRepository;
    private final CardRepository cardRepository;
    private final CardFraudDetectionService fraudDetectionService;
    private final CardService cardService;

    /**
     * Process authorization request
     */
    @Transactional
    public CardAuthorizationResponse authorizeTransaction(CardAuthorizationRequest request) {
        log.info("Processing authorization for card: {} - Amount: {} {}",
            request.getCardId(), request.getAmount(), request.getCurrencyCode());

        // Load card
        Card card = cardRepository.findById(request.getCardId())
            .orElseThrow(() -> new RuntimeException("Card not found: " + request.getCardId()));

        // Generate authorization ID
        String authorizationId = generateAuthorizationId();

        // Capture balance before authorization
        BigDecimal balanceBefore = card.getAvailableCredit();

        // Build authorization entity
        CardAuthorization authorization = CardAuthorization.builder()
            .authorizationId(authorizationId)
            .cardId(request.getCardId())
            .userId(card.getUserId())
            .authorizationStatus(AuthorizationStatus.PENDING)
            .authorizationDate(LocalDateTime.now())
            .authorizationAmount(request.getAmount())
            .currencyCode(request.getCurrencyCode())
            .availableBalanceBefore(balanceBefore)
            .creditLimit(card.getCreditLimit())
            .merchantId(request.getMerchantId())
            .merchantName(request.getMerchantName())
            .merchantCategoryCode(request.getMerchantCategoryCode())
            .merchantCountry(request.getMerchantCountry())
            .posEntryMode(request.getPosEntryMode())
            .terminalId(request.getTerminalId())
            .isOnline(request.getIsOnline())
            .isContactless(request.getIsContactless())
            .isCardPresent(request.getIsCardPresent())
            .posData(request.getPosData())
            .metadata(request.getMetadata())
            .isCaptured(false)
            .isReversed(false)
            .build();

        // Perform authorization checks
        AuthorizationDecision decision = performAuthorizationChecks(card, authorization, request);

        if (decision.isApproved()) {
            // Approve authorization
            authorization.approve(request.getAmount());
            authorization.setAvailableBalanceAfter(balanceBefore.subtract(request.getAmount()));
            authorization.setAuthorizationCode(generateAuthorizationCode());

            // Deduct available credit
            cardService.deductCredit(card.getId(), request.getAmount());

            log.info("Authorization approved: {} - Code: {}", authorizationId, authorization.getAuthorizationCode());
        } else {
            // Decline authorization
            authorization.decline(decision.getDeclineReason(), decision.getDeclineMessage());
            authorization.setAvailableBalanceAfter(balanceBefore);

            log.warn("Authorization declined: {} - Reason: {}", authorizationId, decision.getDeclineReason());
        }

        // Set check results
        authorization.setFraudCheckPassed(decision.isFraudCheckPassed());
        authorization.setVelocityCheckPassed(decision.isVelocityCheckPassed());
        authorization.setLimitCheckPassed(decision.isLimitCheckPassed());
        authorization.setRiskScore(decision.getRiskScore());
        authorization.setRiskLevel(decision.getRiskLevel());

        authorization = authorizationRepository.save(authorization);

        return mapToAuthorizationResponse(authorization);
    }

    /**
     * Capture authorization (convert to transaction)
     */
    @Transactional
    public CardAuthorizationResponse captureAuthorization(String authorizationId, BigDecimal captureAmount) {
        log.info("Capturing authorization: {} - Amount: {}", authorizationId, captureAmount);

        CardAuthorization authorization = authorizationRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new RuntimeException("Authorization not found: " + authorizationId));

        if (!authorization.isApproved()) {
            throw new RuntimeException("Authorization is not approved - cannot capture");
        }

        if (authorization.isCaptured()) {
            throw new RuntimeException("Authorization is already captured");
        }

        if (authorization.isAuthorizationExpired()) {
            throw new RuntimeException("Authorization has expired");
        }

        // Validate capture amount
        if (captureAmount == null) {
            captureAmount = authorization.getApprovedAmount();
        }

        if (captureAmount.compareTo(authorization.getAvailableCaptureAmount()) > 0) {
            throw new RuntimeException("Capture amount exceeds available amount");
        }

        // Capture authorization
        authorization.capture(captureAmount);
        authorization = authorizationRepository.save(authorization);

        log.info("Authorization captured: {} - Amount: {}", authorizationId, captureAmount);

        return mapToAuthorizationResponse(authorization);
    }

    /**
     * Reverse authorization (release hold)
     */
    @Transactional
    public CardAuthorizationResponse reverseAuthorization(String authorizationId, String reason) {
        log.info("Reversing authorization: {} - Reason: {}", authorizationId, reason);

        CardAuthorization authorization = authorizationRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new RuntimeException("Authorization not found: " + authorizationId));

        if (authorization.isReversed()) {
            throw new RuntimeException("Authorization is already reversed");
        }

        if (!authorization.isApproved()) {
            throw new RuntimeException("Cannot reverse non-approved authorization");
        }

        // Get amount to reverse
        BigDecimal amountToReverse = authorization.getAvailableCaptureAmount();

        // Reverse authorization
        authorization.reverse(reason);
        authorization = authorizationRepository.save(authorization);

        // Restore credit to card
        if (amountToReverse.compareTo(BigDecimal.ZERO) > 0) {
            cardService.restoreCredit(authorization.getCardId(), amountToReverse);
        }

        log.info("Authorization reversed: {} - Amount restored: {}", authorizationId, amountToReverse);

        return mapToAuthorizationResponse(authorization);
    }

    /**
     * Get authorization by ID
     */
    @Transactional(readOnly = true)
    public CardAuthorizationResponse getAuthorizationById(String authorizationId) {
        CardAuthorization authorization = authorizationRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new RuntimeException("Authorization not found: " + authorizationId));

        return mapToAuthorizationResponse(authorization);
    }

    /**
     * Get active authorizations for card
     */
    @Transactional(readOnly = true)
    public List<CardAuthorizationResponse> getActiveAuthorizationsByCardId(UUID cardId) {
        List<CardAuthorization> authorizations = authorizationRepository.findActiveAuthorizationsByCardId(
            cardId, LocalDateTime.now());

        return authorizations.stream()
            .map(this::mapToAuthorizationResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get expired authorizations
     */
    @Transactional(readOnly = true)
    public List<CardAuthorizationResponse> getExpiredAuthorizations() {
        List<CardAuthorization> authorizations = authorizationRepository.findExpiredAuthorizations(LocalDateTime.now());

        return authorizations.stream()
            .map(this::mapToAuthorizationResponse)
            .collect(Collectors.toList());
    }

    /**
     * Mark expired authorizations
     */
    @Transactional
    public void markExpiredAuthorizations() {
        List<CardAuthorization> expiredAuths = authorizationRepository.findExpiredAuthorizations(LocalDateTime.now());

        for (CardAuthorization auth : expiredAuths) {
            auth.markExpired();

            // Restore credit if not captured
            BigDecimal amountToRestore = auth.getAvailableCaptureAmount();
            if (amountToRestore.compareTo(BigDecimal.ZERO) > 0) {
                cardService.restoreCredit(auth.getCardId(), amountToRestore);
            }
        }

        if (!expiredAuths.isEmpty()) {
            authorizationRepository.saveAll(expiredAuths);
            log.info("Marked {} authorizations as expired", expiredAuths.size());
        }
    }

    // ========================================================================
    // AUTHORIZATION CHECKS
    // ========================================================================

    private AuthorizationDecision performAuthorizationChecks(
        Card card, CardAuthorization authorization, CardAuthorizationRequest request) {

        AuthorizationDecision decision = new AuthorizationDecision();

        // 1. Check card status
        if (!card.isUsable()) {
            decision.decline(DeclineReason.CARD_BLOCKED, "Card is not usable");
            return decision;
        }

        // 2. Check card expiry
        if (card.isExpired()) {
            decision.decline(DeclineReason.EXPIRED_CARD, "Card has expired");
            return decision;
        }

        // 3. Check if card is activated
        if (card.getCardStatus() != com.waqiti.card.enums.CardStatus.ACTIVE) {
            decision.decline(DeclineReason.CARD_NOT_ACTIVATED, "Card is not activated");
            return decision;
        }

        // 4. Check PIN lock status
        if (card.isPinLocked()) {
            decision.decline(DeclineReason.CARD_BLOCKED, "PIN is locked");
            return decision;
        }

        // 5. Check online transactions enabled
        if (Boolean.TRUE.equals(request.getIsOnline()) && !card.getIsOnlineEnabled()) {
            decision.decline(DeclineReason.ONLINE_NOT_ENABLED, "Online transactions not enabled");
            return decision;
        }

        // 6. Check international transactions enabled
        if (isInternationalTransaction(request) && !card.getIsInternationalEnabled()) {
            decision.decline(DeclineReason.INTERNATIONAL_NOT_ENABLED, "International transactions not enabled");
            return decision;
        }

        // 7. Check credit limit
        if (!card.hasSufficientCredit(request.getAmount())) {
            decision.decline(DeclineReason.INSUFFICIENT_FUNDS, "Insufficient credit available");
            return decision;
        }

        // 8. Fraud detection check
        BigDecimal fraudScore = fraudDetectionService.calculateFraudScore(card, authorization, request);
        decision.setRiskScore(fraudScore);
        decision.setRiskLevel(determineRiskLevel(fraudScore));

        if (fraudScore.compareTo(new BigDecimal("90.00")) > 0) {
            decision.setFraudCheckPassed(false);
            decision.decline(DeclineReason.SUSPECTED_FRAUD, "Transaction suspected as fraudulent");
            return decision;
        }
        decision.setFraudCheckPassed(true);

        // 9. Velocity check
        boolean velocityCheckPassed = fraudDetectionService.checkVelocityLimits(card.getId(), request.getAmount());
        decision.setVelocityCheckPassed(velocityCheckPassed);
        if (!velocityCheckPassed) {
            decision.decline(DeclineReason.VELOCITY_LIMIT_EXCEEDED, "Velocity limit exceeded");
            return decision;
        }

        // 10. Limit checks passed
        decision.setLimitCheckPassed(true);

        // All checks passed - approve
        decision.approve();

        return decision;
    }

    private boolean isInternationalTransaction(CardAuthorizationRequest request) {
        // In production: compare merchant country with card issuer country
        return request.getMerchantCountry() != null && !"USA".equals(request.getMerchantCountry());
    }

    private String determineRiskLevel(BigDecimal fraudScore) {
        if (fraudScore.compareTo(new BigDecimal("75.00")) > 0) return "HIGH";
        if (fraudScore.compareTo(new BigDecimal("50.00")) > 0) return "MEDIUM";
        return "LOW";
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private CardAuthorizationResponse mapToAuthorizationResponse(CardAuthorization authorization) {
        return CardAuthorizationResponse.builder()
            .id(authorization.getId())
            .authorizationId(authorization.getAuthorizationId())
            .authorizationCode(authorization.getAuthorizationCode())
            .authorizationStatus(authorization.getAuthorizationStatus())
            .authorizationAmount(authorization.getAuthorizationAmount())
            .approvedAmount(authorization.getApprovedAmount())
            .currencyCode(authorization.getCurrencyCode())
            .authorizationDate(authorization.getAuthorizationDate())
            .expiryDate(authorization.getExpiryDate())
            .riskScore(authorization.getRiskScore())
            .riskLevel(authorization.getRiskLevel())
            .fraudCheckPassed(authorization.getFraudCheckPassed())
            .velocityCheckPassed(authorization.getVelocityCheckPassed())
            .limitCheckPassed(authorization.getLimitCheckPassed())
            .declineReason(authorization.getDeclineReason())
            .declineMessage(authorization.getDeclineMessage())
            .processorResponseCode(authorization.getProcessorResponseCode())
            .processorResponseMessage(authorization.getProcessorResponseMessage())
            .availableBalanceBefore(authorization.getAvailableBalanceBefore())
            .availableBalanceAfter(authorization.getAvailableBalanceAfter())
            .createdAt(authorization.getCreatedAt())
            .build();
    }

    private String generateAuthorizationId() {
        return "AUTH-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String generateAuthorizationCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * Authorization decision helper class
     */
    private static class AuthorizationDecision {
        private boolean approved = false;
        private DeclineReason declineReason;
        private String declineMessage;
        private boolean fraudCheckPassed = true;
        private boolean velocityCheckPassed = true;
        private boolean limitCheckPassed = true;
        private BigDecimal riskScore = BigDecimal.ZERO;
        private String riskLevel = "LOW";

        public void approve() {
            this.approved = true;
        }

        public void decline(DeclineReason reason, String message) {
            this.approved = false;
            this.declineReason = reason;
            this.declineMessage = message;
        }

        public boolean isApproved() { return approved; }
        public DeclineReason getDeclineReason() { return declineReason; }
        public String getDeclineMessage() { return declineMessage; }
        public boolean isFraudCheckPassed() { return fraudCheckPassed; }
        public boolean isVelocityCheckPassed() { return velocityCheckPassed; }
        public boolean isLimitCheckPassed() { return limitCheckPassed; }
        public BigDecimal getRiskScore() { return riskScore; }
        public String getRiskLevel() { return riskLevel; }

        public void setFraudCheckPassed(boolean fraudCheckPassed) { this.fraudCheckPassed = fraudCheckPassed; }
        public void setVelocityCheckPassed(boolean velocityCheckPassed) { this.velocityCheckPassed = velocityCheckPassed; }
        public void setLimitCheckPassed(boolean limitCheckPassed) { this.limitCheckPassed = limitCheckPassed; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    }
}
