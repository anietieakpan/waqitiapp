package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.model.*;
import com.waqiti.payment.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.utils.MDCUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;

@Component
public class RegionalAchProcessingEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(RegionalAchProcessingEventConsumer.class);
    
    private static final String TOPIC = "waqiti.payment.regional-ach-processing";
    private static final String CONSUMER_GROUP = "regional-ach-processing-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.payment.regional-ach-processing.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final MeterRegistry meterRegistry;
    private final RegionalAchService achService;
    private final AchComplianceService complianceService;
    private final AchRoutingService routingService;
    
    private Counter messagesProcessedCounter;
    private Counter achTransactionsCounter;
    private Counter achBatchesCounter;
    private Timer messageProcessingTimer;
    
    private final ConcurrentHashMap<String, AchTransaction> achTransactions = new ConcurrentHashMap<>();
    
    public RegionalAchProcessingEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            MeterRegistry meterRegistry,
            RegionalAchService achService,
            AchComplianceService complianceService,
            AchRoutingService routingService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.meterRegistry = meterRegistry;
        this.achService = achService;
        this.complianceService = complianceService;
        this.routingService = routingService;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("regional_ach_messages_processed_total")
            .register(meterRegistry);
        achTransactionsCounter = Counter.builder("regional_ach_transactions_total")
            .register(meterRegistry);
        achBatchesCounter = Counter.builder("regional_ach_batches_total")
            .register(meterRegistry);
        messageProcessingTimer = Timer.builder("regional_ach_message_processing_duration")
            .register(meterRegistry);
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processRegionalAch(@Payload String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = executeProcessingStep(eventType, messageNode, requestId);
            
            if (processed) {
                messagesProcessedCounter.increment();
                acknowledgment.acknowledge();
                logger.info("Successfully processed regional ACH message: eventType={}", eventType);
            } else {
                throw new RuntimeException("Failed to process message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing regional ACH message", e);
            dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
            acknowledgment.acknowledge();
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "ACH_TRANSACTION_SUBMITTED":
                return processAchTransactionSubmitted(messageNode, requestId);
            case "ACH_BATCH_CREATION":
                return processAchBatchCreation(messageNode, requestId);
            case "ACH_ROUTING_REQUEST":
                return processAchRoutingRequest(messageNode, requestId);
            case "ACH_SETTLEMENT_PROCESSING":
                return processAchSettlementProcessing(messageNode, requestId);
            case "ACH_RETURN_PROCESSING":
                return processAchReturnProcessing(messageNode, requestId);
            case "ACH_COMPLIANCE_CHECK":
                return processAchComplianceCheck(messageNode, requestId);
            case "ACH_STATUS_UPDATE":
                return processAchStatusUpdate(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processAchTransactionSubmitted(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            String achType = messageNode.path("achType").asText();
            String amount = messageNode.path("amount").asText();
            String originatorId = messageNode.path("originatorId").asText();
            String receiverId = messageNode.path("receiverId").asText();
            String routingNumber = messageNode.path("routingNumber").asText();
            String accountNumber = messageNode.path("accountNumber").asText();
            
            AchTransaction transaction = AchTransaction.builder()
                .id(transactionId)
                .achType(achType)
                .amount(new BigDecimal(amount))
                .originatorId(originatorId)
                .receiverId(receiverId)
                .routingNumber(routingNumber)
                .accountNumber(accountNumber)
                .status("SUBMITTED")
                .submittedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            achTransactions.put(transactionId, transaction);
            achService.processTransaction(transaction);
            achTransactionsCounter.increment();
            
            logger.info("Processed ACH transaction: id={}, type={}, amount={}", 
                transactionId, achType, amount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ACH transaction", e);
            return false;
        }
    }
    
    private boolean processAchBatchCreation(JsonNode messageNode, String requestId) {
        try {
            String batchId = messageNode.path("batchId").asText();
            JsonNode transactionIds = messageNode.path("transactionIds");
            String batchType = messageNode.path("batchType").asText();
            String settlementDate = messageNode.path("settlementDate").asText();
            
            AchBatch batch = AchBatch.builder()
                .id(batchId)
                .transactionIds(extractStringList(transactionIds))
                .batchType(batchType)
                .settlementDate(LocalDateTime.parse(settlementDate))
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            achService.createBatch(batch);
            achBatchesCounter.increment();
            
            logger.info("Created ACH batch: id={}, type={}, transactionCount={}", 
                batchId, batchType, transactionIds.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error creating ACH batch", e);
            return false;
        }
    }
    
    private boolean processAchRoutingRequest(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            String sourceRegion = messageNode.path("sourceRegion").asText();
            String targetRegion = messageNode.path("targetRegion").asText();
            
            AchTransaction transaction = achTransactions.get(transactionId);
            if (transaction != null) {
                AchRoutingDecision routing = routingService.determineRouting(
                    transaction, sourceRegion, targetRegion);
                
                transaction.setRoutingDecision(routing.getRoutingPath());
                transaction.setProcessingRegion(routing.getProcessingRegion());
                achService.updateTransaction(transaction);
            }
            
            logger.info("Processed ACH routing: transactionId={}, source={}, target={}", 
                transactionId, sourceRegion, targetRegion);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ACH routing", e);
            return false;
        }
    }
    
    private boolean processAchSettlementProcessing(JsonNode messageNode, String requestId) {
        try {
            String batchId = messageNode.path("batchId").asText();
            String settlementMethod = messageNode.path("settlementMethod").asText();
            
            AchSettlementResult result = achService.processSettlement(batchId, settlementMethod);
            
            logger.info("Processed ACH settlement: batchId={}, method={}, successful={}", 
                batchId, settlementMethod, result.isSuccessful());
            
            return result.isSuccessful();
            
        } catch (Exception e) {
            logger.error("Error processing ACH settlement", e);
            return false;
        }
    }
    
    private boolean processAchReturnProcessing(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            String returnCode = messageNode.path("returnCode").asText();
            String returnReason = messageNode.path("returnReason").asText();
            
            AchReturn achReturn = AchReturn.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(transactionId)
                .returnCode(returnCode)
                .returnReason(returnReason)
                .status("PROCESSED")
                .processedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            achService.processReturn(achReturn);
            
            AchTransaction transaction = achTransactions.get(transactionId);
            if (transaction != null) {
                transaction.setStatus("RETURNED");
                transaction.setReturnCode(returnCode);
                transaction.setReturnReason(returnReason);
                achService.updateTransaction(transaction);
            }
            
            logger.info("Processed ACH return: transactionId={}, code={}, reason={}", 
                transactionId, returnCode, returnReason);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ACH return", e);
            return false;
        }
    }
    
    private boolean processAchComplianceCheck(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            JsonNode complianceRules = messageNode.path("complianceRules");
            
            AchTransaction transaction = achTransactions.get(transactionId);
            if (transaction != null) {
                AchComplianceResult result = complianceService.checkCompliance(
                    transaction, complianceRules);
                
                transaction.setComplianceStatus(result.getStatus());
                transaction.setComplianceCheckedAt(LocalDateTime.now());
                
                if (!result.isCompliant()) {
                    transaction.setStatus("COMPLIANCE_FAILED");
                    transaction.setComplianceViolations(result.getViolations());
                }
                
                achService.updateTransaction(transaction);
                
                logger.info("ACH compliance check: transactionId={}, compliant={}", 
                    transactionId, result.isCompliant());
                
                return result.isCompliant();
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error processing ACH compliance check", e);
            return false;
        }
    }
    
    private boolean processAchStatusUpdate(JsonNode messageNode, String requestId) {
        try {
            String transactionId = messageNode.path("transactionId").asText();
            String newStatus = messageNode.path("newStatus").asText();
            String statusReason = messageNode.path("statusReason").asText();
            
            AchTransaction transaction = achTransactions.get(transactionId);
            if (transaction != null) {
                transaction.setStatus(newStatus);
                transaction.setStatusReason(statusReason);
                transaction.setStatusUpdatedAt(LocalDateTime.now());
                
                achService.updateTransaction(transaction);
            }
            
            logger.info("Updated ACH status: transactionId={}, status={}, reason={}", 
                transactionId, newStatus, statusReason);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error updating ACH status", e);
            return false;
        }
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}