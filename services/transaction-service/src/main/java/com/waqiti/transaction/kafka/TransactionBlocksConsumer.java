package com.waqiti.transaction.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.transaction.model.*;
import com.waqiti.transaction.repository.TransactionBlockRepository;
import com.waqiti.transaction.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for transaction blocks
 * Handles real-time transaction blocking, fraud prevention, and compliance holds
 * 
 * Critical for: Risk management, fraud prevention, regulatory compliance, operational security
 * SLA: Must process block/unblock requests within 2 seconds for immediate fraud prevention
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionBlocksConsumer {

    private final TransactionBlockRepository blockRepository;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final FraudService fraudService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final RiskService riskService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 2000; // 2 seconds
    private static final Set<String> CRITICAL_BLOCK_TYPES = Set.of(
        "FRAUD_BLOCK", "SANCTIONS_BLOCK", "AML_BLOCK", "SECURITY_BREACH", "REGULATORY_BLOCK"
    );
    
    @KafkaListener(
        topics = {"transaction-blocks"},
        groupId = "transaction-blocks-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "transaction-blocks-processor", fallbackMethod = "handleTransactionBlockFailure")
    @Retry(name = "transaction-blocks-processor")
    public void processTransactionBlockEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing transaction block event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            TransactionBlockRequest blockRequest = extractBlockRequest(payload);
            
            // Validate block request
            validateBlockRequest(blockRequest);
            
            // Check for duplicate block request
            if (isDuplicateBlockRequest(blockRequest)) {
                log.warn("Duplicate block request detected: {}, skipping", blockRequest.getBlockId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Determine block operation type
            BlockOperationType operationType = determineOperationType(blockRequest);
            
            // Process block request
            BlockProcessingResult result = processBlockRequest(blockRequest, operationType);
            
            // Apply immediate transaction controls
            applyTransactionControls(blockRequest, result);
            
            // Handle risk-based actions
            if (blockRequest.requiresRiskAssessment()) {
                performRiskAssessment(blockRequest, result);
            }
            
            // Handle compliance requirements
            if (blockRequest.requiresComplianceReview()) {
                triggerComplianceReview(blockRequest, result);
            }
            
            // Send real-time notifications
            sendBlockNotifications(blockRequest, result);
            
            // Update monitoring systems
            updateMonitoringSystems(blockRequest, result);
            
            // Create audit trail
            auditBlockOperation(blockRequest, result, event);
            
            // Record metrics
            recordBlockMetrics(blockRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed transaction block: {} operation: {} in {}ms", 
                    blockRequest.getBlockId(), operationType, System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for transaction block event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalBlockException e) {
            log.error("Critical block operation failed: {}", eventId, e);
            handleCriticalBlockError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process transaction block event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private TransactionBlockRequest extractBlockRequest(Map<String, Object> payload) {
        return TransactionBlockRequest.builder()
            .blockId(extractString(payload, "blockId", UUID.randomUUID().toString()))
            .blockType(BlockType.fromString(extractString(payload, "blockType", null)))
            .operation(BlockOperation.fromString(extractString(payload, "operation", "BLOCK")))
            .targetType(TargetType.fromString(extractString(payload, "targetType", null)))
            .targetId(extractString(payload, "targetId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .accountId(extractString(payload, "accountId", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .blockReason(extractString(payload, "blockReason", null))
            .blockCategory(extractString(payload, "blockCategory", "SECURITY"))
            .severity(BlockSeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .duration(extractLong(payload, "duration", null))
            .expirationTime(extractInstant(payload, "expirationTime"))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .country(extractString(payload, "country", null))
            .riskScore(extractInteger(payload, "riskScore", 0))
            .alertId(extractString(payload, "alertId", null))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .urgency(extractString(payload, "urgency", "NORMAL"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateBlockRequest(TransactionBlockRequest request) {
        if (request.getBlockType() == null) {
            throw new ValidationException("Block type is required");
        }
        
        if (request.getTargetType() == null) {
            throw new ValidationException("Target type is required");
        }
        
        if (request.getTargetId() == null || request.getTargetId().isEmpty()) {
            throw new ValidationException("Target ID is required");
        }
        
        if (request.getOperation() == null) {
            throw new ValidationException("Block operation is required");
        }
        
        // Validate target existence
        validateTargetExists(request);
        
        // Validate operation permissions
        validateOperationPermissions(request);
        
        // Validate block reason for critical operations
        if (CRITICAL_BLOCK_TYPES.contains(request.getBlockType().toString()) && 
            (request.getBlockReason() == null || request.getBlockReason().trim().isEmpty())) {
            throw new ValidationException("Block reason required for critical block type");
        }
        
        // Validate duration for temporary blocks
        if (request.getBlockType() == BlockType.TEMPORARY && 
            request.getDuration() == null && request.getExpirationTime() == null) {
            throw new ValidationException("Duration or expiration time required for temporary blocks");
        }
    }

    private void validateTargetExists(TransactionBlockRequest request) {
        switch (request.getTargetType()) {
            case TRANSACTION:
                if (!transactionService.transactionExists(request.getTargetId())) {
                    throw new ValidationException("Transaction not found: " + request.getTargetId());
                }
                break;
                
            case ACCOUNT:
                if (!accountService.accountExists(request.getTargetId())) {
                    throw new ValidationException("Account not found: " + request.getTargetId());
                }
                break;
                
            case CUSTOMER:
                if (!customerService.customerExists(request.getTargetId())) {
                    throw new ValidationException("Customer not found: " + request.getTargetId());
                }
                break;
                
            case MERCHANT:
                if (!merchantService.merchantExists(request.getTargetId())) {
                    throw new ValidationException("Merchant not found: " + request.getTargetId());
                }
                break;
        }
    }

    private void validateOperationPermissions(TransactionBlockRequest request) {
        // Check if user/system has permission to perform this operation
        if (!authorizationService.hasBlockPermission(
                request.getRequestedBy(),
                request.getBlockType(),
                request.getTargetType())) {
            throw new ValidationException("Insufficient permissions for block operation");
        }
        
        // Check for existing critical blocks that prevent unblocking
        if (request.getOperation() == BlockOperation.UNBLOCK) {
            List<TransactionBlock> existingBlocks = blockRepository.findActiveBlocksByTarget(
                request.getTargetType(),
                request.getTargetId()
            );
            
            boolean hasCriticalBlocks = existingBlocks.stream()
                .anyMatch(block -> CRITICAL_BLOCK_TYPES.contains(block.getBlockType().toString()) &&
                                 !block.getBlockId().equals(request.getBlockId()));
            
            if (hasCriticalBlocks && !request.hasOverridePermission()) {
                throw new ValidationException("Cannot unblock: critical blocks exist requiring override permission");
            }
        }
    }

    private boolean isDuplicateBlockRequest(TransactionBlockRequest request) {
        return blockRepository.existsByBlockIdAndTimestampAfter(
            request.getBlockId(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private BlockOperationType determineOperationType(TransactionBlockRequest request) {
        if (request.getOperation() == BlockOperation.BLOCK) {
            if (CRITICAL_BLOCK_TYPES.contains(request.getBlockType().toString())) {
                return BlockOperationType.CRITICAL_BLOCK;
            } else if (request.getUrgency().equals("URGENT")) {
                return BlockOperationType.URGENT_BLOCK;
            } else {
                return BlockOperationType.STANDARD_BLOCK;
            }
        } else {
            return BlockOperationType.UNBLOCK;
        }
    }

    private BlockProcessingResult processBlockRequest(TransactionBlockRequest request, 
                                                     BlockOperationType operationType) {
        
        BlockProcessingResult result = new BlockProcessingResult();
        result.setBlockId(request.getBlockId());
        result.setOperationType(operationType);
        result.setProcessingStartTime(Instant.now());
        
        try {
            switch (operationType) {
                case CRITICAL_BLOCK:
                    result = processCriticalBlock(request);
                    break;
                    
                case URGENT_BLOCK:
                    result = processUrgentBlock(request);
                    break;
                    
                case STANDARD_BLOCK:
                    result = processStandardBlock(request);
                    break;
                    
                case UNBLOCK:
                    result = processUnblock(request);
                    break;
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process block request: {}", request.getBlockId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new BlockProcessingException("Block processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private BlockProcessingResult processCriticalBlock(TransactionBlockRequest request) {
        BlockProcessingResult result = new BlockProcessingResult();
        
        // Create immediate block record
        TransactionBlock block = createBlockRecord(request, BlockStatus.ACTIVE);
        result.setBlockRecord(block);
        
        // Immediately stop all matching transactions
        int blockedCount = 0;
        switch (request.getTargetType()) {
            case TRANSACTION:
                transactionService.blockTransaction(request.getTargetId(), request.getBlockReason());
                blockedCount = 1;
                break;
                
            case ACCOUNT:
                blockedCount = transactionService.blockAccountTransactions(
                    request.getTargetId(), request.getBlockReason());
                break;
                
            case CUSTOMER:
                blockedCount = transactionService.blockCustomerTransactions(
                    request.getTargetId(), request.getBlockReason());
                break;
                
            case MERCHANT:
                blockedCount = transactionService.blockMerchantTransactions(
                    request.getTargetId(), request.getBlockReason());
                break;
        }
        
        result.setBlockedTransactionCount(blockedCount);
        
        // Freeze funds if specified
        if (request.shouldFreezeFunds()) {
            freezeFunds(request, result);
        }
        
        // Create emergency alert
        alertingService.createEmergencyAlert(
            "CRITICAL_TRANSACTION_BLOCK",
            String.format("Critical block applied: %s on %s %s", 
                request.getBlockType(), request.getTargetType(), request.getTargetId()),
            request.getBlockReason()
        );
        
        return result;
    }

    private BlockProcessingResult processUrgentBlock(TransactionBlockRequest request) {
        BlockProcessingResult result = new BlockProcessingResult();
        
        // Create block record
        TransactionBlock block = createBlockRecord(request, BlockStatus.ACTIVE);
        result.setBlockRecord(block);
        
        // Apply blocks with priority processing
        int blockedCount = applyTransactionBlocks(request, true);
        result.setBlockedTransactionCount(blockedCount);
        
        // Apply risk-based controls
        applyRiskControls(request, result);
        
        // Send urgent notifications
        notificationService.sendUrgentBlockNotification(request, result);
        
        return result;
    }

    private BlockProcessingResult processStandardBlock(TransactionBlockRequest request) {
        BlockProcessingResult result = new BlockProcessingResult();
        
        // Create block record
        TransactionBlock block = createBlockRecord(request, BlockStatus.ACTIVE);
        result.setBlockRecord(block);
        
        // Apply blocks with standard processing
        int blockedCount = applyTransactionBlocks(request, false);
        result.setBlockedTransactionCount(blockedCount);
        
        // Schedule review if needed
        if (request.requiresReview()) {
            scheduleBlockReview(request);
        }
        
        return result;
    }

    private BlockProcessingResult processUnblock(TransactionBlockRequest request) {
        BlockProcessingResult result = new BlockProcessingResult();
        
        // Find existing block
        TransactionBlock existingBlock = blockRepository.findByBlockId(request.getBlockId())
            .orElseThrow(() -> new ValidationException("Block not found: " + request.getBlockId()));
        
        // Validate unblock permissions
        validateUnblockPermissions(existingBlock, request);
        
        // Update block status
        existingBlock.setStatus(BlockStatus.REMOVED);
        existingBlock.setRemovedAt(Instant.now());
        existingBlock.setRemovedBy(request.getRequestedBy());
        existingBlock.setRemovalReason(request.getBlockReason());
        
        TransactionBlock updatedBlock = blockRepository.save(existingBlock);
        result.setBlockRecord(updatedBlock);
        
        // Remove transaction blocks
        int unblockedCount = removeTransactionBlocks(request);
        result.setUnblockedTransactionCount(unblockedCount);
        
        // Unfreeze funds if applicable
        if (existingBlock.hasFrozenFunds()) {
            unfreezeFunds(existingBlock, result);
        }
        
        // Clear risk flags
        clearRiskFlags(request);
        
        return result;
    }

    private TransactionBlock createBlockRecord(TransactionBlockRequest request, BlockStatus status) {
        Instant expirationTime = request.getExpirationTime();
        if (expirationTime == null && request.getDuration() != null) {
            expirationTime = Instant.now().plus(request.getDuration(), ChronoUnit.MINUTES);
        }
        
        TransactionBlock block = TransactionBlock.builder()
            .blockId(request.getBlockId())
            .blockType(request.getBlockType())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .transactionId(request.getTransactionId())
            .accountId(request.getAccountId())
            .customerId(request.getCustomerId())
            .merchantId(request.getMerchantId())
            .blockReason(request.getBlockReason())
            .blockCategory(request.getBlockCategory())
            .severity(request.getSeverity())
            .status(status)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .country(request.getCountry())
            .riskScore(request.getRiskScore())
            .alertId(request.getAlertId())
            .sourceSystem(request.getSourceSystem())
            .createdBy(request.getRequestedBy())
            .createdAt(request.getTimestamp())
            .expirationTime(expirationTime)
            .metadata(request.getMetadata())
            .build();
        
        return blockRepository.save(block);
    }

    private int applyTransactionBlocks(TransactionBlockRequest request, boolean urgent) {
        int blockedCount = 0;
        
        switch (request.getTargetType()) {
            case TRANSACTION:
                if (transactionService.isTransactionActive(request.getTargetId())) {
                    transactionService.blockTransaction(request.getTargetId(), request.getBlockReason());
                    blockedCount = 1;
                }
                break;
                
            case ACCOUNT:
                blockedCount = transactionService.blockAccountTransactions(
                    request.getTargetId(), request.getBlockReason(), urgent);
                
                // Apply account-level restrictions
                accountService.applyTransactionRestrictions(
                    request.getTargetId(), request.getBlockType(), request.getBlockReason());
                break;
                
            case CUSTOMER:
                blockedCount = transactionService.blockCustomerTransactions(
                    request.getTargetId(), request.getBlockReason(), urgent);
                
                // Block all customer accounts
                List<String> customerAccounts = accountService.getCustomerAccounts(request.getTargetId());
                for (String accountId : customerAccounts) {
                    accountService.applyTransactionRestrictions(
                        accountId, request.getBlockType(), request.getBlockReason());
                }
                break;
                
            case MERCHANT:
                blockedCount = transactionService.blockMerchantTransactions(
                    request.getTargetId(), request.getBlockReason(), urgent);
                
                // Apply merchant-level restrictions
                merchantService.applyTransactionRestrictions(
                    request.getTargetId(), request.getBlockType(), request.getBlockReason());
                break;
        }
        
        return blockedCount;
    }

    private void freezeFunds(TransactionBlockRequest request, BlockProcessingResult result) {
        FundsFreezeRequest freezeRequest = FundsFreezeRequest.builder()
            .freezeId(UUID.randomUUID().toString())
            .blockId(request.getBlockId())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .freezeReason(request.getBlockReason())
            .build();
        
        FundsFreezeResult freezeResult = accountService.freezeFunds(freezeRequest);
        result.setFundsFreezeResult(freezeResult);
    }

    private int removeTransactionBlocks(TransactionBlockRequest request) {
        int unblockedCount = 0;
        
        switch (request.getTargetType()) {
            case TRANSACTION:
                transactionService.unblockTransaction(request.getTargetId());
                unblockedCount = 1;
                break;
                
            case ACCOUNT:
                unblockedCount = transactionService.unblockAccountTransactions(request.getTargetId());
                accountService.removeTransactionRestrictions(request.getTargetId());
                break;
                
            case CUSTOMER:
                unblockedCount = transactionService.unblockCustomerTransactions(request.getTargetId());
                
                List<String> customerAccounts = accountService.getCustomerAccounts(request.getTargetId());
                for (String accountId : customerAccounts) {
                    accountService.removeTransactionRestrictions(accountId);
                }
                break;
                
            case MERCHANT:
                unblockedCount = transactionService.unblockMerchantTransactions(request.getTargetId());
                merchantService.removeTransactionRestrictions(request.getTargetId());
                break;
        }
        
        return unblockedCount;
    }

    private void unfreezeFunds(TransactionBlock block, BlockProcessingResult result) {
        if (block.hasFrozenFunds()) {
            FundsUnfreezeResult unfreezeResult = accountService.unfreezeFunds(
                block.getFreezeId(), "BLOCK_REMOVED");
            result.setFundsUnfreezeResult(unfreezeResult);
        }
    }

    private void validateUnblockPermissions(TransactionBlock existingBlock, TransactionBlockRequest request) {
        // Check if block can be removed
        if (existingBlock.getStatus() != BlockStatus.ACTIVE) {
            throw new ValidationException("Block is not active: " + existingBlock.getBlockId());
        }
        
        // Check removal permissions
        if (!authorizationService.hasUnblockPermission(
                request.getRequestedBy(),
                existingBlock.getBlockType(),
                existingBlock.getSeverity())) {
            throw new ValidationException("Insufficient permissions to remove block");
        }
        
        // Check for dependent blocks
        if (blockRepository.hasDependentBlocks(existingBlock.getBlockId())) {
            throw new ValidationException("Cannot remove block: has dependent blocks");
        }
    }

    private void applyTransactionControls(TransactionBlockRequest request, BlockProcessingResult result) {
        // Apply real-time transaction filtering
        if (request.getOperation() == BlockOperation.BLOCK) {
            transactionFilterService.addFilter(
                request.getTargetType(),
                request.getTargetId(),
                request.getBlockType(),
                request.getBlockReason()
            );
        } else {
            transactionFilterService.removeFilter(
                request.getTargetType(),
                request.getTargetId(),
                request.getBlockId()
            );
        }
        
        // Update payment gateway restrictions
        paymentGatewayService.updateRestrictions(request);
        
        // Apply merchant controls if applicable
        if (request.getMerchantId() != null) {
            merchantControlService.updateControls(request.getMerchantId(), request);
        }
    }

    private void performRiskAssessment(TransactionBlockRequest request, BlockProcessingResult result) {
        RiskAssessmentRequest riskRequest = RiskAssessmentRequest.builder()
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .blockType(request.getBlockType())
            .riskScore(request.getRiskScore())
            .blockReason(request.getBlockReason())
            .build();
        
        RiskAssessmentResult riskResult = riskService.assessBlockRisk(riskRequest);
        result.setRiskAssessmentResult(riskResult);
        
        // Take additional actions based on risk level
        if (riskResult.isHighRisk()) {
            applyAdditionalRiskControls(request, riskResult);
        }
    }

    private void applyAdditionalRiskControls(TransactionBlockRequest request, RiskAssessmentResult riskResult) {
        // Apply enhanced monitoring
        monitoringService.enableEnhancedMonitoring(
            request.getTargetType(),
            request.getTargetId(),
            riskResult.getRiskFactors()
        );
        
        // Trigger additional security measures
        securityService.triggerSecurityMeasures(request, riskResult);
        
        // Escalate to risk team
        escalationService.escalateToRiskTeam(request, riskResult);
    }

    private void applyRiskControls(TransactionBlockRequest request, BlockProcessingResult result) {
        // Calculate enhanced risk score
        int enhancedRiskScore = riskService.calculateEnhancedRiskScore(
            request.getTargetType(),
            request.getTargetId(),
            request.getRiskScore()
        );
        
        result.setEnhancedRiskScore(enhancedRiskScore);
        
        // Apply risk-based velocity limits
        if (enhancedRiskScore > 70) {
            velocityControlService.applyEnhancedLimits(
                request.getTargetType(),
                request.getTargetId()
            );
        }
        
        // Update risk profiles
        riskService.updateRiskProfile(request.getTargetType(), request.getTargetId(), enhancedRiskScore);
    }

    private void clearRiskFlags(TransactionBlockRequest request) {
        // Clear risk flags associated with the block
        riskService.clearBlockRelatedFlags(
            request.getTargetType(),
            request.getTargetId(),
            request.getBlockId()
        );
        
        // Re-evaluate risk profile
        riskService.reevaluateRiskProfile(request.getTargetType(), request.getTargetId());
    }

    private void triggerComplianceReview(TransactionBlockRequest request, BlockProcessingResult result) {
        ComplianceReviewRequest reviewRequest = ComplianceReviewRequest.builder()
            .blockId(request.getBlockId())
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .blockType(request.getBlockType())
            .blockReason(request.getBlockReason())
            .priority(request.getSeverity().toString())
            .build();
        
        ComplianceCase complianceCase = complianceService.createReviewCase(reviewRequest);
        result.setComplianceCase(complianceCase);
    }

    private void scheduleBlockReview(TransactionBlockRequest request) {
        // Schedule periodic review for non-permanent blocks
        if (request.getBlockType() != BlockType.PERMANENT) {
            ReviewSchedule schedule = ReviewSchedule.builder()
                .blockId(request.getBlockId())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reviewInterval(determineReviewInterval(request.getBlockType()))
                .nextReviewDate(Instant.now().plus(determineReviewInterval(request.getBlockType()), ChronoUnit.HOURS))
                .build();
            
            reviewScheduleService.scheduleReview(schedule);
        }
    }

    private long determineReviewInterval(BlockType blockType) {
        switch (blockType) {
            case TEMPORARY:
                return 24; // 24 hours
            case FRAUD_INVESTIGATION:
                return 72; // 3 days
            case COMPLIANCE_HOLD:
                return 168; // 7 days
            default:
                return 48; // 48 hours
        }
    }

    private void sendBlockNotifications(TransactionBlockRequest request, BlockProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "blockId", request.getBlockId(),
            "operation", request.getOperation().toString(),
            "blockType", request.getBlockType().toString(),
            "targetType", request.getTargetType().toString(),
            "targetId", request.getTargetId(),
            "blockReason", request.getBlockReason(),
            "severity", request.getSeverity().toString(),
            "timestamp", request.getTimestamp()
        );
        
        // Send immediate notifications for critical blocks
        if (CRITICAL_BLOCK_TYPES.contains(request.getBlockType().toString())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalBlockAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_BLOCK", notificationData);
            });
        }
        
        // Notify affected parties
        CompletableFuture.runAsync(() -> {
            notificationService.sendBlockNotification(
                request.getTargetType(),
                request.getTargetId(),
                notificationData
            );
        });
        
        // Notify operations team
        CompletableFuture.runAsync(() -> {
            notificationService.sendOperationsNotification(
                "TRANSACTION_BLOCK_" + request.getOperation(),
                notificationData
            );
        });
        
        // Send webhook notifications if configured
        if (request.hasWebhookUrl()) {
            CompletableFuture.runAsync(() -> {
                webhookService.sendBlockWebhook(request.getWebhookUrl(), notificationData);
            });
        }
    }

    private void updateMonitoringSystems(TransactionBlockRequest request, BlockProcessingResult result) {
        // Update real-time monitoring dashboards
        monitoringService.updateBlockDashboard(request, result);
        
        // Update fraud detection systems
        fraudService.updateBlockStatus(request, result);
        
        // Update compliance monitoring
        complianceService.updateBlockMonitoring(request, result);
        
        // Update risk management systems
        riskService.updateBlockMetrics(request, result);
    }

    private void auditBlockOperation(TransactionBlockRequest request, BlockProcessingResult result, 
                                   GenericKafkaEvent event) {
        auditService.auditTransactionBlock(
            request.getBlockId(),
            request.getOperation().toString(),
            request.getBlockType().toString(),
            request.getTargetType().toString(),
            request.getTargetId(),
            request.getBlockReason(),
            result.getStatus().toString(),
            request.getRequestedBy(),
            event.getEventId()
        );
    }

    private void recordBlockMetrics(TransactionBlockRequest request, BlockProcessingResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordBlockMetrics(
            request.getOperation().toString(),
            request.getBlockType().toString(),
            request.getTargetType().toString(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            result.getStatus().toString()
        );
        
        // Record block effectiveness metrics
        if (result.getBlockedTransactionCount() != null) {
            metricsService.recordBlockEffectiveness(
                request.getBlockType().toString(),
                result.getBlockedTransactionCount()
            );
        }
        
        // Record risk metrics
        if (result.getEnhancedRiskScore() != null) {
            metricsService.recordBlockRiskMetrics(
                request.getBlockType().toString(),
                request.getRiskScore(),
                result.getEnhancedRiskScore()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("transaction-block-validation-errors", event);
    }

    private void handleCriticalBlockError(GenericKafkaEvent event, CriticalBlockException e) {
        // Create emergency alert for critical block failures
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_BLOCK_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("transaction-block-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying transaction block event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("transaction-blocks-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for transaction block event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "transaction-blocks");
        
        kafkaTemplate.send("transaction-blocks.DLQ", event);
        
        alertingService.createDLQAlert(
            "transaction-blocks",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleTransactionBlockFailure(GenericKafkaEvent event, String topic, int partition,
                                            long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for transaction block processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Transaction Blocks Circuit Breaker Open",
            "Transaction block processing is failing. Risk management capability compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalBlockException extends RuntimeException {
        public CriticalBlockException(String message) {
            super(message);
        }
    }

    public static class BlockProcessingException extends RuntimeException {
        public BlockProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}