package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletFreezeReason;
import com.waqiti.wallet.entity.WalletFreeze;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletFreezeRepository;
import com.waqiti.wallet.service.WalletNotificationService;
import com.waqiti.wallet.service.TransactionBlockingService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Critical Kafka Consumer for Wallet Freeze Events
 * 
 * Handles wallet freeze requests from various security and compliance sources:
 * - Fraud detection system
 * - AML compliance violations
 * - Manual admin actions
 * - Court orders and regulatory mandates
 * - Suspicious activity alerts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletFreezeRequestedConsumer {

    private final WalletRepository walletRepository;
    private final WalletFreezeRepository freezeRepository;
    private final WalletNotificationService notificationService;
    private final TransactionBlockingService blockingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(
        topics = "wallet-freeze-requested",
        groupId = "wallet-freeze-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleWalletFreezeRequested(
            ConsumerRecord<String, String> record,
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        try {
            log.info("Processing wallet freeze request: key={}, partition={}, offset={}",
                key, partition, offset);
            
            // Parse the freeze request event
            Map<String, Object> freezeEvent = objectMapper.readValue(message, Map.class);
            
            String walletId = (String) freezeEvent.get("walletId");
            String userId = (String) freezeEvent.get("userId");
            String freezeReason = (String) freezeEvent.get("reason");
            String requestedBy = (String) freezeEvent.get("requestedBy");
            String freezeType = (String) freezeEvent.get("freezeType");
            Integer durationHours = (Integer) freezeEvent.get("durationHours");
            String notes = (String) freezeEvent.get("notes");
            Boolean emergencyFreeze = (Boolean) freezeEvent.getOrDefault("emergencyFreeze", false);
            
            // Validate required fields
            if ((walletId == null && userId == null) || freezeReason == null || requestedBy == null) {
                log.error("Invalid freeze request - missing required fields: {}", freezeEvent);
                publishFreezeFailedEvent(walletId, userId, "VALIDATION_ERROR", "Missing required fields");
                acknowledgment.acknowledge();
                return;
            }
            
            // Find wallets to freeze
            List<Wallet> walletsToFreeze = findWalletsToFreeze(walletId, userId);
            
            if (walletsToFreeze.isEmpty()) {
                log.error("No wallets found for freeze request: walletId={}, userId={}", walletId, userId);
                publishFreezeFailedEvent(walletId, userId, "WALLETS_NOT_FOUND", "No wallets found to freeze");
                acknowledgment.acknowledge();
                return;
            }
            
            // Process freeze for each wallet
            for (Wallet wallet : walletsToFreeze) {
                processWalletFreeze(wallet, freezeReason, requestedBy, freezeType, 
                    durationHours, notes, emergencyFreeze);
            }
            
            // Audit the freeze operation
            auditService.logSecurityEvent("WALLET_FREEZE_PROCESSED", 
                Map.of(
                    "walletIds", walletsToFreeze.stream().map(w -> w.getId().toString()).toList(),
                    "userId", userId != null ? userId : "N/A",
                    "reason", freezeReason,
                    "requestedBy", requestedBy,
                    "emergencyFreeze", emergencyFreeze.toString(),
                    "walletCount", walletsToFreeze.size()
                ));
            
            log.info("Successfully processed wallet freeze request: walletCount={}, reason={}, requestedBy={}", 
                walletsToFreeze.size(), freezeReason, requestedBy);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Critical error processing wallet freeze request", e);
            
            // Try to extract identifiers for error event
            String walletId = null;
            String userId = null;
            try {
                Map<String, Object> event = objectMapper.readValue(message, Map.class);
                walletId = (String) event.get("walletId");
                userId = (String) event.get("userId");
            } catch (Exception parseException) {
                log.error("Failed to parse event message for error reporting", parseException);
                // Audit the parse failure for security investigation
                auditService.logSecurityEvent("WALLET_FREEZE_PARSE_ERROR", 
                    Map.of(
                        "rawMessage", message,
                        "error", parseException.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                    ));
            }
            
            // Publish failure event with comprehensive error details
            publishFreezeFailedEvent(walletId, userId, "PROCESSING_ERROR", e.getMessage());
            
            // Audit the critical security failure
            auditService.logSecurityEvent("WALLET_FREEZE_PROCESSING_FAILURE",
                Map.of(
                    "walletId", walletId != null ? walletId : "unknown",
                    "userId", userId != null ? userId : "unknown",
                    "errorType", e.getClass().getSimpleName(),
                    "errorMessage", e.getMessage(),
                    "stackTrace", getStackTraceAsString(e),
                    "timestamp", LocalDateTime.now().toString()
                ));

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet event - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet event processing failed", e);
        }
    }

    private List<Wallet> findWalletsToFreeze(String walletId, String userId) {
        if (walletId != null) {
            // Freeze specific wallet
            Optional<Wallet> wallet = walletRepository.findById(UUID.fromString(walletId));
            return wallet.map(List::of).orElse(List.of());
        } else {
            // Freeze all wallets for user
            return walletRepository.findByUserIdAndStatus(userId, WalletStatus.ACTIVE);
        }
    }

    private void processWalletFreeze(Wallet wallet, String reasonCode, String requestedBy, 
                                   String freezeType, Integer durationHours, String notes, 
                                   Boolean emergencyFreeze) {
        
        try {
            log.info("Processing freeze for wallet: walletId={}, userId={}, reason={}", 
                wallet.getId(), wallet.getUserId(), reasonCode);
            
            // Check if wallet is already frozen
            if (wallet.getStatus() == WalletStatus.FROZEN) {
                log.warn("Wallet already frozen: {}", wallet.getId());
                // Check if we need to extend the freeze or add additional reasons
                handleAlreadyFrozenWallet(wallet, reasonCode, requestedBy, durationHours, notes);
                return;
            }
            
            // Validate wallet can be frozen
            if (!canWalletBeFrozen(wallet)) {
                log.warn("Wallet cannot be frozen - invalid status: {} for wallet {}", 
                    wallet.getStatus(), wallet.getId());
                publishFreezeFailedEvent(wallet.getId().toString(), wallet.getUserId(), 
                    "INVALID_STATUS", "Wallet in status " + wallet.getStatus() + " cannot be frozen");
                return;
            }
            
            // Create freeze record
            WalletFreeze freeze = createFreezeRecord(wallet, reasonCode, requestedBy, 
                freezeType, durationHours, notes, emergencyFreeze);
            
            // Execute the freeze
            executeWalletFreeze(wallet, freeze);
            
            log.info("Wallet freeze completed successfully: walletId={}, freezeId={}", 
                wallet.getId(), freeze.getId());
            
        } catch (Exception e) {
            log.error("Error processing wallet freeze: walletId={}", wallet.getId(), e);
            publishFreezeFailedEvent(wallet.getId().toString(), wallet.getUserId(), 
                "FREEZE_EXECUTION_ERROR", e.getMessage());
        }
    }

    private boolean canWalletBeFrozen(Wallet wallet) {
        return wallet.getStatus() == WalletStatus.ACTIVE ||
               wallet.getStatus() == WalletStatus.LIMITED ||
               wallet.getStatus() == WalletStatus.SUSPENDED;
    }

    private void handleAlreadyFrozenWallet(Wallet wallet, String reasonCode, String requestedBy, 
                                         Integer durationHours, String notes) {
        
        // Find existing active freeze
        Optional<WalletFreeze> existingFreeze = freezeRepository
            .findByWalletIdAndStatus(wallet.getId(), "ACTIVE");
        
        if (existingFreeze.isPresent()) {
            WalletFreeze freeze = existingFreeze.get();
            
            // Add additional reason if different
            if (!freeze.getReasonCode().toString().equals(reasonCode)) {
                String combinedReasons = freeze.getReasonCode() + ", " + reasonCode;
                freeze.setAdditionalReasons(combinedReasons);
                
                log.info("Added additional freeze reason to wallet {}: {}", wallet.getId(), reasonCode);
            }
            
            // Extend duration if specified and longer than current
            if (durationHours != null && durationHours > 0) {
                LocalDateTime newExpiryTime = LocalDateTime.now().plusHours(durationHours);
                if (freeze.getExpiresAt() == null || newExpiryTime.isAfter(freeze.getExpiresAt())) {
                    freeze.setExpiresAt(newExpiryTime);
                    log.info("Extended freeze duration for wallet {}: new expiry {}", 
                        wallet.getId(), newExpiryTime);
                }
            }
            
            freeze.setUpdatedAt(LocalDateTime.now());
            freezeRepository.save(freeze);
            
            // Publish freeze extended event
            publishFreezeExtendedEvent(wallet, freeze, reasonCode);
        }
    }

    private WalletFreeze createFreezeRecord(Wallet wallet, String reasonCode, String requestedBy, 
                                          String freezeType, Integer durationHours, String notes, 
                                          Boolean emergencyFreeze) {
        
        LocalDateTime expiresAt = null;
        if (durationHours != null && durationHours > 0) {
            expiresAt = LocalDateTime.now().plusHours(durationHours);
        }
        
        WalletFreeze freeze = WalletFreeze.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .userId(wallet.getUserId())
            .reasonCode(WalletFreezeReason.valueOf(reasonCode))
            .freezeType(freezeType)
            .requestedBy(requestedBy)
            .notes(notes)
            .emergencyFreeze(emergencyFreeze)
            .status("ACTIVE")
            .freezeLevel(determineFreezeLevel(reasonCode, emergencyFreeze))
            .expiresAt(expiresAt)
            .createdAt(LocalDateTime.now())
            .build();
        
        return freezeRepository.save(freeze);
    }

    private String determineFreezeLevel(String reasonCode, Boolean emergencyFreeze) {
        if (emergencyFreeze) {
            return "EMERGENCY_FULL";
        }
        
        return switch (reasonCode) {
            case "FRAUD_SUSPECTED", "AML_VIOLATION", "SANCTIONS_HIT" -> "FULL";
            case "COMPLIANCE_REVIEW", "DOCUMENT_VERIFICATION" -> "PARTIAL";
            case "ADMIN_REQUEST", "COURT_ORDER" -> "FULL";
            case "SUSPICIOUS_ACTIVITY" -> "PARTIAL";
            default -> "PARTIAL";
        };
    }

    private void executeWalletFreeze(Wallet wallet, WalletFreeze freeze) {
        try {
            log.info("Executing wallet freeze: walletId={}, freezeId={}, level={}", 
                wallet.getId(), freeze.getId(), freeze.getFreezeLevel());
            
            // Step 1: Update wallet status
            WalletStatus previousStatus = wallet.getStatus();
            wallet.setStatus(WalletStatus.FROZEN);
            wallet.setFrozenAt(LocalDateTime.now());
            wallet.setFrozenBy(freeze.getRequestedBy());
            wallet.setFreezeReason(freeze.getReasonCode().toString());
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);
            
            // Step 2: Block ongoing and future transactions
            blockingService.blockWalletTransactions(wallet.getId(), freeze.getFreezeLevel());
            
            // Step 3: Cancel pending transactions (if full freeze)
            if ("FULL".equals(freeze.getFreezeLevel()) || "EMERGENCY_FULL".equals(freeze.getFreezeLevel())) {
                blockingService.cancelPendingTransactions(wallet.getId(), 
                    "Wallet frozen: " + freeze.getReasonCode());
            }
            
            // Step 4: Send notifications based on freeze type
            sendFreezeNotifications(wallet, freeze, previousStatus);
            
            // Step 5: Schedule automatic unfreeze (if duration specified)
            if (freeze.getExpiresAt() != null) {
                scheduleAutomaticUnfreeze(wallet.getId(), freeze.getId(), freeze.getExpiresAt());
            }
            
            // Step 6: Publish freeze completed event
            publishFreezeCompletedEvent(wallet, freeze);
            
            log.info("Wallet freeze executed successfully: walletId={}, freezeId={}", 
                wallet.getId(), freeze.getId());
            
        } catch (Exception e) {
            log.error("Error executing wallet freeze: walletId={}, freezeId={}", 
                wallet.getId(), freeze.getId(), e);
            
            // Mark freeze as failed
            freeze.setStatus("FAILED");
            freeze.setFailureReason(e.getMessage());
            freeze.setUpdatedAt(LocalDateTime.now());
            freezeRepository.save(freeze);
            
            throw new RuntimeException("Failed to execute wallet freeze", e);
        }
    }

    private void sendFreezeNotifications(Wallet wallet, WalletFreeze freeze, WalletStatus previousStatus) {
        try {
            // Determine notification urgency
            String urgency = freeze.getEmergencyFreeze() ? "URGENT" : "NORMAL";
            
            // Send user notification (unless it's a covert investigation)
            if (!isCovertFreeze(freeze.getReasonCode())) {
                notificationService.sendWalletFreezeNotification(
                    wallet.getUserId(),
                    wallet.getId(),
                    freeze.getReasonCode().toString(),
                    freeze.getExpiresAt(),
                    urgency
                );
            }
            
            // Send admin notification
            notificationService.sendAdminFreezeNotification(
                wallet.getId(),
                wallet.getUserId(),
                freeze.getReasonCode().toString(),
                freeze.getRequestedBy(),
                previousStatus.toString(),
                urgency
            );
            
            // Send compliance team notification (for regulatory reasons)
            if (isRegulatoryFreeze(freeze.getReasonCode())) {
                notificationService.sendComplianceFreezeNotification(
                    wallet.getId(),
                    wallet.getUserId(),
                    freeze.getReasonCode().toString(),
                    freeze.getRequestedBy()
                );
            }
            
        } catch (Exception e) {
            log.error("Error sending freeze notifications for wallet: {}", wallet.getId(), e);
            // Don't fail the freeze for notification errors
        }
    }

    private boolean isCovertFreeze(WalletFreezeReason reason) {
        return reason == WalletFreezeReason.INVESTIGATION ||
               reason == WalletFreezeReason.LAW_ENFORCEMENT ||
               reason == WalletFreezeReason.SANCTIONS_HIT;
    }

    private boolean isRegulatoryFreeze(WalletFreezeReason reason) {
        return reason == WalletFreezeReason.AML_VIOLATION ||
               reason == WalletFreezeReason.SANCTIONS_HIT ||
               reason == WalletFreezeReason.COMPLIANCE_REVIEW ||
               reason == WalletFreezeReason.COURT_ORDER;
    }

    private void scheduleAutomaticUnfreeze(UUID walletId, UUID freezeId, LocalDateTime expiresAt) {
        try {
            Map<String, Object> unfreezeEvent = Map.of(
                "eventType", "wallet-unfreeze-scheduled",
                "walletId", walletId.toString(),
                "freezeId", freezeId.toString(),
                "scheduledAt", expiresAt.toString(),
                "automatic", true
            );
            
            // Calculate delay until expiry
            long delaySeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);
            
            // Publish scheduled unfreeze event
            kafkaTemplate.send("wallet-unfreeze-scheduled", walletId.toString(), unfreezeEvent);
            
            log.info("Scheduled automatic unfreeze for wallet {} at {}", walletId, expiresAt);
            
        } catch (Exception e) {
            log.error("Error scheduling automatic unfreeze for wallet: {}", walletId, e);
        }
    }

    private void publishFreezeCompletedEvent(Wallet wallet, WalletFreeze freeze) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "wallet-freeze-completed",
                "walletId", wallet.getId().toString(),
                "userId", wallet.getUserId(),
                "freezeId", freeze.getId().toString(),
                "reason", freeze.getReasonCode().toString(),
                "freezeLevel", freeze.getFreezeLevel(),
                "requestedBy", freeze.getRequestedBy(),
                "emergencyFreeze", freeze.getEmergencyFreeze(),
                "expiresAt", freeze.getExpiresAt() != null ? freeze.getExpiresAt().toString() : null,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("wallet-freeze-completed", wallet.getId().toString(), event);
            log.debug("Published wallet freeze completed event: {}", wallet.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish wallet freeze completed event", e);
        }
    }

    private void publishFreezeExtendedEvent(Wallet wallet, WalletFreeze freeze, String additionalReason) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "wallet-freeze-extended",
                "walletId", wallet.getId().toString(),
                "freezeId", freeze.getId().toString(),
                "additionalReason", additionalReason,
                "newExpiryAt", freeze.getExpiresAt() != null ? freeze.getExpiresAt().toString() : null,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("wallet-freeze-extended", wallet.getId().toString(), event);
            log.debug("Published wallet freeze extended event: {}", wallet.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish wallet freeze extended event", e);
        }
    }

    private void publishFreezeFailedEvent(String walletId, String userId, String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "wallet-freeze-failed",
                "walletId", walletId != null ? walletId : "unknown",
                "userId", userId != null ? userId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("wallet-freeze-failed", 
                walletId != null ? walletId : userId, event);
            log.debug("Published wallet freeze failed event: walletId={}, error={}", walletId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish wallet freeze failed event", e);
            // Last resort - audit the failure to publish (critical for security operations)
            try {
                auditService.logSecurityEvent("WALLET_FREEZE_EVENT_PUBLISH_FAILURE", 
                    Map.of(
                        "walletId", walletId != null ? walletId : "unknown",
                        "userId", userId != null ? userId : "unknown",
                        "errorCode", errorCode,
                        "publishError", e.getMessage()
                    ));
            } catch (Exception auditException) {
                // Absolute last resort - log to console for ops team monitoring
                log.error("CRITICAL SECURITY FAILURE: Unable to audit or publish wallet freeze failure - immediate investigation required. WalletId={}, UserId={}", 
                    walletId, userId, auditException);
            }
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}