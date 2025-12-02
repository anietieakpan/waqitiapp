package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.ComplaintManagementService;
import com.waqiti.customer.service.RegulatoryComplianceService;
import com.waqiti.customer.entity.CustomerComplaint;
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
 * Critical Event Consumer #126: Customer Complaint Event Consumer
 * Processes customer complaints with CFPB compliance and resolution tracking
 * Implements 12-step zero-tolerance processing for consumer protection regulations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerComplaintEventConsumer extends BaseKafkaConsumer {

    private final ComplaintManagementService complaintManagementService;
    private final RegulatoryComplianceService regulatoryComplianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-complaint-events", groupId = "customer-complaint-group")
    @CircuitBreaker(name = "customer-complaint-consumer")
    @Retry(name = "customer-complaint-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerComplaintEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-complaint-event");
        
        try {
            log.info("Step 1: Processing customer complaint event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String complaintId = eventData.path("complaintId").asText();
            String customerId = eventData.path("customerId").asText();
            String complaintType = eventData.path("complaintType").asText();
            String complaintCategory = eventData.path("complaintCategory").asText();
            String description = eventData.path("description").asText();
            String severity = eventData.path("severity").asText();
            String channel = eventData.path("channel").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted complaint details: complaintId={}, type={}, severity={}", 
                    complaintId, complaintType, severity);
            
            // Step 3: Create complaint record with unique tracking number
            CustomerComplaint complaint = complaintManagementService.createComplaint(
                    complaintId, customerId, complaintType, complaintCategory, description, 
                    severity, channel, timestamp);
            
            log.info("Step 3: Created complaint record: trackingNumber={}", complaint.getTrackingNumber());
            
            // Step 4: Classify complaint for routing and priority
            String classification = complaintManagementService.classifyComplaint(
                    complaintType, complaintCategory, description, severity);
            
            log.info("Step 4: Classified complaint: classification={}", classification);
            
            // Step 5: Determine if regulatory notification required (CFPB, OCC, FDIC)
            boolean regulatoryNotificationRequired = regulatoryComplianceService.assessRegulatoryNotification(
                    complaintType, complaintCategory, severity);
            
            if (regulatoryNotificationRequired) {
                log.warn("Step 5: Regulatory notification required for complaint: {}", complaintId);
                regulatoryComplianceService.notifyRegulators(complaintId, complaintType, timestamp);
            }
            
            // Step 6: Route complaint to appropriate department/team
            String assignedTeam = complaintManagementService.routeComplaint(
                    complaintId, classification, severity, timestamp);
            
            log.info("Step 6: Routed complaint to team: {}", assignedTeam);
            
            // Step 7: Set response deadline based on severity and regulations
            LocalDateTime responseDeadline = complaintManagementService.calculateResponseDeadline(
                    severity, complaintType, timestamp);
            
            log.info("Step 7: Set response deadline: {}", responseDeadline);
            
            // Step 8: Check for similar complaints (pattern detection)
            boolean patternDetected = complaintManagementService.detectComplaintPattern(
                    customerId, complaintType, complaintCategory, timestamp);
            
            if (patternDetected) {
                log.warn("Step 8: Complaint pattern detected, escalating: customerId={}", customerId);
                complaintManagementService.escalateToManagement(complaintId, "PATTERN_DETECTED", timestamp);
            }
            
            // Step 9: Send acknowledgment to customer
            complaintManagementService.sendComplaintAcknowledgment(customerId, complaintId, 
                    complaint.getTrackingNumber(), responseDeadline, channel, timestamp);
            
            log.info("Step 9: Sent acknowledgment to customer");
            
            // Step 10: Create case management workflow
            complaintManagementService.createWorkflowTasks(complaintId, assignedTeam, 
                    responseDeadline, timestamp);
            
            // Step 11: Update customer risk profile
            complaintManagementService.updateCustomerProfile(customerId, complaintType, 
                    severity, timestamp);
            
            log.info("Step 11: Updated customer risk profile");
            
            // Step 12: Archive complaint with audit trail
            complaintManagementService.archiveComplaint(complaintId, eventData.toString(), timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer complaint event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer complaint event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("complaintId") || 
            !eventData.has("customerId") || !eventData.has("complaintType")) {
            throw new IllegalArgumentException("Invalid customer complaint event structure");
        }
    }
}