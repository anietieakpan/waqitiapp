package com.waqiti.card.service;

import com.waqiti.card.dto.CardDisputeCreateRequest;
import com.waqiti.card.dto.CardDisputeResponse;
import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardDispute;
import com.waqiti.card.entity.CardTransaction;
import com.waqiti.card.enums.DisputeStatus;
import com.waqiti.card.repository.CardDisputeRepository;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardTransactionRepository;
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
 * CardDisputeService - Dispute and chargeback management
 *
 * Handles:
 * - Dispute creation and lifecycle
 * - Chargeback processing
 * - Provisional credit management
 * - Merchant response tracking
 * - Arbitration handling
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardDisputeService {

    private final CardDisputeRepository disputeRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final CardTransactionService transactionService;

    /**
     * Create a new dispute
     */
    @Transactional
    public CardDisputeResponse createDispute(CardDisputeCreateRequest request) {
        log.info("Creating dispute for transaction: {}", request.getTransactionId());

        // Validate transaction exists
        CardTransaction transaction = transactionRepository.findById(request.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + request.getTransactionId()));

        // Validate transaction is not already disputed
        if (transaction.getIsDisputed()) {
            throw new RuntimeException("Transaction is already disputed");
        }

        // Validate disputed amount
        if (request.getDisputedAmount().compareTo(transaction.getAmount()) > 0) {
            throw new RuntimeException("Disputed amount cannot exceed transaction amount");
        }

        Card card = cardRepository.findById(transaction.getCardId())
            .orElseThrow(() -> new RuntimeException("Card not found"));

        // Generate dispute ID and case number
        String disputeId = generateDisputeId();
        String caseNumber = generateCaseNumber();

        // Create dispute
        CardDispute dispute = CardDispute.builder()
            .disputeId(disputeId)
            .caseNumber(caseNumber)
            .transactionId(request.getTransactionId())
            .cardId(transaction.getCardId())
            .userId(card.getUserId())
            .disputeStatus(DisputeStatus.OPEN)
            .disputeCategory(request.getDisputeCategory())
            .disputeReason(request.getDisputeReason())
            .cardholderExplanation(request.getCardholderExplanation())
            .disputedAmount(request.getDisputedAmount())
            .currencyCode(request.getCurrencyCode())
            .transactionAmount(transaction.getAmount())
            .filedDate(LocalDateTime.now())
            .merchantId(transaction.getMerchantId())
            .merchantName(transaction.getMerchantName())
            .merchantResponseDeadline(LocalDateTime.now().plusDays(10))
            .documentUrls(request.getDocumentUrls())
            .provisionalCreditIssued(false)
            .chargebackIssued(false)
            .escalatedToArbitration(false)
            .build();

        dispute = disputeRepository.save(dispute);

        // Mark transaction as disputed
        transactionService.markAsDisputed(request.getTransactionId(), dispute.getId());

        log.info("Dispute created: {} - Case: {}", disputeId, caseNumber);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Get dispute by ID
     */
    @Transactional(readOnly = true)
    public CardDisputeResponse getDisputeById(String disputeId) {
        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        return mapToDisputeResponse(dispute);
    }

    /**
     * Get disputes for a card
     */
    @Transactional(readOnly = true)
    public List<CardDisputeResponse> getDisputesByCardId(UUID cardId) {
        List<CardDispute> disputes = disputeRepository.findByCardId(cardId);
        return disputes.stream()
            .map(this::mapToDisputeResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get disputes for a user
     */
    @Transactional(readOnly = true)
    public List<CardDisputeResponse> getDisputesByUserId(UUID userId) {
        List<CardDispute> disputes = disputeRepository.findByUserId(userId);
        return disputes.stream()
            .map(this::mapToDisputeResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get active disputes
     */
    @Transactional(readOnly = true)
    public List<CardDisputeResponse> getActiveDisputes() {
        List<CardDispute> disputes = disputeRepository.findActiveDisputes();
        return disputes.stream()
            .map(this::mapToDisputeResponse)
            .collect(Collectors.toList());
    }

    /**
     * Issue provisional credit
     */
    @Transactional
    public CardDisputeResponse issueProvisionalCredit(String disputeId) {
        log.info("Issuing provisional credit for dispute: {}", disputeId);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        if (dispute.getProvisionalCreditIssued()) {
            throw new RuntimeException("Provisional credit already issued");
        }

        // Issue provisional credit
        dispute.issueProvisionalCredit(dispute.getDisputedAmount());

        // Credit customer account - restore available credit on card
        Card card = cardRepository.findByCardIdAndNotDeleted(dispute.getCardId().toString())
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + dispute.getCardId()));

        BigDecimal provisionalAmount = dispute.getProvisionalCreditAmount();
        if (provisionalAmount != null && provisionalAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Restore available credit
            BigDecimal currentAvailable = card.getAvailableCredit() != null ?
                    card.getAvailableCredit() : BigDecimal.ZERO;
            card.setAvailableCredit(currentAvailable.add(provisionalAmount));

            // Reduce outstanding balance
            BigDecimal currentBalance = card.getOutstandingBalance() != null ?
                    card.getOutstandingBalance() : BigDecimal.ZERO;
            card.setOutstandingBalance(currentBalance.subtract(provisionalAmount));

            cardRepository.save(card);
            log.info("Credited {} to card {} for dispute provisional credit",
                    provisionalAmount, card.getCardId());
        }

        dispute = disputeRepository.save(dispute);

        log.info("Provisional credit issued: {} for dispute: {}", dispute.getProvisionalCreditAmount(), disputeId);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Issue chargeback
     */
    @Transactional
    public CardDisputeResponse issueChargeback(String disputeId) {
        log.info("Issuing chargeback for dispute: {}", disputeId);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        if (dispute.getChargebackIssued()) {
            throw new RuntimeException("Chargeback already issued");
        }

        String chargebackReference = "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        dispute.issueChargeback(dispute.getDisputedAmount(), chargebackReference);
        dispute = disputeRepository.save(dispute);

        log.info("Chargeback issued: {} for dispute: {}", chargebackReference, disputeId);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Escalate to arbitration
     */
    @Transactional
    public CardDisputeResponse escalateToArbitration(String disputeId, BigDecimal arbitrationFee) {
        log.info("Escalating dispute to arbitration: {}", disputeId);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        dispute.escalateToArbitration(arbitrationFee);
        dispute = disputeRepository.save(dispute);

        log.info("Dispute escalated to arbitration: {}", disputeId);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Resolve dispute in favor of cardholder
     */
    @Transactional
    public CardDisputeResponse resolveInFavorOfCardholder(String disputeId, String outcome, BigDecimal creditAmount) {
        log.info("Resolving dispute in favor of cardholder: {}", disputeId);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        dispute.resolveInFavorOfCardholder(outcome, creditAmount);
        dispute = disputeRepository.save(dispute);

        log.info("Dispute resolved in favor of cardholder: {}", disputeId);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Resolve dispute in favor of merchant
     */
    @Transactional
    public CardDisputeResponse resolveInFavorOfMerchant(String disputeId, String outcome) {
        log.info("Resolving dispute in favor of merchant: {}", disputeId);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        dispute.resolveInFavorOfMerchant(outcome);
        dispute = disputeRepository.save(dispute);

        log.info("Dispute resolved in favor of merchant: {}", disputeId);

        return mapToDisputeResponse(dispute);
    }

    /**
     * Withdraw dispute
     */
    @Transactional
    public CardDisputeResponse withdrawDispute(String disputeId, String reason) {
        log.info("Withdrawing dispute: {} - Reason: {}", disputeId, reason);

        CardDispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + disputeId));

        dispute.withdraw(reason);
        dispute = disputeRepository.save(dispute);

        log.info("Dispute withdrawn: {}", disputeId);

        return mapToDisputeResponse(dispute);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private CardDisputeResponse mapToDisputeResponse(CardDispute dispute) {
        return CardDisputeResponse.builder()
            .id(dispute.getId())
            .disputeId(dispute.getDisputeId())
            .caseNumber(dispute.getCaseNumber())
            .transactionId(dispute.getTransactionId())
            .cardId(dispute.getCardId())
            .userId(dispute.getUserId())
            .disputeStatus(dispute.getDisputeStatus())
            .disputeCategory(dispute.getDisputeCategory())
            .disputeReason(dispute.getDisputeReason())
            .disputedAmount(dispute.getDisputedAmount())
            .currencyCode(dispute.getCurrencyCode())
            .filedDate(dispute.getFiledDate())
            .merchantResponseDeadline(dispute.getMerchantResponseDeadline())
            .resolutionDate(dispute.getResolutionDate())
            .resolutionOutcome(dispute.getResolutionOutcome())
            .resolvedInFavorOf(dispute.getResolvedInFavorOf())
            .provisionalCreditIssued(dispute.getProvisionalCreditIssued())
            .provisionalCreditAmount(dispute.getProvisionalCreditAmount())
            .chargebackIssued(dispute.getChargebackIssued())
            .escalatedToArbitration(dispute.getEscalatedToArbitration())
            .assignedTo(dispute.getAssignedTo())
            .createdAt(dispute.getCreatedAt())
            .build();
    }

    private String generateDisputeId() {
        return "DSP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String generateCaseNumber() {
        return "CASE-" + System.currentTimeMillis();
    }
}
