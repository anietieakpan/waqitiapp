package com.waqiti.dispute.service;

import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.entity.DisputePriority;
import com.waqiti.dispute.repository.DisputeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dispute Management Service
 *
 * Handles comprehensive dispute lifecycle management including:
 * - Dispute creation and validation
 * - Status transitions and workflows
 * - SLA tracking and escalation
 * - Dispute assignment and routing
 *
 * @author Waqiti Dispute Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeManagementService {

    private final DisputeRepository disputeRepository;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    private final DistributedIdempotencyService idempotencyService;

    @org.springframework.beans.factory.annotation.Value("${feign.client.config.payment-service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${feign.client.config.user-service.url:http://user-service:8081}")
    private String userServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${feign.client.config.notification-service.url:http://notification-service:8084}")
    private String notificationServiceUrl;

    /**
     * Retrieve dispute by ID
     */
    @Transactional(readOnly = true)
    public Optional<Dispute> getDisputeById(String disputeId) {
        log.debug("Retrieving dispute: {}", disputeId);
        return disputeRepository.findByDisputeId(disputeId);
    }

    /**
     * Get all disputes for a user
     */
    @Transactional(readOnly = true)
    public List<Dispute> getUserDisputes(String userId) {
        log.debug("Retrieving disputes for user: {}", userId);
        return disputeRepository.findByUserId(userId);
    }

    /**
     * Get disputes by status
     */
    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByStatus(String status) {
        log.debug("Retrieving disputes with status: {}", status);
        return disputeRepository.findByStatus(status);
    }

    /**
     * Update dispute status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Dispute updateDisputeStatus(String disputeId, DisputeStatus newStatus, String reason) {
        log.info("Updating dispute {} status to: {}", disputeId, newStatus);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        DisputeStatus oldStatus = dispute.getStatus();
        dispute.setStatus(newStatus);
        dispute.setLastUpdated(LocalDateTime.now());

        if (newStatus == DisputeStatus.RESOLVED || newStatus == DisputeStatus.CLOSED) {
            dispute.setResolvedAt(LocalDateTime.now());
            dispute.setResolutionReason(reason);
        }

        Dispute updated = disputeRepository.save(dispute);
        log.info("Dispute {} status updated: {} -> {}", disputeId, oldStatus, newStatus);

        return updated;
    }

    /**
     * Escalate dispute to higher tier
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Dispute escalateDispute(String disputeId, String escalationReason) {
        log.warn("Escalating dispute {}: {}", disputeId, escalationReason);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setPriority(DisputePriority.HIGH);
        dispute.setLastUpdated(LocalDateTime.now());

        Dispute updated = disputeRepository.save(dispute);
        log.info("Dispute {} escalated", disputeId);

        return updated;
    }

    /**
     * Get dispute count by status
     */
    @Transactional(readOnly = true)
    public long getDisputeCountByStatus(String status) {
        return disputeRepository.countByStatus(status);
    }

    /**
     * Check if user has active disputes
     */
    @Transactional(readOnly = true)
    public boolean hasActiveDisputes(String userId) {
        long activeCount = disputeRepository.countByUserIdAndStatus(userId, DisputeStatus.OPEN.name());
        return activeCount > 0;
    }

    // ==================== NEW METHODS FOR CONSUMER SUPPORT ====================

    /**
     * Freeze a transaction to prevent further processing during dispute investigation
     */
    @Transactional
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "paymentService", fallbackMethod = "freezeTransactionFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "paymentService")
    public void freezeTransaction(java.util.UUID transactionId, java.util.UUID userId, String reason, String eventId) {
        log.info("Freezing transaction {} for user {} - Reason: {}", transactionId, userId, reason);

        String idempotencyKey = "freeze-tx:" + transactionId;
        if (idempotencyService.checkAndMarkProcessed(idempotencyKey, "freezeTransaction", java.time.Duration.ofDays(7))) {
            log.warn("Transaction {} already frozen, skipping duplicate freeze", transactionId);
            return;
        }

        try {
            java.util.Map<String, Object> freezeRequest = new java.util.HashMap<>();
            freezeRequest.put("transactionId", transactionId.toString());
            freezeRequest.put("userId", userId.toString());
            freezeRequest.put("reason", reason);
            freezeRequest.put("freezeType", "DISPUTE");

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> request =
                new org.springframework.http.HttpEntity<>(freezeRequest, headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                paymentServiceUrl + "/api/v1/transactions/" + transactionId + "/freeze", request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Transaction {} successfully frozen", transactionId);
            }
        } catch (Exception e) {
            log.error("Error freezing transaction {}", transactionId, e);
            idempotencyService.removeProcessed(idempotencyKey);
            throw new RuntimeException("Failed to freeze transaction", e);
        }
    }

    private void freezeTransactionFallback(java.util.UUID transactionId, java.util.UUID userId, String reason, String eventId, Exception e) {
        log.error("Circuit breaker: Failed to freeze transaction {}", transactionId, e);
        sendOperationsAlert("CRITICAL: Failed to freeze disputed transaction " + transactionId);
    }

    /**
     * Block related transactions
     */
    @Transactional
    public void blockRelatedTransactions(java.util.UUID merchantId, java.util.UUID userId, java.util.UUID originalTransactionId) {
        log.info("Blocking related transactions for merchant {} and user {}", merchantId, userId);
        try {
            java.util.Map<String, Object> blockRequest = new java.util.HashMap<>();
            blockRequest.put("merchantId", merchantId != null ? merchantId.toString() : null);
            blockRequest.put("userId", userId.toString());
            blockRequest.put("originalTransactionId", originalTransactionId.toString());

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(paymentServiceUrl + "/api/v1/transactions/block-related",
                new org.springframework.http.HttpEntity<>(blockRequest, headers), String.class);
        } catch (Exception e) {
            log.warn("Non-critical: Failed to block related transactions", e);
        }
    }

    /**
     * Issue provisional credit per Regulation E
     */
    @Transactional
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "userService", fallbackMethod = "issueProvisionalCreditFallback")
    public String issueProvisionalCredit(java.util.UUID userId, java.math.BigDecimal amount, String disputeId, java.util.UUID transactionId) {
        log.info("Issuing provisional credit of {} to user {} for dispute {}", amount, userId, disputeId);

        String idempotencyKey = "provisional-credit:" + disputeId;
        if (idempotencyService.checkAndMarkProcessed(idempotencyKey, "issueProvisionalCredit", java.time.Duration.ofDays(90))) {
            log.warn("Provisional credit already issued for dispute {}", disputeId);
            return "ALREADY_ISSUED";
        }

        try {
            java.util.Map<String, Object> creditRequest = new java.util.HashMap<>();
            creditRequest.put("userId", userId.toString());
            creditRequest.put("amount", amount);
            creditRequest.put("reason", "DISPUTE_PROVISIONAL_CREDIT");
            creditRequest.put("disputeId", disputeId);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                userServiceUrl + "/api/v1/users/" + userId + "/credits",
                new org.springframework.http.HttpEntity<>(creditRequest, headers), java.util.Map.class);

            String creditId = (String) response.getBody().get("creditId");
            log.info("Provisional credit {} issued for dispute {}", creditId, disputeId);
            return creditId;
        } catch (Exception e) {
            log.error("Failed to issue provisional credit for dispute {}", disputeId, e);
            idempotencyService.removeProcessed(idempotencyKey);
            throw new RuntimeException("Failed to issue provisional credit", e);
        }
    }

    private String issueProvisionalCreditFallback(java.util.UUID userId, java.math.BigDecimal amount, String disputeId,
                                                   java.util.UUID transactionId, Exception e) {
        log.error("CRITICAL: Provisional credit failed for dispute {}", disputeId, e);
        sendOperationsAlert("URGENT: Provisional credit failed for dispute " + disputeId + ". Amount: $" + amount);
        return "FAILED_MANUAL_INTERVENTION_REQUIRED";
    }

    /**
     * Schedule provisional credit decision
     */
    @Transactional
    public void scheduleProvisionalCreditDecision(String disputeId, LocalDateTime deadline) {
        log.info("Scheduling provisional credit decision for dispute {} by {}", disputeId, deadline);
        disputeRepository.findByDisputeId(disputeId).ifPresent(dispute -> {
            dispute.setProvisionalCreditDecisionDeadline(deadline);
            disputeRepository.save(dispute);
        });
    }

    /**
     * Create manual intervention record
     */
    @Transactional
    public void createManualInterventionRecord(Dispute dispute) {
        log.warn("Creating manual intervention record for dispute: {}", dispute.getId());
        dispute.setRequiresManualIntervention(true);
        dispute.setPriority(DisputePriority.URGENT);
        disputeRepository.save(dispute);
        sendOperationsAlert("Manual intervention required for dispute: " + dispute.getId());
    }

    /**
     * Send operations alert
     */
    private void sendOperationsAlert(String message) {
        try {
            java.util.Map<String, Object> alertRequest = new java.util.HashMap<>();
            alertRequest.put("type", "OPERATIONS_ALERT");
            alertRequest.put("severity", "HIGH");
            alertRequest.put("message", message);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(notificationServiceUrl + "/api/v1/alerts/operations",
                new org.springframework.http.HttpEntity<>(alertRequest, headers), String.class);
        } catch (Exception e) {
            log.error("OPERATIONS_ALERT: {}", message);
        }
    }
}
