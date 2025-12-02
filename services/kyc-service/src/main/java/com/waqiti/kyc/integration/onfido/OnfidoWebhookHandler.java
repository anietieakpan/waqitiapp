package com.waqiti.kyc.integration.onfido;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.event.*;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.common.event.EventPublisher;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnfidoWebhookHandler {

    private final KYCVerificationService verificationService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void handleEvent(OnfidoWebhookEvent event) {
        log.info("Processing Onfido webhook event: {} for resource: {}", 
                event.getAction(), event.getResourceType());
        
        try {
            switch (event.getResourceType()) {
                case "check":
                    handleCheckEvent(event);
                    break;
                    
                case "report":
                    handleReportEvent(event);
                    break;
                    
                case "workflow_run":
                    handleWorkflowRunEvent(event);
                    break;
                    
                case "workflow_task":
                    handleWorkflowTaskEvent(event);
                    break;
                    
                case "document":
                    handleDocumentEvent(event);
                    break;
                    
                case "live_photo":
                case "live_video":
                    handleLivenessEvent(event);
                    break;
                    
                default:
                    log.warn("Unhandled webhook resource type: {}", event.getResourceType());
            }
            
        } catch (Exception e) {
            log.error("Error processing Onfido webhook event", e);
            throw new RuntimeException("Failed to process webhook event", e);
        }
    }
    
    private void handleCheckEvent(OnfidoWebhookEvent event) {
        CheckObject check = objectMapper.convertValue(event.getObject(), CheckObject.class);
        
        String action = event.getAction();
        if ("check.completed".equals(action)) {
            handleCheckCompleted(check);
        } else if ("check.started".equals(action)) {
            handleCheckStarted(check);
        } else if ("check.reopened".equals(action)) {
            handleCheckReopened(check);
        } else if ("check.withdrawn".equals(action)) {
            handleCheckWithdrawn(check);
        }
    }
    
    private void handleCheckCompleted(CheckObject check) {
        KYCVerification verification = verificationService.findByProviderReference(check.getId())
                .orElseThrow(() -> new RuntimeException("Verification not found for check: " + check.getId()));
        
        // Update verification status based on check result
        KYCVerification.Status newStatus;
        String resultReason = null;
        
        switch (check.getResult()) {
            case "clear":
                newStatus = KYCVerification.Status.VERIFIED;
                break;
            case "consider":
                newStatus = KYCVerification.Status.MANUAL_REVIEW;
                resultReason = buildResultReason(check);
                break;
            default:
                newStatus = KYCVerification.Status.REJECTED;
                resultReason = buildResultReason(check);
        }
        
        verificationService.updateVerificationStatus(
                verification.getId(),
                newStatus,
                resultReason,
                check.toMap()
        );
        
        // Publish appropriate event
        if (newStatus == KYCVerification.Status.VERIFIED) {
            eventPublisher.publish(KYCVerificationCompletedEvent.builder()
                    .verificationId(verification.getId())
                    .userId(verification.getUserId())
                    .verificationLevel(verification.getVerificationLevel())
                    .completedAt(LocalDateTime.now())
                    .build());
        } else if (newStatus == KYCVerification.Status.REJECTED) {
            eventPublisher.publish(KYCVerificationRejectedEvent.builder()
                    .verificationId(verification.getId())
                    .userId(verification.getUserId())
                    .reason(resultReason)
                    .rejectedAt(LocalDateTime.now())
                    .build());
        } else if (newStatus == KYCVerification.Status.MANUAL_REVIEW) {
            eventPublisher.publish(KYCVerificationReviewRequiredEvent.builder()
                    .verificationId(verification.getId())
                    .userId(verification.getUserId())
                    .reason(resultReason)
                    .reviewRequestedAt(LocalDateTime.now())
                    .build());
        }
    }
    
    private void handleCheckStarted(CheckObject check) {
        log.info("Check started: {}", check.getId());
        // Update verification to in-progress if needed
    }
    
    private void handleCheckReopened(CheckObject check) {
        log.info("Check reopened: {}", check.getId());
        
        KYCVerification verification = verificationService.findByProviderReference(check.getId())
                .orElse(null);
        
        if (verification != null) {
            verificationService.updateVerificationStatus(
                    verification.getId(),
                    KYCVerification.Status.IN_PROGRESS,
                    "Check reopened for review",
                    null
            );
        }
    }
    
    private void handleCheckWithdrawn(CheckObject check) {
        log.info("Check withdrawn: {}", check.getId());
        
        KYCVerification verification = verificationService.findByProviderReference(check.getId())
                .orElse(null);
        
        if (verification != null) {
            verificationService.updateVerificationStatus(
                    verification.getId(),
                    KYCVerification.Status.EXPIRED,
                    "Check withdrawn",
                    null
            );
        }
    }
    
    private void handleReportEvent(OnfidoWebhookEvent event) {
        ReportObject report = objectMapper.convertValue(event.getObject(), ReportObject.class);
        
        if ("report.completed".equals(event.getAction())) {
            log.info("Report completed: {} for check: {} with result: {}", 
                    report.getId(), report.getCheckId(), report.getResult());
            
            // Store report details in verification metadata
            KYCVerification verification = verificationService.findByProviderReference(report.getCheckId())
                    .orElse(null);
            
            if (verification != null) {
                Map<String, Object> metadata = verification.getMetadata();
                metadata.put("report_" + report.getName(), Map.of(
                        "id", report.getId(),
                        "result", report.getResult(),
                        "subResult", report.getSubResult(),
                        "properties", report.getProperties()
                ));
                
                verificationService.updateMetadata(verification.getId(), metadata);
            }
        }
    }
    
    private void handleWorkflowRunEvent(OnfidoWebhookEvent event) {
        WorkflowRunObject workflowRun = objectMapper.convertValue(event.getObject(), WorkflowRunObject.class);
        
        String action = event.getAction();
        if ("workflow_run.completed".equals(action)) {
            handleWorkflowRunCompleted(workflowRun);
        } else if ("workflow_run.started".equals(action)) {
            log.info("Workflow run started: {}", workflowRun.getId());
        }
    }
    
    private void handleWorkflowRunCompleted(WorkflowRunObject workflowRun) {
        KYCVerification verification = verificationService.findByProviderReference(workflowRun.getId())
                .orElseThrow(() -> new RuntimeException("Verification not found for workflow run: " + workflowRun.getId()));
        
        KYCVerification.Status newStatus;
        String resultReason = null;
        
        switch (workflowRun.getStatus()) {
            case "approved":
                newStatus = KYCVerification.Status.VERIFIED;
                break;
            case "declined":
                newStatus = KYCVerification.Status.REJECTED;
                resultReason = String.join(", ", workflowRun.getReasons());
                break;
            case "review":
                newStatus = KYCVerification.Status.MANUAL_REVIEW;
                resultReason = String.join(", ", workflowRun.getReasons());
                break;
            case "abandoned":
                newStatus = KYCVerification.Status.EXPIRED;
                resultReason = "Workflow abandoned by user";
                break;
            case "error":
                newStatus = KYCVerification.Status.FAILED;
                resultReason = "Workflow error: " + workflowRun.getError();
                break;
            default:
                log.warn("Unknown workflow status: {}", workflowRun.getStatus());
                return;
        }
        
        verificationService.updateVerificationStatus(
                verification.getId(),
                newStatus,
                resultReason,
                workflowRun.toMap()
        );
        
        // Publish events based on status
        publishVerificationEvent(verification, newStatus, resultReason);
    }
    
    private void handleWorkflowTaskEvent(OnfidoWebhookEvent event) {
        WorkflowTaskObject task = objectMapper.convertValue(event.getObject(), WorkflowTaskObject.class);
        
        if ("workflow_task.started".equals(event.getAction())) {
            log.info("Workflow task started: {} of type: {}", task.getId(), task.getTaskType());
            
            // Could be used to track user progress through verification steps
            eventPublisher.publish(KYCVerificationStepCompletedEvent.builder()
                    .verificationId(task.getWorkflowRunId())
                    .stepName(task.getTaskType())
                    .completedAt(LocalDateTime.now())
                    .build());
        }
    }
    
    private void handleDocumentEvent(OnfidoWebhookEvent event) {
        DocumentObject document = objectMapper.convertValue(event.getObject(), DocumentObject.class);
        
        if ("document.uploaded".equals(event.getAction())) {
            log.info("Document uploaded: {} for applicant: {}", document.getId(), document.getApplicantId());
            
            // Update verification to track uploaded documents
            eventPublisher.publish(KYCDocumentUploadedEvent.builder()
                    .documentId(document.getId())
                    .applicantId(document.getApplicantId())
                    .documentType(document.getType())
                    .uploadedAt(LocalDateTime.now())
                    .build());
        }
    }
    
    private void handleLivenessEvent(OnfidoWebhookEvent event) {
        LivenessObject liveness = objectMapper.convertValue(event.getObject(), LivenessObject.class);
        
        if (event.getAction().endsWith(".uploaded")) {
            log.info("Liveness check uploaded: {} for applicant: {}", 
                    liveness.getId(), liveness.getApplicantId());
            
            eventPublisher.publish(KYCLivenessCheckCompletedEvent.builder()
                    .checkId(liveness.getId())
                    .applicantId(liveness.getApplicantId())
                    .checkType(event.getResourceType())
                    .completedAt(LocalDateTime.now())
                    .build());
        }
    }
    
    private String buildResultReason(CheckObject check) {
        StringBuilder reason = new StringBuilder();
        
        if (check.getReports() != null) {
            check.getReports().forEach(report -> {
                if (!"clear".equals(report.getResult())) {
                    reason.append(report.getName())
                            .append(": ")
                            .append(report.getResult());
                    
                    if (report.getBreakdown() != null) {
                        reason.append(" (")
                                .append(report.getBreakdown())
                                .append(")");
                    }
                    
                    reason.append("; ");
                }
            });
        }
        
        return reason.toString();
    }
    
    private void publishVerificationEvent(KYCVerification verification, 
                                        KYCVerification.Status status, 
                                        String reason) {
        switch (status) {
            case VERIFIED:
                eventPublisher.publish(KYCVerificationCompletedEvent.builder()
                        .verificationId(verification.getId())
                        .userId(verification.getUserId())
                        .verificationLevel(verification.getVerificationLevel())
                        .completedAt(LocalDateTime.now())
                        .build());
                break;
                
            case REJECTED:
                eventPublisher.publish(KYCVerificationRejectedEvent.builder()
                        .verificationId(verification.getId())
                        .userId(verification.getUserId())
                        .reason(reason)
                        .rejectedAt(LocalDateTime.now())
                        .build());
                break;
                
            case MANUAL_REVIEW:
                eventPublisher.publish(KYCVerificationReviewRequiredEvent.builder()
                        .verificationId(verification.getId())
                        .userId(verification.getUserId())
                        .reason(reason)
                        .reviewRequestedAt(LocalDateTime.now())
                        .build());
                break;
                
            case FAILED:
                eventPublisher.publish(KYCVerificationFailedEvent.builder()
                        .verificationId(verification.getId())
                        .userId(verification.getUserId())
                        .failureReason(reason)
                        .failedAt(LocalDateTime.now())
                        .build());
                break;
                
            case EXPIRED:
                eventPublisher.publish(KYCVerificationExpiredEvent.builder()
                        .verificationId(verification.getId())
                        .userId(verification.getUserId())
                        .expiredAt(LocalDateTime.now())
                        .build());
                break;
        }
    }
    
    public boolean verifyWebhookSignature(String payload, String signature, String webhookToken) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookToken.getBytes(StandardCharsets.UTF_8), 
                    "HmacSHA256"
            );
            hmac.init(secretKey);
            
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            
            return computedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }
    
    // Data classes for webhook payloads
    
    @Data
    public static class OnfidoWebhookEvent {
        private String resourceType;
        private String action;
        private Object object;
        private String completedAtIso8601;
        private String href;
    }
    
    @Data
    public static class CheckObject {
        private String id;
        private String status;
        private String result;
        private String href;
        private String applicantId;
        private String createdAt;
        private String updatedAt;
        private List<ReportSummary> reports;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "status", status,
                    "result", result,
                    "reports", reports != null ? reports : List.of()
            );
        }
    }
    
    @Data
    public static class ReportSummary {
        private String id;
        private String name;
        private String result;
        private String status;
        private String breakdown;
        private String subResult;
    }
    
    @Data
    public static class ReportObject {
        private String id;
        private String checkId;
        private String name;
        private String result;
        private String status;
        private String subResult;
        private Map<String, Object> properties;
        private Map<String, Object> breakdown;
    }
    
    @Data
    public static class WorkflowRunObject {
        private String id;
        private String applicantId;
        private String workflowId;
        private String status;
        private List<String> reasons;
        private String error;
        private Map<String, Object> output;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "workflowId", workflowId,
                    "status", status,
                    "reasons", reasons != null ? reasons : List.of(),
                    "output", output != null ? output : Map.of()
            );
        }
    }
    
    @Data
    public static class WorkflowTaskObject {
        private String id;
        private String workflowRunId;
        private String taskType;
        private String taskId;
        private Map<String, Object> input;
        private Map<String, Object> output;
    }
    
    @Data
    public static class DocumentObject {
        private String id;
        private String applicantId;
        private String type;
        private String side;
        private String issuingCountry;
    }
    
    @Data
    public static class LivenessObject {
        private String id;
        private String applicantId;
        private String challengeType;
        private List<String> challengeSwitches;
    }
}