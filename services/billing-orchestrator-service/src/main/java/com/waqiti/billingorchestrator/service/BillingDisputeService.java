package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.CreateBillingDisputeRequest;
import com.waqiti.billingorchestrator.dto.request.ResolveDisputeRequest;
import com.waqiti.billingorchestrator.dto.response.BillingDisputeResponse;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.entity.BillingDispute;
import com.waqiti.billingorchestrator.entity.BillingDispute.*;
import com.waqiti.billingorchestrator.exception.BillingCycleNotFoundException;
import com.waqiti.billingorchestrator.exception.DisputeNotFoundException;
import com.waqiti.billingorchestrator.repository.BillingCycleRepository;
import com.waqiti.billingorchestrator.repository.BillingDisputeRepository;
import com.waqiti.common.alerting.AlertingService;
import com.waqiti.common.idempotency.Idempotent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Billing Dispute Service
 *
 * Manages complete dispute lifecycle with SLA tracking, workflow automation,
 * and compliance requirements.
 *
 * KEY RESPONSIBILITIES:
 * 1. Dispute creation and validation
 * 2. SLA monitoring and breach prevention
 * 3. Auto-assignment and escalation
 * 4. Resolution workflows (refund, credit note, rejection)
 * 5. Metrics and analytics
 *
 * SLA TARGETS:
 * - First response: 24 hours
 * - Resolution (simple cases): 5 business days
 * - Resolution (complex cases): 15 business days
 * - Escalated cases: 30 days max
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillingDisputeService {

    private final BillingDisputeRepository disputeRepository;
    private final BillingCycleRepository billingCycleRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final AlertingService alertingService;

    // Metrics
    private final Counter disputesCreated;
    private final Counter disputesResolved;
    private final Counter disputesEscalated;
    private final Counter slaBreaches;

    public BillingDisputeService(
            BillingDisputeRepository disputeRepository,
            BillingCycleRepository billingCycleRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            AlertingService alertingService) {
        this.disputeRepository = disputeRepository;
        this.billingCycleRepository = billingCycleRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.alertingService = alertingService;

        // Initialize metrics
        this.disputesCreated = Counter.builder("billing.disputes.created")
                .description("Total billing disputes created")
                .register(meterRegistry);
        this.disputesResolved = Counter.builder("billing.disputes.resolved")
                .description("Total billing disputes resolved")
                .register(meterRegistry);
        this.disputesEscalated = Counter.builder("billing.disputes.escalated")
                .description("Total billing disputes escalated")
                .register(meterRegistry);
        this.slaBreaches = Counter.builder("billing.disputes.sla.breaches")
                .description("Total SLA breaches in dispute resolution")
                .register(meterRegistry);
    }

    /**
     * Creates a new billing dispute
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Idempotent(
        keyExpression = "'billing-dispute:' + #request.billingCycleId + ':' + #customerId",
        serviceName = "billing-orchestrator-service",
        operationType = "CREATE_BILLING_DISPUTE",
        userIdExpression = "#customerId",
        ttlHours = 24
    )
    @CircuitBreaker(name = "billing-dispute-service", fallbackMethod = "createDisputeFallback")
    public BillingDisputeResponse createDispute(CreateBillingDisputeRequest request, UUID customerId) {
        log.info("Creating billing dispute for customer: {}, billing cycle: {}", customerId, request.getBillingCycleId());

        // Validate billing cycle exists
        BillingCycle billingCycle = billingCycleRepository.findById(request.getBillingCycleId())
                .orElseThrow(() -> new BillingCycleNotFoundException("Billing cycle not found: " + request.getBillingCycleId()));

        // Validate customer owns the billing cycle
        if (!billingCycle.getCustomerId().equals(customerId)) {
            log.error("SECURITY: Customer {} attempted to dispute billing cycle {} owned by {}",
                    customerId, request.getBillingCycleId(), billingCycle.getCustomerId());
            throw new SecurityException("Customer does not own this billing cycle");
        }

        // Check for duplicate disputes
        long activeDisputes = disputeRepository.countActiveDisputesByCustomer(customerId);
        if (activeDisputes >= 5) {
            log.warn("Customer {} has {} active disputes - possible abuse", customerId, activeDisputes);
            // Continue but flag for review
        }

        // Calculate priority
        DisputePriority priority = calculatePriority(request.getDisputedAmount());

        // Calculate SLA deadline
        LocalDateTime slaDeadline = calculateSlaDeadline(priority);

        // Create dispute
        BillingDispute dispute = BillingDispute.builder()
                .customerId(customerId)
                .billingCycleId(request.getBillingCycleId())
                .invoiceId(billingCycle.getInvoiceId())
                .invoiceNumber(billingCycle.getInvoiceNumber())
                .status(DisputeStatus.SUBMITTED)
                .reason(request.getReason())
                .disputedAmount(request.getDisputedAmount())
                .currency(request.getCurrency())
                .customerDescription(request.getDescription())
                .customerEvidenceUrl(request.getEvidenceUrl())
                .priority(priority)
                .escalated(false)
                .submittedAt(LocalDateTime.now())
                .slaDeadline(slaDeadline)
                .slaBreach(false)
                .build();

        // Auto-assign to available agent (simplified - would integrate with assignment service)
        dispute.setAssignedTo(findAvailableAgent());

        dispute = disputeRepository.save(dispute);

        // Increment metrics
        disputesCreated.increment();

        // Publish event
        publishDisputeEvent("DISPUTE_CREATED", dispute);

        // Send notification to billing team
        notifyBillingTeam(dispute);

        log.info("Billing dispute created successfully: {}, priority: {}, SLA deadline: {}",
                dispute.getId(), priority, slaDeadline);

        return mapToResponse(dispute);
    }

    /**
     * Retrieves dispute by ID
     */
    @Transactional(readOnly = true)
    public BillingDisputeResponse getDispute(UUID disputeId) {
        log.info("Retrieving dispute: {}", disputeId);

        BillingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found: " + disputeId));

        return mapToResponse(dispute);
    }

    /**
     * Retrieves disputes by customer
     */
    @Transactional(readOnly = true)
    public Page<BillingDisputeResponse> getCustomerDisputes(UUID customerId, Pageable pageable) {
        log.debug("Retrieving disputes for customer: {}", customerId);

        Page<BillingDispute> disputes = disputeRepository.findByCustomerId(customerId, pageable);
        return disputes.map(this::mapToResponse);
    }

    /**
     * Resolves a billing dispute
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "billing-dispute-service")
    public BillingDisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request, UUID resolvedBy) {
        log.info("Resolving dispute: {}, resolution: {}", disputeId, request.getResolutionType());

        BillingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found: " + disputeId));

        // Validate dispute can be resolved
        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.CLOSED) {
            throw new IllegalStateException("Dispute already resolved: " + disputeId);
        }

        // Update dispute
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionType(request.getResolutionType());
        dispute.setResolutionNotes(request.getResolutionNotes());
        dispute.setApprovedRefundAmount(request.getApprovedRefundAmount());
        dispute.setResolutionDate(LocalDateTime.now());
        dispute.setResolvedBy(resolvedBy);

        // Check SLA breach
        if (dispute.isSlaBreached()) {
            dispute.setSlaBreach(true);
            slaBreaches.increment();
            log.warn("SLA BREACH: Dispute {} resolved after deadline. Deadline: {}, Resolved: {}",
                    disputeId, dispute.getSlaDeadline(), dispute.getResolutionDate());
        }

        dispute = disputeRepository.save(dispute);

        // Increment metrics
        disputesResolved.increment();

        // Execute resolution actions
        executeResolution(dispute, request);

        // Publish event
        publishDisputeEvent("DISPUTE_RESOLVED", dispute);

        // Notify customer
        notifyCustomerResolution(dispute);

        log.info("Dispute resolved successfully: {}, resolution: {}, refund amount: {}",
                disputeId, request.getResolutionType(), request.getApprovedRefundAmount());

        return mapToResponse(dispute);
    }

    /**
     * Escalates a dispute to senior billing team
     */
    @Transactional
    public BillingDisputeResponse escalateDispute(UUID disputeId, String escalationReason) {
        log.info("Escalating dispute: {}, reason: {}", disputeId, escalationReason);

        BillingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found: " + disputeId));

        dispute.setEscalated(true);
        dispute.setEscalationReason(escalationReason);
        dispute.setStatus(DisputeStatus.ESCALATED);
        dispute.setPriority(DisputePriority.URGENT);

        // Extend SLA deadline for escalated cases (30 days total)
        dispute.setSlaDeadline(dispute.getSubmittedAt().plusDays(30));

        dispute = disputeRepository.save(dispute);

        // Increment metrics
        disputesEscalated.increment();

        // Publish event
        publishDisputeEvent("DISPUTE_ESCALATED", dispute);

        // Alert senior team
        alertSeniorTeam(dispute);

        log.warn("DISPUTE ESCALATED: {}, reason: {}", disputeId, escalationReason);

        return mapToResponse(dispute);
    }

    /**
     * Monitors SLA breaches and sends alerts
     */
    @Transactional
    public void monitorSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();
        List<DisputeStatus> activeStatuses = List.of(
                DisputeStatus.SUBMITTED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.PENDING_MERCHANT_RESPONSE,
                DisputeStatus.ESCALATED
        );

        // Find breached disputes
        List<BillingDispute> breachedDisputes = disputeRepository.findSlaBreachedDisputes(now, activeStatuses);

        for (BillingDispute dispute : breachedDisputes) {
            if (!Boolean.TRUE.equals(dispute.getSlaBreach())) {
                dispute.setSlaBreach(true);
                disputeRepository.save(dispute);
                slaBreaches.increment();

                log.error("SLA BREACH DETECTED: Dispute {}, customer: {}, age: {} hours, deadline: {}",
                        dispute.getId(), dispute.getCustomerId(), dispute.getDisputeAgeHours(), dispute.getSlaDeadline());

                // Send critical alert
                sendSlaBreachAlert(dispute);
            }
        }

        // Find disputes approaching SLA
        LocalDateTime slaWindow = now.plusHours(24);
        List<BillingDispute> approachingDisputes = disputeRepository.findDisputesApproachingSla(now, slaWindow, activeStatuses);

        for (BillingDispute dispute : approachingDisputes) {
            log.warn("SLA APPROACHING: Dispute {}, customer: {}, deadline in {} hours",
                    dispute.getId(), dispute.getCustomerId(),
                    java.time.Duration.between(now, dispute.getSlaDeadline()).toHours());

            // Send warning alert
            sendSlaWarningAlert(dispute);
        }
    }

    // ==================== Helper Methods ====================

    private DisputePriority calculatePriority(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
            return DisputePriority.URGENT;
        } else if (amount.compareTo(BigDecimal.valueOf(500)) > 0) {
            return DisputePriority.HIGH;
        } else if (amount.compareTo(BigDecimal.valueOf(50)) > 0) {
            return DisputePriority.MEDIUM;
        }
        return DisputePriority.LOW;
    }

    private LocalDateTime calculateSlaDeadline(DisputePriority priority) {
        LocalDateTime now = LocalDateTime.now();
        return switch (priority) {
            case URGENT -> now.plusDays(5);   // 5 business days
            case HIGH -> now.plusDays(10);    // 10 business days
            case MEDIUM -> now.plusDays(15);  // 15 business days
            case LOW -> now.plusDays(30);     // 30 days
        };
    }

    private UUID findAvailableAgent() {
        // Simplified - would integrate with user service to find agents with lowest workload
        // For now, return null (unassigned, will be picked up by assignment service)
        return null;
    }

    private void executeResolution(BillingDispute dispute, ResolveDisputeRequest request) {
        DisputeResolution resolution = request.getResolutionType();

        if (resolution == DisputeResolution.ACCEPTED_FULL_REFUND ||
            resolution == DisputeResolution.ACCEPTED_PARTIAL_REFUND ||
            resolution == DisputeResolution.RESOLVED_GOODWILL_REFUND) {

            // Initiate refund via payment service
            initiateRefund(dispute, request.getApprovedRefundAmount());

        } else if (resolution == DisputeResolution.ACCEPTED_CREDIT_NOTE) {

            // Create credit note
            createCreditNote(dispute, request.getApprovedRefundAmount(), request.getCreditNoteReason());
        }
    }

    private void initiateRefund(BillingDispute dispute, BigDecimal refundAmount) {
        try {
            log.info("Initiating refund for dispute: {}, amount: {}", dispute.getId(), refundAmount);

            // Publish refund event for payment service to process
            kafkaTemplate.send("billing.refund.requested",
                    dispute.getId().toString(),
                    String.format("{\"disputeId\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\"}",
                            dispute.getId(), refundAmount, dispute.getCurrency())
            );

        } catch (Exception e) {
            log.error("Failed to initiate refund for dispute: {}", dispute.getId(), e);
            throw new RuntimeException("Refund initiation failed", e);
        }
    }

    private void createCreditNote(BillingDispute dispute, BigDecimal creditAmount, String reason) {
        try {
            log.info("Creating credit note for dispute: {}, amount: {}", dispute.getId(), creditAmount);

            // Publish credit note event
            kafkaTemplate.send("billing.credit.issued",
                    dispute.getId().toString(),
                    String.format("{\"disputeId\":\"%s\",\"amount\":\"%s\",\"reason\":\"%s\"}",
                            dispute.getId(), creditAmount, reason)
            );

        } catch (Exception e) {
            log.error("Failed to create credit note for dispute: {}", dispute.getId(), e);
        }
    }

    private void publishDisputeEvent(String eventType, BillingDispute dispute) {
        try {
            kafkaTemplate.send("billing.dispute.events",
                    dispute.getId().toString(),
                    String.format("{\"eventType\":\"%s\",\"disputeId\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                            eventType, dispute.getId(), dispute.getStatus(), LocalDateTime.now())
            );
        } catch (Exception e) {
            log.error("Failed to publish dispute event: {}", eventType, e);
        }
    }

    private void notifyBillingTeam(BillingDispute dispute) {
        // Publish notification event for notification-service
        log.debug("Notifying billing team about dispute: {}", dispute.getId());
    }

    private void notifyCustomerResolution(BillingDispute dispute) {
        // Publish customer notification event
        log.debug("Notifying customer about dispute resolution: {}", dispute.getId());
    }

    private void alertSeniorTeam(BillingDispute dispute) {
        // Send high-priority alert to senior billing team
        log.warn("Alerting senior team about escalated dispute: {}", dispute.getId());

        alertingService.sendErrorAlert(
            "Billing Dispute Escalated to Senior Team",
            String.format("Dispute %s escalated due to complexity or amount exceeding threshold", dispute.getId()),
            "billing-orchestrator-service",
            Map.of(
                "disputeId", dispute.getId(),
                "customerId", dispute.getCustomerId(),
                "amount", dispute.getDisputedAmount(),
                "type", dispute.getDisputeType(),
                "severity", dispute.getSeverity(),
                "createdAt", dispute.getCreatedAt()
            )
        );
    }

    private void sendSlaBreachAlert(BillingDispute dispute) {
        log.error("SLA BREACH ALERT: Dispute: {}, Customer: {}, Age: {} hours",
                dispute.getId(), dispute.getCustomerId(), dispute.getDisputeAgeHours());

        alertingService.sendCriticalAlert(
            "🚨 Billing Dispute SLA BREACH",
            String.format("Dispute %s has exceeded SLA deadline (%d hours old)",
                dispute.getId(), dispute.getDisputeAgeHours()),
            "billing-orchestrator-service",
            Map.of(
                "disputeId", dispute.getId(),
                "customerId", dispute.getCustomerId(),
                "amount", dispute.getDisputedAmount(),
                "ageHours", dispute.getDisputeAgeHours(),
                "type", dispute.getDisputeType(),
                "severity", dispute.getSeverity(),
                "assignedTo", dispute.getAssignedTo() != null ? dispute.getAssignedTo() : "UNASSIGNED"
            )
        );
    }

    private void sendSlaWarningAlert(BillingDispute dispute) {
        log.warn("SLA WARNING: Dispute: {}, Deadline approaching", dispute.getId());
    }

    private BillingDisputeResponse mapToResponse(BillingDispute dispute) {
        return BillingDisputeResponse.builder()
                .id(dispute.getId())
                .customerId(dispute.getCustomerId())
                .billingCycleId(dispute.getBillingCycleId())
                .invoiceId(dispute.getInvoiceId())
                .invoiceNumber(dispute.getInvoiceNumber())
                .status(dispute.getStatus())
                .reason(dispute.getReason())
                .resolutionType(dispute.getResolutionType())
                .disputedAmount(dispute.getDisputedAmount())
                .approvedRefundAmount(dispute.getApprovedRefundAmount())
                .currency(dispute.getCurrency())
                .customerDescription(dispute.getCustomerDescription())
                .merchantResponse(dispute.getMerchantResponse())
                .resolutionNotes(dispute.getResolutionNotes())
                .assignedTo(dispute.getAssignedTo())
                .priority(dispute.getPriority())
                .escalated(dispute.getEscalated())
                .escalationReason(dispute.getEscalationReason())
                .submittedAt(dispute.getSubmittedAt())
                .merchantResponseDeadline(dispute.getMerchantResponseDeadline())
                .merchantRespondedAt(dispute.getMerchantRespondedAt())
                .resolutionDate(dispute.getResolutionDate())
                .resolvedBy(dispute.getResolvedBy())
                .slaBreach(dispute.getSlaBreach())
                .slaDeadline(dispute.getSlaDeadline())
                .disputeAgeHours(dispute.getDisputeAgeHours())
                .slaApproaching(dispute.isSlaApproaching())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }

    // Circuit breaker fallback
    private BillingDisputeResponse createDisputeFallback(CreateBillingDisputeRequest request, UUID customerId, Throwable throwable) {
        log.error("Circuit breaker activated for createDispute. Falling back. Error: {}", throwable.getMessage());
        throw new RuntimeException("Dispute service temporarily unavailable. Please try again later.", throwable);
    }
}
