package com.waqiti.common.security.awareness.controller;

import com.waqiti.common.security.awareness.*;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.domain.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;

/**
 * PCI DSS REQ 12.6 - Security Awareness Program REST API
 *
 * Provides endpoints for security awareness training management,
 * phishing simulations, and quarterly assessments.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/security-awareness")
@RequiredArgsConstructor
@Tag(name = "Security Awareness", description = "PCI DSS REQ 12.6 - Security Awareness Program")
@SecurityRequirement(name = "bearer-jwt")
public class SecurityAwarenessController {

    private final SecurityAwarenessService awarenessService;
    private final SecurityAwarenessServiceExtensions awarenessExtensions;
    private final PhishingSimulationService phishingService;
    private final QuarterlyAssessmentService assessmentService;

    // ========================================================================
    // Training Module Endpoints
    // ========================================================================

    /**
     * Get employee's assigned training modules
     */
    @GetMapping("/employees/{employeeId}/training-modules")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Get assigned training modules", description = "Retrieve all training modules assigned to an employee")
    public ResponseEntity<List<TrainingModuleResponse>> getAssignedTrainingModules(
            @PathVariable UUID employeeId
    ) {
        log.info("GET /employees/{}/training-modules", employeeId);

        // Server-side authorization check
        SecurityContextUtil.validateEmployeeAccess(employeeId);

        List<TrainingModuleResponse> modules = awarenessExtensions.getAssignedTrainingModules(employeeId);
        return ResponseEntity.ok(modules);
    }

    /**
     * Start a training module
     */
    @PostMapping("/employees/{employeeId}/training-modules/{moduleId}/start")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Start training module", description = "Begin a training module")
    public ResponseEntity<TrainingModuleContent> startTrainingModule(
            @PathVariable UUID employeeId,
            @PathVariable UUID moduleId
    ) {
        log.info("POST /employees/{}/training-modules/{}/start", employeeId, moduleId);

        SecurityContextUtil.validateEmployeeAccess(employeeId);

        TrainingModuleContent content = awarenessExtensions.startTrainingModule(employeeId, moduleId);
        return ResponseEntity.ok(content);
    }

    /**
     * Complete a training module with score
     */
    @PostMapping("/employees/{employeeId}/training-modules/{moduleId}/complete")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Complete training module", description = "Submit training module completion with score")
    public ResponseEntity<TrainingCompletionResult> completeTrainingModule(
            @PathVariable UUID employeeId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody TrainingCompletionRequest request
    ) {
        log.info("POST /employees/{}/training-modules/{}/complete - score={}%",
                employeeId, moduleId, request.getScorePercentage());

        SecurityContextUtil.validateEmployeeAccess(employeeId);

        TrainingCompletionResult result = awarenessService.completeTraining(
                employeeId,
                moduleId,
                request.getScorePercentage()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Acknowledge training completion (PCI DSS REQ 12.6.2)
     */
    @PostMapping("/employees/{employeeId}/training-modules/{moduleId}/acknowledge")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Acknowledge training", description = "PCI DSS REQ 12.6.2 - Employee acknowledges understanding")
    public ResponseEntity<Void> acknowledgeTraining(
            @PathVariable UUID employeeId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody AcknowledgmentRequest request
    ) {
        log.info("POST /employees/{}/training-modules/{}/acknowledge", employeeId, moduleId);

        SecurityContextUtil.validateEmployeeAccess(employeeId);

        awarenessService.acknowledgeTraining(
                employeeId,
                moduleId,
                request.getSignature(),
                request.getIpAddress()
        );

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Get employee security profile
     */
    @GetMapping("/employees/{employeeId}/security-profile")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Get security profile", description = "Retrieve employee's security awareness profile")
    public ResponseEntity<EmployeeSecurityProfile> getSecurityProfile(
            @PathVariable UUID employeeId
    ) {
        log.info("GET /employees/{}/security-profile", employeeId);

        SecurityContextUtil.validateEmployeeAccess(employeeId);

        EmployeeSecurityProfile profile = awarenessExtensions.getSecurityProfile(employeeId);
        return ResponseEntity.ok(profile);
    }

    // ========================================================================
    // Phishing Simulation Endpoints
    // ========================================================================

    /**
     * Create phishing campaign (Admin only)
     */
    @PostMapping("/phishing-campaigns")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Create phishing campaign", description = "PCI DSS REQ 12.6.3.1 - Create phishing simulation campaign")
    public ResponseEntity<PhishingCampaignResponse> createPhishingCampaign(
            @Valid @RequestBody PhishingCampaignRequest request
    ) {
        log.info("POST /phishing-campaigns - {}", request.getCampaignName());

        UUID campaignId = phishingService.createPhishingCampaign(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PhishingCampaignResponse.builder()
                        .campaignId(campaignId)
                        .message("Phishing campaign created successfully")
                        .build());
    }

    /**
     * Track phishing email opened (tracking pixel endpoint)
     */
    @GetMapping("/phishing-track/pixel/{trackingToken}")
    @Operation(summary = "Track email opened", description = "Tracking pixel for phishing email opens")
    public ResponseEntity<byte[]> trackEmailOpened(
            @PathVariable String trackingToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        phishingService.trackEmailOpened(trackingToken, userAgent);

        // Return 1x1 transparent pixel
        byte[] pixel = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        );

        return ResponseEntity.ok()
                .header("Content-Type", "image/png")
                .body(pixel);
    }

    /**
     * Track phishing link clicked
     */
    @GetMapping("/phishing-track/link/{trackingToken}")
    @Operation(summary = "Track link clicked", description = "Track when employee clicks phishing link")
    public ResponseEntity<String> trackLinkClicked(
            @PathVariable String trackingToken,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        phishingService.trackLinkClicked(trackingToken, ipAddress, userAgent);

        // Redirect to educational landing page
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header("Location", "/phishing-education?token=" + trackingToken)
                .build();
    }

    /**
     * Report phishing email (positive behavior)
     */
    @PostMapping("/phishing-campaigns/report")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Report phishing", description = "Employee reports suspected phishing email")
    public ResponseEntity<Void> reportPhishing(
            @Valid @RequestBody PhishingReportRequest request
    ) {
        log.info("POST /phishing-campaigns/report - trackingToken={}", request.getTrackingToken());

        phishingService.trackPhishingReported(
                request.getTrackingToken(),
                request.getReportedVia()
        );

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ========================================================================
    // Quarterly Assessment Endpoints (PCI DSS REQ 12.6.3)
    // ========================================================================

    /**
     * Create quarterly assessment (Admin only)
     */
    @PostMapping("/quarterly-assessments")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Create quarterly assessment", description = "PCI DSS REQ 12.6.3 - Create quarterly security assessment")
    public ResponseEntity<QuarterlyAssessmentResponse> createQuarterlyAssessment(
            @Valid @RequestBody QuarterlyAssessmentRequest request
    ) {
        log.info("POST /quarterly-assessments - Q{} {} {}",
                request.getQuarter(), request.getYear(), request.getAssessmentType());

        UUID assessmentId = assessmentService.createQuarterlyAssessment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(QuarterlyAssessmentResponse.builder()
                        .assessmentId(assessmentId)
                        .message("Quarterly assessment created successfully")
                        .build());
    }

    /**
     * Publish assessment (Admin only)
     */
    @PostMapping("/quarterly-assessments/{assessmentId}/publish")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Publish assessment", description = "Publish assessment and notify employees")
    public ResponseEntity<Void> publishAssessment(
            @PathVariable UUID assessmentId
    ) {
        log.info("POST /quarterly-assessments/{}/publish", assessmentId);

        assessmentService.publishAssessment(assessmentId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Start assessment for employee
     */
    @PostMapping("/employees/{employeeId}/assessments/{assessmentId}/start")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Start assessment", description = "Begin quarterly security assessment")
    public ResponseEntity<AssessmentSession> startAssessment(
            @PathVariable UUID employeeId,
            @PathVariable UUID assessmentId
    ) {
        log.info("POST /employees/{}/assessments/{}/start", employeeId, assessmentId);

        SecurityContextUtil.validateEmployeeAccess(employeeId);

        AssessmentSession session = assessmentService.startAssessment(employeeId, assessmentId);

        return ResponseEntity.ok(session);
    }

    /**
     * Submit assessment answers
     */
    @PostMapping("/assessment-results/{resultId}/submit")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Submit assessment", description = "Submit assessment answers and get results")
    public ResponseEntity<AssessmentCompletionResult> submitAssessment(
            @PathVariable UUID resultId,
            @Valid @RequestBody AssessmentSubmissionRequest request
    ) {
        log.info("POST /assessment-results/{}/submit", resultId);

        AssessmentCompletionResult result = assessmentService.submitAssessment(
                resultId,
                request.getAnswers()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Get assessment statistics (Admin only)
     */
    @GetMapping("/quarterly-assessments/{assessmentId}/statistics")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Get assessment statistics", description = "Retrieve assessment completion and performance statistics")
    public ResponseEntity<AssessmentStatistics> getAssessmentStatistics(
            @PathVariable UUID assessmentId
    ) {
        log.info("GET /quarterly-assessments/{}/statistics", assessmentId);

        AssessmentStatistics statistics = assessmentService.getAssessmentStatistics(assessmentId);

        return ResponseEntity.ok(statistics);
    }

    // ========================================================================
    // Compliance Reporting Endpoints (PCI DSS Audits)
    // ========================================================================

    /**
     * Get PCI compliance report (Admin only)
     */
    @GetMapping("/compliance/pci-report")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "PCI compliance report", description = "Generate PCI DSS REQ 12.6 compliance report for auditors")
    public ResponseEntity<PCIComplianceReport> getPCIComplianceReport() {
        log.info("GET /compliance/pci-report");

        PCIComplianceReport report = awarenessService.generatePCIComplianceReport();

        return ResponseEntity.ok(report);
    }

    /**
     * Get employees overdue for training (Admin only)
     */
    @GetMapping("/compliance/overdue-employees")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Overdue employees", description = "List employees overdue for annual training")
    public ResponseEntity<List<EmployeeSecurityProfile>> getOverdueEmployees() {
        log.info("GET /compliance/overdue-employees");

        List<EmployeeSecurityProfile> overdueEmployees = awarenessService.getOverdueEmployees();

        return ResponseEntity.ok(overdueEmployees);
    }

    /**
     * Get high-risk employees (Admin only)
     */
    @GetMapping("/compliance/high-risk-employees")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "High-risk employees", description = "List employees with high security risk scores")
    public ResponseEntity<List<EmployeeSecurityProfile>> getHighRiskEmployees() {
        log.info("GET /compliance/high-risk-employees");

        List<EmployeeSecurityProfile> highRiskEmployees = awarenessService.getHighRiskEmployees();

        return ResponseEntity.ok(highRiskEmployees);
    }

    /**
     * Get phishing campaign report (Admin only)
     */
    @GetMapping("/phishing-campaigns/{campaignId}/report")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Phishing campaign report", description = "Get detailed phishing campaign results")
    public ResponseEntity<PhishingCampaignReport> getPhishingCampaignReport(
            @PathVariable UUID campaignId
    ) {
        log.info("GET /phishing-campaigns/{}/report", campaignId);

        PhishingCampaignReport report = phishingService.getCampaignReport(campaignId);

        return ResponseEntity.ok(report);
    }

    // ========================================================================
    // Dashboard & Analytics Endpoints
    // ========================================================================

    /**
     * Get security awareness dashboard (Admin only)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Operation(summary = "Security awareness dashboard", description = "Get overall security awareness program metrics")
    public ResponseEntity<SecurityAwarenessDashboard> getDashboard() {
        log.info("GET /dashboard");

        SecurityAwarenessDashboard dashboard = SecurityAwarenessDashboard.builder()
                .pciComplianceReport(awarenessService.generatePCIComplianceReport())
                .overdueEmployeeCount(awarenessService.getOverdueEmployees().size())
                .highRiskEmployeeCount(awarenessService.getHighRiskEmployees().size())
                .build();

        return ResponseEntity.ok(dashboard);
    }
}
