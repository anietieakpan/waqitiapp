package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.dto.*;
import com.waqiti.gdpr.exception.GDPRException;
import com.waqiti.gdpr.integration.ServiceDataCollector;
import com.waqiti.gdpr.repository.DataSubjectRequestRepository;
import com.waqiti.gdpr.repository.RequestAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DataSubjectRequestService {

    private final DataSubjectRequestRepository requestRepository;
    private final RequestAuditLogRepository auditLogRepository;
    private final ServiceDataCollector dataCollector;
    private final DataAnonymizationService anonymizationService;
    private final NotificationService notificationService;
    private final EncryptionService encryptionService;

    public DataSubjectRequestDTO createRequest(CreateRequestDTO dto, String userId) {
        log.info("Creating data subject request for user: {} of type: {}", userId, dto.getRequestType());

        // Validate request
        validateRequest(dto, userId);

        // Check for existing pending requests
        checkExistingRequests(userId, dto.getRequestType());

        DataSubjectRequest request = DataSubjectRequest.builder()
            .userId(userId)
            .requestType(dto.getRequestType())
            .dataCategories(dto.getDataCategories())
            .exportFormat(dto.getExportFormat())
            .notes(dto.getNotes())
            .build();

        // Generate verification token
        String verificationToken = generateVerificationToken();
        request.setVerificationToken(encryptionService.encrypt(verificationToken));

        request = requestRepository.save(request);

        // Add audit log
        addAuditLog(request, "REQUEST_CREATED", "Data subject request created", userId);

        // Send verification email
        notificationService.sendVerificationEmail(userId, request.getId(), verificationToken);

        return mapToDTO(request);
    }

    public DataSubjectRequestDTO verifyRequest(String requestId, String token) {
        log.info("Verifying data subject request: {}", requestId);

        DataSubjectRequest request = findRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING_VERIFICATION) {
            throw new GDPRException("Request is not pending verification");
        }

        // Verify token
        String storedToken = encryptionService.decrypt(request.getVerificationToken());
        if (!storedToken.equals(token)) {
            throw new GDPRException("Invalid verification token");
        }

        // Update request status
        request.setStatus(RequestStatus.VERIFIED);
        request.setVerifiedAt(LocalDateTime.now());
        request = requestRepository.save(request);

        // Add audit log
        addAuditLog(request, "REQUEST_VERIFIED", "Request verified by user", request.getUserId());

        // Start processing asynchronously
        processRequestAsync(request);

        return mapToDTO(request);
    }

    @Async
    public CompletableFuture<Void> processRequestAsync(DataSubjectRequest request) {
        try {
            log.info("Processing data subject request: {} for user: {}", 
                request.getId(), request.getUserId());

            request.setStatus(RequestStatus.IN_PROGRESS);
            requestRepository.save(request);

            switch (request.getRequestType()) {
                case ACCESS:
                case PORTABILITY:
                    processDataExportRequest(request);
                    break;
                case ERASURE:
                    processErasureRequest(request);
                    break;
                case RECTIFICATION:
                    processRectificationRequest(request);
                    break;
                case RESTRICTION:
                    processRestrictionRequest(request);
                    break;
                case OBJECTION:
                    processObjectionRequest(request);
                    break;
            }

            // Mark as completed
            request.setStatus(RequestStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            requestRepository.save(request);

            // Notify user
            notificationService.sendCompletionNotification(request);

        } catch (Exception e) {
            log.error("Error processing request: {}", request.getId(), e);
            handleRequestError(request, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void processDataExportRequest(DataSubjectRequest request) {
        log.info("Processing data export request for user: {}", request.getUserId());

        // Collect data from all services
        UserDataCollection dataCollection = dataCollector.collectUserData(
            request.getUserId(),
            request.getDataCategories()
        );

        // Generate export file (simplified for DLQ integration)
        String exportUrl = String.format("https://api.example.com/gdpr/exports/%s/download",
            UUID.randomUUID().toString());

        // Update request with export details
        request.setExportUrl(exportUrl);
        request.setExportExpiresAt(LocalDateTime.now().plusDays(7));

        addAuditLog(request, "DATA_EXPORTED",
            "User data exported in " + request.getExportFormat() + " format",
            "system");
    }

    private void processErasureRequest(DataSubjectRequest request) {
        log.info("Processing erasure request for user: {}", request.getUserId());

        // Check if erasure is allowed
        ErasureEligibility eligibility = checkErasureEligibility(request.getUserId());
        
        if (!eligibility.isEligible()) {
            request.setStatus(RequestStatus.REJECTED);
            request.setRejectionReason(eligibility.getReason());
            addAuditLog(request, "ERASURE_REJECTED", eligibility.getReason(), "system");
            return;
        }

        // Perform data erasure
        ErasureResult result = anonymizationService.eraseUserData(
            request.getUserId(),
            request.getDataCategories()
        );

        // Log erasure details
        addAuditLog(request, "DATA_ERASED", 
            String.format("Erased data from %d services, %d records affected", 
                result.getServicesProcessed(), result.getRecordsErased()),
            "system");

        // Archive the request itself
        anonymizeRequest(request);
    }

    private void processRectificationRequest(DataSubjectRequest request) {
        log.info("Processing rectification request for user: {}", request.getUserId());

        // This would typically involve manual review
        // For now, we'll mark it for manual processing
        
        addAuditLog(request, "RECTIFICATION_PENDING", 
            "Rectification request requires manual review", "system");
        
        // Notify data protection officer
        notificationService.notifyDataProtectionOfficer(request);
    }

    private void processRestrictionRequest(DataSubjectRequest request) {
        log.info("Processing restriction request for user: {}", request.getUserId());

        // Apply processing restrictions
        RestrictionResult result = dataCollector.applyProcessingRestrictions(
            request.getUserId(),
            request.getDataCategories()
        );

        addAuditLog(request, "RESTRICTION_APPLIED", 
            String.format("Processing restricted for %d data categories", 
                result.getCategoriesRestricted()),
            "system");
    }

    private void processObjectionRequest(DataSubjectRequest request) {
        log.info("Processing objection request for user: {}", request.getUserId());

        // Update consent records
        List<ConsentRecord> consents = dataCollector.getActiveConsents(request.getUserId());
        
        for (ConsentRecord consent : consents) {
            if (shouldRevokeConsent(consent, request.getDataCategories())) {
                consent.withdraw();
                addAuditLog(request, "CONSENT_WITHDRAWN", 
                    "Withdrew consent for: " + consent.getPurpose(), "system");
            }
        }
    }

    public DataSubjectRequestDTO getRequest(String requestId, String userId) {
        DataSubjectRequest request = findRequestById(requestId);
        
        // Verify user owns this request
        if (!request.getUserId().equals(userId)) {
            throw new GDPRException("Unauthorized access to request");
        }

        return mapToDTO(request);
    }

    public List<DataSubjectRequestDTO> getUserRequests(String userId) {
        List<DataSubjectRequest> requests = requestRepository.findByUserId(userId);
        return requests.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public RequestStatusDTO getRequestStatus(String requestId, String userId) {
        DataSubjectRequest request = findRequestById(requestId);
        
        if (!request.getUserId().equals(userId)) {
            throw new GDPRException("Unauthorized access to request");
        }

        return RequestStatusDTO.builder()
            .requestId(request.getId())
            .status(request.getStatus())
            .submittedAt(request.getSubmittedAt())
            .deadline(request.getDeadline())
            .completedAt(request.getCompletedAt())
            .exportUrl(request.getExportUrl())
            .exportExpiresAt(request.getExportExpiresAt())
            .isOverdue(request.isOverdue())
            .build();
    }

    @Transactional(readOnly = true)
    public List<DataSubjectRequestDTO> getPendingRequests() {
        List<DataSubjectRequest> pendingRequests = requestRepository.findByStatusIn(
            Arrays.asList(RequestStatus.VERIFIED, RequestStatus.IN_PROGRESS)
        );
        
        return pendingRequests.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DataSubjectRequestDTO> getOverdueRequests() {
        List<DataSubjectRequest> allRequests = requestRepository.findByStatusIn(
            Arrays.asList(RequestStatus.PENDING_VERIFICATION, 
                         RequestStatus.VERIFIED, 
                         RequestStatus.IN_PROGRESS)
        );
        
        return allRequests.stream()
            .filter(DataSubjectRequest::isOverdue)
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public void cancelRequest(String requestId, String userId) {
        DataSubjectRequest request = findRequestById(requestId);
        
        if (!request.getUserId().equals(userId)) {
            throw new GDPRException("Unauthorized access to request");
        }

        if (request.getStatus() == RequestStatus.COMPLETED || 
            request.getStatus() == RequestStatus.REJECTED) {
            throw new GDPRException("Cannot cancel completed or rejected request");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason("Cancelled by user");
        request.setCompletedAt(LocalDateTime.now());
        requestRepository.save(request);

        addAuditLog(request, "REQUEST_CANCELLED", "Request cancelled by user", userId);
    }

    // Helper methods

    private void validateRequest(CreateRequestDTO dto, String userId) {
        if (dto.getRequestType() == null) {
            throw new GDPRException("Request type is required");
        }

        if (dto.getRequestType() == RequestType.ACCESS || 
            dto.getRequestType() == RequestType.PORTABILITY) {
            if (dto.getExportFormat() == null) {
                throw new GDPRException("Export format is required for access/portability requests");
            }
        }

        if (dto.getDataCategories() == null || dto.getDataCategories().isEmpty()) {
            throw new GDPRException("At least one data category must be specified");
        }
    }

    private void checkExistingRequests(String userId, RequestType requestType) {
        List<DataSubjectRequest> existingRequests = requestRepository
            .findByUserIdAndRequestTypeAndStatusIn(
                userId, 
                requestType,
                Arrays.asList(RequestStatus.PENDING_VERIFICATION, 
                             RequestStatus.VERIFIED, 
                             RequestStatus.IN_PROGRESS)
            );

        if (!existingRequests.isEmpty()) {
            throw new GDPRException("You already have a pending request of this type");
        }
    }

    private DataSubjectRequest findRequestById(String requestId) {
        return requestRepository.findById(requestId)
            .orElseThrow(() -> new GDPRException("Request not found: " + requestId));
    }

    private String generateVerificationToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }

    private void addAuditLog(DataSubjectRequest request, String action, String details, String performedBy) {
        RequestAuditLog auditLog = RequestAuditLog.builder()
            .request(request)
            .action(action)
            .details(details)
            .performedBy(performedBy)
            .performedAt(LocalDateTime.now())
            .build();

        request.addAuditLog(auditLog);
        auditLogRepository.save(auditLog);
    }

    private ErasureEligibility checkErasureEligibility(String userId) {
        // Check various conditions that might prevent erasure
        
        // Check for active financial obligations
        if (dataCollector.hasActiveFinancialObligations(userId)) {
            return ErasureEligibility.notEligible(
                "Cannot erase data due to active financial obligations"
            );
        }

        // Check for legal holds
        if (dataCollector.hasLegalHold(userId)) {
            return ErasureEligibility.notEligible(
                "Cannot erase data due to legal hold requirements"
            );
        }

        // Check for recent transactions requiring audit trail
        if (dataCollector.hasRecentHighValueTransactions(userId)) {
            return ErasureEligibility.notEligible(
                "Cannot erase data due to recent high-value transactions requiring audit trail"
            );
        }

        return ErasureEligibility.eligible();
    }

    private void anonymizeRequest(DataSubjectRequest request) {
        // Anonymize the request itself after processing
        request.setUserId(anonymizationService.generateAnonymousId(request.getUserId()));
        request.setNotes("[REDACTED]");
        requestRepository.save(request);
    }

    private boolean shouldRevokeConsent(ConsentRecord consent, List<String> dataCategories) {
        // Logic to determine if consent should be revoked based on objection categories
        return dataCategories.contains("ALL") || 
               dataCategories.contains(consent.getPurpose().toString());
    }

    private void handleRequestError(DataSubjectRequest request, Exception e) {
        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason("Processing error: " + e.getMessage());
        request.setCompletedAt(LocalDateTime.now());
        requestRepository.save(request);

        addAuditLog(request, "PROCESSING_ERROR", e.getMessage(), "system");
        
        // Notify user and DPO
        notificationService.sendErrorNotification(request, e);
        notificationService.notifyDataProtectionOfficer(request);
    }

    private DataSubjectRequestDTO mapToDTO(DataSubjectRequest request) {
        return DataSubjectRequestDTO.builder()
            .id(request.getId())
            .userId(request.getUserId())
            .requestType(request.getRequestType())
            .status(request.getStatus())
            .submittedAt(request.getSubmittedAt())
            .completedAt(request.getCompletedAt())
            .deadline(request.getDeadline())
            .dataCategories(request.getDataCategories())
            .exportFormat(request.getExportFormat())
            .exportUrl(request.getExportUrl())
            .exportExpiresAt(request.getExportExpiresAt())
            .rejectionReason(request.getRejectionReason())
            .notes(request.getNotes())
            .isOverdue(request.isOverdue())
            .auditLogs(request.getAuditLogs().stream()
                .map(this::mapAuditLogToDTO)
                .collect(Collectors.toList()))
            .build();
    }

    private AuditLogDTO mapAuditLogToDTO(RequestAuditLog log) {
        return AuditLogDTO.builder()
            .id(log.getId())
            .action(log.getAction())
            .details(log.getDetails())
            .performedBy(log.getPerformedBy())
            .performedAt(log.getPerformedAt())
            .build();
    }

    /**
     * Find or create export request for DLQ processing
     * Used by DataExportService for DLQ recovery
     */
    @Transactional
    public DataSubjectRequest findOrCreateExportRequest(
            String subjectId,
            String exportId,
            RequestType requestType,
            ExportFormat exportFormat,
            String correlationId) {

        log.debug("Finding or creating export request: subjectId={} exportId={} correlationId={}",
                subjectId, exportId, correlationId);

        // Try to find existing request by export ID (stored in notes or ID)
        Optional<DataSubjectRequest> existing = requestRepository.findById(exportId);

        if (existing.isPresent()) {
            log.debug("Found existing export request: exportId={} correlationId={}", exportId, correlationId);
            return existing.get();
        }

        // Create new request
        DataSubjectRequest request = DataSubjectRequest.builder()
                .id(exportId)
                .userId(subjectId)
                .requestType(requestType)
                .status(RequestStatus.IN_PROGRESS)
                .exportFormat(exportFormat)
                .submittedAt(LocalDateTime.now())
                .deadline(LocalDateTime.now().plusDays(30))
                .notes(String.format("Created from DLQ recovery - correlationId: %s", correlationId))
                .build();

        request = requestRepository.save(request);

        addAuditLog(request, "REQUEST_CREATED_FROM_DLQ",
                "Data subject request created from DLQ recovery", "system");

        log.info("Created new export request from DLQ: exportId={} subjectId={} correlationId={}",
                exportId, subjectId, correlationId);

        return request;
    }

    /**
     * Find request by export ID
     * Used by DataExportService
     */
    @Transactional(readOnly = true)
    public DataSubjectRequest findByExportId(String exportId, String correlationId) {
        log.debug("Finding export request by ID: exportId={} correlationId={}", exportId, correlationId);

        return requestRepository.findById(exportId)
                .orElseThrow(() -> new GDPRException("Export request not found: " + exportId));
    }

    /**
     * Save request (used by DataExportService)
     */
    @Transactional
    public DataSubjectRequest save(DataSubjectRequest request, String correlationId) {
        log.debug("Saving export request: requestId={} status={} correlationId={}",
                request.getId(), request.getStatus(), correlationId);

        return requestRepository.save(request);
    }
}