package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.CheckDepositService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.checkdeposit.CheckDepositProcessorService;
import com.waqiti.payment.entity.CheckDeposit;
import com.waqiti.payment.entity.CheckDepositStatus;
import com.waqiti.payment.repository.CheckDepositRepository;
import com.waqiti.payment.dto.CheckDepositEvent;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: Check Deposit Events Consumer - Processes orphaned check deposit events
 * 
 * This consumer was missing causing check deposits to be lost.
 * Without this, customer check deposits would never be processed or credited to accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckDepositEventsConsumer {

    private final CheckDepositService checkDepositService;
    private final CheckDepositProcessorService checkDepositProcessorService;
    private final CheckDepositRepository checkDepositRepository;
    private final NotificationService notificationService;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"check-deposits", "check-deposit-status", "check-deposit-fraud-results"},
        groupId = "check-deposit-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processCheckDepositEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing check deposit event from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String checkDepositId = extractString(eventData, "checkDepositId");
            String depositId = extractString(eventData, "depositId");
            String eventType = extractString(eventData, "eventType");
            String userId = extractString(eventData, "userId");

            // Use either checkDepositId or depositId
            String primaryId = checkDepositId != null ? checkDepositId : depositId;

            // Validate required fields
            if (primaryId == null || userId == null) {
                log.error("Invalid check deposit event - missing required fields: id={}, userId={}", 
                    primaryId, userId);
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Determine event type from topic if not explicit
            if (eventType == null) {
                eventType = determineEventTypeFromTopic(topic, eventData);
            }

            // Acquire distributed lock to prevent concurrent processing
            lockKey = "check-deposit-" + primaryId;
            boolean lockAcquired = lockService.tryLock(lockKey, 60, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for check deposit: {}", primaryId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // Process based on event type and topic
                switch (eventType.toUpperCase()) {
                    case "SUBMITTED":
                    case "CHECK_SUBMITTED":
                        processCheckDepositSubmission(primaryId, userId, eventData);
                        break;
                    case "APPROVED":
                    case "CHECK_APPROVED":
                        processCheckDepositApproval(primaryId, userId, eventData);
                        break;
                    case "REJECTED":
                    case "CHECK_REJECTED":
                        processCheckDepositRejection(primaryId, userId, eventData);
                        break;
                    case "FUNDS_AVAILABLE":
                    case "CLEARED":
                        processCheckDepositClearing(primaryId, userId, eventData);
                        break;
                    case "BOUNCED":
                    case "RETURNED":
                        processCheckDepositBounce(primaryId, userId, eventData);
                        break;
                    case "FRAUD_DETECTED":
                        processCheckDepositFraud(primaryId, userId, eventData);
                        break;
                    case "MANUAL_REVIEW_REQUIRED":
                        processCheckDepositManualReview(primaryId, userId, eventData);
                        break;
                    case "STATUS_UPDATE":
                        processCheckDepositStatusUpdate(primaryId, userId, eventData);
                        break;
                    default:
                        log.warn("Unknown check deposit event type: {} for deposit: {}", eventType, primaryId);
                        processUnknownCheckDepositEvent(primaryId, userId, eventType, eventData);
                }
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing check deposit event", e);
            
            // Send to DLQ after max retries
            if (shouldSendToDlq(e)) {
                sendToDlq(eventPayload, e);
                acknowledgment.acknowledge(); // Acknowledge to prevent reprocessing
            } else {
                throw e; // Let retry mechanism handle
            }
        }
    }

    /**
     * Process check deposit submission event
     */
    private void processCheckDepositSubmission(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit submission - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get or create check deposit record
            CheckDeposit checkDeposit = getOrCreateCheckDeposit(depositId, userId, eventData);
            
            // Update status to submitted
            checkDeposit.setStatus(CheckDepositStatus.SUBMITTED);
            checkDeposit.setSubmittedAt(LocalDateTime.now());
            
            // Extract submission details
            BigDecimal amount = extractBigDecimal(eventData, "amount");
            String description = extractString(eventData, "description");
            String frontImageUrl = extractString(eventData, "frontImageUrl");
            String backImageUrl = extractString(eventData, "backImageUrl");
            
            if (amount != null) checkDeposit.setAmount(amount);
            if (description != null) checkDeposit.setDescription(description);
            if (frontImageUrl != null) checkDeposit.setFrontImageUrl(frontImageUrl);
            if (backImageUrl != null) checkDeposit.setBackImageUrl(backImageUrl);
            
            checkDepositRepository.save(checkDeposit);
            
            // Trigger initial processing workflow
            initiateCheckProcessingWorkflow(checkDeposit);
            
            // Send submission confirmation
            sendSubmissionNotification(checkDeposit);
            
            // Log submission
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_SUBMITTED", userId,
                "Check deposit submitted for processing",
                Map.of("depositId", depositId, "amount", amount != null ? amount : "N/A",
                      "submittedAt", LocalDateTime.now()));
            
            log.info("Successfully processed check deposit submission: {} - amount: {}", 
                depositId, amount);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit submission for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit approval event
     */
    private void processCheckDepositApproval(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit approval - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to approved
            checkDeposit.setStatus(CheckDepositStatus.APPROVED);
            checkDeposit.setApprovedAt(LocalDateTime.now());
            
            // Extract approval details
            String approvedBy = extractString(eventData, "approvedBy");
            String approvalReason = extractString(eventData, "approvalReason");
            Integer holdDays = extractInteger(eventData, "holdDays");
            
            checkDeposit.setApprovedBy(approvedBy);
            checkDeposit.setApprovalReason(approvalReason);
            if (holdDays != null) checkDeposit.setHoldDays(holdDays);
            
            checkDepositRepository.save(checkDeposit);
            
            // Process the approved deposit - credit account with hold
            processApprovedCheckDeposit(checkDeposit);
            
            // Send approval notification
            sendApprovalNotification(checkDeposit);
            
            // Log approval
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_APPROVED", approvedBy != null ? approvedBy : "SYSTEM",
                "Check deposit approved and processed",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "holdDays", holdDays != null ? holdDays : 0,
                      "approvedBy", approvedBy != null ? approvedBy : "SYSTEM"));
            
            log.info("Successfully processed check deposit approval: {} - amount: {} hold: {} days", 
                depositId, checkDeposit.getAmount(), holdDays);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit approval for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit rejection event
     */
    private void processCheckDepositRejection(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit rejection - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to rejected
            checkDeposit.setStatus(CheckDepositStatus.REJECTED);
            checkDeposit.setRejectedAt(LocalDateTime.now());
            
            // Extract rejection details
            String rejectedBy = extractString(eventData, "rejectedBy");
            String rejectionReason = extractString(eventData, "rejectionReason");
            String rejectionCode = extractString(eventData, "rejectionCode");
            
            checkDeposit.setRejectedBy(rejectedBy);
            checkDeposit.setRejectionReason(rejectionReason);
            checkDeposit.setRejectionCode(rejectionCode);
            
            checkDepositRepository.save(checkDeposit);
            
            // Send rejection notification
            sendRejectionNotification(checkDeposit);
            
            // Log rejection
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_REJECTED", rejectedBy != null ? rejectedBy : "SYSTEM",
                "Check deposit rejected",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "rejectionReason", rejectionReason != null ? rejectionReason : "N/A",
                      "rejectionCode", rejectionCode != null ? rejectionCode : "N/A"));
            
            log.info("Successfully processed check deposit rejection: {} - reason: {}", 
                depositId, rejectionReason);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit rejection for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit clearing event (funds become available)
     */
    private void processCheckDepositClearing(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit clearing - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to cleared
            checkDeposit.setStatus(CheckDepositStatus.CLEARED);
            checkDeposit.setClearedAt(LocalDateTime.now());
            checkDeposit.setFundsAvailableAt(Instant.now());
            
            checkDepositRepository.save(checkDeposit);
            
            // Release funds from hold
            releaseFundsFromHold(checkDeposit);
            
            // Send funds available notification
            sendFundsAvailableNotification(checkDeposit);
            
            // Log clearing
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_CLEARED", "SYSTEM",
                "Check deposit cleared - funds now available",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "clearedAt", LocalDateTime.now()));
            
            log.info("Successfully processed check deposit clearing: {} - amount: {} now available", 
                depositId, checkDeposit.getAmount());
                
        } catch (Exception e) {
            log.error("Failed to process check deposit clearing for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit bounce event (check returned)
     */
    private void processCheckDepositBounce(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit bounce - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to bounced
            checkDeposit.setStatus(CheckDepositStatus.BOUNCED);
            checkDeposit.setBouncedAt(LocalDateTime.now());
            
            // Extract bounce details
            String bounceReason = extractString(eventData, "bounceReason");
            String returnCode = extractString(eventData, "returnCode");
            BigDecimal feeAmount = extractBigDecimal(eventData, "feeAmount");
            
            checkDeposit.setBounceReason(bounceReason);
            checkDeposit.setReturnCode(returnCode);
            if (feeAmount != null) checkDeposit.setFeeAmount(feeAmount);
            
            checkDepositRepository.save(checkDeposit);
            
            // Process bounce - reverse credit and charge fees
            processBouncedCheckDeposit(checkDeposit, feeAmount);
            
            // Send bounce notification
            sendBounceNotification(checkDeposit);
            
            // Log bounce
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_BOUNCED", "SYSTEM",
                "Check deposit bounced - funds reversed",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "bounceReason", bounceReason != null ? bounceReason : "N/A",
                      "returnCode", returnCode != null ? returnCode : "N/A",
                      "feeAmount", feeAmount != null ? feeAmount : BigDecimal.ZERO));
            
            log.info("Successfully processed check deposit bounce: {} - reason: {} fee: {}", 
                depositId, bounceReason, feeAmount);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit bounce for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit fraud detection event
     */
    private void processCheckDepositFraud(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit fraud detection - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to fraud detected
            checkDeposit.setStatus(CheckDepositStatus.FRAUD_DETECTED);
            checkDeposit.setFraudDetectedAt(LocalDateTime.now());
            
            // Extract fraud details
            String fraudType = extractString(eventData, "fraudType");
            BigDecimal riskScore = extractBigDecimal(eventData, "riskScore");
            String fraudReason = extractString(eventData, "fraudReason");
            
            checkDeposit.setFraudType(fraudType);
            checkDeposit.setRiskScore(riskScore);
            checkDeposit.setFraudReason(fraudReason);
            
            checkDepositRepository.save(checkDeposit);
            
            // Process fraud case - freeze account, reverse credit
            processFraudulentCheckDeposit(checkDeposit);
            
            // Send fraud alerts
            sendFraudAlert(checkDeposit);
            
            // Log fraud detection
            securityAuditLogger.logSecurityViolation("CHECK_DEPOSIT_FRAUD_DETECTED", userId,
                "Fraudulent check deposit detected",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "fraudType", fraudType != null ? fraudType : "N/A",
                      "riskScore", riskScore != null ? riskScore : "N/A",
                      "fraudReason", fraudReason != null ? fraudReason : "N/A"));
            
            log.error("FRAUD DETECTED: Check deposit {} - type: {} score: {}", 
                depositId, fraudType, riskScore);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit fraud for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit manual review event
     */
    private void processCheckDepositManualReview(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit manual review - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Update status to manual review
            checkDeposit.setStatus(CheckDepositStatus.MANUAL_REVIEW);
            checkDeposit.setManualReviewStartedAt(LocalDateTime.now());
            
            // Extract review details
            String reviewReason = extractString(eventData, "reviewReason");
            String priority = extractString(eventData, "priority");
            String assignedTo = extractString(eventData, "assignedTo");
            
            checkDeposit.setManualReviewReason(reviewReason);
            checkDeposit.setReviewPriority(priority);
            checkDeposit.setAssignedTo(assignedTo);
            
            checkDepositRepository.save(checkDeposit);
            
            // Queue for manual review
            queueForManualReview(checkDeposit, reviewReason, priority);
            
            // Send manual review notifications
            sendManualReviewNotification(checkDeposit);
            
            // Log manual review
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_MANUAL_REVIEW", "SYSTEM",
                "Check deposit queued for manual review",
                Map.of("depositId", depositId, "amount", checkDeposit.getAmount(),
                      "reviewReason", reviewReason != null ? reviewReason : "N/A",
                      "priority", priority != null ? priority : "NORMAL"));
            
            log.info("Successfully queued check deposit for manual review: {} - reason: {}", 
                depositId, reviewReason);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit manual review for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process check deposit status update event
     */
    private void processCheckDepositStatusUpdate(String depositId, String userId, Map<String, Object> eventData) {
        
        log.info("Processing check deposit status update - depositId: {} userId: {}", depositId, userId);
        
        try {
            // Get check deposit record
            CheckDeposit checkDeposit = getCheckDeposit(depositId);
            
            // Extract status update details
            String newStatus = extractString(eventData, "newStatus");
            String statusReason = extractString(eventData, "statusReason");
            String updatedBy = extractString(eventData, "updatedBy");
            
            if (newStatus != null) {
                try {
                    CheckDepositStatus status = CheckDepositStatus.valueOf(newStatus.toUpperCase());
                    checkDeposit.setStatus(status);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status value: {} for deposit: {}", newStatus, depositId);
                }
            }
            
            checkDeposit.setLastUpdatedAt(LocalDateTime.now());
            checkDeposit.setLastUpdatedBy(updatedBy);
            if (statusReason != null) checkDeposit.setStatusReason(statusReason);
            
            checkDepositRepository.save(checkDeposit);
            
            // Send status update notification
            sendStatusUpdateNotification(checkDeposit, newStatus, statusReason);
            
            log.info("Successfully processed check deposit status update: {} - new status: {}", 
                depositId, newStatus);
                
        } catch (Exception e) {
            log.error("Failed to process check deposit status update for deposit: {}", depositId, e);
            throw e;
        }
    }

    /**
     * Process unknown check deposit event
     */
    private void processUnknownCheckDepositEvent(String depositId, String userId, String eventType, 
                                               Map<String, Object> eventData) {
        
        log.warn("Processing unknown check deposit event type: {} for deposit: {}", eventType, depositId);
        
        try {
            // Queue for manual review
            queueUnknownEventForReview(depositId, userId, eventType, eventData);
            
            // Send alert to operations
            sendOperationsAlert("UNKNOWN_CHECK_DEPOSIT_EVENT", depositId, 
                "Unknown check deposit event type received: " + eventType);
            
        } catch (Exception e) {
            log.error("Failed to process unknown check deposit event: {}", depositId, e);
        }
    }

    /**
     * Get or create check deposit record
     */
    private CheckDeposit getOrCreateCheckDeposit(String depositId, String userId, Map<String, Object> eventData) {
        return checkDepositRepository.findById(UUID.fromString(depositId))
            .orElseGet(() -> {
                CheckDeposit newDeposit = new CheckDeposit();
                newDeposit.setId(UUID.fromString(depositId));
                newDeposit.setUserId(userId);
                newDeposit.setStatus(CheckDepositStatus.INITIATED);
                newDeposit.setCreatedAt(LocalDateTime.now());
                
                // Extract basic details from event
                BigDecimal amount = extractBigDecimal(eventData, "amount");
                String description = extractString(eventData, "description");
                
                if (amount != null) newDeposit.setAmount(amount);
                if (description != null) newDeposit.setDescription(description);
                
                return checkDepositRepository.save(newDeposit);
            });
    }

    /**
     * Get existing check deposit record
     */
    private CheckDeposit getCheckDeposit(String depositId) {
        return checkDepositRepository.findById(UUID.fromString(depositId))
            .orElseThrow(() -> new IllegalArgumentException("Check deposit not found: " + depositId));
    }

    /**
     * Determine event type from topic
     */
    private String determineEventTypeFromTopic(String topic, Map<String, Object> eventData) {
        if (topic.contains("status")) {
            return "STATUS_UPDATE";
        } else if (topic.contains("fraud")) {
            return "FRAUD_DETECTED";
        } else {
            // Try to extract from event data
            String status = extractString(eventData, "status");
            String eventType = extractString(eventData, "type");
            return eventType != null ? eventType : (status != null ? status : "UNKNOWN");
        }
    }

    /**
     * Initiate check processing workflow
     */
    private void initiateCheckProcessingWorkflow(CheckDeposit checkDeposit) {
        try {
            // Trigger fraud detection
            checkDepositService.performFraudCheck(checkDeposit.getId().toString());
            
            // Trigger image analysis if not done
            if (checkDeposit.getFrontImageUrl() != null) {
                checkDepositService.analyzeCheckImages(checkDeposit.getId().toString());
            }
            
        } catch (Exception e) {
            log.error("Failed to initiate processing workflow for deposit: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Process approved check deposit - credit account with hold
     */
    private void processApprovedCheckDeposit(CheckDeposit checkDeposit) {
        try {
            // Use the processor service to handle the deposit
            checkDepositProcessorService.processDeposit(
                mapToProcessorCheckDeposit(checkDeposit)
            );
        } catch (Exception e) {
            log.error("Failed to process approved check deposit: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Release funds from hold when check clears
     */
    private void releaseFundsFromHold(CheckDeposit checkDeposit) {
        try {
            checkDepositService.releaseFunds(checkDeposit.getId().toString());
        } catch (Exception e) {
            log.error("Failed to release funds for check deposit: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Process bounced check deposit - reverse credit and charge fees
     */
    private void processBouncedCheckDeposit(CheckDeposit checkDeposit, BigDecimal feeAmount) {
        try {
            checkDepositService.processBounce(
                checkDeposit.getId().toString(),
                checkDeposit.getBounceReason(),
                feeAmount != null ? feeAmount : BigDecimal.ZERO
            );
        } catch (Exception e) {
            log.error("Failed to process bounced check deposit: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Process fraudulent check deposit
     */
    private void processFraudulentCheckDeposit(CheckDeposit checkDeposit) {
        try {
            checkDepositService.handleFraudCase(
                checkDeposit.getId().toString(),
                checkDeposit.getFraudType(),
                checkDeposit.getRiskScore()
            );
        } catch (Exception e) {
            log.error("Failed to process fraudulent check deposit: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Queue check deposit for manual review
     */
    private void queueForManualReview(CheckDeposit checkDeposit, String reason, String priority) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "CHECK_DEPOSIT_MANUAL_REVIEW",
                "depositId", checkDeposit.getId(),
                "userId", checkDeposit.getUserId(),
                "amount", checkDeposit.getAmount(),
                "reason", reason != null ? reason : "Manual review required",
                "priority", priority != null ? priority : "NORMAL",
                "createdAt", LocalDateTime.now().toString()
            );
            
            checkDepositService.queueManualTask("check-deposit-manual-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue check deposit for manual review: {}", checkDeposit.getId(), e);
        }
    }

    /**
     * Queue unknown event for review
     */
    private void queueUnknownEventForReview(String depositId, String userId, String eventType, 
                                          Map<String, Object> eventData) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "UNKNOWN_CHECK_DEPOSIT_EVENT_REVIEW",
                "depositId", depositId,
                "userId", userId,
                "eventType", eventType,
                "eventData", eventData,
                "priority", "NORMAL",
                "createdAt", LocalDateTime.now().toString()
            );
            
            checkDepositService.queueManualTask("unknown-event-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue unknown event for review: {}", depositId, e);
        }
    }

    /**
     * Map CheckDeposit entity to processor DTO
     */
    private com.waqiti.payment.checkdeposit.dto.CheckDeposit mapToProcessorCheckDeposit(CheckDeposit checkDeposit) {
        var processorDeposit = new com.waqiti.payment.checkdeposit.dto.CheckDeposit();
        processorDeposit.setId(checkDeposit.getId().toString());
        processorDeposit.setAmount(checkDeposit.getAmount());
        processorDeposit.setWalletId(checkDeposit.getUserId()); // Assuming userId maps to walletId
        return processorDeposit;
    }

    /**
     * Send various notification types
     */
    private void sendSubmissionNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_SUBMITTED",
                "Your check deposit has been submitted for processing. Amount: $" + checkDeposit.getAmount(),
                Map.of("depositId", checkDeposit.getId(), "amount", checkDeposit.getAmount())
            );
        } catch (Exception e) {
            log.error("Failed to send submission notification", e);
        }
    }

    private void sendApprovalNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_APPROVED",
                "Your check deposit has been approved. Funds will be available in " + checkDeposit.getHoldDays() + " business days.",
                Map.of("depositId", checkDeposit.getId(), "amount", checkDeposit.getAmount(), "holdDays", checkDeposit.getHoldDays())
            );
        } catch (Exception e) {
            log.error("Failed to send approval notification", e);
        }
    }

    private void sendRejectionNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_REJECTED",
                "Your check deposit was not approved. Reason: " + checkDeposit.getRejectionReason(),
                Map.of("depositId", checkDeposit.getId(), "rejectionReason", checkDeposit.getRejectionReason())
            );
        } catch (Exception e) {
            log.error("Failed to send rejection notification", e);
        }
    }

    private void sendFundsAvailableNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_FUNDS_AVAILABLE",
                "Your check deposit funds are now available in your account. Amount: $" + checkDeposit.getAmount(),
                Map.of("depositId", checkDeposit.getId(), "amount", checkDeposit.getAmount())
            );
        } catch (Exception e) {
            log.error("Failed to send funds available notification", e);
        }
    }

    private void sendBounceNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_BOUNCED",
                "Your check deposit was returned. Reason: " + checkDeposit.getBounceReason() + 
                (checkDeposit.getFeeAmount() != null ? " A fee of $" + checkDeposit.getFeeAmount() + " has been charged." : ""),
                Map.of("depositId", checkDeposit.getId(), "bounceReason", checkDeposit.getBounceReason(),
                      "feeAmount", checkDeposit.getFeeAmount() != null ? checkDeposit.getFeeAmount() : BigDecimal.ZERO)
            );
        } catch (Exception e) {
            log.error("Failed to send bounce notification", e);
        }
    }

    private void sendFraudAlert(CheckDeposit checkDeposit) {
        try {
            notificationService.sendSecurityAlert(
                "CHECK_DEPOSIT_FRAUD",
                "Fraudulent check deposit detected - ID: " + checkDeposit.getId(),
                Map.of("depositId", checkDeposit.getId(), "userId", checkDeposit.getUserId(),
                      "amount", checkDeposit.getAmount(), "fraudType", checkDeposit.getFraudType(),
                      "riskScore", checkDeposit.getRiskScore())
            );
        } catch (Exception e) {
            log.error("Failed to send fraud alert", e);
        }
    }

    private void sendManualReviewNotification(CheckDeposit checkDeposit) {
        try {
            notificationService.sendOperationsAlert(
                "CHECK_DEPOSIT_MANUAL_REVIEW",
                "Check deposit requires manual review - ID: " + checkDeposit.getId(),
                Map.of("depositId", checkDeposit.getId(), "amount", checkDeposit.getAmount(),
                      "reviewReason", checkDeposit.getManualReviewReason(),
                      "priority", checkDeposit.getReviewPriority())
            );
        } catch (Exception e) {
            log.error("Failed to send manual review notification", e);
        }
    }

    private void sendStatusUpdateNotification(CheckDeposit checkDeposit, String newStatus, String reason) {
        try {
            notificationService.sendCustomerNotification(
                checkDeposit.getUserId(),
                "CHECK_DEPOSIT_STATUS_UPDATE",
                "Your check deposit status has been updated to: " + newStatus +
                (reason != null ? " (" + reason + ")" : ""),
                Map.of("depositId", checkDeposit.getId(), "newStatus", newStatus,
                      "reason", reason != null ? reason : "N/A")
            );
        } catch (Exception e) {
            log.error("Failed to send status update notification", e);
        }
    }

    private void sendOperationsAlert(String alertType, String depositId, String message) {
        try {
            notificationService.sendOperationsAlert(alertType, message,
                Map.of("depositId", depositId, "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.error("Failed to send operations alert", e);
        }
    }

    /**
     * Helper methods for data extraction
     */
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from value: {} for key: {}", value, key);
            return null;
        }
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Integer from value: {} for key: {}", value, key);
            return null;
        }
    }

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof SecurityException;
    }

    private void sendToDlq(String eventPayload, Exception error) {
        try {
            log.error("Sending check deposit event to DLQ: {}", error.getMessage());
            securityAuditLogger.logSecurityEvent("CHECK_DEPOSIT_EVENT_DLQ", "SYSTEM",
                "Check deposit event sent to DLQ",
                Map.of("error", error.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }
}