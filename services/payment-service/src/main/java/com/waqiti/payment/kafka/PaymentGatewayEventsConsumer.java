package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentGatewayEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.PaymentRoutingEvent;
import com.waqiti.common.events.GatewayFailoverEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentGateway;
import com.waqiti.payment.domain.GatewayRoutingRule;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentGatewayRepository;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.service.PaymentRoutingService;
import com.waqiti.payment.service.GatewayFailoverService;
import com.waqiti.payment.service.PaymentRetryService;
import com.waqiti.payment.exception.PaymentGatewayException;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CRITICAL Consumer for Payment Gateway Events
 * 
 * Handles all payment gateway operations including:
 * - Gateway selection and routing optimization
 * - Provider failover and circuit breaker logic  
 * - Gateway health monitoring and load balancing
 * - Payment method compatibility and routing rules
 * - Gateway performance metrics and cost optimization
 * - Multi-currency gateway support
 * 
 * This is CRITICAL for revenue as it ensures payments are routed to
 * the most appropriate and available gateway to maximize success rates
 * and minimize processing costs.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentGatewayEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRepository gatewayRepository;
    private final PaymentGatewayService gatewayService;
    private final PaymentRoutingService routingService;
    private final GatewayFailoverService failoverService;
    private final PaymentRetryService retryService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final RateLimitService rateLimitService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Gateway selection criteria weights
    private static final double SUCCESS_RATE_WEIGHT = 0.40;
    private static final double COST_WEIGHT = 0.25;
    private static final double SPEED_WEIGHT = 0.20;
    private static final double AVAILABILITY_WEIGHT = 0.15;
    
    // Circuit breaker thresholds
    private static final double CIRCUIT_BREAKER_THRESHOLD = 0.50; // 50% failure rate
    private static final int CIRCUIT_BREAKER_WINDOW_SIZE = 100;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 300000; // 5 minutes
    
    // Gateway performance thresholds
    private static final long SLOW_GATEWAY_THRESHOLD_MS = 5000;
    private static final double MIN_SUCCESS_RATE = 0.85; // 85%
    private static final int MAX_QUEUE_SIZE = 1000;
    
    /**
     * Primary handler for payment gateway events
     * Processes gateway routing, selection, and management events
     */
    @KafkaListener(
        topics = "payment-gateway-events",
        groupId = "payment-gateway-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentGatewayEvent(
            @Payload PaymentGatewayEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pg-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing payment gateway event: paymentId={}, eventType={}, gatewayId={}, correlation={}",
            event.getPaymentId(), event.getEventType(), event.getGatewayId(), correlationId);
        
        try {
            // Security and validation checks
            securityContext.validateFinancialOperation(event.getPaymentId(), "GATEWAY_PROCESSING");
            validateGatewayEvent(event);
            
            // Rate limiting for gateway operations
            if (!rateLimitService.tryAcquire("gateway_processing", event.getGatewayId(), 100, 60)) {
                log.warn("Rate limit exceeded for gateway: {}", event.getGatewayId());
                scheduleRetryWithBackoff(event, "RATE_LIMITED");
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case GATEWAY_SELECTION_REQUEST:
                    processGatewaySelection(event, correlationId);
                    break;
                case GATEWAY_HEALTH_CHECK:
                    processGatewayHealthCheck(event, correlationId);
                    break;
                case GATEWAY_FAILOVER:
                    processGatewayFailover(event, correlationId);
                    break;
                case GATEWAY_PERFORMANCE_UPDATE:
                    processGatewayPerformanceUpdate(event, correlationId);
                    break;
                case GATEWAY_CONFIGURATION_CHANGE:
                    processGatewayConfigurationChange(event, correlationId);
                    break;
                case GATEWAY_ROUTING_RULE_UPDATE:
                    processGatewayRoutingRuleUpdate(event, correlationId);
                    break;
                case GATEWAY_COST_UPDATE:
                    processGatewayCostUpdate(event, correlationId);
                    break;
                case GATEWAY_CAPACITY_ALERT:
                    processGatewayCapacityAlert(event, correlationId);
                    break;
                default:
                    log.warn("Unknown gateway event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the gateway operation
            auditService.logFinancialEvent(
                "GATEWAY_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "gatewayId", event.getGatewayId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process gateway event: paymentId={}, gatewayId={}, error={}",
                event.getPaymentId(), event.getGatewayId(), e.getMessage(), e);
            
            handleGatewayEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Processes gateway selection requests using intelligent routing algorithms
     */
    private void processGatewaySelection(PaymentGatewayEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing gateway selection: paymentId={}, amount={}, currency={}, region={}",
            payment.getId(), payment.getAmount(), payment.getCurrency(), payment.getRegion());
        
        // Get available gateways for this payment
        List<PaymentGateway> availableGateways = gatewayService.getAvailableGateways(
            payment.getPaymentMethod(),
            payment.getCurrency(),
            payment.getRegion(),
            payment.getAmount()
        );
        
        if (availableGateways.isEmpty()) {
            log.error("No available gateways for payment: {}", payment.getId());
            publishGatewayFailoverEvent(payment.getId(), null, "NO_AVAILABLE_GATEWAYS");
            return;
        }
        
        // Apply intelligent gateway selection algorithm
        PaymentGateway selectedGateway = selectOptimalGateway(
            availableGateways, payment, correlationId);
        
        if (selectedGateway == null) {
            log.error("Failed to select gateway for payment: {}", payment.getId());
            publishGatewayFailoverEvent(payment.getId(), null, "SELECTION_FAILED");
            return;
        }
        
        // Update payment with selected gateway
        payment.setGatewayId(selectedGateway.getId());
        payment.setGatewayName(selectedGateway.getName());
        payment.setGatewaySelectionTimestamp(LocalDateTime.now());
        payment.setGatewaySelectionReason(selectedGateway.getSelectionReason());
        paymentRepository.save(payment);
        
        // Update gateway metrics
        metricsService.incrementGatewaySelection(selectedGateway.getId());
        
        // Publish routing event
        publishPaymentRoutingEvent(payment, selectedGateway, correlationId);
        
        log.info("Gateway selected: paymentId={}, gateway={}, reason={}",
            payment.getId(), selectedGateway.getName(), selectedGateway.getSelectionReason());
    }
    
    /**
     * Intelligent gateway selection using weighted scoring algorithm
     */
    private PaymentGateway selectOptimalGateway(
            List<PaymentGateway> gateways, Payment payment, String correlationId) {
        
        Map<PaymentGateway, Double> gatewayScores = new HashMap<>();
        
        for (PaymentGateway gateway : gateways) {
            try {
                // Check circuit breaker status
                if (failoverService.isCircuitOpen(gateway.getId())) {
                    log.debug("Gateway {} circuit is open, skipping", gateway.getId());
                    continue;
                }
                
                // Calculate gateway score based on multiple factors
                double score = calculateGatewayScore(gateway, payment);
                gatewayScores.put(gateway, score);
                
                log.debug("Gateway score calculated: gateway={}, score={}", 
                    gateway.getName(), score);
                
            } catch (Exception e) {
                log.warn("Failed to calculate score for gateway {}: {}",
                    gateway.getId(), e.getMessage());
                continue;
            }
        }
        
        if (gatewayScores.isEmpty()) {
            return null;
        }
        
        // Select gateway with highest score
        PaymentGateway selectedGateway = gatewayScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        if (selectedGateway != null) {
            selectedGateway.setSelectionReason(String.format(
                "Optimal score: %.2f (success: %.2f%%, cost: $%.4f, speed: %dms)",
                gatewayScores.get(selectedGateway),
                selectedGateway.getSuccessRate() * 100,
                selectedGateway.getProcessingFee(),
                selectedGateway.getAverageResponseTime()
            ));
        }
        
        return selectedGateway;
    }
    
    /**
     * Calculates weighted score for gateway selection
     */
    private double calculateGatewayScore(PaymentGateway gateway, Payment payment) {
        // Success rate score (0-100)
        double successScore = gateway.getSuccessRate() * 100 * SUCCESS_RATE_WEIGHT;
        
        // Cost score (inverse of processing fee, normalized)
        double costScore = calculateCostScore(gateway, payment) * COST_WEIGHT;
        
        // Speed score (inverse of response time, normalized)
        double speedScore = calculateSpeedScore(gateway) * SPEED_WEIGHT;
        
        // Availability score (based on uptime and queue size)
        double availabilityScore = calculateAvailabilityScore(gateway) * AVAILABILITY_WEIGHT;
        
        double totalScore = successScore + costScore + speedScore + availabilityScore;
        
        log.debug("Gateway {} score breakdown: success={}, cost={}, speed={}, availability={}, total={}",
            gateway.getName(), successScore, costScore, speedScore, availabilityScore, totalScore);
        
        return totalScore;
    }
    
    /**
     * Calculates cost score (lower cost = higher score)
     */
    private double calculateCostScore(PaymentGateway gateway, Payment payment) {
        BigDecimal processingFee = gateway.calculateProcessingFee(payment.getAmount());
        BigDecimal normalizedFee = processingFee.divide(payment.getAmount(), 4, 
            RoundingMode.HALF_UP);
        
        // Convert to percentage and invert (lower fee = higher score)
        double feePercentage = normalizedFee.doubleValue() * 100;
        return Math.max(0, 100 - (feePercentage * 10)); // Scale appropriately
    }
    
    /**
     * Calculates speed score (faster = higher score)
     */
    private double calculateSpeedScore(PaymentGateway gateway) {
        long responseTime = gateway.getAverageResponseTime();
        if (responseTime <= 1000) return 100; // Excellent
        if (responseTime <= 3000) return 80;  // Good
        if (responseTime <= 5000) return 60;  // Acceptable
        if (responseTime <= 10000) return 40; // Slow
        return 20; // Very slow
    }
    
    /**
     * Calculates availability score
     */
    private double calculateAvailabilityScore(PaymentGateway gateway) {
        double uptimeScore = gateway.getUptimePercentage();
        
        // Penalize high queue size
        int queueSize = gateway.getCurrentQueueSize();
        double queuePenalty = 0;
        if (queueSize > MAX_QUEUE_SIZE) {
            queuePenalty = Math.min(50, (queueSize - MAX_QUEUE_SIZE) / 20.0);
        }
        
        return Math.max(0, uptimeScore - queuePenalty);
    }
    
    /**
     * Processes gateway health check events
     */
    private void processGatewayHealthCheck(PaymentGatewayEvent event, String correlationId) {
        PaymentGateway gateway = gatewayRepository.findById(event.getGatewayId())
            .orElseThrow(() -> new PaymentGatewayException(
                "Gateway not found: " + event.getGatewayId()));
        
        log.info("Processing gateway health check: gatewayId={}, status={}",
            gateway.getId(), event.getGatewayStatus());
        
        // Update gateway health metrics
        gateway.setHealthStatus(event.getGatewayStatus());
        gateway.setLastHealthCheck(LocalDateTime.now());
        gateway.setHealthCheckResponseTime(event.getResponseTime());
        
        // Update circuit breaker state based on health
        if (event.getGatewayStatus().equals("HEALTHY")) {
            failoverService.recordSuccess(gateway.getId());
            if (gateway.isDisabled()) {
                gateway.setDisabled(false);
                log.info("Gateway re-enabled after health check: {}", gateway.getId());
                notificationService.sendOperationalAlert(
                    "Gateway Restored",
                    String.format("Gateway %s has been restored to service", gateway.getName()),
                    NotificationService.Priority.MEDIUM
                );
            }
        } else {
            failoverService.recordFailure(gateway.getId());
            
            // Check if circuit breaker should trip
            if (failoverService.shouldTripCircuit(gateway.getId())) {
                gateway.setDisabled(true);
                log.warn("Gateway disabled due to health check failures: {}", gateway.getId());
                
                notificationService.sendOperationalAlert(
                    "Gateway Circuit Breaker Tripped",
                    String.format("Gateway %s has been disabled due to health check failures", 
                        gateway.getName()),
                    NotificationService.Priority.HIGH
                );
                
                // Trigger failover for pending payments
                triggerFailoverForPendingPayments(gateway.getId());
            }
        }
        
        gatewayRepository.save(gateway);
        metricsService.updateGatewayHealthMetrics(gateway.getId(), event.getGatewayStatus());
    }
    
    /**
     * Processes gateway failover events
     */
    private void processGatewayFailover(PaymentGatewayEvent event, String correlationId) {
        log.info("Processing gateway failover: from={} to={}, reason={}",
            event.getGatewayId(), event.getFailoverGatewayId(), event.getFailoverReason());
        
        Payment payment = getPaymentById(event.getPaymentId());
        
        // Record original gateway failure
        if (event.getGatewayId() != null) {
            failoverService.recordFailure(event.getGatewayId());
            metricsService.incrementGatewayFailover(event.getGatewayId());
        }
        
        // Find alternative gateway
        PaymentGateway alternativeGateway = findAlternativeGateway(
            payment, event.getGatewayId());
        
        if (alternativeGateway == null) {
            log.error("No alternative gateway available for failover: paymentId={}",
                payment.getId());
            
            // Mark payment as failed
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("No alternative gateway available");
            paymentRepository.save(payment);
            
            publishPaymentStatusUpdate(payment, "GATEWAY_FAILOVER_FAILED");
            return;
        }
        
        // Update payment with new gateway
        String originalGateway = payment.getGatewayName();
        payment.setGatewayId(alternativeGateway.getId());
        payment.setGatewayName(alternativeGateway.getName());
        payment.setFailoverTimestamp(LocalDateTime.now());
        payment.setFailoverReason(event.getFailoverReason());
        payment.setOriginalGatewayId(event.getGatewayId());
        paymentRepository.save(payment);
        
        // Publish routing event for new gateway
        publishPaymentRoutingEvent(payment, alternativeGateway, correlationId);
        
        log.info("Gateway failover completed: paymentId={}, from={} to={}",
            payment.getId(), originalGateway, alternativeGateway.getName());
        
        // Send notification to operations team
        notificationService.sendOperationalAlert(
            "Payment Gateway Failover",
            String.format("Payment %s failed over from %s to %s due to: %s",
                payment.getId(), originalGateway, alternativeGateway.getName(),
                event.getFailoverReason()),
            NotificationService.Priority.MEDIUM
        );
    }
    
    /**
     * Finds alternative gateway for failover
     */
    private PaymentGateway findAlternativeGateway(Payment payment, String excludeGatewayId) {
        List<PaymentGateway> availableGateways = gatewayService.getAvailableGateways(
            payment.getPaymentMethod(),
            payment.getCurrency(),
            payment.getRegion(),
            payment.getAmount()
        );
        
        // Exclude the failed gateway
        availableGateways = availableGateways.stream()
            .filter(g -> !g.getId().equals(excludeGatewayId))
            .filter(g -> !failoverService.isCircuitOpen(g.getId()))
            .collect(Collectors.toList());
        
        if (availableGateways.isEmpty()) {
            return null;
        }
        
        // Select best alternative
        return selectOptimalGateway(availableGateways, payment, "failover");
    }
    
    /**
     * Triggers failover for all pending payments on a failed gateway
     */
    private void triggerFailoverForPendingPayments(String gatewayId) {
        List<Payment> pendingPayments = paymentRepository.findPendingPaymentsByGateway(gatewayId);
        
        log.info("Triggering failover for {} pending payments on gateway {}",
            pendingPayments.size(), gatewayId);
        
        for (Payment payment : pendingPayments) {
            CompletableFuture.runAsync(() -> {
                try {
                    PaymentGatewayEvent failoverEvent = PaymentGatewayEvent.builder()
                        .paymentId(payment.getId())
                        .gatewayId(gatewayId)
                        .eventType("GATEWAY_FAILOVER")
                        .failoverReason("Gateway health check failure")
                        .timestamp(Instant.now())
                        .build();
                    
                    kafkaTemplate.send("payment-gateway-events", failoverEvent);
                    
                } catch (Exception e) {
                    log.error("Failed to trigger failover for payment {}: {}",
                        payment.getId(), e.getMessage());
                }
            });
        }
    }
    
    /**
     * Additional utility methods for gateway management
     */
    private void processGatewayPerformanceUpdate(PaymentGatewayEvent event, String correlationId) {
        PaymentGateway gateway = gatewayRepository.findById(event.getGatewayId())
            .orElse(null);
        
        if (gateway != null) {
            gateway.setSuccessRate(event.getSuccessRate());
            gateway.setAverageResponseTime(event.getResponseTime());
            gateway.setCurrentQueueSize(event.getQueueSize());
            gateway.setLastPerformanceUpdate(LocalDateTime.now());
            gatewayRepository.save(gateway);
            
            metricsService.updateGatewayPerformanceMetrics(gateway.getId(), event);
        }
    }
    
    private void processGatewayConfigurationChange(PaymentGatewayEvent event, String correlationId) {
        log.info("Processing gateway configuration change: gatewayId={}", event.getGatewayId());
        gatewayService.reloadGatewayConfiguration(event.getGatewayId());
    }
    
    private void processGatewayRoutingRuleUpdate(PaymentGatewayEvent event, String correlationId) {
        log.info("Processing gateway routing rule update: gatewayId={}", event.getGatewayId());
        routingService.updateRoutingRules(event.getGatewayId(), event.getRoutingRules());
    }
    
    private void processGatewayCostUpdate(PaymentGatewayEvent event, String correlationId) {
        PaymentGateway gateway = gatewayRepository.findById(event.getGatewayId())
            .orElse(null);
        
        if (gateway != null) {
            gateway.setProcessingFee(event.getProcessingFee());
            gateway.setInterchangeFee(event.getInterchangeFee());
            gatewayRepository.save(gateway);
            
            log.info("Gateway cost updated: gatewayId={}, processingFee={}", 
                event.getGatewayId(), event.getProcessingFee());
        }
    }
    
    private void processGatewayCapacityAlert(PaymentGatewayEvent event, String correlationId) {
        log.warn("Gateway capacity alert: gatewayId={}, queueSize={}, threshold={}",
            event.getGatewayId(), event.getQueueSize(), MAX_QUEUE_SIZE);
        
        if (event.getQueueSize() > MAX_QUEUE_SIZE) {
            notificationService.sendOperationalAlert(
                "Gateway Capacity Alert",
                String.format("Gateway %s queue size (%d) exceeds threshold (%d)",
                    event.getGatewayId(), event.getQueueSize(), MAX_QUEUE_SIZE),
                NotificationService.Priority.HIGH
            );
        }
    }
    
    /**
     * Validation and utility methods
     */
    private void validateGatewayEvent(PaymentGatewayEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found: " + paymentId));
    }
    
    private void publishPaymentRoutingEvent(Payment payment, PaymentGateway gateway, 
            String correlationId) {
        
        PaymentRoutingEvent routingEvent = PaymentRoutingEvent.builder()
            .paymentId(payment.getId())
            .gatewayId(gateway.getId())
            .gatewayName(gateway.getName())
            .routingReason(gateway.getSelectionReason())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-routing-events", routingEvent);
    }
    
    private void publishGatewayFailoverEvent(String paymentId, String gatewayId, String reason) {
        GatewayFailoverEvent failoverEvent = GatewayFailoverEvent.builder()
            .paymentId(paymentId)
            .gatewayId(gatewayId)
            .failoverReason(reason)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("gateway-failover-events", failoverEvent);
    }
    
    private void publishPaymentStatusUpdate(Payment payment, String reason) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus().toString())
            .reason(reason)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-status-updated-events", statusEvent);
    }
    
    private void scheduleRetryWithBackoff(PaymentGatewayEvent event, String reason) {
        retryService.scheduleRetry(event, reason, 2000L); // 2 second initial delay
    }
    
    private void handleGatewayEventError(PaymentGatewayEvent event, Exception error, 
            String correlationId) {
        
        // Send to DLQ for manual review
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-gateway-events-dlq", dlqPayload);
        
        // Send alert to operations team
        notificationService.sendOperationalAlert(
            "Gateway Event Processing Failed",
            String.format("Failed to process gateway event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        // Update metrics
        metricsService.incrementGatewayEventError(event.getGatewayId());
    }
}