package com.waqiti.compliance.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.compliance.dto.*;
import com.waqiti.compliance.service.AMLComplianceService;
import com.waqiti.compliance.service.KYCService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.security.rbac.RequiresPermission;
import com.waqiti.common.security.rbac.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Management", description = "AML/KYC and regulatory compliance operations")
@Validated
public class ComplianceController {

    private final AMLComplianceService amlService;
    private final KYCService kycService;
    private final RegulatoryReportingService reportingService;
    private final SanctionsScreeningService sanctionsService;

    // AML Endpoints
    @PostMapping("/aml/transactions/screen")
    @RequiresPermission(Permission.COMPLIANCE_READ)
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 500, refillTokens = 500, refillPeriodMinutes = 1)
    @Operation(summary = "Screen transaction for AML compliance")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<AMLScreeningResponse>> screenTransaction(
            @Valid @RequestBody AMLScreeningRequest request) {
        log.info("Screening transaction for AML: {}", request.getTransactionId());
        
        AMLScreeningResponse response = amlService.screenTransaction(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/aml/alerts")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @Operation(summary = "Get AML alerts")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AMLAlertResponse>>> getAMLAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        
        AMLAlertFilter filter = AMLAlertFilter.builder()
                .status(status)
                .severity(severity)
                .alertType(alertType)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        Page<AMLAlertResponse> alerts = amlService.getAMLAlerts(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    @PostMapping("/aml/alerts/{alertId}/resolve")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @Operation(summary = "Resolve AML alert")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AMLAlertResponse>> resolveAMLAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody ResolveAMLAlertRequest request) {
        log.info("Resolving AML alert: {}", alertId);
        
        AMLAlertResponse response = amlService.resolveAlert(alertId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/aml/sar")
    @RequiresPermission(Permission.COMPLIANCE_SAR_FILE)
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    @Operation(summary = "File Suspicious Activity Report")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SARResponse>> fileSAR(
            @Valid @RequestBody FileSARRequest request) {
        log.info("Filing SAR for user: {}", request.getSubjectUserId());
        
        SARResponse response = amlService.fileSAR(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // KYC Endpoints
    @PostMapping("/kyc/verification")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @Operation(summary = "Initiate KYC verification")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KYCVerificationResponse>> initiateKYCVerification(
            @Valid @RequestBody InitiateKYCRequest request) {
        log.info("Initiating KYC verification for user: {}", request.getUserId());
        
        KYCVerificationResponse response = kycService.initiateVerification(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/kyc/documents/upload")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @Operation(summary = "Upload KYC document")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KYCDocumentResponse>> uploadKYCDocument(
            @RequestParam("userId") UUID userId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        log.info("Uploading KYC document for user: {}, type: {}", userId, documentType);
        
        KYCDocumentUploadRequest request = KYCDocumentUploadRequest.builder()
                .userId(userId)
                .documentType(documentType)
                .file(file)
                .build();
        
        KYCDocumentResponse response = kycService.uploadDocument(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/kyc/verification/{verificationId}/submit")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @Operation(summary = "Submit KYC verification for review")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KYCVerificationResponse>> submitKYCVerification(
            @PathVariable UUID verificationId) {
        log.info("Submitting KYC verification for review: {}", verificationId);
        
        KYCVerificationResponse response = kycService.submitForReview(verificationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/kyc/verification/{verificationId}/review")
    @Operation(summary = "Review KYC verification")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KYCVerificationResponse>> reviewKYCVerification(
            @PathVariable UUID verificationId,
            @Valid @RequestBody ReviewKYCRequest request) {
        log.info("Reviewing KYC verification: {}", verificationId);
        
        KYCVerificationResponse response = kycService.reviewVerification(verificationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/kyc/verification/{userId}")
    @Operation(summary = "Get KYC verification status")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<KYCVerificationResponse>> getKYCVerificationStatus(
            @PathVariable UUID userId) {
        
        KYCVerificationResponse response = kycService.getVerificationStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/kyc/pending-reviews")
    @Operation(summary = "Get pending KYC reviews")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<KYCVerificationResponse>>> getPendingKYCReviews(
            Pageable pageable) {
        
        Page<KYCVerificationResponse> reviews = kycService.getPendingReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    // Sanctions Screening Endpoints
    @PostMapping("/sanctions/screen")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 200, refillTokens = 200, refillPeriodMinutes = 1)
    @Operation(summary = "Screen entity against sanctions lists")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<SanctionsScreeningResponse>> screenEntity(
            @Valid @RequestBody SanctionsScreeningRequest request) {
        log.info("Screening entity for sanctions: {}", request.getEntityId());
        
        SanctionsScreeningResponse response = sanctionsService.screenEntity(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sanctions/matches")
    @Operation(summary = "Get sanctions screening matches")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SanctionsMatchResponse>>> getSanctionsMatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entityType,
            Pageable pageable) {
        
        Page<SanctionsMatchResponse> matches = sanctionsService.getSanctionsMatches(status, entityType, pageable);
        return ResponseEntity.ok(ApiResponse.success(matches));
    }

    @PostMapping("/sanctions/matches/{matchId}/resolve")
    @Operation(summary = "Resolve sanctions match")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SanctionsMatchResponse>> resolveSanctionsMatch(
            @PathVariable UUID matchId,
            @Valid @RequestBody ResolveSanctionsMatchRequest request) {
        log.info("Resolving sanctions match: {}", matchId);
        
        SanctionsMatchResponse response = sanctionsService.resolveMatch(matchId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Regulatory Reporting Endpoints
    @PostMapping("/reports/generate")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60, tokens = 2)
    @Operation(summary = "Generate regulatory report")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RegulatoryReportResponse>> generateRegulatoryReport(
            @Valid @RequestBody GenerateReportRequest request) {
        log.info("Generating regulatory report: {}", request.getReportType());
        
        RegulatoryReportResponse response = reportingService.generateReport(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reports")
    @Operation(summary = "Get regulatory reports")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<RegulatoryReportResponse>>> getRegulatoryReports(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        Page<RegulatoryReportResponse> reports = reportingService.getReports(reportType, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/reports/{reportId}/download")
    @Operation(summary = "Download regulatory report")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadRegulatoryReport(@PathVariable UUID reportId) {
        
        byte[] reportData = reportingService.downloadReport(reportId);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=regulatory-report-" + reportId + ".pdf")
                .body(reportData);
    }

    // Risk Assessment Endpoints
    @PostMapping("/risk-assessment")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @Operation(summary = "Perform compliance risk assessment")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> performRiskAssessment(
            @Valid @RequestBody RiskAssessmentRequest request) {
        log.info("Performing risk assessment for: {}", request.getEntityId());
        
        RiskAssessmentResponse response = amlService.performRiskAssessment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/risk-assessments")
    @Operation(summary = "Get risk assessments")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<RiskAssessmentResponse>>> getRiskAssessments(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String riskLevel,
            Pageable pageable) {
        
        Page<RiskAssessmentResponse> assessments = amlService.getRiskAssessments(entityType, riskLevel, pageable);
        return ResponseEntity.ok(ApiResponse.success(assessments));
    }

    // Configuration Endpoints
    @GetMapping("/config/rules")
    @Operation(summary = "Get compliance rules")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplianceRuleResponse>>> getComplianceRules() {
        
        List<ComplianceRuleResponse> rules = amlService.getComplianceRules();
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    @PostMapping("/config/rules")
    @Operation(summary = "Create compliance rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceRuleResponse>> createComplianceRule(
            @Valid @RequestBody CreateComplianceRuleRequest request) {
        log.info("Creating compliance rule: {}", request.getRuleName());
        
        ComplianceRuleResponse response = amlService.createComplianceRule(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/config/rules/{ruleId}")
    @Operation(summary = "Update compliance rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceRuleResponse>> updateComplianceRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateComplianceRuleRequest request) {
        log.info("Updating compliance rule: {}", ruleId);
        
        ComplianceRuleResponse response = amlService.updateComplianceRule(ruleId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Compliance service is healthy"));
    }

    // ============================================================================
    // PAYMENT SERVICE INTEGRATION ENDPOINTS (P0 CRITICAL)
    // Added to fix Feign client mismatch issues
    // ============================================================================

    /**
     * P0 CRITICAL FIX: Government verification endpoint
     * Called by: payment-service/ComplianceServiceClient.verifyWithGovernment()
     *
     * Verifies business entity with government databases (IRS EIN, Secretary of State, etc.)
     */
    @PostMapping("/government/verify")
    @RequiresPermission(Permission.COMPLIANCE_READ)
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 5)
    @Operation(summary = "Verify entity with government databases",
               description = "Validates business entity information against government registries")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<GovernmentVerificationResponse>> verifyWithGovernment(
            @Valid @RequestBody GovernmentVerificationRequest request) {

        log.info("Government verification for entity: {}, type: {}",
                request.getEntityId(), request.getEntityType());

        // Delegate to KYC service which handles government verification
        GovernmentVerificationResponse response = kycService.verifyWithGovernment(request);

        log.info("Government verification completed for entity: {}, result: {}",
                request.getEntityId(), response.getVerificationStatus());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * P0 CRITICAL FIX: KYB (Know Your Business) screening endpoint
     * Called by: payment-service/ComplianceServiceClient.performKYBScreening()
     *
     * Comprehensive business screening including ownership verification,
     * beneficial ownership checks, and business legitimacy validation
     */
    @PostMapping("/kyb/screen")
    @RequiresPermission(Permission.COMPLIANCE_READ)
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 5)
    @Operation(summary = "Perform KYB screening for business entity",
               description = "Comprehensive Know Your Business screening including ownership and legitimacy checks")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<KYBScreeningResponse>> performKYBScreening(
            @Valid @RequestBody KYBScreeningRequest request) {

        log.info("KYB screening for business: {}, jurisdiction: {}",
                request.getBusinessId(), request.getJurisdiction());

        // Perform comprehensive KYB screening
        KYBScreeningResponse response = kycService.performKYBScreening(request);

        log.info("KYB screening completed for business: {}, risk level: {}",
                request.getBusinessId(), response.getRiskLevel());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * P0 CRITICAL FIX: Sanctions check endpoint (alias)
     * Called by: payment-service/ComplianceServiceClient.checkSanctions()
     *
     * This is an alias for /sanctions/screen to maintain backward compatibility
     * with payment-service client expectations
     */
    @PostMapping("/sanctions/check")
    @RequiresPermission(Permission.COMPLIANCE_READ)
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 500, refillTokens = 500, refillPeriodMinutes = 1)
    @Operation(summary = "Check entity against sanctions lists (alias)",
               description = "Screens entity against OFAC, UN, EU and other sanctions lists. Alias for /sanctions/screen")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<SanctionsCheckResponse>> checkSanctions(
            @Valid @RequestBody SanctionsCheckRequest request) {

        log.info("Sanctions check (via /check alias) for entity: {}, type: {}",
                request.getEntityId(), request.getEntityType());

        // Delegate to existing sanctions screening method
        SanctionsScreeningRequest screeningRequest = SanctionsScreeningRequest.builder()
                .entityId(request.getEntityId())
                .entityType(request.getEntityType())
                .entityName(request.getEntityName())
                .country(request.getCountry())
                .dateOfBirth(request.getDateOfBirth())
                .checkType(request.getCheckType())
                .build();

        SanctionsScreeningResponse screeningResponse = sanctionsService.screenEntity(screeningRequest);

        // Map to expected response type
        SanctionsCheckResponse response = SanctionsCheckResponse.builder()
                .entityId(request.getEntityId())
                .screeningId(screeningResponse.getScreeningId())
                .isMatch(screeningResponse.isMatch())
                .matchCount(screeningResponse.getMatches() != null ? screeningResponse.getMatches().size() : 0)
                .riskScore(screeningResponse.getRiskScore())
                .sanctionsList(screeningResponse.getListsChecked())
                .matches(screeningResponse.getMatches())
                .screenedAt(screeningResponse.getScreenedAt())
                .status(screeningResponse.getStatus())
                .build();

        log.info("Sanctions check completed for entity: {}, isMatch: {}, matches: {}",
                request.getEntityId(), response.isMatch(), response.getMatchCount());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}