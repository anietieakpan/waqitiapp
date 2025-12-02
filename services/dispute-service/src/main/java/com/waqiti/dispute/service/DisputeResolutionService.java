package com.waqiti.dispute.service;

import com.waqiti.dispute.dto.*;
import com.waqiti.dispute.entity.*;
import com.waqiti.dispute.exception.DatabaseOperationException;
import com.waqiti.dispute.exception.DisputeNotFoundException;
import com.waqiti.dispute.exception.DisputeProcessingException;
import com.waqiti.dispute.exception.ExternalServiceException;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.repository.DisputeEvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.KafkaException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dispute Resolution Service - ENHANCED FOR PRODUCTION
 *
 * Comprehensive service for end-to-end dispute resolution including:
 * - Dispute creation and validation
 * - Evidence submission and management
 * - Resolution processing and decision making
 * - Refund/chargeback execution
 * - Notification and audit trail
 * - Controller integration (13 methods)
 * - Kafka consumer integration (17 methods)
 *
 * @author Waqiti Dispute Team
 * @version 3.0.0-PRODUCTION
 * @since 2025-10-25
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DisputeResolutionService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final DisputeManagementService disputeManagementService;
    private final InvestigationService investigationService;
    private final ChargebackService chargebackService;
    private final NotificationService notificationService;
    private final DisputeAnalysisService disputeAnalysisService;
    private final DisputeNotificationService disputeNotificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final DistributedIdempotencyService idempotencyService;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Create a new dispute
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Dispute createDispute(DisputeRequest request) {
        log.info("Creating dispute for transaction: {} by user: {}",
            request.getTransactionId(), request.getUserId());

        // Validate no duplicate dispute exists
        List<Dispute> existing = disputeRepository.findByUserId(request.getUserId());
        for (Dispute existingDispute : existing) {
            if (existingDispute.getTransactionId().equals(request.getTransactionId()) &&
                existingDispute.getStatus() == DisputeStatus.OPEN) {
                throw new IllegalStateException("Active dispute already exists for transaction: " +
                    request.getTransactionId());
            }
        }

        // Create dispute entity
        Dispute dispute = Dispute.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .disputeType(request.getDisputeType())
            .status(DisputeStatus.OPEN)
            .priority(DisputePriority.MEDIUM)
            .reason(request.getReason())
            .description(request.getDescription())
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();

        // Save dispute
        Dispute savedDispute = disputeRepository.save(dispute);

        // Send notification
        notificationService.sendDisputeStatusNotification(
            request.getUserId(),
            savedDispute.getId(),
            "CREATED"
        );

        // Start investigation
        investigationService.startInvestigation(savedDispute.getId());

        log.info("Dispute created successfully: {}", savedDispute.getId());
        return savedDispute;
    }

    /**
     * Submit evidence for a dispute
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DisputeEvidence submitEvidence(String disputeId, EvidenceSubmission submission) {
        log.info("Submitting evidence for dispute: {}", disputeId);

        // Validate dispute exists and is active
        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        if (dispute.getStatus() == DisputeStatus.CLOSED || dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new IllegalStateException("Cannot submit evidence for closed dispute: " + disputeId);
        }

        // Create evidence entity
        DisputeEvidence evidence = DisputeEvidence.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(disputeId)
            .evidenceType(submission.getEvidenceType())
            .submittedBy(submission.getSubmittedBy())
            .submittedAt(LocalDateTime.now())
            .documentUrl(submission.getDocumentUrl())
            .description(submission.getDescription())
            .verificationStatus(VerificationStatus.PENDING)
            .systemGenerated(false)
            .build();

        // Save evidence
        DisputeEvidence savedEvidence = disputeEvidenceRepository.save(evidence);

        // Update dispute timestamp
        dispute.setLastUpdated(LocalDateTime.now());
        disputeRepository.save(dispute);

        log.info("Evidence submitted successfully: {}", savedEvidence.getId());
        return savedEvidence;
    }

    /**
     * Resolve a dispute
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Dispute resolveDispute(String disputeId, ResolutionRequest resolution) {
        log.info("Resolving dispute: {} with decision: {}", disputeId, resolution.getDecision());

        // Get dispute
        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        // Validate can be resolved
        if (dispute.getStatus() == DisputeStatus.CLOSED || dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new IllegalStateException("Dispute already resolved: " + disputeId);
        }

        // Apply resolution
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionDecision(resolution.getDecision());
        dispute.setResolutionReason(resolution.getReason());
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setLastUpdated(LocalDateTime.now());

        // Set refund amount if applicable
        if (resolution.getRefundAmount() != null && resolution.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            dispute.setRefundAmount(resolution.getRefundAmount());
        }

        // Save dispute
        Dispute resolvedDispute = disputeRepository.save(dispute);

        // Send notification
        notificationService.sendDisputeResolutionNotification(
            dispute.getUserId(),
            disputeId,
            resolution.getDecision().name()
        );

        // If chargeback needed
        if (resolution.getDecision() == ResolutionDecision.FAVOR_CUSTOMER && resolution.getRefundAmount() != null) {
            chargebackService.processChargeback(disputeId, "DISPUTE_RESOLVED", resolution.getReason());
        }

        log.info("Dispute resolved successfully: {}", disputeId);
        return resolvedDispute;
    }

    /**
     * Get dispute by ID
     */
    @Transactional(readOnly = true)
    public Dispute getDispute(String disputeId) {
        log.debug("Retrieving dispute: {}", disputeId);
        return disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));
    }

    /**
     * Get all evidence for a dispute
     */
    @Transactional(readOnly = true)
    public List<DisputeEvidence> getDisputeEvidence(String disputeId) {
        log.debug("Retrieving evidence for dispute: {}", disputeId);
        return disputeEvidenceRepository.findByDisputeId(disputeId);
    }

    /**
     * Close a dispute
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Dispute closeDispute(String disputeId, String reason) {
        log.info("Closing dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setStatus(DisputeStatus.CLOSED);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setResolutionReason(reason);
        dispute.setLastUpdated(LocalDateTime.now());

        Dispute closedDispute = disputeRepository.save(dispute);

        notificationService.sendDisputeStatusNotification(
            dispute.getUserId(),
            disputeId,
            "CLOSED"
        );

        log.info("Dispute closed: {}", disputeId);
        return closedDispute;
    }

    /**
     * Get disputes by user
     */
    @Transactional(readOnly = true)
    public List<Dispute> getUserDisputes(String userId) {
        log.debug("Retrieving disputes for user: {}", userId);
        return disputeRepository.findByUserId(userId);
    }

    /**
     * Get disputes by status
     */
    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByStatus(DisputeStatus status) {
        log.debug("Retrieving disputes with status: {}", status);
        return disputeRepository.findByStatus(status.name());
    }

    // ==================== CONTROLLER INTEGRATION METHODS (NEW) ====================

    /**
     * Create dispute - Controller signature
     * Converts CreateDisputeRequest DTO → Creates dispute → Returns DisputeDTO
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DisputeDTO createDispute(CreateDisputeRequest request) {
        log.info("Creating dispute (Controller) for transaction: {} by user: {}",
                request.getTransactionId(), request.getInitiatorId());

        // Reuse existing logic with DisputeRequest
        DisputeRequest internalRequest = DisputeRequest.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getInitiatorId())
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                .description(request.getDescription())
                .build();

        Dispute dispute = createDispute(internalRequest);

        // Set additional fields from CreateDisputeRequest
        if (request.getMerchantId() != null) {
            dispute.setMerchantId(request.getMerchantId());
        }
        if (request.getAmount() != null) {
            dispute.setAmount(request.getAmount());
            dispute.setCurrency(request.getCurrency());
            dispute.setPriority(calculatePriority(request.getAmount(), request.getDisputeType()));
        }

        dispute = disputeRepository.save(dispute);
        return convertToDTO(dispute);
    }

    /**
     * Get dispute with user access validation
     */
    @Transactional(readOnly = true)
    public DisputeDTO getDispute(String disputeId, String userId) {
        log.debug("Retrieving dispute (Controller): {} for user: {}", disputeId, userId);

        Dispute dispute = getDispute(disputeId);

        // Validate user has access
        if (!dispute.getUserId().equals(userId)) {
            throw new AccessDeniedException("User not authorized to access this dispute");
        }

        return convertToDTO(dispute);
    }

    /**
     * Get user disputes with pagination and filtering
     */
    @Transactional(readOnly = true)
    public Page<DisputeDTO> getUserDisputes(String userId, DisputeStatus status,
                                             LocalDateTime startDate, LocalDateTime endDate,
                                             Pageable pageable) {
        log.debug("Fetching disputes (Controller) for user: {}", userId);

        List<Dispute> disputes = getUserDisputes(userId);

        // Apply filters
        List<Dispute> filtered = disputes.stream()
                .filter(d -> status == null || d.getStatus() == status)
                .filter(d -> startDate == null || !d.getCreatedAt().isBefore(startDate))
                .filter(d -> endDate == null || !d.getCreatedAt().isAfter(endDate))
                .sorted(Comparator.comparing(Dispute::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Dispute> page = start < filtered.size() ? filtered.subList(start, end) : List.of();

        List<DisputeDTO> dtos = page.stream().map(this::convertToDTO).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, filtered.size());
    }

    /**
     * Update dispute status - Controller signature
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DisputeDTO updateDisputeStatus(UpdateDisputeStatusRequest request) {
        log.info("Updating dispute (Controller) {} status to: {}", request.getDisputeId(), request.getNewStatus());

        Dispute dispute = disputeManagementService.updateDisputeStatus(
                request.getDisputeId(),
                request.getNewStatus(),
                request.getReason()
        );

        return convertToDTO(dispute);
    }

    /**
     * Add evidence - Controller signature
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvidenceDTO addEvidence(AddEvidenceRequest request) {
        log.info("Adding evidence (Controller) to dispute: {}", request.getDisputeId());

        EvidenceSubmission submission = EvidenceSubmission.builder()
                .evidenceType(EvidenceType.valueOf(request.getEvidenceType()))
                .submittedBy(request.getUploadedBy())
                .description(request.getDescription())
                .documentUrl("file://" + request.getFile().getOriginalFilename()) // TODO: Use SecureFileUploadService
                .build();

        DisputeEvidence evidence = submitEvidence(request.getDisputeId(), submission);
        return convertToEvidenceDTO(evidence);
    }

    /**
     * Escalate dispute - Controller signature
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DisputeDTO escalateDispute(EscalateDisputeRequest request) {
        log.warn("Escalating dispute (Controller): {}", request.getDisputeId());

        Dispute dispute = disputeRepository.findByDisputeId(request.getDisputeId())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + request.getDisputeId()));

        dispute.setEscalationLevel(dispute.getEscalationLevel() + 1);
        dispute.setEscalatedAt(LocalDateTime.now());
        dispute.setEscalationReason(request.getEscalationReason());
        dispute.setPriority(dispute.getEscalationLevel() >= 3 ? DisputePriority.CRITICAL : DisputePriority.HIGH);
        dispute.setLastUpdated(LocalDateTime.now());

        Dispute escalated = disputeRepository.save(dispute);

        disputeNotificationService.notifyDisputeTeam(
                UUID.fromString(dispute.getId()),
                UUID.fromString(dispute.getUserId()),
                "ESCALATION",
                "ESCALATED",
                dispute.getAmount(),
                dispute.getCurrency(),
                request.getEscalationReason()
        );

        return convertToDTO(escalated);
    }

    /**
     * Search disputes with criteria
     */
    @Transactional(readOnly = true)
    public Page<DisputeDTO> searchDisputes(DisputeSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching disputes (Controller) with criteria: {}", criteria);

        List<Dispute> allDisputes = disputeRepository.findAll();

        List<Dispute> filtered = allDisputes.stream()
                .filter(d -> criteria.getStatus() == null || d.getStatus() == criteria.getStatus())
                .filter(d -> criteria.getSearchTerm() == null ||
                        d.getReason().toLowerCase().contains(criteria.getSearchTerm().toLowerCase()))
                .filter(d -> criteria.getStartDate() == null || !d.getCreatedAt().isBefore(criteria.getStartDate()))
                .filter(d -> criteria.getEndDate() == null || !d.getCreatedAt().isAfter(criteria.getEndDate()))
                .sorted(Comparator.comparing(Dispute::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Dispute> page = start < filtered.size() ? filtered.subList(start, end) : List.of();

        List<DisputeDTO> dtos = page.stream().map(this::convertToDTO).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, filtered.size());
    }

    /**
     * Get dispute statistics
     */
    @Transactional(readOnly = true)
    public DisputeStatistics getDisputeStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching dispute statistics (Controller)");

        List<Dispute> disputes = startDate != null && endDate != null
                ? disputeRepository.findByDateRange(startDate, endDate)
                : disputeRepository.findAll();

        return DisputeStatistics.builder()
                .totalDisputes((long) disputes.size())
                .openDisputes(disputes.stream().filter(d -> d.getStatus() == DisputeStatus.OPEN).count())
                .resolvedDisputes(disputes.stream().filter(d -> d.getStatus() == DisputeStatus.RESOLVED).count())
                .rejectedDisputes(disputes.stream().filter(d -> d.getStatus() == DisputeStatus.REJECTED).count())
                .totalAmount(disputes.stream().map(Dispute::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .averageResolutionDays(disputes.stream().filter(d -> d.getResolvedAt() != null)
                        .mapToLong(Dispute::getAgeInDays).average().orElse(0.0))
                .build();
    }

    /**
     * Export disputes
     */
    @Transactional(readOnly = true)
    public byte[] exportDisputes(ExportRequest request) {
        log.info("Exporting disputes (Controller) in {} format", request.getFormat());

        List<Dispute> disputes = request.getStartDate() != null && request.getEndDate() != null
                ? disputeRepository.findByDateRange(request.getStartDate(), request.getEndDate())
                : disputeRepository.findAll();

        if (request.getStatus() != null) {
            disputes = disputes.stream().filter(d -> d.getStatus() == request.getStatus()).collect(Collectors.toList());
        }

        return switch (request.getFormat().toUpperCase()) {
            case "CSV" -> generateCSV(disputes);
            case "JSON" -> generateJSON(disputes);
            default -> throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        };
    }

    /**
     * Get dispute timeline
     */
    @Transactional(readOnly = true)
    public List<DisputeTimelineEvent> getDisputeTimeline(String disputeId, String userId) {
        log.debug("Fetching timeline (Controller) for dispute: {}", disputeId);

        Dispute dispute = getDispute(disputeId, userId);
        List<DisputeTimelineEvent> timeline = new ArrayList<>();

        timeline.add(DisputeTimelineEvent.builder()
                .eventType("CREATED").timestamp(dispute.getCreatedAt())
                .description("Dispute created").build());

        if (dispute.getEscalatedAt() != null) {
            timeline.add(DisputeTimelineEvent.builder()
                    .eventType("ESCALATED").timestamp(dispute.getEscalatedAt())
                    .description("Escalated: " + dispute.getEscalationReason()).build());
        }

        if (dispute.getResolvedAt() != null) {
            timeline.add(DisputeTimelineEvent.builder()
                    .eventType("RESOLVED").timestamp(dispute.getResolvedAt())
                    .description("Resolved: " + dispute.getResolutionDecision()).build());
        }

        return timeline.stream().sorted(Comparator.comparing(DisputeTimelineEvent::getTimestamp)).collect(Collectors.toList());
    }

    /**
     * Bulk update disputes
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BulkUpdateResult bulkUpdateDisputes(BulkUpdateRequest request) {
        log.info("Bulk updating (Controller) {} disputes", request.getDisputeIds().size());

        int successCount = 0, failureCount = 0;
        List<String> errors = new ArrayList<>();

        for (String disputeId : request.getDisputeIds()) {
            try {
                Optional<Dispute> optDispute = disputeRepository.findByDisputeId(disputeId);
                if (optDispute.isPresent()) {
                    Dispute dispute = optDispute.get();
                    if (request.getNewStatus() != null) dispute.setStatus(request.getNewStatus());
                    if (request.getPriority() != null) dispute.setPriority(request.getPriority());
                    dispute.setLastUpdated(LocalDateTime.now());
                    disputeRepository.save(dispute);
                    successCount++;
                } else {
                    errors.add("Not found: " + disputeId);
                    failureCount++;
                }
            } catch (DisputeProcessingException | DataAccessException e) {
                errors.add("Failed " + disputeId + ": " + e.getMessage());
                failureCount++;
                log.warn("Bulk resolution failed for dispute: {}", disputeId, e);
            }
        }

        return BulkUpdateResult.builder().successCount(successCount).failureCount(failureCount).errors(errors).build();
    }

    /**
     * Get dispute categories
     */
    @Transactional(readOnly = true)
    public List<String> getDisputeCategories() {
        return Arrays.stream(DisputeType.values()).map(Enum::name).collect(Collectors.toList());
    }

    /**
     * Get resolution templates
     */
    @Transactional(readOnly = true)
    public List<ResolutionTemplate> getResolutionTemplates(String category) {
        // FIXED: Load from database
        try {
            String sql = """
                SELECT id, category, title, description, resolution_type,
                       refund_percentage, requires_evidence, auto_approve_threshold
                FROM resolution_templates
                WHERE category = ? OR category = 'GENERAL'
                ORDER BY priority DESC
                """;

            List<ResolutionTemplate> templates = jdbcTemplate.query(sql, (rs, rowNum) ->
                ResolutionTemplate.builder()
                    .id(rs.getString("id"))
                    .category(rs.getString("category"))
                    .title(rs.getString("title"))
                    .description(rs.getString("description"))
                    .resolutionType(rs.getString("resolution_type"))
                    .refundPercentage(rs.getBigDecimal("refund_percentage"))
                    .requiresEvidence(rs.getBoolean("requires_evidence"))
                    .autoApproveThreshold(rs.getBigDecimal("auto_approve_threshold"))
                    .build(),
                category
            );

            // Fallback to defaults if database is empty
            if (templates.isEmpty()) {
                templates = List.of(
                    ResolutionTemplate.builder().id("TPL-001").category("UNAUTHORIZED_CHARGE")
                            .title("Full Refund").build(),
                    ResolutionTemplate.builder().id("TPL-002").category("PRODUCT_NOT_RECEIVED")
                            .title("Full Refund - Non-delivery").build()
                );
            }

            return templates;

        } catch (DataAccessException e) {
            log.error("Database error loading resolution templates - using defaults", e);
            // Fallback to hardcoded defaults
            List<ResolutionTemplate> templates = List.of(
                    ResolutionTemplate.builder().id("TPL-001").category("UNAUTHORIZED_CHARGE")
                            .title("Full Refund").build(),
                    ResolutionTemplate.builder().id("TPL-002").category("PRODUCT_NOT_RECEIVED")
                            .title("Full Refund - Non-delivery").build()
            );
        return category != null ? templates.stream().filter(t -> t.getCategory().equals(category)).collect(Collectors.toList()) : templates;
    }

    // ==================== KAFKA CONSUMER INTEGRATION METHODS (NEW) ====================

    /**
     * Process auto-resolution (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processAutoResolution(UUID disputeId, UUID customerId, String resolutionType,
            String resolutionDecision, BigDecimal disputeAmount, String currency, String disputeReason,
            String disputeCategory, LocalDateTime resolutionTimestamp, String aiConfidenceScore,
            Map<String, Object> resolutionEvidence, String resolutionExplanation) {
        log.info("Processing auto-resolution (Kafka): disputeId={}, decision={}", disputeId, resolutionDecision);

        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionReason(resolutionExplanation);
        dispute.setResolvedAt(resolutionTimestamp);
        dispute.setAutoResolutionAttempted(true);
        dispute.setAutoResolutionScore(Double.parseDouble(aiConfidenceScore));
        disputeRepository.save(dispute);
    }

    /**
     * Approve dispute (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void approveDispute(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, List<String> supportingDocuments) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionDecision(ResolutionDecision.FAVOR_CUSTOMER);
        dispute.setRefundAmount(disputeAmount);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
    }

    /**
     * Deny dispute (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void denyDispute(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, List<String> supportingDocuments) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.REJECTED);
        dispute.setResolutionDecision(ResolutionDecision.FAVOR_MERCHANT);
        disputeRepository.save(dispute);
    }

    /**
     * Partially approve dispute (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void partiallyApproveDispute(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, List<String> supportingDocuments) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionDecision(ResolutionDecision.PARTIAL_REFUND);
        dispute.setRefundAmount(disputeAmount);
        disputeRepository.save(dispute);
    }

    /**
     * Issue chargeback (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void issueChargeback(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, String merchantId, String transactionId) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setChargebackAmount(disputeAmount);
        dispute.setChargebackCode("CB-" + System.currentTimeMillis());
        disputeRepository.save(dispute);
        chargebackService.processChargeback(disputeId.toString(), "CHARGEBACK_ISSUED", "Customer dispute");
    }

    /**
     * Assign merchant liability (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignMerchantLiability(UUID disputeId, UUID customerId, String merchantId,
            BigDecimal disputeAmount, String currency) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setResolutionDecision(ResolutionDecision.FAVOR_CUSTOMER);
        disputeRepository.save(dispute);
    }

    /**
     * Assign customer liability (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void assignCustomerLiability(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, Map<String, Object> fraudIndicators) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setResolutionDecision(ResolutionDecision.FAVOR_MERCHANT);
        dispute.setStatus(DisputeStatus.REJECTED);
        disputeRepository.save(dispute);
    }

    /**
     * Escalate for manual review (Kafka)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void escalateForManualReview(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, List<String> supportingDocuments) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.ESCALATED);
        dispute.setEscalationLevel(dispute.getEscalationLevel() + 1);
        dispute.setPriority(DisputePriority.HIGH);
        disputeRepository.save(dispute);
    }

    /**
     * Update dispute status - UUID version (Kafka)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateDisputeStatus(UUID disputeId, UUID customerId, String newStatus,
            String resolutionType, LocalDateTime resolutionTimestamp, String aiConfidenceScore, Boolean requiresManualReview) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.valueOf(newStatus));
        if (requiresManualReview) dispute.setEscalationLevel(dispute.getEscalationLevel() + 1);
        disputeRepository.save(dispute);
    }

    /**
     * Process refund (Kafka) - WITH IDEMPOTENCY PROTECTION
     *
     * CRITICAL: Prevents duplicate refunds due to:
     * - Kafka message redelivery
     * - Consumer restarts
     * - Network retries
     * - Manual replay
     *
     * @param disputeId Dispute ID
     * @param customerId Customer to refund
     * @param disputeAmount Refund amount
     * @param currency Currency code
     * @param transactionId Original transaction ID
     * @param disputeReason Reason for refund
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "walletService", fallbackMethod = "processRefundFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "walletService")
    public void processRefund(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, String transactionId, String disputeReason) {

        log.info("Processing refund for dispute: {}, customer: {}, amount: {} {}",
            disputeId, customerId, disputeAmount, currency);

        // STEP 1: Generate idempotency key
        String idempotencyKey = "refund:dispute:" + disputeId.toString();

        // STEP 2: Check if already processed (prevents duplicate refunds)
        if (idempotencyService.checkAndMarkProcessed(
                idempotencyKey,
                "processRefund",
                java.time.Duration.ofDays(90))) { // Keep for 90 days (chargeback window)

            log.warn("Refund already processed for dispute: {} - Skipping duplicate", disputeId);
            return; // Exit early - refund already issued
        }

        try {
            // STEP 3: Load dispute and validate state
            Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                    .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

            // Validate refund hasn't been processed
            if (dispute.isRefundProcessed()) {
                log.warn("Refund already marked as processed in dispute record: {}", disputeId);
                return;
            }

            // Validate amount matches dispute
            if (disputeAmount.compareTo(dispute.getDisputeAmount()) > 0) {
                throw new IllegalArgumentException(
                    "Refund amount " + disputeAmount + " exceeds dispute amount " + dispute.getDisputeAmount());
            }

            // STEP 4: Update dispute record (in transaction)
            dispute.setRefundAmount(disputeAmount);
            dispute.setRefundProcessed(true);
            dispute.setRefundProcessedAt(java.time.LocalDateTime.now());
            disputeRepository.save(dispute);

            // STEP 5: Call wallet-service to process refund
            Map<String, Object> refundRequest = new java.util.HashMap<>();
            refundRequest.put("idempotencyKey", idempotencyKey); // Pass to wallet-service
            refundRequest.put("disputeId", disputeId.toString());
            refundRequest.put("customerId", customerId.toString());
            refundRequest.put("amount", disputeAmount);
            refundRequest.put("currency", currency);
            refundRequest.put("originalTransactionId", transactionId);
            refundRequest.put("reason", disputeReason);
            refundRequest.put("refundType", "DISPUTE_RESOLUTION");
            refundRequest.put("requestTimestamp", java.time.Instant.now().toString());

            // Get wallet service URL
            String walletServiceUrl = System.getenv("WALLET_SERVICE_URL");
            if (walletServiceUrl == null) walletServiceUrl = "http://wallet-service:8082";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Idempotency-Key", idempotencyKey);

            org.springframework.http.HttpEntity<Map<String, Object>> request =
                new org.springframework.http.HttpEntity<>(refundRequest, headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                walletServiceUrl + "/api/v1/refunds",
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ Refund processed successfully for dispute: {}, amount: {} {} - Idempotency key: {}",
                    disputeId, disputeAmount, currency, idempotencyKey);

                // STEP 6: Publish refund success event
                publishRefundSuccessEvent(disputeId, customerId, disputeAmount, currency, transactionId);

            } else {
                log.error("Wallet service returned non-success status: {}", response.getStatusCode());
                throw new DisputeProcessingException("Wallet service refund failed with status: " + response.getStatusCode());
            }

        } catch (RestClientException | DisputeProcessingException e) {
            log.error("CRITICAL: Failed to process refund for dispute: {} - Removing idempotency lock to allow retry",
                disputeId, e);

            // IMPORTANT: Remove idempotency record to allow retry
            idempotencyService.removeProcessed(idempotencyKey);

            // Re-throw to trigger retry logic
            throw new RuntimeException("Refund processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method when wallet service is unavailable
     */
    private void processRefundFallback(UUID disputeId, UUID customerId, BigDecimal disputeAmount,
            String currency, String transactionId, String disputeReason, Exception e) {

        log.error("CRITICAL ALERT: Wallet service circuit breaker activated - Cannot process refund for dispute: {}",
            disputeId, e);

        // Mark dispute as requiring manual refund processing
        disputeRepository.findByDisputeId(disputeId.toString()).ifPresent(dispute -> {
            dispute.setRequiresManualIntervention(true);
            dispute.setManualInterventionReason(
                "REFUND_FAILED_WALLET_SERVICE_UNAVAILABLE: " + e.getMessage());
            dispute.setRefundProcessed(false); // Ensure not marked as processed
            disputeRepository.save(dispute);
        });

        // Send urgent alert to operations team
        sendOperationsAlert("URGENT: Refund failed for dispute " + disputeId +
            ". Amount: $" + disputeAmount + " " + currency +
            ". Customer: " + customerId +
            ". MANUAL PROCESSING REQUIRED IMMEDIATELY. " +
            "Reason: Wallet service unavailable - " + e.getMessage());

        // Don't throw - let consumer continue, manual intervention will handle
        log.error("Refund marked for manual processing - dispute: {}", disputeId);
    }

    /**
     * Publish refund success event
     */
    private void publishRefundSuccessEvent(UUID disputeId, UUID customerId,
            BigDecimal amount, String currency, String transactionId) {
        try {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put("eventType", "DISPUTE_REFUND_PROCESSED");
            event.put("eventId", UUID.randomUUID().toString());
            event.put("disputeId", disputeId.toString());
            event.put("customerId", customerId.toString());
            event.put("amount", amount);
            event.put("currency", currency);
            event.put("transactionId", transactionId);
            event.put("timestamp", java.time.Instant.now().toString());

            kafkaTemplate.send("dispute.refund.processed", event);
            log.debug("Refund success event published for dispute: {}", disputeId);

        } catch (KafkaException e) {
            log.warn("Failed to publish refund success event (non-critical) - event will be missed", e);
            // Don't fail refund if event publishing fails
        }
    }

    /**
     * Send operations alert
     */
    private void sendOperationsAlert(String message) {
        try {
            // Implementation depends on notification service
            log.error("OPERATIONS ALERT: {}", message);
            // TODO: Integrate with PagerDuty, Slack, or email alerting
        } catch (RestClientException | KafkaException e) {
            log.error("Failed to send operations alert - manual intervention required", e);
        }
    }

    /**
     * Process chargeback adjustment (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processChargebackAdjustment(UUID disputeId, UUID customerId, String merchantId,
            BigDecimal disputeAmount, String currency, String transactionId) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setChargebackAmount(disputeAmount);
        disputeRepository.save(dispute);
    }

    /**
     * Process merchant liability adjustment (Kafka)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processMerchantLiabilityAdjustment(UUID disputeId, String merchantId,
            BigDecimal disputeAmount, String currency, String disputeReason) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        disputeRepository.save(dispute);

        // FIXED: Call merchant service to debit
        try {
            Map<String, Object> debitRequest = Map.of(
                "disputeId", disputeId.toString(),
                "merchantId", merchantId,
                "amount", disputeAmount,
                "currency", currency,
                "reason", disputeReason,
                "transactionType", "CHARGEBACK_DEBIT"
            );

            // Call merchant service REST API
            String merchantServiceUrl = System.getenv("MERCHANT_SERVICE_URL");
            if (merchantServiceUrl == null) merchantServiceUrl = "http://merchant-service:8086";

            restTemplate.postForEntity(
                merchantServiceUrl + "/api/v1/merchants/" + merchantId + "/debits",
                debitRequest,
                String.class
            );

            log.info("Merchant liability adjustment processed for dispute: {}, merchant: {}, amount: {} {}",
                disputeId, merchantId, disputeAmount, currency);

        } catch (RestClientException e) {
            log.error("Failed to process merchant liability adjustment for dispute: {}", disputeId, e);
            throw new ExternalServiceException("wallet-service", "Merchant debit failed: " + e.getMessage(), 503, e);
        }
    }

    /**
     * Record processing failure (Kafka)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordProcessingFailure(UUID disputeId, UUID customerId, String resolutionType,
            String resolutionDecision, String errorMessage) {
        try {
            Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString()).orElse(null);
            if (dispute != null) {
                if (dispute.getMetadata() == null) dispute.setMetadata(new HashMap<>());
                dispute.getMetadata().put("last_error", errorMessage);
                disputeRepository.save(dispute);
            }
        } catch (DataAccessException e) {
            log.warn("Failed to record error in dispute metadata - error tracking incomplete", e);
        }
    }

    /**
     * Mark for emergency review (Kafka)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markForEmergencyReview(UUID disputeId, UUID customerId, String resolutionType,
            String resolutionDecision, String reason) {
        Dispute dispute = disputeRepository.findByDisputeId(disputeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        dispute.setStatus(DisputeStatus.ESCALATED);
        dispute.setPriority(DisputePriority.CRITICAL);
        dispute.setEscalationLevel(99); // Emergency indicator
        disputeRepository.save(dispute);
        disputeNotificationService.sendEmergencyNotification(disputeId.toString(),
                customerId.toString(), resolutionType, resolutionDecision, reason);
    }

    // ==================== HELPER METHODS ====================

    private DisputePriority calculatePriority(BigDecimal amount, DisputeType type) {
        if (type == DisputeType.CHARGEBACK || amount.compareTo(BigDecimal.valueOf(10000)) > 0)
            return DisputePriority.CRITICAL;
        if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) return DisputePriority.HIGH;
        if (amount.compareTo(BigDecimal.valueOf(100)) > 0) return DisputePriority.MEDIUM;
        return DisputePriority.LOW;
    }

    private DisputeDTO convertToDTO(Dispute dispute) {
        return DisputeDTO.builder()
                .id(dispute.getId()).transactionId(dispute.getTransactionId()).userId(dispute.getUserId())
                .amount(dispute.getAmount()).currency(dispute.getCurrency()).disputeType(dispute.getDisputeType())
                .status(dispute.getStatus()).priority(dispute.getPriority()).reason(dispute.getReason())
                .createdAt(dispute.getCreatedAt()).resolvedAt(dispute.getResolvedAt())
                .resolutionDecision(dispute.getResolutionDecision()).build();
    }

    private EvidenceDTO convertToEvidenceDTO(DisputeEvidence evidence) {
        return EvidenceDTO.builder()
                .id(evidence.getId()).disputeId(evidence.getDisputeId())
                .evidenceType(evidence.getEvidenceType()).description(evidence.getDescription())
                .submittedBy(evidence.getSubmittedBy()).submittedAt(evidence.getSubmittedAt()).build();
    }

    private byte[] generateCSV(List<Dispute> disputes) {
        StringBuilder csv = new StringBuilder("ID,Transaction,User,Amount,Status,Created\n");
        disputes.forEach(d -> csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                d.getId(), d.getTransactionId(), d.getUserId(), d.getAmount(), d.getStatus(), d.getCreatedAt())));
        return csv.toString().getBytes();
    }

    private byte[] generateJSON(List<Dispute> disputes) {
        return disputes.stream().map(this::convertToDTO).map(Object::toString)
                .collect(Collectors.joining(",", "[", "]")).getBytes();
    }
}
