package com.waqiti.customer.service;

import com.waqiti.customer.dto.CreateComplaintRequest;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerComplaint;
import com.waqiti.customer.entity.CustomerComplaint.ComplaintType;
import com.waqiti.customer.entity.CustomerComplaint.Severity;
import com.waqiti.customer.entity.CustomerComplaint.Status;
import com.waqiti.customer.repository.CustomerComplaintRepository;
import com.waqiti.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complaint Management Service - Production-Ready Implementation
 *
 * Handles customer complaint management including:
 * - Complaint creation and tracking
 * - Assignment and routing
 * - Escalation handling
 * - Resolution tracking
 * - SLA monitoring
 * - CFPB submission
 * - Compliance reporting
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintManagementService {

    private final CustomerComplaintRepository customerComplaintRepository;
    private final CustomerRepository customerRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RegulatoryComplianceService regulatoryComplianceService;
    private final ComplaintRoutingService complaintRoutingService;

    private static final String COMPLAINT_EVENTS_TOPIC = "customer-complaint-events";
    private static final String COMPLAINT_ESCALATION_TOPIC = "complaint-escalation-events";
    private static final int STANDARD_SLA_HOURS = 48;
    private static final int HIGH_SEVERITY_SLA_HOURS = 24;
    private static final int CRITICAL_SLA_HOURS = 8;

    /**
     * Create a new customer complaint
     *
     * @param request Complaint creation request
     * @return Created complaint
     */
    @Transactional
    public CustomerComplaint createComplaint(CreateComplaintRequest request) {
        log.info("Creating complaint: customerId={}, type={}", request.getCustomerId(), request.getComplaintType());

        try {
            // Validate customer exists
            Customer customer = customerRepository.findByCustomerId(request.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

            // Determine severity if not provided
            Severity severity = request.getSeverity() != null
                    ? request.getSeverity()
                    : determineSeverity(request.getComplaintType(), request.getDescription());

            // Calculate SLA due date
            LocalDateTime slaDueDate = calculateSlaDueDate(severity);

            // Create complaint
            CustomerComplaint complaint = CustomerComplaint.builder()
                    .complaintId(UUID.randomUUID().toString())
                    .customerId(request.getCustomerId())
                    .complaintType(request.getComplaintType())
                    .complaintCategory(request.getComplaintCategory())
                    .severity(severity)
                    .status(Status.OPEN)
                    .description(request.getDescription())
                    .slaDueDate(slaDueDate)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Save complaint
            complaint = customerComplaintRepository.save(complaint);

            // Auto-route complaint
            String assignedTo = complaintRoutingService.routeComplaint(complaint);
            complaint.setAssignedTo(assignedTo);
            customerComplaintRepository.save(complaint);

            // Publish complaint created event
            publishComplaintEvent(complaint, "COMPLAINT_CREATED");

            // Check if regulatory notification required
            if (requiresRegulatoryNotification(complaint)) {
                regulatoryComplianceService.submitRegulatoryNotification(
                        "COMPLAINT_CREATED",
                        Map.of("complaintId", complaint.getComplaintId(),
                                "type", complaint.getComplaintType().name(),
                                "severity", severity.name())
                );
            }

            log.info("Complaint created: complaintId={}, assignedTo={}, slaDue={}",
                    complaint.getComplaintId(), assignedTo, slaDueDate);

            return complaint;

        } catch (Exception e) {
            log.error("Failed to create complaint: customerId={}", request.getCustomerId(), e);
            throw new RuntimeException("Failed to create complaint", e);
        }
    }

    /**
     * Assign complaint to a user/team
     *
     * @param complaintId Complaint ID
     * @param assignedTo User/team to assign to
     */
    @Transactional
    public void assignComplaint(String complaintId, String assignedTo) {
        log.info("Assigning complaint: complaintId={}, assignedTo={}", complaintId, assignedTo);

        try {
            CustomerComplaint complaint = customerComplaintRepository.findByComplaintId(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

            complaint.assignTo(assignedTo);
            customerComplaintRepository.save(complaint);

            publishComplaintEvent(complaint, "COMPLAINT_ASSIGNED");

            log.info("Complaint assigned: complaintId={}, assignedTo={}", complaintId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to assign complaint: complaintId={}", complaintId, e);
            throw new RuntimeException("Failed to assign complaint", e);
        }
    }

    /**
     * Escalate complaint to supervisor
     *
     * @param complaintId Complaint ID
     */
    @Transactional
    public void escalateComplaint(String complaintId) {
        log.warn("Escalating complaint: complaintId={}", complaintId);

        try {
            CustomerComplaint complaint = customerComplaintRepository.findByComplaintId(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

            // Update complaint status and severity
            complaint.escalate();
            customerComplaintRepository.save(complaint);

            // Publish escalation event
            Map<String, Object> escalationEvent = Map.of(
                    "eventType", "COMPLAINT_ESCALATED",
                    "complaintId", complaintId,
                    "customerId", complaint.getCustomerId(),
                    "severity", complaint.getSeverity().name(),
                    "escalatedAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send(COMPLAINT_ESCALATION_TOPIC, complaintId, escalationEvent);

            log.warn("Complaint escalated: complaintId={}, newSeverity={}", complaintId, complaint.getSeverity());

        } catch (Exception e) {
            log.error("Failed to escalate complaint: complaintId={}", complaintId, e);
            throw new RuntimeException("Failed to escalate complaint", e);
        }
    }

    /**
     * Resolve a complaint
     *
     * @param complaintId Complaint ID
     * @param resolution Resolution description
     */
    @Transactional
    public void resolveComplaint(String complaintId, String resolution) {
        log.info("Resolving complaint: complaintId={}", complaintId);

        try {
            CustomerComplaint complaint = customerComplaintRepository.findByComplaintId(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

            complaint.resolve(resolution);
            customerComplaintRepository.save(complaint);

            publishComplaintEvent(complaint, "COMPLAINT_RESOLVED");

            log.info("Complaint resolved: complaintId={}, resolutionTime={} hours",
                    complaintId, complaint.getResolutionTimeHours());

        } catch (Exception e) {
            log.error("Failed to resolve complaint: complaintId={}", complaintId, e);
            throw new RuntimeException("Failed to resolve complaint", e);
        }
    }

    /**
     * Check for SLA violations
     *
     * @return List of overdue complaints
     */
    public List<CustomerComplaint> checkSlaViolations() {
        log.info("Checking for SLA violations");

        try {
            List<CustomerComplaint> allComplaints = customerComplaintRepository.findByStatusIn(
                    Arrays.asList(Status.OPEN, Status.IN_PROGRESS, Status.ESCALATED)
            );

            List<CustomerComplaint> overdueComplaints = allComplaints.stream()
                    .filter(CustomerComplaint::isOverdue)
                    .collect(Collectors.toList());

            log.warn("Found {} SLA violations", overdueComplaints.size());

            // Auto-escalate severely overdue complaints
            for (CustomerComplaint complaint : overdueComplaints) {
                Long hoursOverdue = complaint.getHoursUntilSla();
                if (hoursOverdue != null && hoursOverdue < -24) {
                    escalateComplaint(complaint.getComplaintId());
                }
            }

            return overdueComplaints;

        } catch (Exception e) {
            log.error("Failed to check SLA violations", e);
            return Collections.emptyList();
        }
    }

    /**
     * Route complaint intelligently
     *
     * @param complaint Complaint to route
     */
    @Transactional
    public void routeComplaint(CustomerComplaint complaint) {
        log.info("Routing complaint: complaintId={}", complaint.getComplaintId());

        try {
            String assignedTo = complaintRoutingService.routeComplaint(complaint);
            complaint.setAssignedTo(assignedTo);
            customerComplaintRepository.save(complaint);

            log.info("Complaint routed: complaintId={}, assignedTo={}", complaint.getComplaintId(), assignedTo);

        } catch (Exception e) {
            log.error("Failed to route complaint: complaintId={}", complaint.getComplaintId(), e);
        }
    }

    /**
     * Track complaint metrics and KPIs
     *
     * @return Complaint metrics map
     */
    public Map<String, Object> trackComplaintMetrics() {
        log.info("Tracking complaint metrics");

        try {
            List<CustomerComplaint> allComplaints = customerComplaintRepository.findAll();

            long openCount = allComplaints.stream()
                    .filter(c -> c.getStatus() == Status.OPEN)
                    .count();

            long inProgressCount = allComplaints.stream()
                    .filter(c -> c.getStatus() == Status.IN_PROGRESS)
                    .count();

            long resolvedCount = allComplaints.stream()
                    .filter(CustomerComplaint::isResolved)
                    .count();

            long escalatedCount = allComplaints.stream()
                    .filter(CustomerComplaint::isEscalated)
                    .count();

            long overdueCount = allComplaints.stream()
                    .filter(CustomerComplaint::isOverdue)
                    .count();

            double averageResolutionTime = calculateAverageResolutionTime(allComplaints);

            Map<String, Object> metrics = Map.of(
                    "totalComplaints", allComplaints.size(),
                    "openComplaints", openCount,
                    "inProgressComplaints", inProgressCount,
                    "resolvedComplaints", resolvedCount,
                    "escalatedComplaints", escalatedCount,
                    "overdueComplaints", overdueCount,
                    "averageResolutionTimeHours", averageResolutionTime,
                    "measuredAt", LocalDateTime.now().toString()
            );

            log.info("Complaint metrics tracked: total={}, open={}, resolved={}",
                    allComplaints.size(), openCount, resolvedCount);

            return metrics;

        } catch (Exception e) {
            log.error("Failed to track complaint metrics", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Submit complaint to CFPB (Consumer Financial Protection Bureau)
     *
     * @param complaintId Complaint ID
     */
    @Transactional
    public void submitToCFPB(String complaintId) {
        log.info("Submitting complaint to CFPB: complaintId={}", complaintId);

        try {
            CustomerComplaint complaint = customerComplaintRepository.findByComplaintId(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

            // Mark as submitted to CFPB
            complaint.submitToCfpb();
            customerComplaintRepository.save(complaint);

            // Notify regulatory compliance service
            regulatoryComplianceService.submitRegulatoryNotification(
                    "CFPB_SUBMISSION",
                    Map.of("complaintId", complaintId,
                            "customerId", complaint.getCustomerId(),
                            "type", complaint.getComplaintType().name())
            );

            log.info("Complaint submitted to CFPB: complaintId={}, submissionDate={}",
                    complaintId, complaint.getCfpbSubmissionDate());

        } catch (Exception e) {
            log.error("Failed to submit complaint to CFPB: complaintId={}", complaintId, e);
            throw new RuntimeException("Failed to submit complaint to CFPB", e);
        }
    }

    /**
     * Generate compliance report for specified period
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Complaint report data
     */
    public Map<String, Object> generateComplaintReport(LocalDate startDate, LocalDate endDate) {
        log.info("Generating complaint report: startDate={}, endDate={}", startDate, endDate);

        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            List<CustomerComplaint> complaints = customerComplaintRepository.findAll().stream()
                    .filter(c -> c.getCreatedAt().isAfter(startDateTime) && c.getCreatedAt().isBefore(endDateTime))
                    .collect(Collectors.toList());

            Map<String, Long> complaintsByType = complaints.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.getComplaintType().name(),
                            Collectors.counting()
                    ));

            Map<String, Long> complaintsBySeverity = complaints.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.getSeverity().name(),
                            Collectors.counting()
                    ));

            Map<String, Object> report = Map.of(
                    "reportPeriod", Map.of("start", startDate.toString(), "end", endDate.toString()),
                    "totalComplaints", complaints.size(),
                    "complaintsByType", complaintsByType,
                    "complaintsBySeverity", complaintsBySeverity,
                    "resolvedCount", complaints.stream().filter(CustomerComplaint::isResolved).count(),
                    "averageResolutionTimeHours", calculateAverageResolutionTime(complaints),
                    "cfpbSubmissions", complaints.stream().filter(CustomerComplaint::isCfpbSubmitted).count(),
                    "generatedAt", LocalDateTime.now().toString()
            );

            log.info("Complaint report generated: total={}, period={} to {}",
                    complaints.size(), startDate, endDate);

            return report;

        } catch (Exception e) {
            log.error("Failed to generate complaint report", e);
            return Collections.emptyMap();
        }
    }

    // ==================== Private Helper Methods ====================

    private Severity determineSeverity(ComplaintType type, String description) {
        // Auto-determine severity based on complaint type and keywords
        if (type == ComplaintType.FRAUD ||
                type == ComplaintType.UNAUTHORIZED_TRANSACTION ||
                type == ComplaintType.DISCRIMINATION) {
            return Severity.CRITICAL;
        }

        if (type == ComplaintType.BILLING ||
                type == ComplaintType.FEES_CHARGES ||
                type == ComplaintType.TRANSACTION_DISPUTE) {
            return Severity.HIGH;
        }

        // Check description for urgency keywords
        if (description != null) {
            String lowerDesc = description.toLowerCase();
            if (lowerDesc.contains("urgent") || lowerDesc.contains("immediate") || lowerDesc.contains("critical")) {
                return Severity.HIGH;
            }
        }

        return Severity.MEDIUM;
    }

    private LocalDateTime calculateSlaDueDate(Severity severity) {
        int hoursToAdd = switch (severity) {
            case CRITICAL -> CRITICAL_SLA_HOURS;
            case HIGH -> HIGH_SEVERITY_SLA_HOURS;
            default -> STANDARD_SLA_HOURS;
        };

        return LocalDateTime.now().plusHours(hoursToAdd);
    }

    private boolean requiresRegulatoryNotification(CustomerComplaint complaint) {
        return complaint.getSeverity() == Severity.CRITICAL ||
                complaint.getComplaintType() == ComplaintType.FRAUD ||
                complaint.getComplaintType() == ComplaintType.DISCRIMINATION ||
                complaint.getComplaintType() == ComplaintType.COMPLIANCE;
    }

    private void publishComplaintEvent(CustomerComplaint complaint, String eventType) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", eventType,
                    "complaintId", complaint.getComplaintId(),
                    "customerId", complaint.getCustomerId(),
                    "type", complaint.getComplaintType().name(),
                    "severity", complaint.getSeverity().name(),
                    "status", complaint.getStatus().name(),
                    "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send(COMPLAINT_EVENTS_TOPIC, complaint.getComplaintId(), event);

        } catch (Exception e) {
            log.error("Failed to publish complaint event: complaintId={}", complaint.getComplaintId(), e);
        }
    }

    private double calculateAverageResolutionTime(List<CustomerComplaint> complaints) {
        return complaints.stream()
                .filter(CustomerComplaint::isResolved)
                .mapToLong(c -> c.getResolutionTimeHours() != null ? c.getResolutionTimeHours() : 0)
                .average()
                .orElse(0.0);
    }
}
