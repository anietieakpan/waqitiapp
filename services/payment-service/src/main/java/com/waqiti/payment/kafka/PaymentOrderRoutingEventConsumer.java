package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.PaymentRoutingService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.RegulatoryReportingService;
import com.waqiti.payment.entity.PaymentOrder;
import com.waqiti.payment.entity.RoutingDecision;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Critical Event Consumer #108: Payment Order Routing Event Consumer
 * Processes intelligent payment routing with compliance and fraud checks
 * Implements 12-step zero-tolerance processing for secure payment routing workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderRoutingEventConsumer extends BaseKafkaConsumer {

    private final PaymentRoutingService routingService;
    private final ComplianceService complianceService;
    private final FraudDetectionService fraudDetectionService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-order-routing-events", groupId = "payment-order-routing-group")
    @CircuitBreaker(name = "payment-order-routing-consumer")
    @Retry(name = "payment-order-routing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentOrderRoutingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "payment-order-routing-event");
        
        try {
            log.info("Step 1: Processing payment order routing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String orderId = eventData.path("orderId").asText();
            String senderId = eventData.path("senderId").asText();
            String receiverId = eventData.path("receiverId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String paymentMethod = eventData.path("paymentMethod").asText(); // ACH, WIRE, RTP, CARD
            String priority = eventData.path("priority").asText(); // HIGH, NORMAL, LOW
            String senderCountry = eventData.path("senderCountry").asText();
            String receiverCountry = eventData.path("receiverCountry").asText();
            String purposeCode = eventData.path("purposeCode").asText();
            List<String> routingPreferences = objectMapper.convertValue(
                eventData.path("routingPreferences"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            LocalDateTime requestedExecutionTime = LocalDateTime.parse(eventData.path("requestedExecutionTime").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted routing details: orderId={}, amount={}, method={}, sender={}, receiver={}", 
                    orderId, amount, paymentMethod, senderId, receiverId);
            
            // Step 3: Validate payment order and participant accounts
            PaymentOrder order = routingService.validatePaymentOrder(
                orderId, senderId, receiverId, amount, currency, timestamp);
            
            log.info("Step 3: Validated payment order: orderId={}, status={}", 
                    orderId, order.getStatus());
            
            // Step 4: Perform regulatory compliance checks (BSA/AML, OFAC, sanctions)
            complianceService.performPaymentRoutingComplianceCheck(
                orderId, senderId, receiverId, amount, currency, 
                senderCountry, receiverCountry, purposeCode, timestamp);
            
            log.info("Step 4: Completed compliance checks for payment routing");
            
            // Step 5: Execute fraud detection and risk assessment
            String fraudRisk = fraudDetectionService.assessPaymentRoutingRisk(
                senderId, receiverId, amount, currency, paymentMethod, 
                senderCountry, receiverCountry, timestamp);
            
            if ("HIGH".equals(fraudRisk)) {
                log.warn("Step 5: High fraud risk detected for payment routing: orderId={}", orderId);
                routingService.flagHighRiskPayment(orderId, fraudRisk, timestamp);
            }
            
            // Step 6: Determine optimal routing path based on multiple factors
            RoutingDecision routingDecision = routingService.determineOptimalRouting(
                order, paymentMethod, priority, senderCountry, receiverCountry, 
                routingPreferences, requestedExecutionTime, timestamp);
            
            log.info("Step 6: Determined optimal routing: channel={}, provider={}, cost={}", 
                    routingDecision.getRoutingChannel(), routingDecision.getProvider(), 
                    routingDecision.getEstimatedCost());
            
            // Step 7: Validate routing channel capacity and availability
            boolean channelAvailable = routingService.validateRoutingChannelCapacity(
                routingDecision.getRoutingChannel(), amount, currency, 
                requestedExecutionTime, timestamp);
            
            if (!channelAvailable) {
                log.warn("Step 7: Primary routing channel unavailable, selecting fallback");
                routingDecision = routingService.selectFallbackRouting(
                    order, paymentMethod, timestamp);
            }
            
            // Step 8: Reserve routing capacity and initiate payment
            routingService.reserveRoutingCapacity(
                routingDecision.getRoutingChannel(), amount, currency, 
                orderId, timestamp);
            
            log.info("Step 8: Reserved routing capacity for payment order");
            
            // Step 9: Execute payment routing through selected channel
            PaymentOrder routedOrder = routingService.executePaymentRouting(
                order, routingDecision, timestamp);
            
            log.info("Step 9: Executed payment routing: orderId={}, status={}, routingRef={}", 
                    orderId, routedOrder.getStatus(), routedOrder.getRoutingReference());
            
            // Step 10: Monitor routing progress and handle exceptions
            routingService.monitorRoutingProgress(orderId, routingDecision, timestamp);
            
            // Step 11: Send routing confirmations and updates
            routingService.sendRoutingConfirmations(
                orderId, senderId, receiverId, routingDecision, 
                routedOrder.getEstimatedDeliveryTime(), timestamp);
            
            // Generate regulatory reports for cross-border payments
            if (!senderCountry.equals(receiverCountry) && 
                amount.compareTo(new BigDecimal("3000")) >= 0) {
                regulatoryReportingService.generateCrossBorderRoutingReports(
                    routedOrder, routingDecision, timestamp);
                
                log.info("Step 11: Generated regulatory reports for cross-border routing");
            }
            
            // Step 12: Log routing event for audit trail and update order status
            routingService.logPaymentRoutingEvent(
                orderId, senderId, receiverId, amount, currency, 
                paymentMethod, routingDecision.getRoutingChannel(), 
                routingDecision.getProvider(), routedOrder.getStatus(), 
                fraudRisk, timestamp);
            
            routingService.updateOrderRoutingStatus(orderId, routingDecision, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed payment order routing event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing payment order routing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("orderId") || 
            !eventData.has("senderId") || !eventData.has("receiverId") ||
            !eventData.has("amount") || !eventData.has("currency") ||
            !eventData.has("paymentMethod") || !eventData.has("priority") ||
            !eventData.has("senderCountry") || !eventData.has("receiverCountry") ||
            !eventData.has("purposeCode") || !eventData.has("requestedExecutionTime")) {
            throw new IllegalArgumentException("Invalid payment order routing event structure");
        }
    }
}