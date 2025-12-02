package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.EscalationService;
import com.waqiti.customer.service.CustomerServiceManagerService;
import com.waqiti.customer.service.TicketManagementService;
import com.waqiti.customer.service.CustomerCommunicationService;
import com.waqiti.customer.service.SLAManagementService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerServiceEscalation;
import com.waqiti.customer.domain.EscalationType;
import com.waqiti.customer.domain.EscalationPriority;
import com.waqiti.customer.domain.EscalationStatus;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CustomerServiceEscalationsConsumer {

    private final EscalationService escalationService;
    private final CustomerServiceManagerService managerService;
    private final TicketManagementService ticketService;
    private final CustomerCommunicationService communicationService;
    private final SLAManagementService slaService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter escalationsProcessedCounter;
    private final Counter criticalEscalationsCounter;
    private final Counter managerEscalationsCounter;
    private final Counter executiveEscalationsCounter;
    private final Timer escalationProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public CustomerServiceEscalationsConsumer(
            EscalationService escalationService,
            CustomerServiceManagerService managerService,
            TicketManagementService ticketService,
            CustomerCommunicationService communicationService,
            SLAManagementService slaService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.escalationService = escalationService;
        this.managerService = managerService;
        this.ticketService = ticketService;
        this.communicationService = communicationService;
        this.slaService = slaService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.escalationsProcessedCounter = Counter.builder("customer.service.escalations.processed")
            .description("Count of customer service escalations processed")
            .register(meterRegistry);
        
        this.criticalEscalationsCounter = Counter.builder("customer.service.escalations.critical")
            .description("Count of critical customer service escalations")
            .register(meterRegistry);
        
        this.managerEscalationsCounter = Counter.builder("customer.service.escalations.manager_level")
            .description("Count of escalations to manager level")
            .register(meterRegistry);
        
        this.executiveEscalationsCounter = Counter.builder("customer.service.escalations.executive_level")
            .description("Count of escalations to executive level")
            .register(meterRegistry);
        
        this.escalationProcessingTimer = Timer.builder("customer.service.escalations.processing.duration")
            .description("Time taken to process customer service escalations")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "customer-service-escalations",
        groupId = "customer-service-escalations-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "customer-service-escalations-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCustomerServiceEscalationEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.error("CUSTOMER SERVICE ESCALATION RECEIVED - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Customer service escalation event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String escalationId = (String) eventData.get("escalationId");
            String customerId = (String) eventData.get("customerId");
            String ticketId = (String) eventData.get("ticketId");
            String escalationType = (String) eventData.get("escalationType");
            String priority = (String) eventData.get("priority");
            String reason = (String) eventData.get("reason");
            String description = (String) eventData.get("description");
            String escalatedBy = (String) eventData.get("escalatedBy");
            String escalatedTo = (String) eventData.get("escalatedTo");
            Integer escalationLevel = (Integer) eventData.get("escalationLevel");
            Boolean requiresImmediateAction = (Boolean) eventData.getOrDefault("requiresImmediateAction", false);
            String originalIssue = (String) eventData.get("originalIssue");
            
            String correlationId = String.format("service-escalation-%s-%d", 
                escalationId, System.currentTimeMillis());
            
            log.error("PROCESSING SERVICE ESCALATION - escalationId: {}, customerId: {}, type: {}, priority: {}, level: {}, correlationId: {}", 
                escalationId, customerId, escalationType, priority, escalationLevel, correlationId);
            
            escalationsProcessedCounter.increment();
            if ("CRITICAL".equals(priority) || "URGENT".equals(priority)) {
                criticalEscalationsCounter.increment();
            }
            if (escalationLevel >= 2) {
                managerEscalationsCounter.increment();
            }
            if (escalationLevel >= 3) {
                executiveEscalationsCounter.increment();
            }
            
            processCustomerServiceEscalation(escalationId, customerId, ticketId, escalationType,
                priority, reason, description, escalatedBy, escalatedTo, escalationLevel,
                requiresImmediateAction, originalIssue, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(escalationProcessingTimer);
            
            log.error("SERVICE ESCALATION PROCESSED - eventId: {}, escalationId: {}", eventId, escalationId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process customer service escalation event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Customer service escalation processing failed", e);
        }
    }

    @CircuitBreaker(name = "customer", fallbackMethod = "processCustomerServiceEscalationFallback")
    @Retry(name = "customer")
    private void processCustomerServiceEscalation(
            String escalationId,
            String customerId,
            String ticketId,
            String escalationType,
            String priority,
            String reason,
            String description,
            String escalatedBy,
            String escalatedTo,
            Integer escalationLevel,
            Boolean requiresImmediateAction,
            String originalIssue,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Get customer details
        Customer customer = escalationService.getCustomerById(customerId);
        if (customer == null) {
            throw new RuntimeException("Customer not found: " + customerId);
        }
        
        // Create escalation record
        CustomerServiceEscalation escalation = CustomerServiceEscalation.builder()
            .id(escalationId)
            .customerId(customerId)
            .ticketId(ticketId)
            .escalationType(EscalationType.valueOf(escalationType))
            .priority(EscalationPriority.valueOf(priority))
            .reason(reason)
            .description(description)
            .escalatedBy(escalatedBy)
            .escalatedTo(escalatedTo)
            .escalationLevel(escalationLevel)
            .requiresImmediateAction(requiresImmediateAction)
            .originalIssue(originalIssue)
            .status(EscalationStatus.ACTIVE)
            .escalatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        escalationService.saveEscalation(escalation);
        
        // Assign to appropriate manager/executive based on level
        String assignedTo = assignEscalationBasedOnLevel(escalation, customer, correlationId);
        escalation.setAssignedTo(assignedTo);
        
        // Set SLA requirements based on priority and customer tier
        var slaRequirements = slaService.setSLARequirements(escalation, customer, correlationId);
        escalation.setSlaDeadline(slaRequirements.getResponseDeadline());
        escalation.setSlaResolutionDeadline(slaRequirements.getResolutionDeadline());
        
        // Update ticket with escalation information
        ticketService.updateTicketWithEscalation(ticketId, escalation, correlationId);
        
        // Notify customer about escalation
        communicationService.notifyCustomerOfEscalation(customer, escalation, correlationId);
        
        // Notify assigned manager/executive immediately
        managerService.notifyAssignedManager(assignedTo, escalation, customer, correlationId);
        
        // Set up escalation monitoring and reminders
        escalationService.setupEscalationMonitoring(escalation, correlationId);
        
        // Trigger immediate action protocols if required
        if (requiresImmediateAction || "CRITICAL".equals(priority)) {
            escalationService.triggerImmediateActionProtocols(escalation, customer, correlationId);
        }
        
        // Update customer experience metrics
        escalationService.updateCustomerExperienceMetrics(customer, escalation, correlationId);
        
        // Check for patterns and trends
        escalationService.analyzeEscalationPatterns(customer, escalation, correlationId);
        
        // Publish escalation status update
        kafkaTemplate.send("customer-service-escalation-status-updates", Map.of(
            "escalationId", escalationId,
            "customerId", customerId,
            "ticketId", ticketId,
            "escalationType", escalationType,
            "priority", priority,
            "escalationLevel", escalationLevel,
            "assignedTo", assignedTo,
            "status", "ACTIVE",
            "slaResponseDeadline", escalation.getSlaDeadline().toString(),
            "slaResolutionDeadline", escalation.getSlaResolutionDeadline().toString(),
            "eventType", "SERVICE_ESCALATION_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to management dashboards
        kafkaTemplate.send("management-escalation-dashboard", Map.of(
            "escalationId", escalationId,
            "customerId", customerId,
            "customerName", customer.getFullName(),
            "customerTier", customer.getCustomerTier(),
            "ticketId", ticketId,
            "escalationType", escalationType,
            "priority", priority,
            "reason", reason,
            "escalationLevel", escalationLevel,
            "assignedTo", assignedTo,
            "requiresImmediateAction", requiresImmediateAction,
            "slaDeadline", escalation.getSlaDeadline().toString(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to quality assurance team
        kafkaTemplate.send("quality-assurance-escalations", Map.of(
            "escalationId", escalationId,
            "customerId", customerId,
            "ticketId", ticketId,
            "escalationType", escalationType,
            "reason", reason,
            "originalIssue", originalIssue,
            "escalatedBy", escalatedBy,
            "escalationLevel", escalationLevel,
            "requiresQualityReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to executive team if level 3 or higher
        if (escalationLevel >= 3) {
            kafkaTemplate.send("executive-customer-escalations", Map.of(
                "escalationId", escalationId,
                "customerId", customerId,
                "customerName", customer.getFullName(),
                "customerTier", customer.getCustomerTier(),
                "ticketId", ticketId,
                "escalationType", escalationType,
                "priority", priority,
                "reason", reason,
                "description", description,
                "escalationLevel", escalationLevel,
                "assignedTo", assignedTo,
                "requiresExecutiveAttention", true,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update operational metrics
        kafkaTemplate.send("operational-metrics-updates", Map.of(
            "metricType", "CUSTOMER_SERVICE_ESCALATION",
            "escalationType", escalationType,
            "priority", priority,
            "escalationLevel", escalationLevel,
            "customerTier", customer.getCustomerTier(),
            "department", escalationService.getDepartmentFromType(escalationType),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to training and improvement team
        kafkaTemplate.send("training-improvement-insights", Map.of(
            "insightType", "SERVICE_ESCALATION",
            "escalationId", escalationId,
            "escalationType", escalationType,
            "reason", reason,
            "originalIssue", originalIssue,
            "escalatedBy", escalatedBy,
            "escalationLevel", escalationLevel,
            "departmentInvolved", escalationService.getDepartmentFromType(escalationType),
            "trainingRequired", escalationService.assessTrainingNeeds(escalation),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logCustomerEvent(
            "CUSTOMER_SERVICE_ESCALATION_PROCESSED",
            customerId,
            Map.of(
                "escalationId", escalationId,
                "ticketId", ticketId,
                "escalationType", escalationType,
                "priority", priority,
                "reason", reason,
                "escalatedBy", escalatedBy,
                "escalatedTo", escalatedTo,
                "assignedTo", assignedTo,
                "escalationLevel", escalationLevel,
                "requiresImmediateAction", requiresImmediateAction,
                "slaResponseDeadline", escalation.getSlaDeadline().toString(),
                "slaResolutionDeadline", escalation.getSlaResolutionDeadline().toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("SERVICE ESCALATION FULLY PROCESSED - escalationId: {}, customerId: {}, level: {}, assignedTo: {}, correlationId: {}", 
            escalationId, customerId, escalationLevel, assignedTo, correlationId);
    }

    private String assignEscalationBasedOnLevel(CustomerServiceEscalation escalation, Customer customer, String correlationId) {
        switch (escalation.getEscalationLevel()) {
            case 1:
                return managerService.assignTierOneManager(escalation, customer, correlationId);
            case 2:
                return managerService.assignTierTwoManager(escalation, customer, correlationId);
            case 3:
                return managerService.assignSeniorManager(escalation, customer, correlationId);
            case 4:
                return managerService.assignDirector(escalation, customer, correlationId);
            case 5:
                return managerService.assignExecutive(escalation, customer, correlationId);
            default:
                return managerService.assignDefaultManager(escalation, customer, correlationId);
        }
    }

    private void processCustomerServiceEscalationFallback(
            String escalationId,
            String customerId,
            String ticketId,
            String escalationType,
            String priority,
            String reason,
            String description,
            String escalatedBy,
            String escalatedTo,
            Integer escalationLevel,
            Boolean requiresImmediateAction,
            String originalIssue,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for service escalation processing - escalationId: {}, customerId: {}, correlationId: {}, error: {}", 
            escalationId, customerId, correlationId, e.getMessage());
        
        // Try emergency escalation notification
        try {
            managerService.sendEmergencyEscalationAlert(
                escalationId, customerId, escalationType, priority, escalationLevel, correlationId);
        } catch (Exception fallbackException) {
            log.error("Emergency escalation alert failed - escalationId: {}, error: {}", 
                escalationId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("escalationId", escalationId);
        fallbackEvent.put("customerId", customerId);
        fallbackEvent.put("ticketId", ticketId);
        fallbackEvent.put("escalationType", escalationType);
        fallbackEvent.put("priority", priority);
        fallbackEvent.put("escalationLevel", escalationLevel);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("service-escalation-processing-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Customer service escalation message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("service-escalation-processing-failures", dltEvent);
            
            managerService.sendCriticalOperationalAlert(
                "Customer Service Escalation Processing Failed",
                String.format("CRITICAL: Failed to process customer service escalation after max retries. Customer service quality may be compromised. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process service escalation DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}