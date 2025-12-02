package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CustomerServiceTicketService;
import com.waqiti.customer.service.SLAManagementService;
import com.waqiti.customer.service.EscalationService;
import com.waqiti.customer.service.CustomerSatisfactionService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.ServiceRequest;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #224: Customer Service Request Event Consumer
 * Processes support ticket management and SLA compliance tracking
 * Implements 12-step zero-tolerance processing for customer service lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceRequestEventConsumer extends BaseKafkaConsumer {

    private final CustomerServiceTicketService ticketService;
    private final SLAManagementService slaService;
    private final EscalationService escalationService;
    private final CustomerSatisfactionService satisfactionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-service-request-events", groupId = "customer-service-request-group")
    @CircuitBreaker(name = "customer-service-request-consumer")
    @Retry(name = "customer-service-request-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerServiceRequestEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-service-request-event");
        
        try {
            log.info("Step 1: Processing customer service request event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String requestType = eventData.path("requestType").asText();
            String priority = eventData.path("priority").asText();
            String channel = eventData.path("channel").asText();
            String description = eventData.path("description").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String category = eventData.path("category").asText();
            
            log.info("Step 2: Extracted service request details: customerId={}, type={}, priority={}, channel={}", 
                    customerId, requestType, priority, channel);
            
            // Step 3: Customer authentication and verification
            log.info("Step 3: Authenticating customer and verifying account status");
            Customer customer = ticketService.validateCustomerIdentity(customerId);
            if (customer == null || customer.isAccountClosed()) {
                throw new IllegalStateException("Customer not found or account closed: " + customerId);
            }
            
            // Step 4: Request categorization and routing
            log.info("Step 4: Categorizing request and determining appropriate routing");
            ServiceRequest serviceRequest = ticketService.createServiceRequest(eventData);
            String assignedQueue = ticketService.determineRoutingQueue(requestType, priority, category);
            String assignedAgent = ticketService.assignToAvailableAgent(assignedQueue, customer.getTier());
            
            // Step 5: SLA determination and compliance setup
            log.info("Step 5: Determining SLA requirements and setting up compliance monitoring");
            int slaHours = slaService.calculateSLARequirement(requestType, priority, customer.getTier());
            LocalDateTime slaDeadline = timestamp.plusHours(slaHours);
            slaService.createSLAMonitoring(serviceRequest, slaDeadline);
            
            // Step 6: Automated resolution attempt
            log.info("Step 6: Attempting automated resolution for common requests");
            if (ticketService.isAutomatableRequest(requestType)) {
                boolean autoResolved = ticketService.attemptAutomatedResolution(serviceRequest);
                if (autoResolved) {
                    ticketService.markAsResolved(serviceRequest, "AUTOMATED_RESOLUTION", timestamp);
                    satisfactionService.scheduleSatisfactionSurvey(customer, serviceRequest);
                    log.info("Request automatically resolved: {}", serviceRequest.getTicketId());
                    ack.acknowledge();
                    return;
                }
            }
            
            // Step 7: Knowledge base integration and suggestion
            log.info("Step 7: Searching knowledge base and providing suggested solutions");
            ticketService.attachKnowledgeBaseSuggestions(serviceRequest);
            ticketService.provideSelfServiceOptions(customer, serviceRequest);
            ticketService.updateCustomerPortalWithProgress(customer, serviceRequest);
            
            // Step 8: Escalation rules and compliance validation
            log.info("Step 8: Evaluating escalation rules and regulatory compliance");
            if ("HIGH".equals(priority) || "CRITICAL".equals(priority)) {
                escalationService.triggerImmediateEscalation(serviceRequest);
            }
            
            if (ticketService.isRegulatoryComplaintCategory(category)) {
                ticketService.flagForRegulatoryCompliance(serviceRequest);
                ticketService.notifyComplianceTeam(serviceRequest);
            }
            
            // Step 9: Customer communication and acknowledgment
            log.info("Step 9: Sending acknowledgment and setting communication expectations");
            ticketService.sendAcknowledgmentNotification(customer, serviceRequest);
            ticketService.scheduleProgressUpdates(customer, serviceRequest, slaDeadline);
            ticketService.provideEstimatedResolutionTime(customer, serviceRequest);
            
            // Step 10: Quality assurance and monitoring setup
            log.info("Step 10: Setting up quality monitoring and performance tracking");
            ticketService.createQualityAssuranceRecord(serviceRequest);
            slaService.startSLAMonitoring(serviceRequest);
            ticketService.updateCustomerSatisfactionMetrics(customer, channel);
            
            // Step 11: Audit trail and compliance documentation
            log.info("Step 11: Creating comprehensive audit trail and compliance records");
            ticketService.createAuditTrail(serviceRequest, eventData);
            if (ticketService.isFinancialComplaintCategory(category)) {
                ticketService.generateRegulatoryReportingRecord(serviceRequest);
            }
            ticketService.archiveRequestDocuments(serviceRequest);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed service request: ticketId={}, eventId={}", 
                    serviceRequest.getTicketId(), eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer service request event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("requestType") || !eventData.has("priority") ||
            !eventData.has("channel") || !eventData.has("description") ||
            !eventData.has("category")) {
            throw new IllegalArgumentException("Invalid customer service request event structure");
        }
    }
}