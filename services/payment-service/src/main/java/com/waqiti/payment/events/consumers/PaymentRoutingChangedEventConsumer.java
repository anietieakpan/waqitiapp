package com.waqiti.payment.events.consumers;

import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.dto.analytics.RecordRoutingAnalyticsRequest;
import com.waqiti.payment.dto.ledger.RecordRoutingChangeRequest;
import com.waqiti.payment.dto.ledger.RecordRoutingChangeResponse;
import com.waqiti.payment.dto.notification.RoutingChangeNotificationRequest;
import com.waqiti.payment.events.PaymentRoutingChangedEvent;
import com.waqiti.payment.exception.ServiceIntegrationException;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Routing Changed Event Consumer
 * 
 * CRITICAL CONSUMER - Processes payment routing change events
 * 
 * EVENT SOURCE:
 * - PaymentRoutingEventsConsumer: Line 688 publishes when routing gateway changes
 * 
 * BUSINESS CRITICALITY:
 * - Records routing changes for cost optimization tracking
 * - Maintains audit trail of routing decisions
 * - Captures cost savings from intelligent routing
 * - Supports analytics on routing effectiveness
 * - Enables real-time routing performance monitoring
 * 
 * PROCESSING ACTIONS:
 * - Record routing change in ledger for cost accounting
 * - Send notifications for significant routing changes
 * - Capture analytics on routing optimization effectiveness
 * - Audit routing decisions for compliance
 * - Track cost savings achieved through intelligent routing
 * 
 * BUSINESS VALUE:
 * - Cost optimization tracking: 15-30% savings on processing fees
 * - Success rate improvement monitoring: 5-10% improvement tracking
 * - Compliance verification for routing decisions
 * - Performance benchmarking across payment gateways
 * - Real-time routing effectiveness analysis
 * 
 * FAILURE IMPACT:
 * - Loss of cost optimization metrics
 * - Missing routing audit trail
 * - Inability to track routing effectiveness
 * - Reduced visibility into gateway performance
 * - Compliance gaps in routing documentation
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Graceful degradation for non-critical operations
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRoutingChangedEventConsumer {
    
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    
    private static final String TOPIC_NAME = "payment-routing-changed-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    @KafkaListener(
        topics = "${kafka.topics.payment-routing-changed-events:payment-routing-changed-events}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handlePaymentRoutingChanged(
            @Payload PaymentRoutingChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.info("Received payment routing changed event - eventId: {}, correlationId: {}, " +
                "topic: {}, partition: {}, offset: {}, paymentId: {}, originalGateway: {}, " +
                "newGateway: {}, strategy: {}, costSavings: {}", 
                event.getEventId(), correlationId, topic, partition, offset, event.getPaymentId(),
                event.getOriginalGateway(), event.getNewGateway(), event.getStrategy(), 
                event.getCostSavings());
        
        try {
            if (event.getPaymentId() == null || event.getNewGateway() == null) {
                log.error("Invalid payment routing changed event - missing required fields: paymentId={}, newGateway={}",
                        event.getPaymentId(), event.getNewGateway());
                auditService.logEventProcessingFailure(
                    event.getEventId() != null ? event.getEventId() : "UNKNOWN", 
                    TOPIC_NAME, 
                    "VALIDATION_FAILED",
                    "Missing required fields: paymentId or newGateway",
                    correlationId,
                    Map.of("event", event)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            String eventId = event.getEventId() != null ? event.getEventId() : 
                    UUID.randomUUID().toString();
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate payment routing changed event detected - eventId: {}, correlationId: {}", 
                        eventId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processPaymentRoutingChanged(event, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed payment routing changed event - eventId: {}, correlationId: {}, " +
                    "paymentId: {}, originalGateway: {}, newGateway: {}, processingTimeMs: {}", 
                    eventId, correlationId, event.getPaymentId(), event.getOriginalGateway(),
                    event.getNewGateway(), processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process payment routing changed event - eventId: {}, correlationId: {}, " +
                    "paymentId: {}, error: {}, processingTimeMs: {}", 
                    event.getEventId(), correlationId, event.getPaymentId(), 
                    e.getMessage(), processingTime, e);
            
            auditService.logEventProcessingFailure(
                event.getEventId() != null ? event.getEventId() : "UNKNOWN",
                TOPIC_NAME,
                "PROCESSING_FAILED",
                e.getMessage(),
                correlationId,
                Map.of(
                    "event", event,
                    "error", e.getClass().getName(),
                    "errorMessage", e.getMessage(),
                    "processingTimeMs", processingTime
                )
            );
            
            throw new ServiceIntegrationException(
                "Failed to process payment routing changed event: " + e.getMessage(), e);
        }
    }
    
    private void processPaymentRoutingChanged(PaymentRoutingChangedEvent event, String correlationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing payment routing changed - paymentId: {}, originalGateway: {}, newGateway: {}, " +
                "strategy: {}, costSavings: {}, correlationId: {}",
                event.getPaymentId(), event.getOriginalGateway(), event.getNewGateway(),
                event.getStrategy(), event.getCostSavings(), correlationId);
        
        recordRoutingChangeInLedger(event, correlationId);
        
        sendRoutingChangeNotifications(event, correlationId);
        
        recordRoutingChangeAnalytics(event, correlationId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("paymentId", event.getPaymentId());
        auditMetadata.put("originalGateway", event.getOriginalGateway());
        auditMetadata.put("newGateway", event.getNewGateway());
        auditMetadata.put("strategy", event.getStrategy());
        auditMetadata.put("costSavings", event.getCostSavings());
        auditMetadata.put("routingReason", event.getRoutingReason());
        auditMetadata.put("changeInitiatedBy", event.getChangeInitiatedBy());
        
        auditService.logPaymentRoutingChanged(
            event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString(),
            event.getPaymentId(),
            event.getOriginalGateway(),
            event.getNewGateway(),
            event.getStrategy(),
            event.getCostSavings(),
            event.getRoutingReason(),
            processingTime,
            correlationId,
            auditMetadata
        );
        
        log.info("Payment routing changed processed successfully - paymentId: {}, newGateway: {}, " +
                "processingTimeMs: {}, correlationId: {}",
                event.getPaymentId(), event.getNewGateway(), processingTime, correlationId);
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordRoutingChangeInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordRoutingChangeInLedger(PaymentRoutingChangedEvent event, String correlationId) {
        log.info("Recording routing change in ledger - paymentId: {}, costSavings: {}, correlationId: {}",
                event.getPaymentId(), event.getCostSavings(), correlationId);
        
        RecordRoutingChangeRequest request = RecordRoutingChangeRequest.builder()
                .paymentId(event.getPaymentId())
                .originalGateway(event.getOriginalGateway())
                .newGateway(event.getNewGateway())
                .strategy(event.getStrategy())
                .costSavings(event.getCostSavings())
                .originalCost(event.getOriginalCost())
                .newCost(event.getNewCost())
                .currency(event.getCurrency())
                .paymentAmount(event.getPaymentAmount())
                .customerId(event.getCustomerId())
                .routingReason(event.getRoutingReason())
                .changeTimestamp(event.getTimestamp())
                .correlationId(correlationId)
                .build();
        
        try {
            RecordRoutingChangeResponse response = ledgerServiceClient.recordRoutingChange(request);
            log.info("Routing change recorded in ledger successfully - paymentId: {}, ledgerEntryId: {}, correlationId: {}",
                    event.getPaymentId(), response.getLedgerEntryId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record routing change in ledger - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordRoutingChangeInLedgerFallback(PaymentRoutingChangedEvent event, String correlationId, 
                                                      Throwable throwable) {
        log.error("CRITICAL: Ledger service unavailable for routing change recording - paymentId: {}, " +
                "originalGateway: {}, newGateway: {}, error: {}, correlationId: {}",
                event.getPaymentId(), event.getOriginalGateway(), event.getNewGateway(), 
                throwable.getMessage(), correlationId);
        
        auditService.logCriticalLedgerFailure(
            "RECORD_ROUTING_CHANGE",
            event.getCustomerId(),
            event.getPaymentId(),
            throwable.getMessage(),
            correlationId,
            Map.of(
                "originalGateway", event.getOriginalGateway(),
                "newGateway", event.getNewGateway(),
                "costSavings", event.getCostSavings() != null ? event.getCostSavings() : "NULL"
            )
        );
        
        throw new ServiceIntegrationException(
            "Critical ledger service failure for routing change: " + throwable.getMessage(), throwable);
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRoutingChangeNotificationsFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendRoutingChangeNotifications(PaymentRoutingChangedEvent event, String correlationId) {
        log.info("Sending routing change notifications - paymentId: {}, customerId: {}, correlationId: {}",
                event.getPaymentId(), event.getCustomerId(), correlationId);
        
        RoutingChangeNotificationRequest request = RoutingChangeNotificationRequest.builder()
                .userId(event.getCustomerId())
                .paymentId(event.getPaymentId())
                .originalGateway(event.getOriginalGatewayName())
                .newGateway(event.getNewGatewayName())
                .strategy(event.getStrategy())
                .costSavings(event.getCostSavings())
                .routingReason(event.getRoutingReason())
                .channels(List.of("EMAIL", "IN_APP"))
                .correlationId(correlationId)
                .build();
        
        try {
            notificationServiceClient.sendRoutingChangeNotification(request);
            log.info("Routing change notifications sent successfully - paymentId: {}, correlationId: {}",
                    event.getPaymentId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to send routing change notifications - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void sendRoutingChangeNotificationsFallback(PaymentRoutingChangedEvent event, String correlationId,
                                                         Throwable throwable) {
        log.warn("Notification service unavailable for routing change notifications - paymentId: {}, error: {}, correlationId: {}",
                event.getPaymentId(), throwable.getMessage(), correlationId);
    }
    
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "recordRoutingChangeAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void recordRoutingChangeAnalytics(PaymentRoutingChangedEvent event, String correlationId) {
        log.info("Recording routing change analytics - paymentId: {}, strategy: {}, correlationId: {}",
                event.getPaymentId(), event.getStrategy(), correlationId);
        
        RecordRoutingAnalyticsRequest request = RecordRoutingAnalyticsRequest.builder()
                .paymentId(event.getPaymentId())
                .customerId(event.getCustomerId())
                .originalGateway(event.getOriginalGateway())
                .newGateway(event.getNewGateway())
                .strategy(event.getStrategy())
                .costSavings(event.getCostSavings())
                .originalCost(event.getOriginalCost())
                .newCost(event.getNewCost())
                .originalSuccessRate(event.getOriginalSuccessRate())
                .newSuccessRate(event.getNewSuccessRate())
                .originalProcessingTime(event.getOriginalProcessingTime())
                .newProcessingTime(event.getNewProcessingTime())
                .paymentAmount(event.getPaymentAmount())
                .currency(event.getCurrency())
                .region(event.getRegion())
                .paymentMethod(event.getPaymentMethod())
                .routingReason(event.getRoutingReason())
                .changeTimestamp(event.getTimestamp())
                .correlationId(correlationId)
                .build();
        
        try {
            analyticsServiceClient.recordRoutingAnalytics(request);
            log.info("Routing change analytics recorded successfully - paymentId: {}, correlationId: {}",
                    event.getPaymentId(), correlationId);
        } catch (Exception e) {
            log.error("Failed to record routing change analytics - paymentId: {}, error: {}, correlationId: {}",
                    event.getPaymentId(), e.getMessage(), correlationId, e);
            throw e;
        }
    }
    
    private void recordRoutingChangeAnalyticsFallback(PaymentRoutingChangedEvent event, String correlationId,
                                                       Throwable throwable) {
        log.warn("Analytics service unavailable for routing change analytics - paymentId: {}, error: {}, correlationId: {}",
                event.getPaymentId(), throwable.getMessage(), correlationId);
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload PaymentRoutingChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.error("Payment routing changed event moved to DLT - eventId: {}, correlationId: {}, " +
                "originalTopic: {}, paymentId: {}, originalGateway: {}, newGateway: {}, error: {}",
                event.getEventId(), correlationId, topic, event.getPaymentId(), 
                event.getOriginalGateway(), event.getNewGateway(), exceptionMessage);
        
        Map<String, Object> dltMetadata = new HashMap<>();
        dltMetadata.put("originalTopic", topic);
        dltMetadata.put("paymentId", event.getPaymentId());
        dltMetadata.put("originalGateway", event.getOriginalGateway());
        dltMetadata.put("newGateway", event.getNewGateway());
        dltMetadata.put("strategy", event.getStrategy());
        dltMetadata.put("costSavings", event.getCostSavings());
        dltMetadata.put("exceptionMessage", exceptionMessage);
        dltMetadata.put("eventTimestamp", event.getTimestamp());
        dltMetadata.put("dltTimestamp", Instant.now());
        
        auditService.logDeadLetterEvent(
            event.getEventId() != null ? event.getEventId() : "UNKNOWN",
            TOPIC_NAME,
            "PAYMENT_ROUTING_CHANGED_DLT",
            exceptionMessage,
            correlationId,
            dltMetadata
        );
    }
}