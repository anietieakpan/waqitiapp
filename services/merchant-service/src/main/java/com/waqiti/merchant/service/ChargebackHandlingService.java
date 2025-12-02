package com.waqiti.merchant.service;

import com.waqiti.common.events.ChargebackInitiatedEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.merchant.domain.Chargeback;
import com.waqiti.merchant.domain.ChargebackStatus;
import com.waqiti.merchant.domain.Merchant;
import com.waqiti.merchant.repository.ChargebackRepository;
import com.waqiti.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL: Chargeback Handling Service
 * 
 * Manages chargeback disputes and merchant liability.
 * Essential for:
 * - Processing chargeback claims from card networks
 * - Managing dispute evidence collection
 * - Calculating merchant liability and fees
 * - Preventing merchant fund losses
 * - Maintaining card network compliance
 * 
 * WITHOUT THIS: Merchants lose funds, platform faces penalties
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChargebackHandlingService {

    private final ChargebackRepository chargebackRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantAccountService merchantAccountService;
    private final DisputeEvidenceService disputeEvidenceService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Chargeback fee structure
    private static final BigDecimal CHARGEBACK_FEE = new BigDecimal("25.00");
    private static final BigDecimal HIGH_RISK_CHARGEBACK_FEE = new BigDecimal("50.00");
    private static final int DISPUTE_RESPONSE_DAYS = 7;
    private static final double EXCESSIVE_CHARGEBACK_THRESHOLD = 0.01; // 1% threshold

    /**
     * Process incoming chargeback from card network
     */
    public Chargeback processIncomingChargeback(ChargebackInitiatedEvent event) {
        log.warn("Processing chargeback: transactionId={}, amount={}, reason={}", 
                event.getOriginalTransactionId(), event.getAmount(), event.getReasonCode());
        
        try {
            // Find merchant
            Merchant merchant = merchantRepository.findById(UUID.fromString(event.getMerchantId()))
                .orElseThrow(() -> new IllegalStateException("Merchant not found: " + event.getMerchantId()));
            
            // Check for duplicate chargeback
            if (chargebackRepository.existsByOriginalTransactionId(event.getOriginalTransactionId())) {
                log.warn("Duplicate chargeback for transaction: {}", event.getOriginalTransactionId());
                return chargebackRepository.findByOriginalTransactionId(event.getOriginalTransactionId())
                    .orElseThrow();
            }
            
            // Create chargeback record
            Chargeback chargeback = createChargebackRecord(event, merchant);
            
            // Debit merchant account immediately (liability shift)
            debitMerchantAccount(merchant, chargeback);
            
            // Apply chargeback fee
            applyChargebackFee(merchant, chargeback);
            
            // Check if merchant exceeds chargeback threshold
            checkChargebackThreshold(merchant);
            
            // Collect initial evidence
            collectInitialEvidence(chargeback);
            
            // Notify merchant immediately
            notifyMerchantOfChargeback(merchant, chargeback);
            
            // Save chargeback
            chargeback = chargebackRepository.save(chargeback);
            
            // Publish event for downstream processing
            publishChargebackEvent(chargeback);
            
            // Update metrics
            updateChargebackMetrics(merchant, chargeback);
            
            log.info("Chargeback processed: id={}, merchantId={}, amount={}", 
                    chargeback.getId(), merchant.getId(), chargeback.getAmount());
            
            return chargeback;
            
        } catch (Exception e) {
            log.error("Failed to process chargeback for transaction: {}", 
                    event.getOriginalTransactionId(), e);
            
            // Create alert for operations team
            createChargebackProcessingAlert(event, e);
            
            throw new ChargebackProcessingException("Chargeback processing failed", e);
        }
    }

    /**
     * Submit dispute evidence to fight chargeback
     */
    public void submitDisputeEvidence(UUID chargebackId, DisputeEvidenceRequest request) {
        log.info("Submitting dispute evidence for chargeback: {}", chargebackId);
        
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found"));
        
        // Validate dispute window
        if (chargeback.getResponseDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Dispute response deadline has passed");
        }
        
        // Validate evidence
        validateDisputeEvidence(request);
        
        // Store evidence
        DisputeEvidence evidence = disputeEvidenceService.storeEvidence(chargebackId, request);
        
        // Submit to card network
        submitToCardNetwork(chargeback, evidence);
        
        // Update chargeback status
        chargeback.setStatus(ChargebackStatus.DISPUTED);
        chargeback.setDisputeSubmittedAt(LocalDateTime.now());
        chargeback.setDisputeEvidence(evidence.getId());
        chargebackRepository.save(chargeback);
        
        log.info("Dispute evidence submitted for chargeback: {}", chargebackId);
    }

    /**
     * Process chargeback resolution from card network
     */
    public void processChargebackResolution(UUID chargebackId, ChargebackResolution resolution) {
        log.info("Processing chargeback resolution: {} - {}", chargebackId, resolution.getDecision());
        
        Chargeback chargeback = chargebackRepository.findById(chargebackId)
            .orElseThrow(() -> new IllegalArgumentException("Chargeback not found"));
        
        Merchant merchant = merchantRepository.findById(chargeback.getMerchantId())
            .orElseThrow();
        
        switch (resolution.getDecision()) {
            case WON:
                handleChargebackWon(merchant, chargeback, resolution);
                break;
            case LOST:
                handleChargebackLost(merchant, chargeback, resolution);
                break;
            case PARTIAL:
                handlePartialResolution(merchant, chargeback, resolution);
                break;
        }
        
        // Update chargeback record
        chargeback.setStatus(mapResolutionToStatus(resolution.getDecision()));
        chargeback.setResolvedAt(LocalDateTime.now());
        chargeback.setResolutionDetails(resolution.getDetails());
        chargebackRepository.save(chargeback);
        
        // Notify merchant
        notifyMerchantOfResolution(merchant, chargeback, resolution);
        
        // Update metrics
        updateResolutionMetrics(merchant, resolution);
    }

    /**
     * Calculate chargeback liability for merchant
     */
    public BigDecimal calculateChargebackLiability(UUID merchantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Chargeback> chargebacks = chargebackRepository
            .findByMerchantIdAndCreatedAtBetween(merchantId, startDate, endDate);
        
        BigDecimal totalLiability = BigDecimal.ZERO;
        
        for (Chargeback chargeback : chargebacks) {
            if (chargeback.getStatus() != ChargebackStatus.WON) {
                totalLiability = totalLiability.add(chargeback.getAmount());
                totalLiability = totalLiability.add(chargeback.getFeeAmount());
            }
        }
        
        return totalLiability;
    }

    /**
     * Check if merchant is high risk based on chargeback rate
     */
    public boolean isMerchantHighRisk(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
            .orElseThrow();
        
        // Calculate chargeback rate for last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        long totalTransactions = merchant.getTransactionCount30Days();
        long chargebackCount = chargebackRepository.countByMerchantIdAndCreatedAtAfter(merchantId, thirtyDaysAgo);
        
        if (totalTransactions == 0) {
            return false;
        }
        
        double chargebackRate = (double) chargebackCount / totalTransactions;
        
        boolean isHighRisk = chargebackRate > EXCESSIVE_CHARGEBACK_THRESHOLD;
        
        if (isHighRisk) {
            log.warn("Merchant {} has excessive chargeback rate: {}%", 
                    merchantId, chargebackRate * 100);
            
            // Flag merchant as high risk
            merchant.setHighRiskStatus(true);
            merchant.setChargebackRate(chargebackRate);
            merchantRepository.save(merchant);
        }
        
        return isHighRisk;
    }

    // Private helper methods

    private Chargeback createChargebackRecord(ChargebackInitiatedEvent event, Merchant merchant) {
        return Chargeback.builder()
            .id(UUID.randomUUID())
            .merchantId(merchant.getId())
            .originalTransactionId(event.getOriginalTransactionId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .reasonCode(event.getReasonCode())
            .reasonDescription(mapReasonCodeToDescription(event.getReasonCode()))
            .cardNetwork(event.getCardNetwork())
            .issuerReferenceNumber(event.getIssuerReferenceNumber())
            .status(ChargebackStatus.PENDING)
            .receivedAt(LocalDateTime.now())
            .responseDeadline(LocalDateTime.now().plusDays(DISPUTE_RESPONSE_DAYS))
            .cardholderName(event.getCardholderName())
            .last4Digits(event.getLast4Digits())
            .feeAmount(calculateChargebackFee(merchant))
            .liabilityShift(determineLiabilityShift(event))
            .build();
    }

    private void debitMerchantAccount(Merchant merchant, Chargeback chargeback) {
        // Immediately debit merchant account for chargeback amount
        merchantAccountService.debitAccount(
            merchant.getId(),
            chargeback.getAmount(),
            "Chargeback: " + chargeback.getReasonDescription(),
            chargeback.getId().toString()
        );
        
        log.info("Debited merchant account {} for chargeback amount: {}", 
                merchant.getId(), chargeback.getAmount());
    }

    private void applyChargebackFee(Merchant merchant, Chargeback chargeback) {
        BigDecimal fee = calculateChargebackFee(merchant);
        
        merchantAccountService.applyFee(
            merchant.getId(),
            fee,
            "Chargeback processing fee",
            chargeback.getId().toString()
        );
        
        chargeback.setFeeAmount(fee);
        
        log.info("Applied chargeback fee of {} to merchant {}", fee, merchant.getId());
    }

    private BigDecimal calculateChargebackFee(Merchant merchant) {
        if (merchant.isHighRiskStatus()) {
            return HIGH_RISK_CHARGEBACK_FEE;
        }
        return CHARGEBACK_FEE;
    }

    private void checkChargebackThreshold(Merchant merchant) {
        if (isMerchantHighRisk(merchant.getId())) {
            // Take action for excessive chargebacks
            log.error("CRITICAL: Merchant {} exceeds chargeback threshold", merchant.getId());
            
            // Suspend merchant if extremely high risk
            if (merchant.getChargebackRate() > 0.02) { // 2% threshold
                suspendMerchantAccount(merchant);
            }
            
            // Notify risk team
            notificationService.notifyRiskTeam(
                "EXCESSIVE_CHARGEBACKS",
                String.format("Merchant %s has chargeback rate of %.2f%%", 
                    merchant.getId(), merchant.getChargebackRate() * 100)
            );
        }
    }

    private void collectInitialEvidence(Chargeback chargeback) {
        // Automatically collect available evidence
        try {
            disputeEvidenceService.collectAutomaticEvidence(chargeback.getId(), chargeback.getOriginalTransactionId());
        } catch (Exception e) {
            log.error("Failed to collect initial evidence for chargeback: {}", chargeback.getId(), e);
        }
    }

    private void handleChargebackWon(Merchant merchant, Chargeback chargeback, ChargebackResolution resolution) {
        // Credit merchant account back
        merchantAccountService.creditAccount(
            merchant.getId(),
            chargeback.getAmount(),
            "Chargeback reversed - dispute won",
            chargeback.getId().toString()
        );
        
        // Refund chargeback fee (optional based on business rules)
        merchantAccountService.creditAccount(
            merchant.getId(),
            chargeback.getFeeAmount(),
            "Chargeback fee refunded",
            chargeback.getId().toString()
        );
        
        log.info("Chargeback won for merchant {}: amount {} refunded", 
                merchant.getId(), chargeback.getAmount());
    }

    private void handleChargebackLost(Merchant merchant, Chargeback chargeback, ChargebackResolution resolution) {
        // Funds already debited, just update status
        log.info("Chargeback lost for merchant {}: amount {}", 
                merchant.getId(), chargeback.getAmount());
        
        // Update merchant risk profile
        merchant.incrementChargebackLossCount();
        merchantRepository.save(merchant);
    }

    private void handlePartialResolution(Merchant merchant, Chargeback chargeback, ChargebackResolution resolution) {
        BigDecimal refundAmount = resolution.getPartialAmount();
        
        if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            merchantAccountService.creditAccount(
                merchant.getId(),
                refundAmount,
                "Partial chargeback reversal",
                chargeback.getId().toString()
            );
            
            log.info("Partial chargeback resolution for merchant {}: {} refunded of {}", 
                    merchant.getId(), refundAmount, chargeback.getAmount());
        }
    }

    private void suspendMerchantAccount(Merchant merchant) {
        merchant.setAccountStatus(Merchant.AccountStatus.SUSPENDED);
        merchant.setSuspendedAt(LocalDateTime.now());
        merchant.setSuspensionReason("Excessive chargeback rate");
        merchantRepository.save(merchant);
        
        // Publish suspension event
        kafkaTemplate.send("merchant-suspension-events", 
            MerchantSuspensionEvent.builder()
                .merchantId(merchant.getId().toString())
                .reason("EXCESSIVE_CHARGEBACKS")
                .chargebackRate(merchant.getChargebackRate())
                .timestamp(LocalDateTime.now())
                .build()
        );
        
        log.error("Merchant account suspended due to excessive chargebacks: {}", merchant.getId());
    }

    private void notifyMerchantOfChargeback(Merchant merchant, Chargeback chargeback) {
        notificationService.sendChargebackNotification(
            merchant.getContactEmail(),
            chargeback.getId(),
            chargeback.getAmount(),
            chargeback.getReasonDescription(),
            chargeback.getResponseDeadline()
        );
    }

    private void notifyMerchantOfResolution(Merchant merchant, Chargeback chargeback, ChargebackResolution resolution) {
        notificationService.sendChargebackResolutionNotification(
            merchant.getContactEmail(),
            chargeback.getId(),
            resolution.getDecision(),
            resolution.getDetails()
        );
    }

    private String mapReasonCodeToDescription(String reasonCode) {
        // Map card network reason codes to descriptions
        return switch (reasonCode) {
            case "10.1" -> "Fraud - Card Present Transaction";
            case "10.2" -> "Fraud - Card Not Present Transaction";
            case "10.3" -> "Other Fraud";
            case "10.4" -> "Fraud - Card Absent Environment";
            case "11.1" -> "Card Recovery Bulletin";
            case "11.2" -> "Declined Authorization";
            case "11.3" -> "No Authorization";
            case "12.1" -> "Late Presentment";
            case "12.2" -> "Incorrect Transaction Code";
            case "13.1" -> "Merchandise/Services Not Received";
            case "13.2" -> "Cancelled Recurring Transaction";
            case "13.3" -> "Not as Described or Defective";
            case "13.4" -> "Counterfeit Merchandise";
            case "13.5" -> "Misrepresentation";
            case "13.6" -> "Credit Not Processed";
            case "13.7" -> "Cancelled Merchandise/Services";
            default -> "Other Dispute Reason";
        };
    }

    private ChargebackStatus mapResolutionToStatus(ChargebackResolution.Decision decision) {
        return switch (decision) {
            case WON -> ChargebackStatus.WON;
            case LOST -> ChargebackStatus.LOST;
            case PARTIAL -> ChargebackStatus.PARTIAL;
            default -> ChargebackStatus.CLOSED;
        };
    }

    private boolean determineLiabilityShift(ChargebackInitiatedEvent event) {
        // Check if 3D Secure was used for liability shift
        return event.getMetadata() != null && 
               Boolean.TRUE.equals(event.getMetadata().get("3dsecure"));
    }

    private void validateDisputeEvidence(DisputeEvidenceRequest request) {
        if (request.getEvidenceDocuments() == null || request.getEvidenceDocuments().isEmpty()) {
            throw new IllegalArgumentException("Dispute evidence documents required");
        }
        
        if (request.getDisputeNarrative() == null || request.getDisputeNarrative().length() < 100) {
            throw new IllegalArgumentException("Detailed dispute narrative required (minimum 100 characters)");
        }
    }

    private void submitToCardNetwork(Chargeback chargeback, DisputeEvidence evidence) {
        // Implementation would submit to actual card network API
        log.info("Submitting dispute to {} for chargeback {}", 
                chargeback.getCardNetwork(), chargeback.getId());
    }

    private void publishChargebackEvent(Chargeback chargeback) {
        kafkaTemplate.send("chargeback-processed-events", 
            ChargebackProcessedEvent.builder()
                .chargebackId(chargeback.getId().toString())
                .merchantId(chargeback.getMerchantId().toString())
                .amount(chargeback.getAmount())
                .status(chargeback.getStatus().toString())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    private void updateChargebackMetrics(Merchant merchant, Chargeback chargeback) {
        metricsService.incrementCounter("chargeback.received",
            "merchant_id", merchant.getId().toString(),
            "reason_code", chargeback.getReasonCode(),
            "amount_range", getAmountRange(chargeback.getAmount())
        );
    }

    private void updateResolutionMetrics(Merchant merchant, ChargebackResolution resolution) {
        metricsService.incrementCounter("chargeback.resolved",
            "merchant_id", merchant.getId().toString(),
            "decision", resolution.getDecision().toString()
        );
    }

    private String getAmountRange(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("100")) < 0) return "0-100";
        if (amount.compareTo(new BigDecimal("500")) < 0) return "100-500";
        if (amount.compareTo(new BigDecimal("1000")) < 0) return "500-1000";
        return "1000+";
    }

    private void createChargebackProcessingAlert(ChargebackInitiatedEvent event, Exception error) {
        notificationService.sendCriticalAlert(
            "CHARGEBACK_PROCESSING_FAILURE",
            String.format("Failed to process chargeback for transaction %s: %s", 
                event.getOriginalTransactionId(), error.getMessage())
        );
    }
}