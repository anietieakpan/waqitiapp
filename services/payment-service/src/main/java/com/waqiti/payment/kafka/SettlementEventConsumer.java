package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.MerchantPayoutService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementEventConsumer {
    
    private final SettlementService settlementService;
    private final MerchantPayoutService merchantPayoutService;
    private final PaymentNotificationService paymentNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"settlement-events", "merchant-settlements", "settlement-initiated", "settlement-completed"},
        groupId = "settlement-event-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleSettlementEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("SETTLEMENT EVENT: Processing settlement - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID settlementId = null;
        UUID merchantId = null;
        String settlementStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            settlementId = UUID.fromString((String) event.get("settlementId"));
            merchantId = UUID.fromString((String) event.get("merchantId"));
            settlementStatus = (String) event.get("settlementStatus");
            String settlementType = (String) event.get("settlementType");
            BigDecimal settlementAmount = new BigDecimal(event.get("settlementAmount").toString());
            String currency = (String) event.get("currency");
            BigDecimal transactionFees = new BigDecimal(event.get("transactionFees").toString());
            BigDecimal processingFees = new BigDecimal(event.get("processingFees").toString());
            BigDecimal netAmount = new BigDecimal(event.get("netAmount").toString());
            Integer transactionCount = (Integer) event.get("transactionCount");
            LocalDateTime settlementPeriodStart = LocalDateTime.parse((String) event.get("settlementPeriodStart"));
            LocalDateTime settlementPeriodEnd = LocalDateTime.parse((String) event.get("settlementPeriodEnd"));
            LocalDateTime settlementTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String payoutMethod = (String) event.get("payoutMethod");
            String bankAccountId = (String) event.get("bankAccountId");
            String settlementReference = (String) event.get("settlementReference");
            @SuppressWarnings("unchecked")
            List<UUID> transactionIds = (List<UUID>) event.getOrDefault("transactionIds", List.of());
            Boolean requiresApproval = (Boolean) event.getOrDefault("requiresApproval", false);
            String approvedBy = (String) event.get("approvedBy");
            
            log.info("Settlement event - SettlementId: {}, MerchantId: {}, Status: {}, Type: {}, Amount: {} {}, NetAmount: {} {}, TxnCount: {}, Period: {} to {}", 
                    settlementId, merchantId, settlementStatus, settlementType, settlementAmount, currency, 
                    netAmount, currency, transactionCount, settlementPeriodStart, settlementPeriodEnd);
            
            validateSettlement(settlementId, merchantId, settlementStatus, settlementAmount, netAmount, currency);
            
            processSettlementByType(settlementId, merchantId, settlementStatus, settlementType, 
                    settlementAmount, currency, transactionFees, processingFees, netAmount, 
                    transactionCount, settlementPeriodStart, settlementPeriodEnd, settlementTimestamp, 
                    payoutMethod, bankAccountId, settlementReference, transactionIds);
            
            if ("INITIATED".equals(settlementStatus)) {
                handleInitiatedSettlement(settlementId, merchantId, settlementType, settlementAmount, 
                        currency, netAmount, transactionCount, settlementPeriodStart, settlementPeriodEnd, 
                        requiresApproval);
            } else if ("APPROVED".equals(settlementStatus)) {
                handleApprovedSettlement(settlementId, merchantId, netAmount, currency, approvedBy, 
                        payoutMethod, bankAccountId);
            } else if ("PROCESSING".equals(settlementStatus)) {
                handleProcessingSettlement(settlementId, merchantId, netAmount, currency, payoutMethod, 
                        settlementReference);
            } else if ("COMPLETED".equals(settlementStatus)) {
                handleCompletedSettlement(settlementId, merchantId, netAmount, currency, 
                        settlementTimestamp, settlementReference);
            } else if ("FAILED".equals(settlementStatus)) {
                handleFailedSettlement(settlementId, merchantId, settlementAmount, currency);
            }
            
            notifyMerchant(merchantId, settlementId, settlementStatus, netAmount, currency, 
                    settlementTimestamp);
            
            updateSettlementMetrics(settlementStatus, settlementType, settlementAmount, currency, 
                    transactionCount);
            
            auditSettlement(settlementId, merchantId, settlementStatus, settlementType, 
                    settlementAmount, netAmount, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Settlement event processed - SettlementId: {}, Status: {}, ProcessingTime: {}ms", 
                    settlementId, settlementStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Settlement processing failed - SettlementId: {}, MerchantId: {}, Status: {}, Error: {}", 
                    settlementId, merchantId, settlementStatus, e.getMessage(), e);
            
            if (settlementId != null && merchantId != null) {
                handleSettlementFailure(settlementId, merchantId, settlementStatus, e);
            }
            
            throw new RuntimeException("Settlement processing failed", e);
        }
    }
    
    private void validateSettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                    BigDecimal settlementAmount, BigDecimal netAmount, String currency) {
        if (settlementId == null || merchantId == null) {
            throw new IllegalArgumentException("Settlement ID and Merchant ID are required");
        }
        
        if (settlementStatus == null || settlementStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Settlement status is required");
        }
        
        if (settlementAmount == null || settlementAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid settlement amount");
        }
        
        if (netAmount == null || netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid net amount");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        log.debug("Settlement validation passed - SettlementId: {}", settlementId);
    }
    
    private void processSettlementByType(UUID settlementId, UUID merchantId, String settlementStatus,
                                        String settlementType, BigDecimal settlementAmount, String currency,
                                        BigDecimal transactionFees, BigDecimal processingFees,
                                        BigDecimal netAmount, Integer transactionCount,
                                        LocalDateTime periodStart, LocalDateTime periodEnd,
                                        LocalDateTime settlementTimestamp, String payoutMethod,
                                        String bankAccountId, String settlementReference,
                                        List<UUID> transactionIds) {
        try {
            switch (settlementType) {
                case "DAILY" -> processDailySettlement(settlementId, merchantId, settlementStatus, 
                        settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
                
                case "WEEKLY" -> processWeeklySettlement(settlementId, merchantId, settlementStatus, 
                        settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
                
                case "MONTHLY" -> processMonthlySettlement(settlementId, merchantId, settlementStatus, 
                        settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
                
                case "ON_DEMAND" -> processOnDemandSettlement(settlementId, merchantId, settlementStatus, 
                        settlementAmount, currency, netAmount, transactionIds);
                
                case "INSTANT" -> processInstantSettlement(settlementId, merchantId, settlementAmount, 
                        currency, netAmount, payoutMethod, bankAccountId);
                
                default -> {
                    log.warn("Unknown settlement type: {}", settlementType);
                    processGenericSettlement(settlementId, merchantId, settlementType);
                }
            }
            
            log.debug("Settlement type processing completed - SettlementId: {}, Type: {}", 
                    settlementId, settlementType);
            
        } catch (Exception e) {
            log.error("Failed to process settlement by type - SettlementId: {}, Type: {}", 
                    settlementId, settlementType, e);
            throw new RuntimeException("Settlement type processing failed", e);
        }
    }
    
    private void processDailySettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                       BigDecimal settlementAmount, String currency, BigDecimal netAmount,
                                       Integer transactionCount, LocalDateTime periodStart,
                                       LocalDateTime periodEnd) {
        log.info("Processing DAILY settlement - SettlementId: {}, Amount: {} {}, NetAmount: {} {}, TxnCount: {}", 
                settlementId, settlementAmount, currency, netAmount, currency, transactionCount);
        
        settlementService.processDailySettlement(settlementId, merchantId, settlementStatus, 
                settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
    }
    
    private void processWeeklySettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                        BigDecimal settlementAmount, String currency, BigDecimal netAmount,
                                        Integer transactionCount, LocalDateTime periodStart,
                                        LocalDateTime periodEnd) {
        log.info("Processing WEEKLY settlement - SettlementId: {}, Amount: {} {}, NetAmount: {} {}, TxnCount: {}", 
                settlementId, settlementAmount, currency, netAmount, currency, transactionCount);
        
        settlementService.processWeeklySettlement(settlementId, merchantId, settlementStatus, 
                settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
    }
    
    private void processMonthlySettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                         BigDecimal settlementAmount, String currency, BigDecimal netAmount,
                                         Integer transactionCount, LocalDateTime periodStart,
                                         LocalDateTime periodEnd) {
        log.info("Processing MONTHLY settlement - SettlementId: {}, Amount: {} {}, NetAmount: {} {}, TxnCount: {}", 
                settlementId, settlementAmount, currency, netAmount, currency, transactionCount);
        
        settlementService.processMonthlySettlement(settlementId, merchantId, settlementStatus, 
                settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
    }
    
    private void processOnDemandSettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                          BigDecimal settlementAmount, String currency, BigDecimal netAmount,
                                          List<UUID> transactionIds) {
        log.info("Processing ON_DEMAND settlement - SettlementId: {}, Amount: {} {}, NetAmount: {} {}, TxnCount: {}", 
                settlementId, settlementAmount, currency, netAmount, currency, transactionIds.size());
        
        settlementService.processOnDemandSettlement(settlementId, merchantId, settlementStatus, 
                settlementAmount, currency, netAmount, transactionIds);
    }
    
    private void processInstantSettlement(UUID settlementId, UUID merchantId, BigDecimal settlementAmount,
                                         String currency, BigDecimal netAmount, String payoutMethod,
                                         String bankAccountId) {
        log.info("Processing INSTANT settlement - SettlementId: {}, Amount: {} {}, NetAmount: {} {}, Method: {}", 
                settlementId, settlementAmount, currency, netAmount, currency, payoutMethod);
        
        merchantPayoutService.processInstantPayout(settlementId, merchantId, settlementAmount, 
                currency, netAmount, payoutMethod, bankAccountId);
    }
    
    private void processGenericSettlement(UUID settlementId, UUID merchantId, String settlementType) {
        log.info("Processing generic settlement - SettlementId: {}, Type: {}", settlementId, settlementType);
        
        settlementService.processGeneric(settlementId, merchantId, settlementType);
    }
    
    private void handleInitiatedSettlement(UUID settlementId, UUID merchantId, String settlementType,
                                          BigDecimal settlementAmount, String currency, BigDecimal netAmount,
                                          Integer transactionCount, LocalDateTime periodStart,
                                          LocalDateTime periodEnd, Boolean requiresApproval) {
        try {
            log.info("Processing initiated settlement - SettlementId: {}, Type: {}, RequiresApproval: {}", 
                    settlementId, settlementType, requiresApproval);
            
            settlementService.recordInitiatedSettlement(settlementId, merchantId, settlementType, 
                    settlementAmount, currency, netAmount, transactionCount, periodStart, periodEnd);
            
            if (requiresApproval && netAmount.compareTo(new BigDecimal("10000")) >= 0) {
                settlementService.requestApproval(settlementId, merchantId, netAmount, currency);
                log.info("Approval requested for high-value settlement - SettlementId: {}, Amount: {} {}", 
                        settlementId, netAmount, currency);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle initiated settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void handleApprovedSettlement(UUID settlementId, UUID merchantId, BigDecimal netAmount,
                                         String currency, String approvedBy, String payoutMethod,
                                         String bankAccountId) {
        try {
            log.info("Processing approved settlement - SettlementId: {}, ApprovedBy: {}, Method: {}", 
                    settlementId, approvedBy, payoutMethod);
            
            settlementService.recordApprovedSettlement(settlementId, merchantId, netAmount, currency, 
                    approvedBy);
            
            merchantPayoutService.initiatePayout(settlementId, merchantId, netAmount, currency, 
                    payoutMethod, bankAccountId);
            
        } catch (Exception e) {
            log.error("Failed to handle approved settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void handleProcessingSettlement(UUID settlementId, UUID merchantId, BigDecimal netAmount,
                                           String currency, String payoutMethod, String settlementReference) {
        try {
            log.info("Processing settlement in progress - SettlementId: {}, Ref: {}, Method: {}", 
                    settlementId, settlementReference, payoutMethod);
            
            settlementService.trackSettlementProgress(settlementId, merchantId, settlementReference);
            
        } catch (Exception e) {
            log.error("Failed to handle processing settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void handleCompletedSettlement(UUID settlementId, UUID merchantId, BigDecimal netAmount,
                                          String currency, LocalDateTime settlementTimestamp,
                                          String settlementReference) {
        try {
            log.info("Processing completed settlement - SettlementId: {}, Amount: {} {}, Ref: {}", 
                    settlementId, netAmount, currency, settlementReference);
            
            settlementService.recordCompletedSettlement(settlementId, merchantId, netAmount, currency, 
                    settlementTimestamp, settlementReference);
            
            settlementService.updateMerchantBalance(merchantId, netAmount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle completed settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void handleFailedSettlement(UUID settlementId, UUID merchantId, BigDecimal settlementAmount,
                                       String currency) {
        try {
            log.error("Processing failed settlement - SettlementId: {}, Amount: {} {}", 
                    settlementId, settlementAmount, currency);
            
            settlementService.recordFailedSettlement(settlementId, merchantId, settlementAmount, currency);
            
            settlementService.requestManualReview(settlementId, merchantId, "Settlement failed");
            
        } catch (Exception e) {
            log.error("Failed to handle failed settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void notifyMerchant(UUID merchantId, UUID settlementId, String settlementStatus,
                               BigDecimal netAmount, String currency, LocalDateTime settlementTimestamp) {
        try {
            paymentNotificationService.sendSettlementNotification(merchantId, settlementId, 
                    settlementStatus, netAmount, currency, settlementTimestamp);
            
            log.info("Merchant notified of settlement - MerchantId: {}, SettlementId: {}, Status: {}", 
                    merchantId, settlementId, settlementStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify merchant - MerchantId: {}, SettlementId: {}", 
                    merchantId, settlementId, e);
        }
    }
    
    private void updateSettlementMetrics(String settlementStatus, String settlementType,
                                        BigDecimal settlementAmount, String currency,
                                        Integer transactionCount) {
        try {
            settlementService.updateSettlementMetrics(settlementStatus, settlementType, 
                    settlementAmount, currency, transactionCount);
        } catch (Exception e) {
            log.error("Failed to update settlement metrics - Status: {}, Type: {}", 
                    settlementStatus, settlementType, e);
        }
    }
    
    private void auditSettlement(UUID settlementId, UUID merchantId, String settlementStatus,
                                String settlementType, BigDecimal settlementAmount, BigDecimal netAmount,
                                LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "SETTLEMENT_PROCESSED",
                    merchantId.toString(),
                    String.format("Settlement %s - Type: %s, Amount: %s, NetAmount: %s", 
                            settlementStatus, settlementType, settlementAmount, netAmount),
                    Map.of(
                            "settlementId", settlementId.toString(),
                            "merchantId", merchantId.toString(),
                            "settlementStatus", settlementStatus,
                            "settlementType", settlementType,
                            "settlementAmount", settlementAmount.toString(),
                            "netAmount", netAmount.toString(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit settlement - SettlementId: {}", settlementId, e);
        }
    }
    
    private void handleSettlementFailure(UUID settlementId, UUID merchantId, String settlementStatus,
                                        Exception error) {
        try {
            settlementService.handleSettlementFailure(settlementId, merchantId, settlementStatus, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "SETTLEMENT_PROCESSING_FAILED",
                    merchantId.toString(),
                    "Failed to process settlement: " + error.getMessage(),
                    Map.of(
                            "settlementId", settlementId.toString(),
                            "merchantId", merchantId.toString(),
                            "settlementStatus", settlementStatus != null ? settlementStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle settlement failure - SettlementId: {}", settlementId, e);
        }
    }
    
    @KafkaListener(
        topics = {"settlement-events.DLQ", "merchant-settlements.DLQ", "settlement-initiated.DLQ", "settlement-completed.DLQ"},
        groupId = "settlement-event-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Settlement event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID settlementId = event.containsKey("settlementId") ? 
                    UUID.fromString((String) event.get("settlementId")) : null;
            UUID merchantId = event.containsKey("merchantId") ? 
                    UUID.fromString((String) event.get("merchantId")) : null;
            String settlementType = (String) event.get("settlementType");
            
            log.error("DLQ: Settlement failed permanently - SettlementId: {}, MerchantId: {}, Type: {} - MANUAL INTERVENTION REQUIRED", 
                    settlementId, merchantId, settlementType);
            
            if (settlementId != null && merchantId != null) {
                settlementService.markForManualReview(settlementId, merchantId, settlementType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse settlement DLQ event: {}", eventJson, e);
        }
    }
}