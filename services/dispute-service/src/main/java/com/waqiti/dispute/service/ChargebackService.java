package com.waqiti.dispute.service;

import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.repository.DisputeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Chargeback Service
 *
 * Handles chargeback processing including:
 * - Chargeback initiation
 * - Chargeback response management
 * - Chargeback resolution tracking
 *
 * @author Waqiti Dispute Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChargebackService {

    private final DisputeRepository disputeRepository;

    /**
     * Process chargeback for dispute
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processChargeback(String disputeId, String chargebackCode, String reason) {
        log.info("Processing chargeback for dispute {}: code={}", disputeId, chargebackCode);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setChargebackCode(chargebackCode);
        dispute.setChargebackReason(reason);
        dispute.setLastUpdated(LocalDateTime.now());

        disputeRepository.save(dispute);

        log.info("Chargeback processed for dispute: {}", disputeId);
    }

    /**
     * Submit chargeback response
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submitChargebackResponse(String disputeId, String response) {
        log.info("Submitting chargeback response for dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setLastUpdated(LocalDateTime.now());
        disputeRepository.save(dispute);

        log.info("Chargeback response submitted for dispute: {}", disputeId);
    }
}
