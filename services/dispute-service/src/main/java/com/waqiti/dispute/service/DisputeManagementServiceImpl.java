package com.waqiti.dispute.service;

import com.waqiti.common.events.DisputeEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.entity.DisputePriority;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.service.DistributedIdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

/**
 * Dispute Management Service Implementation
 * Provides comprehensive dispute lifecycle management including:
 * - Transaction freezing and fund locking
 * - Provisional credit issuance
 * - Manual intervention handling
 * - Related transaction blocking
 *
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeManagementServiceImpl implements DisputeManagementService {

    private final DisputeRepository disputeRepository;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DistributedIdempotencyService idempotencyService;

    @Value("${feign.client.config.payment-service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    @Value("${feign.client.config.user-service.url:http://user-service:8081}")
    private String userServiceUrl;

    @Value("${feign.client.config.notification-service.url:http://notification-service:8084}")
    private String notificationServiceUrl;

    /**
     * Freeze a transaction to prevent further processing
     * Critical for dispute investigation - prevents double spending
     */
    @Override
    @Transactional
    @CircuitBreaker(name = "paymentService", fallbackMethod = "freezeTransactionFallback")
    @Retry(name = "paymentService")
    public void freezeTransaction(UUID transactionId, UUID userId, String reason, String eventId) {
        log.info("Freezing transaction {} for user {} - Reason: {}", transactionId, userId, reason);

        // Idempotency check
        String idempotencyKey = "freeze-tx:" + transactionId;
        if (idempotencyService.checkAndMarkProcessed(idempotencyKey, "freezeTransaction", Duration.ofDays(7))) {
            log.warn("Transaction {} already frozen, skipping duplicate freeze", transactionId);
            return;
        }

        try {
            // Call payment service to freeze transaction
            Map<String, Object> freezeRequest = new HashMap<>();
            freezeRequest.put("transactionId", transactionId.toString());
            freezeRequest.put("userId", userId.toString());
            freezeRequest.put("reason", reason);
            freezeRequest.put("freezeType", "DISPUTE");
            freezeRequest.put("eventId", eventId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(freezeRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                paymentServiceUrl + "/api/v1/transactions/" + transactionId + "/freeze",
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Transaction {} successfully frozen", transactionId);

                // Publish event
                kafkaTemplate.send("dispute.transaction.frozen", DisputeEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .transactionId(transactionId)
                    .userId(userId)
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build());
            } else {
                log.error("Failed to freeze transaction {}: HTTP {}", transactionId, response.getStatusCode());
                throw new RuntimeException("Transaction freeze failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error freezing transaction {}", transactionId, e);
            // Remove idempotency record to allow retry
            idempotencyService.removeProcessed(idempotencyKey);
            throw new RuntimeException("Failed to freeze transaction", e);
        }
    }

    /**
     * Fallback method for freezeTransaction when payment service is unavailable
     */
    private void freezeTransactionFallback(UUID transactionId, UUID userId, String reason, String eventId, Exception e) {
        log.error("Circuit breaker activated for freezeTransaction - Payment service unavailable", e);

        // Create manual intervention record
        createManualInterventionRecord(Map.of(
            "action", "FREEZE_TRANSACTION",
            "transactionId", transactionId.toString(),
            "userId", userId.toString(),
            "reason", reason,
            "error", e.getMessage()
        ));

        // Send alert to operations team
        sendOperationsAlert("CRITICAL: Failed to freeze disputed transaction " + transactionId +
            ". Manual intervention required immediately.");
    }

    /**
     * Block related transactions to prevent circumvention of dispute freeze
     */
    @Override
    @Transactional
    @CircuitBreaker(name = "paymentService", fallbackMethod = "blockRelatedTransactionsFallback")
    public void blockRelatedTransactions(UUID merchantId, UUID userId, UUID originalTransactionId) {
        log.info("Blocking related transactions for merchant {} and user {}", merchantId, userId);

        try {
            Map<String, Object> blockRequest = new HashMap<>();
            blockRequest.put("merchantId", merchantId != null ? merchantId.toString() : null);
            blockRequest.put("userId", userId.toString());
            blockRequest.put("originalTransactionId", originalTransactionId.toString());
            blockRequest.put("blockType", "DISPUTE_PREVENTION");
            blockRequest.put("duration", "24h"); // 24-hour block while investigating

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(blockRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                paymentServiceUrl + "/api/v1/transactions/block-related",
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Related transactions blocked for merchant {} and user {}", merchantId, userId);
            }

        } catch (Exception e) {
            log.warn("Non-critical: Failed to block related transactions", e);
            // Non-blocking - log but don't fail dispute creation
        }
    }

    private void blockRelatedTransactionsFallback(UUID merchantId, UUID userId, UUID originalTransactionId, Exception e) {
        log.warn("Circuit breaker fallback: Could not block related transactions", e);
        // Non-critical operation - just log
    }

    /**
     * Issue provisional credit to customer per Regulation E requirements
     * Must be issued within 10 business days of dispute notification
     */
    @Override
    @Transactional
    @CircuitBreaker(name = "userService", fallbackMethod = "issueProvisionalCreditFallback")
    @Retry(name = "userService")
    public String issueProvisionalCredit(UUID userId, BigDecimal amount, String disputeId, UUID transactionId) {
        log.info("Issuing provisional credit of {} to user {} for dispute {}", amount, userId, disputeId);

        // Idempotency check - prevent duplicate credits
        String idempotencyKey = "provisional-credit:" + disputeId;
        if (idempotencyService.checkAndMarkProcessed(idempotencyKey, "issueProvisionalCredit", Duration.ofDays(90))) {
            log.warn("Provisional credit already issued for dispute {}", disputeId);
            // Retrieve existing credit ID from database
            return disputeRepository.findByDisputeId(disputeId)
                .map(Dispute::getProvisionalCreditId)
                .orElse("ALREADY_ISSUED");
        }

        try {
            Map<String, Object> creditRequest = new HashMap<>();
            creditRequest.put("userId", userId.toString());
            creditRequest.put("amount", amount);
            creditRequest.put("currency", "USD");
            creditRequest.put("reason", "DISPUTE_PROVISIONAL_CREDIT");
            creditRequest.put("disputeId", disputeId);
            creditRequest.put("transactionId", transactionId.toString());
            creditRequest.put("type", "PROVISIONAL");
            creditRequest.put("reversible", true);
            creditRequest.put("idempotencyKey", idempotencyKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(creditRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                userServiceUrl + "/api/v1/users/" + userId + "/credits",
                request,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String creditId = (String) response.getBody().get("creditId");
                log.info("Provisional credit {} issued successfully for dispute {}", creditId, disputeId);

                // Publish event
                kafkaTemplate.send("dispute.provisional-credit.issued", DisputeEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .disputeId(disputeId)
                    .userId(userId)
                    .amount(amount)
                    .creditId(creditId)
                    .timestamp(LocalDateTime.now())
                    .build());

                return creditId;
            } else {
                throw new RuntimeException("Provisional credit issuance failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Critical error issuing provisional credit for dispute {}", disputeId, e);
            // Remove idempotency to allow retry
            idempotencyService.removeProcessed(idempotencyKey);
            throw new RuntimeException("Failed to issue provisional credit", e);
        }
    }

    private String issueProvisionalCreditFallback(UUID userId, BigDecimal amount, String disputeId, UUID transactionId, Exception e) {
        log.error("CRITICAL: Circuit breaker activated for provisional credit - User service unavailable", e);

        // Mark dispute as requiring manual credit issuance
        disputeRepository.findByDisputeId(disputeId).ifPresent(dispute -> {
            dispute.setRequiresManualIntervention(true);
            dispute.setManualInterventionReason("PROVISIONAL_CREDIT_FAILED: " + e.getMessage());
            disputeRepository.save(dispute);
        });

        sendOperationsAlert("URGENT: Provisional credit failed for dispute " + disputeId +
            ". Amount: $" + amount + ". REGULATORY COMPLIANCE AT RISK. Manual issuance required.");

        return "FAILED_MANUAL_INTERVENTION_REQUIRED";
    }

    /**
     * Schedule provisional credit decision
     */
    @Override
    @Transactional
    public void scheduleProvisionalCreditDecision(String disputeId, LocalDateTime deadline) {
        log.info("Scheduling provisional credit decision for dispute {} by {}", disputeId, deadline);

        disputeRepository.findByDisputeId(disputeId).ifPresent(dispute -> {
            dispute.setProvisionalCreditDecisionDeadline(deadline);
            dispute.setRequiresProvisionalCreditDecision(true);
            disputeRepository.save(dispute);
        });

        // Schedule task
        kafkaTemplate.send("dispute.provisional-credit.decision-scheduled", DisputeEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .disputeId(disputeId)
            .deadline(deadline)
            .timestamp(LocalDateTime.now())
            .build());
    }

    /**
     * Create manual intervention record for operations team
     */
    @Override
    @Transactional
    public void createManualInterventionRecord(Map<String, Object> interventionDetails) {
        log.warn("Creating manual intervention record: {}", interventionDetails);

        String disputeId = (String) interventionDetails.getOrDefault("disputeId", "UNKNOWN");

        // Update dispute if exists
        if (!disputeId.equals("UNKNOWN")) {
            disputeRepository.findByDisputeId(disputeId).ifPresent(dispute -> {
                dispute.setRequiresManualIntervention(true);
                dispute.setManualInterventionReason(interventionDetails.toString());
                dispute.setPriority(DisputePriority.URGENT);
                disputeRepository.save(dispute);
            });
        }

        // Publish event for operations dashboard
        kafkaTemplate.send("dispute.manual-intervention.required", interventionDetails);

        // Send notification
        sendOperationsAlert("Manual intervention required: " + interventionDetails);
    }

    /**
     * Send operations alert
     */
    private void sendOperationsAlert(String message) {
        try {
            Map<String, Object> alertRequest = new HashMap<>();
            alertRequest.put("type", "OPERATIONS_ALERT");
            alertRequest.put("severity", "HIGH");
            alertRequest.put("message", message);
            alertRequest.put("timestamp", LocalDateTime.now());
            alertRequest.put("service", "dispute-service");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(alertRequest, headers);

            restTemplate.postForEntity(
                notificationServiceUrl + "/api/v1/alerts/operations",
                request,
                String.class
            );

        } catch (Exception e) {
            log.error("Failed to send operations alert", e);
            // Fallback: log to error stream for monitoring
            log.error("OPERATIONS_ALERT: {}", message);
        }
    }

    /**
     * Get dispute by ID
     */
    @Override
    @Transactional(readOnly = true)
    public Dispute getDisputeById(String disputeId) {
        return disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
    }

    /**
     * Update dispute status
     */
    @Override
    @Transactional
    public void updateDisputeStatus(String disputeId, DisputeStatus newStatus, String reason) {
        log.info("Updating dispute {} status to {} - Reason: {}", disputeId, newStatus, reason);

        Dispute dispute = getDisputeById(disputeId);
        DisputeStatus oldStatus = dispute.getStatus();

        dispute.setStatus(newStatus);
        dispute.setStatusUpdatedAt(LocalDateTime.now());
        dispute.setStatusUpdateReason(reason);

        disputeRepository.save(dispute);

        // Publish status change event
        kafkaTemplate.send("dispute.status.changed", DisputeEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .disputeId(disputeId)
            .oldStatus(oldStatus.name())
            .newStatus(newStatus.name())
            .reason(reason)
            .timestamp(LocalDateTime.now())
            .build());

        log.info("Dispute {} status updated from {} to {}", disputeId, oldStatus, newStatus);
    }
}
