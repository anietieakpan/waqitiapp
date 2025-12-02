package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentRoutingEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.GatewayLoadBalanceEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.RoutingRule;
import com.waqiti.payment.domain.RoutingDecision;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.RoutingRuleRepository;
import com.waqiti.payment.service.PaymentRoutingService;
import com.waqiti.payment.service.LeastCostRoutingService;
import com.waqiti.payment.service.GeographicRoutingService;
import com.waqiti.payment.service.LoadBalancingService;
import com.waqiti.payment.service.RoutingOptimizationService;
import com.waqiti.payment.exception.RoutingException;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.metrics.RoutingMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.fraud.FraudService;
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
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL Consumer for Payment Routing Events
 * 
 * Handles intelligent payment routing including:
 * - Least-cost routing optimization
 * - Geographic and regulatory compliance routing
 * - Provider load balancing and performance optimization
 * - Currency-specific routing rules
 * - Risk-based routing decisions
 * - Real-time routing optimization based on success rates
 * - Multi-hop routing for complex payment flows
 * 
 * This is CRITICAL for cost optimization and success rate maximization.
 * Intelligent routing can save 15-30% on processing fees while improving
 * payment success rates by 5-10%.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRoutingEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final PaymentRoutingService routingService;
    private final LeastCostRoutingService leastCostRoutingService;
    private final GeographicRoutingService geographicRoutingService;
    private final LoadBalancingService loadBalancingService;
    private final RoutingOptimizationService optimizationService;
    private final RoutingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final FraudService fraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    // Routing strategy weights
    private static final double COST_OPTIMIZATION_WEIGHT = 0.35;
    private static final double SUCCESS_RATE_WEIGHT = 0.30;
    private static final double SPEED_WEIGHT = 0.20;
    private static final double COMPLIANCE_WEIGHT = 0.15;
    
    // Cost thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal COST_SAVINGS_THRESHOLD = new BigDecimal("0.5"); // $0.50 minimum savings
    private static final double MIN_ACCEPTABLE_SUCCESS_RATE = 0.85; // 85%
    
    // Load balancing parameters
    private static final int MAX_GATEWAY_LOAD = 1000;
    private static final double LOAD_BALANCE_THRESHOLD = 0.80; // 80% capacity
    
    // Routing cache for performance
    private final Map<String, RoutingDecision> routingCache = new ConcurrentHashMap<>();
    
    /**
     * Primary handler for payment routing events
     * Processes intelligent routing decisions and optimizations
     */
    @KafkaListener(
        topics = "payment-routing-events",
        groupId = "payment-routing-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentRoutingEvent(
            @Payload PaymentRoutingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pr-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing payment routing event: paymentId={}, eventType={}, gateway={}, correlation={}",
            event.getPaymentId(), event.getEventType(), event.getGatewayId(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(event.getPaymentId(), "PAYMENT_ROUTING");
            validateRoutingEvent(event);
            
            // Check for fraud indicators in routing
            if (fraudService.isRoutingFraudulent(event)) {
                log.warn("Fraudulent routing detected: paymentId={}", event.getPaymentId());
                handleFraudulentRouting(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case ROUTING_REQUEST:
                    processRoutingRequest(event, correlationId);
                    break;
                case ROUTING_OPTIMIZATION:
                    processRoutingOptimization(event, correlationId);
                    break;
                case LEAST_COST_ROUTING:
                    processLeastCostRouting(event, correlationId);
                    break;
                case GEOGRAPHIC_ROUTING:
                    processGeographicRouting(event, correlationId);
                    break;
                case LOAD_BALANCE_ROUTING:
                    processLoadBalanceRouting(event, correlationId);
                    break;
                case ROUTING_RULE_UPDATE:
                    processRoutingRuleUpdate(event, correlationId);
                    break;
                case ROUTING_PERFORMANCE_UPDATE:
                    processRoutingPerformanceUpdate(event, correlationId);
                    break;
                case ROUTING_FAILOVER:
                    processRoutingFailover(event, correlationId);
                    break;
                default:
                    log.warn("Unknown routing event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the routing operation
            auditService.logFinancialEvent(
                "ROUTING_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "gatewayId", event.getGatewayId(),
                    "correlationId", correlationId,
                    "routingStrategy", event.getRoutingStrategy(),
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing routing event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            handleRoutingEventError(event, e, correlationId);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Routing event processing failed", e);
        }
    }
    
    /**
     * Processes routing requests using intelligent algorithms
     */
    private void processRoutingRequest(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing routing request: paymentId={}, amount={}, currency={}, region={}",
            payment.getId(), payment.getAmount(), payment.getCurrency(), payment.getRegion());
        
        // Check cache first for performance
        String cacheKey = generateRoutingCacheKey(payment);
        RoutingDecision cachedDecision = routingCache.get(cacheKey);
        
        if (cachedDecision != null && isCachedDecisionValid(cachedDecision)) {
            log.debug("Using cached routing decision: paymentId={}", payment.getId());
            applyRoutingDecision(payment, cachedDecision, "CACHED", correlationId);
            return;
        }
        
        // Generate new routing decision
        RoutingDecision routingDecision = generateOptimalRoutingDecision(payment, correlationId);
        
        if (routingDecision == null) {
            log.error("Failed to generate routing decision for payment: {}", payment.getId());
            handleRoutingFailure(payment, "ROUTING_GENERATION_FAILED", correlationId);
            return;
        }
        
        // Cache the decision
        routingCache.put(cacheKey, routingDecision);
        
        // Apply the routing decision
        applyRoutingDecision(payment, routingDecision, "OPTIMIZED", correlationId);
        
        log.info("Routing decision applied: paymentId={}, strategy={}, gateway={}, savings=${}",
            payment.getId(), routingDecision.getStrategy(), routingDecision.getGatewayId(),
            routingDecision.getCostSavings());
    }
    
    /**
     * Generates optimal routing decision using multiple strategies
     */
    private RoutingDecision generateOptimalRoutingDecision(Payment payment, String correlationId) {
        List<RoutingDecision> routingOptions = new ArrayList<>();
        
        try {
            // Strategy 1: Least Cost Routing
            RoutingDecision leastCostDecision = leastCostRoutingService.calculateLeastCostRoute(payment);
            if (leastCostDecision != null) {
                leastCostDecision.setStrategyWeight(COST_OPTIMIZATION_WEIGHT);
                routingOptions.add(leastCostDecision);
            }
            
            // Strategy 2: Success Rate Optimization
            RoutingDecision successRateDecision = routingService.calculateHighestSuccessRateRoute(payment);
            if (successRateDecision != null) {
                successRateDecision.setStrategyWeight(SUCCESS_RATE_WEIGHT);
                routingOptions.add(successRateDecision);
            }
            
            // Strategy 3: Speed Optimization
            RoutingDecision speedDecision = routingService.calculateFastestRoute(payment);
            if (speedDecision != null) {
                speedDecision.setStrategyWeight(SPEED_WEIGHT);
                routingOptions.add(speedDecision);
            }
            
            // Strategy 4: Compliance-First Routing
            RoutingDecision complianceDecision = geographicRoutingService.calculateCompliantRoute(payment);
            if (complianceDecision != null) {
                complianceDecision.setStrategyWeight(COMPLIANCE_WEIGHT);
                routingOptions.add(complianceDecision);
            }
            
            if (routingOptions.isEmpty()) {
                log.warn("No routing options available for payment: {}", payment.getId());
                return null;
            }
            
            // Select optimal routing decision using weighted scoring
            return selectOptimalRoutingDecision(routingOptions, payment);
            
        } catch (Exception e) {
            log.error("Failed to generate routing decision for payment {}: {}",
                payment.getId(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Selects optimal routing decision using weighted multi-criteria analysis
     */
    private RoutingDecision selectOptimalRoutingDecision(
            List<RoutingDecision> options, Payment payment) {
        
        Map<RoutingDecision, Double> decisionScores = new HashMap<>();
        
        for (RoutingDecision decision : options) {
            double score = calculateRoutingScore(decision, payment);
            decisionScores.put(decision, score);
            
            log.debug("Routing option score: strategy={}, gateway={}, score={}",
                decision.getStrategy(), decision.getGatewayId(), score);
        }
        
        // Select decision with highest score
        RoutingDecision optimalDecision = decisionScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(options.get(0)); // Fallback to first option
        
        if (optimalDecision != null) {
            optimalDecision.setSelectionReason(String.format(
                "Optimal score: %.2f (cost: $%.2f, success: %.1f%%, speed: %dms)",
                decisionScores.get(optimalDecision),
                optimalDecision.getTotalCost(),
                optimalDecision.getSuccessRate() * 100,
                optimalDecision.getExpectedProcessingTime()
            ));
        }
        
        return optimalDecision;
    }
    
    /**
     * Calculates weighted score for routing decision
     */
    private double calculateRoutingScore(RoutingDecision decision, Payment payment) {
        // Cost score (lower cost = higher score)
        double costScore = calculateCostScore(decision, payment) * COST_OPTIMIZATION_WEIGHT;
        
        // Success rate score
        double successScore = decision.getSuccessRate() * 100 * SUCCESS_RATE_WEIGHT;
        
        // Speed score (faster = higher score)
        double speedScore = calculateSpeedScore(decision) * SPEED_WEIGHT;
        
        // Compliance score
        double complianceScore = calculateComplianceScore(decision, payment) * COMPLIANCE_WEIGHT;
        
        double totalScore = costScore + successScore + speedScore + complianceScore;
        
        log.debug("Routing score breakdown: strategy={}, cost={}, success={}, speed={}, compliance={}, total={}",
            decision.getStrategy(), costScore, successScore, speedScore, complianceScore, totalScore);
        
        return totalScore;
    }
    
    /**
     * Calculates cost score for routing decision
     */
    private double calculateCostScore(RoutingDecision decision, Payment payment) {
        BigDecimal costPercentage = decision.getTotalCost()
            .divide(payment.getAmount(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        // Convert to score (lower percentage = higher score)
        double score = Math.max(0, 100 - costPercentage.doubleValue() * 10);
        return Math.min(100, score);
    }
    
    /**
     * Calculates speed score for routing decision
     */
    private double calculateSpeedScore(RoutingDecision decision) {
        long processingTime = decision.getExpectedProcessingTime();
        if (processingTime <= 2000) return 100; // Excellent (≤2s)
        if (processingTime <= 5000) return 80;  // Good (≤5s)
        if (processingTime <= 10000) return 60; // Acceptable (≤10s)
        if (processingTime <= 20000) return 40; // Slow (≤20s)
        return 20; // Very slow (>20s)
    }
    
    /**
     * Calculates compliance score for routing decision
     */
    private double calculateComplianceScore(RoutingDecision decision, Payment payment) {
        double score = 100.0;
        
        // Check geographic compliance
        if (!geographicRoutingService.isGeographicallyCompliant(decision, payment)) {
            score -= 30;
        }
        
        // Check regulatory compliance
        if (!routingService.isRegulatoryCompliant(decision, payment)) {
            score -= 40;
        }
        
        // Check currency compliance
        if (!routingService.isCurrencySupported(decision, payment.getCurrency())) {
            score -= 20;
        }
        
        // Check amount limits
        if (!routingService.isAmountWithinLimits(decision, payment.getAmount())) {
            score -= 10;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Processes least cost routing optimization
     */
    private void processLeastCostRouting(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing least cost routing: paymentId={}, currentCost=${}",
            payment.getId(), payment.getEstimatedProcessingCost());
        
        // Skip for low-value payments where optimization overhead isn't worth it
        if (payment.getAmount().compareTo(HIGH_VALUE_THRESHOLD) < 0) {
            log.debug("Skipping least cost routing for low-value payment: {}", payment.getId());
            return;
        }
        
        RoutingDecision leastCostDecision = leastCostRoutingService.calculateLeastCostRoute(payment);
        
        if (leastCostDecision == null) {
            log.warn("Could not calculate least cost route for payment: {}", payment.getId());
            return;
        }
        
        // Check if savings are significant enough
        BigDecimal currentCost = payment.getEstimatedProcessingCost();
        BigDecimal potentialSavings = currentCost.subtract(leastCostDecision.getTotalCost());
        
        if (potentialSavings.compareTo(COST_SAVINGS_THRESHOLD) < 0) {
            log.debug("Cost savings not significant for payment {}: ${}",
                payment.getId(), potentialSavings);
            return;
        }
        
        // Ensure success rate is acceptable
        if (leastCostDecision.getSuccessRate() < MIN_ACCEPTABLE_SUCCESS_RATE) {
            log.warn("Least cost route has unacceptable success rate: {}% for payment {}",
                leastCostDecision.getSuccessRate() * 100, payment.getId());
            return;
        }
        
        // Apply the least cost routing
        applyRoutingDecision(payment, leastCostDecision, "LEAST_COST", correlationId);
        
        log.info("Least cost routing applied: paymentId={}, savings=${}, newGateway={}",
            payment.getId(), potentialSavings, leastCostDecision.getGatewayId());
        
        // Track cost savings
        metricsService.recordCostSavings(payment.getId(), potentialSavings);
    }
    
    /**
     * Processes geographic routing for regulatory compliance
     */
    private void processGeographicRouting(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing geographic routing: paymentId={}, region={}, currency={}",
            payment.getId(), payment.getRegion(), payment.getCurrency());
        
        RoutingDecision geographicDecision = geographicRoutingService.calculateCompliantRoute(payment);
        
        if (geographicDecision == null) {
            log.error("No compliant geographic route available for payment: {}", payment.getId());
            handleRoutingFailure(payment, "NO_COMPLIANT_ROUTE", correlationId);
            return;
        }
        
        // Check if current routing is already compliant
        if (geographicRoutingService.isCurrentRoutingCompliant(payment)) {
            log.debug("Current routing is already geographically compliant: {}", payment.getId());
            return;
        }
        
        // Apply geographic routing
        applyRoutingDecision(payment, geographicDecision, "GEOGRAPHIC_COMPLIANCE", correlationId);
        
        log.info("Geographic routing applied: paymentId={}, complianceRegion={}, gateway={}",
            payment.getId(), geographicDecision.getComplianceRegion(), 
            geographicDecision.getGatewayId());
    }
    
    /**
     * Processes load balancing routing
     */
    private void processLoadBalanceRouting(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing load balance routing: paymentId={}, currentGateway={}",
            payment.getId(), payment.getGatewayId());
        
        // Check current gateway load
        int currentLoad = loadBalancingService.getCurrentGatewayLoad(payment.getGatewayId());
        double loadPercentage = (double) currentLoad / MAX_GATEWAY_LOAD;
        
        if (loadPercentage < LOAD_BALANCE_THRESHOLD) {
            log.debug("Gateway load is acceptable: {}% for payment {}",
                loadPercentage * 100, payment.getId());
            return;
        }
        
        // Find alternative gateway with lower load
        RoutingDecision loadBalanceDecision = loadBalancingService.findLeastLoadedGateway(payment);
        
        if (loadBalanceDecision == null) {
            log.warn("No alternative gateway available for load balancing: {}", payment.getId());
            
            // Send capacity alert
            notificationService.sendOperationalAlert(
                "Gateway Capacity Warning",
                String.format("Gateway %s is at %d%% capacity with no alternatives available",
                    payment.getGatewayId(), (int)(loadPercentage * 100)),
                NotificationService.Priority.HIGH
            );
            return;
        }
        
        // Apply load balancing
        applyRoutingDecision(payment, loadBalanceDecision, "LOAD_BALANCE", correlationId);
        
        log.info("Load balance routing applied: paymentId={}, from={} ({}% load) to={} ({}% load)",
            payment.getId(), payment.getGatewayId(), (int)(loadPercentage * 100),
            loadBalanceDecision.getGatewayId(), loadBalanceDecision.getGatewayLoadPercentage());
        
        // Publish load balance event
        publishGatewayLoadBalanceEvent(payment, loadBalanceDecision, correlationId);
    }
    
    /**
     * Applies routing decision to payment
     */
    private void applyRoutingDecision(Payment payment, RoutingDecision decision, 
            String strategy, String correlationId) {
        
        String originalGateway = payment.getGatewayId();
        
        // Update payment with routing decision
        payment.setGatewayId(decision.getGatewayId());
        payment.setGatewayName(decision.getGatewayName());
        payment.setRoutingStrategy(strategy);
        payment.setRoutingReason(decision.getSelectionReason());
        payment.setRoutingTimestamp(LocalDateTime.now());
        payment.setEstimatedProcessingCost(decision.getTotalCost());
        payment.setExpectedProcessingTime(decision.getExpectedProcessingTime());
        
        if (decision.getCostSavings() != null) {
            payment.setCostSavings(decision.getCostSavings());
        }
        
        paymentRepository.save(payment);
        
        // Update routing metrics
        metricsService.recordRoutingDecision(strategy, decision);
        
        // If gateway changed, publish routing change event
        if (!Objects.equals(originalGateway, decision.getGatewayId())) {
            publishRoutingChangeEvent(payment, originalGateway, decision, correlationId);
        }
        
        log.info("Routing decision applied: paymentId={}, strategy={}, gateway={}->{}",
            payment.getId(), strategy, originalGateway, decision.getGatewayId());
    }
    
    /**
     * Additional utility methods for routing optimization
     */
    private void processRoutingOptimization(PaymentRoutingEvent event, String correlationId) {
        optimizationService.optimizeRoutingForPayment(event.getPaymentId(), correlationId);
    }
    
    private void processRoutingRuleUpdate(PaymentRoutingEvent event, String correlationId) {
        log.info("Processing routing rule update: ruleId={}", event.getRoutingRuleId());
        
        // Clear cache for affected routes
        routingCache.entrySet().removeIf(entry -> 
            affectedByRuleUpdate(entry.getValue(), event.getRoutingRuleId()));
        
        routingService.reloadRoutingRules(event.getRoutingRuleId());
    }
    
    private void processRoutingPerformanceUpdate(PaymentRoutingEvent event, String correlationId) {
        metricsService.updateRoutingPerformanceMetrics(
            event.getGatewayId(), 
            event.getSuccessRate(), 
            event.getAverageProcessingTime()
        );
    }
    
    private void processRoutingFailover(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.warn("Processing routing failover: paymentId={}, failedGateway={}, reason={}",
            payment.getId(), event.getFailedGatewayId(), event.getFailoverReason());
        
        // Generate new routing decision excluding failed gateway
        RoutingDecision failoverDecision = routingService.calculateFailoverRoute(
            payment, event.getFailedGatewayId());
        
        if (failoverDecision != null) {
            applyRoutingDecision(payment, failoverDecision, "FAILOVER", correlationId);
        } else {
            handleRoutingFailure(payment, "NO_FAILOVER_ROUTE", correlationId);
        }
    }
    
    /**
     * Validation and utility methods
     */
    private void validateRoutingEvent(PaymentRoutingEvent event) {
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
    
    private String generateRoutingCacheKey(Payment payment) {
        return String.format("%s:%s:%s:%s",
            payment.getPaymentMethod(),
            payment.getCurrency(),
            payment.getRegion(),
            payment.getAmount().toString()
        );
    }
    
    private boolean isCachedDecisionValid(RoutingDecision decision) {
        // Cache is valid for 5 minutes
        return decision.getTimestamp().isAfter(Instant.now().minusSeconds(300));
    }
    
    private boolean affectedByRuleUpdate(RoutingDecision decision, String ruleId) {
        return decision.getAppliedRules() != null && 
               decision.getAppliedRules().contains(ruleId);
    }
    
    private void handleFraudulentRouting(PaymentRoutingEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setStatus(PaymentStatus.FRAUD_REVIEW);
        payment.setFraudReason("Fraudulent routing detected");
        paymentRepository.save(payment);
        
        notificationService.sendSecurityAlert(
            "Fraudulent Routing Detected",
            String.format("Suspicious routing detected for payment %s", event.getPaymentId()),
            NotificationService.Priority.CRITICAL
        );
    }
    
    private void handleRoutingFailure(Payment payment, String reason, String correlationId) {
        payment.setStatus(PaymentStatus.ROUTING_FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        
        publishPaymentStatusUpdate(payment, reason, correlationId);
        
        notificationService.sendOperationalAlert(
            "Payment Routing Failed",
            String.format("Failed to route payment %s: %s", payment.getId(), reason),
            NotificationService.Priority.HIGH
        );
    }
    
    private void publishRoutingChangeEvent(Payment payment, String originalGateway, 
            RoutingDecision decision, String correlationId) {
        
        Map<String, Object> routingChangeEvent = Map.of(
            "paymentId", payment.getId(),
            "originalGateway", originalGateway != null ? originalGateway : "NONE",
            "newGateway", decision.getGatewayId(),
            "strategy", payment.getRoutingStrategy(),
            "costSavings", decision.getCostSavings() != null ? decision.getCostSavings() : BigDecimal.ZERO,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-routing-changed-events", routingChangeEvent);
    }
    
    private void publishGatewayLoadBalanceEvent(Payment payment, RoutingDecision decision, 
            String correlationId) {
        
        GatewayLoadBalanceEvent loadBalanceEvent = GatewayLoadBalanceEvent.builder()
            .paymentId(payment.getId())
            .fromGatewayId(payment.getGatewayId())
            .toGatewayId(decision.getGatewayId())
            .loadPercentage(decision.getGatewayLoadPercentage())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("gateway-load-balance-events", loadBalanceEvent);
    }
    
    private void publishPaymentStatusUpdate(Payment payment, String reason, String correlationId) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus().toString())
            .reason(reason)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-status-updated-events", statusEvent);
    }
    
    private void handleRoutingEventError(PaymentRoutingEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-routing-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Routing Event Processing Failed",
            String.format("Failed to process routing event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementRoutingEventError(event.getEventType());
    }
}