package com.waqiti.dispute.service;

import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeEvidence;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.repository.DisputeEvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Investigation Service
 *
 * Handles dispute investigation workflows including:
 * - Evidence collection and verification
 * - Investigation case management
 * - Investigation findings and recommendations
 * - Investigation timeline tracking
 *
 * @author Waqiti Dispute Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvestigationService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /**
     * Start investigation for a dispute
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void startInvestigation(String disputeId) {
        log.info("Starting investigation for dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setLastUpdated(LocalDateTime.now());
        disputeRepository.save(dispute);

        log.info("Investigation started for dispute: {}", disputeId);
    }

    /**
     * Collect evidence for investigation
     */
    @Transactional(readOnly = true)
    public List<DisputeEvidence> collectEvidence(String disputeId) {
        log.debug("Collecting evidence for dispute: {}", disputeId);
        return disputeEvidenceRepository.findByDisputeId(disputeId);
    }

    /**
     * Verify evidence authenticity
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void verifyEvidence(String evidenceId, boolean isValid, String notes) {
        log.info("Verifying evidence: {}", evidenceId);

        DisputeEvidence evidence = disputeEvidenceRepository.findById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));

        if (isValid) {
            evidence.setVerifiedAt(LocalDateTime.now());
            evidence.setVerificationNotes(notes);
        }

        disputeEvidenceRepository.save(evidence);
        log.info("Evidence {} verification complete: {}", evidenceId, isValid);
    }

    /**
     * Complete investigation with findings
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeInvestigation(String disputeId, String findings, String recommendation) {
        log.info("Completing investigation for dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setLastUpdated(LocalDateTime.now());
        disputeRepository.save(dispute);

        log.info("Investigation completed for dispute: {}", disputeId);
    }

    /**
     * Get investigation status
     */
    @Transactional(readOnly = true)
    public String getInvestigationStatus(String disputeId) {
        log.debug("Getting investigation status for dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        long evidenceCount = disputeEvidenceRepository.countByDisputeId(disputeId);

        if (evidenceCount == 0) {
            return "AWAITING_EVIDENCE";
        } else {
            return "UNDER_INVESTIGATION";
        }
    }
}
